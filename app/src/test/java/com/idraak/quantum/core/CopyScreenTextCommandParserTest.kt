package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyScreenTextCommandParserTest {
    @Test fun recognizesEnglishUrduAndHindi() {
        assertTrue(CopyScreenTextCommandParser.matches("copy screen text"))
        assertTrue(CopyScreenTextCommandParser.matches("Copy visible text."))
        assertTrue(CopyScreenTextCommandParser.matches("اسکرین کا متن کاپی کرو"))
        assertTrue(CopyScreenTextCommandParser.matches("اسکرین پر لکھا ہوا کاپی کرو"))
        assertTrue(CopyScreenTextCommandParser.matches("स्क्रीन का टेक्स्ट कॉपी करो"))
    }

    @Test fun rejectsUnrelatedCommands() {
        assertFalse(CopyScreenTextCommandParser.matches("read screen"))
        assertFalse(CopyScreenTextCommandParser.matches("take screenshot"))
        assertFalse(CopyScreenTextCommandParser.matches("copy my password"))
    }
}
