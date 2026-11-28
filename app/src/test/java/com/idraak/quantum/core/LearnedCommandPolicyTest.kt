package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedCommandPolicyTest {
    @Test
    fun acceptsNormalSupportedAlias() {
        LearnedCommandPolicy.validate("youtube chalao", "youtube kholo")
        assertTrue(true)
    }

    @Test
    fun rejectsCredentialMaterialInPhrase() {
        val rejected = runCatching {
            LearnedCommandPolicy.validate("remember password: Example123", "open settings")
        }.isFailure
        assertTrue(rejected)
    }

    @Test
    fun rejectsCredentialMaterialInTarget() {
        val rejected = runCatching {
            LearnedCommandPolicy.validate("open my account", "API key: sk-1234567890abcdef")
        }.isFailure
        assertTrue(rejected)
    }

    @Test
    fun rejectsOutOfRangePhraseAndTargetLengths() {
        assertTrue(runCatching { LearnedCommandPolicy.validate("x", "open settings") }.isFailure)
        assertTrue(runCatching { LearnedCommandPolicy.validate("valid phrase", "x") }.isFailure)
    }
}
