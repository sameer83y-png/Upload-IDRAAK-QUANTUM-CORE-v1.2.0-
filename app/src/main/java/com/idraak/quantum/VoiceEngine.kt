
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * IDRAAK Voice Engine
 *
 * Responsibilities:
 * 1. Text-to-Speech
 * 2. Voice service control
 * 3. Language management
 * 4. Speech result handling
 * 5. Wake word controller integration
 *
 * SpeechRecognizer remains owned by VoiceForegroundService.
 * This prevents multiple recognizers from competing for microphone access.
 */
class VoiceEngine(
    private val context: Context,
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
        fun onVoiceStateChanged(state: VoiceState) {}
    }

    enum class VoiceState {
        INITIALIZING,
        READY,
        LISTENING,
        SPEAKING,
        PAUSED,
        SLEEPING,
        ERROR,
        DESTROYED
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var destroyed = false

    private var currentLanguage = "hi"
    private var currentState = VoiceState.INITIALIZING

    private val wakeWordController = WakeWordController(
        onWakeWordDetected = {
            if (!destroyed) {
                listener?.onWakeWordDetected()
                setState(VoiceState.READY)
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
                setState(VoiceState.SLEEPING)
                listener?.onSleepDetected()
            }
        }
    )

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (destroyed) return

        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            applyTtsLanguage(currentLanguage)

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(utteranceId: String?) {
                        if (!destroyed) {
                            setState(VoiceState.SPEAKING)
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

            setState(VoiceState.READY)
            listener?.onEngineReady()

        } else {
            reportError("Text-to-Speech initialization failed")
        }
    }

    /**
     * Select voice language.
     * Supported: Hindi, English, Urdu, Arabic.
     */
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

        if (ttsReady) {
            return applyTtsLanguage(normalized)
        }

        return true
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
            reportError("Selected TTS language is not available: $language")
            return false
        }

        return true
    }

    /**
     * Start the existing foreground voice service.
     */
    fun startListening() {
        if (destroyed) return

        val intent = Intent(
            context,
            VoiceForegroundService::class.java
        ).apply {
            action = VoiceForegroundService.ACTION_START
            putExtra(
                VoiceForegroundService.EXTRA_LANGUAGE,
                currentLanguage
            )
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            setState(VoiceState.LISTENING)

        } catch (e: Exception) {
            reportError(
                e.message ?: "Could not start voice listening service"
            )
        }
    }

    /**
     * Pause microphone listening.
     */
    fun pauseListening() {
        if (destroyed) return

        sendServiceAction(VoiceForegroundService.ACTION_PAUSE)
        setState(VoiceState.PAUSED)
    }

    /**
     * Resume microphone listening.
     */
    fun resumeListening() {
        if (destroyed || !ttsReady) return

        val intent = Intent(
            context,
            VoiceForegroundService::class.java
        ).apply {
            action = VoiceForegroundService.ACTION_RESUME
            putExtra(
                VoiceForegroundService.EXTRA_LANGUAGE,
                currentLanguage
            )
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            setState(VoiceState.LISTENING)

        } catch (e: Exception) {
            reportError(
                e.message ?: "Could not resume voice listening"
            )
        }
    }

    /**
     * Stop the foreground voice service.
     */
    fun stopListening() {
        if (destroyed) return

        sendServiceAction(VoiceForegroundService.ACTION_STOP)
        setState(VoiceState.PAUSED)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(
            context,
            VoiceForegroundService::class.java
        ).apply {
            this.action = action
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {
            reportError(e.message ?: "Voice service action failed")
        }
    }

    /**
     * Process recognized speech received from VoiceForegroundService.
     */
    fun processRecognizedText(text: String) {
        if (destroyed || text.isBlank()) return

        Log.d(TAG, "Recognized: $text")

        val result = wakeWordController.processText(text)

        when (result) {
            WakeWordController.Result.WAKE_WORD_DETECTED -> {
                setState(VoiceState.READY)
            }

            WakeWordController.Result.SLEEP_DETECTED -> {
                setState(VoiceState.SLEEPING)
            }

            WakeWordController.Result.COMMAND_DETECTED -> {
                setState(VoiceState.READY)
            }

            WakeWordController.Result.IGNORED -> {
                // No valid command detected.
            }

            WakeWordController.Result.PROCESSING -> {
                // A command is already being processed.
            }
        }
    }

    /**
     * Speak text using Android TextToSpeech.
     */
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
            reportError("Unable to speak the requested text")
        }
    }

    /**
     * Stop current speech output.
     */
    fun stopSpeaking() {
        if (destroyed) return

        tts?.stop()
        resumeListening()
    }

    /**
     * Tell the wake word controller that command processing is finished.
     */
    fun finishProcessing() {
        if (destroyed) return

        wakeWordController.finishProcessing()
        setState(VoiceState.READY)
    }

    fun getCurrentLanguage(): String {
        return currentLanguage
    }

    fun getCurrentState(): VoiceState {
        return currentState
    }

    private fun setState(state: VoiceState) {
        if (destroyed) return

        currentState = state
        listener?.onVoiceStateChanged(state)

        Log.d(TAG, "Voice state: $state")
    }

    private fun reportError(message: String) {
        if (destroyed) return

        Log.e(TAG, message)

        setState(VoiceState.ERROR)
        listener?.onSpeechError(message)
    }

    /**
     * Release TTS resources.
     * Call when the owning Activity/Application is destroyed.
     */
    fun release() {
        if (destroyed) return

        destroyed = true

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "TTS shutdown failed", e)
        }

        tts = null
        ttsReady = false
        currentState = VoiceState.DESTROYED

        Log.d(TAG, "Voice engine released")
    }
}
