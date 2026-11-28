package com.idraak.quantum.core

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fully local Urdu/Hindi speech recognition for Android 8+.
 * The multilingual model is downloaded once; subsequent recognition needs no network.
 */
class OnDeviceVoiceController(
    context: Context,
    private val onText: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onComplete: (() -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val workerActive = AtomicBoolean(false)
    private val destroyRequested = AtomicBoolean(false)
    private val recognizerLock = Any()
    private val modelManager = OfflineWhisperModelManager(appContext)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var recognizer: OfflineRecognizer? = null

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) {
            onStatus("آواز پہلے سے سنی جا رہی ہے۔")
            return false
        }
        workerActive.set(true)
        Thread({
            try {
                val files = modelManager.prepare { percent ->
                    onStatus("پہلی بار آف لائن وائس ماڈل ڈاؤن لوڈ ہو رہا ہے: $percent%")
                }
                if (!running.get()) return@Thread

                val activeRecognizer = recognizer ?: createRecognizer(files).also {
                    recognizer = it
                }
                if (!running.get()) return@Thread

                onStatus("آف لائن اردو / ہندی وائس تیار ہے۔ اب بولیں۔")
                val samples = recordUntilSilence()
                if (!running.get()) return@Thread
                if (samples.size < MIN_SAMPLES) {
                    onStatus("آواز واضح نہیں ملی۔ دوبارہ کوشش کریں۔")
                    return@Thread
                }

                onStatus("آف لائن آواز سمجھ رہا ہوں…")
                val stream = activeRecognizer.createStream()
                try {
                    stream.acceptWaveform(samples.toFloatArray(), SAMPLE_RATE)
                    activeRecognizer.decode(stream)
                    val result = activeRecognizer.getResult(stream)
                    if (result.text.isNotBlank()) onText(result.text.trim())
                    else onStatus("بات سمجھ نہیں آئی۔ دوبارہ بولیں۔")
                } finally {
                    stream.release()
                }
            } catch (error: Exception) {
                onStatus("آف لائن وائس شروع نہیں ہو سکی: " + (error.message ?: "دوبارہ کوشش کریں۔"))
            } finally {
                releaseRecorder()
                running.set(false)
                workerActive.set(false)
                if (destroyRequested.get()) releaseRecognizer()
                try {
                    onComplete?.invoke()
                } catch (_: Exception) {
                    // Completion notification must not crash the voice worker.
                }
            }
        }, "idraak-offline-whisper").start()
        return true
    }

    private fun createRecognizer(files: OfflineWhisperModelManager.ModelFiles): OfflineRecognizer {
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = files.encoder.absolutePath,
                decoder = files.decoder.absolutePath,
                language = "",
                task = "transcribe",
                tailPaddings = 300
            ),
            tokens = files.tokens.absolutePath,
            numThreads = 2,
            provider = "cpu"
        )
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = model,
            decodingMethod = "greedy_search"
        )
        return OfflineRecognizer(config = config)
    }

    private fun recordUntilSilence(): ArrayList<Float> {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) throw IllegalStateException("مائیکروفون دستیاب نہیں۔")

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, SAMPLE_RATE)
        )
        audioRecord = recorder
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            releaseRecorder()
            throw IllegalStateException("مائیکروفون شروع نہیں ہو سکا۔")
        }

        val collected = ArrayList<Float>(SAMPLE_RATE * 8)
        val buffer = ShortArray(SAMPLE_RATE / 10)
        var speechDetected = false
        var quietSamples = 0
        var totalSamples = 0
        val silenceLimit = SAMPLE_RATE * SILENCE_SECONDS

        recorder.startRecording()
        try {
            while (running.get() && totalSamples < SAMPLE_RATE * MAX_RECORD_SECONDS) {
                val count = recorder.read(buffer, 0, buffer.size)
                when (AudioReadPolicy.classify(count)) {
                    AudioReadPolicy.Outcome.FAILURE ->
                        throw IllegalStateException("مائیکروفون سے آواز پڑھنے میں خرابی ہوئی۔")
                    AudioReadPolicy.Outcome.RETRY -> continue
                    AudioReadPolicy.Outcome.PROCESS -> Unit
                }

                var energy = 0.0
                for (index in 0 until count) {
                    val value = buffer[index].toFloat() / 32768.0f
                    collected.add(value)
                    energy += value * value
                }
                totalSamples += count

                val rms = kotlin.math.sqrt(energy / count)
                if (rms >= SPEECH_RMS_THRESHOLD) {
                    speechDetected = true
                    quietSamples = 0
                } else if (speechDetected) {
                    quietSamples += count
                    if (quietSamples >= silenceLimit) break
                }
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) { }
            recorder.release()
            if (audioRecord === recorder) audioRecord = null
        }

        return if (speechDetected) collected else arrayListOf()
    }

    private fun releaseRecorder() {
        val recorder = audioRecord
        audioRecord = null
        if (recorder != null) {
            try { recorder.stop() } catch (_: Exception) { }
            try { recorder.release() } catch (_: Exception) { }
        }
    }

    fun destroy() {
        destroyRequested.set(true)
        running.set(false)
        // Wake a blocking read; the worker's finally block remains the sole owner of release().
        try { audioRecord?.stop() } catch (_: Exception) { }
        // Native recognizer memory must be released, but never while decode() is using it.
        if (!workerActive.get()) releaseRecognizer()
    }

    private fun releaseRecognizer() {
        synchronized(recognizerLock) {
            val active = recognizer
            recognizer = null
            try {
                active?.release()
            } catch (_: Exception) {
                // Cleanup must not crash the activity or foreground voice service.
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MIN_SAMPLES = 4_800
        private const val SILENCE_SECONDS = 1.2
        private const val MAX_RECORD_SECONDS = 15
        private const val SPEECH_RMS_THRESHOLD = 0.012
    }
}
