
package com.idraak.quantum

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * IDRAAK - Permission Manager
 *
 * Responsibility:
 * 1. Checks required Android runtime permissions.
 * 2. Requests missing runtime permissions.
 * 3. Checks Accessibility Service authorization.
 * 4. Opens Android Accessibility Settings.
 *
 * Does not handle voice recognition, AI requests,
 * command execution, or settings storage.
 */
internal class PermissionManager {

    companion object {

        const val REQUEST_CODE = 4101

        /**
         * Returns the runtime permissions required by IDRAAK.
         */
        fun getRequiredPermissions(): Array<String> {

            val permissions = mutableListOf(
                Manifest.permission.RECORD_AUDIO
            )

            // Notification permission is required on Android 13+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }

            return permissions.toTypedArray()
        }

        /**
         * Checks whether a specific permission is granted.
         */
        fun hasPermission(
            context: Context,
            permission: String
        ): Boolean {

            return context.checkSelfPermission(permission) ==
                PackageManager.PERMISSION_GRANTED
        }

        /**
         * Returns all currently missing runtime permissions.
         */
        fun getMissingPermissions(
            context: Context
        ): Array<String> {

            return getRequiredPermissions().filter { permission ->
                !hasPermission(context, permission)
            }.toTypedArray()
        }

        /**
         * Checks whether all required runtime permissions are granted.
         */
        fun hasAllRequiredPermissions(
            context: Context
        ): Boolean {

            return getMissingPermissions(context).isEmpty()
        }

        /**
         * Requests missing runtime permissions from the user.
         *
         * Returns true if a permission request was started.
         * Returns false if no permission is missing.
         */
        fun requestMissingPermissions(
            activity: Activity,
            requestCode: Int = REQUEST_CODE
        ): Boolean {

            val missingPermissions =
                getMissingPermissions(activity)

            if (missingPermissions.isEmpty()) {
                return false
            }

            activity.requestPermissions(
                missingPermissions,
                requestCode
            )

            return true
        }

        /**
         * Checks whether the specified Accessibility Service
         * has been enabled by the user in Android Settings.
         */
        fun isAccessibilityServiceEnabled(
            context: Context,
            serviceClass: Class<out AccessibilityService>
        ): Boolean {

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val expectedComponent =
                ComponentName(context, serviceClass).flattenToString()

            return enabledServices
                .split(":")
                .any { enabledService ->
                    enabledService.equals(
                        expectedComponent,
                        ignoreCase = true
                    )
                }
        }

        /**
         * Opens Android Accessibility Settings.
         *
         * The user must manually enable IDRAAK's service.
         */
        fun openAccessibilitySettings(
            context: Context
        ) {

            val intent = Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        }
    }
}

