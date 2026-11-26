package com.idraak.quantum

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("idraak_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(preferences.getInt("background", Color.rgb(5, 10, 23)))
        }
        root.addView(TextView(this).apply {
            text = "IDRAAK CONTROL CENTER"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(preferences.getInt("accent", Color.rgb(67, 218, 255)))
            setPadding(0, 0, 0, 10)
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "PERSONALIZATION  •  DEVICE ACCESS  •  PRIVACY"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 26)
        }, matchWrap())

        addThemeButton(root, "QUANTUM CYAN", Color.rgb(67, 218, 255), Color.rgb(5, 10, 23))
        addThemeButton(root, "NEBULA VIOLET", Color.rgb(183, 132, 255), Color.rgb(12, 8, 27))
        addThemeButton(root, "EMERALD CORE", Color.rgb(91, 235, 170), Color.rgb(4, 18, 17))

        root.addView(TextView(this).apply {
            text = "DEVICE PERMISSIONS"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 28, 0, 8)
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "OPEN APP PERMISSIONS"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            }
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "BACK TO IDRAAK"
            setOnClickListener { finish() }
        }, matchWrap().apply { topMargin = 12 })
        setContentView(root)
    }

    private fun addThemeButton(root: LinearLayout, label: String, accent: Int, background: Int) {
        root.addView(Button(this).apply {
            text = label
            setTextColor(Color.BLACK)
            setBackgroundColor(accent)
            setOnClickListener {
                preferences.edit()
                    .putInt("accent", accent)
                    .putInt("background", background)
                    .apply()
                render()
            }
        }, matchWrap().apply { topMargin = 10 })
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
