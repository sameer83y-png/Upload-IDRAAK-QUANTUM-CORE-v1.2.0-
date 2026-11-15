
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
 * जिम्मेदारी:
 * - Text-to-Speech इंजन initialize करना
 * - दिए गए टेक्स्ट को आवाज़ में बदलना
 * - Speech playback callbacks संभालना
 * - बोलते समय listening service को अस्थायी रूप से pause/resume करना
 *
 * भाषा और Voice Selection का logic TtsLanguageManager में रहेगा।
 * Speech Recognition का logic VoiceForegroundService में रहेगा।
 * Wake Word का logic WakeWordController में रहेगा।
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

    /*
     * भाषा और Voice Selection की जिम्मेदारी अलग क्लास की है।
     * इस फाइल में Locale mapping या Voice selection नहीं होगी।
     */
    private val languageManager = TtsLanguageManager(appContext)

    private var tts: TextToSpeech? = null

    private var engineReady = false
    private var listeningRequested = false
    private var pausedForSpeech = false

    private var recognitionLanguage = "hi-IN"
    private var activeUtteranceId: String? = null

    private var currentState = EngineState.INITIALIZING
        private set

    init {
        Log.d(TAG, "Initializing TextToSpeech engine")
        tts = TextToSpeech(appContext, this)
    }

    // -------------------------------------------------
    // TextToSpeech initialization
    // -------------------------------------------------

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TextToSpeech initialization failed: $status")
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
                    Log.d(TAG, "Speech started: $utteranceId")
                    updateState(EngineState.SPEAKING)
                    listener?.onSpeechStarted()
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Speech completed: $utteranceId")

                    if (utteranceId == activeUtteranceId) {
                        activeUtteranceId = null
                    }

                    listener?.onSpeechCompleted()
                    resumeAfterSpeech()
                    updateStateAfterSpeech()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handleSpeechError(
                        "आवाज़ चलाने में समस्या आई।",
                        utteranceId
                    )
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    handleSpeechError(
                        "Text-to-Speech error: $errorCode",
                        utteranceId
                    )
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean
                ) {
                    Log.d(
                        TAG,
                        "Speech stopped: $utteranceId, interrupted=$interrupted"
                    )

                    if (utteranceId == activeUtteranceId) {
                        activeUtteranceId = null
                    }

                    resumeAfterSpeech()
                    updateStateAfterSpeech()
                }
            }
        )

        engineReady = true

        /*
         * TTS की भाषा और Voice configuration अलग manager संभालेगा।
         * यहाँ केवल उसे configuration लागू करने के लिए कहा जाता है।
         */
        val settingsApplied = languageManager.applyTo(engine)

        if (!settingsApplied) {
            Log.w(
                TAG,
                "TTS language/voice configuration is not ready"
            )
        }

        updateState(EngineState.READY)
        listener?.onEngineReady()

        Log.d(TAG, "TextToSpeech engine initialized")
    }

    // -------------------------------------------------
    // Language configuration delegation
    // -------------------------------------------------

    /**
     * भाषा बदलने का वास्तविक logic TtsLanguageManager में है।
     */
    fun setLanguage(language: String) {
        val engine = if (engineReady) tts else null
        val applied = languageManager.setLanguage(language, engine)

        Log.d(
            TAG,
            "Language configuration requested; applied=$applied"
        )
    }

    /**
     * Voice gender चुनने का वास्तविक logic TtsLanguageManager में है।
     */
    fun setVoiceGender(gender: String) {
        val engine = if (engineReady) tts else null
        val applied = languageManager.setVoiceGender(gender, engine)

        Log.d(
            TAG,
            "Voice gender configuration requested; applied=$applied"
        )
    }

    // -------------------------------------------------
    // Listening service coordination
    // -------------------------------------------------

    /**
     * Speech Recognition शुरू करने का निर्देश VoiceManager को देता है।
     * Recognition का implementation इस फाइल में नहीं है।
     */
    fun startListening(language: String = recognitionLanguage) {
        if (currentState == EngineState.DESTROYED) return

        recognitionLanguage = language
        listeningRequested = true
        pausedForSpeech = false

        voiceManager.start(recognitionLanguage)

        updateState(EngineState.LISTENING)

        Log.d(TAG, "Listening requested: $recognitionLanguage")
    }

    fun pauseListening() {
        if (currentState == EngineState.DESTROYED) return

        listeningRequested = false
        pausedForSpeech = false

        voiceManager.pause()
        updateState(EngineState.PAUSED)

        Log.d(TAG, "Listening paused")
    }

    fun resumeListening() {
        if (currentState == EngineState.DESTROYED) return

        listeningRequested = true
        pausedForSpeech = false

        voiceManager.resume(recognitionLanguage)
        updateState(EngineState.LISTENING)

        Log.d(TAG, "Listening resumed")
    }

    fun stopListening() {
        if (currentState == EngineState.DESTROYED) return

        listeningRequested = false
        pausedForSpeech = false

        voiceManager.stop()
        updateState(EngineState.READY)

        Log.d(TAG, "Listening stopped")
    }

    /**
     * TTS बोलने से पहले Recognition service को अस्थायी रूप से रोकता है।
     */
    private fun pauseForSpeech() {
        if (!listeningRequested || pausedForSpeech) return

        pausedForSpeech = true
        voiceManager.pause()

        Log.d(TAG, "Listening paused for speech playback")
    }

    /**
     * TTS पूरा होने पर Recognition service फिर शुरू करता है।
     */
    private fun resumeAfterSpeech() {
        if (!listeningRequested || !pausedForSpeech) return

        pausedForSpeech = false
        voiceManager.resume(recognitionLanguage)

        Log.d(TAG, "Listening resumed after speech playback")
    }

    // -------------------------------------------------
    // Speech playback
    // -------------------------------------------------

    fun speak(text: String) {
        val cleanText = text.trim()

        if (cleanText.isEmpty()) {
            Log.w(TAG, "Empty speech request ignored")
            return
        }

        if (currentState == EngineState.DESTROYED) {
            Log.w(TAG, "Speech request ignored: engine destroyed")
            return
        }

        if (!engineReady || tts == null) {
            Log.w(TAG, "Speech request ignored: TTS not ready")
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
            Log.d(TAG, "Speech request submitted: $utteranceId")
        }
    }

    fun stopSpeaking() {
        if (currentState == EngineState.DESTROYED) return

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
        Log.e(TAG, "$message utteranceId=$utteranceId")

        if (utteranceId == activeUtteranceId) {
            activeUtteranceId = null
        }

        listener?.onSpeechError(message)

        resumeAfterSpeech()
        updateStateAfterSpeech()
    }

    // -------------------------------------------------
    // State handling
    // -------------------------------------------------

    private fun updateStateAfterSpeech() {
        if (currentState == EngineState.DESTROYED) return

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
        if (currentState == EngineState.DESTROYED) return

        if (currentState == newState) return

        currentState = newState

        Log.d(TAG, "Voice state changed: $newState")
        listener?.onVoiceStateChanged(newState)
    }

    /**
     * केवल Voice Engine की state बदलता है।
     * Wake Word या wake/sleep detection यहाँ नहीं होता।
     */
    fun setSleeping(sleeping: Boolean) {
        if (currentState == EngineState.DESTROYED) return

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
        if (currentState == EngineState.DESTROYED) return

        Log.d(TAG, "Releasing VoiceEngine")

        listeningRequested = false
        pausedForSpeech = false
        activeUtteranceId = null

        engineReady = false

        tts?.stop()
        tts?.shutdown()
        tts = null

        updateState(EngineState.DESTROYED)

        Log.d(TAG, "VoiceEngine released")
    }
}
