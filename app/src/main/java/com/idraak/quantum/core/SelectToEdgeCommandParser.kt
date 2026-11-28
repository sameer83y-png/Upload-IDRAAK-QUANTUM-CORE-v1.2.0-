package com.idraak.quantum.core

import java.util.Locale

enum class SelectionEdge { START, END }

/** Recognizes explicit requests to select text between the cursor and a field edge. */
object SelectToEdgeCommandParser {
    fun parse(raw: String): SelectionEdge? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return when (text) {
            "select from cursor to start", "select to start", "highlight to beginning",
            "کرسر سے شروع تک منتخب کرو", "شروع تک متن منتخب کرو",
            "कर्सर से शुरुआत तक चुनो", "शुरुआत तक टेक्स्ट चुनो" -> SelectionEdge.START

            "select from cursor to end", "select to end", "highlight to end",
            "کرسر سے آخر تک منتخب کرو", "آخر تک متن منتخب کرو",
            "कर्सर से अंत तक चुनो", "अंत तक टेक्स्ट चुनो" -> SelectionEdge.END

            else -> null
        }
    }
}
