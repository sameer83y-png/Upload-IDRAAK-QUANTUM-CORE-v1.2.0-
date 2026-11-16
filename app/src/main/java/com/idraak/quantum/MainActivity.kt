
package com.idraak.quantum

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * IDRAAK - Main Activity
 *
 * Responsibility:
 * Application entry point and lifecycle coordinator.
 *
 * Starts the actual LauncherHomeView.
 * Feature-specific logic remains in dedicated modules.
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "IDRAAK_MAIN_ACTIVITY"
        private const val BACKGROUND_COLOR = 0xFF03071C.toInt()
        private const val TEXT_COLOR = 0xFF00FFFF.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindow()
        launchMainInterface()
    }

    /**
     * Configure the application window.
     */
    private fun configureWindow() {
        window.statusBarColor = BACKGROUND_COLOR
        window.navigationBarColor = BACKGROUND_COLOR

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    /**
     * Launch the actual IDRAAK home interface.
     */
    private fun launchMainInterface() {
        try {
            val homeView = LauncherHomeView(this) {
                // Voice interaction is managed by
                // the dedicated voice components.
            }

            setContentView(homeView)

            Log.i(TAG, "IDRAAK launcher interface started")

        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Failed to start IDRAAK launcher interface",
                exception
            )

            showStartupError(exception)
        }
    }

    /**
     * Display a diagnostic screen if launcher startup fails.
     */
    private fun showStartupError(exception: Exception) {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(BACKGROUND_COLOR)
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "IDRAAK STARTUP ERROR"
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTextColor(TEXT_COLOR)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val details = TextView(this).apply {
            text = exception.message
                ?: exception.javaClass.simpleName
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 0)
        }

        rootLayout.addView(title)
        rootLayout.addView(details)

        setContentView(rootLayout)
    }
}
