
package com.idraak.quantum

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

class IDRAAKAccessibilityService : AccessibilityService() {

    companion object {

        @Volatile
        var latestVisibleText: String = ""

        @Volatile
        private var activeService: IDRAAKAccessibilityService? = null

        fun isConnected(): Boolean {
            return activeService != null
        }

        // स्क्रीन पर दिए गए टेक्स्ट वाला बटन क्लिक करना
        fun clickText(text: String): Boolean {
            if (text.isBlank()) return false

            val service = activeService ?: return false
            val root = service.rootInActiveWindow ?: return false

            return try {
                val nodes = root.findAccessibilityNodeInfosByText(text)

                for (node in nodes) {
                    var target: AccessibilityNodeInfo? = node

                    while (target != null) {
                        if (target.isClickable && target.isEnabled) {
                            return target.performAction(
                                AccessibilityNodeInfo.ACTION_CLICK
                            )
                        }
                        target = target.parent
                    }
                }

                false
            } catch (_: Exception) {
                false
            }
        }

        // स्क्रीन नीचे स्क्रॉल करना
        fun scrollDown(): Boolean {
            return scroll(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            )
        }

        // स्क्रीन ऊपर स्क्रॉल करना
        fun scrollUp(): Boolean {
            return scroll(
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            )
        }

        private fun scroll(action: Int): Boolean {
            val service = activeService ?: return false
            val root = service.rootInActiveWindow ?: return false

            return try {
                val nodes = ArrayList<AccessibilityNodeInfo>()

                fun findScrollable(node: AccessibilityNodeInfo?) {
                    if (node == null) return

                    if (node.isScrollable) {
                        nodes.add(node)
                    }

                    for (i in 0 until node.childCount) {
                        findScrollable(node.getChild(i))
                    }
                }

                findScrollable(root)

                for (node in nodes) {
                    if (node.performAction(action)) {
                        return true
                    }
                }

                false
            } catch (_: Exception) {
                false
            }
        }

        // फोन का Back बटन
        fun goBack(): Boolean {
            return activeService?.performGlobalAction(
                GLOBAL_ACTION_BACK
            ) ?: false
        }

        // फोन की Home स्क्रीन
        fun goHome(): Boolean {
            return activeService?.performGlobalAction(
                GLOBAL_ACTION_HOME
            ) ?: false
        }

        // Notification panel खोलना
        fun openNotifications(): Boolean {
            return activeService?.performGlobalAction(
                GLOBAL_ACTION_NOTIFICATIONS
            ) ?: false
        }

        // Quick Settings खोलना
        fun openQuickSettings(): Boolean {
            return activeService?.performGlobalAction(
                GLOBAL_ACTION_QUICK_SETTINGS
            ) ?: false
        }

        // सक्रिय टेक्स्ट बॉक्स में टेक्स्ट लिखना
        fun typeText(text: String): Boolean {
            if (text.isEmpty()) return false

            val service = activeService ?: return false
            val root = service.rootInActiveWindow ?: return false

            return try {
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
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val root = rootInActiveWindow ?: return
            val collected = StringBuilder()

            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null) return

                node.text?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        collected.append(it).append("\n")
                    }

                node.contentDescription?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        collected.append(it).append("\n")
                    }

                for (i in 0 until node.childCount) {
                    walk(node.getChild(i))
                }
            }

            walk(root)

            latestVisibleText = collected.toString().take(5000)

        } catch (_: Exception) {
            // स्क्रीन पढ़ने में त्रुटि होने पर सेवा बंद नहीं होगी
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
