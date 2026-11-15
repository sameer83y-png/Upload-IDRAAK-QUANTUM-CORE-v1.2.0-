
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
import android.util.Log
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
    private var recognizerGeneration = 0

    // Explicit STOP और temporary PAUSE को अलग रखता है।
    private var userStopped = false

    private val prefs by lazy {
        getSharedPreferences("idraak_voice_service", MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "IDRAAK_VOICE"

        const val CHANNEL = "idraak_voice_channel"

        const val ACTION_STOP =
            "com.idraak.quantum.STOP_VOICE"

        const val ACTION_RESULT =
            "com.idraak.quantum.VOICE_RESULT"

        const val ACTION_STATE =
            "com.idraak.quantum.VOICE_STATE"

        const val ACTION_PAUSE =
            "com.idraak.quantum.PAUSE_VOICE"

        const val ACTION_RESUME =
            "com.idraak.quantum.RESUME_VOICE"

        const val ACTION_ERROR =
            "com.idraak.quantum.VOICE_ERROR"

        const val EXTRA_TEXT = "recognized_text"
        const val EXTRA_LANGUAGE = "idraak_language"
        const val EXTRA_RUNNING = "voice_running"
        const val EXTRA_USER_STOPPED = "voice_user_stopped"

        const val EXTRA_ERROR_CODE = "voice_error_code"
        const val EXTRA_ERROR_MESSAGE = "voice_error_message"

        private const val NOTIFICATION_ID = 42
        private const val PREF_ENABLED = "voice_enabled"
        private const val PREF_LANGUAGE = "voice_language"
    }

    private val restartRunnable = Runnable {
        restartScheduled = false

        if (listening && !sessionInProgress) {
            Log.d(TAG, "Restarting recognition session")
            startRecognitionSession()
        }
    }

    override fun onCreate() {
        super.onCreate()

        manager = getSystemService(NotificationManager::class.java)
        createChannel()

        Log.d(TAG, "VoiceForegroundService created")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL,
                "IDRAAK Microphone",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Visible notification while IDRAAK voice service is active"
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
            .setContentTitle("IDRAAK Voice Service")
            .setContentText("Voice service active")
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

        Log.d(TAG, "Foreground service started")
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

        Log.d(
            TAG,
            "Voice state: running=$running, userStopped=$userStopped"
        )
    }

    private fun sendError(
        code: Int,
        message: String
    ) {
        Log.e(TAG, "Voice error: code=$code, message=$message")

        val intent = Intent(ACTION_ERROR)
            .setPackage(packageName)
            .putExtra(EXTRA_ERROR_CODE, code)
            .putExtra(EXTRA_ERROR_MESSAGE, message)

        sendBroadcast(intent)
    }

    private fun sendRecognizedText(text: String) {
        if (text.isBlank()) return

        Log.i(TAG, "Speech recognized: $text")

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

        Log.d(
            TAG,
            "onStartCommand: action=${intent?.action ?: "START"}"
        )

        when (intent?.action) {

            ACTION_STOP -> {
                userStopped = true

                prefs.edit()
                    .putBoolean(PREF_ENABLED, false)
                    .apply()

                stopListening()
                sendState(false, true)
                stopSelf()

                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                Log.d(TAG, "Pause requested")

                pauseListening()

                return START_STICKY
            }

            ACTION_RESUME -> {
                userStopped = false

                if (!hasAudioPermission()) {
                    prefs.edit()
                        .putBoolean(PREF_ENABLED, false)
                        .apply()

                    stopListening()

                    sendError(
                        -1,
                        "RECORD_AUDIO permission is not granted"
                    )

                    sendState(false, false)
                    stopSelf()

                    return START_NOT_STICKY
                }

                try {
                    val requestedLanguage =
                        intent.getStringExtra(EXTRA_LANGUAGE)

                    val newLanguage =
                        if (!requestedLanguage.isNullOrBlank()) {
                            selectedLanguage(requestedLanguage)
                        } else {
                            prefs.getString(PREF_LANGUAGE, null)
                                ?.let { selectedLanguage(it) }
                                ?: activeLanguage
                        }

                    // भाषा बदलने पर पुराने recognizer को रोकना जरूरी है।
                    if (
                        listening &&
                        activeLanguage != newLanguage
                    ) {
                        Log.d(
                            TAG,
                            "Language changed: $activeLanguage -> $newLanguage"
                        )

                        pauseListening()
                    }

                    activeLanguage = newLanguage

                    prefs.edit()
                        .putString(PREF_LANGUAGE, activeLanguage)
                        .putBoolean(PREF_ENABLED, true)
                        .apply()

                    startForegroundSafely()

                    retryDelay = 1500L

                    resumeListening()

                } catch (e: Exception) {
                    Log.e(TAG, "Resume failed", e)

                    stopListening()

                    prefs.edit()
                        .putBoolean(PREF_ENABLED, false)
                        .apply()

                    sendError(
                        -2,
                        "Resume failed: ${e.message ?: e.javaClass.simpleName}"
                    )

                    sendState(false, false)
                    stopSelf()

                    return START_NOT_STICKY
                }

                return START_STICKY
            }
        }

        if (
            intent == null &&
            !prefs.getBoolean(PREF_ENABLED, false)
        ) {
            Log.d(TAG, "Service restart ignored: voice is disabled")

            stopSelf()

            return START_NOT_STICKY
        }

        if (!hasAudioPermission()) {
            prefs.edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()

            stopListening()

            sendError(
                -1,
                "RECORD_AUDIO permission is not granted"
            )

            sendState(false, false)
            stopSelf()

            return START_NOT_STICKY
        }

        userStopped = false

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

            Log.d(TAG, "Starting recognition: $activeLanguage")

            resumeListening()

        } catch (e: Exception) {
            Log.e(TAG, "Service startup failed", e)

            stopListening()

            prefs.edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()

            sendError(
                -2,
                "Service startup failed: ${e.message ?: e.javaClass.simpleName}"
            )

            sendState(false, false)
            stopSelf()

            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun hasAudioPermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun selectedLanguage(requestedValue: String?): String {
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

    private fun speechErrorName(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "ERROR_SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                "ERROR_LANGUAGE_NOT_SUPPORTED"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "ERROR_LANGUAGE_UNAVAILABLE"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                "ERROR_TOO_MANY_REQUESTS"
            else -> "UNKNOWN_SPEECH_ERROR"
        }
    }

    private fun createRecognizerOnce() {
        if (recognizer != null) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            throw IllegalStateException(
                "Speech recognition is unavailable on this device"
            )
        }

        val generation = ++recognizerGeneration

        Log.d(
            TAG,
            "Creating SpeechRecognizer: generation=$generation"
        )

        val speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer = speechRecognizer

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {

                private fun isCurrentSession(): Boolean {
                    return generation == recognizerGeneration &&
                        recognizer === speechRecognizer
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrentSession() || !listening) return

                    Log.i(TAG, "Recognizer READY")

                    retryDelay = 1500L
                    sessionInProgress = true

                    sendState(true, false)
                }

                override fun onBeginningOfSpeech() {
                    if (!isCurrentSession()) return

                    Log.i(TAG, "Speech input detected")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isCurrentSession()) return

                    Log.d(TAG, "End of speech detected")
                }

                override fun onError(error: Int) {
                    if (!isCurrentSession()) return

                    sessionInProgress = false

                    if (!listening) return

                    val errorName = speechErrorName(error)

                    Log.w(
                        TAG,
                        "Recognition callback error: $errorName ($error)"
                    )

                    if (
                        error ==
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    ) {
                        prefs.edit()
                            .putBoolean(PREF_ENABLED, false)
                            .apply()

                        stopListening()

                        sendError(
                            error,
                            "$errorName: microphone permission denied"
                        )

                        sendState(false, false)
                        stopSelf()

                        return
                    }

                    // NO_MATCH और SPEECH_TIMEOUT सामान्य हैं।
                    val normalRecognitionError =
                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                    releaseRecognizer(generation)

                    val delay = when {
                        normalRecognitionError -> 700L

                        error ==
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 3000L

                        else -> retryDelay
                    }

                    if (!normalRecognitionError) {
                        sendError(
                            error,
                            "$errorName: recognition session failed; retrying"
                        )
                    }

                    // सामान्य recognition error पर Voice State OFF नहीं होगी।
                    scheduleRestart(delay)

                    if (!normalRecognitionError) {
                        retryDelay =
                            (retryDelay * 2).coerceAtMost(15000L)
                    } else {
                        retryDelay = 1500L
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (!isCurrentSession()) return

                    sessionInProgress = false

                    if (!listening) return

                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val recognizedText = matches?.firstOrNull()

                    Log.d(
                        TAG,
                        "Final results received: $recognizedText"
                    )

                    releaseRecognizer(generation)

                    if (!recognizedText.isNullOrBlank()) {
                        sendRecognizedText(recognizedText)
                    } else {
                        Log.w(
                            TAG,
                            "Recognition returned empty results"
                        )
                    }

                    retryDelay = 1500L
                    scheduleRestart(700L)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isCurrentSession()) return

                    val partialText = partialResults
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()

                    if (!partialText.isNullOrBlank()) {
                        Log.d(TAG, "Partial speech: $partialText")
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )
    }

    private fun resumeListening() {
        if (listening) {
            Log.d(TAG, "Already listening")
            return
        }

        listening = true
        sessionInProgress = false
        retryDelay = 1500L

        handler.removeCallbacks(restartRunnable)
        restartScheduled = false

        Log.d(TAG, "Listening loop enabled")

        startRecognitionSession()
    }

    private fun pauseListening() {
        Log.d(TAG, "Pausing voice recognition")

        listening = false
        sessionInProgress = false

        handler.removeCallbacks(restartRunnable)
        restartScheduled = false

        releaseRecognizer()

        sendState(false, false)
    }

    private fun startRecognitionSession() {
        if (!listening || sessionInProgress) return

        if (!hasAudioPermission()) {
            prefs.edit()
                .putBoolean(PREF_ENABLED, false)
                .apply()

            stopListening()

            sendError(
                -1,
                "RECORD_AUDIO permission was revoked"
            )

            sendState(false, false)
            stopSelf()

            return
        }

        try {
            if (recognizer == null) {
                createRecognizerOnce()
            }

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

            Log.d(
                TAG,
                "Calling startListening(), language=$activeLanguage"
            )

            recognizer?.startListening(speechIntent)

        } catch (e: Exception) {
            sessionInProgress = false

            Log.e(TAG, "startRecognitionSession failed", e)

            releaseRecognizer()

            sendError(
                -3,
                "Could not start recognition: ${e.message ?: e.javaClass.simpleName}"
            )

            scheduleRestart(retryDelay)

            retryDelay =
                (retryDelay * 2).coerceAtMost(15000L)
        }
    }

    private fun scheduleRestart(delay: Long) {
        if (!listening || restartScheduled) return

        restartScheduled = true

        Log.d(TAG, "Scheduling restart in ${delay}ms")

        handler.postDelayed(
            restartRunnable,
            delay.coerceAtLeast(500L)
        )
    }

    private fun releaseRecognizer(expectedGeneration: Int? = null) {
        if (
            expectedGeneration != null &&
            expectedGeneration != recognizerGeneration
        ) {
            return
        }

        recognizerGeneration++

        val oldRecognizer = recognizer
        recognizer = null
        sessionInProgress = false

        try {
            oldRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer cancel failed", e)
        }

        try {
            oldRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer destroy failed", e)
        }

        if (oldRecognizer != null) {
            Log.d(TAG, "SpeechRecognizer released")
        }
    }

    private fun stopListening() {
        Log.d(TAG, "Stopping listening loop")

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
        Log.d(TAG, "VoiceForegroundService destroyed")

        stopListening()

        // Explicit STOP के बाद userStopped=true ही रहेगा।
        // इससे MainActivity को गलत state broadcast नहीं मिलेगा।
        sendState(false, userStopped)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
