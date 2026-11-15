
package com.idraak.quantum

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * IDRAAK Voice Engine
 *
 * Responsibilities:
 * - Text To Speech
 * - TTS language and voice selection
 * - Coordinate listening pause/resume during speech
 *
 * Speech recognition:
 * VoiceForegroundService
 *
 * Service control:
 * VoiceManager
 *
 * Wake word:
 * WakeWordController
 */
class VoiceEngine(
    context: Context,
    private val voiceManager: VoiceManager,
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
        fun onVoiceStateChanged(state: EngineState) {}
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null

    private var ttsReady = false
    private var destroyed = false

    private var desiredListening = false
    private var currentLanguage = "hi"
    private var currentGender = "Male"

    private var currentState = EngineState.INITIALIZING
    private var activeUtteranceId: String? = null
    private var pendingSpeech: String? = null

    init {
        tts = TextToSpeech(appContext, this)
    }

    // -------------------------------------------------
    // TTS INITIALIZATION
    // -------------------------------------------------

    override fun onInit(status: Int) {
        if (destroyed) return

        if (status != TextToSpeech.SUCCESS) {
            reportError("Text-to-Speech initialization failed")
            return
        }

        ttsReady = true

        setupTtsListener()

        if (!applyTtsLanguage(currentLanguage)) {
            return
        }

        applyVoiceGender()

        setState(EngineState.READY)
        listener?.onEngineReady()

        val pending = pendingSpeech
        pendingSpeech = null

        if (!pending.isNullOrBlank()) {
            speak(pending)
        }
    }

    // -------------------------------------------------
    // LANGUAGE
    // -------------------------------------------------

    fun setLanguage(language: String): Boolean {
        if (destroyed) return false

        val normalized = when (
            language.trim().lowercase(Locale.ROOT)
        ) {
            "hi", "hindi", "hi-in" -> "hi"
            "en", "english", "en-in", "en-us" -> "en"
            "ur", "urdu", "ur-pk" -> "ur"
            "ar", "arabic", "ar-sa" -> "ar"
            else -> return false
        }

        currentLanguage = normalized

        if (!ttsReady) return true

        val success = applyTtsLanguage(normalized)

        if (success) {
            applyVoiceGender()
        }

        return success
    }

    private fun getLocale(language: String): Locale {
        return when (language) {
            "hi" -> Locale.forLanguageTag("hi-IN")
            "en" -> Locale.forLanguageTag("en-IN")
            "ur" -> Locale.forLanguageTag("ur-PK")
            "ar" -> Locale.forLanguageTag("ar-SA")
            else -> Locale.getDefault()
        }
    }

    private fun applyTtsLanguage(language: String): Boolean {
        val engine = tts ?: return false

        val locale = getLocale(language)

        val result = engine.setLanguage(locale)

        if (result < TextToSpeech.LANG_AVAILABLE) {
            reportError("TTS language is unavailable: $language")
            return false
        }

        return true
    }

    // -------------------------------------------------
    // VOICE GENDER
    // -------------------------------------------------

    fun setVoiceGender(gender: String) {
        if (destroyed) return

        currentGender = if (
            gender.equals("Female", ignoreCase = true)
        ) {
            "Female"
        } else {
            "Male"
        }

        if (ttsReady) {
            applyVoiceGender()
        }
    }

    private fun applyVoiceGender() {
        val engine = tts ?: return

        val languageCode = getLocale(currentLanguage).language

        val voices = engine.voices?.filter {
            it.locale.language == languageCode
        } ?: emptyList()

        val selectedVoice = voices.firstOrNull {
            if (currentGender == "Female") {
                it.name.contains("female", ignoreCase = true)
            } else {
                it.name.contains("male", ignoreCase = true) &&
                    !it.name.contains("female", ignoreCase = true)
            }
        } ?: voices.firstOrNull()

        if (selectedVoice != null) {
            try {
                engine.voice = selectedVoice
            } catch (e: Exception) {
                Log.w(TAG, "Voice selection failed", e)
            }
        }
    }

    // -------------------------------------------------
    // SPEECH RECOGNITION SERVICE CONTROL
    // -------------------------------------------------

    fun startListening(language: String? = null) {
        if (destroyed) return

        if (!language.isNullOrBlank()) {
            setLanguage(language)
        }

        desiredListening = true

        try {
            voiceManager.start(
                getLocale(currentLanguage).toLanguageTag()
            )

            setState(EngineState.LISTENING)

        } catch (e: Exception) {
            reportError(
                e.message ?: "Unable to start voice service"
            )
        }
    }

    fun pauseListening() {
        if (destroyed) return

        desiredListening = false

        try {
            voiceManager.pause()
            setState(EngineState.PAUSED)
        } catch (e: Exception) {
            reportError(
                e.message ?: "Unable to pause voice service"
            )
        }
    }

    fun resumeListening() {
        if (destroyed) return

        desiredListening = true

        try {
            voiceManager.resume(
                getLocale(currentLanguage).toLanguageTag()
            )

            setState(EngineState.LISTENING)

        } catch (e: Exception) {
            reportError(
                e.message ?: "Unable to resume voice service"
            )
        }
    }

    fun stopListening() {
        if (destroyed) return

        desiredListening = false

        try {
            voiceManager.stop()
            setState(EngineState.PAUSED)
        } catch (e: Exception) {
            reportError(
                e.message ?: "Unable to stop voice service"
            )
        }
    }

    /**
     * Internal pause for TTS.
     * It does not change desiredListening.
     */
    private fun pauseForSpeech() {
        if (destroyed || !desiredListening) return

        try {
            voiceManager.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Pause before speech failed", e)
        }
    }

    /**
     * Resume only when listening was previously requested.
     */
    private fun resumeAfterSpeech() {
        if (destroyed || !desiredListening) return

        try {
            voiceManager.resume(
                getLocale(currentLanguage).toLanguageTag()
            )

            setState(EngineState.LISTENING)

        } catch (e: Exception) {
            reportError(
                e.message ?: "Unable to resume after speech"
            )
        }
    }

    // -------------------------------------------------
    // TEXT TO SPEECH
    // -------------------------------------------------

    fun speak(text: String) {
        if (destroyed || text.isBlank()) return

        if (!ttsReady) {
            pendingSpeech = text
            return
        }

        val engine = tts ?: return

        pauseForSpeech()

        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId

        setState(EngineState.SPEAKING)

        val result = engine.speak(
            text.trim(),
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )

        if (result == TextToSpeech.ERROR) {
            activeUtteranceId = null
            reportError("Unable to speak requested text")
            resumeAfterSpeech()
        }
    }

    fun stopSpeaking() {
        if (destroyed) return

        activeUtteranceId = null
        pendingSpeech = null

        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "TTS stop failed", e)
        }

        resumeAfterSpeech()
    }

    // -------------------------------------------------
    // TTS CALLBACKS
    // -------------------------------------------------

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        if (
                            destroyed ||
                            utteranceId != activeUtteranceId
                        ) return@post

                        setState(EngineState.SPEAKING)
                        listener?.onSpeechStarted()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        if (
                            destroyed ||
                            utteranceId != activeUtteranceId
                        ) return@post

                        activeUtteranceId = null

                        listener?.onSpeechCompleted()

                        resumeAfterSpeech()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        handleTtsError(
                            utteranceId,
                            "Text-to-Speech failed"
                        )
                    }
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    mainHandler.post {
                        handleTtsError(
                            utteranceId,
                            "Text-to-Speech error: $errorCode"
                        )
                    }
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean
                ) {
                    mainHandler.post {
                        if (
                            destroyed ||
                            utteranceId != activeUtteranceId
                        ) return@post

                        activeUtteranceId = null

                        if (desiredListening) {
                            resumeAfterSpeech()
                        } else {
                            setState(EngineState.PAUSED)
                        }
                    }
                }
            }
        )
    }

    private fun handleTtsError(
        utteranceId: String?,
        message: String
    ) {
        if (
            destroyed ||
            utteranceId != activeUtteranceId
        ) return

        activeUtteranceId = null

        reportError(message)
        resumeAfterSpeech()
    }

    // -------------------------------------------------
    // WAKE WORD STATE DISPLAY
    // -------------------------------------------------

    fun setSleeping(sleeping: Boolean) {
        if (destroyed) return

        if (sleeping) {
            setState(EngineState.SLEEPING)
        } else {
            setState(
                if (desiredListening) {
                    EngineState.LISTENING
                } else {
                    EngineState.READY
                }
            )
        }
    }

    // -------------------------------------------------
    // STATE
    // -------------------------------------------------

    fun getCurrentLanguage(): String {
        return currentLanguage
    }

    fun getCurrentState(): EngineState {
        return currentState
    }

    fun isTtsReady(): Boolean {
        return ttsReady
    }

    fun isListeningRequested(): Boolean {
        return desiredListening
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

    // -------------------------------------------------
    // RELEASE
    // -------------------------------------------------

    fun release() {
        if (destroyed) return

        destroyed = true

        desiredListening = false
        pendingSpeech = null
        activeUtteranceId = null

        mainHandler.removeCallbacksAndMessages(null)

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "TTS release failed", e)
        }

        tts = null
        ttsReady = false
        currentState = EngineState.DESTROYED

        Log.d(TAG, "Voice engine released")
    }
}
