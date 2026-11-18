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
    fun getVoiceState(): VoiceState = state

    @Synchronized
    fun setWakeWordEnabled(enabled: Boolean) {

        wakeWordEnabled = enabled

        val newState = if (enabled) {
            VoiceState.SLEEPING
        } else {
            VoiceState.ACTIVE
        }

        changeState(newState)
    }

    @Synchronized
    fun activate() {
        changeState(VoiceState.ACTIVE)
    }

    @Synchronized
    fun sleep() {

        if (wakeWordEnabled) {
            changeState(VoiceState.SLEEPING)
        } else {
            changeState(VoiceState.ACTIVE)
        }

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

                val wakeMatch = findWakeWord(text)

                if (wakeMatch == null) return

                onWakeWordDetected()

                changeState(VoiceState.ACTIVE)

                val command = removeMatchedWord(
                    text,
                    wakeMatch
                ).trim()

                if (command.isNotBlank()) {
                    onCommandDetected(command)
                }
            }

            VoiceState.ACTIVE -> {

                if (containsSleepPhrase(text)) {
                    sleep()
                    return
                }

                val wakeMatch = findWakeWord(text)

                val command = if (wakeMatch != null) {
                    onWakeWordDetected()
                    removeMatchedWord(text, wakeMatch).trim()
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
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
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

                val distance = levenshteinDistance(
                    word,
                    normalizedAlias
                )

                val minimumLength = minOf(
                    word.length,
                    normalizedAlias.length
                )

                // छोटे शब्दों को Wake Word नहीं मानना।
                if (
                    minimumLength >= 4 &&
                    distance <= MAX_EDIT_DISTANCE
                ) {
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

        val words = text.split(Regex("\\s+")).toMutableList()

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
                ) 0 else 1

                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }

            val temp = previous
            previous = current
            current = temp
        }

        return previous[second.length]
    }
}
