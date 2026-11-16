package com.idraak.quantum

/**
 * Gemini को भेजे जाने वाले prompt को तैयार करता है।
 *
 * HTTP request, API key और JSON payload का निर्माण
 * Python idraak_core.py की जिम्मेदारी है।
 */
internal class GeminiRequestBuilder {

    fun build(
        userMessage: String,
        conversation: List<ConversationMessage>
    ): String {
        val currentMessage = userMessage.trim()
        if (currentMessage.isEmpty()) return ""

        val history = conversation
            .takeLast(MAX_HISTORY_MESSAGES)
            .filter { it.content.isNotBlank() }
            .joinToString("\n") { message ->
                val role = when (message.role.lowercase()) {
                    "user" -> "User"
                    "assistant", "model" -> "IDRAAK"
                    else -> "Context"
                }

                "$role: ${message.content.trim()}"
            }

        return buildString {
            appendLine(
                "You are IDRAAK, the user's multilingual Android assistant."
            )
            appendLine(
                "Reply in the same language as the user's latest message."
            )
            appendLine(
                "Supported offline languages are Hindi, English, Urdu and Arabic."
            )
            appendLine(
                "Be concise, natural, helpful and respectful."
            )
            appendLine(
                "Never claim that a phone action was completed unless the Android app confirms it."
            )
            appendLine(
                "Ask for confirmation before sending messages, making payments, " +
                    "or performing sensitive account or security actions."
            )

            if (history.isNotBlank()) {
                appendLine()
                appendLine("Recent conversation:")
                appendLine(history)
            }

            appendLine()
            appendLine("Latest user message:")
            append(currentMessage)
        }
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 10
    }
}
