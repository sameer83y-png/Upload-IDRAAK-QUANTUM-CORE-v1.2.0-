package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * IDRAAK Voice Session Controller
 *
 * Vosk:
 * - Wake word detection.
 * - Releases microphone after wake word.
 *
 * Google Speech Recognizer:
 * - Captures Hindi/Hinglish command after Vosk releases microphone.
 *
 * VoiceCommandRouter:
 * - Processes recognized commands using the existing command engine.
 */
internal class VoiceSessionController(
    context: Context,
    private val onResponse: (String) -> Unit,
    private val onStateChanged: (WakeWordController.VoiceState) -> Unit
) {

    companion object {
        private const val TAG = "IDRAAK_VOICE_SESSION"

        private const val ERROR_RESPONSE =
            "Command process नहीं हो सकी।"

        private const val SPEECH_ERROR_RESPONSE =
            "माफ कीजिए, मैं आपकी आवाज़ समझ नहीं पाया। कृपया दोबारा बोलिए।"
    }

    private val appContext = context.applicationContext

    private val commandRouter =
        VoiceCommandRouter(appContext)

    private val worker =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val sessionLock = Any()

    @Volatile
    private var released = false

    @Volatile
    private var sessionGeneration = 0L

    @Volatile
    private var waitingForGoogleSpeech = false

    private val commandInProgress =
        AtomicBoolean(false)

    private val googleRecognizer =
        GoogleSpeechRecognizer(
            context = appContext,
            listener = object : GoogleSpeechRecognizer.Listener {

                override fun onReady() {
                    Log.i(TAG, "Google Speech Recognition ready")
                }

                override fun onPartialResult(text: String) {
                    Log.d(TAG, "Google partial: $text")
                }

                override fun onFinalResult(text: String) {
                    if (released || text.isBlank()) return

                    Log.i(TAG, "Google final command: $text")

                    waitingForGoogleSpeech = false

                    // Google result में wake word दोबारा
                    // खोजने की जरूरत नहीं है।
                    handleCommand(text)
                }

                override fun onError(
                    errorCode: Int,
                    message: String
                ) {
                    if (released) return

                    Log.e(
                        TAG,
                        "Google speech error: $errorCode - $message"
                    )

                    waitingForGoogleSpeech = false

                    mainHandler.post {
                        if (released) return@post

                        safelyDeliverResponse(
                            SPEECH_ERROR_RESPONSE
                        )
                    }
                }
            }
        )

    private val wakeWordController =
        WakeWordController(

            onWakeWordDetected = {
                handleWakeWordDetected()
            },

            onCommandDetected = { command ->
                handleCommand(command)
            },

            onSleepDetected = {
                invalidateCurrentSession()
            },

            onStateChanged = { state ->
                mainHandler.post {
                    if (!released) {
                        safelyNotifyState(state)
                    }
                }
            }
        )

    /**
     * Vosk recognized text.
     */
    fun onRecognizedText(text: String) {
        if (released || text.isBlank()) return

        if (waitingForGoogleSpeech) return

        try {
            wakeWordController.processRecognizedText(text)
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Recognized text processing failed",
                exception
            )
        }
    }

    /**
     * Wake word detected by Vosk.
     *
     * Pause Vosk first.
     * Google recognition starts only after ACTION_PAUSED.
     */
    private fun handleWakeWordDetected() {
        if (released || waitingForGoogleSpeech) return

        if (!googleRecognizer.isAvailable()) {
            Log.e(TAG, "Google speech recognition is unavailable")

            mainHandler.post {
                if (!released) {
                    safelyDeliverResponse(
                        "Google Speech Recognition इस फोन में उपलब्ध नहीं है।"
                    )
                }
            }
            return
        }

        waitingForGoogleSpeech = true

        Log.i(
            TAG,
            "Wake word detected. Requesting Vosk microphone release."
        )

        try {
            val pauseIntent = Intent(
                appContext,
                VoiceForegroundService::class.java
            ).apply {
                action = VoiceForegroundService.ACTION_PAUSE
            }

            appContext.startService(pauseIntent)

        } catch (exception: Exception) {
            waitingForGoogleSpeech = false

            Log.e(
                TAG,
                "Unable to pause Vosk service",
                exception
            )

            mainHandler.post {
                if (!released) {
                    safelyDeliverResponse(
                        "माइक्रोफोन को Google Voice के लिए तैयार नहीं कर पाया।"
                    )
                }
            }
        }
    }

    /**
     * MainActivity calls this after receiving ACTION_PAUSED.
     *
     * This confirms Vosk has released AudioRecord.
     */
    fun onVoskPaused() {
        if (released || !waitingForGoogleSpeech) return

        Log.i(
            TAG,
            "Vosk microphone release confirmed. Starting Google Speech."
        )

        try {
            googleRecognizer.startListening("hi-IN")

        } catch (exception: Exception) {
            waitingForGoogleSpeech = false

            Log.e(
                TAG,
                "Google recognizer start failed",
                exception
            )

            safelyDeliverResponse(
                SPEECH_ERROR_RESPONSE
            )
        }
    }

    /**
     * Manual activation.
     */
    fun activate() {
        if (released) return

        wakeWordController.activate()
    }

    /**
     * Sleep session.
     */
    @Synchronized
    fun sleep() {
        if (released) return

        waitingForGoogleSpeech = false

        try {
            googleRecognizer.stopListening()
        } catch (exception: Exception) {
            Log.w(TAG, "Google recognizer stop failed", exception)
        }

        wakeWordController.sleep()
    }

    /**
     * Enable or disable Vosk wake word mode.
     */
    fun setWakeWordEnabled(enabled: Boolean) {
        if (released) return

        wakeWordController.setEnabled(enabled)
    }

    fun getVoiceState(): WakeWordController.VoiceState {
        return wakeWordController.getState()
    }

    fun isProcessing(): Boolean {
        return wakeWordController.isProcessing()
    }

    /**
     * Invalidate previous command session.
     */
    private fun invalidateCurrentSession() {

        synchronized(sessionLock) {
            sessionGeneration++
            commandInProgress.set(false)

            waitingForGoogleSpeech = false

            Log.d(
                TAG,
                "Session invalidated. Generation=$sessionGeneration"
            )
        }

        try {
            googleRecognizer.stopListening()
        } catch (exception: Exception) {
            Log.w(
                TAG,
                "Unable to stop Google recognition",
                exception
            )
        }
    }

    /**
     * Route recognized command.
     *
     * Both Google results and existing Vosk active-mode
     * commands use this method.
     */
    private fun handleCommand(command: String) {
        if (released || command.isBlank()) return

        if (!commandInProgress.compareAndSet(false, true)) {
            Log.d(
                TAG,
                "Command ignored: another command is processing"
            )
            return
        }

        val commandGeneration = sessionGeneration

        wakeWordController.startProcessing()

        try {
            worker.execute {
                executeRoute(
                    command = command,
                    generation = commandGeneration
                )
            }

        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to submit voice command",
                exception
            )

            finishWithError(commandGeneration)
        }
    }

    /**
     * Execute existing VoiceCommandRouter.
     */
    private fun executeRoute(
        command: String,
        generation: Long
    ) {

        val task: suspend () -> String = {
            commandRouter.routeRecognizedText(command)
        }

        try {
            task.startCoroutine(
                object : Continuation<String> {

                    override val context =
                        EmptyCoroutineContext

                    override fun resumeWith(
                        result: Result<String>
                    ) {

                        val response = result.getOrElse { error ->

                            Log.e(
                                TAG,
                                "Command routing failed",
                                error
                            )

                            error.message
                                ?.takeIf { it.isNotBlank() }
                                ?: ERROR_RESPONSE
                        }

                        mainHandler.post {
                            completeCommand(
                                response = response,
                                generation = generation
                            )
                        }
                    }
                }
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to execute command route",
                exception
            )

            finishWithError(generation)
        }
    }

    /**
     * Complete command on main thread.
     */
    private fun completeCommand(
        response: String,
        generation: Long
    ) {

        synchronized(sessionLock) {

            if (released) return

            if (generation != sessionGeneration) {
                Log.d(
                    TAG,
                    "Ignoring response from an old session"
                )
                return
            }

            try {
                val isAwake =
                    wakeWordController.getState() !=
                        WakeWordController.VoiceState.SLEEPING

                if (isAwake) {
                    safelyDeliverResponse(response)
                }

            } finally {

                if (
                    generation == sessionGeneration &&
                    !released
                ) {
                    commandInProgress.set(false)
                    wakeWordController.finishProcessing()
                }
            }
        }
    }

    /**
     * Error completion.
     */
    private fun finishWithError(generation: Long) {

        mainHandler.post {

            synchronized(sessionLock) {

                if (released) return@synchronized

                if (generation != sessionGeneration) {
                    return@synchronized
                }

                try {
                    val isAwake =
                        wakeWordController.getVoiceState() !=
                            WakeWordController.VoiceState.SLEEPING

                    if (isAwake) {
                        safelyDeliverResponse(ERROR_RESPONSE)
                    }

                } finally {

                    if (
                        generation == sessionGeneration &&
                        !released
                    ) {
                        commandInProgress.set(false)
                        wakeWordController.finishProcessing()
                    }
                }
            }
        }
    }

    private fun safelyDeliverResponse(response: String) {
        try {
            onResponse(response)
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Response callback failed",
                exception
            )
        }
    }

    private fun safelyNotifyState(
        state: WakeWordController.VoiceState
    ) {
        try {
            onStateChanged(state)
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Voice state callback failed",
                exception
            )
        }
    }

    /**
     * Release controller resources.
     */
    @Synchronized
    fun release() {

        synchronized(sessionLock) {
            if (released) return

            released = true
            sessionGeneration++

            waitingForGoogleSpeech = false
            commandInProgress.set(false)
        }

        try {
            googleRecognizer.destroy()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Google recognizer cleanup failed",
                exception
            )
        }

        worker.shutdownNow()

        try {
            commandRouter.clearConversation()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Conversation cleanup failed",
                exception
            )
        }

        mainHandler.removeCallbacksAndMessages(null)
    }
}
