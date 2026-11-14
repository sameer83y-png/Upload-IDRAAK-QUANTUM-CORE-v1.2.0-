
package com.idraak.quantum

class ScreenController {

    /**
     * Check whether IDRAAK Accessibility Service is enabled.
     */
    fun isAccessibilityEnabled(): Boolean {
        return IDRAAKAccessibilityService.isConnected()
    }

    /**
     * Read currently visible screen text.
     */
    fun readScreen(): String {
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
     * Open notification panel.
     */
    fun openNotifications(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.openNotifications()
    }

    /**
     * Open quick settings panel.
     */
    fun openQuickSettings(): Boolean {
        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.openQuickSettings()
    }

    /**
     * Enter text into the currently focused editable field.
     */
    fun typeText(text: String): Boolean {
        if (text.isEmpty()) return false

        if (!isAccessibilityEnabled()) return false

        return IDRAAKAccessibilityService.typeText(text)
    }
}
