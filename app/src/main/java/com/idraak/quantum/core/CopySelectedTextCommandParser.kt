package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests to copy the currently selected text. */
object CopySelectedTextCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "copy selected text", "copy selection", "copy highlighted text",
            "copy selected words",
            "منتخب متن کاپی کرو", "منتخب کیا ہوا متن کاپی کرو",
            "چنا ہوا متن کاپی کریں", "منتخب عبارت نقل کرو",
            "चुना हुआ टेक्स्ट कॉपी करो", "चुने हुए शब्द कॉपी करो"
        )
    }
}
