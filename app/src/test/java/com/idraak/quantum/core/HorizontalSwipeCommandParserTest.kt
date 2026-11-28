package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HorizontalSwipeCommandParserTest {
    @Test fun parsesEnglishAndUrduDirections() {
        assertEquals(true, HorizontalSwipeCommandParser.parse("swipe left"))
        assertEquals(false, HorizontalSwipeCommandParser.parse("drag right"))
        assertEquals(true, HorizontalSwipeCommandParser.parse("بائیں طرف سوائپ کرو"))
        assertEquals(false, HorizontalSwipeCommandParser.parse("دائیں سوائپ کریں"))
    }

    @Test fun parsesHindiAndRejectsUnclearCommands() {
        assertEquals(true, HorizontalSwipeCommandParser.parse("बाएं स्वाइप करो"))
        assertEquals(false, HorizontalSwipeCommandParser.parse("दाएं स्वाइप करो"))
        assertNull(HorizontalSwipeCommandParser.parse("swipe up"))
        assertNull(HorizontalSwipeCommandParser.parse("swipe"))
    }
}
