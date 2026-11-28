package com.idraak.quantum.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CommandErrorEntry(
    val command: String,
    val error: String,
    val createdAt: Long
)

/**
 * Small, on-device diagnostic history for failed commands.
 * It stores failures only, never credentials, and can be reviewed or cleared by the user.
 */
class CommandErrorStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("idraak_command_errors", Context.MODE_PRIVATE)

    @Synchronized
    fun record(command: String, error: String) {
        val cleanCommand = command.trim().take(MAX_TEXT)
        val cleanError = error.trim().take(MAX_TEXT)
        if (cleanCommand.isBlank() || cleanError.isBlank()) return

        val entries = readArray()
        entries.put(
            JSONObject()
                .put("command", cleanCommand)
                .put("error", cleanError)
                .put("created_at", System.currentTimeMillis())
        )
        while (entries.length() > MAX_ENTRIES) entries.remove(0)
        preferences.edit().putString(KEY_ENTRIES, entries.toString()).apply()
    }

    @Synchronized
    fun recent(limit: Int = 10): List<CommandErrorEntry> {
        val entries = readArray()
        val result = mutableListOf<CommandErrorEntry>()
        val start = (entries.length() - limit.coerceIn(1, MAX_ENTRIES)).coerceAtLeast(0)
        for (index in entries.length() - 1 downTo start) {
            val item = entries.optJSONObject(index) ?: continue
            result += CommandErrorEntry(
                command = item.optString("command"),
                error = item.optString("error"),
                createdAt = item.optLong("created_at")
            )
        }
        return result
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    private fun readArray(): JSONArray =
        try {
            JSONArray(preferences.getString(KEY_ENTRIES, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 100
        private const val MAX_TEXT = 1500
    }
}
