package com.idraak.quantum.core

import java.util.Locale

enum class CursorPosition { START, END }

/** Recognizes explicit requests to move the cursor within the focused text field. */
object MoveCursorCommandParser {
    fun parse(raw: String): CursorPosition? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return when (text) {
            "move cursor to start", "cursor to start", "go to beginning of text",
            "move cursor to beginning", "text start",
            "کرسر شروع میں لے جاؤ", "کرسر شروع پر رکھو", "متن کے شروع میں جاؤ",
            "کرسر آغاز میں لے جاؤ",
            "कर्सर शुरुआत में ले जाओ", "टेक्स्ट की शुरुआत में जाओ" -> CursorPosition.START

            "move cursor to end", "cursor to end", "go to end of text",
            "move cursor to end of text", "text end",
            "کرسر آخر میں لے جاؤ", "کرسر آخر پر رکھو", "متن کے آخر میں جاؤ",
            "کرسر اختتام پر لے جاؤ",
            "कर्सर अंत में ले जाओ", "टेक्स्ट के अंत में जाओ" -> CursorPosition.END

            else -> null
        }
    }
}
