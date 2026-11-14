
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class LauncherApp(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)

class LauncherEngine(private val context: Context) {

    private val packageManager: PackageManager =
        context.packageManager

    fun getInstalledApps(): List<LauncherApp> {

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return activities
            .mapNotNull { resolveInfo ->
                try {
                    val activity = resolveInfo.activityInfo
                    val packageName = activity.packageName

                    LauncherApp(
                        name = resolveInfo.loadLabel(packageManager)
                            .toString(),
                        packageName = packageName,
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    }

    fun searchApps(
        query: String,
        apps: List<LauncherApp>
    ): List<LauncherApp> {

        if (query.isBlank()) return apps

        return apps.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    fun launchApp(packageName: String): Boolean {

        return try {
            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName)
                    ?: return false

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)

            true
        } catch (_: Exception) {
            false
        }
    }
}
