package com.idraak.quantum

import java.text.Normalizer
import java.util.Locale

/**
 * Speech recognition से मिले text को command matching के लिए normalize करता है।
 * मूल text को अलग से सुरक्षित रखा जा सकता है; यह class कोई command execute नहीं करती।
 */
class SpeechTextNormalizer {

    fun normalize(text: String): String {
        if (text.isBlank()) return ""

        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace('\u200B', ' ')
            .replace('\uFEFF', ' ')
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("[\\u0964\\u0965]"), " ")
            .replace(Regex("[,،؛;:!?؟।]+"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s._-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun containsWakeWord(text: String): Boolean {
        val normalized = normalize(text)
        val wakeWords = listOf(
            "idraak",
            "इद्राक",
            "आईड्राक",
            "इत्राक",
            "ایڈراک"
        )
        return wakeWords.any { normalized.contains(it) }
    }

    fun removeWakeWord(text: String): String {
        var result = normalize(text)
        val wakeWords = listOf(
            "idraak",
            "इद्राक",
            "आईड्राक",
            "इत्राक",
            "ایڈراک"
        )

        wakeWords.forEach { word ->
            result = result.replace(word, " ")
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }
}
