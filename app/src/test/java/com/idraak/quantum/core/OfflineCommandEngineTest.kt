package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCommandEngineTest {
    private val engine = OfflineCommandEngine()

    @Test
    fun parsesEnglishReminder() {
        val action = engine.process("remind me to drink water in 10 minutes").action
        assertTrue(action is CommandAction.ScheduleReminder)
        assertEquals("drink water", (action as CommandAction.ScheduleReminder).message)
    }

    @Test
    fun parsesUrduReminder() {
        val action = engine.process("مجھے 10 منٹ بعد پانی پینا یاد دلانا").action
        assertTrue(action is CommandAction.ScheduleReminder)
        assertEquals("پانی پینا", (action as CommandAction.ScheduleReminder).message)
    }

    @Test
    fun parsesUrduYoutubeSearch() {
        val action = engine.process("یوٹیوب پر آج کی خبریں تلاش کرو").action
        assertTrue(action is CommandAction.OpenUrl)
        assertTrue((action as CommandAction.OpenUrl).url.contains("youtube.com/results"))
    }

    @Test
    fun parsesGenericShareCommand() {
        val action = engine.process("یہ پیغام بھیجو میں راستے میں ہوں").action
        assertTrue(action is CommandAction.ShareText)
        assertEquals("میں راستے میں ہوں", (action as CommandAction.ShareText).text)
    }

    @Test
    fun parsesVolumeAndWifiCommands() {
        assertEquals(
            VolumeDirection.UP,
            (engine.process("آواز بڑھاؤ").action as CommandAction.AdjustVolume).direction
        )
        assertTrue(engine.process("وائی فائی سیٹنگز کھولو").action is CommandAction.OpenWifiSettings)
    }

    @Test
    fun parsesGenericEnglishAppLaunch() {
        val action = engine.process("open calculator").action
        assertTrue(action is CommandAction.OpenApp)
        assertEquals("calculator", (action as CommandAction.OpenApp).appName)
    }

    @Test
    fun parsesGenericUrduAppLaunch() {
        val action = engine.process("کیلکولیٹر کھولو").action
        assertTrue(action is CommandAction.OpenApp)
        assertEquals("کیلکولیٹر", (action as CommandAction.OpenApp).appName)
    }

    @Test
    fun parsesAlarmTime() {
        val action = engine.process("الارم 19:30 لگاؤ").action
        assertTrue(action is CommandAction.SetAlarm)
        assertEquals(19, (action as CommandAction.SetAlarm).hour)
        assertEquals(30, action.minute)
    }
}
