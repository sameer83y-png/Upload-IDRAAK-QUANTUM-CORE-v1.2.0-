package com.idraak.quantum

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Android permissions और विशेष access की स्थिति जाँचता है।
 * Permission prompts Activity से ही माँगे जाने चाहिए।
 */
class PermissionManager(context: Context) {

    private val appContext = context.applicationContext

    fun hasMicrophonePermission(): Boolean =
        hasPermission(Manifest.permission.RECORD_AUDIO)

    fun hasCameraPermission(): Boolean =
        hasPermission(Manifest.permission.CAMERA)

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    fun hasPermission(permission: String): Boolean =
        appContext.checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED

    fun isAccessibilityServiceEnabled(): Boolean {
        val manager = appContext.getSystemService(
            Context.ACCESSIBILITY_SERVICE
        ) as? AccessibilityManager ?: return false

        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any {
            it.resolveInfo.serviceInfo.packageName == appContext.packageName &&
                it.resolveInfo.serviceInfo.name ==
                IDRAAKAccessibilityService::class.java.name
        }
    }

    fun openAccessibilitySettings(): Boolean =
        openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun openAppDetailsSettings(): Boolean =
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }

    private fun openSystemSettings(action: String): Boolean =
        try {
            appContext.startActivity(
                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
}
