
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

        try {
            settingsManager = SettingsManager(this)

            homeView = LauncherHomeView(
                context = this,
                onSettingsClick = {
                    showSettings()
                },
                onVoiceClick = {
                    // IDRAAK में कोई visible mic button नहीं होगा।
                    // Voice session का संचालन dedicated voice components करेंगे।
                }
            )

            showHome()
        } catch (exception: Exception) {
            showStartupError(exception)
        }
    }

    private fun configureWindow() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun showHome() {
        settingsVisible = false
        setContentView(homeView)

        if (homeView.isAttachedToWindow) {
            homeView.startAnimation()
        }
    }

    private fun showSettings() {
        if (!::settingsManager.isInitialized) return

        settingsVisible = true

        val settingsView = SettingsView(
            context = this,
            settingsManager = settingsManager,
            onClose = {
                showHome()
            }
        )

        setContentView(settingsView)
    }

    private fun showStartupError(exception: Exception) {
        val errorView = TextView(this).apply {
            text = "IDRAAK STARTUP ERROR\n\n${exception.message ?: "Unknown error"}"
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

        if (::homeView.isInitialized && homeView.isAppDrawerOpen) {
            homeView.closeAppDrawer()
            return
        }

        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::homeView.isInitialized) {
            homeView.stopAnimation()
        }

        super.onDestroy()
    }
}
