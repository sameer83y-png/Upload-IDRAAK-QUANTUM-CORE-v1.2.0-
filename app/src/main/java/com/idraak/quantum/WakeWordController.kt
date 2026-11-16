
package com.idraak.quantum

import java.text.Normalizer
import java.util.Locale

/**
 * IDRAAK Wake Word Controller
 *
 * Responsibilities:
 * - Detect IDRAAK wake words.
 * - Manage SLEEPING, ACTIVE and PROCESSING states.
 * - Detect sleep commands in every state.
 * - Forward recognized commands exactly once.
 *
 * Speech recognition is handled by VoiceForegroundService.
 * Microphone lifecycle is not controlled by this class.
 */
class WakeWordController(
    private val onWakeWordDetected: () -> Unit,
    private val onCommandDetected: (String) -> Unit,
    private val onSleepDetected: () -> Unit,
    private val onStateChanged: (VoiceState) -> Unit
) {

    enum class VoiceState {
        SLEEPING,
        ACTIVE,
        PROCESSING
    }

    companion object {

        private val WAKE_WORDS = listOf(
            "idraak",
            "idraaq",
            "idrak",
            "इद्राक",
            "इद्राक्",
            "आईड्राक",
            "आइड्राक",
            "इत्राक"
        )

        private val SLEEP_WORDS = listOf(
            "सो जाओ",
            "सो जा",
            "स्लीप मोड",
            "sleep",
            "go to sleep",
            "sleep mode",
            "خواب",
            "سو جاؤ",
            "سو جا",
            "اذهب للنوم",
            "نم"
        )

        private val ARABIC_DIACRITICS =
            Regex("[\\u064B-\\u065F\\u0670]")

        private val ARABIC_TATWEEL =
            Regex("\\u0640")

        private const val WORD_BOUNDARY_CHARS =
            "[\\p{L}\\p{M}\\p{N}]"

        private val WHITESPACE =
            Regex("\\s+")

        private val PUNCTUATION =
            Regex("[،,!?؛:।؟.!]+")

        private val INVISIBLE_CHARACTERS =
            Regex("[\\u200C\\u200D\\uFEFF]")

        private val NORMALIZED_WAKE_WORDS: List<String> =
            WAKE_WORDS.map { word -> normalizeText(word) }

        private val NORMALIZED_SLEEP_WORDS: List<String> =
            SLEEP_WORDS.map { word -> normalizeText(word) }

        /**
         * Static normalization function.
         * Can safely be used during companion initialization.
         */
        private fun normalizeText(value: String): String {

            var result = Normalizer.normalize(
                value,
                Normalizer.Form.NFC
            )

            result = result
                .lowercase(Locale.ROOT)
                .replace(ARABIC_DIACRITICS, "")
                .replace(ARABIC_TATWEEL, "")
                .replace("ā", "a")
                .replace("ī", "i")
                .replace("ū", "u")
                .replace("’", "'")
                .replace("'", "")
                .replace(PUNCTUATION, " ")
                .replace(INVISIBLE_CHARACTERS, "")
                .replace(WHITESPACE, " ")
                .trim()

            return result
        }
    }

    @Volatile
    private var currentState = VoiceState.SLEEPING

    @Volatile
    private var enabled = true

    fun getState(): VoiceState = currentState

    fun isActive(): Boolean =
        enabled && currentState == VoiceState.ACTIVE

    fun isProcessing(): Boolean =
        enabled && currentState == VoiceState.PROCESSING

    fun isEnabled(): Boolean = enabled

    @Synchronized
    fun processRecognizedText(recognizedText: String) {

        if (!enabled || recognizedText.isBlank()) return

        val text = normalizeText(recognizedText)

        if (text.isBlank()) return

        // Sleep command has priority in every state.
        if (containsAnyPhrase(text, NORMALIZED_SLEEP_WORDS)) {
            sleep()
            return
        }

        when (currentState) {

            VoiceState.SLEEPING -> {

                if (!containsAnyPhrase(text, NORMALIZED_WAKE_WORDS)) {
                    return
                }

                val remainingCommand = removeWakeWords(text)

                setState(VoiceState.ACTIVE)

                safelyInvoke {
                    onWakeWordDetected()
                }

                if (remainingCommand.isNotBlank()) {
                    safelyInvoke {
                        onCommandDetected(remainingCommand)
                    }
                }
            }

            VoiceState.ACTIVE -> {

                val command =
                    if (containsAnyPhrase(text, NORMALIZED_WAKE_WORDS)) {
                        removeWakeWords(text)
                    } else {
                        text
                    }

                if (command.isNotBlank()) {
                    safelyInvoke {
                        onCommandDetected(command)
                    }
                }
            }

            VoiceState.PROCESSING -> {
                // Ignore new commands while processing.
                // Sleep commands are handled above.
            }
        }
    }

    @Synchronized
    fun startProcessing() {

        if (!enabled) return

        if (currentState == VoiceState.ACTIVE) {
            setState(VoiceState.PROCESSING)
        }
    }

    @Synchronized
    fun finishProcessing() {

        if (!enabled) return

        if (currentState == VoiceState.PROCESSING) {
            setState(VoiceState.ACTIVE)
        }
    }

    @Synchronized
    fun sleep() {

        if (currentState == VoiceState.SLEEPING) return

        setState(VoiceState.SLEEPING)

        safelyInvoke {
            onSleepDetected()
        }
    }

    @Synchronized
    fun setEnabled(value: Boolean) {

        if (enabled == value) return

        enabled = value

        if (!value) {

            val wasNotSleeping =
                currentState != VoiceState.SLEEPING

            setState(VoiceState.SLEEPING)

            if (wasNotSleeping) {
                safelyInvoke {
                    onSleepDetected()
                }
            }
        }
    }

    @Synchronized
    fun activate() {

        if (!enabled) return

        setState(VoiceState.ACTIVE)
    }

    private fun removeWakeWords(text: String): String {

        var result = text

        for (word in NORMALIZED_WAKE_WORDS) {

            val pattern = Regex(
                "(?<!$WORD_BOUNDARY_CHARS)" +
                    Regex.escape(word) +
                    "(?!$WORD_BOUNDARY_CHARS)"
            )

            result = pattern.replace(result, " ")
        }

        return result
            .replace(WHITESPACE, " ")
            .trim()
    }

    private fun containsAnyPhrase(
        text: String,
        phrases: List<String>
    ): Boolean {

        return phrases.any { phrase ->
            containsPhrase(text, phrase)
        }
    }

    private fun containsPhrase(
        text: String,
        phrase: String
    ): Boolean {

        if (phrase.isBlank()) return false

        val pattern = Regex(
            "(?<!$WORD_BOUNDARY_CHARS)" +
                Regex.escape(phrase) +
                "(?!$WORD_BOUNDARY_CHARS)"
        )

        return pattern.containsMatchIn(text)
    }

    private inline fun safelyInvoke(action: () -> Unit) {

        try {
            action()
        } catch (exception: Exception) {
            android.util.Log.e(
                "IDRAAK_WAKE_WORD",
                "Wake word callback failed",
                exception
            )
        }
    }
}
