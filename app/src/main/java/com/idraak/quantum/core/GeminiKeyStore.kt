package com.idraak.quantum.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the user-provided Gemini key with an Android Keystore AES key. */
class GeminiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("idraak_secure_ai", Context.MODE_PRIVATE)

    fun save(apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isBlank()) {
            clear()
            return
        }
        require(clean.length <= MAX_API_KEY_LENGTH && clean.none(Char::isWhitespace)) {
            "Gemini API key must be 512 characters or fewer and contain no spaces."
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun read(): String? {
        val ivText = preferences.getString(KEY_IV, null) ?: return null
        val dataText = preferences.getString(KEY_DATA, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        preferences.edit().remove(KEY_IV).remove(KEY_DATA).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ALIAS = "idraak_gemini_api_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_IV = "gemini_key_iv"
        private const val KEY_DATA = "gemini_key_data"
        private const val MAX_API_KEY_LENGTH = 512
    }
}
