package com.idraak.quantum

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Voice session lifecycle coordinator.
 *
 * - Foreground voice service को start/pause/resume/stop करता है।
 * - Recognized text को WakeWordController तक पहुँचाता है।
 * - Wake word के बाद command को VoiceCommandRouter तक भेजता है।
 * - Processing पूरी होने पर WakeWordController को वापस ACTIVE करता है।
 */
internal class VoiceSessionController(
    context: Context,
    private val onResponse: (String) -> Unit,
    private val onStateChanged: (WakeWordController.VoiceState) -> Unit
) {

    private val appContext = context.applicationContext
    private val voiceManager = VoiceManager(appContext)
    private val commandRouter = VoiceCommandRouter(appContext)

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var released = false

    private val wakeWordController = WakeWordController(
        onWakeWordDetected = {
            // State callback नीचे onStateChanged में handle होता है।
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

    fun start(language: String = "hi-IN") {
        if (released) return
        voiceManager.start(language)
    }

    fun pause() {
        if (released) return
        voiceManager.pause()
    }

    fun resume(language: String = "hi-IN") {
        if (released) return
        voiceManager.resume(language)
    }

    fun stop() {
        if (released) return
        voiceManager.stop()
        wakeWordController.sleep()
    }

    /**
     * MainActivity के voice broadcast receiver से recognized text यहाँ भेजें।
     */
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
        wakeWordController.sleep()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        if (released) return
        wakeWordController.setEnabled(enabled)
    }

    fun getVoiceState(): WakeWordController.VoiceState =
        wakeWordController.getState()

    fun isProcessing(): Boolean =
        wakeWordController.isProcessing()

    private fun handleCommand(command: String) {
        if (released || command.isBlank()) return

        wakeWordController.startProcessing()

        try {
            worker.execute {
                executeRoute(command)
            }
        } catch (_: Exception) {
            wakeWordController.finishProcessing()
            mainHandler.post {
                if (!released) {
                    onResponse("Command process नहीं हो सकी।")
                }
            }
        }
    }

    private fun executeRoute(command: String) {
        val task: suspend () -> String = {
            commandRouter.routeRecognizedText(command)
        }

        task.startCoroutine(
            object : Continuation<String> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<String>) {
                    val response = result.getOrElse { error ->
                        error.message
                            ?.takeIf { it.isNotBlank() }
                            ?: "Command process नहीं हो सकी।"
                    }

                    mainHandler.post {
                        if (!released) {
                            onResponse(response)
                            wakeWordController.finishProcessing()
                        }
                    }
                }
            }
        )
    }

    fun release() {
        if (released) return

        released = true
        voiceManager.stop()
        worker.shutdownNow()
        commandRouter.clearConversation()
    }
}
