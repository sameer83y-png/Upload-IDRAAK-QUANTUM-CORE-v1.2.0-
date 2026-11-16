
package com.idraak.quantum

import android.content.Context
import android.content.SharedPreferences

/**
 * IDRAAK - Settings Manager
 *
 * Responsibility:
 * Stores and retrieves IDRAAK application settings.
 *
 * Does not handle settings UI, permissions,
 * voice recognition, or command execution.
 */
internal class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "idraak_settings"

        private const val KEY_LANGUAGE = "language"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"

        private const val DEFAULT_LANGUAGE = "auto"
        private const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getLanguage(): String {
        return preferences.getString(
            KEY_LANGUAGE,
            DEFAULT_LANGUAGE
        ) ?: DEFAULT_LANGUAGE
    }

    fun setLanguage(language: String) {
        preferences.edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    fun isWakeWordEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_WAKE_WORD_ENABLED,
            true
        )
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_WAKE_WORD_ENABLED, enabled)
            .apply()
    }

    fun isOfflineModeEnabled(): Boolean {
        return preferences.getBoolean(
            KEY_OFFLINE_MODE,
            false
        )
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_OFFLINE_MODE, enabled)
            .apply()
    }

    fun getGeminiApiKey(): String {
        return preferences.getString(
            KEY_GEMINI_API_KEY,
            ""
        ) ?: ""
    }

    fun setGeminiApiKey(apiKey: String) {
        preferences.edit()
            .putString(KEY_GEMINI_API_KEY, apiKey.trim())
            .apply()
    }

    fun getGeminiModel(): String {
        return preferences.getString(
            KEY_GEMINI_MODEL,
            DEFAULT_GEMINI_MODEL
        ) ?: DEFAULT_GEMINI_MODEL
    }

    fun setGeminiModel(model: String) {
        preferences.edit()
            .putString(KEY_GEMINI_MODEL, model.trim())
            .apply()
    }

    fun clearSettings() {
        preferences.edit()
            .clear()
            .apply()
    }
}
