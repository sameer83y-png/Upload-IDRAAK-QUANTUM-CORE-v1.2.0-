package com.idraak.quantum.core

import java.util.Locale

enum class DeleteDirection { BACKWARD, FORWARD }

/** Recognizes explicit one-character deletion commands. */
object DeleteCharacterCommandParser {
    fun parse(raw: String): DeleteDirection? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return when (text) {
            "delete character", "delete one character", "backspace", "delete previous character",
            "ایک حرف پیچھے سے مٹاؤ", "پچھلا حرف حذف کرو", "ایک حرف حذف کرو",
            "एक अक्षर पीछे से मिटाओ", "पिछला अक्षर हटाओ" -> DeleteDirection.BACKWARD
            "delete next character", "forward delete", "delete character ahead",
            "اگلا حرف مٹاؤ", "اگلا حرف حذف کرو", "آگے والا حرف مٹاؤ",
            "अगला अक्षर मिटाओ", "आगे का अक्षर हटाओ" -> DeleteDirection.FORWARD
            else -> null
        }
    }
}
