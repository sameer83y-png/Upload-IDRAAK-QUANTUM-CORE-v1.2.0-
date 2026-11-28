package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPermissionRecoveryTest {
    @Test
    fun olderAndroidVersionsDoNotRequireNotificationRuntimePermission() {
        assertTrue(ReminderPermissionRecovery.shouldRestore(32, false))
    }

    @Test
    fun android13AndLaterRequireNotificationPermission() {
        assertFalse(ReminderPermissionRecovery.shouldRestore(33, false))
        assertFalse(ReminderPermissionRecovery.shouldRestore(35, false))
    }

    @Test
    fun grantedNotificationPermissionAllowsReminderRecovery() {
        assertTrue(ReminderPermissionRecovery.shouldRestore(33, true))
        assertTrue(ReminderPermissionRecovery.shouldRestore(35, true))
    }
}
