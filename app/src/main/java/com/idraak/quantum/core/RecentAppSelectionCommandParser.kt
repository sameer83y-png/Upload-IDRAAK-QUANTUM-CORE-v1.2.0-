package com.idraak.quantum.core

import java.util.Locale

/** Parses requests to select a uniquely visible app card in Android Recents. */
object RecentAppSelectionCommandParser {
    fun parse(raw: String): String? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        val prefixes = listOf(
            "switch to ", "switch app to ", "switch back to ",
            "select recent app ", "open recent app ",
            "حالیہ ایپس سے ", "حالیہ ایپس میں سے ",
            "ایپ پر واپس جاؤ ", "ایپ میں واپس جاؤ ",
            "حال کے ایپس سے ", "हाल के ऐप में ", "ऐप पर वापस जाओ "
        )
        val prefix = prefixes.firstOrNull { text.startsWith(it) } ?: return null
        return text.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
    }
}
