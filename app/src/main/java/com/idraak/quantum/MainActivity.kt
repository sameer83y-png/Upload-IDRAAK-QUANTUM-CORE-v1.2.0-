package com.idraak.quantum

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var homeView: LauncherHomeView
    private lateinit var voiceManager: VoiceManager
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var voiceSessionController: VoiceSessionController

    private var settingsVisible = false
    private var voiceStarted = false
    private var receiverRegistered = false

    // Manual activation permission के बाद पूरी करने के लिए।
    private var pendingManualActivation = false

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 7101
        private const val REQUEST_NOTIFICATION_PERMISSION = 7102

        private const val TAG = "IDRAAK_MAIN"
    }

    private val voiceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent == null) return

            when (intent.action) {

                VoiceForegroundService.ACTION_RESULT -> {

                    val recognizedText =
                        intent.getStringExtra(
                            VoiceForegroundService.EXTRA_TEXT
                        ) ?: return

                    if (::voiceSessionController.isInitialized) {
                        voiceSessionController.onRecognizedText(
                            recognizedText
                        )
                    }
                }

                VoiceForegroundService.ACTION_STATE -> {

                    val running = intent.getBooleanExtra(
                        VoiceForegroundService.EXTRA_RUNNING,
                        false
                    )

                    if (
                        running &&
                        ::homeView.isInitialized &&
                        ::voiceSessionController.isInitialized
                    ) {
                        val awake =
                            voiceSessionController.getVoiceState() !=
                                WakeWordController.VoiceState.SLEEPING

                        homeView.setVoiceStatus(awake)
                    }
                }

                VoiceForegroundService.ACTION_ERROR -> {

                    val errorMessage =
                        intent.getStringExtra(
                            VoiceForegroundService.EXTRA_ERROR_MESSAGE
                        ) ?: "Microphone service error"

                    voiceStarted = false

                    if (::homeView.isInitialized) {
                        homeView.setVoiceStatus(false)
                    }

                    Log.e(TAG, errorMessage)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            CrashLogger.install(applicationContext)
            Log.i(TAG, "Crash Logger initialized")
        } catch (exception: Exception) {
            Log.e(TAG, "Crash Logger initialization failed", exception)
        }

        try {
            configureWindow()
            initializeIDRAAK()
            registerVoiceReceiver()
            requestVoicePermissions()

        } catch (exception: Exception) {
            Log.e(TAG, "Startup failed", exception)
            showStartupError(exception)
        }
    }

    private fun configureWindow() {

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun initializeIDRAAK() {

        settingsManager = SettingsManager(this)

        homeView = LauncherHomeView(
            context = this,
            onSettingsClick = {
                showSettings()
            },
            onVoiceClick = {
                handleVoiceButtonClick()
            }
        )

        voiceManager = VoiceManager(this)

        voiceEngine = VoiceEngine(
            context = this,
            voiceManager = voiceManager
        )

        voiceSessionController = VoiceSessionController(
            context = this,

            onResponse = { response ->

                runOnUiThread {

                    if (
                        !isFinishing &&
                        !isDestroyed &&
                        ::voiceEngine.isInitialized &&
                        ::homeView.isInitialized
                    ) {
                        homeView.setVoiceStatus(true)
                        voiceEngine.speak(response)
                    }
                }
            },

            onStateChanged = { state ->

                runOnUiThread {

                    if (::homeView.isInitialized) {

                        val active =
                            state != WakeWordController.VoiceState.SLEEPING

                        homeView.setVoiceStatus(active)
                    }

                    if (::voiceEngine.isInitialized) {
                        voiceEngine.setSleeping(
                            state == WakeWordController.VoiceState.SLEEPING
                        )
                    }
                }
            }
        )

        applyWakeWordSetting()

        showHome()
    }

    // -------------------------------------------------
    // MANUAL VOICE BUTTON CONTROL
    // -------------------------------------------------

    private fun handleVoiceButtonClick() {

        if (
            !::voiceSessionController.isInitialized ||
            !::settingsManager.isInitialized
        ) {
            Log.e(TAG, "Voice session is not initialized")
            return
        }

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            pendingManualActivation = true

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )

            Log.i(TAG, "Requesting microphone permission for manual voice")
            return
        }

        if (!voiceStarted) {

            startVoiceSystem()

            if (!voiceStarted) {
                Log.e(TAG, "Voice system failed to start")
                return
            }
        }

        activateManualVoiceSession()
    }

    private fun activateManualVoiceSession() {

        if (!::voiceSessionController.isInitialized) return

        try {

            when (voiceSessionController.getVoiceState()) {

                WakeWordController.VoiceState.SLEEPING -> {

                    // Manual activation को Wake Word setting से
                    // स्वतंत्र रूप से काम करने दें।
                    voiceSessionController.setWakeWordEnabled(true)

                    voiceSessionController.activate()

                    if (::homeView.isInitialized) {
                        homeView.setVoiceStatus(true)
                    }

                    Log.i(TAG, "IDRAAK manually activated")
                }

                WakeWordController.VoiceState.ACTIVE,
                WakeWordController.VoiceState.PROCESSING -> {

                    voiceSessionController.sleep()

                    // Sleep के बाद user की original setting restore करें।
                    applyWakeWordSetting()

                    if (::homeView.isInitialized) {
                        homeView.setVoiceStatus(false)
                    }

                    Log.i(TAG, "IDRAAK manually put to sleep")
                }
            }

        } catch (exception: Exception) {

            Log.e(TAG, "Manual voice activation failed", exception)

            if (::homeView.isInitialized) {
                homeView.setVoiceStatus(false)
            }
        }
    }

    private fun applyVoicePreferences() {

        if (
            !::settingsManager.isInitialized ||
            !::voiceEngine.isInitialized
        ) {
            return
        }

        try {
            val selectedLanguage = settingsManager.voiceLanguage

            val ttsLanguage = when (selectedLanguage) {
                "auto", "hi-IN" -> "hi-IN"
                "en-US" -> "en-US"
                "en-IN" -> "en-IN"
                "ur-PK" -> "ur-PK"
                "ar-SA" -> "ar-SA"
                else -> "hi-IN"
            }

            voiceEngine.setLanguage(ttsLanguage)

            voiceEngine.setVoiceGender(
                settingsManager.voiceGender
            )

            voiceEngine.setSpeechRate(
                settingsManager.speechRate
            )

            Log.i(
                TAG,
                "Voice preferences applied: language=$ttsLanguage, " +
                    "gender=${settingsManager.voiceGender}, " +
                    "speechRate=${settingsManager.speechRate}"
            )

        } catch (exception: Exception) {
            Log.e(TAG, "Could not apply voice preferences", exception)
        }
    }

    private fun applyWakeWordSetting() {

        if (
            !::settingsManager.isInitialized ||
            !::voiceSessionController.isInitialized
        ) {
            return
        }

        voiceSessionController.setWakeWordEnabled(
            settingsManager.wakeWordEnabled
        )
    }

    private fun getRecognitionLanguage(): String {

        return when (settingsManager.voiceLanguage) {
            "en-US", "en-IN" -> "en-IN"
            "hi-IN", "auto" -> "hi-IN"
            "ur-PK" -> "ur-PK"
            "ar-SA" -> "ar-SA"
            else -> "hi-IN"
        }
    }

    // -------------------------------------------------
    // BROADCAST RECEIVER
    // -------------------------------------------------

    private fun registerVoiceReceiver() {

        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(VoiceForegroundService.ACTION_RESULT)
            addAction(VoiceForegroundService.ACTION_STATE)
            addAction(VoiceForegroundService.ACTION_ERROR)
        }

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                voiceReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )

        } else {

            @Suppress("DEPRECATION")
            registerReceiver(voiceReceiver, filter)
        }

        receiverRegistered = true
    }

    // -------------------------------------------------
    // PERMISSIONS
    // -------------------------------------------------

    private fun requestVoicePermissions() {

        val audioGranted =
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        if (audioGranted) {
            requestNotificationPermissionIfNeeded()
            startVoiceSystem()
            return
        }

        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_AUDIO_PERMISSION
        )
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        when (requestCode) {

            REQUEST_AUDIO_PERMISSION -> {

                val granted =
                    grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED

                if (granted) {

                    Log.i(TAG, "Microphone permission granted")

                    requestNotificationPermissionIfNeeded()
                    startVoiceSystem()

                    if (pendingManualActivation) {

                        pendingManualActivation = false

                        if (voiceStarted) {
                            activateManualVoiceSession()
                        }
                    }

                } else {

                    pendingManualActivation = false
                    voiceStarted = false

                    if (::homeView.isInitialized) {
                        homeView.setVoiceStatus(false)
                    }

                    Log.e(TAG, "Microphone permission denied")
                }
            }

            REQUEST_NOTIFICATION_PERMISSION -> {

                if (
                    grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.i(TAG, "Notification permission granted")
                } else {
                    Log.w(TAG, "Notification permission denied")
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT < 33) return

        val notificationGranted =
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (!notificationGranted) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    // -------------------------------------------------
    // VOICE SYSTEM STARTUP
    // -------------------------------------------------

    private fun startVoiceSystem() {

        if (voiceStarted) return

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (::homeView.isInitialized) {
                homeView.setVoiceStatus(false)
            }
            return
        }

        if (
            !::voiceEngine.isInitialized ||
            !::voiceSessionController.isInitialized
        ) {
            Log.e(TAG, "Voice components are not initialized")
            return
        }

        try {

            applyVoicePreferences()
            applyWakeWordSetting()

            val language = getRecognitionLanguage()

            voiceEngine.startListening(language)

            voiceStarted = true

            val awake =
                voiceSessionController.getVoiceState() !=
                    WakeWordController.VoiceState.SLEEPING

            if (::homeView.isInitialized) {
                homeView.setVoiceStatus(awake)
            }

            Log.i(
                TAG,
                "Voice system started with recognition language: $language"
            )

        } catch (exception: Exception) {

            voiceStarted = false

            if (::homeView.isInitialized) {
                homeView.setVoiceStatus(false)
            }

            Log.e(
                TAG,
                "Voice startup failed",
                exception
            )
        }
    }

    // -------------------------------------------------
    // SCREENS
    // -------------------------------------------------

    private fun showHome() {

        settingsVisible = false

        if (::voiceEngine.isInitialized) {
            applyVoicePreferences()
        }

        if (::voiceSessionController.isInitialized) {
            applyWakeWordSetting()
        }

        if (::homeView.isInitialized) {
            setContentView(homeView)
        }
    }

    private fun showSettings() {

        if (!::settingsManager.isInitialized) return

        try {

            settingsVisible = true

            val settingsView = SettingsView(
                context = this,
                settingsManager = settingsManager,
                onClose = {
                    showHome()
                }
            )

            setContentView(settingsView)

        } catch (exception: Exception) {

            settingsVisible = false

            Log.e(
                TAG,
                "Settings screen failed",
                exception
            )

            showStartupError(exception)
        }
    }

    // -------------------------------------------------
    // ERROR SCREEN
    // -------------------------------------------------

    private fun showStartupError(exception: Exception) {

        val errorView = TextView(this).apply {

            text = "IDRAAK STARTUP ERROR\n\n" +
                (exception.message ?: "Unknown error")

            textSize = 16f
            setTextColor(0xFFFF6666.toInt())
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val container = FrameLayout(this).apply {

            setBackgroundColor(0xFF03071C.toInt())

            addView(
                errorView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        setContentView(container)
    }

    // -------------------------------------------------
    // BACK BUTTON
    // -------------------------------------------------

    @Deprecated("Deprecated in Android API 33")
    override fun onBackPressed() {

        if (settingsVisible) {
            showHome()
            return
        }

        if (
            ::homeView.isInitialized &&
            homeView.isAppDrawerOpen()
        ) {
            homeView.closeAppDrawer()
            return
        }

        super.onBackPressed()
    }

    // -------------------------------------------------
    // CLEANUP
    // -------------------------------------------------

    override fun onDestroy() {

        pendingManualActivation = false

        if (receiverRegistered) {

            try {
                unregisterReceiver(voiceReceiver)
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Receiver cleanup failed",
                    exception
                )
            }

            receiverRegistered = false
        }

        if (::voiceSessionController.isInitialized) {
            try {
                voiceSessionController.release()
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Voice session cleanup failed",
                    exception
                )
            }
        }

        if (::voiceEngine.isInitialized) {
            try {
                voiceEngine.release()
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Voice engine cleanup failed",
                    exception
                )
            }
        }

        super.onDestroy()
    }
}
