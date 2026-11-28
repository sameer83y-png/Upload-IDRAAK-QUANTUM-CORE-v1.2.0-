package com.idraak.quantum.core

import java.util.Locale

/** Recognizes explicit previous/next word deletion commands. */
object DeleteWordCommandParser {
    fun parse(raw: String): DeleteDirection? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?؟،,۔]+$"), "")
            .replace(Regex("\\s+"), " ")
        return when (text) {
            "delete previous word", "delete word before cursor", "backspace word",
            "پچھلا لفظ حذف کرو", "لفظ پیچھے سے مٹاؤ", "کرسر کے پیچھے والا لفظ حذف کرو",
            "पिछला शब्द हटाओ", "एक शब्द पीछे से मिटाओ" -> DeleteDirection.BACKWARD
            "delete next word", "delete word after cursor", "forward delete word",
            "اگلا لفظ حذف کرو", "لفظ آگے سے مٹاؤ", "کرسر کے آگے والا لفظ حذف کرو",
            "अगला शब्द हटाओ", "एक शब्द आगे से मिटाओ" -> DeleteDirection.FORWARD
            else -> null
        }
    }
}
