package com.idraak.quantum

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable

/**
 * IDRAAK Vosk Recognition Engine
 *
 * Responsibilities:
 * - Process continuous PCM audio.
 * - Produce partial recognition results.
 * - Produce final recognition results.
 * - Keep the recognizer ready for the next utterance.
 *
 * Microphone ownership belongs exclusively to
 * VoiceForegroundService.
 */
internal class VoskRecognitionEngine(
    model: Model,
    private val sampleRate: Float = 16000.0f,
    private val listener: Listener
) : Closeable {

    interface Listener {

        fun onPartialResult(text: String)

        fun onFinalResult(text: String)

        fun onRecognitionError(error: Exception)
    }

    private val recognizer: Recognizer

    @Volatile
    private var closed = false

    init {
        require(sampleRate.isFinite() && sampleRate > 0f) {
            "Invalid audio sample rate"
        }

        recognizer = Recognizer(model, sampleRate)
    }

    /**
     * Process PCM 16-bit mono audio.
     */
    @Synchronized
    fun acceptAudio(
        audioData: ByteArray,
        length: Int = audioData.size
    ) {
        if (closed) return

        if (length <= 0 || length > audioData.size) {
            return
        }

        // PCM 16-bit audio must contain complete samples.
        if (length % 2 != 0) {
            notifyError(
                IllegalArgumentException(
                    "Invalid PCM audio length: $length"
                )
            )
            return
        }

        try {
            val completed = recognizer.acceptWaveForm(
                audioData,
                length
            )

            if (completed) {

                val result = recognizer.result

                extractText(result, "text")?.let {
                    notifyFinalResult(it)
                }

            } else {

                val partial = recognizer.partialResult

                extractText(partial, "partial")?.let {
                    notifyPartialResult(it)
                }
            }

        } catch (exception: Exception) {

            notifyError(exception)
        }
    }

    /**
     * Flush remaining audio before final shutdown.
     */
    @Synchronized
    fun finish() {
        if (closed) return

        try {
            val result = recognizer.finalResult

            extractText(result, "text")?.let {
                notifyFinalResult(it)
            }

        } catch (exception: Exception) {

            notifyError(exception)
        }
    }

    /**
     * Reset recognition state without recreating the model.
     */
    @Synchronized
    fun reset() {
        if (closed) return

        try {
            recognizer.reset()
        } catch (exception: Exception) {

            notifyError(exception)
        }
    }

    private fun extractText(
        json: String,
        key: String
    ): String? {

        return try {
            val objectResult = JSONObject(json)

            val text = objectResult.optString(
                key,
                ""
            ).trim()

            text.ifEmpty { null }

        } catch (exception: Exception) {

            notifyError(exception)
            null
        }
    }

    private fun notifyPartialResult(text: String) {
        try {
            listener.onPartialResult(text)
        } catch (exception: Exception) {
            notifyError(exception)
        }
    }

    private fun notifyFinalResult(text: String) {
        try {
            listener.onFinalResult(text)
        } catch (exception: Exception) {
            notifyError(exception)
        }
    }

    private fun notifyError(exception: Exception) {
        try {
            listener.onRecognitionError(exception)
        } catch (_: Exception) {
            // Listener errors must not crash audio processing.
        }
    }

    /**
     * Release native recognizer resources.
     */
    @Synchronized
    override fun close() {
        if (closed) return

        closed = true

        try {
            recognizer.close()
        } catch (exception: Exception) {

            notifyError(exception)
        }
    }
}
