package com.idraak.quantum.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Re-arms reminders only when Android allows this app to post notifications. */
object ReminderPermissionRecovery {
    fun shouldRestore(apiLevel: Int, notificationsGranted: Boolean): Boolean =
        apiLevel < 33 || notificationsGranted

    fun restorePendingIfAllowed(context: Context): Boolean {
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!shouldRestore(Build.VERSION.SDK_INT, notificationsGranted)) return false
        ReminderReceiver.restorePending(context)
        return true
    }
}
