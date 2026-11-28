package com.idraak.quantum.core

import java.util.Locale

/**
 * Converts vague offline-router outcomes and Android action failures into
 * useful next steps. It never reports an action as completed unless the
 * executor actually returned a success response.
 */
object ActionRecoveryPolicy {
    private const val UNKNOWN_COMMAND =
        "یہ حکم ابھی میرے آف لائن کمانڈز میں شامل نہیں۔ براہ کرم اسے دوسرے الفاظ میں بتائیں۔"

    fun isUnknownCommand(engineMessage: String): Boolean = engineMessage == UNKNOWN_COMMAND

    fun responseForUnknownCommand(rawCommand: String, engineMessage: String): String {
        if (engineMessage != UNKNOWN_COMMAND) return engineMessage

        val command = rawCommand.trim()
        val normalized = command.lowercase(Locale.ROOT)
        val suggestions = CommandSuggestionEngine.suggestionsFor(command)
        if (suggestions != null) return suggestions

        return when {
            normalized.matches(Regex("^(open|launch|start|کھولو|چلاؤ)\\s*$")) ->
                "کون سی ایپ یا چیز کھولنی ہے؟ اس کا نام بتا دیں۔"
            normalized.contains("تلاش") || normalized.contains("search") ||
                normalized.contains("سرچ") || normalized.contains("گوگل") ->
                "کیا تلاش کرنا ہے، اور کس ایپ میں؟ مثال کے طور پر یوٹیوب یا گوگل پر تلاش۔"
            normalized.contains("کال") || normalized.contains("فون کرو") ||
                normalized.startsWith("call ") ->
                "کس رابطے کو کال کرنی ہے؟ رابطے کا نام یا فون نمبر بتائیں۔"
            normalized.contains("ٹیپ") || normalized.contains("کلک") ||
                normalized.contains("tap") || normalized.contains("click") ->
                "اسکرین پر کس بٹن یا تحریر کو ٹیپ کرنا ہے؟ اس کا نام بتائیں۔"
            else ->
                "آپ کا حکم سمجھنے کی کوشش کر رہا ہوں۔ کس ایپ یا چیز پر عمل کرنا ہے؟ ذرا واضح الفاظ میں بتائیں، میں دستیاب طریقہ آزماؤں گا۔"
        }
    }

    fun responseForExecutionFailure(rawCommand: String, executorMessage: String): String {
        val message = executorMessage.lowercase(Locale.ROOT)
        return when {
            message.contains("compatible") || message.contains("ایپ نہیں ملی") ->
                "مطلوبہ ایپ فون میں نہیں ملی۔ اس کا درست نام بتائیں تاکہ دوبارہ تلاش کروں، یا پہلے اسے انسٹال کریں۔"
            message.contains("android نے مطلوبہ اجازت") ||
                message.contains("permission") || message.contains("securityexception") ->
                "یہ کام جاری رکھنے کے لیے Android کی اجازت درکار ہے۔ فون کی Settings میں Idraak کی App permissions کھول کر متعلقہ اجازت فعال کریں، پھر یہی حکم دوبارہ دیں۔"
            message.contains("یہ کام مکمل نہیں ہو سکا") ->
                "یہ کوشش مکمل نہیں ہوئی۔ میں نے اصل خرابی نوٹ کی ہے؛ ایک بار دوبارہ کوشش کریں یا حکم کو مختصر اور واضح بتائیں۔"
            else -> executorMessage
        }
    }
}
