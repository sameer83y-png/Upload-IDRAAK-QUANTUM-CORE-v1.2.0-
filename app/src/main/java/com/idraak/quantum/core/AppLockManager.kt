package com.idraak.quantum.core

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local Idraak app-lock verifier.
 * Only a random salt and PBKDF2 hash are persisted; the password is never stored.
 */
class AppLockManager(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("idraak_app_lock", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean =
        preferences.contains(KEY_SALT) && preferences.contains(KEY_HASH)

    fun setPassword(password: CharArray): Boolean {
        if (password.size !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) return false
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt)
        preferences.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
        return true
    }

    fun verify(password: CharArray): Boolean {
        // Reject oversized or malformed input before running the deliberately
        // expensive PBKDF2 derivation on the caller's thread.
        if (password.size !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) return false

        val saltText = preferences.getString(KEY_SALT, null) ?: return false
        val hashText = preferences.getString(KEY_HASH, null) ?: return false
        return try {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            if (salt.size != SALT_BYTES || expected.size != HASH_BITS / 8) {
                return false
            }
            val actual = derive(password, salt)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    fun disable() {
        preferences.edit().clear().apply()
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"
        private const val MIN_PASSWORD_LENGTH = 6
        private const val MAX_PASSWORD_LENGTH = 64
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 180_000
        private const val HASH_BITS = 256
    }
}
