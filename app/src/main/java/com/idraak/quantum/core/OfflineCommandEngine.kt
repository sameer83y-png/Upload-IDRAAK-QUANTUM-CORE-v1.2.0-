package com.idraak.quantum.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** First offline command layer. Android execution stays separate for permission-aware handling. */
class OfflineCommandEngine {
    fun process(rawText: String): CommandResult {
        val text = rawText.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return CommandResult("Please enter a command.")
        if (containsAny(text, "hello", "hi", "hey", "salam", "assalam")) {
            return CommandResult("Hello. Idraak is ready.")
        }
        if (containsAny(text, "open settings", "phone settings", "settings kholo", "सेटिंग")) {
            return CommandResult("Opening phone settings.", CommandAction.OpenSettings)
        }
        if (containsAny(text, "open camera", "camera kholo", "कैमरा खोल")) {
            return CommandResult("Opening camera.", CommandAction.OpenCamera)
        }

        val youtubePrefix = listOf("search youtube for", "youtube par", "youtube pe", "यूट्यूब पर")
            .firstOrNull { text.contains(it) }
        if (youtubePrefix != null) {
            val query = rawText.substringAfter(youtubePrefix, "").trim()
            if (query.isNotBlank()) return CommandResult(
                "Opening YouTube search for: $query",
                CommandAction.OpenUrl("https://www.youtube.com/results?search_query=" + encode(query))
            )
            return CommandResult("What should I search for on YouTube?")
        }

        val webPrefix = listOf("search web for", "google", "search for", "वेब पर खोजो")
            .firstOrNull { text.startsWith(it) }
        if (webPrefix != null) {
            val query = rawText.drop(webPrefix.length).trim()
            if (query.isNotBlank()) return CommandResult(
                "Searching the web for: $query",
                CommandAction.OpenUrl("https://www.google.com/search?q=" + encode(query))
            )
        }

        if (text.startsWith("open https://") || text.startsWith("open http://")) {
            val url = rawText.substringAfter("open ").trim()
            return CommandResult("Opening website.", CommandAction.OpenUrl(url))
        }
        return CommandResult("I don't have an offline command for that yet. This will be added to the command coverage plan.")
    }

    private fun containsAny(value: String, vararg candidates: String): Boolean =
        candidates.any(value::contains)

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
