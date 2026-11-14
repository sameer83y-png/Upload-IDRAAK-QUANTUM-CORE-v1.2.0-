
package com.idraak.quantum

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class IDRAAKAccessibilityService : AccessibilityService() {

    companion object {

        @Volatile
        var latestVisibleText: String = ""

        @Volatile
        private var activeService: IDRAAKAccessibilityService? = null

        fun isConnected(): Boolean {
            return activeService != null
        }

        // स्क्रीन पर मौजूद टेक्स्ट वाले बटन को क्लिक करना
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

            return try {
                val root = service.rootInActiveWindow ?: return false

                fun findScrollable(
                    node: AccessibilityNodeInfo?
                ): Boolean {
                    if (node == null) return false

                    if (node.isScrollable && node.isEnabled) {
                        if (node.performAction(action)) {
                            return true
                        }
                    }

                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)

                        if (findScrollable(child)) {
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

        // फोन का Back बटन
        fun goBack(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // फोन की Home स्क्रीन
        fun goHome(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_HOME
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // Notification panel खोलना
        fun openNotifications(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // Quick Settings खोलना
        fun openQuickSettings(): Boolean {
            return try {
                activeService?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                ) ?: false
            } catch (_: Exception) {
                false
            }
        }

        // सक्रिय टेक्स्ट बॉक्स में टेक्स्ट लिखना
        fun typeText(text: String): Boolean {
            if (text.isEmpty()) return false

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

                if (collected.length >= 5000) return

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
            // स्क्रीन पढ़ने में त्रुटि होने पर सेवा चालू रहेगी
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
