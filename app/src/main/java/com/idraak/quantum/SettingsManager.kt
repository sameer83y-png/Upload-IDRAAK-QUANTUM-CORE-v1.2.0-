package com.idraak.quantum

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * IDRAAK Settings Manager
 *
 * Responsibilities:
 * - Persistent application settings.
 * - Secure Gemini API key storage using Android Keystore.
 * - Voice language, gender and speech-rate preferences.
 *
 * Conversation history and permanent memories must be managed
 * by their own dedicated managers, never by this class.
 */
class SettingsManager(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val lock = Any()

    var apiKey: String
        get() = synchronized(lock) {
            readApiKey()
        }
        set(value) {
            synchronized(lock) {
                val cleanValue = value.trim()

                if (cleanValue.isEmpty()) {
                    clearApiKeyInternal()
                } else {
                    storeEncryptedApiKey(cleanValue)
                }
            }
        }

    var geminiModel: String
        get() = preferences.getString(KEY_MODEL, DEFAULT_MODEL)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_MODEL
        set(value) {
            val cleanValue = value.trim().ifBlank { DEFAULT_MODEL }
            preferences.edit()
                .putString(KEY_MODEL, cleanValue)
                .apply()
        }

    var offlineMode: Boolean
        get() = preferences.getBoolean(KEY_OFFLINE, false)
        set(value) {
            preferences.edit()
                .putBoolean(KEY_OFFLINE, value)
                .apply()
        }

    var wakeWordEnabled: Boolean
        get() = preferences.getBoolean(KEY_WAKE_WORD, true)
        set(value) {
            preferences.edit()
                .putBoolean(KEY_WAKE_WORD, value)
                .apply()
        }

    /**
     * Supported stored values:
     * auto, hi-IN, en-US, en-IN, ur-PK, ar-SA
     *
     * Recognition support is determined separately by the
     * voice-recognition engine.
     */
    var voiceLanguage: String
        get() {
            val stored = preferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE)
                ?: DEFAULT_LANGUAGE

            return normalizeLanguage(stored)
        }
        set(value) {
            preferences.edit()
                .putString(KEY_LANGUAGE, normalizeLanguage(value))
                .apply()
        }

    var voiceGender: String
        get() {
            val stored = preferences.getString(KEY_GENDER, DEFAULT_GENDER)
                ?: DEFAULT_GENDER

            return normalizeGender(stored)
        }
        set(value) {
            preferences.edit()
                .putString(KEY_GENDER, normalizeGender(value))
                .apply()
        }

    var speechRate: Float
        get() {
            val stored = preferences.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE)

            return if (stored.isFinite()) {
                stored.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
            } else {
                DEFAULT_SPEECH_RATE
            }
        }
        set(value) {
            val safeValue = if (value.isFinite()) {
                value.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
            } else {
                DEFAULT_SPEECH_RATE
            }

            preferences.edit()
                .putFloat(KEY_SPEECH_RATE, safeValue)
                .apply()
        }

    /**
     * Controls whether ordinary conversation history should be retained.
     * Actual history storage belongs to ConversationHistoryManager.
     */
    var conversationHistoryEnabled: Boolean
        get() = preferences.getBoolean(KEY_HISTORY_ENABLED, true)
        set(value) {
            preferences.edit()
                .putBoolean(KEY_HISTORY_ENABLED, value)
                .apply()
        }

    /**
     * Controls whether explicit long-term memories may be retained.
     * Actual memory storage belongs to PermanentMemoryManager.
     */
    var permanentMemoryEnabled: Boolean
        get() = preferences.getBoolean(KEY_MEMORY_ENABLED, true)
        set(value) {
            preferences.edit()
                .putBoolean(KEY_MEMORY_ENABLED, value)
                .apply()
        }

    fun clearApiKey() {
        synchronized(lock) {
            clearApiKeyInternal()
        }
    }

    /**
     * Clears settings only.
     *
     * This method never accesses Conversation History or Permanent Memory.
     */
    fun clearAll() {
        synchronized(lock) {
            preferences.edit()
                .remove(KEY_API_LEGACY)
                .remove(KEY_API_ENCRYPTED)
                .remove(KEY_MODEL)
                .remove(KEY_OFFLINE)
                .remove(KEY_WAKE_WORD)
                .remove(KEY_LANGUAGE)
                .remove(KEY_GENDER)
                .remove(KEY_SPEECH_RATE)
                .remove(KEY_HISTORY_ENABLED)
                .remove(KEY_MEMORY_ENABLED)
                .apply()

            deleteEncryptionKey()
        }
    }

    private fun normalizeLanguage(value: String): String {
        return when (value.trim().lowercase()) {
            "auto", "automatic" -> "auto"

            "hi-in", "hi", "hindi" -> "hi-IN"

            "en-us", "english-us" -> "en-US"
            "en-in", "en", "english" -> "en-IN"

            "ur-pk", "ur", "urdu" -> "ur-PK"

            "ar-sa", "ar", "arabic" -> "ar-SA"

            else -> DEFAULT_LANGUAGE
        }
    }

    private fun normalizeGender(value: String): String {
        return when (value.trim().lowercase()) {
            "female", "f" -> "Female"
            "male", "m" -> "Male"
            else -> DEFAULT_GENDER
        }
    }

    private fun readApiKey(): String {
        val encryptedValue = preferences.getString(KEY_API_ENCRYPTED, null)

        if (!encryptedValue.isNullOrBlank()) {
            return try {
                decryptApiKey(encryptedValue)
            } catch (_: Exception) {
                ""
            }
        }

        // Migrate an API key saved by the older plaintext implementation.
        val legacyValue = preferences.getString(KEY_API_LEGACY, "")
            .orEmpty()
            .trim()

        if (legacyValue.isNotEmpty()) {
            try {
                storeEncryptedApiKey(legacyValue)
                preferences.edit()
                    .remove(KEY_API_LEGACY)
                    .apply()

                return legacyValue
            } catch (_: Exception) {
                // Do not return or expose an insecurely stored key.
                return ""
            }
        }

        return ""
    }

    private fun storeEncryptedApiKey(value: String) {
        val encrypted = encryptApiKey(value)

        preferences.edit()
            .putString(KEY_API_ENCRYPTED, encrypted)
            .remove(KEY_API_LEGACY)
            .apply()
    }

    private fun clearApiKeyInternal() {
        preferences.edit()
            .remove(KEY_API_LEGACY)
            .remove(KEY_API_ENCRYPTED)
            .apply()
    }

    private fun encryptApiKey(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateSecretKey()
        )

        val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        val combined = ByteBuffer.allocate(iv.size + encryptedBytes.size)
            .put(iv)
            .put(encryptedBytes)
            .array()

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptApiKey(encodedValue: String): String {
        val combined = Base64.decode(encodedValue, Base64.NO_WRAP)

        require(combined.size > GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES) {
            "Invalid encrypted API key data."
        }

        val buffer = ByteBuffer.wrap(combined)

        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(iv)

        val encryptedBytes = ByteArray(buffer.remaining())
        buffer.get(encryptedBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )

        return String(
            cipher.doFinal(encryptedBytes),
            Charsets.UTF_8
        )
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEY_ALIAS, null)

        if (existingKey is SecretKey) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build()

        keyGenerator.init(keySpec)

        return keyGenerator.generateKey()
    }

    private fun deleteEncryptionKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
            // Settings reset must not crash if keystore cleanup fails.
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "idraak_settings"

        // Existing key retained for migration compatibility.
        private const val KEY_API_LEGACY = "gemini_api_key"
        private const val KEY_API_ENCRYPTED = "gemini_api_key_encrypted"

        private const val KEY_MODEL = "gemini_model"
        private const val KEY_OFFLINE = "offline_mode"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        private const val KEY_LANGUAGE = "voice_language"
        private const val KEY_GENDER = "voice_gender"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_HISTORY_ENABLED = "conversation_history_enabled"
        private const val KEY_MEMORY_ENABLED = "permanent_memory_enabled"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "idraak_gemini_api_key_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_TAG_LENGTH_BYTES = 16

        private const val DEFAULT_LANGUAGE = "auto"
        private const val DEFAULT_GENDER = "Male"
        private const val DEFAULT_SPEECH_RATE = 1.0f

        private const val MIN_SPEECH_RATE = 0.5f
        private const val MAX_SPEECH_RATE = 2.0f

        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
