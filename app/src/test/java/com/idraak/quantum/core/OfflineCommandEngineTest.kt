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
    fun parsesContactCallingCommandsWithoutDialingDirectly() {
        val english = engine.process("call Ammi").action
        val urdu = engine.process("امی کو کال کرو").action

        assertTrue(english is CommandAction.DialContact)
        assertEquals("Ammi", (english as CommandAction.DialContact).contactName)
        assertTrue(urdu is CommandAction.DialContact)
        assertEquals("امی", (urdu as CommandAction.DialContact).contactName)
    }

    @Test
    fun parsesCompositeEnglishYoutubeSearch() {
        val action = engine.process("open youtube and search for quantum computing").action
        assertTrue(action is CommandAction.OpenUrl)
        assertTrue((action as CommandAction.OpenUrl).url.contains("quantum+computing"))
    }

    @Test
    fun parsesCompositeRomanUrduYoutubeSearch() {
        val action = engine.process("youtube kholo aur cricket highlights search karo").action
        assertTrue(action is CommandAction.OpenUrl)
        assertTrue((action as CommandAction.OpenUrl).url.contains("cricket+highlights"))
    }

    @Test
    fun parsesCompositeUrduYoutubeSearch() {
        val action = engine.process("یوٹیوب کھولو اور ٹیک خبریں تلاش کرو").action
        assertTrue(action is CommandAction.OpenUrl)
        assertTrue((action as CommandAction.OpenUrl).url.contains("youtube.com/results"))
        assertTrue((action as CommandAction.OpenUrl).url.contains("%D9%") || (action as CommandAction.OpenUrl).url.contains("+"))
    }

    @Test
    fun parsesRomanUrduGoogleSearch() {
        val action = engine.process("google par latest android news search karo").action
        assertTrue(action is CommandAction.OpenUrl)
        assertTrue((action as CommandAction.OpenUrl).url.contains("latest+android+news"))
    }

    @Test
    fun parsesRomanUrduSearchWithQueryFirst() {
        val action = engine.process("weather tomorrow internet par dhundo").action
        assertTrue(action is CommandAction.OpenUrl)
        assertTrue((action as CommandAction.OpenUrl).url.contains("weather+tomorrow"))
    }

    @Test
    fun parsesUrduGoogleSearch() {
        val action = engine.process("گوگل پر نئی ٹیکنالوجی تلاش کرو").action
        assertTrue(action is CommandAction.OpenUrl)
        assertTrue((action as CommandAction.OpenUrl).url.contains("google.com/search"))
    }

    @Test
    fun parsesEnglishTargetedAppShare() {
        val action = engine.process("share hello from Idraak on whatsapp").action
        assertTrue(action is CommandAction.ShareTextToApp)
        assertEquals("hello from Idraak", (action as CommandAction.ShareTextToApp).text)
        assertEquals("whatsapp", action.appName)
    }

    @Test
    fun parsesUrduTargetedAppShare() {
        val action = engine.process("میرا پیغام whatsapp پر بھیجو").action
        assertTrue(action is CommandAction.ShareTextToApp)
        assertEquals("میرا پیغام", (action as CommandAction.ShareTextToApp).text)
        assertEquals("whatsapp", action.appName)
    }

    @Test
    fun parsesAppFirstUrduTargetedShare() {
        val action = engine.process("whatsapp پر میرا پیغام بھیجو").action
        assertTrue(action is CommandAction.ShareTextToApp)
        assertEquals("میرا پیغام", (action as CommandAction.ShareTextToApp).text)
        assertEquals("whatsapp", action.appName)
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
