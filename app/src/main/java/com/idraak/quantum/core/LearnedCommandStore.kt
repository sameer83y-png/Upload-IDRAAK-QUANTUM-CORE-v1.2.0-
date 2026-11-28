package com.idraak.quantum.core

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Local, explicit phrase-to-command learning. It stores aliases, not executable code. */
class LearnedCommandStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("idraak_learned_commands", Context.MODE_PRIVATE)

    fun remember(phrase: String, supportedCommand: String) {
        val key = normalize(phrase)
        val target = supportedCommand.trim()
        require(key.length in 2..100) { "Command phrase must contain 2 to 100 characters." }
        require(target.length in 2..120) { "Target command must contain 2 to 120 characters." }
        val commands = readAll()
        commands[key] = target
        while (commands.size > MAX_RULES) commands.remove(commands.keys.first())
        preferences.edit().putString(KEY_RULES, JSONObject(commands as Map<*, *>).toString()).apply()
    }

    fun resolve(phrase: String): String? = readAll()[normalize(phrase)]

    fun all(): Map<String, String> = readAll()

    fun clear(phrase: String): Boolean {
        val key = normalize(phrase)
        val commands = readAll()
        if (commands.remove(key) == null) return false
        preferences.edit().putString(KEY_RULES, JSONObject(commands as Map<*, *>).toString()).apply()
        return true
    }

    private fun readAll(): LinkedHashMap<String, String> {
        val raw = preferences.getString(KEY_RULES, null) ?: return linkedMapOf()
        return try {
            val json = JSONObject(raw)
            val result = linkedMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key)
                if (key.isNotBlank() && value.isNotBlank()) result[key] = value
            }
            result
        } catch (_: Exception) {
            linkedMapOf()
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    companion object {
        private const val KEY_RULES = "phrase_rules"
        private const val MAX_RULES = 100
    }
}
