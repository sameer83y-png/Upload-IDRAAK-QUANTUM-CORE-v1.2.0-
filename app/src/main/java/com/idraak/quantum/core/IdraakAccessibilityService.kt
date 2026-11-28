package com.idraak.quantum.core

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * User-enabled bridge for explicit, foreground screen-control commands.
 * Android requires the user to enable this service in system settings.
 */
class IdraakAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    private fun clickVisibleText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(query)
        for (node in matches) {
            var candidate: AccessibilityNodeInfo? = node
            var depth = 0
            while (candidate != null && depth < 8) {
                if (candidate.isClickable &&
                    candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) return true
                candidate = candidate.parent
                depth++
            }
        }
        return false
    }

    private fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (root.performAction(action)) return true
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var checked = 0
        while (queue.isNotEmpty() && checked < 300) {
            val node = queue.removeFirst()
            checked++
            if (node.isScrollable && node.performAction(action)) return true
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return false
    }

    private fun global(action: Int): Boolean = performGlobalAction(action)

    companion object {
        @Volatile private var activeService: IdraakAccessibilityService? = null

        fun isConnected(): Boolean = activeService != null

        fun clickText(text: String): Boolean =
            activeService?.clickVisibleText(text.trim()) == true

        fun scrollForward(): Boolean =
            activeService?.scroll(true) == true

        fun scrollBackward(): Boolean =
            activeService?.scroll(false) == true

        fun goBack(): Boolean =
            activeService?.global(GLOBAL_ACTION_BACK) == true

        fun goHome(): Boolean =
            activeService?.global(GLOBAL_ACTION_HOME) == true

        fun showNotifications(): Boolean =
            activeService?.global(GLOBAL_ACTION_NOTIFICATIONS) == true

        fun showQuickSettings(): Boolean =
            activeService?.global(GLOBAL_ACTION_QUICK_SETTINGS) == true
    }
}
