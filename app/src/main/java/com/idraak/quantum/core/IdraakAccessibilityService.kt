package com.idraak.quantum.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled accessibility bridge for explicit Idraak control commands.
 * Android requires the user to enable this service in system settings.
 */
class IdraakAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event observation is intentionally minimal. Command execution is added
        // through explicit, user-triggered actions rather than background tapping.
    }

    override fun onInterrupt() = Unit

    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
}
