
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo

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

        val activities = packageManager.queryIntentActivities(
            launcherIntent,
            0
        )

        val apps = activities.mapNotNull { resolveInfo ->

            val activityInfo = resolveInfo.activityInfo
                ?: return@mapNotNull null

            val packageName = activityInfo.packageName

            try {
                val applicationInfo =
                    packageManager.getApplicationInfo(packageName, 0)

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

        return apps
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    /**
     * Search installed launcher apps by name or package.
     */
    fun searchApps(query: String): List<SoftwareAppInfo> {

        if (query.isBlank()) {
            return emptyList()
        }

        val searchText = query.trim().lowercase()

        return getInstalledApps().filter {
            it.appName.lowercase().contains(searchText) ||
            it.packageName.lowercase().contains(searchText)
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

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(launchIntent)

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Find an app by its visible name and open it.
     */
    fun openAppByName(appName: String): Boolean {

        val app = searchApps(appName).firstOrNull()
            ?: return false

        return openApp(app.packageName)
    }

    /**
     * Open Android app information/settings page.
     */
    fun openAppSettings(packageName: String): Boolean {

        if (packageName.isBlank()) return false

        return try {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
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
                android.provider.Settings.ACTION_SETTINGS
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
                packageManager.getApplicationInfo(packageName, 0)

            packageManager.getApplicationLabel(
                applicationInfo
            ).toString()

        } catch (_: Exception) {
            null
        }
    }
}
