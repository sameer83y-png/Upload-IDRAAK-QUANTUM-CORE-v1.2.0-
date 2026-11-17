package com.idraak.quantum

import android.content.Context
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
 * Responsibilities:
 * - Recognized speech को WakeWordController तक पहुँचाना।
 * - Wake word के बाद command को VoiceCommandRouter तक भेजना।
 * - Command processing lifecycle संभालना।
 * - Sleep के बाद पुरानी command के जवाब रोकना।
 * - Voice state changes को main thread पर भेजना।
 *
 * Microphone और Vosk service का नियंत्रण
 * VoiceEngine / VoiceForegroundService की जिम्मेदारी है।
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

    private val commandInProgress =
        AtomicBoolean(false)

    private val wakeWordController =
        WakeWordController(
            onWakeWordDetected = {
                Log.d(TAG, "Wake word detected")
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
     * Vosk से प्राप्त recognized text यहाँ आता है।
     */
    fun onRecognizedText(text: String) {
        if (released || text.isBlank()) return

        try {
            wakeWordController.processRecognizedText(text)
        } catch (exception: Exception) {
            Log.e(TAG, "Recognized text processing failed", exception)
        }
    }

    /**
     * Assistant को ACTIVE state में लाता है।
     */
    fun activate() {
        if (released) return

        wakeWordController.activate()
    }

    /**
     * केवल wake-word state को SLEEPING करता है।
     * Microphone या Vosk service बंद नहीं होती।
     */
    @Synchronized
    fun sleep() {
        if (released) return

        wakeWordController.sleep()
    }

    /**
     * Wake word recognition enable/disable करता है।
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
     * Sleep होने पर वर्तमान session को अमान्य करता है।
     *
     * इससे पुरानी command का जवाब नई session में
     * deliver नहीं होगा।
     */
    private fun invalidateCurrentSession() {
        synchronized(sessionLock) {
            sessionGeneration++
            commandInProgress.set(false)

            Log.d(
                TAG,
                "Session invalidated. Generation=$sessionGeneration"
            )
        }
    }

    /**
     * Wake word के बाद प्राप्त command को process करता है।
     */
    private fun handleCommand(command: String) {
        if (released || command.isBlank()) return

        if (!commandInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Command ignored: another command is processing")
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
            Log.e(TAG, "Unable to submit voice command", exception)
            finishWithError(commandGeneration)
        }
    }

    /**
     * Router का suspend function चलाता है।
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
     * Main thread पर command completion संभालता है।
     */
    private fun completeCommand(
        response: String,
        generation: Long
    ) {

        synchronized(sessionLock) {

            if (released) return

            // पुरानी session का जवाब पूरी तरह अनदेखा करें।
            if (generation != sessionGeneration) {
                Log.d(TAG, "Ignoring response from an old session")
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

                // केवल उसी session का processing flag clear करें।
                if (generation == sessionGeneration && !released) {
                    commandInProgress.set(false)
                    wakeWordController.finishProcessing()
                }
            }
        }
    }

    /**
     * Command submission या execution में error आने पर।
     */
    private fun finishWithError(generation: Long) {
        mainHandler.post {

            synchronized(sessionLock) {

                if (released) return@synchronized

                // पुरानी session की error भी नई session को
                // प्रभावित नहीं करनी चाहिए।
                if (generation != sessionGeneration) {
                    Log.d(TAG, "Ignoring error from an old session")
                    return@synchronized
                }

                try {
                    val isAwake =
                        wakeWordController.getState() !=
                            WakeWordController.VoiceState.SLEEPING

                    if (isAwake) {
                        safelyDeliverResponse(ERROR_RESPONSE)
                    }

                } finally {

                    if (generation == sessionGeneration && !released) {
                        commandInProgress.set(false)
                        wakeWordController.finishProcessing()
                    }
                }
            }
        }
    }

    /**
     * Response callback को सुरक्षित रूप से invoke करता है।
     */
    private fun safelyDeliverResponse(response: String) {
        try {
            onResponse(response)
        } catch (exception: Exception) {
            Log.e(TAG, "Response callback failed", exception)
        }
    }

    /**
     * Voice state callback को सुरक्षित रूप से invoke करता है।
     */
    private fun safelyNotifyState(
        state: WakeWordController.VoiceState
    ) {
        try {
            onStateChanged(state)
        } catch (exception: Exception) {
            Log.e(TAG, "Voice state callback failed", exception)
        }
    }

    /**
     * Controller resources release करता है।
     *
     * यह method microphone service को stop नहीं करता।
     */
    @Synchronized
    fun release() {
        synchronized(sessionLock) {
            if (released) return

            released = true
            sessionGeneration++
            commandInProgress.set(false)
        }

        worker.shutdownNow()

        try {
            commandRouter.clearConversation()
        } catch (exception: Exception) {
            Log.e(TAG, "Conversation cleanup failed", exception)
        }

        mainHandler.removeCallbacksAndMessages(null)
    }
}
