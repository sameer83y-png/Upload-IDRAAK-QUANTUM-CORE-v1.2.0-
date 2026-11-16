
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
 * Preserves ZWNJ and ZWJ characters required
 * for correct Urdu and Arabic text shaping.
 *
 * Does not recognize speech, interpret commands,
 * or execute actions.
 */
internal class SpeechTextNormalizer {

    /**
     * Removes unnecessary control and format characters
     * while preserving ZWNJ and ZWJ.
     */
    fun clean(text: String): String {
        return text
            .replace(
                Regex("[\\p{Cc}\\p{Cf}&&[^\\u200C\\u200D]]"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Normalizes Unicode text using NFC.
     */
    fun normalize(text: String): String {
        return Normalizer.normalize(
            clean(text),
            Normalizer.Form.NFC
        )
    }

    /**
     * Produces comparison-friendly normalized text.
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
