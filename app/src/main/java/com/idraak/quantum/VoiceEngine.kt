
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
 * केवल जिम्मेदारियाँ:
 * - TextToSpeech initialization
 * - Text-to-Speech playback
 * - Speech callbacks
 * - Playback state management
 *
 * भाषा और Voice Selection:
 * TtsLanguageManager
 *
 * Recognition service commands:
 * VoiceManager
 *
 * Speech Recognition:
 * VoiceForegroundService
 *
 * Wake Word:
 * WakeWordController
 */
class VoiceEngine(
    context: Context,
    private val voiceManager: VoiceManager,
    private val listener: Listener? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "IDRAAK_VoiceEngine"
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

    // Language configuration is delegated to its own class.
    private val languageManager =
        TtsLanguageManager(appContext)

    private var tts: TextToSpeech? = null

    private var engineReady = false
    private var destroyed = false

    private var listeningRequested = false
    private var pausedForSpeech = false

    private var recognitionLanguage = "hi-IN"
    private var activeUtteranceId: String? = null

    private var currentState = EngineState.INITIALIZING
        private set

    init {
        Log.d(TAG, "Initializing TTS engine")
        tts = TextToSpeech(appContext, this)
    }

    // -------------------------------------------------
    // TTS Initialization
    // -------------------------------------------------

    override fun onInit(status: Int) {
        if (destroyed) return

        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS initialization failed: $status")
            engineReady = false
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

                    Log.d(TAG, "Speech started: $utteranceId")

                    updateState(EngineState.SPEAKING)
                    listener?.onSpeechStarted()
                }

                override fun onDone(utteranceId: String?) {
                    if (destroyed) return

                    Log.d(TAG, "Speech completed: $utteranceId")

                    if (utteranceId != activeUtteranceId) return

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

        // Language and voice configuration is handled externally.
        val configurationApplied =
            languageManager.applyTo(engine)

        if (!configurationApplied) {
            Log.w(
                TAG,
                "TTS language or voice configuration unavailable"
            )
        }

        updateState(EngineState.READY)

        listener?.onEngineReady()

        Log.d(TAG, "TTS engine initialized")
    }

    // -------------------------------------------------
    // Language Manager Delegation
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

    // -------------------------------------------------
    // Listening Service Coordination
    // -------------------------------------------------

    fun startListening(language: String = recognitionLanguage) {
        if (destroyed) return

        recognitionLanguage = language
        listeningRequested = true
        pausedForSpeech = false

        voiceManager.start(recognitionLanguage)

        updateState(EngineState.LISTENING)

        Log.d(TAG, "Listening started: $recognitionLanguage")
    }

    fun pauseListening() {
        if (destroyed) return

        listeningRequested = false
        pausedForSpeech = false

        voiceManager.pause()

        updateState(EngineState.PAUSED)

        Log.d(TAG, "Listening paused")
    }

    fun resumeListening() {
        if (destroyed) return

        listeningRequested = true
        pausedForSpeech = false

        voiceManager.resume(recognitionLanguage)

        updateState(EngineState.LISTENING)

        Log.d(TAG, "Listening resumed")
    }

    fun stopListening() {
        if (destroyed) return

        listeningRequested = false
        pausedForSpeech = false

        voiceManager.stop()

        updateState(EngineState.READY)

        Log.d(TAG, "Listening stopped")
    }

    private fun pauseForSpeech() {
        if (!listeningRequested || pausedForSpeech) return

        pausedForSpeech = true
        voiceManager.pause()

        Log.d(TAG, "Listening paused for TTS playback")
    }

    private fun resumeAfterSpeech() {
        if (!listeningRequested || !pausedForSpeech) return

        pausedForSpeech = false
        voiceManager.resume(recognitionLanguage)

        Log.d(TAG, "Listening resumed after TTS playback")
    }

    // -------------------------------------------------
    // Speech Playback
    // -------------------------------------------------

    fun speak(text: String) {
        if (destroyed) return

        val cleanText = text.trim()

        if (cleanText.isEmpty()) {
            Log.w(TAG, "Empty speech request ignored")
            return
        }

        if (!engineReady || tts == null) {
            Log.w(TAG, "TTS engine is not ready")

            listener?.onSpeechError(
                "आवाज़ का इंजन अभी तैयार नहीं है।"
            )
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

        if (result == TextToSpeech.ERROR) {
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

        Log.e(
            TAG,
            "$message utteranceId=$utteranceId"
        )

        if (utteranceId != activeUtteranceId) return

        activeUtteranceId = null

        listener?.onSpeechError(message)

        resumeAfterSpeech()
        updateStateAfterSpeech()
    }

    // -------------------------------------------------
    // State Management
    // -------------------------------------------------

    private fun updateStateAfterSpeech() {
        if (destroyed) return

        val nextState = when {
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

    fun setSleeping(sleeping: Boolean) {
        if (destroyed) return

        if (sleeping) {
            updateState(EngineState.SLEEPING)
        } else {
            updateStateAfterSpeech()
        }
    }

    fun getCurrentState(): EngineState {
        return currentState
    }

    fun isEngineReady(): Boolean {
        return engineReady
    }

    fun isSpeaking(): Boolean {
        return currentState == EngineState.SPEAKING
    }

    // -------------------------------------------------
    // Release
    // -------------------------------------------------

    fun release() {
        if (destroyed) return

        Log.d(TAG, "Releasing VoiceEngine")

        destroyed = true

        listeningRequested = false
        pausedForSpeech = false
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
