
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

                    /*
                     * Service का running होना यह नहीं बताता
                     * कि IDRAAK ACTIVE है या SLEEPING।
                     *
                     * Service TTS के दौरान भी pause हो सकती है,
                     * इसलिए false मिलने पर UI को forcefully
                     * inactive नहीं करेंगे।
                     */
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

        configureWindow()

        try {
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
                // Voice control is handled by dedicated components.
                // No visible microphone button is required.
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

        voiceSessionController.setWakeWordEnabled(
            settingsManager.wakeWordEnabled
        )

        showHome()
    }

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

    /**
     * पहले microphone permission माँगता है।
     * Notification permission अलग request में माँगी जाती है।
     */
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

    @Deprecated("Deprecated in Android API 23")
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

                val audioGranted =
                    checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                if (audioGranted) {

                    requestNotificationPermissionIfNeeded()
                    startVoiceSystem()

                } else {

                    voiceStarted = false

                    if (::homeView.isInitialized) {
                        homeView.setVoiceStatus(false)
                    }

                    Log.e(
                        TAG,
                        "Microphone permission was denied"
                    )
                }
            }

            REQUEST_NOTIFICATION_PERMISSION -> {
                // Notification permission का परिणाम
                // microphone permission को प्रभावित नहीं करता।
                if (
                    checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startVoiceSystem()
                }
            }
        }
    }

    /**
     * Persistent Vosk voice service शुरू करता है।
     */
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

            val language =
                settingsManager.voiceLanguage
                    .takeIf {
                        it.isNotBlank() && it != "auto"
                    } ?: "hi-IN"

            voiceSessionController.setWakeWordEnabled(
                settingsManager.wakeWordEnabled
            )

            /*
             * VoiceEngine service को शुरू करता है।
             * Recognized text receiver के माध्यम से
             * VoiceSessionController तक पहुँचता है।
             */
            voiceEngine.startListening(language)

            voiceStarted = true

            val awake =
                voiceSessionController.getVoiceState() !=
                    WakeWordController.VoiceState.SLEEPING

            homeView.setVoiceStatus(awake)

        } catch (exception: Exception) {

            voiceStarted = false
            homeView.setVoiceStatus(false)

            Log.e(
                TAG,
                "Voice startup failed",
                exception
            )
        }
    }

    private fun showHome() {

        settingsVisible = false
        setContentView(homeView)
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

    override fun onDestroy() {

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
            voiceSessionController.release()
        }

        /*
         * TTS और Activity-side resources release होते हैं।
         * यहाँ VoiceManager.stop() नहीं बुलाया जाता,
         * ताकि persistent foreground voice service
         * Activity destruction के कारण बंद न हो।
         */
        if (::voiceEngine.isInitialized) {
            voiceEngine.release()
        }

        super.onDestroy()
    }
}
