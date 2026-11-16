
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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.vosk.Model
import java.util.Locale
import kotlin.math.max

class VoiceForegroundService : Service() {

    companion object {
        private const val TAG = "IDRAAK_VOICE"

        const val CHANNEL = "idraak_voice_channel"

        const val ACTION_STOP = "com.idraak.quantum.STOP_VOICE"
        const val ACTION_RESULT = "com.idraak.quantum.VOICE_RESULT"
        const val ACTION_STATE = "com.idraak.quantum.VOICE_STATE"
        const val ACTION_PAUSE = "com.idraak.quantum.PAUSE_VOICE"
        const val ACTION_RESUME = "com.idraak.quantum.RESUME_VOICE"
        const val ACTION_ERROR = "com.idraak.quantum.VOICE_ERROR"

        const val EXTRA_TEXT = "recognized_text"
        const val EXTRA_LANGUAGE = "idraak_language"
        const val EXTRA_RUNNING = "voice_running"
        const val EXTRA_USER_STOPPED = "voice_user_stopped"
        const val EXTRA_ERROR_CODE = "voice_error_code"
        const val EXTRA_ERROR_MESSAGE = "voice_error_message"

        private const val NOTIFICATION_ID = 42

        private const val PREF_ENABLED = "voice_enabled"
        private const val PREF_LANGUAGE = "voice_language"
        private const val PREF_PAUSED = "voice_paused"
        private const val PREF_USER_STOPPED = "voice_user_stopped"

        private const val SAMPLE_RATE = 16000
        private const val RESTART_DELAY = 1500L
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var modelManager: VoskModelManager

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var listening = false

    @Volatile
    private var paused = false

    @Volatile
    private var userStopped = false

    @Volatile
    private var captureRunning = false

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recognitionEngine: VoskRecognitionEngine? = null

    @Volatile
    private var audioThread: Thread? = null

    @Volatile
    private var captureGeneration = 0

    private var modelLoading = false
    private var activeLanguage = "hi-IN"

    private val prefs by lazy {
        getSharedPreferences("idraak_voice_service", MODE_PRIVATE)
    }

    private val modelListener = object : VoskModelManager.Listener {

        override fun onProgress(language: String, percent: Int) {
            Log.d(
                TAG,
                "Vosk model download: language=$language, progress=$percent%"
            )
        }

        override fun onReady(language: String, model: Model) {
            modelLoading = false

            if (!listening || paused || userStopped) {
                Log.d(TAG, "Model ready, but listening is not requested")
                return
            }

            val expectedLanguage = modelLanguage(activeLanguage)

            if (language != expectedLanguage) {
                Log.d(
                    TAG,
                    "Ignoring outdated model callback: $language"
                )
                return
            }

            Log.i(TAG, "Vosk model ready: $language")

            try {
                startAudioCapture(model)
            } catch (exception: Exception) {
                Log.e(TAG, "Could not start audio capture", exception)

                failService(
                    -4,
                    "Could not start microphone: " +
                        (exception.message ?: exception.javaClass.simpleName)
                )
            }
        }

        override fun onError(language: String, message: String) {
            modelLoading = false

            Log.e(TAG, "Vosk model error: $language, $message")

            if (!listening || paused || userStopped) return

            failService(
                -5,
                "Vosk model error: $message"
            )
        }
    }

    private val restartRunnable = Runnable {
        if (listening && !paused && !userStopped && !captureRunning) {
            requestModel()
        }
    }

    override fun onCreate() {
        super.onCreate()

        notificationManager =
            getSystemService(NotificationManager::class.java)

        modelManager = VoskModelManager(applicationContext)

        userStopped = prefs.getBoolean(PREF_USER_STOPPED, false)
        paused = prefs.getBoolean(PREF_PAUSED, false)

        activeLanguage = prefs.getString(
            PREF_LANGUAGE,
            "hi-IN"
        ) ?: "hi-IN"

        createChannel()

        Log.d(
            TAG,
            "Service created: paused=$paused, userStopped=$userStopped"
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL,
            "IDRAAK Microphone",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                "Visible notification while IDRAAK voice service is active"
        }

        notificationManager.createNotificationChannel(channel)
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

        val statusText = when {
            paused -> "Voice listening paused"
            captureRunning -> "Listening for voice commands"
            listening -> "Preparing offline voice recognition"
            else -> "Voice service starting"
        }

        return builder
            .setContentTitle("IDRAAK Voice Service")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop listening",
                pending
            )
            .build()
    }

