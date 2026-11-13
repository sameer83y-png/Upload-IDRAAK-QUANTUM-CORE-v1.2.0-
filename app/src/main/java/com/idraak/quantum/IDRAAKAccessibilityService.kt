package com.idraak.quantum

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class IDRAAKAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var latestVisibleText: String = ""
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val collected = StringBuilder()
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                collected.append(it).append("\n")
            }
            for (i in 0 until node.childCount) {
                walk(node.getChild(i))
            }
        }

        walk(root)
        latestVisibleText = collected.toString().take(5000)
    }

    override fun onInterrupt() {
        latestVisibleText = ""
    }
}
