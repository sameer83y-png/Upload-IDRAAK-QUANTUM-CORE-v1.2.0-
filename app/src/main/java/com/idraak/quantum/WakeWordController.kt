
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
 * - Forward recognized commands to the command handler.
 *
 * Speech recognition is handled by VoiceForegroundService.
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

        private val WORD_BOUNDARY_CHARS =
            "[\\p{L}\\p{M}\\p{N}]"

        private val WHITESPACE =
            Regex("\\s+")

        private val PUNCTUATION =
            Regex("[،,!?؛:।؟.!]+")
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

    /**
     * Processes recognized speech.
     *
     * Sleep commands have priority in every state.
     * Commands are accepted only in ACTIVE state.
     */
    @Synchronized
    fun processRecognizedText(recognizedText: String) {

        if (!enabled || recognizedText.isBlank()) return

        val text = normalize(recognizedText)

        if (text.isBlank()) return

        // Sleep command works in every state.
        if (containsAny(text, SLEEP_WORDS)) {
            sleep()
            return
        }

        when (currentState) {

            VoiceState.SLEEPING -> {

                if (!containsWakeWord(text)) return

                val remainingCommand = removeWakeWord(text)

                setState(VoiceState.ACTIVE)

                onWakeWordDetected()

                // Supports combined speech:
                // "IDRAAK open WhatsApp"
                if (remainingCommand.isNotBlank()) {
                    onCommandDetected(remainingCommand)
                }
            }

            VoiceState.ACTIVE -> {

                val command = if (containsWakeWord(text)) {
                    removeWakeWord(text)
                } else {
                    text
                }

                if (command.isNotBlank()) {
                    onCommandDetected(command)
                }
            }

            VoiceState.PROCESSING -> {
                // Ignore additional commands while processing.
                // Sleep commands are already handled above.
            }
        }
    }

    /**
     * Call when command processing has completed.
     */
    @Synchronized
    fun finishProcessing() {

        if (!enabled) return

        if (currentState == VoiceState.PROCESSING) {
            setState(VoiceState.ACTIVE)
        }
    }

    /**
     * Call immediately before processing a command.
     */
    @Synchronized
    fun startProcessing() {

        if (!enabled) return

        if (currentState == VoiceState.ACTIVE) {
            setState(VoiceState.PROCESSING)
        }
    }

    /**
     * Moves IDRAAK into sleeping state.
     */
    @Synchronized
    fun sleep() {

        if (currentState == VoiceState.SLEEPING) return

        setState(VoiceState.SLEEPING)

        onSleepDetected()
    }

    /**
     * Enables or disables voice command processing.
     */
    @Synchronized
    fun setEnabled(value: Boolean) {

        if (enabled == value) return

        enabled = value

        if (!value) {

            if (currentState != VoiceState.SLEEPING) {
                setState(VoiceState.SLEEPING)
                onSleepDetected()
            } else {
                setState(VoiceState.SLEEPING)
            }
        }
    }

    /**
     * Manually activates the controller.
     */
    @Synchronized
    fun activate() {

        if (!enabled) return

        setState(VoiceState.ACTIVE)
    }

    /**
     * Checks whether recognized text contains a wake word.
     */
    private fun containsWakeWord(text: String): Boolean =
        WAKE_WORDS.any { word ->
            containsPhrase(text, normalize(word))
        }

    /**
     * Removes all recognized wake words from the text.
     */
    private fun removeWakeWord(text: String): String {

        var result = text

        for (word in WAKE_WORDS) {

            val normalizedWord = normalize(word)

            val pattern = Regex(
                "(?<!$WORD_BOUNDARY_CHARS)" +
                    Regex.escape(normalizedWord) +
                    "(?!$WORD_BOUNDARY_CHARS)"
            )

            result = pattern.replace(result, " ")
        }

        return result
            .replace(WHITESPACE, " ")
            .trim()
    }

    /**
     * Checks recognized text against sleep phrases.
     */
    private fun containsAny(
        text: String,
        phrases: List<String>
    ): Boolean =
        phrases.any { phrase ->
            containsPhrase(text, normalize(phrase))
        }

    /**
     * Matches complete words or phrases.
     * Prevents accidental partial-word matches.
     */
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

    /**
     * Normalizes recognized text across supported languages.
     */
    private fun normalize(value: String): String {

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
            .replace(Regex("[\\u200C\\u200D\\uFEFF]"), "")
            .replace(WHITESPACE, " ")
            .trim()

        return result
    }

    /**
     * Updates voice state and notifies the state listener.
     */
    private fun setState(newState: VoiceState) {

        if (currentState == newState) return

        currentState = newState

        onStateChanged(newState)
    }
}
