package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.Locale

data class LauncherApp(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)

class LauncherEngine(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager =
        appContext.packageManager

    companion object {
        private const val TAG = "IDRAAK_LAUNCHER_ENGINE"
    }

    /**
     * Returns all installed launchable applications.
     * One entry is maintained per package.
     */
    fun getInstalledApps(): List<LauncherApp> {

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = try {

            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to query installed applications",
                e
            )

            return emptyList()
        }

        return try {

            activities.mapNotNull { resolveInfo ->

                try {

                    val activity = resolveInfo.activityInfo
                        ?: return@mapNotNull null

                    val packageName = activity.packageName
                        ?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                    val appName = try {
                        resolveInfo.loadLabel(packageManager)
                            ?.toString()
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: packageName
                    } catch (_: Exception) {
                        packageName
                    }

                    val appIcon = try {
                        resolveInfo.loadIcon(packageManager)
                    } catch (_: Exception) {
                        packageManager.defaultActivityIcon
                    }

                    LauncherApp(
                        name = appName,
                        packageName = packageName,
                        icon = appIcon
                    )

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "Skipping an unavailable application",
                        e
                    )

                    null
                }
            }
                .distinctBy { it.packageName }
                .sortedBy {
                    it.name.lowercase(Locale.ROOT)
                }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Application list processing failed",
                e
            )

            emptyList()
        }
    }

    /**
     * Searches applications by visible name or package name.
     */
    fun searchApps(
        query: String,
        apps: List<LauncherApp>
    ): List<LauncherApp> {

        val searchQuery = query.trim()

        if (searchQuery.isEmpty()) {
            return apps
        }

        return apps.filter { app ->

            app.name.contains(
                searchQuery,
                ignoreCase = true
            ) || app.packageName.contains(
                searchQuery,
                ignoreCase = true
            )
        }
    }

    /**
     * Launches an application using its package name.
     */
    fun launchApp(packageName: String): Boolean {

        if (packageName.isBlank()) {
            Log.w(TAG, "Cannot launch an empty package name")
            return false
        }

        return try {

            val launchIntent =
                packageManager.getLaunchIntentForPackage(
                    packageName
                ) ?: run {
                    Log.w(
                        TAG,
                        "No launch intent found for $packageName"
                    )
                    return false
                }

            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            appContext.startActivity(launchIntent)

            true

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Permission denied while launching $packageName",
                e
            )

            false

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to launch $packageName",
                e
            )

            false
        }
    }
}
