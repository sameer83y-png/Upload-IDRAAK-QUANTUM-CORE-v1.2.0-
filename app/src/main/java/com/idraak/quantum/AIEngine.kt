
package com.idraak.quantum

/**
 * IDRAAK - AI Engine
 *
 * Responsibility:
 * Coordinates online and offline AI processing.
 *
 * GeminiApiClient handles the Kotlin-to-Python
 * bridge for online Gemini processing.
 *
 * OfflineCommandEngine handles local processing.
 *
 * This class does not perform HTTP requests,
 * initialize Python, recognize speech, parse phone
 * commands, or execute Android actions.
 */
internal class AIEngine(
    private val geminiApiClient: GeminiApiClient,
    private val offlineCommandEngine: OfflineCommandEngine
) {

    enum class Mode {
        ONLINE,
        OFFLINE
    }

    sealed class Result {

        data class Success(
            val response: String,
            val mode: Mode
        ) : Result()

        data class Failure(
            val message: String,
            val mode: Mode
        ) : Result()
    }

    /**
     * Processes a text prompt using the selected AI mode.
     */
    suspend fun process(
        prompt: String,
        offlineMode: Boolean,
        apiKey: String,
        model: String
    ): Result {

        val cleanPrompt = prompt.trim()

        if (cleanPrompt.isEmpty()) {
            return Result.Failure(
                message = "कोई संदेश प्राप्त नहीं हुआ।",
                mode = if (offlineMode) Mode.OFFLINE else Mode.ONLINE
            )
        }

        return if (offlineMode) {
            processOffline(cleanPrompt)
        } else {
            processOnline(
                prompt = cleanPrompt,
                apiKey = apiKey,
                model = model
            )
        }
    }

    /**
     * Handles offline AI processing.
     */
    private suspend fun processOffline(
        prompt: String
    ): Result {

        return try {
            val response = offlineCommandEngine
                .process(prompt)
                .trim()

            if (response.isEmpty()) {
                Result.Failure(
                    message = "इस कमांड के लिए ऑफलाइन सहायता उपलब्ध नहीं है।",
                    mode = Mode.OFFLINE
                )
            } else {
                Result.Success(
                    response = response,
                    mode = Mode.OFFLINE
                )
            }

        } catch (exception: Exception) {
            Result.Failure(
                message = exception.message
                    ?: "ऑफलाइन प्रोसेसिंग में समस्या आई।",
                mode = Mode.OFFLINE
            )
        }
    }

    /**
     * Handles online Gemini processing.
     */
    private suspend fun processOnline(
        prompt: String,
        apiKey: String,
        model: String
    ): Result {

        if (apiKey.isBlank()) {
            return Result.Failure(
                message = "Gemini API Key उपलब्ध नहीं है। Settings में API Key दर्ज करें।",
                mode = Mode.ONLINE
            )
        }

        return try {
            val response = geminiApiClient.generateResponse(
                prompt = prompt,
                apiKey = apiKey,
                model = model
            ).trim()

            if (response.isEmpty()) {
                Result.Failure(
                    message = "Gemini से खाली जवाब प्राप्त हुआ।",
                    mode = Mode.ONLINE
                )
            } else {
                Result.Success(
                    response = response,
                    mode = Mode.ONLINE
                )
            }

        } catch (exception: Exception) {
            Result.Failure(
                message = exception.message
                    ?: "Gemini से संपर्क नहीं हो सका।",
                mode = Mode.ONLINE
            )
        }
    }
}
