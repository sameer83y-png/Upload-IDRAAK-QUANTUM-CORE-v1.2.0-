package com.idraak.quantum.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Voice input compatible with Android 8.0+.
 * Prefer the on-device recognizer when the OS exposes it; otherwise use the
 * installed speech service and request offline recognition where supported.
 */
class OnDeviceVoiceController(
    context: Context,
    private val onText: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    fun start(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onStatus("اس فون پر وائس ریکگنیشن سروس دستیاب نہیں۔")
            return false
        }

        return try {
            destroy()
            val useOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            recognizer = if (useOnDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }

            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onStatus("سن رہا ہوں… اردو میں اپنا حکم بولیں۔")
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    onStatus("آواز سمجھ رہا ہوں…")
                }
                override fun onError(error: Int) {
                    onStatus(when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "بات واضح نہیں ہوئی، دوبارہ بولیں۔"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "آواز نہیں ملی، دوبارہ کوشش کریں۔"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "اردو وائس پیک دستیاب نہیں۔ فون کی زبان کی سیٹنگز چیک کریں۔"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "مائیکروفون کی اجازت درکار ہے۔"
                        else -> "آواز شناخت نہیں ہو سکی۔ دوبارہ کوشش کریں۔"
                    })
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (spoken.isNotBlank()) onText(spoken)
                    else onStatus("بات واضح نہیں ہوئی، دوبارہ بولیں۔")
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ur-PK")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            recognizer?.startListening(intent)
            true
        } catch (error: Exception) {
            destroy()
            onStatus("وائس سروس شروع نہیں ہو سکی۔ دوبارہ کوشش کریں۔")
            false
        }
    }

    fun destroy() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
