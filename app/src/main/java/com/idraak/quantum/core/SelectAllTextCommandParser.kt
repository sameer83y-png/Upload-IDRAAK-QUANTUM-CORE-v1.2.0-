package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests to select all text in the focused text field. */
object SelectAllTextCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "select all", "select all text", "select all text in field",
            "highlight all text", "select everything in field",
            "تمام متن منتخب کرو", "سارا متن منتخب کرو",
            "پورا متن سلیکٹ کرو", "تمام تحریر منتخب کریں",
            "सारा टेक्स्ट चुनो", "पूरा टेक्स्ट सेलेक्ट करो",
            "सभी टेक्स्ट चुनें"
        )
    }
}
