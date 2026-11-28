package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderIdPolicyTest {
    @Test
    fun rejectsAnIdAlreadyUsedByPendingReminder() {
        val candidates = listOf(42, 43).iterator()
        val id = ReminderIdPolicy.nextId(listOf("42|12345|encoded")) { candidates.next() }
        assertEquals(43, id)
    }

    @Test
    fun rejectsZeroAndNegativeIds() {
        val candidates = listOf(0, -7, 9).iterator()
        val id = ReminderIdPolicy.nextId(emptyList()) { candidates.next() }
        assertEquals(9, id)
    }

    @Test
    fun ignoresMalformedStoredRecordsWhenCheckingIds() {
        val id = ReminderIdPolicy.nextId(listOf("not-a-record", "12|123|abc")) { 13 }
        assertEquals(13, id)
    }
}
