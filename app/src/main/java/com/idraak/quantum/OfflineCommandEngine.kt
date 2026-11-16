package com.idraak.quantum

import java.util.Locale

/**
 * सीमित offline conversational replies देता है।
 * Phone commands का parsing और execution दूसरी classes करती हैं।
 */
internal class OfflineCommandEngine(
    private val normalizer: SpeechTextNormalizer =
        SpeechTextNormalizer()
) {

    fun respond(userText: String): String? {
        val text = normalizer.normalize(userText)
        if (text.isBlank()) return null

        return when {
            containsAny(
                text,
                "नमस्ते",
                "नमस्कार",
                "hello",
                "hi idraak",
                "السلام علیکم",
                "مرحبا"
            ) -> {
                "नमस्ते! मैं IDRAAK हूँ। बताइए, क्या करना है?"
            }

            containsAny(
                text,
                "तुम कौन हो",
                "तुम्हारा नाम क्या है",
                "who are you",
                "what is your name",
                "من أنت",
                "آپ کون ہیں"
            ) -> {
                "मैं IDRAAK हूँ, आपका voice-controlled Android assistant।"
            }

            containsAny(
                text,
                "तुम क्या कर सकते हो",
                "help",
                "मदद करो",
                "what can you do",
                "ماذا تستطيع"
            ) -> {
                "मैं offline रहते हुए सीमित Hindi, English, Urdu और Arabic commands समझ सकता हूँ। " +
                    "आप supported phone actions जैसे volume, brightness, flashlight, camera, " +
                    "Back, Home और screen reading के commands दे सकते हैं।"
            }

            containsAny(
                text,
                "तुम्हें कौन सी भाषा आती है",
                "which languages",
                "supported languages",
                "ما هي اللغات"
            ) -> {
                "मेरे supported offline languages Hindi, English, Urdu और Arabic हैं।"
            }

            containsAny(
                text,
                "धन्यवाद",
                "शुक्रिया",
                "thank you",
                "thanks",
                "شکریہ"
            ) -> {
                "आपका स्वागत है।"
            }

            containsAny(
                text,
                "क्या तुम सुन रहे हो",
                "are you listening",
                "سن رہے ہو"
            ) -> {
                "हाँ, मैं सुन रहा हूँ। अपना command बताइए।"
            }

            else -> null
        }
    }

    private fun containsAny(
        text: String,
        vararg phrases: String
    ): Boolean {
        return phrases.any {
            text.contains(it.lowercase(Locale.ROOT))
        }
    }
}
