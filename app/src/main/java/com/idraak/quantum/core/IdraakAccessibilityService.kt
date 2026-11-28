package com.idraak.quantum.core

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

    private fun doubleTapVisibleText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // Only double-tap a unique visible label; never guess between duplicate controls.
        val node = uniqueMatchingNode(root, query) ?: return false
        if (!node.isEnabled) return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty ||
            bounds.width() >= resources.displayMetrics.widthPixels * 0.95f ||
            bounds.height() >= resources.displayMetrics.heightPixels * 0.45f ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N
        ) return false

        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        fun tapPath() = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX + 1f, centerY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath(), 0, 70))
            .addStroke(GestureDescription.StrokeDescription(tapPath(), 160, 70))
            .build()
        return dispatchGesture(gesture, null, null)
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

    private fun scrollRepeatedly(forward: Boolean, count: Int): Int {
        var completed = 0
        repeat(count.coerceIn(1, 5)) {
            val moved = scroll(forward) || swipeScreen(forward)
            if (!moved) return completed
            completed++
        }
        return completed
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

        val submitTerms = searchTerms + listOf("go", "submit", "done", "ابحث", "چلو")
        val searchButtons = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isClickable &&
                !node.isEditable && submitTerms.any { term ->
                    normalized(node.text) == term ||
                        normalized(node.contentDescription) == term ||
                        normalized(node.text).contains(term) ||
                        normalized(node.contentDescription).contains(term)
                }
        }
        if (searchButtons.size == 1 && performClick(searchButtons.single())) return true

        // Some apps submit immediately after ACTION_SET_TEXT; otherwise
        // leave the query in the field without guessing at an unrelated control.
        return true
    }

    private fun controlMedia(action: String): Boolean {
        val labels = when (action) {
            "play" -> listOf("play", "play video", "resume", "پلے", "چلائیں", "چلاؤ", "ویڈیو چلاؤ", "ویڈیو چلائیں")
            "pause" -> listOf("pause", "pause video", "پاز", "روکیں", "روکو", "ویڈیو روکیں", "ویڈیو روک دو")
            "next" -> listOf("next", "next video", "skip", "اگلا", "اگلی", "اگلی ویڈیو", "اگلا ویڈیو")
            "previous" -> listOf("previous", "previous video", "پچھلا", "پچھلی", "پچھلی ویڈیو", "پچھلا ویڈیو")
            else -> emptyList()
        }
        return labels.any { label -> clickVisibleText(label) }
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

    private fun showRecentApps(): Boolean = global(GLOBAL_ACTION_RECENTS)

    private fun takeSystemScreenshot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return global(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    private fun copyVisibleScreenText(): Boolean {
        val text = readVisibleText().trim()
        if (text.isBlank()) return false
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("Idraak screen text", text))
        return true
    }

    /**
     * Pastes clipboard content only into the single currently focused, editable,
     * non-password field. It never reads or speaks clipboard contents.
     */
    /**
     * Clears only the single focused, visible, editable, non-password text field.
     * It never guesses which field to change and never targets password inputs.
     */
    /**
     * Selects all text only in one focused, visible, editable, non-password field.
     */
    private fun selectAllFocusedText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        val textLength = field.text?.length ?: return false
        if (textLength == 0) return false
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, textLength)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
    }

    /** Copies only an explicitly selected range in the focused non-password field. */
    private fun copySelectedFocusedText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        val selectionStart = field.textSelectionStart
        val selectionEnd = field.textSelectionEnd
        if (selectionStart < 0 || selectionEnd <= selectionStart) return false
        return field.performAction(AccessibilityNodeInfo.ACTION_COPY)
    }

    /** Cuts only an explicitly selected range in the focused non-password field. */
    private fun cutSelectedFocusedText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        val selectionStart = field.textSelectionStart
        val selectionEnd = field.textSelectionEnd
        if (selectionStart < 0 || selectionEnd <= selectionStart) return false
        return field.performAction(AccessibilityNodeInfo.ACTION_CUT)
    }


    /** Selects the word at the cursor in one focused, visible, editable, non-password field. */
    private fun selectWordAtCursor(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        val text = field.text?.toString() ?: return false
        if (text.isEmpty()) return false

        val selectionStart = field.textSelectionStart
        val selectionEnd = field.textSelectionEnd
        if (selectionStart < 0 || selectionEnd != selectionStart || selectionStart > text.length) {
            return false
        }

        fun isWordChar(char: Char): Boolean {
            val type = Character.getType(char)
            return char.isLetterOrDigit() || char == '_' ||
                type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
        }

        val probe = when {
            selectionStart < text.length && isWordChar(text[selectionStart]) -> selectionStart
            selectionStart > 0 && isWordChar(text[selectionStart - 1]) -> selectionStart - 1
            else -> return false
        }
        var start = probe
        var end = probe + 1
        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < text.length && isWordChar(text[end])) end++
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
    }

    /** Moves one Unicode code point from a collapsed cursor in a safe editable field. */
    private fun moveFocusedCursorByCharacter(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        val text = field.text?.toString() ?: return false
        val start = field.textSelectionStart
        val end = field.textSelectionEnd
        if (start < 0 || end != start || start > text.length) return false
        if (forward && start == text.length || !forward && start == 0) return false

        val next = Character.offsetByCodePoints(text, start, if (forward) 1 else -1)
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, next)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, next)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
    }

    /** Deletes one Unicode code point beside a collapsed cursor; never deletes a selection. */
    private fun deleteFocusedCharacter(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        val text = field.text?.toString() ?: return false
        val cursor = field.textSelectionStart
        if (cursor < 0 || field.textSelectionEnd != cursor || cursor > text.length) return false
        if (forward && cursor == text.length || !forward && cursor == 0) return false
        val deleteStart = if (forward) cursor else Character.offsetByCodePoints(text, cursor, -1)
        val deleteEnd = if (forward) Character.offsetByCodePoints(text, cursor, 1) else cursor
        val updatedText = text.removeRange(deleteStart, deleteEnd)
        val setTextArguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updatedText)
        }
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArguments)) return false
        val selectionArguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, deleteStart)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, deleteStart)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArguments)
        return true
    }

    private fun moveFocusedCursor(toEnd: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        val textLength = field.text?.length ?: return false
        val position = if (toEnd) textLength else 0
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, position)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, position)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
    }

    /** Selects from the collapsed cursor to one edge; existing selections are left untouched. */
    private fun selectFromCursorToEdge(toEnd: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        val textLength = field.text?.length ?: return false
        val start = field.textSelectionStart
        val end = field.textSelectionEnd
        if (textLength == 0 || start < 0 || end != start || start > textLength) return false
        val edge = if (toEnd) textLength else 0
        if (edge == start) return false
        val arguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, minOf(start, edge))
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, maxOf(start, edge))
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)
    }

    private fun clearFocusedTextField(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val field = focusedFields.single()
        if (field.text.isNullOrEmpty()) return false
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                ""
            )
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun pasteClipboardText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedFields = allNodes(root).filter { node ->
            node.isVisibleToUser && node.isEnabled && node.isEditable &&
                node.isFocused && !node.isPassword
        }
        if (focusedFields.size != 1) return false
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        if (!clipboard.hasPrimaryClip()) return false
        val description = clipboard.primaryClipDescription ?: return false
        if (!description.hasMimeType("text/*")) return false
        return focusedFields.single().performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

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

    private fun swipeHorizontal(left: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val metrics = resources.displayMetrics
        val centerY = metrics.heightPixels * 0.5f
        val startX = if (left) metrics.widthPixels * 0.82f else metrics.widthPixels * 0.18f
        val endX = if (left) metrics.widthPixels * 0.18f else metrics.widthPixels * 0.82f
        val path = Path().apply {
            moveTo(startX, centerY)
            lineTo(endX, centerY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        return dispatchGesture(gesture, null, null)
    }


    /**
     * Performs an explicit two-finger pinch around the screen center.
     */
    private fun pinchZoom(zoomIn: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        val innerX = metrics.widthPixels * 0.035f
        val outerX = metrics.widthPixels * 0.16f
        val innerY = metrics.heightPixels * 0.0175f
        val outerY = metrics.heightPixels * 0.08f
        val startX = if (zoomIn) innerX else outerX
        val endX = if (zoomIn) outerX else innerX
        val startY = if (zoomIn) innerY else outerY
        val endY = if (zoomIn) outerY else innerY
        val firstPath = Path().apply {
            moveTo(centerX - startX, centerY - startY)
            lineTo(centerX - endX, centerY - endY)
        }
        val secondPath = Path().apply {
            moveTo(centerX + startX, centerY + startY)
            lineTo(centerX + endX, centerY + endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(firstPath, 0, 450))
            .addStroke(GestureDescription.StrokeDescription(secondPath, 0, 450))
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
        fun controlMedia(action: String): Boolean =
            activeService?.controlMedia(action) == true
        fun seekBySeconds(offsetSeconds: Long): Boolean =
            activeService?.seekBySeconds(offsetSeconds) == true
        fun longPressText(text: String): Boolean = activeService?.longPressVisibleText(text.trim()) == true
        fun doubleTapText(text: String): Boolean = activeService?.doubleTapVisibleText(text.trim()) == true
        fun scrollForward(): Boolean = activeService?.scroll(true) == true
        fun scrollBackward(): Boolean = activeService?.scroll(false) == true
        fun scrollByCount(forward: Boolean, count: Int): Int =
            activeService?.scrollRepeatedly(forward, count.coerceIn(1, 5)) ?: 0
        fun goBack(): Boolean = activeService?.global(GLOBAL_ACTION_BACK) == true
        fun showRecentApps(): Boolean = activeService?.showRecentApps() == true
        fun takeScreenshot(): Boolean = activeService?.takeSystemScreenshot() == true
        fun copyScreenText(): Boolean = activeService?.copyVisibleScreenText() == true
        fun pasteClipboardText(): Boolean = activeService?.pasteClipboardText() == true
        fun clearFocusedTextField(): Boolean = activeService?.clearFocusedTextField() == true
        fun selectAllFocusedText(): Boolean = activeService?.selectAllFocusedText() == true
        fun copySelectedText(): Boolean = activeService?.copySelectedFocusedText() == true
        fun cutSelectedText(): Boolean = activeService?.cutSelectedFocusedText() == true
        fun selectWordAtCursor(): Boolean = activeService?.selectWordAtCursor() == true
        fun moveCursorByCharacter(forward: Boolean): Boolean =
            activeService?.moveFocusedCursorByCharacter(forward) == true
        fun deleteCharacter(forward: Boolean): Boolean =
            activeService?.deleteFocusedCharacter(forward) == true
        fun moveCursorToStart(): Boolean = activeService?.moveFocusedCursor(false) == true
        fun moveCursorToEnd(): Boolean = activeService?.moveFocusedCursor(true) == true
        fun selectFromCursorToStart(): Boolean = activeService?.selectFromCursorToEdge(false) == true
        fun selectFromCursorToEnd(): Boolean = activeService?.selectFromCursorToEdge(true) == true
        fun goHome(): Boolean = activeService?.global(GLOBAL_ACTION_HOME) == true
        fun showNotifications(): Boolean = activeService?.global(GLOBAL_ACTION_NOTIFICATIONS) == true
        fun showQuickSettings(): Boolean = activeService?.global(GLOBAL_ACTION_QUICK_SETTINGS) == true
        fun swipeUp(): Boolean = activeService?.swipeScreen(true) == true
        fun swipeDown(): Boolean = activeService?.swipeScreen(false) == true
        fun swipeLeft(): Boolean = activeService?.swipeHorizontal(true) == true
        fun swipeRight(): Boolean = activeService?.swipeHorizontal(false) == true
        fun pinchZoom(zoomIn: Boolean): Boolean = activeService?.pinchZoom(zoomIn) == true
    }
}
