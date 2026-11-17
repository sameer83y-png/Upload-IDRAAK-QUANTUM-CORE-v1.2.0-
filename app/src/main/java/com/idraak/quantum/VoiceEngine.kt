package com.idraak.quantum

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.UUID

/**
 * IDRAAK Voice Engine
 *
 * Responsibilities:
 * - TextToSpeech initialization and playback.
 * - Speech callbacks and playback state.
 * - TTS language, gender and speech-rate configuration.
 * - Coordinate recognition pause/resume through VoiceManager.
 *
 * Vosk recognition and microphone management belong to
 * VoiceForegroundService.
 *
 * Wake-word detection belongs to WakeWordController.
 */
class VoiceEngine(
    context: Context,
    private val voiceManager: VoiceManager,
    private val listener: Listener? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "IDRAAK_VoiceEngine"

        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val MIN_SPEECH_RATE = 0.5f
        private const val MAX_SPEECH_RATE = 2.0f
    }

    interface Listener {
        fun onEngineReady()
        fun onSpeechStarted()
        fun onSpeechCompleted()
        fun onSpeechError(message: String)
        fun onVoiceStateChanged(state: EngineState)
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

    private val languageManager =
        TtsLanguageManager(appContext)

    private var tts: TextToSpeech? = null

    private var engineReady = false
    private var destroyed = false

    private var listeningRequested = false
    private var pausedForSpeech = false
    private var manuallyPaused = false
    private var sleeping = false

    private var recognitionLanguage = "hi-IN"
    private var activeUtteranceId: String? = null

    private var pendingSpeechText: String? = null

    private var speechRate = DEFAULT_SPEECH_RATE

    private var currentState = EngineState.INITIALIZING
        private set

    init {
        Log.d(TAG, "Initializing TTS engine")
        tts = TextToSpeech(appContext, this)
    }

    // -------------------------------------------------
    // TTS INITIALIZATION
    // -------------------------------------------------

    override fun onInit(status: Int) {
        if (destroyed) return

        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS initialization failed: $status")

            engineReady = false
            pendingSpeechText = null

            updateState(EngineState.ERROR)

            listener?.onSpeechError(
                "Text-to-Speech इंजन शुरू नहीं हो पाया।"
            )
            return
        }

        val engine = tts ?: return

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    if (destroyed) return
                    if (utteranceId != activeUtteranceId) return

                    Log.d(TAG, "Speech started: $utteranceId")

                    updateState(EngineState.SPEAKING)
                    listener?.onSpeechStarted()
                }

                override fun onDone(utteranceId: String?) {
                    if (destroyed) return
                    if (utteranceId != activeUtteranceId) return

                    Log.d(TAG, "Speech completed: $utteranceId")

                    activeUtteranceId = null

                    listener?.onSpeechCompleted()

                    resumeAfterSpeech()
                    updateStateAfterSpeech()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (destroyed) return

                    handleSpeechError(
                        "आवाज़ चलाने में समस्या आई।",
                        utteranceId
                    )
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    if (destroyed) return

                    handleSpeechError(
                        "Text-to-Speech error: $errorCode",
                        utteranceId
                    )
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean
                ) {
                    if (destroyed) return

                    Log.d(
                        TAG,
                        "Speech stopped: $utteranceId, interrupted=$interrupted"
                    )

                    if (utteranceId != activeUtteranceId) return

                    activeUtteranceId = null

                    resumeAfterSpeech()
                    updateStateAfterSpeech()
                }
            }
        )

        engineReady = true

        val configurationApplied =
            languageManager.applyTo(engine)

        if (!configurationApplied) {
            Log.w(
                TAG,
                "TTS language or voice configuration unavailable"
            )
        }

        applySpeechRate()

        updateStateAfterSpeech()

        listener?.onEngineReady()

        Log.d(TAG, "TTS engine initialized")

        val pendingText = pendingSpeechText
        pendingSpeechText = null

        if (!pendingText.isNullOrBlank()) {
            speak(pendingText)
        }
    }

    // -------------------------------------------------
    // LANGUAGE AND VOICE CONFIGURATION
    // -------------------------------------------------

    fun setLanguage(language: String) {
        if (destroyed) return

        val applied = languageManager.setLanguage(
            language,
            if (engineReady) tts else null
        )

        Log.d(
            TAG,
            "Language configuration result: $applied"
        )
    }

    fun setVoiceGender(gender: String) {
        if (destroyed) return

        val applied = languageManager.setVoiceGender(
            gender,
            if (engineReady) tts else null
        )

        Log.d(
            TAG,
            "Voice gender configuration result: $applied"
        )
    }

    /**
     * Sets the TTS speech rate.
     *
     * Supported range: 0.5x to 2.0x.
     * 1.0x is the normal speech rate.
     */
    fun setSpeechRate(rate: Float) {
        if (destroyed) return

        if (!rate.isFinite()) {
            Log.w(TAG, "Invalid speech rate ignored: $rate")
            return
        }

        speechRate = rate.coerceIn(
            MIN_SPEECH_RATE,
            MAX_SPEECH_RATE
        )

        if (engineReady) {
            applySpeechRate()
        }

        Log.d(
            TAG,
            "Speech rate selected: ${speechRate}x"
        )
    }

    /**
     * Applies the stored speech rate to the TTS engine.
     */
    private fun applySpeechRate() {
        if (destroyed || !engineReady) return

        val engine = tts ?: return

        val result = engine.setSpeechRate(speechRate)

        if (result == TextToSpeech.ERROR) {
            Log.e(
                TAG,
                "Failed to apply speech rate: ${speechRate}x"
            )
        } else {
            Log.d(
                TAG,
                "Speech rate applied: ${speechRate}x"
            )
        }
    }

    // -------------------------------------------------
    // LISTENING SERVICE COORDINATION
    // -------------------------------------------------

    fun startListening(language: String = recognitionLanguage) {
        if (destroyed) return

        if (language.isBlank()) {
            Log.e(TAG, "Cannot start listening: language is empty")
            return
        }

        recognitionLanguage = language
        listeningRequested = true
        manuallyPaused = false

        voiceManager.start(recognitionLanguage)

        if (activeUtteranceId != null) {
            pausedForSpeech = true
            voiceManager.pause()
            updateState(EngineState.SPEAKING)
        } else {
            pausedForSpeech = false
            updateStateAfterSpeech()
        }

        Log.d(TAG, "Listening requested: $recognitionLanguage")
    }

    /**
     * Manually pauses recognition.
     * TTS playback may continue independently.
     */
    fun pauseListening() {
        if (destroyed) return

        listeningRequested = false
        pausedForSpeech = false
        manuallyPaused = true

        voiceManager.pause()

        updateStateAfterSpeech()

        Log.d(TAG, "Listening manually paused")
    }

    /**
     * Resumes recognition unless TTS is currently speaking.
     */
    fun resumeListening() {
        if (destroyed) return

        listeningRequested = true
        manuallyPaused = false

        if (activeUtteranceId != null) {
            if (!pausedForSpeech) {
                pausedForSpeech = true
                voiceManager.pause()
            }

            updateState(EngineState.SPEAKING)
        } else {
            pausedForSpeech = false

            voiceManager.resume(recognitionLanguage)

            updateStateAfterSpeech()
        }

        Log.d(TAG, "Listening resume requested")
    }

    /**
     * Completely stops voice recognition service.
     */
    fun stopListening() {
        if (destroyed) return

        listeningRequested = false
        pausedForSpeech = false
        manuallyPaused = false

        voiceManager.stop()

        updateStateAfterSpeech()

        Log.d(TAG, "Listening stopped")
    }

    /**
     * Pauses recognition only when TTS needs the microphone.
     */
    private fun pauseForSpeech() {
        if (!listeningRequested) return
        if (pausedForSpeech) return
        if (manuallyPaused) return

        pausedForSpeech = true

        voiceManager.pause()

        Log.d(TAG, "Listening paused for TTS playback")
    }

    /**
     * Resumes recognition after TTS only when
     * listening is still requested and not manually paused.
     */
    private fun resumeAfterSpeech() {
        if (!listeningRequested) return
        if (!pausedForSpeech) return
        if (manuallyPaused) return

        pausedForSpeech = false

        voiceManager.resume(recognitionLanguage)

        Log.d(TAG, "Listening resumed after TTS playback")
    }

    // -------------------------------------------------
    // SPEECH PLAYBACK
    // -------------------------------------------------

    fun speak(text: String) {
        if (destroyed) return

        val cleanText = text.trim()

        if (cleanText.isEmpty()) {
            Log.w(TAG, "Empty speech request ignored")
            return
        }

        if (!engineReady || tts == null) {
            Log.d(TAG, "TTS not ready. Speech request queued.")

            pendingSpeechText = cleanText
            return
        }

        pauseForSpeech()

        val utteranceId = UUID.randomUUID().toString()

        activeUtteranceId = utteranceId

        updateState(EngineState.SPEAKING)

        val result = tts?.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )

        if (result == null || result == TextToSpeech.ERROR) {
            handleSpeechError(
                "Text-to-Speech ने आवाज़ शुरू नहीं की।",
                utteranceId
            )
        } else {
            Log.d(
                TAG,
                "Speech request submitted: $utteranceId"
            )
        }
    }

    fun stopSpeaking() {
        if (destroyed) return

        pendingSpeechText = null

        tts?.stop()

        activeUtteranceId = null

        resumeAfterSpeech()
        updateStateAfterSpeech()

        Log.d(TAG, "Speech playback stopped")
    }

    private fun handleSpeechError(
        message: String,
        utteranceId: String?
    ) {
        if (destroyed) return
        if (utteranceId != activeUtteranceId) return

        Log.e(
            TAG,
            "$message utteranceId=$utteranceId"
        )

        activeUtteranceId = null

        listener?.onSpeechError(message)

        resumeAfterSpeech()
        updateStateAfterSpeech()
    }

    // -------------------------------------------------
    // STATE MANAGEMENT
    // -------------------------------------------------

    private fun updateStateAfterSpeech() {
        if (destroyed) return

        val nextState = when {
            activeUtteranceId != null ->
                EngineState.SPEAKING

            sleeping ->
                EngineState.SLEEPING

            manuallyPaused ->
                EngineState.PAUSED

            listeningRequested && !pausedForSpeech ->
                EngineState.LISTENING

            listeningRequested && pausedForSpeech ->
                EngineState.PAUSED

            else ->
                EngineState.READY
        }

        updateState(nextState)
    }

    private fun updateState(newState: EngineState) {
        if (destroyed && newState != EngineState.DESTROYED) return
        if (currentState == newState) return

        currentState = newState

        Log.d(TAG, "State changed: $newState")

        listener?.onVoiceStateChanged(newState)
    }

    /**
     * Mirrors the sleep state managed by WakeWordController.
     * Sleep does not stop Vosk recognition.
     */
    fun setSleeping(value: Boolean) {
        if (destroyed) return

        sleeping = value

        updateStateAfterSpeech()
    }

    fun getCurrentState(): EngineState {
        return currentState
    }

    fun isEngineReady(): Boolean {
        return engineReady
    }

    fun isSpeaking(): Boolean {
        return activeUtteranceId != null
    }

    // -------------------------------------------------
    // RELEASE
    // -------------------------------------------------

    fun release() {
        if (destroyed) return

        Log.d(TAG, "Releasing VoiceEngine")

        destroyed = true

        listeningRequested = false
        pausedForSpeech = false
        manuallyPaused = false
        sleeping = false

        pendingSpeechText = null
        activeUtteranceId = null

        engineReady = false

        tts?.stop()
        tts?.shutdown()
        tts = null

        languageManager.release()

        currentState = EngineState.DESTROYED

        listener?.onVoiceStateChanged(
            EngineState.DESTROYED
        )

        Log.d(TAG, "VoiceEngine released")
    }
}
