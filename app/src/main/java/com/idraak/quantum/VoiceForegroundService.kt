package com.idraak.quantum

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import java.util.Locale

class VoiceForegroundService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var manager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    companion object {
        const val CHANNEL = "idraak_voice_channel"
        const val ACTION_STOP = "com.idraak.quantum.STOP_VOICE"
        const val ACTION_RESULT = "com.idraak.quantum.VOICE_RESULT"
        const val EXTRA_TEXT = "recognized_text"
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            "IDRAAK Microphone",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Visible notification while IDRAAK microphone is active"
        manager.createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val stopIntent = Intent(this, VoiceForegroundService::class.java)
            .setAction(ACTION_STOP)

        val pending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL)
            .setContentTitle("IDRAAK is listening")
            .setContentText("Microphone active — tap Stop to pause")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop listening",
                pending
            )
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListening()
            stopSelf()
            return START_NOT_STICKY
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    42,
                    notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(42, notification())
            }
            startListening()
        } catch (_: Exception) {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startListening() {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }

        listening = true
        createRecognizer()
    }

    private fun createRecognizer() {
        if (!listening) return

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (listening) {
                        handler.postDelayed({ createRecognizer() }, 900)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    val text = matches?.firstOrNull()

                    if (!text.isNullOrBlank()) {
                        sendBroadcast(
                            Intent(ACTION_RESULT).putExtra(EXTRA_TEXT, text)
                        )
                    }

                    if (listening) {
                        handler.postDelayed({ createRecognizer() }, 500)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault().toLanguageTag()
                )
            }

            recognizer?.startListening(intent)

        } catch (_: Exception) {
            stopListening()
        }
    }

    private fun stopListening() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
