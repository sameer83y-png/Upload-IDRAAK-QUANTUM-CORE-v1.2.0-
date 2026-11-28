package com.idraak.quantum.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
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

    private fun uniqueMatchingNode(
        root: AccessibilityNodeInfo,
        query: String
    ): AccessibilityNodeInfo? {
        val visibleNodes = allNodes(root).filter { it.isVisibleToUser }
        val labels = visibleNodes.map { node ->
            listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.hintText?.toString()
            )
        }
        val index = AccessibilityMatchPolicy.findUniqueMatchIndex(labels, query) ?: return null
        return visibleNodes.getOrNull(index)
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
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        // GestureDescription needs a path with non-zero length; moveTo() alone
        // creates a zero-length path and can make fallback taps/long-presses fail.
        val path = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX + 1f, centerY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clickVisibleText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // Never guess when several visible controls have the same or similar label.
        val node = uniqueMatchingNode(root, query) ?: return false
        if (performClick(node)) return true
        // Some apps expose a label but omit the clickable parent from the node tree.
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return node.isEnabled && bounds.width() > 0 && bounds.height() > 0 &&
            bounds.width() < resources.displayMetrics.widthPixels * 0.95f &&
            bounds.height() < resources.displayMetrics.heightPixels * 0.45f &&
            dispatchTap(bounds)
    }

    private fun longPressVisibleText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // Ambiguous labels are rejected rather than long-pressing an arbitrary control.
        val node = uniqueMatchingNode(root, query) ?: return false
        var candidate: AccessibilityNodeInfo? = node
        var depth = 0
        while (candidate != null && depth < 10) {
            if (candidate.isEnabled &&
                candidate.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            ) return true
            candidate = candidate.parent
            depth++
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return node.isEnabled && bounds.width() > 0 && bounds.height() > 0 &&
            bounds.width() < resources.displayMetrics.widthPixels * 0.95f &&
            bounds.height() < resources.displayMetrics.heightPixels * 0.45f &&
            dispatchTap(bounds, 900L)
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


    private fun setTextInField(fieldLabel: String, value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = uniqueMatchingNode(root, fieldLabel) ?: return false
        if (!node.isVisibleToUser || !node.isEnabled || !node.isEditable) return false
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun searchVisibleApp(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editableFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable
        }
        val searchTerms = listOf("search", "find", "تلاش", "کھوج", "buscar")
        val searchFields = editableFields.filter { node ->
            val label = normalized(node.hintText).ifBlank {
                normalized(node.contentDescription).ifBlank { normalized(node.text) }
            }
            searchTerms.any { label.contains(it) }
        }
        val target = when {
            searchFields.size == 1 -> searchFields.single()
            searchFields.size > 1 -> return false
            editableFields.count { it.isFocused } == 1 -> editableFields.single { it.isFocused }
            else -> return false
        }
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                query
            )
        }
        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return false

        val searchButtons = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isClickable &&
                !node.isEditable && searchTerms.any { term ->
                    normalized(node.text).contains(term) ||
                        normalized(node.contentDescription).contains(term)
                }
        }
        if (searchButtons.size == 1) performClick(searchButtons.single())
        return true
    }

    private fun seekBySeconds(offsetSeconds: Long): Boolean {
        val root = rootInActiveWindow ?: return false
        val seekBars = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled &&
                (node.rangeInfo != null ||
                    node.className?.toString()?.contains("SeekBar", ignoreCase = true) == true)
        }
        if (seekBars.size != 1) return false
        val node = seekBars.single()
        val range = node.rangeInfo ?: return false
        // Media timelines commonly expose milliseconds. Do not guess when the
        // range is a percentage or an unknown scale.
        if (range.type != AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT ||
            range.max < 1000f || range.max <= range.min
        ) return false
        val target = (range.current + offsetSeconds * 1000f)
            .coerceIn(range.min, range.max)
        val arguments = Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, target)
        }
        return node.performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            arguments
        )
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
        fun setText(fieldLabel: String, value: String): Boolean =
            activeService?.setTextInField(fieldLabel.trim(), value) == true
        fun searchCurrentApp(query: String): Boolean =
            activeService?.searchVisibleApp(query.trim()) == true
        fun seekBySeconds(offsetSeconds: Long): Boolean =
            activeService?.seekBySeconds(offsetSeconds) == true
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
