
package com.idraak.quantum

import java.util.Locale

/**
 * IDRAAK Wake Word Controller
 *
 * जिम्मेदारियाँ:
 * 1. IDRAAK Wake Word पहचानना
 * 2. Voice Command Mode सक्रिय करना
 * 3. Sleep Command पहचानना
 * 4. Command Mode की स्थिति संभालना
 *
 * यह Controller SpeechRecognizer से प्राप्त Text पर काम करता है।
 * वास्तविक लगातार Wake Word Detection के लिए अलग Keyword Spotting
 * Engine की आवश्यकता होगी।
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
            "نم",
            "نم جاؤ",
            "اذهب للنوم"
        )

        private val COMMAND_PREFIXES = listOf(
            "idraak",
            "idraaq",
            "idrak",
            "इद्राक",
            "आईड्राक",
            "इत्राक"
        )
    }

    @Volatile
    private var currentState = VoiceState.SLEEPING

    @Volatile
    private var enabled = true

    fun getState(): VoiceState {
        return currentState
    }

    fun isActive(): Boolean {
        return enabled && currentState == VoiceState.ACTIVE
    }

    fun isEnabled(): Boolean {
        return enabled
    }

    /**
     * SpeechRecognizer से प्राप्त Text यहाँ भेजें।
     */
    @Synchronized
    fun processRecognizedText(
        recognizedText: String
    ) {

        if (!enabled || recognizedText.isBlank()) {
            return
        }

        val text = normalize(recognizedText)

        if (text.isBlank()) return

        // Sleep Command हर स्थिति में प्राथमिकता रखता है।
        if (containsAny(text, SLEEP_WORDS)) {
            sleep()
            return
        }

        when (currentState) {

            VoiceState.SLEEPING -> {

                if (containsWakeWord(text)) {

                    val remainingCommand =
                        removeWakeWord(text)

                    if (remainingCommand.isNotBlank()) {

                        setState(VoiceState.ACTIVE)

                        onWakeWordDetected()

                        onCommandDetected(
                            remainingCommand
                        )

                    } else {

                        setState(VoiceState.ACTIVE)

                        onWakeWordDetected()
                    }
                }
            }

            VoiceState.ACTIVE -> {

                if (containsWakeWord(text)) {

                    val remainingCommand =
                        removeWakeWord(text)

                    if (remainingCommand.isNotBlank()) {
                        onCommandDetected(
                            remainingCommand
                        )
                    }

                } else {

                    onCommandDetected(text)
                }
            }

            VoiceState.PROCESSING -> {
                // Processing के दौरान Duplicate Commands नहीं भेजेंगे।
            }
        }
    }

    /**
     * AI Controller को बताता है कि Command Processing पूरी हुई।
     */
    @Synchronized
    fun finishProcessing() {

        if (!enabled) return

        if (currentState == VoiceState.PROCESSING) {
            setState(VoiceState.ACTIVE)
        }
    }

    /**
     * Command Processing शुरू करना।
     */
    @Synchronized
    fun startProcessing() {

        if (!enabled) return

        if (currentState == VoiceState.ACTIVE) {
            setState(VoiceState.PROCESSING)
        }
    }

    /**
     * IDRAAK को Sleeping Mode में डालना।
     */
    @Synchronized
    fun sleep() {

        if (currentState == VoiceState.SLEEPING) {
            return
        }

        setState(VoiceState.SLEEPING)
        onSleepDetected()
    }

    /**
     * Voice Controller को Enable / Disable करना।
     */
    @Synchronized
    fun setEnabled(value: Boolean) {

        enabled = value

        if (!value) {
            if (currentState != VoiceState.SLEEPING) {
                sleep()
            } else {
                setState(VoiceState.SLEEPING)
            }
        }
    }

    /**
     * Command Mode को दोबारा सक्रिय करना।
     */
    @Synchronized
    fun activate() {

        if (!enabled) return

        setState(VoiceState.ACTIVE)
    }

    /**
     * Wake Word पहचानना।
     */
    private fun containsWakeWord(text: String): Boolean {

        return WAKE_WORDS.any { word ->
            containsPhrase(text, word)
        }
    }

    /**
     * Wake Word हटाकर वास्तविक Command निकालना।
     */
    private fun removeWakeWord(text: String): String {

        var result = text

        for (word in COMMAND_PREFIXES) {

            val pattern = Regex(
                "(?<![\\p{L}\\p{N}])" +
                    Regex.escape(word) +
                    "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE
            )

            result = pattern.replace(result, " ")
        }

        for (word in listOf(
            "इद्राक",
            "इद्राक्",
            "आईड्राक",
            "आइड्राक",
            "इत्राक"
        )) {

            result = result.replace(word, " ")
        }

        return result
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Phrase Matching।
     */
    private fun containsAny(
        text: String,
        phrases: List<String>
    ): Boolean {

        return phrases.any { phrase ->
            containsPhrase(text, normalize(phrase))
        }
    }

    private fun containsPhrase(
        text: String,
        phrase: String
    ): Boolean {

        if (phrase.isBlank()) return false

        if (phrase.any { it.code > 127 }) {
            return text.contains(phrase)
        }

        val pattern = Regex(
            "(?<![\\p{L}\\p{N}])" +
                Regex.escape(phrase) +
                "(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )

        return pattern.containsMatchIn(text)
    }

    /**
     * Hindi, English, Urdu और Arabic Text Normalization।
     */
    private fun normalize(value: String): String {

        return value
            .lowercase(Locale.ROOT)
            .replace("ā", "a")
            .replace("ī", "i")
            .replace("ū", "u")
            .replace("’", "'")
            .replace("'", "")
            .replace(Regex("[،,!?؛:।]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun setState(
        newState: VoiceState
    ) {

        if (currentState == newState) return

        currentState = newState

        onStateChanged(newState)
    }
}
