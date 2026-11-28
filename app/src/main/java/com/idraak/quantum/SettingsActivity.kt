package com.idraak.quantum

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import com.idraak.quantum.core.GeminiKeyStore
import com.idraak.quantum.core.GeminiBrainClient
import android.widget.LinearLayout
import android.widget.TextView
import com.idraak.quantum.core.IdraakAccessibilityService

class SettingsActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("idraak_settings", MODE_PRIVATE) }
    private lateinit var permissionStatus: TextView
    private lateinit var geminiKeyInput: EditText
    private lateinit var geminiModelInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::permissionStatus.isInitialized) updatePermissionStatus()
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
            text = "GEMINI BRAIN (OPTIONAL)"
            textSize = 15f
            setTextColor(preferences.getInt("accent", Color.rgb(67, 218, 255)))
            setPadding(0, 26, 0, 8)
        }, matchWrap())
        geminiKeyInput = EditText(this).apply {
            hint = "Paste your own Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(22, 34, 54))
        }
        root.addView(geminiKeyInput, matchWrap())
        geminiModelInput = EditText(this).apply {
            hint = "Gemini model (default: " + GeminiBrainClient.DEFAULT_MODEL + ")"
            setText(preferences.getString("gemini_model", GeminiBrainClient.DEFAULT_MODEL))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(22, 34, 54))
        }
        root.addView(geminiModelInput, matchWrap().apply { topMargin = 8 })
        root.addView(Button(this).apply {
            text = "SAVE GEMINI SETTINGS"
            setOnClickListener {
                GeminiKeyStore(this@SettingsActivity).save(geminiKeyInput.text.toString())
                val model = geminiModelInput.text.toString().trim().ifBlank { GeminiBrainClient.DEFAULT_MODEL }
                preferences.edit().putString("gemini_model", model).apply()
                android.widget.Toast.makeText(this@SettingsActivity, "Gemini settings saved securely on this device.", android.widget.Toast.LENGTH_LONG).show()
                geminiKeyInput.text.clear()
            }
        }, matchWrap().apply { topMargin = 8 })
        root.addView(Button(this).apply {
            text = "REMOVE SAVED GEMINI KEY"
            setOnClickListener {
                GeminiKeyStore(this@SettingsActivity).clear()
                geminiKeyInput.text.clear()
                android.widget.Toast.makeText(this@SettingsActivity, "Gemini key removed.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }, matchWrap().apply { topMargin = 6 })
        root.addView(TextView(this).apply {
            text = "Gemini is optional and uses your own API key. Unknown commands may be sent to Google when Gemini is enabled. Phone actions remain controlled by Idraak's offline engine; Gemini cannot execute them directly."
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 14)
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "IDRAAK PERMISSION MANAGER"
            textSize = 15f
            setTextColor(preferences.getInt("accent", Color.rgb(67, 218, 255)))
            setPadding(0, 30, 0, 8)
        }, matchWrap())

        permissionStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(22, 34, 54))
        }
        root.addView(permissionStatus, matchWrap())
        updatePermissionStatus()

        root.addView(Button(this).apply {
            text = "REQUEST REQUIRED APP PERMISSIONS"
            setOnClickListener { requestRuntimePermissions() }
        }, matchWrap().apply { topMargin = 10 })
        root.addView(Button(this).apply {
            text = "ENABLE ACCESSIBILITY CONTROL"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, matchWrap().apply { topMargin = 8 })
        root.addView(Button(this).apply {
            text = "OPEN IDRAAK SYSTEM PERMISSIONS"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }, matchWrap().apply { topMargin = 8 })
        root.addView(Button(this).apply {
            text = "CHECK PERMISSION STATUS"
            setOnClickListener { updatePermissionStatus() }
        }, matchWrap().apply { topMargin = 8 })
        root.addView(TextView(this).apply {
            text = "Android keeps control of sensitive permissions. Idraak only requests permissions needed for its features; Accessibility must be enabled by you in Android Settings."
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(4, 12, 4, 12)
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "BACK TO IDRAAK"
            setOnClickListener { finish() }
        }, matchWrap().apply { topMargin = 12 })
        setContentView(root)
    }

    private fun requestRuntimePermissions() {
        val requested = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requested.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requested.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (requested.isEmpty()) {
            updatePermissionStatus()
            return
        }
        requestPermissions(requested.toTypedArray(), REQUEST_REQUIRED_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_REQUIRED_PERMISSIONS) updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        if (!::permissionStatus.isInitialized) return
        val microphone = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Granted" else "Not granted"
        val notifications = when {
            Build.VERSION.SDK_INT < 33 -> "Managed by Android (no runtime prompt on this version)"
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> "Granted"
            else -> "Not granted"
        }
        val accessibility = if (isAccessibilityEnabled()) "Enabled" else "Not enabled"
        permissionStatus.text = "Microphone: $microphone\nNotifications: $notifications\nAccessibility control: $accessibility"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, IdraakAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        return splitter.any { it.equals(expected, ignoreCase = true) }
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

    companion object {
        private const val REQUEST_REQUIRED_PERMISSIONS = 4301
    }
}
