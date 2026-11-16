
package com.idraak.quantum

import java.text.Normalizer
import java.util.Locale

/**
 * IDRAAK - Speech Text Normalizer
 *
 * Responsibility:
 * Cleans and normalizes recognized speech text.
 *
 * Supports Hindi, English, Urdu, and Arabic text.
 * Does not recognize speech, interpret commands,
 * or execute actions.
 */
internal class SpeechTextNormalizer {

    /**
     * Removes unnecessary whitespace and control characters.
     */
    fun clean(text: String): String {
        return text
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Normalizes Unicode text without removing
     * Hindi, Urdu, or Arabic characters.
     */
    fun normalize(text: String): String {
        return Normalizer.normalize(
            clean(text),
            Normalizer.Form.NFC
        )
    }

    /**
     * Produces a comparison-friendly version of text.
     * English letters are converted to lowercase.
     * Other supported scripts are preserved.
     */
    fun normalizeForComparison(text: String): String {
        return normalize(text).lowercase(Locale.ROOT)
    }

    /**
     * Checks whether recognized speech contains usable text.
     */
    fun isEmpty(text: String): Boolean {
        return normalize(text).isEmpty()
    }
}
