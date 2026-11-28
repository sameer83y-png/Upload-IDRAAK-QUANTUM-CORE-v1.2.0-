package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSeekCommandParserTest {
    @Test fun parsesUrduNumberWordsAndDirections() {
        assertEquals(300L, MediaSeekCommandParser.parseOffsetSeconds("ویڈیو پانچ منٹ آگے کرو"))
        assertEquals(-1L, MediaSeekCommandParser.parseOffsetSeconds("پیچھے ایک سیکنڈ"))
        assertEquals(2L, MediaSeekCommandParser.parseOffsetSeconds("ویڈیو دو سیکنڈ آگے کر دو"))
    }

    @Test fun parsesLocalizedDigitsAndEnglishCommands() {
        assertEquals(300L, MediaSeekCommandParser.parseOffsetSeconds("آگے ۵ منٹ"))
        assertEquals(-1L, MediaSeekCommandParser.parseOffsetSeconds("1 second backward"))
        assertEquals(120L, MediaSeekCommandParser.parseOffsetSeconds("video 2 minutes forward"))
    }

    @Test fun rejectsUnclearOrExcessiveRequests() {
        assertNull(MediaSeekCommandParser.parseOffsetSeconds("ویڈیو آگے کرو"))
        assertNull(MediaSeekCommandParser.parseOffsetSeconds("آگے 999999 منٹ"))
        assertNull(MediaSeekCommandParser.parseOffsetSeconds("پانچ منٹ"))
    }
}
