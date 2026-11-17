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

    @Volatile
    private var released = false

    /**
     * हर नई session lifecycle पर बदलता है।
     * Sleep या release के बाद पुराने command का जवाब
     * स्वीकार नहीं किया जाएगा।
     */
    @Volatile
    private var sessionGeneration = 0L

    private val commandInProgress =
        AtomicBoolean(false)

    private val wakeWordController =
        WakeWordController(
            onWakeWordDetected = {
                // State changes are handled by onStateChanged.
            },

            onCommandDetected = { command ->
                handleCommand(command)
            },

            onSleepDetected = {
                // WakeWordController का onStateChanged
                // पहले ही SLEEPING state भेजता है।
                // Duplicate callback की जरूरत नहीं है।
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

        wakeWordController.processRecognizedText(text)
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

        sessionGeneration++

        wakeWordController.sleep()
    }

    /**
     * Wake word recognition enable/disable करता है।
     */
    fun setWakeWordEnabled(enabled: Boolean) {
        if (released) return

        if (!enabled) {
            synchronized(this) {
                sessionGeneration++
            }
        }

        wakeWordController.setEnabled(enabled)
    }

    fun getVoiceState(): WakeWordController.VoiceState {
        return wakeWordController.getState()
    }

    fun isProcessing(): Boolean {
        return wakeWordController.isProcessing()
    }

    /**
     * Wake word के बाद प्राप्त command को process करता है।
     */
    private fun handleCommand(command: String) {
        if (released || command.isBlank()) return

        if (!commandInProgress.compareAndSet(false, true)) {
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
     * Router का suspend function चलाता है।
     *
     * Gemini response आने पर continuation resume होगा।
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

        if (released) {
            commandInProgress.set(false)
            return
        }

        try {
            val isCurrentSession =
                generation == sessionGeneration

            val isAwake =
                wakeWordController.getState() !=
                    WakeWordController.VoiceState.SLEEPING

            if (isCurrentSession && isAwake) {
                safelyDeliverResponse(response)
            }

        } finally {

            commandInProgress.set(false)

            if (!released) {
                wakeWordController.finishProcessing()
            }
        }
    }

    /**
     * Command submission या execution में error आने पर।
     */
    private fun finishWithError(generation: Long) {
        mainHandler.post {

            if (released) {
                commandInProgress.set(false)
                return@post
            }

            try {
                val isCurrentSession =
                    generation == sessionGeneration

                val isAwake =
                    wakeWordController.getState() !=
                        WakeWordController.VoiceState.SLEEPING

                if (isCurrentSession && isAwake) {
                    safelyDeliverResponse(ERROR_RESPONSE)
                }

            } finally {

                commandInProgress.set(false)

                if (!released) {
                    wakeWordController.finishProcessing()
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
            Log.e(
                TAG,
                "Response callback failed",
                exception
            )
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
            Log.e(
                TAG,
                "Voice state callback failed",
                exception
            )
        }
    }

    /**
     * Controller resources release करता है।
     *
     * यह method microphone service को stop नहीं करता।
     * Service lifecycle VoiceEngine की जिम्मेदारी है।
     */
    @Synchronized
    fun release() {
        if (released) return

        released = true
        sessionGeneration++

        commandInProgress.set(false)

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
