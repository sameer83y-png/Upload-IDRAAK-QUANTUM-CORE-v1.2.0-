package com.idraak.quantum

/**
 * बातचीत का सीमित इतिहास रखता है।
 * यह API call या command execution नहीं करता।
 */
data class ConversationMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ConversationContextManager(
    private val maxMessages: Int = 12
) {

    private val lock = Any()
    private val messages = ArrayDeque<ConversationMessage>()

    init {
        require(maxMessages >= 2) {
            "Conversation history must allow at least two messages"
        }
    }

    fun addUserMessage(message: String) {
        addMessage("user", message)
    }

    fun addAssistantMessage(message: String) {
        addMessage("assistant", message)
    }

    private fun addMessage(role: String, content: String) {
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) return

        synchronized(lock) {
            messages.addLast(
                ConversationMessage(
                    role = role,
                    content = cleanContent
                )
            )

            while (messages.size > maxMessages) {
                messages.removeFirst()
            }
        }
    }

    fun getMessages(): List<ConversationMessage> {
        return synchronized(lock) {
            messages.toList()
        }
    }

    fun getRecentMessages(limit: Int): List<ConversationMessage> {
        if (limit <= 0) return emptyList()

        return synchronized(lock) {
            messages.toList().takeLast(limit)
        }
    }

    fun clear() {
        synchronized(lock) {
            messages.clear()
        }
    }

    fun size(): Int {
        return synchronized(lock) {
            messages.size
        }
    }

    fun buildConversationText(): String {
        return synchronized(lock) {
            messages.joinToString("\n") { message ->
                val speaker = if (message.role == "user") {
                    "User"
                } else {
                    "IDRAAK"
                }

                "$speaker: ${message.content}"
            }
        }
    }
}
