package com.idraak.quantum.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.idraak.quantum.MainActivity
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-started foreground microphone service. It listens only for the configured wake phrase,
 * then hands the microphone to the existing offline Whisper recognizer for one command.
 */
class IdraakWakeWordService : Service() {
    @Volatile private var active = false
    private val stopping = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var spotter: KeywordSpotter? = null
    @Volatile private var wakeStream: com.k2fsa.sherpa.onnx.OnlineStream? = null
    @Volatile private var voiceController: OnDeviceVoiceController? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var pendingSpeech: String? = null
    private val speechPending = AtomicBoolean(false)
    private val restartRequested = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val supportedLocales = listOf(
                    Locale("ur", "PK"),
                    Locale("hi", "IN"),
                    Locale.US
                )
                ttsReady = false
                for (locale in supportedLocales) {
                    val result = tts?.setLanguage(locale)
                    if (result != null &&
                        result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        ttsReady = true
                        break
                    }
                }
                if (!ttsReady) {
                    updateNotification("Speech voice unavailable. Install a supported Urdu, Hindi, or English TTS voice.")
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        finishSpeechAndResumeListening()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finishSpeechAndResumeListening()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        finishSpeechAndResumeListening()
                    }
                })
                speakPendingIfReady()
            } else {
                ttsReady = false
                pendingSpeech = null
                speechPending.set(false)
                updateNotification("Text-to-speech is unavailable on this device.")
                if (restartRequested.get()) restartWakeWordListening()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAssistant()
            return START_NOT_STICKY
        }
        if (active) return START_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        stopping.set(false)
        Thread({ listenForWakeWord() }, "idraak-wake-word").start()
        return START_STICKY
    }

    private fun listenForWakeWord() {
        var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
        var kws: KeywordSpotter? = null
        try {
            val files = WakeWordModelManager(this).prepare()
            if (stopping.get()) return
            val model = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = files.encoder.absolutePath,
                    decoder = files.decoder.absolutePath,
                    joiner = files.joiner.absolutePath
                ),
                tokens = files.tokens.absolutePath,
                numThreads = 2,
                provider = "cpu",
                modelType = "zipformer2"
            )
            val createdSpotter = KeywordSpotter(config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = model,
                keywordsFile = files.keywords.absolutePath,
                keywordsScore = 1.5f,
                keywordsThreshold = 0.25f
            ))
            kws = createdSpotter
            spotter = createdSpotter
            val activeSpotter = createdSpotter
            val createdStream = activeSpotter.createStream("Idraak")
                ?: throw IllegalStateException("Wake phrase stream could not be created.")
            stream = createdStream
            wakeStream = createdStream
            val activeStream = createdStream
            if (activeStream.ptr == 0L) throw IllegalStateException("Wake phrase could not be configured.")
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) throw IllegalStateException("Microphone is unavailable.")
            val audio = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer * 2, SAMPLE_RATE)
            )
            recorder = audio
            if (audio.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone could not start.")
            audio.startRecording()
            updateNotification("Idraak is listening for its wake phrase")
            val buffer = ShortArray(SAMPLE_RATE / 10)
            while (!stopping.get()) {
                val count = audio.read(buffer, 0, buffer.size)
                if (count <= 0) continue
                val samples = FloatArray(count) { buffer[it] / 32768.0f }
                activeStream.acceptWaveform(samples, SAMPLE_RATE)
                while (activeSpotter.isReady(activeStream)) {
                    activeSpotter.decode(activeStream)
                    val keyword = activeSpotter.getResult(activeStream).keyword
                    if (keyword.isNotBlank()) {
                        activeSpotter.reset(activeStream)
                        updateNotification("Idraak woke up. Speak your command.")
                        stopWakeRecorder(audio, activeStream, activeSpotter)
                        recorder = null
                        wakeStream = null
                        stream = null
                        spotter = null
                        activeSpotter.release()
                        kws = null
                        captureCommand()
                        return
                    }
                }
            }
        } catch (error: Exception) {
            if (!stopping.get()) updateNotification("Voice service stopped: " + (error.message ?: "microphone error"))
        } finally {
            stream?.let { try { it.release() } catch (_: Exception) { } }
            val audio = recorder
            if (audio != null) {
                try { audio.stop() } catch (_: Exception) { }
                try { audio.release() } catch (_: Exception) { }
                recorder = null
            }
            kws?.let { try { it.release() } catch (_: Exception) { } }
            wakeStream = null
            spotter = null
            if (!stopping.get() && active && voiceController == null) {
                // If initialization fails, keep the foreground service visible but do not spin/retry.
                stopAssistant()
            }
        }
    }

    private fun stopWakeRecorder(
        audio: AudioRecord,
        stream: com.k2fsa.sherpa.onnx.OnlineStream,
        kws: KeywordSpotter
    ) {
        try { audio.stop() } catch (_: Exception) { }
        try { audio.release() } catch (_: Exception) { }
        try { stream.release() } catch (_: Exception) { }
    }

    private fun captureCommand() {
        if (stopping.get()) return
        val controller = OnDeviceVoiceController(
            this,
            onText = { text ->
                if (text.isNotBlank() && !stopping.get()) {
                    val processor = IdraakCommandProcessor(applicationContext)
                    val processed = try {
                        processor.process(text)
                    } catch (error: Exception) {
                        IdraakProcessingResult(
                            false,
                            "کمانڈ پروسیس نہیں ہو سکی: " + (error.localizedMessage ?: "unknown error")
                        )
                    }
                    val reply = processed.response
                    if (!processed.handled) processor.recordExchange(text, reply)
                    processor.close()
                    speak(reply)
                    updateNotification("Command processed. Listening for 'Idraak' again.")
                }
            },
            onStatus = { status ->
                if (!stopping.get()) updateNotification(status)
            },
            onComplete = {
                voiceController?.destroy()
                voiceController = null
                if (!stopping.get()) {
                    restartRequested.set(true)
                    if (!speechPending.get()) restartWakeWordListening()
                }
            }
        )
        voiceController = controller
        controller.start()
    }

    @Synchronized
    private fun speak(message: String) {
        if (stopping.get()) return
        val engine = tts
        if (engine == null) {
            pendingSpeech = message
            speechPending.set(true)
            return
        }
        if (!ttsReady) {
            pendingSpeech = message
            speechPending.set(true)
            return
        }
        pendingSpeech = null
        speechPending.set(true)
        val result = engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "idraak-background-reply")
        if (result == TextToSpeech.ERROR) finishSpeechAndResumeListening()
    }

    @Synchronized
    private fun speakPendingIfReady() {
        if (stopping.get() || !ttsReady) return
        val message = pendingSpeech ?: return
        pendingSpeech = null
        speak(message)
    }

    private fun finishSpeechAndResumeListening() {
        speechPending.set(false)
        if (restartRequested.get() && !stopping.get()) {
            restartWakeWordListening()
        }
    }

    private fun restartWakeWordListening() {
        if (stopping.get() || !active || speechPending.get()) return
        if (!restartRequested.compareAndSet(true, false)) return
        Thread({ listenForWakeWord() }, "idraak-wake-restart").start()
    }

    private fun stopAssistant() {
        if (!stopping.compareAndSet(false, true)) return
        active = false
        voiceController?.destroy()
        voiceController = null
        // The wake-word worker owns AudioRecord, OnlineStream, and KeywordSpotter.
        // Stop the blocking read here, but let the worker's finally block release native
        // resources exactly once after it has exited the decode/read loop.
        try { recorder?.stop() } catch (_: Exception) { }
        pendingSpeech = null
        speechPending.set(false)
        tts?.stop()
        stopForeground(true)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Idraak background voice", NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, IdraakWakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Idraak voice assistant is active")
            .setContentText("Listening for 'Idraak' • Microphone active")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stop)
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Idraak voice assistant")
            .setContentText(message.take(120))
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening",
                PendingIntent.getService(this, 1, Intent(this, IdraakWakeWordService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopAssistant()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.idraak.quantum.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.idraak.quantum.action.STOP_WAKE_WORD"
        private const val CHANNEL_ID = "idraak_background_voice"
        private const val NOTIFICATION_ID = 7301
        private const val SAMPLE_RATE = 16000
    }
}
