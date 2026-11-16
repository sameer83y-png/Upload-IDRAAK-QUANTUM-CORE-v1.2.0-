package com.idraak.quantum

import android.content.Context

/**
 * IDRAAK AI orchestration layer.
 *
 * जिम्मेदारियाँ:
 * - बातचीत का इतिहास लेना और अपडेट करना
 * - Prompt तैयार करवाना
 * - GeminiApiClient को request देना
 * - Response validate करवाना
 * - Error को user-readable बनाना
 *
 * यह Android phone commands execute नहीं करता।
 */
internal class AIEngine(context: Context) {

    private val settingsManager = SettingsManager(
        context.applicationContext
    )

    private val geminiApiClient = GeminiApiClient(
        context.applicationContext
    )

    private val requestBuilder = GeminiRequestBuilder()
    private val responseParser = GeminiResponseParser()
    private val errorMapper = GeminiErrorMapper()

    val conversationContext = ConversationContextManager()

    @Volatile
    private var requestInProgress = false

    suspend fun generateResponse(userMessage: String): String {
        val message = userMessage.trim()

        if (message.isEmpty()) {
            return "कृपया अपना सवाल बताइए।"
        }

        synchronized(this) {
            if (requestInProgress) {
                return "मैं अभी पिछले सवाल का जवाब तैयार कर रहा हूँ।"
            }
            requestInProgress = true
        }

        return try {
            if (settingsManager.offlineMode) {
                return "ऑफलाइन मोड चालू है। इस समय Gemini AI उपलब्ध नहीं है।"
            }

            val apiKey = settingsManager.apiKey.trim()

            if (apiKey.isEmpty()) {
                return "पहले IDRAAK Settings में Gemini API key डालें।"
            }

            val prompt = requestBuilder.build(
                userMessage = message,
                conversation = conversationContext.getMessages()
            )

            if (prompt.isBlank()) {
                return "आपका सवाल पढ़ा नहीं जा सका। कृपया दोबारा बोलें।"
            }

            val rawResponse = geminiApiClient.generateResponse(
                prompt = prompt,
                apiKey = apiKey,
                model = settingsManager.geminiModel
            )

            val answer = responseParser.parse(rawResponse)

            conversationContext.addUserMessage(message)
            conversationContext.addAssistantMessage(answer)

            answer
        } catch (error: Exception) {
            errorMapper.map(error)
        } finally {
            synchronized(this) {
                requestInProgress = false
            }
        }
    }

    fun clearConversation() {
        conversationContext.clear()
    }

    fun isRequestInProgress(): Boolean =
        requestInProgress
}
