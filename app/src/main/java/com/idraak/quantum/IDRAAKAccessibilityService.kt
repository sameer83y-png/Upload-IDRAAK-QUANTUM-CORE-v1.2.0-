
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

        // स्क्रीन पर टेक्स्ट खोजकर क्लिक करना
        fun clickText(text: String): Boolean {
            if (text.isBlank()) return false

            val service = activeService ?: return false

            return try {
                val root = service.rootInActiveWindow ?: return false
                val nodes = root.findAccessibilityNodeInfosByText(text)

                for (node in nodes) {
                    var target: AccessibilityNodeInfo? = node

                    while (target != null) {
                        if (target.isClickable && target.isEnabled) {
                            if (target.performAction(
                                    AccessibilityNodeInfo.ACTION_CLICK
                                )
                            ) {
                                return true
                            }
                        }

                        target = target.parent
                    }
                }

                false
            } catch (_: Exception) {
                false
            }
        }

        // View ID से क्लिक करना
        fun clickViewId(viewId: String): Boolean {
            if (viewId.isBlank()) return false

            val service = activeService ?: return false

            return try {
                val root = service.rootInActiveWindow ?: return false
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)

                for (node in nodes) {
                    var target: AccessibilityNodeInfo? = node

                    while (target != null) {
                        if (target.isClickable && target.isEnabled) {
                            if (target.performAction(
                                    AccessibilityNodeInfo.ACTION_CLICK
                                )
                            ) {
                                return true
                            }
                        }

                        target = target.parent
                    }
                }

                false
            } catch (_: Exception) {
                false
            }
        }

        // स्क्रीन पर निर्धारित स्थान पर Tap
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

        // स्क्रीन पर Swipe
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
                endX < 0f || endY < 0f
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

        // Scroll
        fun scrollDown(): Boolean {
            return scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        fun scrollUp(): Boolean {
            return scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }

        private fun scroll(action: Int): Boolean {
            val service = activeService ?: return false

            return try {
                val root = service.rootInActiveWindow ?: return false

                fun findScrollable(node: AccessibilityNodeInfo?): Boolean {
                    if (node == null) return false

                    if (node.isScrollable && node.isEnabled) {
                        if (node.performAction(action)) {
                            return true
                        }
                    }

                    for (i in 0 until node.childCount) {
                        if (findScrollable(node.getChild(i))) {
                            return true
                        }
                    }

                    return false
                }

                findScrollable(root)
            } catch (_: Exception) {
                false
            }
        }

        // Back
        fun goBack(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // Home
        fun goHome(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_HOME
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // Recent Apps
        fun openRecentApps(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_RECENTS
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // Notification Panel
        fun openNotifications(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // Quick Settings
        fun openQuickSettings(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // सक्रिय Text Field में टेक्स्ट लिखना
        fun typeText(text: String): Boolean {
            val service = activeService ?: return false

            return try {
                val root = service.rootInActiveWindow ?: return false

                val node = root.findFocus(
                    AccessibilityNodeInfo.FOCUS_INPUT
                ) ?: return false

                if (!node.isEditable || !node.isEnabled) {
                    return false
                }

                val arguments = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                }

                node.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    arguments
                )
            } catch (_: Exception) {
                false
            }
        }

        // सक्रिय Text Field साफ करना
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
            // अतिरिक्त flags लागू न होने पर भी सेवा चालू रहेगी
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val root = rootInActiveWindow ?: run {
                latestVisibleText = ""
                return
            }

            val collected = StringBuilder()

            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null || collected.length >= 5000) return

                node.text?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        collected.append(it).append('\n')
                    }

                node.contentDescription?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        collected.append(it).append('\n')
                    }

                for (i in 0 until node.childCount) {
                    if (collected.length >= 5000) break
                    walk(node.getChild(i))
                }
            }

            walk(root)

            latestVisibleText = collected.toString().take(5000)

        } catch (_: Exception) {
            // स्क्रीन पढ़ने में त्रुटि होने पर सेवा सुरक्षित रहेगी
        }
    }

    override fun onInterrupt() {
        latestVisibleText = ""
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }

        latestVisibleText = ""

        super.onDestroy()
    }
}
