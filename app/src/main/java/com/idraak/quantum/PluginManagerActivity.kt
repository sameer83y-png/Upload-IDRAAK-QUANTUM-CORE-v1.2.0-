package com.idraak.quantum

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * User-approved app connector registry.
 *
 * A connection records the user's choice to make an installed app available
 * to Idraak's app-launch/share workflows. It does not install or execute
 * third-party plugin code and does not bypass Android permissions.
 */
class PluginManagerActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private val connectedPackages: MutableSet<String>
        get() = prefs.getStringSet(KEY_CONNECTED_PACKAGES, emptySet())
            ?.toMutableSet() ?: mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val background = getSharedPreferences("idraak_settings", MODE_PRIVATE)
            .getInt("background", Color.rgb(5, 10, 23))
        val accent = getSharedPreferences("idraak_settings", MODE_PRIVATE)
            .getInt("accent", Color.rgb(67, 218, 255))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 28)
            setBackgroundColor(background)
        }
        content.addView(TextView(this).apply {
            text = "IDRAAK APP PLUGINS"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(accent)
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "HYBRID CONNECTOR MANAGER"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 6, 0, 18)
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "Idraak will discover launchable apps on this phone. Tap CONNECT and approve each app yourself. A connection makes the app available to Idraak's supported Android launch/share workflows. Accessibility remains the fallback for visible controls when you enable it."
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(22, 34, 54))
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "SECURITY: This does not install third-party code, read private app data, or grant hidden permissions. Direct app features work only when that app exposes a supported Android integration. Android confirmation and app restrictions still apply."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(4, 12, 4, 18)
        }, matchWrap())

        val apps = try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(launcherIntent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        } catch (_: Exception) {
            emptyList()
        }

        if (apps.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "No launchable apps were found."
                setTextColor(Color.WHITE)
                textSize = 14f
            }, matchWrap())
        } else {
            content.addView(TextView(this).apply {
                text = "DISCOVERED APPS  •  ${apps.size}"
                textSize = 14f
                setTextColor(accent)
                setPadding(0, 8, 0, 10)
            }, matchWrap())
            apps.forEach { info ->
                val appName = info.loadLabel(packageManager).toString()
                val appPackage = info.activityInfo.packageName
                val isConnected = connectedPackages.contains(appPackage)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12, 10, 12, 10)
                    setBackgroundColor(Color.rgb(18, 29, 47))
                }
                card.addView(TextView(this).apply {
                    text = appName
                    textSize = 15f
                    setTextColor(Color.WHITE)
                }, matchWrap())
                card.addView(TextView(this).apply {
                    text = appPackage + if (isConnected) "\\nCONNECTED" else "\\nNot connected"
                    textSize = 10f
                    setTextColor(if (isConnected) accent else Color.LTGRAY)
                    setPadding(0, 3, 0, 5)
                }, matchWrap())
                card.addView(Button(this).apply {
                    text = if (isConnected) "DISCONNECT" else "CONNECT"
                    setOnClickListener {
                        if (isConnected) confirmDisconnect(appName, appPackage)
                        else confirmConnect(appName, appPackage)
                    }
                }, matchWrap())
                if (isConnected) {
                    card.addView(Button(this).apply {
                        text = "OPEN $appName"
                        setOnClickListener {
                            val launch = packageManager.getLaunchIntentForPackage(appPackage)
                            if (launch != null) {
                                try {
                                    startActivity(launch)
                                } catch (_: Exception) {
                                    toast("Android could not open $appName.")
                                }
                            } else {
                                toast("No launch action is available for $appName.")
                            }
                        }
                    }, matchWrap().apply { topMargin = 4 })
                }
                content.addView(card, matchWrap().apply { topMargin = 8 })
            }
        }

        content.addView(Button(this).apply {
            text = "BACK TO SETTINGS"
            setOnClickListener { finish() }
        }, matchWrap().apply { topMargin = 18 })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        setContentView(scroll)
    }

    private fun confirmConnect(appName: String, appPackage: String) {
        AlertDialog.Builder(this)
            .setTitle("Connect $appName?")
            .setMessage("Allow Idraak to add $appName to your personal app connector list? Idraak will use only supported Android app entry points. This does not grant access to private data or enable hidden actions.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CONNECT") { _, _ ->
                val updated = connectedPackages
                updated.add(appPackage)
                prefs.edit().putStringSet(KEY_CONNECTED_PACKAGES, updated).apply()
                toast("$appName connected.")
                render()
            }
            .show()
    }

    private fun confirmDisconnect(appName: String, appPackage: String) {
        AlertDialog.Builder(this)
            .setTitle("Disconnect $appName?")
            .setMessage("Remove this app from Idraak's connector list? You can connect it again later.")
            .setNegativeButton("KEEP", null)
            .setPositiveButton("DISCONNECT") { _, _ ->
                val updated = connectedPackages
                updated.remove(appPackage)
                prefs.edit().putStringSet(KEY_CONNECTED_PACKAGES, updated).apply()
                toast("$appName disconnected.")
                render()
            }
            .show()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    companion object {
        private const val PREFS_NAME = "idraak_plugin_registry"
        private const val KEY_CONNECTED_PACKAGES = "connected_packages"
    }
}
