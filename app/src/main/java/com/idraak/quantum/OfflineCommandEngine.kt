
package com.idraak.quantum

/**
 * IDRAAK - Offline Command Engine
 *
 * Responsibility:
 * Handles supported offline commands in Hindi, English,
 * Urdu, and Arabic.
 *
 * Does not communicate with Gemini API
 * or handle speech recognition.
 */
internal class OfflineCommandEngine {

    /**
     * Processes a command locally without internet.
     *
     * An empty response means that the requested
     * command is not implemented or supported yet.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun process(prompt: String): String {
        return ""
    }
}
