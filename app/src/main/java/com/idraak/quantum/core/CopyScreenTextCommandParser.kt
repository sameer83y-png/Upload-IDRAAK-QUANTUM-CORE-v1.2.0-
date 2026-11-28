package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests to copy text visible on the current screen. */
object CopyScreenTextCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "copy screen text", "copy text on screen", "copy visible text",
            "copy everything on screen", "اسکرین کا متن کاپی کرو",
            "اسکرین پر لکھا ہوا کاپی کرو", "اسکرین کا ٹیکسٹ کاپی کریں",
            "اسکرین پر موجود تحریر کاپی کرو",
            "स्क्रीन का टेक्स्ट कॉपी करो", "स्क्रीन पर लिखा कॉपी करो",
            "स्क्रीन का टेक्स्ट कॉपी करें"
        )
    }
}
