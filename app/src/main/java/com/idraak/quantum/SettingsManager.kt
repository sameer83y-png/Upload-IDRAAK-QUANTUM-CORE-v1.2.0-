package com.idraak.quantum

import android.content.Context

/**
 * IDRAAK की स्थायी प्राथमिकताएँ SharedPreferences में रखता है।
 * UI और voice logic इस class में नहीं रखे जाते।
 */
class SettingsManager(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = preferences.getString(KEY_API, "").orEmpty()
        set(value) {
            preferences.edit().putString(KEY_API, value.trim()).apply()
        }

    var geminiModel: String
        get() = preferences.getString(KEY_MODEL, DEFAULT_MODEL)
            ?: DEFAULT_MODEL
        set(value) {
            preferences.edit()
                .putString(KEY_MODEL, value.trim().ifBlank { DEFAULT_MODEL })
                .apply()
        }

    var offlineMode: Boolean
        get() = preferences.getBoolean(KEY_OFFLINE, false)
        set(value) {
            preferences.edit().putBoolean(KEY_OFFLINE, value).apply()
        }

    var wakeWordEnabled: Boolean
        get() = preferences.getBoolean(KEY_WAKE_WORD, true)
        set(value) {
            preferences.edit().putBoolean(KEY_WAKE_WORD, value).apply()
        }

    var voiceLanguage: String
        get() = preferences.getString(KEY_LANGUAGE, "auto") ?: "auto"
        set(value) {
            preferences.edit().putString(KEY_LANGUAGE, value).apply()
        }

    var speechRate: Float
        get() = preferences.getFloat(KEY_SPEECH_RATE, 1.0f)
        set(value) {
            preferences.edit()
                .putFloat(KEY_SPEECH_RATE, value.coerceIn(0.5f, 2.0f))
                .apply()
        }

    fun clearApiKey() {
        preferences.edit().remove(KEY_API).apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "idraak_settings"
        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_OFFLINE = "offline_mode"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        private const val KEY_LANGUAGE = "voice_language"
        private const val KEY_SPEECH_RATE = "speech_rate"

        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
