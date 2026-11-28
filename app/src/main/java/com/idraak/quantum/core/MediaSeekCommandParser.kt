package com.idraak.quantum.core

import java.util.Locale

/**
 * Parses short natural-language media seek requests without relying on online AI.
 * Returns a signed offset in seconds, or null when the phrase is not a seek command.
 */
object MediaSeekCommandParser {
    private val numberWords = mapOf(
        "ایک" to 1L, "اک" to 1L, "one" to 1L,
        "دو" to 2L, "two" to 2L,
        "تین" to 3L, "three" to 3L,
        "چار" to 4L, "four" to 4L,
        "پانچ" to 5L, "five" to 5L,
        "چھ" to 6L, "six" to 6L,
        "سات" to 7L, "seven" to 7L,
        "آٹھ" to 8L, "اٹھ" to 8L, "eight" to 8L,
        "نو" to 9L, "nine" to 9L,
        "دس" to 10L, "ten" to 10L,
        "گیارہ" to 11L, "بارہ" to 12L, "تیرہ" to 13L,
        "چودہ" to 14L, "پندرہ" to 15L, "سولہ" to 16L,
        "سترہ" to 17L, "اٹھارہ" to 18L, "انیس" to 19L,
        "بیس" to 20L,
        "इक" to 1L, "एक" to 1L, "दो" to 2L, "तीन" to 3L,
        "चार" to 4L, "पांच" to 5L, "पाँच" to 5L, "छह" to 6L,
        "सात" to 7L, "आठ" to 8L, "नौ" to 9L, "दस" to 10L
    )

    fun parseOffsetSeconds(raw: String): Long? {
        var phrase = raw.trim().lowercase(Locale.ROOT)
            .map { char -> char.digitToIntOrNull()?.toString() ?: char.toString() }
            .joinToString("")
            .replace(Regex("[،,.!؟?۔]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        phrase = phrase
            .replace(Regex("^(?:ویڈیو|video)(?:\\s+کو)?\\s+"), "")
            .replace(Regex("\\s+(?:کرو|کریں|کر دو|کر دیں|کرنا|کردو|کردیں)$"), "")
            .trim()

        val tokens = phrase.split(" ").filter { it.isNotBlank() }
        if (tokens.size !in 3..4) return null

        val directionWords = setOf(
            "forward", "ahead", "backward", "back", "آگے", "پیچھے",
            "forward", "back"
        )
        val unitWords = setOf(
            "second", "seconds", "sec", "secs", "minute", "minutes", "min", "mins",
            "سیکنڈ", "سیکنڈز", "منٹ", "منٹس"
        )
        val direction = tokens.firstOrNull { it in directionWords } ?: return null
        val unit = tokens.firstOrNull { it in unitWords } ?: return null
        val amountToken = tokens.firstOrNull { it.toLongOrNull() != null || numberWords.containsKey(it) }
            ?: return null
        val amount = amountToken.toLongOrNull() ?: numberWords[amountToken] ?: return null
        val multiplier = if (unit.startsWith("min") || unit in setOf("منٹ", "منٹس")) 60L else 1L
        if (amount <= 0L || amount > 86_400L / multiplier) return null
        val seconds = amount * multiplier
        return if (direction in setOf("forward", "ahead", "آگے")) seconds else -seconds
    }
}
