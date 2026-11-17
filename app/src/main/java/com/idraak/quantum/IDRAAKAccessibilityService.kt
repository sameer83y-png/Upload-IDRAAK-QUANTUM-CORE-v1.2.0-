package com.idraak.quantum

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class IDRAAKAccessibilityService : AccessibilityService() {

    companion object {

        @Volatile
        var latestVisibleText: String = ""

        @Volatile
        private var activeService: IDRAAKAccessibilityService? = null

        fun isConnected(): Boolean = activeService != null

        fun getScreenText(): String = latestVisibleText

        private fun clickNodeOrParent(
            node: AccessibilityNodeInfo
        ): Boolean {
            var current: AccessibilityNodeInfo? =
                AccessibilityNodeInfo.obtain(node)

            try {
                while (current != null) {
                    val parent = current.parent

                    val clicked =
                        current.isClickable &&
                        current.isEnabled &&
                        current.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )

                    current.recycle()

                    if (clicked) {
                        parent?.recycle()
                        return true
                    }

                    current = parent
                }

                return false
            } catch (_: Exception) {
                current?.recycle()
                return false
            }
        }

        fun clickText(text: String): Boolean {
            if (text.isBlank()) return false

            val service = activeService ?: return false

            return try {
                val root = service.rootInActiveWindow
                    ?: return false

                try {
                    val nodes =
                        root.findAccessibilityNodeInfosByText(text)

                    for (node in nodes) {
                        try {
                            if (clickNodeOrParent(node)) {
                                return true
                            }
                        } finally {
                            node.recycle()
                        }
                    }

                    false
                } finally {
                    root.recycle()
                }
            } catch (_: Exception) {
                false
            }
        }

        fun clickViewId(viewId: String): Boolean {
            if (viewId.isBlank()) return false

            val service = activeService ?: return false

            return try {
                val root = service.rootInActiveWindow
                    ?: return false

                try {
                    val nodes =
                        root.findAccessibilityNodeInfosByViewId(viewId)

                    for (node in nodes) {
                        try {
                            if (clickNodeOrParent(node)) {
                                return true
                            }
                        } finally {
                            node.recycle()
                        }
                    }

                    false
                } finally {
                    root.recycle()
                }
            } catch (_: Exception) {
                false
            }
        }

        fun tap(x: Float, y: Float): Boolean {
            val service = activeService ?: return false
            if (x < 0f || y < 0f) return false

            return try {
                val path = Path().apply {
                    moveTo(x, y)
                }

                val gesture = GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            path,
                            0,
                            100
                        )
                    )
                    .build()

                service.dispatchGesture(gesture, null, null)
            } catch (_: Exception) {
                false
            }
        }

        fun swipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            duration: Long = 400L
        ): Boolean {
            val service = activeService ?: return false

            if (
                startX < 0f || startY < 0f ||
                endX < 0f || endY < 0f ||
                duration <= 0L
            ) {
                return false
            }

            return try {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }

                val safeDuration = duration.coerceIn(100L, 2000L)

                val gesture = GestureDescription.Builder()
                    .addStroke(
                        GestureDescription.StrokeDescription(
                            path,
                            0,
                            safeDuration
                        )
                    )
                    .build()

                service.dispatchGesture(gesture, null, null)
            } catch (_: Exception) {
                false
            }
        }

        fun scrollDown(): Boolean {
            return scroll(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            )
        }

        fun scrollUp(): Boolean {
            return scroll(
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            )
        }

        private fun scroll(action: Int): Boolean {
            val service = activeService ?: return false

            return try {
                val root = service.rootInActiveWindow
                    ?: return false

                try {
                    findScrollableAndPerform(root, action)
                } finally {
                    root.recycle()
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun findScrollableAndPerform(
            node: AccessibilityNodeInfo,
            action: Int
        ): Boolean {
            if (node.isScrollable && node.isEnabled) {
                if (node.performAction(action)) {
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue

                val handled = try {
                    findScrollableAndPerform(child, action)
                } finally {
                    child.recycle()
                }

                if (handled) return true
            }

            return false
        }

        fun goBack(): Boolean {
            return performGlobalActionSafely(
                AccessibilityService.GLOBAL_ACTION_BACK
            )
        }

        fun goHome(): Boolean {
            return performGlobalActionSafely(
                AccessibilityService.GLOBAL_ACTION_HOME
            )
        }

        fun openRecentApps(): Boolean {
            return performGlobalActionSafely(
                AccessibilityService.GLOBAL_ACTION_RECENTS
            )
        }

        fun openNotifications(): Boolean {
            return performGlobalActionSafely(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            )
        }

        fun openQuickSettings(): Boolean {
            return performGlobalActionSafely(
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            )
        }

        private fun performGlobalActionSafely(action: Int): Boolean {
            val service = activeService ?: return false

            return try {
                service.performGlobalAction(action)
            } catch (_: Exception) {
                false
            }
        }

        fun typeText(text: String): Boolean {
            val service = activeService ?: return false

            return try {
                val root = service.rootInActiveWindow
                    ?: return false

                try {
                    val node = root.findFocus(
                        AccessibilityNodeInfo.FOCUS_INPUT
                    ) ?: return false

                    try {
                        if (!node.isEditable || !node.isEnabled) {
                            return false
                        }

                        val arguments = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo
                                    .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                text
                            )
                        }

                        node.performAction(
                            AccessibilityNodeInfo.ACTION_SET_TEXT,
                            arguments
                        )
                    } finally {
                        node.recycle()
                    }
                } finally {
                    root.recycle()
                }
            } catch (_: Exception) {
                false
            }
        }

        fun clearText(): Boolean = typeText("")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        activeService = this

        try {
            val info = serviceInfo

            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

            serviceInfo = info
        } catch (_: Exception) {
            // Service remains usable if optional flags fail.
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val root = rootInActiveWindow ?: run {
                latestVisibleText = ""
                return
            }

            try {
                val collected = LinkedHashSet<String>()

                fun walk(node: AccessibilityNodeInfo?) {
                    if (node == null) return
                    if (collected.sumOf { it.length } >= 5000) return

                    node.text?.toString()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { collected.add(it) }

                    node.contentDescription?.toString()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { collected.add(it) }

                    for (i in 0 until node.childCount) {
                        if (collected.sumOf { it.length } >= 5000) {
                            break
                        }

                        val child = node.getChild(i) ?: continue

                        try {
                            walk(child)
                        } finally {
                            child.recycle()
                        }
                    }
                }

                walk(root)

                latestVisibleText =
                    collected.joinToString("\n").take(5000)

            } finally {
                root.recycle()
            }

        } catch (_: Exception) {
            latestVisibleText = ""
        }
    }

    override fun onInterrupt() {
        latestVisibleText = ""
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
            latestVisibleText = ""
        }

        super.onDestroy()
    }
}
