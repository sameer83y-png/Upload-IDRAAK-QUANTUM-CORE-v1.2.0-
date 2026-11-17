package com.idraak.quantum

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import android.os.Process
import android.util.Log
import org.vosk.Model
import java.util.Locale
import kotlin.math.max

class VoiceForegroundService : Service() {

    companion object {
        private const val TAG = "IDRAAK_VOICE_SERVICE"

        const val ACTION_START = "com.idraak.quantum.voice.START"
        const val ACTION_STOP = "com.idraak.quantum.voice.STOP"
        const val ACTION_PAUSE = "com.idraak.quantum.voice.PAUSE"
        const val ACTION_RESUME = "com.idraak.quantum.voice.RESUME"

        const val ACTION_RESULT = "com.idraak.quantum.voice.RESULT"
        const val ACTION_STATE = "com.idraak.quantum.voice.STATE"
        const val ACTION_ERROR = "com.idraak.quantum.voice.ERROR"

        const val EXTRA_TEXT = "text"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_USER_STOPPED = "user_stopped"
        const val EXTRA_ERROR_CODE = "error_code"
        const val EXTRA_ERROR_MESSAGE = "error_message"

        private const val PREFS_NAME = "idraak_voice_service"
        private const val PREF_VOICE_ENABLED = "voice_enabled"
        private const val PREF_LANGUAGE = "voice_language"
        private const val PREF_PAUSED = "voice_paused"
        private const val PREF_USER_STOPPED = "voice_user_stopped"

        private const val CHANNEL_ID = "idraak_voice_channel"
        private const val NOTIFICATION_ID = 4101

        private const val SAMPLE_RATE = 16000
        private const val AUDIO_READ_SIZE = 4096
        private const val AUDIO_BUFFER_MULTIPLIER = 2
        private const val WORKER_JOIN_TIMEOUT_MS = 3000L
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
    private var captureGeneration = 0

    @Volatile
    private var modelRequestGeneration = 0

    @Volatile
    private var modelLoading = false

    @Volatile
    private var activeLanguage = "hi-IN"

    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private var pendingModel: Model? = null

    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val restartRunnable = Runnable {
        if (!listening || paused || userStopped) {
            return@Runnable
        }

        val model = pendingModel

        if (model != null) {
            startAudioCapture(model)
        } else {
            requestModel()
        }
    }

    override fun onCreate() {
        super.onCreate()

        try {
            notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            modelManager = VoskModelManager(this)

            activeLanguage = canonicalLanguage(
                preferences.getString(PREF_LANGUAGE, "hi-IN")
                    ?: "hi-IN"
            )

            paused = preferences.getBoolean(PREF_PAUSED, false)
            userStopped = preferences.getBoolean(
                PREF_USER_STOPPED,
                false
            )

            createNotificationChannel()

            Log.i(TAG, "Voice service created")

        } catch (exception: Exception) {
            Log.e(TAG, "Service initialization failed", exception)
            stopSelf()
        } catch (error: LinkageError) {
            Log.e(TAG, "Native initialization failed", error)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (!::modelManager.isInitialized) {
            Log.e(TAG, "Model manager is not initialized")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            when (intent?.action) {

                ACTION_STOP -> {
                    userStopped = true
                    listening = false
                    paused = false

                    preferences.edit()
                        .putBoolean(PREF_VOICE_ENABLED, false)
                        .putBoolean(PREF_PAUSED, false)
                        .putBoolean(PREF_USER_STOPPED, true)
                        .apply()

                    invalidateModelRequest()
                    pendingModel = null
                    stopAudioCapture()
                    sendState(false, true)
                    stopForegroundCompat()
                    stopSelf()

                    return START_NOT_STICKY
                }

                ACTION_PAUSE -> {
                    paused = true

                    preferences.edit()
                        .putBoolean(PREF_PAUSED, true)
                        .apply()

                    invalidateModelRequest()
                    stopAudioCapture()

                    updateNotification(
                        "IDRAAK microphone paused"
                    )

                    sendState(false, false)

                    return START_STICKY
                }

                ACTION_RESUME -> {
                    val requestedLanguage =
                        intent.getStringExtra(EXTRA_LANGUAGE)
                            ?: preferences.getString(
                                PREF_LANGUAGE,
                                activeLanguage
                            )
                            ?: activeLanguage

                    resumeVoice(requestedLanguage)
                    return START_STICKY
                }

                ACTION_START -> {
                    val requestedLanguage =
                        intent.getStringExtra(EXTRA_LANGUAGE)
                            ?: preferences.getString(
                                PREF_LANGUAGE,
                                activeLanguage
                            )
                            ?: activeLanguage

                    startVoice(requestedLanguage)
                    return START_STICKY
                }

                null -> {
                    val enabled = preferences.getBoolean(
                        PREF_VOICE_ENABLED,
                        false
                    )

                    if (!enabled || userStopped) {
                        stopAudioCapture()
                        stopForegroundCompat()
                        stopSelf()
                        return START_NOT_STICKY
                    }

                    activeLanguage = canonicalLanguage(
                        preferences.getString(
                            PREF_LANGUAGE,
                            activeLanguage
                        ) ?: activeLanguage
                    )

                    paused = preferences.getBoolean(
                        PREF_PAUSED,
                        false
                    )

                    if (!startForegroundSafely()) {
                        return START_NOT_STICKY
                    }

                    if (paused) {
                        listening = false
                        sendState(false, false)
                    } else {
                        listening = true
                        requestModel()
                    }

                    return START_STICKY
                }

                else -> {
                    startVoice(
                        intent.getStringExtra(EXTRA_LANGUAGE)
                            ?: activeLanguage
                    )

                    return START_STICKY
                }
            }

        } catch (exception: Exception) {
            Log.e(TAG, "Service command failed", exception)

            failService(
                exception.message ?: "Voice service command failed.",
                -20
            )

            return START_NOT_STICKY

        } catch (error: LinkageError) {
            Log.e(TAG, "Native service error", error)

            failService(
                error.message ?: "Vosk native service error.",
                -21
            )

            return START_NOT_STICKY
        }
    }

    private fun startVoice(language: String) {
        activeLanguage = canonicalLanguage(language)

        listening = true
        paused = false
        userStopped = false
        pendingModel = null

        preferences.edit()
            .putBoolean(PREF_VOICE_ENABLED, true)
            .putString(PREF_LANGUAGE, activeLanguage)
            .putBoolean(PREF_PAUSED, false)
            .putBoolean(PREF_USER_STOPPED, false)
            .apply()

        invalidateModelRequest()
        stopAudioCapture()

        if (!startForegroundSafely()) return

        updateNotification(
            "IDRAAK is preparing voice recognition"
        )

        requestModel()
    }

    private fun resumeVoice(language: String) {
        val newLanguage = canonicalLanguage(language)

        if (newLanguage != activeLanguage) {
            activeLanguage = newLanguage
            pendingModel = null

            invalidateModelRequest()
            stopAudioCapture()
        }

        listening = true
        paused = false
        userStopped = false

        preferences.edit()
            .putBoolean(PREF_VOICE_ENABLED, true)
            .putString(PREF_LANGUAGE, activeLanguage)
            .putBoolean(PREF_PAUSED, false)
            .putBoolean(PREF_USER_STOPPED, false)
            .apply()

        if (!startForegroundSafely()) return

        updateNotification("IDRAAK is listening")
        requestModel()
    }

    private fun canonicalLanguage(value: String): String {
        val language = value.trim().lowercase(Locale.ROOT)

        return when {
            language == "hi" ||
                language.startsWith("hi-") ||
                language.contains("hindi") -> "hi-IN"

            language == "en" ||
                language.startsWith("en-") ||
                language.contains("english") -> "en-IN"

            language == "ur" ||
                language.startsWith("ur-") ||
                language.contains("urdu") -> "ur-PK"

            language == "ar" ||
                language.startsWith("ar-") ||
                language.contains("arabic") -> "ar"

            else -> "hi-IN"
        }
    }

    private fun requestModel() {
        if (!listening || paused || userStopped) return
        if (captureRunning || modelLoading) return

        if (!hasAudioPermission()) {
            failService(
                "Microphone permission is not granted.",
                PackageManager.PERMISSION_DENIED
            )
            return
        }

        if (
            activeLanguage != "hi-IN" &&
            activeLanguage != "en-IN"
        ) {
            failService(
                "Vosk currently supports Hindi and English only.",
                -2
            )
            return
        }

        modelLoading = true

        val requestId = ++modelRequestGeneration
        val requestedLanguage = activeLanguage

        val listener = object : VoskModelManager.Listener {

            override fun onProgress(
                language: String,
                percent: Int
            ) {
                if (requestId != modelRequestGeneration) return
                if (!listening || paused || userStopped) return

                updateNotification(
                    "Downloading ${languageLabel(language)} model: $percent%"
                )
            }

            override fun onReady(
                language: String,
                model: Model
            ) {
                if (requestId != modelRequestGeneration) return
                if (!listening || paused || userStopped) return

                if (
                    canonicalLanguage(language) != requestedLanguage
                ) {
                    return
                }

                modelLoading = false
                pendingModel = model

                startAudioCapture(model)
            }

            override fun onError(
                language: String,
                message: String
            ) {
                if (requestId != modelRequestGeneration) return

                modelLoading = false

                if (!listening || paused || userStopped) return

                failService(
                    "Vosk model error (${languageLabel(language)}): $message",
                    -3
                )
            }
        }

        try {
            modelManager.ensureModel(
                requestedLanguage,
                listener
            )

        } catch (exception: Exception) {
            if (requestId == modelRequestGeneration) {
                modelLoading = false

                failService(
                    exception.message
                        ?: "Unable to load Vosk model.",
                    -4
                )
            }

        } catch (error: LinkageError) {
            if (requestId == modelRequestGeneration) {
                modelLoading = false

                failService(
                    error.message
                        ?: "Vosk native library initialization failed.",
                    -5
                )
            }
        }
    }

    private fun startAudioCapture(model: Model) {
        if (!listening || paused || userStopped) return

        if (!hasAudioPermission()) {
            failService(
                "Microphone permission is not granted.",
                PackageManager.PERMISSION_DENIED
            )
            return
        }

        val existingThread = audioThread

        if (existingThread != null && existingThread.isAlive) {
            pendingModel = model

            handler.removeCallbacks(restartRunnable)
            handler.postDelayed(restartRunnable, 500)

            return
        }

        val minimumBuffer = try {
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        } catch (exception: Exception) {
            failService(
                "Unable to query microphone buffer: " +
                    (exception.message ?: "Unknown audio error"),
                -6
            )
            return
        } catch (error: LinkageError) {
            failService(
                "Microphone native initialization failed: " +
                    (error.message ?: "Native error"),
                -7
            )
            return
        }

        if (minimumBuffer <= 0) {
            failService(
                "Android could not initialize microphone buffer. Code: $minimumBuffer",
                minimumBuffer
            )
            return
        }

        val bufferSize = max(
            minimumBuffer * AUDIO_BUFFER_MULTIPLIER,
            SAMPLE_RATE * 2
        )

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (exception: Exception) {
            failService(
                exception.message ?: "Unable to create AudioRecord.",
                -8
            )
            return
        } catch (error: LinkageError) {
            failService(
                error.message ?: "AudioRecord native error.",
                -9
            )
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            try {
                recorder.release()
            } catch (_: Exception) {
            }

            failService(
                "Android could not initialize the microphone recorder.",
                -10
            )
            return
        }

        val engine = try {
            VoskRecognitionEngine(
                model = model,
                sampleRate = SAMPLE_RATE.toFloat(),
                listener = object : VoskRecognitionEngine.Listener {

                    override fun onPartialResult(text: String) {
                        // Partial results are intentionally ignored.
                    }

                    override fun onFinalResult(text: String) {
                        val finalText = text.trim()

                        if (finalText.isEmpty()) return
                        if (!listening || paused || userStopped) return

                        sendRecognitionResult(finalText)
                    }

                    override fun onRecognitionError(
                        error: Exception
                    ) {
                        Log.e(
                            TAG,
                            "Vosk recognition error",
                            error
                        )
                    }
                }
            )

        } catch (exception: Exception) {
            try {
                recorder.release()
            } catch (_: Exception) {
            }

            failService(
                exception.message
                    ?: "Unable to create Vosk recognizer.",
                -11
            )
            return

        } catch (error: LinkageError) {
            try {
                recorder.release()
            } catch (_: Exception) {
            }

            failService(
                error.message
                    ?: "Vosk native recognizer initialization failed.",
                -12
            )
            return
        }

        val generation = ++captureGeneration

        try {
            recorder.startRecording()

            if (
                recorder.recordingState !=
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                throw IllegalStateException(
                    "AudioRecord did not enter recording state."
                )
            }

        } catch (exception: Exception) {
            try {
                engine.close()
            } catch (_: Exception) {
            }

            try {
                recorder.release()
            } catch (_: Exception) {
            }

            failService(
                exception.message
                    ?: "Unable to start microphone recording.",
                -13
            )
            return
        }

        audioRecord = recorder
        captureRunning = true

        val worker = Thread({

            try {
                try {
                    Process.setThreadPriority(
                        Process.THREAD_PRIORITY_AUDIO
                    )
                } catch (_: Exception) {
                }

                val buffer = ByteArray(AUDIO_READ_SIZE)

                while (
                    captureRunning &&
                    generation == captureGeneration &&
                    listening &&
                    !paused &&
                    !userStopped
                ) {
                    val count = recorder.read(
                        buffer,
                        0,
                        buffer.size
                    )

                    if (count > 0) {
                        engine.acceptAudio(buffer, count)

                    } else if (
                        count == AudioRecord.ERROR_DEAD_OBJECT ||
                        count == AudioRecord.ERROR_INVALID_OPERATION ||
                        count == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        if (
                            generation == captureGeneration &&
                            captureRunning
                        ) {
                            throw IllegalStateException(
                                "AudioRecord read failed with code $count"
                            )
                        }

                        break
                    }
                }

            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Audio capture thread failed",
                    exception
                )

                if (
                    generation == captureGeneration &&
                    listening &&
                    !paused &&
                    !userStopped
                ) {
                    handler.post {
                        if (
                            generation == captureGeneration &&
                            listening &&
                            !paused &&
                            !userStopped
                        ) {
                            failService(
                                exception.message
                                    ?: "Microphone capture failed.",
                                -14
                            )
                        }
                    }
                }

            } catch (error: LinkageError) {
                Log.e(
                    TAG,
                    "Vosk native recognition failed",
                    error
                )

                handler.post {
                    if (generation == captureGeneration) {
                        failService(
                            error.message
                                ?: "Vosk native recognition failed.",
                            -15
                        )
                    }
                }

            } finally {
                /*
                 * The recognizer is closed before the model manager.
                 * The manager itself remains alive until service destroy.
                 */
                try {
                    engine.finish()
                } catch (exception: Exception) {
                    Log.e(
                        TAG,
                        "Final Vosk result failed",
                        exception
                    )
                }

                try {
                    engine.close()
                } catch (exception: Exception) {
                    Log.e(
                        TAG,
                        "Recognizer cleanup failed",
                        exception
                    )
                } catch (error: LinkageError) {
                    Log.e(
                        TAG,
                        "Native recognizer cleanup failed",
                        error
                    )
                }

                try {
                    if (
                        recorder.recordingState ==
                        AudioRecord.RECORDSTATE_RECORDING
                    ) {
                        recorder.stop()
                    }
                } catch (_: Exception) {
                }

                try {
                    recorder.release()
                } catch (exception: Exception) {
                    Log.e(
                        TAG,
                        "AudioRecord release failed",
                        exception
                    )
                }

                if (generation == captureGeneration) {
                    captureRunning = false
                    audioRecord = null
                }

                if (audioThread === Thread.currentThread()) {
                    audioThread = null
                }

                handler.post {
                    if (
                        generation == captureGeneration &&
                        listening &&
                        !paused &&
                        !userStopped &&
                        pendingModel != null
                    ) {
                        handler.removeCallbacks(restartRunnable)
                        handler.postDelayed(
                            restartRunnable,
                            250
                        )
                    }
                }
            }

        }, "IDRAAK-Vosk-Audio")

        audioThread = worker

        try {
            worker.start()

        } catch (exception: Exception) {
            captureRunning = false
            audioThread = null
            audioRecord = null

            try {
                engine.close()
            } catch (_: Exception) {
            }

            try {
                recorder.stop()
            } catch (_: Exception) {
            }

            try {
                recorder.release()
            } catch (_: Exception) {
            }

            failService(
                exception.message
                    ?: "Unable to start audio worker thread.",
                -16
            )
            return
        }

        updateNotification(
            "IDRAAK is continuously listening"
        )

        sendState(true, false)
    }

    private fun stopAudioCapture() {
        captureRunning = false
        captureGeneration++

        handler.removeCallbacks(restartRunnable)

        val recorder = audioRecord
        val worker = audioThread

        audioRecord = null

        if (recorder != null) {
            try {
                if (
                    recorder.recordingState ==
                    AudioRecord.RECORDSTATE_RECORDING
                ) {
                    recorder.stop()
                }
            } catch (exception: Exception) {
                Log.d(
                    TAG,
                    "AudioRecord stop requested",
                    exception
                )
            }
        }

        if (
            worker != null &&
            worker !== Thread.currentThread()
        ) {
            try {
                worker.join(WORKER_JOIN_TIMEOUT_MS)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            if (worker.isAlive) {
                Log.w(
                    TAG,
                    "Audio worker has not finished cleanup yet."
                )
            } else if (audioThread === worker) {
                audioThread = null
            }
        }
    }

    private fun invalidateModelRequest() {
        modelRequestGeneration++
        modelLoading = false
        handler.removeCallbacks(restartRunnable)
    }

    private fun failService(
        message: String,
        errorCode: Int
    ) {
        if (userStopped && !listening) return

        Log.e(TAG, message)

        listening = false
        paused = false
        userStopped = true
        pendingModel = null

        preferences.edit()
            .putBoolean(PREF_VOICE_ENABLED, false)
            .putBoolean(PREF_PAUSED, false)
            .putBoolean(PREF_USER_STOPPED, true)
            .apply()

        invalidateModelRequest()
        stopAudioCapture()

        sendError(errorCode, message)
        sendState(false, true)

        stopForegroundCompat()
        stopSelf()
    }

    private fun hasAudioPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendRecognitionResult(text: String) {
        val intent = Intent(ACTION_RESULT)
            .setPackage(packageName)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_LANGUAGE, activeLanguage)

        sendBroadcast(intent)
    }

    private fun sendState(
        running: Boolean,
        stoppedByUser: Boolean
    ) {
        val intent = Intent(ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_RUNNING, running)
            .putExtra(EXTRA_USER_STOPPED, stoppedByUser)
            .putExtra(EXTRA_LANGUAGE, activeLanguage)

        sendBroadcast(intent)
    }

    private fun sendError(
        code: Int,
        message: String
    ) {
        val intent = Intent(ACTION_ERROR)
            .setPackage(packageName)
            .putExtra(EXTRA_ERROR_CODE, code)
            .putExtra(EXTRA_ERROR_MESSAGE, message)

        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "IDRAAK Voice Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent voice recognition service"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        message: String
    ): Notification {
        val stopIntent = Intent(
            this,
            VoiceForegroundService::class.java
        ).setAction(ACTION_STOP)

        val pendingFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }

        val stopPendingIntent = PendingIntent.getService(
            this,
            4102,
            stopIntent,
            pendingFlags
        )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }

