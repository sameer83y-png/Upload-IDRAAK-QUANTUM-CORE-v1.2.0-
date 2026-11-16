package com.idraak.quantum

/**
 * Gemini और network errors को user-readable संदेश में बदलता है।
 * यह API call नहीं करता।
 */
internal class GeminiErrorMapper {

    fun map(error: Throwable): String {
        val details = (
            error.message
                ?: error.cause?.message
                ?: ""
            ).trim()

        val normalized = details.lowercase()

        return when {
            normalized.contains("401") ||
                normalized.contains("unauthorized") ||
                normalized.contains("api key") &&
                normalized.contains("invalid") -> {
                "Gemini API key गलत या अमान्य है। IDRAAK Settings में API key जाँचें।"
            }

            normalized.contains("403") ||
                normalized.contains("permission denied") -> {
                "Gemini API ने access अस्वीकार किया। API key और उसकी permissions जाँचें।"
            }

            normalized.contains("429") ||
                normalized.contains("quota") ||
                normalized.contains("rate limit") -> {
                "Gemini की request limit पूरी हो गई है। कुछ समय बाद दोबारा कोशिश करें।"
            }

            normalized.contains("timeout") ||
                normalized.contains("timed out") -> {
                "Gemini से जवाब आने में बहुत समय लगा। कृपया दोबारा कोशिश करें।"
            }

            normalized.contains("unable to resolve host") ||
                normalized.contains("unknownhost") ||
                normalized.contains("network") ||
                normalized.contains("internet") -> {
                "इंटरनेट कनेक्शन उपलब्ध नहीं है। अपना नेटवर्क जाँचें।"
            }

            normalized.contains("empty response") -> {
                "Gemini से खाली जवाब मिला। कृपया सवाल दोबारा पूछें।"
            }

            details.isNotBlank() -> {
                "Gemini त्रुटि: ${details.take(MAX_ERROR_LENGTH)}"
            }

            else -> {
                "Gemini से जवाब प्राप्त नहीं हो सका। कृपया दोबारा कोशिश करें।"
            }
        }
    }

    companion object {
        private const val MAX_ERROR_LENGTH = 300
    }
}
