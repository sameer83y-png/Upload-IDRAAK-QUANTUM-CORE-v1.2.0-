
package com.idraak.quantum

/**
 * IDRAAK Screen Controller
 *
 * Responsibilities:
 * - Check Accessibility Service availability.
 * - Provide a dedicated interface for screen operations.
 * - Forward screen actions to IDRAAKAccessibilityService.
 *
 * Actual accessibility operations are handled only by
 * IDRAAKAccessibilityService.
 */
class ScreenController {

    /**
     * Check whether IDRAAK Accessibility Service is connected.
     */
    fun isAccessibilityEnabled(): Boolean {
        return IDRAAKAccessibilityService.isConnected()
    }

    /**
     * Read currently visible screen text.
     *
     * Returns empty text when Accessibility Service
     * is unavailable.
     */
    fun readScreen(): String {
        if (!isAccessibilityEnabled()) return ""

        return IDRAAKAccessibilityService.latestVisibleText
    }

    /**
     * Find and click a visible element by its text.
     */
    fun clickText(text: String): Boolean {
        if (text.isBlank()) return false
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.clickText(text)
    }

    /**
     * Click a screen element using its View ID.
     */
    fun clickViewId(viewId: String): Boolean {
        if (viewId.isBlank()) return false
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.clickViewId(viewId)
    }

    /**
     * Perform a tap gesture at screen coordinates.
     */
    fun tap(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f) return false
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.tap(x, y)
    }

    /**
     * Perform a swipe gesture.
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 400L
    ): Boolean {
        if (startX < 0f || startY < 0f) return false
        if (endX < 0f || endY < 0f) return false
        if (duration <= 0L) return false
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.swipe(
            startX,
            startY,
            endX,
            endY,
            duration
        )
    }

    /**
     * Scroll the current screen down.
     */
    fun scrollDown(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.scrollDown()
    }

    /**
     * Scroll the current screen up.
     */
    fun scrollUp(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.scrollUp()
    }

    /**
     * Navigate back.
     */
    fun goBack(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.goBack()
    }

    /**
     * Navigate to Android home screen.
     */
    fun goHome(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.goHome()
    }

    /**
     * Open Android notification panel.
     */
    fun openNotifications(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.openNotifications()
    }

    /**
     * Open Android quick settings panel.
     */
    fun openQuickSettings(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.openQuickSettings()
    }

    /**
     * Open Android recent apps screen.
     */
    fun openRecentApps(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.openRecentApps()
    }

    /**
     * Enter text into the currently focused editable field.
     */
    fun typeText(text: String): Boolean {
        if (text.isEmpty()) return false
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.typeText(text)
    }

    /**
     * Clear text from the currently focused editable field.
     */
    fun clearText(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.clearText()
    }
}
