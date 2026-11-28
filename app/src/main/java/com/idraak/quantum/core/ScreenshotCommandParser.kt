package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests for Android's system screenshot action. */
object ScreenshotCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "screenshot", "take screenshot", "capture screen", "capture screenshot",
            "take a screenshot", "save screenshot",
            "اسکرین شاٹ", "اسکرین شاٹ لو", "اسکرین شاٹ لیں",
            "اسکرین شاٹ لے لو", "اسکرین کی تصویر لو", "اسکرین کی تصویر محفوظ کرو",
            "स्क्रीनशॉट", "स्क्रीनशॉट लो", "स्क्रीनशॉट लें",
            "स्क्रीन की तस्वीर लो", "स्क्रीन की फोटो लो"
        )
    }
}
