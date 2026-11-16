package com.idraak.quantum

/**
 * IDRAAK Screen Controller
 *
 * स्क्रीन संचालन का interface।
 * वास्तविक Accessibility actions केवल IDRAAKAccessibilityService में होते हैं।
 */
class ScreenController {

    fun isAccessibilityEnabled(): Boolean =
        IDRAAKAccessibilityService.isConnected()

    fun readScreen(): String {
        if (!isAccessibilityEnabled()) return ""
        return IDRAAKAccessibilityService.latestVisibleText
    }

    fun clickText(text: String): Boolean {
        if (text.isBlank() || !isAccessibilityEnabled()) return false
        return IDRAAKAccessibilityService.clickText(text)
    }

    fun clickViewId(viewId: String): Boolean {
        if (viewId.isBlank() || !isAccessibilityEnabled()) return false
        return IDRAAKAccessibilityService.clickViewId(viewId)
    }

    fun tap(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f || !isAccessibilityEnabled()) return false
        return IDRAAKAccessibilityService.tap(x, y)
    }

    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 400L
    ): Boolean {
        if (
            startX < 0f || startY < 0f ||
            endX < 0f || endY < 0f ||
            duration <= 0L || !isAccessibilityEnabled()
        ) return false

        return IDRAAKAccessibilityService.swipe(
            startX, startY, endX, endY, duration
        )
    }

    fun scrollDown(): Boolean =
        isAccessibilityEnabled() && IDRAAKAccessibilityService.scrollDown()

    fun scrollUp(): Boolean =
        isAccessibilityEnabled() && IDRAAKAccessibilityService.scrollUp()

    fun goBack(): Boolean =
        isAccessibilityEnabled() && IDRAAKAccessibilityService.goBack()

    fun goHome(): Boolean =
        isAccessibilityEnabled() && IDRAAKAccessibilityService.goHome()

    fun openNotifications(): Boolean =
        isAccessibilityEnabled() && IDRAAKAccessibilityService.openNotifications()

    fun openQuickSettings(): Boolean =
        isAccessibilityEnabled() && IDRAAKAccessibilityService.openQuickSettings()

    fun openRecentApps(): Boolean =
        isAccessibilityEnabled() && IDRAAKAccessibilityService.openRecentApps()

    fun typeText(text: String): Boolean {
        if (text.isEmpty() || !isAccessibilityEnabled()) return false
        return IDRAAKAccessibilityService.typeText(text)
    }

    fun clearText(): Boolean =
        isAccessibilityEnabled() && IDRAAKAccessibilityService.clearText()
}
