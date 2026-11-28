package com.idraak.quantum.core

import java.util.Locale

/** Parses explicit double-tap requests without guessing the target. */
object DoubleTapCommandParser {
    fun parse(raw: String): String? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        val prefixes = listOf(
            "double tap ", "double click ", "tap twice on ", "دو بار ٹیپ کرو ",
            "دو مرتبہ ٹیپ کرو ", "دو بار کلک کرو ", "دو مرتبہ کلک کرو ",
            "دو بار دباؤ ", "دو مرتبہ دباؤ ",
            "दो बार टैप करो ", "दो बार क्लिक करो "
        )
        val prefix = prefixes.firstOrNull { text.startsWith(it) } ?: return null
        return text.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
    }
}
