package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetentionPolicyTest {
    @Test
    fun doesNotPruneWithinLimit() {
        assertFalse(MemoryRetentionPolicy.shouldPrune(0))
        assertFalse(MemoryRetentionPolicy.shouldPrune(300))
    }

    @Test
    fun prunesOnlyAfterLimitIsExceeded() {
        assertTrue(MemoryRetentionPolicy.shouldPrune(301))
        assertTrue(MemoryRetentionPolicy.shouldPrune(1000))
    }

    @Test
    fun retentionLimitIsBounded() {
        assertTrue(MemoryRetentionPolicy.MAX_CORRECTIONS == 300)
    }
}
