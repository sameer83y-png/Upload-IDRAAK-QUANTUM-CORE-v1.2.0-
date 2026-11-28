package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests to select the word at the current text cursor. */
object SelectWordCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "select word", "select current word", "highlight word",
            "لفظ منتخب کرو", "موجودہ لفظ منتخب کرو", "لفظ کو ہائی لائٹ کرو",
            "शब्द चुनो", "मौजूदा शब्द चुनो", "शब्द हाइलाइट करो"
        )
    }
}