    private fun startForegroundSafely() {
        val currentNotification = notification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                currentNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                currentNotification
            )
        }

        Log.d(TAG, "Foreground service started")
    }

    private fun updateNotification() {
        try {
            notificationManager.notify(
                NOTIFICATION_ID,
                notification()
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Notification update failed", exception)
        }
    }

    private fun sendState(
        running: Boolean,
        stoppedByUser: Boolean
    ) {
        val intent = Intent(ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_RUNNING, running)
            .putExtra(EXTRA_USER_STOPPED, stoppedByUser)

        sendBroadcast(intent)

        Log.d(
            TAG,
            "Voice state: running=$running, userStopped=$stoppedByUser"
        )
    }

    private fun sendError(code: Int, message: String) {
        Log.e(TAG, "Voice error: code=$code, message=$message")

        val intent = Intent(ACTION_ERROR)
            .setPackage(packageName)
            .putExtra(EXTRA_ERROR_CODE, code)
            .putExtra(EXTRA_ERROR_MESSAGE, message)

        sendBroadcast(intent)
    }

    private fun sendRecognizedText(text: String) {
        val cleanedText = text.trim()

        if (cleanedText.isEmpty()) return

        Log.i(TAG, "Speech recognized: $cleanedText")

        val intent = Intent(ACTION_RESULT)
            .setPackage(packageName)
            .putExtra(EXTRA_TEXT, cleanedText)

        sendBroadcast(intent)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d(
            TAG,
            "onStartCommand: action=${intent?.action ?: "START_OR_RESTART"}"
        )

        when (intent?.action) {

            ACTION_STOP -> {
                userStopped = true
                paused = false
                listening = false

                prefs.edit()
                    .putBoolean(PREF_ENABLED, false)
                    .putBoolean(PREF_PAUSED, false)
                    .putBoolean(PREF_USER_STOPPED, true)
                    .apply()

                stopListening()
                sendState(false, true)
                stopSelf()

                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                Log.d(TAG, "Pause requested")

                paused = true

                prefs.edit()
                    .putBoolean(PREF_PAUSED, true)
                    .apply()

                pauseListening()
                updateNotification()

                return START_STICKY
            }

            ACTION_RESUME -> {
                if (!hasAudioPermission()) {
                    failService(
                        -1,
                        "RECORD_AUDIO permission is not granted"
                    )
                    return START_NOT_STICKY
                }

                try {
                    userStopped = false
                    paused = false

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

                    if (activeLanguage != newLanguage) {
                        stopAudioCapture()
                        modelLoading = false
                    }

                    activeLanguage = newLanguage

                    prefs.edit()
                        .putString(PREF_LANGUAGE, activeLanguage)
                        .putBoolean(PREF_ENABLED, true)
                        .putBoolean(PREF_PAUSED, false)
                        .putBoolean(PREF_USER_STOPPED, false)
                        .apply()

                    startForegroundSafely()

                    listening = true

                    sendState(false, false)

                    updateNotification()
                    requestModel()

                    return START_STICKY

                } catch (exception: Exception) {
                    Log.e(TAG, "Resume failed", exception)

                    failService(
                        -2,
                        "Resume failed: " +
                            (exception.message ?: exception.javaClass.simpleName)
                    )

                    return START_NOT_STICKY
                }
            }
        }

        if (intent == null) {
            val enabled = prefs.getBoolean(PREF_ENABLED, false)

            userStopped = prefs.getBoolean(PREF_USER_STOPPED, false)
            paused = prefs.getBoolean(PREF_PAUSED, false)

            if (!enabled || userStopped) {
                Log.d(
                    TAG,
                    "Service restart ignored: voice disabled or user-stopped"
                )

                sendState(false, userStopped)
                stopSelf()

                return START_NOT_STICKY
            }

            if (paused) {
                Log.d(TAG, "Service recreated in paused state")

                try {
                    startForegroundSafely()

                    listening = false
                    sendState(false, false)

                    return START_STICKY

                } catch (exception: Exception) {
                    Log.e(TAG, "Could not restore paused service", exception)

                    failService(
                        -2,
                        "Could not restore paused service: " +
                            (exception.message ?: exception.javaClass.simpleName)
                    )

                    return START_NOT_STICKY
                }
            }
        }

        if (!hasAudioPermission()) {
            failService(
                -1,
                "RECORD_AUDIO permission is not granted"
            )
            return START_NOT_STICKY
        }

        try {
            userStopped = false
            paused = false

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
                .putBoolean(PREF_PAUSED, false)
                .putBoolean(PREF_USER_STOPPED, false)
                .putString(PREF_LANGUAGE, activeLanguage)
                .apply()

            startForegroundSafely()

            listening = true

            Log.d(TAG, "Starting Vosk recognition: $activeLanguage")

            sendState(false, false)

            updateNotification()
            requestModel()

        } catch (exception: Exception) {
            Log.e(TAG, "Service startup failed", exception)

            failService(
                -2,
                "Service startup failed: " +
                    (exception.message ?: exception.javaClass.simpleName)
            )

            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun failService(code: Int, message: String) {
        Log.e(TAG, message)

        userStopped = false
        paused = false
        listening = false

        prefs.edit()
            .putBoolean(PREF_ENABLED, false)
            .putBoolean(PREF_PAUSED, false)
            .putBoolean(PREF_USER_STOPPED, false)
            .apply()

        handler.removeCallbacks(restartRunnable)

        stopListening()

        sendError(code, message)
        sendState(false, false)

        stopSelf()
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

    private fun modelLanguage(language: String): String {
        return when {
            language.startsWith("hi", ignoreCase = true) -> "hi"
            language.startsWith("en", ignoreCase = true) -> "en"
            else -> language
        }
    }

    /**
     * Requests the selected model from VoskModelManager.
     * Downloading and model loading happen off the main thread.
     */
    private fun requestModel() {
        if (!listening || paused || userStopped) return

        if (!hasAudioPermission()) {
            failService(
                -1,
                "RECORD_AUDIO permission was revoked"
            )
            return
        }

        if (captureRunning) return
        if (modelLoading) return

        modelLoading = true

        updateNotification()

        Log.d(TAG, "Preparing Vosk model for $activeLanguage")

        try {
            modelManager.ensureModel(
                activeLanguage,
                modelListener
            )
        } catch (exception: Exception) {
            modelLoading = false

            failService(
                -5,
                "Could not load Vosk model: " +
                    (exception.message ?: exception.javaClass.simpleName)
            )
        }
    }

    /**
     * Creates AudioRecord and continuously sends PCM audio to Vosk.
     */
    private fun startAudioCapture(model: Model) {
        if (!listening || paused || userStopped) return
        if (captureRunning) return

        if (!hasAudioPermission()) {
            throw SecurityException(
                "RECORD_AUDIO permission is not granted"
            )
        }

        stopAudioCapture()

        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minimumBuffer <= 0) {
            throw IllegalStateException(
                "AudioRecord returned invalid buffer size: $minimumBuffer"
            )
        }

        val bufferSize = max(
            minimumBuffer * 2,
            SAMPLE_RATE
        )

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()

            throw IllegalStateException(
                "AudioRecord initialization failed"
            )
        }

        val engine = VoskRecognitionEngine(
            model = model,
            sampleRate = SAMPLE_RATE.toFloat(),
            listener = object : VoskRecognitionEngine.Listener {

                override fun onPartialResult(text: String) {
                    if (!listening || paused || userStopped) return

                    Log.d(TAG, "Vosk partial: $text")
                }

                override fun onFinalResult(text: String) {
                    if (!listening || paused || userStopped) return

                    sendRecognizedText(text)
                }

                override fun onRecognitionError(error: Exception) {
                    Log.e(TAG, "Vosk recognition error", error)

                    if (listening && !paused && !userStopped) {
                        sendError(
                            -6,
                            "Vosk recognition error: " +
                                (error.message ?: error.javaClass.simpleName)
                        )
                    }
                }
            }
        )

        val generation = ++captureGeneration

        audioRecord = recorder
        recognitionEngine = engine
        captureRunning = true

        try {
            recorder.startRecording()

            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException(
                    "AudioRecord could not enter recording state"
                )
            }

        } catch (exception: Exception) {
            captureRunning = false
            audioRecord = null
            recognitionEngine = null

            try {
                recorder.release()
            } catch (_: Exception) {
            }

            engine.close()

            throw exception
        }

        val worker = Thread({
            val audioBuffer = ByteArray(bufferSize)

            Log.i(TAG, "Continuous Vosk microphone capture started")

            try {
                while (
                    captureRunning &&
                    listening &&
                    !paused &&
                    !userStopped &&
                    generation == captureGeneration
                ) {
                    val count = recorder.read(
                        audioBuffer,
                        0,
                        audioBuffer.size
                    )

                    if (count > 0) {
                        engine.acceptAudio(
                            audioBuffer,
                            count
                        )
                    } else if (
                        count == AudioRecord.ERROR_DEAD_OBJECT ||
                        count == AudioRecord.ERROR ||
                        count == AudioRecord.ERROR_INVALID_OPERATION
                    ) {
                        throw IllegalStateException(
                            "AudioRecord read failed: $count"
                        )
                    }
                }

            } catch (exception: Exception) {
                if (
                    generation == captureGeneration &&
                    listening &&
                    !paused &&
                    !userStopped
                ) {
                    Log.e(TAG, "Microphone capture failed", exception)

                    handler.post {
                        if (
                            generation == captureGeneration &&
                            listening &&
                            !paused &&
                            !userStopped
                        ) {
                            failService(
                                -7,
                                "Microphone capture failed: " +
                                    (exception.message
                                        ?: exception.javaClass.simpleName)
                            )
                        }
                    }
                }

            } finally {
                try {
                    if (
                        recorder.recordingState ==
                        AudioRecord.RECORDSTATE_RECORDING
                    ) {
                        recorder.stop()
                    }
                } catch (exception: Exception) {
                    Log.w(TAG, "AudioRecord stop failed", exception)
                }

                try {
                    recorder.release()
                } catch (exception: Exception) {
                    Log.w(TAG, "AudioRecord release failed", exception)
                }

                Log.d(TAG, "Microphone capture thread finished")
            }

        }, "IDRAAK-Vosk-Audio")

        worker.priority = Thread.MAX_PRIORITY
        audioThread = worker

        worker.start()

        sendState(true, false)
        updateNotification()
    }

    /**
     * Stops microphone capture and releases the active recognizer.
     * The loaded Model remains cached by VoskModelManager.
     */
    private fun stopAudioCapture() {
        captureRunning = false
        captureGeneration++

        val oldRecorder = audioRecord
        val oldEngine = recognitionEngine
        val oldThread = audioThread

        audioRecord = null
        recognitionEngine = null
        audioThread = null

        try {
            if (
                oldRecorder != null &&
                oldRecorder.recordingState ==
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                oldRecorder.stop()
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Could not stop AudioRecord", exception)
        }

        try {
            oldEngine?.close()
        } catch (exception: Exception) {
            Log.w(TAG, "Could not close Vosk recognizer", exception)
        }

        if (
            oldThread != null &&
            oldThread !== Thread.currentThread()
        ) {
            try {
                oldThread.join(500)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun resumeListening() {
        if (paused || userStopped) {
            Log.d(TAG, "Resume ignored: paused or user-stopped")
            return
        }

        if (listening) {
            Log.d(TAG, "Already listening")
            return
        }

        listening = true

        handler.removeCallbacks(restartRunnable)

        Log.d(TAG, "Vosk listening loop enabled")

        updateNotification()
        requestModel()
    }

    private fun pauseListening() {
        Log.d(TAG, "Pausing Vosk microphone capture")

        listening = false

        handler.removeCallbacks(restartRunnable)

        modelLoading = false

        stopAudioCapture()

        sendState(false, false)
    }

    private fun stopListening() {
        Log.d(TAG, "Stopping Vosk listening")

        listening = false
        captureRunning = false
        modelLoading = false

        handler.removeCallbacks(restartRunnable)

        stopAudioCapture()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "VoiceForegroundService destroyed")

        handler.removeCallbacksAndMessages(null)

        stopListening()

        try {
            modelManager.close()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not close Vosk model manager", exception)
        }

        sendState(false, userStopped)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
