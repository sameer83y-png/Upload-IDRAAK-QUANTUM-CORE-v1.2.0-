
package com.idraak.quantum

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.speech.*
import java.util.Locale

class VoiceForegroundService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var manager: NotificationManager

    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private var activeLanguage = "hi-IN"

    private val restartRunnable = Runnable {
        if (listening) {
            createRecognizer()
        }
    }

    companion object {
        const val CHANNEL = "idraak_voice_channel"
        const val ACTION_STOP = "com.idraak.quantum.STOP_VOICE"
        const val ACTION_RESULT = "com.idraak.quantum.VOICE_RESULT"
        const val EXTRA_TEXT = "recognized_text"
        const val EXTRA_LANGUAGE = "idraak_language"
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL,
                "IDRAAK Microphone",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description =
                "Visible notification while IDRAAK microphone is active"

            manager.createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val stopIntent = Intent(this, VoiceForegroundService::class.java)
            .setAction(ACTION_STOP)

        val pending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            Notification.Builder(this)
        }

        return builder
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

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopListening()
            stopSelf()
            return START_NOT_STICKY
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (listening) {
            return START_STICKY
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

            startListening(intent)

        } catch (_: Exception) {
            stopListening()
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun selectedLanguage(startIntent: Intent?): String {
        val requested = startIntent
            ?.getStringExtra(EXTRA_LANGUAGE)
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        return when {
            requested.contains("hindi") ||
                requested == "hi" ||
                requested == "hi-in" -> "hi-IN"

            requested.contains("urdu") ||
                requested == "ur" ||
                requested == "ur-pk" -> "ur-PK"

            requested.contains("arabic") ||
                requested == "ar" ||
                requested.startsWith("ar-") -> "ar"

            requested.contains("english") ||
                requested == "en" ||
                requested.startsWith("en-") -> "en-IN"

            else -> {
                when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
                    "hi" -> "hi-IN"
                    "ur" -> "ur-PK"
                    "ar" -> "ar"
                    "en" -> "en-IN"
                    else -> "hi-IN"
                }
            }
        }
    }

    private fun startListening(startIntent: Intent?) {
        if (listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        activeLanguage = selectedLanguage(startIntent)
        listening = true

        createRecognizer()
    }

    private fun createRecognizer() {
        if (!listening) return

        try {
            recognizer?.setRecognitionListener(null)
            recognizer?.destroy()
            recognizer = null

            val speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            recognizer = speechRecognizer

            speechRecognizer.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {}

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        scheduleRestart(1000)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val recognizedText = matches?.firstOrNull()

                        if (!recognizedText.isNullOrBlank()) {
                            val resultIntent = Intent(ACTION_RESULT)
                                .setPackage(packageName)
                                .putExtra(EXTRA_TEXT, recognizedText)

                            sendBroadcast(resultIntent)
                        }

                        scheduleRestart(500)
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {}

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

            val speechIntent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    activeLanguage
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    activeLanguage
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

            speechRecognizer.startListening(speechIntent)

        } catch (_: Exception) {
            scheduleRestart(1500)
        }
    }

    private fun scheduleRestart(delay: Long) {
        if (!listening) return

        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delay)
    }

    private fun stopListening() {
        listening = false

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.setRecognitionListener(null)
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
