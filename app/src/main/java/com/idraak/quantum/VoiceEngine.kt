
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * IDRAAK Voice Engine
 *
 * TTS is managed here.
 * Speech recognition remains managed by VoiceForegroundService.
 * Wake word and command state are managed by WakeWordController.
 */
class VoiceEngine(
    context: Context,
    private val listener: Listener? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "IDRAAK_VOICE_ENGINE"
    }

    interface Listener {
        fun onEngineReady() {}
        fun onSpeechStarted() {}
        fun onSpeechCompleted() {}
        fun onSpeechError(message: String) {}
        fun onRecognizedText(text: String) {}
        fun onWakeWordDetected() {}
        fun onSleepDetected() {}
        fun onVoiceStateChanged(state: EngineState) {}
        fun onWakeWordStateChanged(state: WakeWordController.VoiceState) {}
    }

    enum class EngineState {
        INITIALIZING,
        READY,
        LISTENING,
        SPEAKING,
        PAUSED,
        SLEEPING,
        ERROR,
        DESTROYED
    }

    private val appContext = context.applicationContext

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var destroyed = false

    private var currentLanguage = "hi"
    private var currentState = EngineState.INITIALIZING

    private val wakeWordController = WakeWordController(
        onWakeWordDetected = {
            if (!destroyed) {
                listener?.onWakeWordDetected()
                setState(EngineState.READY)
            }
        },
        onCommandDetected = { command ->
            if (!destroyed && command.isNotBlank()) {
                listener?.onRecognizedText(command)
            }
        },
        onSleepDetected = {
            if (!destroyed) {
                pauseListening()
                setState(EngineState.SLEEPING)
                listener?.onSleepDetected()
            }
        },
        onStateChanged = { state ->
            if (!destroyed) {
                listener?.onWakeWordStateChanged(state)
            }
        }
    )

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (destroyed) return

        if (status != TextToSpeech.SUCCESS) {
            reportError("Text-to-Speech initialization failed")
            return
        }

        ttsReady = true

        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    if (!destroyed) {
                        setState(EngineState.SPEAKING)
                        listener?.onSpeechStarted()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (!destroyed) {
                        listener?.onSpeechCompleted()
                        resumeListening()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    reportError("Text-to-Speech failed")
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    reportError("Text-to-Speech error: $errorCode")
                }
            }
        )

        val languageReady = applyTtsLanguage(currentLanguage)

        if (languageReady) {
            setState(EngineState.READY)
            listener?.onEngineReady()
        }
    }

    fun setLanguage(language: String): Boolean {
        if (destroyed) return false

        val normalized = when (language.lowercase(Locale.ROOT)) {
            "hi", "hindi", "hi-in" -> "hi"
            "en", "english", "en-in" -> "en"
            "ur", "urdu", "ur-pk" -> "ur"
            "ar", "arabic", "ar-sa" -> "ar"
            else -> return false
        }

        currentLanguage = normalized

        return if (ttsReady) {
            applyTtsLanguage(normalized)
        } else {
            true
        }
    }

    private fun applyTtsLanguage(language: String): Boolean {
        val locale = when (language) {
            "hi" -> Locale.forLanguageTag("hi-IN")
            "en" -> Locale.forLanguageTag("en-IN")
            "ur" -> Locale.forLanguageTag("ur-PK")
            "ar" -> Locale.forLanguageTag("ar-SA")
            else -> Locale.getDefault()
        }

        val result = tts?.setLanguage(locale)
            ?: TextToSpeech.ERROR

        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED ||
            result == TextToSpeech.ERROR
        ) {
            reportError("TTS language is unavailable: $language")
            return false
        }

        return true
    }

    /**
     * Start or resume Android foreground voice service.
     */
    fun startListening() {
        if (destroyed) return

        startServiceAction(
            VoiceForegroundService.ACTION_RESUME,
            EngineState.LISTENING
        )
    }

    fun pauseListening() {
        if (destroyed) return

        startServiceAction(
            VoiceForegroundService.ACTION_PAUSE,
            EngineState.PAUSED
        )
    }

    fun resumeListening() {
        if (destroyed || !ttsReady) return

        startServiceAction(
            VoiceForegroundService.ACTION_RESUME,
            EngineState.LISTENING
        )
    }

    fun stopListening() {
        if (destroyed) return

        startServiceAction(
            VoiceForegroundService.ACTION_STOP,
            EngineState.PAUSED
        )
    }

    private fun startServiceAction(
        action: String,
        newState: EngineState
    ) {
        val intent = Intent(
            appContext,
            VoiceForegroundService::class.java
        ).apply {
            this.action = action
            putExtra(
                VoiceForegroundService.EXTRA_LANGUAGE,
                currentLanguage
            )
        }

        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                action != VoiceForegroundService.ACTION_PAUSE &&
                action != VoiceForegroundService.ACTION_STOP
            ) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }

            setState(newState)

        } catch (e: Exception) {
            reportError(
                e.message ?: "Voice service action failed"
            )
        }
    }

    fun processRecognizedText(text: String) {
        if (destroyed || text.isBlank()) return

        Log.d(TAG, "Recognized speech: $text")

        wakeWordController.processRecognizedText(text)

        when (wakeWordController.getState()) {
            WakeWordController.VoiceState.SLEEPING -> {
                setState(EngineState.SLEEPING)
            }

            WakeWordController.VoiceState.ACTIVE,
            WakeWordController.VoiceState.PROCESSING -> {
                setState(EngineState.READY)
            }
        }
    }

    fun speak(text: String) {
        if (destroyed || text.isBlank()) return

        if (!ttsReady) {
            reportError("Text-to-Speech is not ready")
            return
        }

        pauseListening()

        val utteranceId = UUID.randomUUID().toString()

        val result = tts?.speak(
            text.trim(),
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        ) ?: TextToSpeech.ERROR

        if (result == TextToSpeech.ERROR) {
            reportError("Unable to speak requested text")
        }
    }

    fun stopSpeaking() {
        if (destroyed) return

        tts?.stop()
        resumeListening()
    }

    fun startProcessing() {
        if (destroyed) return

        wakeWordController.startProcessing()
    }

    fun finishProcessing() {
        if (destroyed) return

        wakeWordController.finishProcessing()

        if (
            wakeWordController.getState() ==
            WakeWordController.VoiceState.ACTIVE
        ) {
            setState(EngineState.READY)
        }
    }

    fun activate() {
        if (destroyed) return

        wakeWordController.activate()
        setState(EngineState.READY)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        if (destroyed) return

        wakeWordController.setEnabled(enabled)

        if (!enabled) {
            setState(EngineState.SLEEPING)
        }
    }

    fun getCurrentLanguage(): String = currentLanguage

    fun getCurrentState(): EngineState = currentState

    fun getWakeWordState(): WakeWordController.VoiceState {
        return wakeWordController.getState()
    }

    private fun setState(state: EngineState) {
        if (destroyed) return

        currentState = state
        listener?.onVoiceStateChanged(state)

        Log.d(TAG, "Engine state: $state")
    }

    private fun reportError(message: String) {
        if (destroyed) return

        Log.e(TAG, message)

        setState(EngineState.ERROR)
        listener?.onSpeechError(message)
    }

    fun release() {
        if (destroyed) return

        destroyed = true

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "TTS release failed", e)
        }

        tts = null
        ttsReady = false
        currentState = EngineState.DESTROYED

        Log.d(TAG, "Voice engine released")
    }
}
