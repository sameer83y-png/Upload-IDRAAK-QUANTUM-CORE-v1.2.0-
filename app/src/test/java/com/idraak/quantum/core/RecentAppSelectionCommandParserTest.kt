package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentAppSelectionCommandParserTest {
    @Test fun parsesEnglishUrduAndHindiTargets() {
        assertEquals("youtube", RecentAppSelectionCommandParser.parse("switch to YouTube"))
        assertEquals("chrome", RecentAppSelectionCommandParser.parse("switch app to Chrome"))
        assertEquals("واٹس ایپ", RecentAppSelectionCommandParser.parse("حالیہ ایپس سے واٹس ایپ"))
        assertEquals("یوٹیوب", RecentAppSelectionCommandParser.parse("حالیہ ایپس میں سے یوٹیوب"))
        assertEquals("क्रोम", RecentAppSelectionCommandParser.parse("ऐप पर वापस जाओ क्रोम"))
    }

    @Test fun rejectsMissingOrUnrelatedTargets() {
        assertNull(RecentAppSelectionCommandParser.parse("switch to"))
        assertNull(RecentAppSelectionCommandParser.parse("open settings"))
        assertNull(RecentAppSelectionCommandParser.parse("show recent apps"))
    }
}
