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
import com.idraak.quantum.core.IdraakAutoUpdater
import com.idraak.quantum.core.DownloadedIdraakUpdate
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.biometric.BiometricManager
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

        root.addView(TextView(this).apply {
            text = "CHOOSE YOUR CORE THEME"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 4)
        }, matchWrap())
        addThemeButton(root, "QUANTUM CYAN", Color.rgb(67, 218, 255), Color.rgb(5, 10, 23))
        addThemeButton(root, "NEBULA VIOLET", Color.rgb(183, 132, 255), Color.rgb(12, 8, 27))
        addThemeButton(root, "EMERALD CORE", Color.rgb(91, 235, 170), Color.rgb(4, 18, 17))
        addThemeButton(root, "SOLAR GOLD", Color.rgb(255, 202, 92), Color.rgb(24, 17, 5))
        addThemeButton(root, "CRIMSON PULSE", Color.rgb(255, 105, 125), Color.rgb(25, 6, 13))
        addThemeButton(root, "ARCTIC BLUE", Color.rgb(145, 205, 255), Color.rgb(5, 15, 29))
        addThemeButton(root, "ROSE QUARTZ", Color.rgb(255, 164, 205), Color.rgb(27, 10, 20))
        addThemeButton(root, "OBSIDIAN LIME", Color.rgb(190, 240, 92), Color.rgb(10, 15, 5))

        root.addView(Button(this).apply {
            text = "CHECK & INSTALL IDRAAK UPDATES"
            setOnClickListener {
                android.widget.Toast.makeText(
                    this@SettingsActivity,
                    "Checking official release and verifying APK…",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                val updater = IdraakAutoUpdater(this@SettingsActivity)
                Thread {
                    val outcome: Any? = try {
                        updater.checkAndDownload(BuildConfig.VERSION_NAME)
                    } catch (error: Exception) {
                        error
                    }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        when (outcome) {
                            null -> android.widget.Toast.makeText(
                                this@SettingsActivity,
                                "Idraak is up to date.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            is Exception -> android.app.AlertDialog.Builder(this@SettingsActivity)
                                .setTitle("IDRAAK UPDATE")
                                .setMessage(outcome.message ?: "Update verification failed. No changes were made.")
                                .setPositiveButton("OK", null)
                                .show()
                            is DownloadedIdraakUpdate -> android.app.AlertDialog.Builder(this@SettingsActivity)
                                .setTitle("Verified update: " + outcome.version)
                                .setMessage("The official APK was downloaded. Its SHA-256, package name and signing identity passed verification. Android will show its own installation screen.")
                                .setNegativeButton("LATER", null)
                                .setPositiveButton("INSTALL") { _, _ ->
                                    try {
                                        val opened = updater.install(outcome)
                                        if (!opened) {
                                            android.widget.Toast.makeText(
                                                this@SettingsActivity,
                                                "Allow Idraak to install apps on the next Android screen, then check for updates again.",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } catch (error: Exception) {
                                        android.widget.Toast.makeText(
                                            this@SettingsActivity,
                                            error.message ?: "Could not open Android installer.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                .show()
                            else -> android.widget.Toast.makeText(
                                this@SettingsActivity,
                                "Update result was not recognized. Nothing was installed.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            }
        }, matchWrap().apply { topMargin = 16 })


        root.addView(TextView(this).apply {
            text = "HYBRID APP PLUGIN MANAGER"
            textSize = 15f
            setTextColor(preferences.getInt("accent", Color.rgb(67, 218, 255)))
            setPadding(0, 28, 0, 8)
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "Discover installed apps, connect only after your approval, and disconnect whenever you want. Unsupported apps continue to use Android intents and Accessibility where available."
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(4, 4, 4, 8)
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "OPEN APP PLUGIN MANAGER"
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, PluginManagerActivity::class.java))
            }
        }, matchWrap())

        addAppLockSection(root)
        addReleaseSigningSection(root)

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
        root.addView(Button(this).apply {
            text = "GET GEMINI API KEY — OPEN GOOGLE AI STUDIO"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
            }
        }, matchWrap().apply { topMargin = 8 })
        root.addView(TextView(this).apply {
            text = "How to get your key: 1) Open Google AI Studio. 2) Sign in to your Google account. 3) Open API keys and choose Create API key. 4) Copy the key and paste it above, then tap SAVE GEMINI SETTINGS. Keep the key private. Check Google AI Studio usage and billing before using paid services."
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(4, 6, 4, 12)
        }, matchWrap())
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
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
    }

    private fun addAppLockSection(root: LinearLayout) {
        root.addView(TextView(this).apply {
            text = "APP LOCK & PRIVACY"
            textSize = 15f
            setTextColor(preferences.getInt("accent", Color.rgb(67, 218, 255)))
            setPadding(0, 28, 0, 8)
        }, matchWrap())
        val manager = com.idraak.quantum.core.AppLockManager(this)
        root.addView(TextView(this).apply {
            text = if (manager.isEnabled()) "App Lock: ENABLED" else "App Lock: NOT SET"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(22, 34, 54))
        }, matchWrap())
        root.addView(Button(this).apply {
            text = if (manager.isEnabled()) "CHANGE APP PASSWORD" else "SET APP PASSWORD"
            setOnClickListener { showSetAppPasswordDialog() }
        }, matchWrap().apply { topMargin = 8 })
        val biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val biometricEnabled = preferences.getBoolean("face_unlock_enabled", false)
        root.addView(Button(this).apply {
            text = if (biometricEnabled) "DISABLE FACE / BIOMETRIC UNLOCK"
                else "ENABLE FACE / BIOMETRIC UNLOCK"
            isEnabled = biometricAvailable || biometricEnabled
            setOnClickListener {
                if (!biometricAvailable && !biometricEnabled) {
                    android.widget.Toast.makeText(
                        this@SettingsActivity,
                        "इस फोन में compatible biometric enrollment उपलब्ध नहीं है। पहले Android Settings में Face/Fingerprint सेट करें।",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    preferences.edit().putBoolean("face_unlock_enabled", !biometricEnabled).apply()
                    render()
                }
            }
        }, matchWrap().apply { topMargin = 8 })
        root.addView(TextView(this).apply {
            text = "Biometric verification is handled by Android. If this phone supports face unlock, Android may show it; otherwise it may use its enrolled fingerprint or another supported biometric. Idraak never receives or stores your face image. If you forget your password, biometric verification lets you RESET it; the old password cannot be read back because only its salted hash is stored."
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 12)
        }, matchWrap())
        if (manager.isEnabled()) {
            root.addView(Button(this).apply {
                text = "TURN OFF APP LOCK"
                setOnClickListener { showDisableAppLockDialog() }
            }, matchWrap().apply { topMargin = 6 })
        }
        root.addView(TextView(this).apply {
            text = "Set a password of 6–64 characters. Idraak stores only a salted PBKDF2 verification hash, not the password itself. If biometric recovery is enabled, verify with Android biometrics to reset a forgotten password."
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 12)
        }, matchWrap())
    }

    private fun showSetAppPasswordDialog() {
        val manager = com.idraak.quantum.core.AppLockManager(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 4)
        }
        val current = if (manager.isEnabled()) EditText(this).apply {
            hint = "Current password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else null
        if (current != null) layout.addView(current, matchWrap())
        val first = EditText(this).apply {
            hint = "New password (6–64 characters)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val second = EditText(this).apply {
            hint = "Confirm new password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(first, matchWrap())
        layout.addView(second, matchWrap().apply { topMargin = 8 })
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("SET IDRAAK APP PASSWORD")
            .setMessage("The password is never displayed again after saving.")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val oldChars = current?.text?.toString()?.toCharArray()
                val newText = first.text.toString()
                val confirmText = second.text.toString()
                val oldValid = oldChars == null || manager.verify(oldChars)
                oldChars?.fill('\u0000')
                when {
                    !oldValid -> android.widget.Toast.makeText(this, "Current password is incorrect.", android.widget.Toast.LENGTH_LONG).show()
                    newText.length !in 6..64 -> android.widget.Toast.makeText(this, "Use 6–64 characters.", android.widget.Toast.LENGTH_LONG).show()
                    newText != confirmText -> android.widget.Toast.makeText(this, "Passwords do not match.", android.widget.Toast.LENGTH_LONG).show()
                    else -> {
                        val chars = newText.toCharArray()
                        val saved = manager.setPassword(chars)
                        chars.fill('\u0000')
                        if (saved) {
                            android.widget.Toast.makeText(this, "App password saved on this device.", android.widget.Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                            render()
                        } else {
                            android.widget.Toast.makeText(this, "Could not save app password.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showDisableAppLockDialog() {
        val manager = com.idraak.quantum.core.AppLockManager(this)
        val input = EditText(this).apply {
            hint = "Current app password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("TURN OFF APP LOCK")
            .setMessage("Enter your current password to disable the lock.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("TURN OFF", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chars = input.text.toString().toCharArray()
                val valid = manager.verify(chars)
                chars.fill('\u0000')
                if (valid) {
                    manager.disable()
                    dialog.dismiss()
                    android.widget.Toast.makeText(this, "App Lock disabled.", android.widget.Toast.LENGTH_SHORT).show()
                    render()
                } else {
                    input.text.clear()
                    android.widget.Toast.makeText(this, "Password incorrect.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun addReleaseSigningSection(root: LinearLayout) {
        root.addView(TextView(this).apply {
            text = "RELEASE SIGNING & SELF-UPDATES"
            textSize = 15f
            setTextColor(preferences.getInt("accent", Color.rgb(67, 218, 255)))
            setPadding(0, 28, 0, 8)
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "This is a developer publishing credential, not an app password. Idraak never receives or reads it. Create and keep your signing keystore privately, then add these four values as GitHub Actions Secrets: IDRAAK_KEYSTORE_BASE64, IDRAAK_KEYSTORE_PASSWORD, IDRAAK_KEY_ALIAS, IDRAAK_KEY_PASSWORD. Do not paste them into Idraak, chat, source code, or a public file. GitHub uses them only to sign official release APKs."
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 10)
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "OPEN GITHUB RELEASE SECRETS"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/sameer83y-png/Upload-IDRAAK-QUANTUM-CORE-v1.2.0-/settings/secrets/actions"
                )))
            }
        }, matchWrap())
    }

    private fun requestRuntimePermissions() {
        val requested = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requested.add(Manifest.permission.RECORD_AUDIO)
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requested.add(Manifest.permission.READ_CONTACTS)
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
        val contacts = if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) "Granted" else "Not granted"
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
