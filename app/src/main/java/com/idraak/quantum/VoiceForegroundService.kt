
package com.idraak.quantum

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceForegroundService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var manager: NotificationManager

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var listening = false

    private var sessionInProgress = false
    private var restartScheduled = false
    private var activeLanguage = "hi-IN"
    private var retryDelay = 1500L

    private val prefs by lazy {
        getSharedPreferences("idraak_voice_service", MODE_PRIVATE)
    }

    companion object {

        const val CHANNEL = "idraak_voice_channel"

        const val ACTION_STOP =
            "com.idraak.quantum.STOP_VOICE"

        const val ACTION_RESULT =
            "com.idraak.quantum.VOICE_RESULT"

        const val ACTION_STATE =
            "com.idraak.quantum.VOICE_STATE"

        const val EXTRA_TEXT = "recognized_text"
        const val EXTRA_LANGUAGE = "idraak_language"
        const val EXTRA_RUNNING = "voice_running"
        const val EXTRA_USER_STOPPED = "voice_user_stopped"

        private const val NOTIFICATION_ID = 42

        private const val PREF_ENABLED = "voice_enabled"
        private const val PREF_LANGUAGE = "voice_language"
    }

    private val restartRunnable = Runnable {
        restartScheduled = false

        if (listening) {
            startRecognitionSession()
        }
    }

    override fun onCreate() {
        super.onCreate()

        manager = getSystemService(
            NotificationManager::class.java
        )

        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL,
                "IDRAAK Microphone",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Visible notification while IDRAAK microphone is active"
            }

            manager.createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {

        val stopIntent = Intent(
            this,
            VoiceForegroundService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val pendingFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }

        val pending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            pendingFlags
        )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL)
            } else {
                Notification.Builder(this)
            }

        return builder
            .setContentTitle("IDRAAK Microphone")
            .setContentText("Voice service active — tap Stop to pause")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop listening",
                pending
            )
            .build()
    }

    private fun startForegroundSafely() {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification()
            )
        }
    }

    private fun sendState(
        running: Boolean,
        userStopped: Boolean
    ) {
        val intent = Intent(ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_RUNNING, running)
            .putExtra(EXTRA_USER_STOPPED, userStopped)

        sendBroadcast(intent)
    }

    private fun sendRecognizedText(text: String) {
        if (text.isBlank()) return

        val intent = Intent(ACTION_RESULT)
            .setPackage(packageName)
            .putExtra(EXTRA_TEXT, text)

        sendBroadcast(intent)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {

            prefs.edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()

            stopListening()
            sendState(false, true)
            stopSelf()

            return START_NOT_STICKY
        }

        if (
            intent == null &&
            !prefs.getBoolean(PREF_ENABLED, false)
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()

            sendState(false, false)
            stopSelf()

            return START_NOT_STICKY
        }

        if (listening) {
            return START_STICKY
        }

        try {
            startForegroundSafely()

            val requestedLanguage =
                intent?.getStringExtra(EXTRA_LANGUAGE)

            activeLanguage =
                if (!requestedLanguage.isNullOrBlank()) {
                    selectedLanguage(requestedLanguage)
                } else {
                    prefs.getString(PREF_LANGUAGE, null)
                        ?.let { selectedLanguage(it) }
                        ?: selectedLanguage(null)
                }

            prefs.edit()
                .putBoolean(PREF_ENABLED, true)
                .putString(PREF_LANGUAGE, activeLanguage)
                .apply()

            retryDelay = 1500L
            listening = true

            createRecognizerOnce()

            if (recognizer == null) {
                throw IllegalStateException(
                    "Speech recognizer could not be created"
                )
            }

            sendState(true, false)
            startRecognitionSession()

        } catch (_: Exception) {

            prefs.edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()

            stopListening()
            sendState(false, false)
            stopSelf()

            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun selectedLanguage(
        requestedValue: String?
    ): String {

        val requested = requestedValue
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

            else -> when (
                Locale.getDefault().language.lowercase(Locale.ROOT)
            ) {
                "hi" -> "hi-IN"
                "ur" -> "ur-PK"
                "ar" -> "ar"
                "en" -> "en-IN"
                else -> "hi-IN"
            }
        }
    }

    private fun createRecognizerOnce() {

        if (recognizer != null) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            throw IllegalStateException(
                "Speech recognition is unavailable"
            )
        }

        val speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer = speechRecognizer

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    retryDelay = 1500L
                    sessionInProgress = true
                    sendState(true, false)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {

                    sessionInProgress = false

                    if (!listening) return

                    if (
                        error ==
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    ) {
                        prefs.edit()
                            .putBoolean(PREF_ENABLED, false)
                            .apply()

                        stopListening()
                        sendState(false, false)
                        stopSelf()

                        return
                    }

                    // Client Error पर खराब Recognizer को बदलना
                    if (
                        error == SpeechRecognizer.ERROR_CLIENT
                    ) {
                        releaseRecognizer()
                    }

                    // Busy Error पर थोड़ी देर रुककर पुनः प्रयास
                    val delay = if (
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    ) {
                        3000L
                    } else {
                        retryDelay
                    }

                    scheduleRestart(delay)

                    retryDelay = (
                        retryDelay * 2
                    ).coerceAtMost(15000L)
                }

                override fun onResults(results: Bundle?) {

                    sessionInProgress = false

                    if (!listening) return

                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val recognizedText = matches?.firstOrNull()

                    if (!recognizedText.isNullOrBlank()) {
                        sendRecognizedText(recognizedText)
                    }

                    retryDelay = 1500L

                    // उसी Recognizer से अगला Session
                    scheduleRestart(700L)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    // Partial text को अभी command की तरह execute नहीं करेंगे
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )
    }

    private fun startRecognitionSession() {

        if (!listening) return
        if (sessionInProgress) return

        if (recognizer == null) {
            try {
                createRecognizerOnce()
            } catch (_: Exception) {
                scheduleRestart(retryDelay)

                retryDelay = (
                    retryDelay * 2
                ).coerceAtMost(15000L)

                return
            }
        }

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()

            stopListening()
            sendState(false, false)
            stopSelf()

            return
        }

        try {

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

            sessionInProgress = true
            recognizer?.startListening(speechIntent)

        } catch (_: Exception) {

            sessionInProgress = false

            scheduleRestart(retryDelay)

            retryDelay = (
                retryDelay * 2
            ).coerceAtMost(15000L)
        }
    }

    private fun scheduleRestart(delay: Long) {

        if (!listening || restartScheduled) return

        restartScheduled = true

        handler.postDelayed(
            restartRunnable,
            delay.coerceAtLeast(500L)
        )
    }

    private fun releaseRecognizer() {

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null
        sessionInProgress = false
    }

    private fun stopListening() {

        listening = false
        sessionInProgress = false
        restartScheduled = false

        handler.removeCallbacks(restartRunnable)

        releaseRecognizer()

        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {

        stopListening()
        sendState(false, false)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