        builder
            .setContentTitle("IDRAAK Quantum Core")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop",
                    stopPendingIntent
                ).build()
            )

        return builder.build()
    }

    private fun startForegroundSafely(): Boolean {
        if (!hasAudioPermission()) {
            failService(
                "Microphone permission is not granted.",
                PackageManager.PERMISSION_DENIED
            )
            return false
        }

        return try {
            val notification = buildNotification(
                "IDRAAK is starting"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }

            true

        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to start foreground service",
                exception
            )

            sendError(
                -17,
                exception.message
                    ?: "Unable to start microphone foreground service."
            )

            stopSelf()
            false

        } catch (error: LinkageError) {
            Log.e(
                TAG,
                "Foreground service native error",
                error
            )

            sendError(
                -18,
                error.message ?: "Foreground service native error."
            )

            stopSelf()
            false
        }
    }

    private fun updateNotification(message: String) {
        try {
            notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification(message)
            )
        } catch (exception: Exception) {
            Log.w(
                TAG,
                "Notification update failed",
                exception
            )
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (exception: Exception) {
            Log.w(
                TAG,
                "Unable to remove foreground notification",
                exception
            )
        }
    }

    private fun languageLabel(language: String): String {
        return when {
            language.startsWith(
                "hi",
                ignoreCase = true
            ) -> "Hindi"

            language.startsWith(
                "en",
                ignoreCase = true
            ) -> "English"

            else -> language
        }
    }

    override fun onDestroy() {
        listening = false
        paused = false
        userStopped = true

        invalidateModelRequest()
        pendingModel = null

        /*
         * Stop and join the audio worker before closing models.
         * If the worker is still alive, do not close models that
         * its recognizer may still be using.
         */
        stopAudioCapture()

        val worker = audioThread

        if (worker != null && worker.isAlive) {
            Log.e(
                TAG,
                "Audio worker did not stop. Model manager cleanup deferred."
            )
        } else {
            if (::modelManager.isInitialized) {
                try {
                    modelManager.close()
                } catch (exception: Exception) {
                    Log.e(
                        TAG,
                        "Vosk model manager cleanup failed",
                        exception
                    )
                }
            }
        }

        sendState(false, true)
        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }
}
