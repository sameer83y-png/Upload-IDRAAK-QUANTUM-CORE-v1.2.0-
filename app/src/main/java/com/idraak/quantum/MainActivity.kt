
package com.idraak.quantum

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var homeView: LauncherHomeView

    private var settingsVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindow()
        initializeIDRAAK()
    }

    private fun configureWindow() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun initializeIDRAAK() {
        try {
            settingsManager = SettingsManager(this)

            homeView = LauncherHomeView(
                context = this,
                onSettingsClick = {
                    showSettings()
                },
                onVoiceClick = {
                    // Voice interaction का संचालन dedicated
                    // voice components करेंगे।
                    // Home screen पर कोई mic button नहीं होगा।
                }
            )

            showHome()

        } catch (exception: Exception) {
            showStartupError(exception)
        }
    }

    private fun showHome() {
        settingsVisible = false
        setContentView(homeView)
    }

    private fun showSettings() {
        if (!::settingsManager.isInitialized) {
            return
        }

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
}
