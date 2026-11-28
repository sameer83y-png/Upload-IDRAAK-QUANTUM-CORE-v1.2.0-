package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests to cut the currently selected text. */
object CutSelectedTextCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "cut selected text", "cut selection", "cut highlighted text",
            "cut selected words",
            "منتخب متن کاٹو", "منتخب کیا ہوا متن کاٹ دو",
            "چنا ہوا متن کاٹیں", "منتخب عبارت کاٹ دو",
            "चुना हुआ टेक्स्ट काटो", "चुने हुए शब्द काट दो"
        )
    }
}
