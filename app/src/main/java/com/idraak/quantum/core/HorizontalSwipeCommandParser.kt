package com.idraak.quantum.core

import java.util.Locale

/** Returns true for a left swipe, false for a right swipe, or null if not a swipe command. */
object HorizontalSwipeCommandParser {
    fun parse(raw: String): Boolean? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return when (text) {
            "swipe left", "drag left", "بائیں سوائپ کرو", "بائیں سوائپ کریں",
            "بائیں طرف سوائپ کرو", "بائیں طرف سوائپ کریں",
            "बाएं स्वाइप करो", "बाईं ओर स्वाइप करो" -> true
            "swipe right", "drag right", "دائیں سوائپ کرو", "دائیں سوائپ کریں",
            "دائیں طرف سوائپ کرو", "دائیں طرف سوائپ کریں",
            "दाएं स्वाइप करो", "दाईं ओर स्वाइप करो" -> false
            else -> null
        }
    }
}
