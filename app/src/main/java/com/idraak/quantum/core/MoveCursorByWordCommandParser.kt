package com.idraak.quantum.core

import java.util.Locale

enum class WordCursorDirection { BACKWARD, FORWARD }

/** Recognizes explicit one-word cursor movement commands. */
object MoveCursorByWordCommandParser {
    fun parse(raw: String): WordCursorDirection? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return when (text) {
            "move cursor one word left", "move cursor left one word", "cursor back one word",
            "کرسر ایک لفظ پیچھے کرو", "ایک لفظ پیچھے کرسر لے جاؤ",
            "कर्सर एक शब्द पीछे करो", "एक शब्द पीछे कर्सर ले जाओ" ->
                WordCursorDirection.BACKWARD

            "move cursor one word right", "move cursor right one word", "cursor forward one word",
            "کرسر ایک لفظ آگے کرو", "ایک لفظ آگے کرسر لے جاؤ",
            "कर्सर एक शब्द आगे करो", "एक शब्द आगे कर्सर ले जाओ" ->
                WordCursorDirection.FORWARD

            else -> null
        }
    }
}
