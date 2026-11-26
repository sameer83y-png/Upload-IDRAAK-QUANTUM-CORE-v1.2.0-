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

        val englishGreeting = Regex("(^|\\\\s)(hello|hi|hey|salam|assalam)(\\\\s|$)", RegexOption.IGNORE_CASE)
        if (englishGreeting.containsMatchIn(text) || containsAny(text, "السلام علیکم", "سلام")) {
            return CommandResult("وعلیکم السلام۔ ادراک حاضر ہے۔")
        }
        if (containsAny(text, "open settings", "phone settings", "settings kholo", "سیٹنگ کھولو", "سیٹنگز کھولو", "فون کی سیٹنگ")) {
            return CommandResult("فون کی سیٹنگز کھول رہا ہوں۔", CommandAction.OpenSettings)
        }
        if (containsAny(text, "open camera", "camera kholo", "کیمرہ کھولو", "کیمرا کھولو")) {
            return CommandResult("کیمرہ کھول رہا ہوں۔", CommandAction.OpenCamera)
        }

        if (containsAny(text, "volume up", "increase volume", "آواز بڑھاؤ", "آواز بڑھاؤ", "آواز تیز کرو", "والیوم بڑھاؤ")) {
            return CommandResult("میڈیا کی آواز بڑھا رہا ہوں۔", CommandAction.AdjustVolume(VolumeDirection.UP))
        }
        if (containsAny(text, "volume down", "decrease volume", "آواز کم کرو", "آواز گھٹاؤ", "والیوم کم کرو")) {
            return CommandResult("میڈیا کی آواز کم کر رہا ہوں۔", CommandAction.AdjustVolume(VolumeDirection.DOWN))
        }
        if (containsAny(text, "mute", "mute volume", "آواز بند کرو", "آواز خاموش کرو", "والیوم بند کرو")) {
            return CommandResult("میڈیا کی آواز خاموش کر رہا ہوں۔", CommandAction.AdjustVolume(VolumeDirection.MUTE))
        }

        val englishReminder = Regex(
            "(?:remind me|remind)\\\\s+(?:to\\\\s+)?(.+?)\\\\s+in\\\\s+(\\\\d+)\\\\s+(minutes?|mins?|hours?|hrs?)",
            RegexOption.IGNORE_CASE
        ).find(original)
        if (englishReminder != null) {
            val amount = englishReminder.groupValues[2].toLongOrNull() ?: 0L
            val unit = englishReminder.groupValues[3].lowercase(Locale.ROOT)
            val duration = amount * if (unit.startsWith("h")) 3_600_000L else 60_000L
            if (amount in 1..10080) {
                val message = englishReminder.groupValues[1].trim()
                return CommandResult(
                    "یاد دہانی لگا دی گئی ہے: $message",
                    CommandAction.ScheduleReminder(System.currentTimeMillis() + duration, message)
                )
            }
        }

        val urduReminder = Regex(
            "مجھے\\\\s*(\\\\d+)\\\\s*(منٹ|منٹوں|گھنٹے|گھنٹہ)\\\\s*بعد\\\\s*(.+?)\\\\s*(?:یاد دلانا|یاد دلا دینا)",
            RegexOption.IGNORE_CASE
        ).find(original)
        if (urduReminder != null) {
            val amount = urduReminder.groupValues[1].toLongOrNull() ?: 0L
            val unit = urduReminder.groupValues[2]
            val duration = amount * if (unit.startsWith("گھن")) 3_600_000L else 60_000L
            if (amount in 1..10080) {
                val message = urduReminder.groupValues[3].trim()
                return CommandResult(
                    "یاد دہانی لگا دی گئی ہے: $message",
                    CommandAction.ScheduleReminder(System.currentTimeMillis() + duration, message)
                )
            }
        }

        if (containsAny(text, "open alarm", "show alarms", "alarm kholo", "الارم کھولو", "الارم دکھاؤ", "میرے الارم دکھاؤ")) {
            return CommandResult("الارم کھول رہا ہوں۔", CommandAction.OpenAlarm)
        }

        if (containsAny(text, "set alarm", "add alarm", "create alarm", "alarm lagao", "الارم لگاؤ", "الارم لگاو", "الارم سیٹ کرو", "الارم لگا دو", "الارم")) {
            val time = Regex("(?<!\\d)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(original)
            if (time == null) return CommandResult("کس وقت کا الارم لگاؤں؟ مثال: 7:30 AM یا 19:30۔")
            var hour = time.groupValues[1].toIntOrNull() ?: return CommandResult("وقت واضح نہیں۔ مثال: 7:30 AM یا 19:30۔")
            val minute = time.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            val meridiem = time.groupValues[3].lowercase(Locale.ROOT)
            if (minute !in 0..59 || hour !in 0..23) return CommandResult("یہ وقت درست نہیں۔ مثال: 7:30 AM یا 19:30۔")
            if (meridiem.isNotBlank()) {
                if (hour !in 1..12) return CommandResult("AM یا PM کے ساتھ گھنٹہ 1 سے 12 کے درمیان ہونا چاہیے۔")
                hour = when {
                    meridiem == "am" && hour == 12 -> 0
                    meridiem == "pm" && hour != 12 -> hour + 12
                    else -> hour
                }
            }
            return CommandResult(
                "الارم ${time.groupValues[0].trim()} کے لیے گھڑی کی ایپ میں سیٹ کر رہا ہوں۔",
                CommandAction.SetAlarm(hour, minute, "Idraak alarm")
            )
        }

        val phoneMatch = Regex("(?:call|dial|phone|کال کرو|فون کرو|نمبر ملاؤ)\\s*([+0-9][+0-9() -]{5,})").find(original)
        if (phoneMatch != null) {
            val number = phoneMatch.groupValues[1].filter { it.isDigit() || it == '+' }
            if (number.count(Char::isDigit) in 6..15) return CommandResult(
                "ڈائلر کھول رہا ہوں۔ نمبر چیک کرکے خود کال کریں۔", CommandAction.DialNumber(number)
            )
        }

        val youtubeMarkers = listOf("search youtube for", "youtube par", "youtube pe", "یوتیوب پر", "یوٹیوب پر", "یوٹیوب میں", "یوٹیوب پہ", "یوٹیوب پر تلاش کرو", "یوٹیوب پر سرچ کرو")
        val youtubeMarker = youtubeMarkers.firstOrNull { text.contains(it) }
        if (youtubeMarker != null) {
            val query = extractAfterMarker(original, youtubeMarker).removePrefixes("search", "تلاش کرو", "سرچ کرو", "دکھاؤ", "چلاؤ").trim()
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
            if (phrases.any { text == it || text.startsWith("$it ") }) return CommandResult(
                "ایپ کھولنے کی کوشش کر رہا ہوں۔", CommandAction.OpenApp(app)
            )
        }

        val webMarkers = listOf("search web for", "search google for", "google search", "google پر", "ویب پر تلاش کرو", "گوگل پر تلاش کرو", "انٹرنیٹ پر تلاش کرو", "تلاش کرو")
        val webMarker = webMarkers.firstOrNull { text.startsWith(it) }
        if (webMarker != null) {
            val query = extractAfterMarker(original, webMarker).trim()
            if (query.isNotBlank()) return CommandResult(
                "ویب پر تلاش کر رہا ہوں: $query",
                CommandAction.OpenUrl("https://www.google.com/search?q=" + encode(query))
            )
            return CommandResult("کیا تلاش کرنا ہے؟")
        }

        val openWebsite = Regex("^(?:open|visit|کھولو|ویب سائٹ کھولو)\\s+(https?://\\S+)$", RegexOption.IGNORE_CASE).find(original)
        if (openWebsite != null) return CommandResult(
            "ویب سائٹ کھول رہا ہوں۔", CommandAction.OpenUrl(openWebsite.groupValues[1])
        )

        return CommandResult("یہ حکم ابھی میرے آف لائن کمانڈز میں شامل نہیں۔ براہ کرم اسے دوسرے الفاظ میں بتائیں۔")
    }

    private fun containsAny(value: String, vararg candidates: String): Boolean = candidates.any(value::contains)

    private fun extractAfterMarker(original: String, marker: String): String {
        val index = original.lowercase(Locale.ROOT).indexOf(marker.lowercase(Locale.ROOT))
        return if (index >= 0) original.substring(index + marker.length) else ""
    }

    private fun String.removePrefixes(vararg prefixes: String): String {
        var value = this.trim()
        for (prefix in prefixes) if (value.lowercase(Locale.ROOT).startsWith(prefix.lowercase(Locale.ROOT))) {
            value = value.drop(prefix.length).trim()
        }
        return value
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
