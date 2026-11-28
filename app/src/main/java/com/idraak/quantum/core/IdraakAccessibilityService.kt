package com.idraak.quantum.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * User-enabled foreground UI bridge.
 * Only controls UI elements exposed by Android Accessibility in the foreground app.
 */
class IdraakAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // rootInActiveWindow is queried on demand so commands use the current screen.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    private fun normalized(value: CharSequence?): String =
        value?.toString()?.trim()?.lowercase(Locale.ROOT)
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()

    private fun allNodes(root: AccessibilityNodeInfo, limit: Int = 600): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && result.size < limit) {
            val node = queue.removeFirst()
            result.add(node)
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return result
    }

    private fun nodeMatches(node: AccessibilityNodeInfo, query: String): Boolean {
        val wanted = normalized(query)
        if (wanted.isBlank()) return false
        return listOf(node.text, node.contentDescription, node.hintText).any { label ->
            val actual = normalized(label)
            actual == wanted || (actual.length >= 2 && actual.contains(wanted))
        }
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        var depth = 0
        while (candidate != null && depth < 10) {
            if (candidate.isEnabled && candidate.isClickable &&
                candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) return true
            candidate = candidate.parent
            depth++
        }
        return false
    }

    private fun dispatchTap(bounds: Rect, durationMs: Long = 90L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || bounds.isEmpty) return false
        val path = Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clickVisibleText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = allNodes(root).filter { it.isVisibleToUser && nodeMatches(it, query) }
        for (node in matches) {
            if (performClick(node)) return true
        }
        // Some apps expose a label but omit the clickable parent from the node tree.
        for (node in matches) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (node.isEnabled && bounds.width() > 0 && bounds.height() > 0 &&
                bounds.width() < resources.displayMetrics.widthPixels * 0.95f &&
                bounds.height() < resources.displayMetrics.heightPixels * 0.45f &&
                dispatchTap(bounds)
            ) return true
        }
        return false
    }

    private fun longPressVisibleText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = allNodes(root).filter { it.isVisibleToUser && nodeMatches(it, query) }
        for (node in matches) {
            var candidate: AccessibilityNodeInfo? = node
            var depth = 0
            while (candidate != null && depth < 10) {
                if (candidate.isEnabled &&
                    candidate.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                ) return true
                candidate = candidate.parent
                depth++
            }
        }
        for (node in matches) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (node.isEnabled && bounds.width() > 0 && bounds.height() > 0 &&
                bounds.width() < resources.displayMetrics.widthPixels * 0.95f &&
                bounds.height() < resources.displayMetrics.heightPixels * 0.45f &&
                dispatchTap(bounds, 900L)
            ) return true
        }
        return false
    }

    private fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (root.performAction(action)) return true
        for (node in allNodes(root)) {
            if (node.isVisibleToUser && node.isEnabled && node.isScrollable &&
                node.performAction(action)
            ) return true
        }
        return false
    }

    private fun readVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        val output = LinkedHashSet<String>()
        for (node in allNodes(root)) {
            if (!node.isVisibleToUser) continue
            listOf(node.text, node.contentDescription, node.hintText).forEach { value ->
                val label = value?.toString()?.trim().orEmpty()
                if (label.isNotBlank()) output.add(label)
            }
            if (output.sumOf { it.length } >= 4000) break
        }
        return output.joinToString("\n").take(4000)
    }

    private fun global(action: Int): Boolean = performGlobalAction(action)

    private fun swipeScreen(up: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val startY = if (up) metrics.heightPixels * 0.78f else metrics.heightPixels * 0.24f
        val endY = if (up) metrics.heightPixels * 0.24f else metrics.heightPixels * 0.78f
        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile private var activeService: IdraakAccessibilityService? = null

        fun isConnected(): Boolean = activeService != null
        fun readScreenText(): String = activeService?.readVisibleText().orEmpty()
        fun clickText(text: String): Boolean = activeService?.clickVisibleText(text.trim()) == true
        fun longPressText(text: String): Boolean = activeService?.longPressVisibleText(text.trim()) == true
        fun scrollForward(): Boolean = activeService?.scroll(true) == true
        fun scrollBackward(): Boolean = activeService?.scroll(false) == true
        fun goBack(): Boolean = activeService?.global(GLOBAL_ACTION_BACK) == true
        fun goHome(): Boolean = activeService?.global(GLOBAL_ACTION_HOME) == true
        fun showNotifications(): Boolean = activeService?.global(GLOBAL_ACTION_NOTIFICATIONS) == true
        fun showQuickSettings(): Boolean = activeService?.global(GLOBAL_ACTION_QUICK_SETTINGS) == true
        fun swipeUp(): Boolean = activeService?.swipeScreen(true) == true
        fun swipeDown(): Boolean = activeService?.swipeScreen(false) == true
    }
}
