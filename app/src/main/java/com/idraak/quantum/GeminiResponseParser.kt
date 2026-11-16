package com.idraak.quantum

/**
 * GeminiApiClient से प्राप्त text को validate और normalize करता है।
 *
 * Gemini के HTTP response और Python JSON envelope को दोबारा parse
 * नहीं करता; वह काम idraak_core.py और GeminiApiClient के पास रहता है।
 */
internal class GeminiResponseParser {

    fun parse(response: String?): String {
        val result = response
            ?.replace("\u0000", "")
            ?.trim()
            .orEmpty()

        if (result.isBlank()) {
            throw IllegalStateException(
                "Gemini returned an empty response"
            )
        }

        return result
    }
}
