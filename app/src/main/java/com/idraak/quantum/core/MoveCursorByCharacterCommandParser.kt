package com.idraak.quantum.core

import java.util.Locale

enum class CursorStep { BACKWARD, FORWARD }

/** Recognizes explicit one-character cursor movement commands. */
object MoveCursorByCharacterCommandParser {
    fun parse(raw: String): CursorStep? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return when (text) {
            "move cursor left", "move cursor one character left", "cursor left",
            "کرسر ایک حرف پیچھے کرو", "کرسر بائیں لے جاؤ", "ایک حرف پیچھے جاؤ",
            "कर्सर एक अक्षर पीछे करो", "कर्सर बाईं ओर ले जाओ" -> CursorStep.BACKWARD

            "move cursor right", "move cursor one character right", "cursor right",
            "کرسر ایک حرف آگے کرو", "کرسر دائیں لے جاؤ", "ایک حرف آگے جاؤ",
            "कर्सर एक अक्षर आगे करो", "कर्सर दाईं ओर ले जाओ" -> CursorStep.FORWARD

            else -> null
        }
    }
}
