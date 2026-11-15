package com.idraak.quantum

import java.util.Locale

/**

- IDRAAK Wake Word Controller
- 
- Responsibilities:
- 1. Detect IDRAAK wake word.
- 2. Activate voice command mode.
- 3. Detect sleep commands in supported languages.
- 4. Manage voice states safely.
- 
- This controller processes text received from SpeechRecognizer.
- It is not a dedicated always-on keyword spotting engine.
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

    private val WORD_BOUNDARY_CHARS =  
        "[\\p{L}\\p{M}\\p{N}]"  
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
 * Process recognized speech text.  
 */  
@Synchronized  
fun processRecognizedText(recognizedText: String) {  

    if (!enabled || recognizedText.isBlank()) {  
        return  
    }  

    val text = normalize(recognizedText)  

    if (text.isBlank()) {  
        return  
    }  

    // Sleep commands have priority in every state.  
    if (containsAny(text, SLEEP_WORDS)) {  
        sleep()  
        return  
    }  

    when (currentState) {  

        VoiceState.SLEEPING -> {  

            if (!containsWakeWord(text)) {  
                return  
            }  

            val remainingCommand = removeWakeWord(text)  

            setState(VoiceState.ACTIVE)  

            onWakeWordDetected()  

            // Support: "IDRAAK open YouTube"  
            if (remainingCommand.isNotBlank()) {  
                onCommandDetected(remainingCommand)  
            }  
        }  

        VoiceState.ACTIVE -> {  

            if (containsWakeWord(text)) {  

                val remainingCommand = removeWakeWord(text)  

                if (remainingCommand.isNotBlank()) {  
                    onCommandDetected(remainingCommand)  
                }  

            } else {  

                // Already active: accept the next command.  
                onCommandDetected(text)  
            }  
        }  

        VoiceState.PROCESSING -> {  
            // Ignore duplicate commands while processing.  
        }  
    }  
}  

/**  
 * Mark command processing as complete.  
 */  
@Synchronized  
fun finishProcessing() {  

    if (!enabled) return  

    if (currentState == VoiceState.PROCESSING) {  
        setState(VoiceState.ACTIVE)  
    }  
}  

/**  
 * Mark command processing as started.  
 */  
@Synchronized  
fun startProcessing() {  

    if (!enabled) return  

    if (currentState == VoiceState.ACTIVE) {  
        setState(VoiceState.PROCESSING)  
    }  
}  

/**  
 * Put IDRAAK into sleeping mode.  
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
 * Enable or disable voice controller.  
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
 * Activate command mode manually.  
 */  
@Synchronized  
fun activate() {  

    if (!enabled) return  

    setState(VoiceState.ACTIVE)  
}  

/**  
 * Detect supported wake words.  
 */  
private fun containsWakeWord(text: String): Boolean {  

    return WAKE_WORDS.any { word ->  
        containsPhrase(text, word)  
    }  
}  

/**  
 * Remove wake words from recognized text.  
 */  
private fun removeWakeWord(text: String): String {  

    var result = text  

    for (word in WAKE_WORDS) {  

        val pattern = Regex(  
            "(?<!$WORD_BOUNDARY_CHARS)" +  
                Regex.escape(normalize(word)) +  
                "(?!$WORD_BOUNDARY_CHARS)",  
            RegexOption.IGNORE_CASE  
        )  

        result = pattern.replace(result, " ")  
    }  

    return result  
        .replace(Regex("\\s+"), " ")  
        .trim()  
}  

/**  
 * Match any phrase from a list.  
 */  
private fun containsAny(  
    text: String,  
    phrases: List<String>  
): Boolean {  

    return phrases.any { phrase ->  
        containsPhrase(text, normalize(phrase))  
    }  
}  

/**  
 * Match a complete phrase, not a substring inside another word.  
 */  
private fun containsPhrase(  
    text: String,  
    phrase: String  
): Boolean {  

    if (phrase.isBlank()) return false  

    val pattern = Regex(  
        "(?<!$WORD_BOUNDARY_CHARS)" +  
            Regex.escape(phrase) +  
            "(?!$WORD_BOUNDARY_CHARS)",  
        RegexOption.IGNORE_CASE  
    )  

    return pattern.containsMatchIn(text)  
}  

/**  
 * Normalize Hindi, English, Urdu and Arabic text.  
 */  
private fun normalize(value: String): String {  

    return value  
        .lowercase(Locale.ROOT)  
        .replace(ARABIC_DIACRITICS, "")  
        .replace("ā", "a")  
        .replace("ī", "i")  
        .replace("ū", "u")  
        .replace("’", "'")  
        .replace("'", "")  
        .replace(Regex("[،,!?؛:।؟]+"), " ")  
        .replace(Regex("[\\u200C\\u200D\\uFEFF]"), "")  
        .replace(Regex("\\s+"), " ")  
        .trim()  
}  

/**  
 * Update state only when it actually changes.  
 */  
private fun setState(newState: VoiceState) {  

    if (currentState == newState) return  

    currentState = newState  

    onStateChanged(newState)  
}

}
