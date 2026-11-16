
package com.idraak.quantum

import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
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
        require(sampleRate > 0f) {
            "Invalid audio sample rate"
        }

        recognizer = Recognizer(model, sampleRate)
    }

    /**
     * Send PCM 16-bit mono audio to Vosk.
     *
     * Audio must be sampled at the same
     * rate used to create this recognizer.
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

        try {
            val completed = recognizer.acceptWaveForm(
                audioData,
                length
            )

            if (completed) {
                val result = recognizer.result
                extractText(result)?.let {
                    listener.onFinalResult(it)
                }
            } else {
                val partial = recognizer.partialResult
                extractText(partial)?.let {
                    listener.onPartialResult(it)
                }
            }

        } catch (exception: Exception) {
            listener.onRecognitionError(exception)
        }
    }

    /**
     * Flush remaining audio before stopping.
     */
    @Synchronized
    fun finish() {
        if (closed) return

        try {
            val result = recognizer.finalResult

            extractText(result)?.let {
                listener.onFinalResult(it)
            }

        } catch (exception: Exception) {
            listener.onRecognitionError(exception)
        }
    }

    /**
     * Reset recognition state without
     * recreating the model.
     */
    @Synchronized
    fun reset() {
        if (closed) return

        try {
            recognizer.reset()
        } catch (exception: Exception) {
            listener.onRecognitionError(exception)
        }
    }

    private fun extractText(json: String): String? {
        return try {
            val objectResult = JSONObject(json)

            val text = objectResult.optString(
                "text",
                ""
            ).trim()

            text.ifEmpty { null }

        } catch (_: Exception) {
            null
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
            listener.onRecognitionError(exception)
        }
    }
}
