package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMemoryCredentialGuardTest {
    @Test
    fun detectsExplicitPasswordAndPinAssignments() {
        assertTrue(LocalMemoryStore.containsCredentialMaterial("my password is Example123"))
        assertTrue(LocalMemoryStore.containsCredentialMaterial("PIN: 4829"))
        assertTrue(LocalMemoryStore.containsCredentialMaterial("OTP = 123456"))
    }

    @Test
    fun detectsCommonApiAndSessionTokens() {
        assertTrue(LocalMemoryStore.containsCredentialMaterial("API key: sk-1234567890abcdef"))
        assertTrue(LocalMemoryStore.containsCredentialMaterial("token eyJabcdefghijklmnopqrstuvwxyz.abcdefghijk.abcdefghijk"))
    }

    @Test
    fun allowsOrdinaryNonSecretMemories() {
        assertFalse(LocalMemoryStore.containsCredentialMaterial("My preferred language is Urdu"))
        assertFalse(LocalMemoryStore.containsCredentialMaterial("I use a password manager"))
        assertFalse(LocalMemoryStore.containsCredentialMaterial("Idraak project uses offline memory"))
    }
}
