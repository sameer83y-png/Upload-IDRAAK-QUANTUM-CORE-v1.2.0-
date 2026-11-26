package com.idraak.quantum.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Deterministic offline intent router. Unknown requests are never reported as completed.
 * Natural-language coverage is intentionally explicit and grows through tested rules.
 */
class OfflineCommandEngine {
    fun process(rawText: String): CommandResult {
        val original = rawText.trim()
        val text = original.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (text.isBlank()) return CommandResult("براہ کرم اپنا حکم بتائیں۔")

        if (containsAny(text, "hello", "hi", "hey", "salam", "assalam", "السلام علیکم", "سلام")) {
            return CommandResult("وعلیکم السلام۔ ادراک حاضر ہے۔")
        }

        if (containsAny(text, "open settings", "phone settings", "settings kholo", "سیٹنگ کھولو", "سیٹنگز کھولو", "فون کی سیٹنگ")) {
            return CommandResult("فون کی سیٹنگز کھول رہا ہوں۔", CommandAction.OpenSettings)
        }
        if (containsAny(text, "open camera", "camera kholo", "کیمرہ کھولو", "کیمرا کھولو")) {
            return CommandResult("کیمرہ کھول رہا ہوں۔", CommandAction.OpenCamera)
        }
        if (containsAny(text, "open alarm", "show alarms", "alarm kholo", "الارم کھولو", "الارم دکھاؤ", "میرے الارم دکھاؤ")) {
            return CommandResult("الارم کھول رہا ہوں۔", CommandAction.OpenAlarm)
        }

        val phoneMatch = Regex("(?:call|dial|phone|کال کرو|فون کرو|نمبر ملاؤ)\\s*([+0-9][+0-9() -]{5,})").find(original)
        if (phoneMatch != null) {
            val number = phoneMatch.groupValues[1].filter { it.isDigit() || it == '+' }
            if (number.count(Char::isDigit) in 6..15) {
                return CommandResult("ڈائلر کھول رہا ہوں۔ نمبر چیک کرکے خود کال کریں۔", CommandAction.DialNumber(number))
            }
        }

        val youtubeMarkers = listOf(
            "search youtube for", "youtube par", "youtube pe", "یوتیوب پر", "یوٹیوب پر",
            "یوٹیوب میں", "یوٹیوب پہ", "یوٹیوب پر تلاش کرو", "یوٹیوب پر سرچ کرو"
        )
        val youtubeMarker = youtubeMarkers.firstOrNull { text.contains(it) }
        if (youtubeMarker != null) {
            val query = extractAfterMarker(original, youtubeMarker)
                .removePrefixes("search", "تلاش کرو", "سرچ کرو", "دکھاؤ", "چلاؤ")
                .trim()
            if (query.isNotBlank()) return CommandResult(
                "یوٹیوب پر تلاش کھول رہا ہوں: $query",
                CommandAction.OpenUrl("https://www.youtube.com/results?search_query=" + encode(query))
            )
            return CommandResult("یوٹیوب پر کیا تلاش کروں؟")
        }

        val openAppRules = listOf(
            listOf("open youtube", "youtube kholo", "یوٹیوب کھولو") to "youtube",
            listOf("open chrome", "chrome kholo", "کروم کھولو", "براوزر کھولو", "براؤزر کھولو") to "chrome",
            listOf("open chatgpt", "chatgpt kholo", "چیٹ جی پی ٹی کھولو", "چیٹ جی پی ٹی") to "chatgpt",
            listOf("open gemini", "gemini kholo", "جیمنائی کھولو", "جیمنی کھولو") to "gemini"
        )
        for ((phrases, app) in openAppRules) {
            if (phrases.any { text == it || text.startsWith("$it ") }) {
                return CommandResult("ایپ کھولنے کی کوشش کر رہا ہوں۔", CommandAction.OpenApp(app))
            }
        }

        val webMarkers = listOf(
            "search web for", "search google for", "google search", "google پر",
            "ویب پر تلاش کرو", "گوگل پر تلاش کرو", "انٹرنیٹ پر تلاش کرو", "تلاش کرو"
        )
        val webMarker = webMarkers.firstOrNull { text.startsWith(it) }
        if (webMarker != null) {
            val query = extractAfterMarker(original, webMarker).trim()
            if (query.isNotBlank()) return CommandResult(
                "ویب پر تلاش کر رہا ہوں: $query",
                CommandAction.OpenUrl("https://www.google.com/search?q=" + encode(query))
            )
            return CommandResult("کیا تلاش کرنا ہے؟")
        }

        val openWebsite = Regex("^(?:open|visit|کھولو|ویب سائٹ کھولو)\\s+(https?://\\S+)$", RegexOption.IGNORE_CASE)
            .find(original)
        if (openWebsite != null) {
            return CommandResult("ویب سائٹ کھول رہا ہوں۔", CommandAction.OpenUrl(openWebsite.groupValues[1]))
        }

        return CommandResult("یہ حکم ابھی میرے آف لائن کمانڈز میں شامل نہیں۔ براہ کرم اسے دوسرے الفاظ میں بتائیں۔")
    }

    private fun containsAny(value: String, vararg candidates: String): Boolean =
        candidates.any(value::contains)

    private fun extractAfterMarker(original: String, marker: String): String {
        val index = original.lowercase(Locale.ROOT).indexOf(marker.lowercase(Locale.ROOT))
        return if (index >= 0) original.substring(index + marker.length) else ""
    }

    private fun String.removePrefixes(vararg prefixes: String): String {
        var value = this.trim()
        for (prefix in prefixes) {
            if (value.lowercase(Locale.ROOT).startsWith(prefix.lowercase(Locale.ROOT))) {
                value = value.drop(prefix.length).trim()
            }
        }
        return value
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
