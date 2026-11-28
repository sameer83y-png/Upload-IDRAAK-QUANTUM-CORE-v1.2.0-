package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryInputPolicyTest {
    @Test
    fun acceptsNonEmptyContentWithinLimit() {
        assertTrue(MemoryInputPolicy.isValidContentLength(1))
        assertTrue(MemoryInputPolicy.isValidContentLength(2000))
    }

    @Test
    fun rejectsEmptyAndOversizedContent() {
        assertFalse(MemoryInputPolicy.isValidContentLength(0))
        assertFalse(MemoryInputPolicy.isValidContentLength(2001))
        assertFalse(MemoryInputPolicy.isValidContentLength(Int.MAX_VALUE))
    }

    @Test
    fun keepsMemoryLimitAtTwoThousandCharacters() {
        assertTrue(MemoryInputPolicy.MAX_CONTENT_LENGTH == 2000)
    }
}
