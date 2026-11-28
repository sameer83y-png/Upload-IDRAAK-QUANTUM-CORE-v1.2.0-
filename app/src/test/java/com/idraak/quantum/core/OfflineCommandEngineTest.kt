package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCommandEngineTest {
    private val engine = OfflineCommandEngine()

    @Test
    fun greetingIsAHandledOfflineReplyRatherThanAnUnknownCommand() {
        val result = engine.process("السلام علیکم")
        assertEquals(CommandAction.None, result.action)
        assertEquals("وعلیکم السلام۔ ادراک حاضر ہے۔", result.message)
        assertTrue(!ActionRecoveryPolicy.isUnknownCommand(result.message))
    }

    @Test
    fun greetingPrefixDoesNotSwallowAnUrduYoutubeCommand() {
        val result = engine.process("السلام علیکم، یوٹیوب پر آج کی خبریں تلاش کرو")
        assertTrue(result.action is CommandAction.OpenUrl)
        assertTrue((result.action as CommandAction.OpenUrl).url.contains("youtube.com/results"))
    }

    @Test
    fun greetingPrefixDoesNotSwallowAnEnglishYoutubeCommand() {
        val result = engine.process("Hello, open youtube and search for quantum computing")
        assertTrue(result.action is CommandAction.OpenUrl)
        assertTrue((result.action as CommandAction.OpenUrl).url.contains("quantum+computing"))
    }

    @Test
    fun genuinelyUnsupportedCommandRemainsEligibleForFallback() {
        val result = engine.process("do something impossible")
        assertEquals(CommandAction.None, result.action)
        assertTrue(ActionRecoveryPolicy.isUnknownCommand(result.message))
    }

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
        val url = (action as CommandAction.OpenUrl).url
        assertTrue(url.contains("youtube.com/results"))
        assertTrue(!url.contains("%D8%AA%D9%84%D8%A7%D8%B4"))
    }

    @Test
    fun parsesContactCallingCommandsWithoutDialingDirectly() {
        val english = engine.process("call Ammi").action
        val urdu = engine.process("امی کو کال کرو").action
        val hindi = engine.process("मम्मी को कॉल करो").action

        assertTrue(english is CommandAction.DialContact)
        assertEquals("Ammi", (english as CommandAction.DialContact).contactName)
        assertTrue(urdu is CommandAction.DialContact)
        assertEquals("امی", (urdu as CommandAction.DialContact).contactName)
        assertTrue(hindi is CommandAction.DialContact)
        assertEquals("मम्मी", (hindi as CommandAction.DialContact).contactName)
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
    fun parsesTargetedWebLinkForConnectedApp() {
        val english = engine.process("open https://example.com in Chrome").action
        val urdu = engine.process("یہ لنک https://example.com Chrome میں کھولو").action

        assertTrue(english is CommandAction.OpenUrlInApp)
        assertEquals("https://example.com", (english as CommandAction.OpenUrlInApp).url)
        assertEquals("Chrome", english.appName)
        assertTrue(urdu is CommandAction.OpenUrlInApp)
        assertEquals("https://example.com", (urdu as CommandAction.OpenUrlInApp).url)
        assertEquals("Chrome", urdu.appName)
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
    fun parsesTargetedShareToAnyNamedApp() {
        val english = engine.process("send hello from Idraak to Signal").action
        val romanUrdu = engine.process("signal par mera paigham bhejo").action
        val urduMessageFirst = engine.process("میرا پیغام signal پر بھیجو").action
        val urduAppFirst = engine.process("signal پر میرا پیغام بھیجو").action

        assertTrue(english is CommandAction.ShareTextToApp)
        assertEquals("Signal", (english as CommandAction.ShareTextToApp).appName)
        assertEquals("hello from Idraak", english.text)
        assertTrue(romanUrdu is CommandAction.ShareTextToApp)
        assertEquals("signal", (romanUrdu as CommandAction.ShareTextToApp).appName)
        assertTrue(urduMessageFirst is CommandAction.ShareTextToApp)
        assertEquals("میرا پیغام", (urduMessageFirst as CommandAction.ShareTextToApp).text)
        assertEquals("signal", urduMessageFirst.appName)
        assertTrue(urduAppFirst is CommandAction.ShareTextToApp)
        assertEquals("میرا پیغام", (urduAppFirst as CommandAction.ShareTextToApp).text)
        assertEquals("signal", urduAppFirst.appName)
    }

    @Test
    fun parsesGenericShareCommand() {
        val action = engine.process("یہ پیغام بھیجو میں راستے میں ہوں").action
        assertTrue(action is CommandAction.ShareText)
        assertEquals("میں راستے میں ہوں", (action as CommandAction.ShareText).text)
    }


    @Test
    fun parsesPhotoAndVideoCameraCommandsSeparately() {
        assertEquals(CommandAction.OpenCamera, engine.process("camera kholo").action)
        assertEquals(CommandAction.OpenCamera, engine.process("کیمرہ کھولو").action)
        assertEquals(CommandAction.OpenVideoCamera, engine.process("record video").action)
        assertEquals(CommandAction.OpenVideoCamera, engine.process("ویڈیو ریکارڈ کرو").action)
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
    fun parsesReminderUsingUrduDigits() {
        val action = engine.process("مجھے ۱۰ منٹ بعد پانی پینا یاد دلانا").action
        assertTrue(action is CommandAction.ScheduleReminder)
        assertEquals("پانی پینا", (action as CommandAction.ScheduleReminder).message)
    }

    @Test
    fun parsesAlarmUsingDevanagariDigits() {
        val action = engine.process("अलार्म ७:३० लगाओ").action
        assertTrue(action is CommandAction.SetAlarm)
        assertEquals(7, (action as CommandAction.SetAlarm).hour)
        assertEquals(30, action.minute)
    }

    @Test
    fun parsesPhoneNumberUsingArabicIndicDigits() {
        val action = engine.process("call ٩١٢٣٤٥٦٧٨٩").action
        assertTrue(action is CommandAction.DialNumber)
        assertEquals("9123456789", (action as CommandAction.DialNumber).number)
    }

    @Test
    fun parsesAlarmTime() {
        val action = engine.process("الارم 19:30 لگاؤ").action
        assertTrue(action is CommandAction.SetAlarm)
        assertEquals(19, (action as CommandAction.SetAlarm).hour)
        assertEquals(30, action.minute)
    }

    @Test
    fun parsesReminderUsingDevanagariDigits() {
        val action = engine.process("मुझे १० मिनट बाद पानी पीना याद दिलाना").action
        assertTrue(action is CommandAction.ScheduleReminder)
        assertEquals("पानी पीना", (action as CommandAction.ScheduleReminder).message)
    }

    @Test
    fun parsesReminderUsingArabicIndicDigits() {
        val action = engine.process("مجھے ١٠ منٹ بعد پانی پینا یاد دلانا").action
        assertTrue(action is CommandAction.ScheduleReminder)
        assertEquals("پانی پینا", (action as CommandAction.ScheduleReminder).message)
    }

    @Test
    fun parsesAlarmUsingArabicIndicDigits() {
        val action = engine.process("الارم ١٩:٣٠ لگاؤ").action
        assertTrue(action is CommandAction.SetAlarm)
        assertEquals(19, (action as CommandAction.SetAlarm).hour)
        assertEquals(30, action.minute)
    }

    @Test
    fun parsesPhoneNumberUsingDevanagariDigits() {
        val action = engine.process("call ९१२३४५६७८९").action
        assertTrue(action is CommandAction.DialNumber)
        assertEquals("9123456789", (action as CommandAction.DialNumber).number)
    }


    @Test
    fun parsesHindiPhoneNumberUsingDevanagariDigits() {
        val action = engine.process("नंबर मिलाओ ९१२३४५६७८९").action
        assertTrue(action is CommandAction.DialNumber)
        assertEquals("9123456789", (action as CommandAction.DialNumber).number)
    }

    @Test
    fun parsesHindiReminderUsingHours() {
        val action = engine.process("मुझे २ घंटे बाद दवा लेने की याद दिलाना").action
        assertTrue(action is CommandAction.ScheduleReminder)
        assertEquals("दवा लेने की", (action as CommandAction.ScheduleReminder).message)
    }

}
