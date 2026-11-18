package com.idraak.quantum

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class GoogleSpeechRecognizer(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onReady()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(errorCode: Int, message: String)
    }

    companion object {
        private const val TAG = "GoogleSpeechRecognizer"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isListening = false
    private var isDestroyed = false

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(languageTag: String = "hi-IN") {
        mainHandler.post {
            startListeningInternal(languageTag)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startListeningInternal(languageTag: String) {
        if (isDestroyed) return

        if (context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifyError(
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                "Microphone permission is not granted."
            )
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            notifyError(
                SpeechRecognizer.ERROR_CLIENT,
                "Speech recognition service is not available."
            )
            return
        }

        stopListeningInternal()

        try {
            if (speechRecognizer == null) {
                speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(context)

                speechRecognizer?.setRecognitionListener(
                    recognitionListener
                )
            }

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    languageTag
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    languageTag
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )

                putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    false
                )
            }

            isListening = true
            speechRecognizer?.startListening(intent)

            Log.d(TAG, "Started listening: $languageTag")

        } catch (e: Exception) {
            isListening = false
            Log.e(TAG, "Start listening failed", e)

            notifyError(
                SpeechRecognizer.ERROR_CLIENT,
                e.message ?: "Unable to start speech recognition."
            )
        }
    }

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            listener.onReady()
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            isListening = false

            Log.e(TAG, "Recognition error: $error")

            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO ->
                    "Audio recording error."

                SpeechRecognizer.ERROR_CLIENT ->
                    "Speech recognition client error."

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Microphone permission required."

                SpeechRecognizer.ERROR_NETWORK ->
                    "Network error."

                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Network timeout."

                SpeechRecognizer.ERROR_NO_MATCH ->
                    "No speech match found."

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Speech recognizer is busy."

                SpeechRecognizer.ERROR_SERVER ->
                    "Recognition server error."

                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "No speech detected."

                else ->
                    "Unknown recognition error: $error"
            }

            listener.onError(error, message)
        }

        override fun onResults(results: Bundle?) {
            isListening = false

            val matches = results?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )

            val recognizedText = matches
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (recognizedText.isNotEmpty()) {
                Log.d(TAG, "Recognized: $recognizedText")
                listener.onFinalResult(recognizedText)
            } else {
                notifyError(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    "No recognizable speech found."
                )
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )

            val partialText = matches
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (partialText.isNotEmpty()) {
                listener.onPartialResult(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun stopListening() {
        mainHandler.post {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        try {
            if (isListening) {
                speechRecognizer?.stopListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop listening failed", e)
        }

        isListening = false
    }

    fun destroy() {
        mainHandler.post {
            if (isDestroyed) return@post

            isDestroyed = true
            isListening = false

            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Destroy failed", e)
            }

            speechRecognizer = null
            Log.d(TAG, "Recognizer destroyed")
        }
    }

    private fun notifyError(code: Int, message: String) {
        Log.e(TAG, message)
        listener.onError(code, message)
    }
}
