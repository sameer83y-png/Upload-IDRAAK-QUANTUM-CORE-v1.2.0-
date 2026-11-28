package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests to paste clipboard text into the focused field. */
object PasteClipboardCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "paste clipboard", "paste from clipboard", "paste clipboard text",
            "paste copied text", "clipboard paste",
            "کلپ بورڈ پیسٹ کرو", "کلپ بورڈ سے پیسٹ کرو",
            "کاپی کیا ہوا متن پیسٹ کرو", "کاپی شدہ متن پیسٹ کریں",
            "کلپ بورڈ کا متن پیسٹ کرو",
            "क्लिपबोर्ड से पेस्ट करो", "कॉपी किया हुआ टेक्स्ट पेस्ट करो",
            "क्लिपबोर्ड का टेक्स्ट पेस्ट करें"
        )
    }
}
