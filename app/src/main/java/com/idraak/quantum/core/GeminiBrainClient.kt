package com.idraak.quantum.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Gemini is a text fallback only; it cannot directly execute phone actions. */
class GeminiBrainClient(
    private val apiKey: String,
    private val model: String
) {
    fun reply(userText: String): String {
        require(apiKey.isNotBlank()) { "Gemini API key is not configured." }
        val safeModel = model.trim().ifBlank { DEFAULT_MODEL }
        require(safeModel.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Invalid Gemini model name." }

        val prompt = """
            You are Idraak, a helpful multilingual mobile assistant.
            Reply naturally in Urdu script when the user writes Urdu or Hindi; use the user's language otherwise.
            You are a text assistant only. You cannot see or control the phone in this chat request.
            Never claim that you opened an app, changed a setting, sent a message, or performed a device action.
            If the user asks for a phone action, give a short command phrase that Idraak's offline controls support,
            and clearly say if Android may require permission or confirmation.
            Keep answers concise and safe.
            User message: $userText
        """.trimIndent()

        val body = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )
        ).put("generationConfig", JSONObject().put("temperature", 0.4).put("maxOutputTokens", 700))

        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) {
                val detail = try {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                } catch (_: Exception) { null }
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Gemini request failed (HTTP $status).")
            }
            val candidates = JSONObject(responseText).optJSONArray("candidates")
                ?: throw IllegalStateException("Gemini returned no answer.")
            val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini returned an empty answer.")
            val answer = buildString {
                for (index in 0 until parts.length()) append(parts.optJSONObject(index)?.optString("text").orEmpty())
            }.trim()
            if (answer.isBlank()) throw IllegalStateException("Gemini returned an empty answer.")
            return answer.take(5000)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
