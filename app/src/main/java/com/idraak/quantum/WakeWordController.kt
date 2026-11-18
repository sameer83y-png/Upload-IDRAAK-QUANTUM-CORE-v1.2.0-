package com.idraak.quantum

import java.text.Normalizer
import java.util.Locale

internal class WakeWordController(
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
            "इद्राख",
            "इदराज",
            "इदराक",
            "इधराक",
            "इधराख",
            "इद्राज",
            "इतराक",
            "इतराख",
            "इतराज"
        )

        private val SLEEP_WORDS = listOf(
            "सो जाओ",
            "स्लीप",
            "बंद हो जाओ",
            "आराम करो",
            "sleep",
            "go to sleep",
            "stop listening"
        )

        private const val MAX_EDIT_DISTANCE = 2
    }

    private var state = VoiceState.SLEEPING
    private var wakeWordEnabled = true

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        setWakeWordEnabled(enabled)
    }

    @Synchronized
    fun getState(): VoiceState {
        return state
    }

    @Synchronized
    fun isProcessing(): Boolean {
        return state == VoiceState.PROCESSING
    }

    @Synchronized
    fun getVoiceState(): VoiceState {
        return state
    }

    @Synchronized
    fun setWakeWordEnabled(enabled: Boolean) {

        wakeWordEnabled = enabled

        changeState(
            if (enabled) {
                VoiceState.SLEEPING
            } else {
                VoiceState.ACTIVE
            }
        )
    }

    @Synchronized
    fun activate() {
        changeState(VoiceState.ACTIVE)
    }

    @Synchronized
    fun sleep() {

        changeState(
            if (wakeWordEnabled) {
                VoiceState.SLEEPING
            } else {
                VoiceState.ACTIVE
            }
        )

        onSleepDetected()
    }

    @Synchronized
    fun startProcessing() {
        if (state == VoiceState.ACTIVE) {
            changeState(VoiceState.PROCESSING)
        }
    }

    @Synchronized
    fun finishProcessing() {
        if (state == VoiceState.PROCESSING) {
            changeState(VoiceState.ACTIVE)
        }
    }

    /**
     * Vosk wake-word mode:
     * When sleeping, detect only the wake word.
     * Do not forward the remaining Vosk transcript.
     *
     * Google Speech Recognition will capture the actual command
     * after the Vosk microphone has been released.
     *
     * When ACTIVE, recognized text is treated as a command.
     * This also allows Google recognition results to use the
     * existing VoiceCommandRouter.
     */
    @Synchronized
    fun processRecognizedText(recognizedText: String) {

        val text = normalizeText(recognizedText)

        if (text.isBlank()) return

        when (state) {

            VoiceState.SLEEPING -> {

                if (!wakeWordEnabled) {
                    changeState(VoiceState.ACTIVE)
                    processRecognizedText(text)
                    return
                }

                val wakeMatch = findWakeWord(text) ?: return

                // Wake word मिला।
                // Google recognition handoff callback शुरू होगा।
                onWakeWordDetected()

                // अब Google का command result ACTIVE state में आएगा।
                changeState(VoiceState.ACTIVE)

                // जानबूझकर Vosk का बाकी transcript आगे नहीं भेजते।
                // इससे एक ही command दो बार execute नहीं होगा।
            }

            VoiceState.ACTIVE -> {

                if (containsSleepPhrase(text)) {
                    sleep()
                    return
                }

                val wakeMatch = findWakeWord(text)

                val command = if (wakeMatch != null) {

                    removeMatchedWord(
                        text,
                        wakeMatch
                    )

                } else {
                    text
                }

                if (command.isNotBlank()) {
                    onCommandDetected(command)
                }
            }

            VoiceState.PROCESSING -> {

                if (containsSleepPhrase(text)) {
                    sleep()
                }
            }
        }
    }

    private fun changeState(newState: VoiceState) {

        if (state == newState) return

        state = newState

        onStateChanged(newState)
    }

    private fun normalizeText(input: String): String {

        var text = Normalizer.normalize(
            input,
            Normalizer.Form.NFC
        ).lowercase(Locale.ROOT)

        text = text
            .replace("़", "")
            .replace("ـ", "")
            .replace(
                Regex("[\\u064B-\\u065F\\u0670]"),
                ""
            )
            .replace(
                Regex("[^\\p{L}\\p{N}\\s]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()

        return text
    }

    private fun findWakeWord(text: String): String? {

        val words = text.split(Regex("\\s+"))

        for (word in words) {

            if (word.isBlank()) continue

            for (alias in WAKE_WORDS) {

                val normalizedAlias = normalizeText(alias)

                if (word == normalizedAlias) {
                    return word
                }

                val minimumLength = minOf(
                    word.length,
                    normalizedAlias.length
                )

                if (minimumLength < 4) continue

                val distance = levenshteinDistance(
                    word,
                    normalizedAlias
                )

                if (distance <= MAX_EDIT_DISTANCE) {
                    return word
                }
            }
        }

        return null
    }

    private fun removeMatchedWord(
        text: String,
        matchedWord: String
    ): String {

        val words = text
            .split(Regex("\\s+"))
            .toMutableList()

        val index = words.indexOf(matchedWord)

        if (index >= 0) {
            words.removeAt(index)
        }

        return words.joinToString(" ").trim()
    }

    private fun containsSleepPhrase(text: String): Boolean {

        return SLEEP_WORDS.any { phrase ->

            val normalizedPhrase = normalizeText(phrase)

            text == normalizedPhrase ||
                text.contains(" $normalizedPhrase ") ||
                text.startsWith("$normalizedPhrase ") ||
                text.endsWith(" $normalizedPhrase")
        }
    }

    private fun levenshteinDistance(
        first: String,
        second: String
    ): Int {

        if (first == second) return 0

        if (first.isEmpty()) return second.length

        if (second.isEmpty()) return first.length

        var previous = IntArray(second.length + 1) {
            it
        }

        var current = IntArray(second.length + 1)

        for (i in first.indices) {

            current[0] = i + 1

            for (j in second.indices) {

                val cost = if (
                    first[i] == second[j]
                ) {
                    0
                } else {
                    1
                }

                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }

            val temporary = previous
            previous = current
            current = temporary
        }

        return previous[second.length]
    }
}
