
package com.idraak.quantum

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * IDRAAK Voice Session Controller
 *
 * Responsibilities:
 * - Recognized speech को WakeWordController तक पहुँचाना।
 * - Wake word के बाद command को VoiceCommandRouter तक भेजना।
 * - Processing पूरी होने पर WakeWordController को ACTIVE करना।
 * - Voice state changes को main thread पर भेजना।
 *
 * Microphone और Vosk service का नियंत्रण VoiceEngine /
 * VoiceForegroundService की जिम्मेदारी है।
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

    private val wakeWordController =
        WakeWordController(
            onWakeWordDetected = {
                // State changes are handled by onStateChanged.
            },

            onCommandDetected = { command ->
                handleCommand(command)
            },

            onSleepDetected = {
                mainHandler.post {
                    if (!released) {
                        onStateChanged(
                            WakeWordController.VoiceState.SLEEPING
                        )
                    }
                }
            },

            onStateChanged = { state ->
                mainHandler.post {
                    if (!released) {
                        onStateChanged(state)
                    }
                }
            }
        )

    fun onRecognizedText(text: String) {
        if (released || text.isBlank()) return

        wakeWordController.processRecognizedText(text)
    }

    fun activate() {
        if (released) return

        wakeWordController.activate()
    }

    fun sleep() {
        if (released) return

        // Sleep केवल wake-word state बदलता है।
        // Microphone service बंद नहीं होती।
        wakeWordController.sleep()
    }

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

    private fun handleCommand(command: String) {
        if (released || command.isBlank()) return

        wakeWordController.startProcessing()

        try {
            worker.execute {
                executeRoute(command)
            }
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to submit voice command",
                exception
            )

            finishWithError()
        }
    }

    private fun executeRoute(command: String) {

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

                        val response =
                            result.getOrElse { error ->

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
                            if (!released) {
                                try {
                                    onResponse(response)
                                } finally {
                                    wakeWordController.finishProcessing()
                                }
                            }
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

            finishWithError()
        }
    }

    private fun finishWithError() {
        mainHandler.post {
            if (!released) {
                try {
                    onResponse(ERROR_RESPONSE)
                } finally {
                    wakeWordController.finishProcessing()
                }
            }
        }
    }

    fun release() {
        if (released) return

        released = true

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
