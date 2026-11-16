package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
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
                packageManager.queryIntentActivities(launcherIntent, 0)
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return activities.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName
                ?: return@mapNotNull null

            try {
                val applicationInfo = getApplicationInfo(packageName)
                    ?: return@mapNotNull null

                val isSystem =
                    applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

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
        }.distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase(Locale.ROOT) }
    }

    fun searchApps(query: String): List<SoftwareAppInfo> {
        if (query.isBlank()) return emptyList()
        val term = query.trim().lowercase(Locale.ROOT)

        return getInstalledApps().filter {
            it.appName.lowercase(Locale.ROOT).contains(term) ||
                it.packageName.lowercase(Locale.ROOT).contains(term)
        }
    }

    fun isAppInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }

    fun openApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openAppByName(appName: String): Boolean {
        if (appName.isBlank()) return false
        val matches = searchApps(appName)
        if (matches.size != 1) return false
        return openApp(matches.first().packageName)
    }

    fun openAppSettings(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

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

    fun openSystemSettings(): Boolean {
        return try {
            appContext.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getAppName(packageName: String): String? {
        if (packageName.isBlank()) return null
        return try {
            val info = getApplicationInfo(packageName) ?: return null
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            null
        }
    }

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
                packageManager.getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
