package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests to open Android's system app switcher. */
object RecentAppsCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "recent apps", "open recent apps", "show recent apps", "app switcher",
            "open app switcher", "show running apps",
            "حالیہ ایپس", "حالیہ ایپس کھولو", "حالیہ ایپس دکھاؤ",
            "ایپ سوئچر کھولو", "کھلی ایپس دکھاؤ", "چلتی ایپس دکھاؤ",
            "हाल के ऐप खोलो", "हाल के ऐप दिखाओ", "ऐप स्विचर खोलो",
            "recent kholo", "recent apps kholo"
        )
    }
}
