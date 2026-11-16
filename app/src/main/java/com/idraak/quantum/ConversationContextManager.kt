
package com.idraak.quantum

/**
 * IDRAAK - Conversation Context Manager
 *
 * Responsibility:
 * Maintains conversation context and message history
 * for online and offline AI processing.
 *
 * Does not communicate with Gemini,
 * recognize speech, parse phone commands,
 * or execute Android actions.
 *
 * Conversation history is maintained in memory only.
 */
internal class ConversationContextManager(
    private val maxMessages: Int = 20
) {

    enum class Role {
        USER,
        ASSISTANT
    }

    data class Message(
        val role: Role,
        val content: String,
        val timestamp: Long
    )

    private val messages = mutableListOf<Message>()

    init {
        require(maxMessages > 0) {
            "Maximum message count must be greater than zero."
        }
    }

    /**
     * Adds a user message to conversation history.
     */
    @Synchronized
    fun addUserMessage(content: String) {
        addMessage(Role.USER, content)
    }

    /**
     * Adds an assistant response to conversation history.
     */
    @Synchronized
    fun addAssistantMessage(content: String) {
        addMessage(Role.ASSISTANT, content)
    }

    /**
     * Adds a message and removes the oldest messages
     * when the configured history limit is exceeded.
     */
    @Synchronized
    private fun addMessage(
        role: Role,
        content: String
    ) {
        val cleanContent = content.trim()

        if (cleanContent.isEmpty()) return

        messages.add(
            Message(
                role = role,
                content = cleanContent,
                timestamp = System.currentTimeMillis()
            )
        )

        while (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }

    /**
     * Returns a safe copy of the complete conversation history.
     */
    @Synchronized
    fun getMessages(): List<Message> {
        return messages.toList()
    }

    /**
     * Returns the most recent messages.
     */
    @Synchronized
    fun getRecentMessages(limit: Int): List<Message> {
        if (limit <= 0 || messages.isEmpty()) {
            return emptyList()
        }

        return messages
            .takeLast(limit)
            .toList()
    }

    /**
     * Returns the current number of stored messages.
     */
    @Synchronized
    fun getMessageCount(): Int {
        return messages.size
    }

    /**
     * Checks whether conversation history is empty.
     */
    @Synchronized
    fun isEmpty(): Boolean {
        return messages.isEmpty()
    }

    /**
     * Clears all conversation history from memory.
     */
    @Synchronized
    fun clear() {
        messages.clear()
    }
}
