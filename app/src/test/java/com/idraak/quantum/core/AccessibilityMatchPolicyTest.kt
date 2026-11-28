package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityMatchPolicyTest {
    @Test
    fun exactMatchWinsOverPartialMatches() {
        val labels = listOf(
            listOf("Settings and privacy"),
            listOf("Settings")
        )
        assertEquals(1, AccessibilityMatchPolicy.findUniqueMatchIndex(labels, "settings"))
    }

    @Test
    fun uniquePartialMatchIsAccepted() {
        val labels = listOf(
            listOf("Open camera"),
            listOf("Cancel")
        )
        assertEquals(0, AccessibilityMatchPolicy.findUniqueMatchIndex(labels, "camera"))
    }

    @Test
    fun ambiguousPartialMatchesAreRejected() {
        val labels = listOf(
            listOf("Open settings"),
            listOf("Settings and privacy")
        )
        assertNull(AccessibilityMatchPolicy.findUniqueMatchIndex(labels, "settings"))
    }

    @Test
    fun duplicateExactMatchesAreRejected() {
        val labels = listOf(listOf("Allow"), listOf("Allow"))
        assertNull(AccessibilityMatchPolicy.findUniqueMatchIndex(labels, "allow"))
    }

    @Test
    fun blankQueryIsRejected() {
        assertNull(AccessibilityMatchPolicy.findUniqueMatchIndex(listOf(listOf("Allow")), "  "))
    }
}
