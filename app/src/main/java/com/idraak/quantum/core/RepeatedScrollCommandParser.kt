package com.idraak.quantum.core

import java.util.Locale

data class RepeatedScrollCommand(val forward: Boolean, val count: Int)

/** Parses bounded, offline scroll-count commands in Urdu, Hindi and English. */
object RepeatedScrollCommandParser {
    private val counts = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "ایک" to 1, "دو" to 2, "تین" to 3, "چار" to 4, "پانچ" to 5,
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5
    )

    fun parse(raw: String): RepeatedScrollCommand? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .map { it.digitToIntOrNull()?.toString() ?: it.toString() }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
        val english = Regex("^(?:scroll|swipe) (down|up)(?: (\\d+|one|two|three|four|five)(?: times?)?)?$")
            .matchEntire(text)
        if (english != null) {
            val countToken = english.groupValues[2]
            val count = if (countToken.isBlank()) 1 else countToken.toIntOrNull() ?: counts[countToken] ?: return null
            if (count !in 1..5) return null
            val forward = if (english.groupValues[0].startsWith("swipe")) english.groupValues[1] == "up" else english.groupValues[1] == "down"
            return RepeatedScrollCommand(forward, count)
        }

        val urdu = Regex("^(?:(\\S+) بار )?(نیچے|اوپر) (اسکرول|سوائپ) (?:کرو|کریں)$")
            .matchEntire(text)
        if (urdu != null) {
            val countToken = urdu.groupValues[1]
            val count = if (countToken.isBlank()) 1 else countToken.toIntOrNull() ?: counts[countToken] ?: return null
            if (count !in 1..5) return null
            val forward = if (urdu.groupValues[3] == "سوائپ") urdu.groupValues[2] == "اوپر" else urdu.groupValues[2] == "نیچے"
            return RepeatedScrollCommand(forward, count)
        }
        return null
    }
}
