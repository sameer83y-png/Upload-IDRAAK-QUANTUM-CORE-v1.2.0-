package com.idraak.quantum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplaceSelectedTextCommandParserTest {
    @Test fun extractsReplacementInEnglishUrduAndHindi() {
        assertEquals("hello world", ReplaceSelectedTextCommandParser.parse("replace selected text with hello world"))
        assertEquals("نیا متن", ReplaceSelectedTextCommandParser.parse("منتخب متن کو نیا متن سے بدل دو"))
        assertEquals("नया पाठ", ReplaceSelectedTextCommandParser.parse("चुने हुए टेक्स्ट को नया पाठ से बदलो"))
    }

    @Test fun rejectsAmbiguousOrEmptyCommands() {
        assertNull(ReplaceSelectedTextCommandParser.parse("replace selected text"))
        assertNull(ReplaceSelectedTextCommandParser.parse("replace selected text with"))
        assertNull(ReplaceSelectedTextCommandParser.parse("replace text with hello"))
        assertNull(ReplaceSelectedTextCommandParser.parse("delete selected text"))
    }
}
