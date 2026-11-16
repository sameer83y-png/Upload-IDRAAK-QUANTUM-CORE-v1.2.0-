
package com.idraak.quantum

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * IDRAAK - Main Activity
 *
 * Responsibility:
 * Acts as the main application entry point
 * and coordinates the application lifecycle.
 *
 * This initial foundation intentionally has
 * no dependency on Voice, Gemini, Controllers,
 * Settings, or other feature modules.
 *
 * Each feature will be integrated through
 * its dedicated component in later stages.
 */
class MainActivity : Activity() {

    companion object {
        private const val BACKGROUND_COLOR = 0xFF03071C.toInt()
        private const val TEXT_COLOR = 0xFF00FFFF.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindow()
        showInitialInterface()
    }

    /**
     * Configures the main application window.
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
     * Displays the initial IDRAAK application screen.
     *
     * This is a temporary foundation interface.
     * The actual launcher interface will be integrated
     * through LauncherHomeView in a later stage.
     */
    private fun showInitialInterface() {

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BACKGROUND_COLOR)
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "IDRAAK"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(TEXT_COLOR)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val status = TextView(this).apply {
            text = "CORE INITIALIZED"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 0)
        }

        rootLayout.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        rootLayout.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(rootLayout)
    }
}
