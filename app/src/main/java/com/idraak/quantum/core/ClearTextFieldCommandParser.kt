package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit requests to clear the currently focused text field. */
object ClearTextFieldCommandParser {
    fun matches(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return text in setOf(
            "clear text field", "clear current field", "clear this field",
            "clear input", "erase text field", "empty text field",
            "موجودہ ٹیکسٹ فیلڈ صاف کرو", "منتخب فیلڈ کا متن صاف کرو",
            "اس فیلڈ کا متن مٹا دو", "ٹیکسٹ فیلڈ خالی کرو",
            "موجودہ فیلڈ صاف کریں",
            "मौजूदा टेक्स्ट फ़ील्ड साफ करो", "इस फ़ील्ड का टेक्स्ट मिटा दो",
            "टेक्स्ट फ़ील्ड खाली करो"
        )
    }
}
