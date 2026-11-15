
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.util.Locale

data class SoftwareAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean
)

class SoftwareController(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    /**
     * Returns all apps that have a launcher icon.
     */
    fun getInstalledApps(
        includeSystemApps: Boolean = true
    ): List<SoftwareAppInfo> {

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(
                    launcherIntent,
                    0
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return activities.mapNotNull { resolveInfo ->

            val activityInfo = resolveInfo.activityInfo
                ?: return@mapNotNull null

            val packageName = activityInfo.packageName

            try {
                val applicationInfo = getApplicationInfo(packageName)
                    ?: return@mapNotNull null

                val isSystem = (
                    applicationInfo.flags and
                        ApplicationInfo.FLAG_SYSTEM
                    ) != 0

                if (!includeSystemApps && isSystem) {
                    return@mapNotNull null
                }

                SoftwareAppInfo(
                    appName = packageManager.getApplicationLabel(
                        applicationInfo
                    ).toString(),
                    packageName = packageName,
                    isSystemApp = isSystem
                )

            } catch (_: Exception) {
                null
            }
        }
            .distinctBy { it.packageName }
            .sortedBy {
                it.appName.lowercase(Locale.ROOT)
            }
    }

    /**
     * Search installed launcher apps by name or package.
     */
    fun searchApps(query: String): List<SoftwareAppInfo> {

        if (query.isBlank()) {
            return emptyList()
        }

        val searchText = query.trim().lowercase(Locale.ROOT)

        return getInstalledApps().filter {
            it.appName.lowercase(Locale.ROOT).contains(searchText) ||
                it.packageName.lowercase(Locale.ROOT).contains(searchText)
        }
    }

    /**
     * Check whether an app is installed and launchable.
     */
    fun isAppInstalled(packageName: String): Boolean {

        if (packageName.isBlank()) return false

        return try {
            packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launch an app using its package name.
     */
    fun openApp(packageName: String): Boolean {

        if (packageName.isBlank()) return false

        return try {
            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName)
                    ?: return false

            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            appContext.startActivity(launchIntent)

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Find an app by its visible name.
     *
     * Does not launch an arbitrary app when multiple
     * applications match the requested name.
     */
    fun openAppByName(appName: String): Boolean {

        if (appName.isBlank()) return false

        val matches = searchApps(appName)

        if (matches.size != 1) {
            return false
        }

        return openApp(matches.first().packageName)
    }

    /**
     * Open Android app information/settings page.
     */
    fun openAppSettings(packageName: String): Boolean {

        if (packageName.isBlank()) return false

        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            appContext.startActivity(intent)
            true

        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ask Android to show its uninstall confirmation screen.
     * The user must confirm the uninstall.
     */
    fun requestUninstall(packageName: String): Boolean {

        if (packageName.isBlank()) return false

        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            appContext.startActivity(intent)
            true

        } catch (_: Exception) {
            false
        }
    }

    /**
     * Open Android system settings.
     */
    fun openSystemSettings(): Boolean {

        return try {
            val intent = Intent(
                Settings.ACTION_SETTINGS
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            appContext.startActivity(intent)
            true

        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get the visible app name from its package name.
     */
    fun getAppName(packageName: String): String? {

        if (packageName.isBlank()) return null

        return try {
            val applicationInfo =
                getApplicationInfo(packageName) ?: return null

            packageManager.getApplicationLabel(
                applicationInfo
            ).toString()

        } catch (_: Exception) {
            null
        }
    }

    /**
     * Android-version-compatible ApplicationInfo lookup.
     */
    private fun getApplicationInfo(
        packageName: String
    ): ApplicationInfo? {

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(
                    packageName,
                    0
                )
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
