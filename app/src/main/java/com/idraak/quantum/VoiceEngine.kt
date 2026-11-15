
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
    private var languageReady = false
    private var destroyed = false

    private var desiredListening = false

    private var currentLanguage = "hi"
    private var currentGender = "Male"

    private var currentState = EngineState.INITIALIZING

    private var activeUtteranceId: String? = null
    private var pendingSpeech: String? = null

    init {
        Log.d(TAG, "Creating TextToSpeech engine")

        tts = TextToSpeech(appContext, this)
    }

    // -------------------------------------------------
    // TTS INITIALIZATION
    // -------------------------------------------------

    override fun onInit(status: Int) {

        if (destroyed) return

        if (status != TextToSpeech.SUCCESS) {

            Log.e(TAG, "TTS initialization failed: $status")

            reportError(
                "Android Text-to-Speech initialization failed"
            )

            return
        }

        ttsReady = true

        Log.d(TAG, "TTS engine initialized successfully")

        setupTtsListener()

        languageReady = applyTtsLanguage(
            currentLanguage
        )

        if (languageReady) {
            applyVoiceGender()
        }

        setState(EngineState.READY)

        listener?.onEngineReady()

        Log.d(
            TAG,
            "TTS ready=$ttsReady, languageReady=$languageReady, language=$currentLanguage"
        )

        val pending = pendingSpeech

        if (!pending.isNullOrBlank() && languageReady) {

            pendingSpeech = null

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

            else -> {
                Log.e(TAG, "Unsupported language requested: $language")
                return false
            }
        }

        currentLanguage = normalized

        if (!ttsReady) {
            Log.d(TAG, "Language saved for TTS initialization: $normalized")
            return true
        }

        languageReady = applyTtsLanguage(normalized)

        if (!languageReady) {

            val message =
                "इस डिवाइस में ${languageName(normalized)} TTS आवाज़ उपलब्ध नहीं है। कृपया Android Text-to-Speech में इस भाषा का Voice Data इंस्टॉल करें।"

            Log.e(
                TAG,
                "$message Locale=${getLocale(normalized).toLanguageTag()}"
            )

            reportError(message)

            return false
        }

        applyVoiceGender()

        Log.d(
            TAG,
            "TTS language selected: ${getLocale(normalized).toLanguageTag()}"
        )

        val pending = pendingSpeech

        if (!pending.isNullOrBlank()) {

            pendingSpeech = null

            speak(pending)
        }

        return true
    }

    private fun languageName(language: String): String {

        return when (language) {
            "hi" -> "हिंदी"
            "en" -> "English"
            "ur" -> "اردو"
            "ar" -> "العربية"
            else -> language
        }
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

        return try {

            val result = engine.setLanguage(locale)

            Log.d(
                TAG,
                "setLanguage(${locale.toLanguageTag()}) result=$result"
            )

            result >= TextToSpeech.LANG_AVAILABLE

        } catch (e: Exception) {

            Log.e(TAG, "TTS language selection failed", e)

            false
        }
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

        if (ttsReady && languageReady) {
            applyVoiceGender()
        }
    }

    private fun applyVoiceGender() {

        val engine = tts ?: return

        if (!languageReady) return

        try {

            val locale = getLocale(currentLanguage)

            val voices = engine.voices?.filter {

                it.locale.language == locale.language

            } ?: emptyList()

            Log.d(
                TAG,
                "Available ${locale.language} TTS voices: ${voices.map { it.name }}"
            )

            val selectedVoice = voices.firstOrNull {

                if (currentGender == "Female") {

                    it.name.contains(
                        "female",
                        ignoreCase = true
                    )

                } else {

                    it.name.contains(
                        "male",
                        ignoreCase = true
                    ) && !it.name.contains(
                        "female",
                        ignoreCase = true
                    )
                }

            } ?: voices.firstOrNull()

            if (selectedVoice != null) {

                engine.voice = selectedVoice

                Log.d(
                    TAG,
                    "Selected TTS voice: ${selectedVoice.name}"
                )

            } else {

                Log.w(
                    TAG,
                    "No matching voice found for ${locale.language}"
                )
            }

        } catch (e: Exception) {

            Log.e(TAG, "Voice selection failed", e)
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

            Log.d(TAG, "Voice recognition service start requested")

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

            Log.d(TAG, "Voice recognition service resume requested")

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

    // -------------------------------------------------
    // LISTENING COORDINATION
    // -------------------------------------------------

    private fun pauseForSpeech() {

        if (destroyed || !desiredListening) return

        try {

            voiceManager.pause()

            Log.d(TAG, "Listening paused for TTS")

        } catch (e: Exception) {

            Log.e(TAG, "Pause before speech failed", e)
        }
    }

    private fun resumeAfterSpeech() {

        if (destroyed || !desiredListening) return

        try {

            voiceManager.resume(
                getLocale(currentLanguage).toLanguageTag()
            )

            setState(EngineState.LISTENING)

            Log.d(TAG, "Listening resumed after TTS")

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

            Log.d(TAG, "TTS not ready; speech queued")

            pendingSpeech = text

            return
        }

        if (!languageReady) {

            Log.e(
                TAG,
                "Speech blocked because selected TTS language is unavailable"
            )

            reportError(
                "चुनी हुई भाषा की TTS आवाज़ उपलब्ध नहीं है। Android Text-to-Speech में Voice Data इंस्टॉल करें।"
            )

            return
        }

        val engine = tts ?: return

        pauseForSpeech()

        val utteranceId = UUID.randomUUID().toString()

        activeUtteranceId = utteranceId

        setState(EngineState.SPEAKING)

        Log.d(
            TAG,
            "TTS speak requested. Utterance=$utteranceId, length=${text.length}"
        )

        try {

            val result = engine.speak(
                text.trim(),
                TextToSpeech.QUEUE_FLUSH,
                Bundle(),
                utteranceId
            )

            Log.d(
                TAG,
                "TTS speak() returned: $result"
            )

            if (result == TextToSpeech.ERROR) {

                activeUtteranceId = null

                reportError(
                    "Android Text-to-Speech could not start speaking"
                )

                resumeAfterSpeech()
            }

        } catch (e: Exception) {

            activeUtteranceId = null

            Log.e(TAG, "TTS speak request failed", e)

            reportError(
                e.message ?: "Text-to-Speech request failed"
            )

            resumeAfterSpeech()
        }
    }

    fun stopSpeaking() {

        if (destroyed) return

        activeUtteranceId = null
        pendingSpeech = null

        try {

            tts?.stop()

            Log.d(TAG, "TTS speech stopped")

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

                        Log.d(
                            TAG,
                            "TTS playback started: $utteranceId"
                        )

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

                        Log.d(
                            TAG,
                            "TTS playback completed: $utteranceId"
                        )

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

                        Log.d(
                            TAG,
                            "TTS playback stopped: interrupted=$interrupted"
                        )

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

        Log.e(TAG, message)

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

    fun isLanguageReady(): Boolean {
        return languageReady
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
        languageReady = false

        currentState = EngineState.DESTROYED

        Log.d(TAG, "Voice engine released")
    }
}
