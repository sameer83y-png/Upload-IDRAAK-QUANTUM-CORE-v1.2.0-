package com.idraak.quantum.core

import java.util.Locale

/** Parses explicit requests to replace the currently selected text. */
object ReplaceSelectedTextCommandParser {
    fun parse(raw: String): String? {
        val text = raw.trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[.!?؟،,۔]+$"), "")
        val lower = text.lowercase(Locale.ROOT)

        val englishPrefixes = listOf(
            "replace selected text with ",
            "replace selection with ",
            "replace highlighted text with "
        )
        englishPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            return text.drop(prefix.length).trim().takeIf { it.isNotEmpty() }
        }

        val urdu = Regex("^(?:منتخب متن کو|منتخب عبارت کو) (.+) سے بدل دو$")
            .find(text)
        if (urdu != null) return urdu.groupValues[1].trim().takeIf { it.isNotEmpty() }

        val hindi = Regex("^(?:चुने हुए टेक्स्ट को|चयनित टेक्स्ट को) (.+) से बदलो$")
            .find(text)
        if (hindi != null) return hindi.groupValues[1].trim().takeIf { it.isNotEmpty() }

        return null
    }
}
