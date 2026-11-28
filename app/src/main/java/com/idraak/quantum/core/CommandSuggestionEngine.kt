package com.idraak.quantum.core

import java.util.Locale

/**
 * Finds the closest supported command family when the offline parser cannot
 * understand a request. Suggestions intentionally use phrases already
 * accepted by OfflineCommandEngine; no command is executed from this class.
 */
object CommandSuggestionEngine {
    fun suggestionsFor(rawCommand: String): String? {
        val value = rawCommand.trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        if (value.isBlank()) return null

        return when {
            hasAny(value, "youtube", "you tube", "यूट्यूब", "یوٹیوب", "یوتیوب") ->
                "شاید آپ یوٹیوب کھولنا چاہتے ہیں۔ یہ حکم دیں: “youtube kholo”۔ کسی چیز کو تلاش کرنے کے لیے کہیں: “youtube par cricket search karo” یا “youtube kholo aur cricket search karo”۔"

            hasAny(value, "camera", "कैमरा", "کیمرہ", "کیمرا") ->
                "کیمرہ کھولنے کے لیے کہیں: “open camera” یا “camera kholo”۔"

            hasAny(value, "chrome", "browser", "ब्राउज़र", "براوزر", "کروم") ->
                "براؤزر کھولنے کے لیے کہیں: “open chrome” یا “chrome kholo”۔"

            hasAny(value, "whatsapp", "واٹس ایپ", "व्हाट्सएप") ->
                "واٹس ایپ کھولنے کے لیے کہیں: “open whatsapp” یا “whatsapp kholo”۔ پیغام بھیجنے کے لیے کہیں: “share message” کے بعد اپنا پیغام بولیں۔"

            hasAny(value, "instagram", "انسٹاگرام") ->
                "انسٹاگرام کھولنے کے لیے کہیں: “open instagram” یا “instagram kholo”۔"

            hasAny(value, "chatgpt", "چیٹ جی پی ٹی") ->
                "ChatGPT کھولنے کے لیے کہیں: “open chatgpt” یا “chatgpt kholo”۔"

            hasAny(value, "gemini", "جیمنائی", "جیمنی") ->
                "Gemini کھولنے کے لیے کہیں: “open gemini” یا “gemini kholo”۔"

            hasAny(value, "google", "گوگل", "गूगल", "internet", "انٹرنیٹ") ||
                hasAny(value, "search", "تلاش", "سرچ", "ढूंढ", "खोज") ->
                "ویب پر تلاش کے لیے کہیں: “google par latest news search karo” یا “گوگل پر نئی خبریں تلاش کرو”۔"

            hasAny(value, "wifi", "وائی فائی", "वाईफाई") ->
                "وائی فائی سیٹنگز کھولنے کے لیے کہیں: “open wifi” یا “وائی فائی سیٹنگز کھولو”۔"

            hasAny(value, "bluetooth", "بلوٹوتھ", "ब्लूटूथ") ->
                "Bluetooth سیٹنگز کھولنے کے لیے کہیں: “open bluetooth” یا “بلوٹوتھ سیٹنگز کھولو”۔"

            hasAny(value, "volume", "آواز", "والیوم", "आवाज़", "आवाज") ->
                "آواز بڑھانے کے لیے “volume up”، کم کرنے کے لیے “volume down”، اور خاموش کرنے کے لیے “mute” کہیں۔"

            hasAny(value, "alarm", "الارم", "अलार्म") ->
                "الارم لگانے کے لیے وقت کے ساتھ کہیں: “set alarm 7:30 AM” یا “الارم 19:30 لگاؤ”۔"

            hasAny(value, "screen", "اسکرین", "स्क्रीन", "ٹیپ", "کلک", "click", "tap") ->
                "اسکرین پڑھنے کے لیے “read screen”، اور کسی نظر آنے والے بٹن کے لیے “tap” کے ساتھ اس کا نام کہیں۔ اس کے لیے Idraak Accessibility فعال ہونا ضروری ہے۔"

            hasAny(value, "settings", "سیٹنگ", "सेटिंग") ->
                "فون کی سیٹنگز کھولنے کے لیے کہیں: “open settings” یا “settings kholo”۔"

            hasAny(value, "call", "کال", "فون", "कॉल") ->
                "کال کے لیے رابطے کا نام یا نمبر واضح بتائیں، مثلاً “call 9876543210”۔ Idraak ڈائلر کھولے گا تاکہ آپ کال کی تصدیق کریں۔"

            else -> null
        }
    }

    private fun hasAny(value: String, vararg terms: String): Boolean =
        terms.any { value.contains(it) }
}
