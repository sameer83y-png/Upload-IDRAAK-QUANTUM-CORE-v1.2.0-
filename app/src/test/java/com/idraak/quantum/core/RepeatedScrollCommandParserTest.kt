package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatedScrollCommandParserTest {
    @Test fun parsesEnglishScrollCounts() {
        assertEquals(RepeatedScrollCommand(true, 3), RepeatedScrollCommandParser.parse("scroll down 3 times"))
        assertEquals(RepeatedScrollCommand(false, 2), RepeatedScrollCommandParser.parse("swipe up two times"))
        assertEquals(RepeatedScrollCommand(true, 1), RepeatedScrollCommandParser.parse("scroll down"))
    }

    @Test fun parsesUrduAndLocalizedDigits() {
        assertEquals(RepeatedScrollCommand(true, 3), RepeatedScrollCommandParser.parse("تین بار نیچے اسکرول کرو"))
        assertEquals(RepeatedScrollCommand(false, 2), RepeatedScrollCommandParser.parse("۲ بار اوپر سوائپ کریں"))
        assertEquals(RepeatedScrollCommand(true, 5), RepeatedScrollCommandParser.parse("پانچ بار نیچے اسکرول کرو"))
    }

    @Test fun rejectsUnsafeOrUnclearCounts() {
        assertNull(RepeatedScrollCommandParser.parse("scroll down 99 times"))
        assertNull(RepeatedScrollCommandParser.parse("نیچے اسکرول کرو"))
        assertNull(RepeatedScrollCommandParser.parse("scroll sideways 2 times"))
    }
}
