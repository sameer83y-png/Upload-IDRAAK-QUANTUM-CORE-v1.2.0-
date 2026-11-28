package com.idraak.quantum.core

import android.content.Context
import java.util.Locale

data class IdraakProcessingResult(
    val handled: Boolean,
    val response: String
)

/**
 * Shared offline command pipeline used by the foreground UI and wake-word service.
 * Memory is stored only on explicit remember/correct requests; learned rules are aliases
 * to commands already supported by OfflineCommandEngine.
 */
class IdraakCommandProcessor(context: Context) {
    private val appContext = context.applicationContext
    private val commandEngine = OfflineCommandEngine()
    private val memoryStore = LocalMemoryStore(appContext)
    private val historyStore = ConversationHistoryStore(appContext)
    private val errorStore = CommandErrorStore(appContext)
    private val learnedStore = LearnedCommandStore(appContext)
    private val codingEngine = CodeLearningEngine()
    private val actionExecutor = AndroidActionExecutor(appContext)

    fun process(rawCommand: String): IdraakProcessingResult {
        val command = rawCommand.trim()
        if (command.isBlank()) return IdraakProcessingResult(true, "براہ کرم اپنا حکم واضح بتائیں۔")

        val direct = handleAccessibility(command)
        if (direct != null) return completed(command, direct)

        val memory = handleMemory(command)
        if (memory != null) return completed(command, memory)

        val learning = handleLearning(command)
        if (learning != null) return completed(command, learning)

        val diagnostics = handleDiagnostics(command)
        if (diagnostics != null) return completed(command, diagnostics)

        val coding = codingEngine.handle(command)
        if (coding != null) return completed(command, coding)

        val effectiveCommand = learnedStore.resolve(command) ?: command
        val result = commandEngine.process(effectiveCommand)
        if (result.action == CommandAction.None) {
            errorStore.record(command, result.message)
            return IdraakProcessingResult(
                false,
                ActionRecoveryPolicy.responseForUnknownCommand(command, result.message)
            )
        }

        val execution = actionExecutor.execute(result.action)
        val failed = execution.contains("compatible") ||
            execution.contains("ایپ نہیں ملی") ||
            execution.contains("Android نے مطلوبہ اجازت") ||
            execution.contains("permission", ignoreCase = true) ||
            execution.contains("رابطے تلاش نہیں ہو سکے") ||
            execution.contains("رابطے تلاش کرنے کے لیے") ||
            execution.contains("رابطہ نہیں ملا") ||
            execution.contains("یہ کام مکمل نہیں ہو سکا")
        if (failed) errorStore.record(command, execution)
        val response = if (failed) {
            ActionRecoveryPolicy.responseForExecutionFailure(command, execution)
        } else {
            result.message + "\n" + execution
        }
        return completed(command, response)
    }

    fun recordExchange(command: String, response: String) {
        historyStore.add("user", command)
        historyStore.add("assistant", response)
    }

    fun close() {
        memoryStore.close()
        historyStore.close()
    }

    private fun completed(command: String, response: String): IdraakProcessingResult {
        recordExchange(command, response)
        return IdraakProcessingResult(true, response)
    }

    private fun handleDiagnostics(raw: String): String? {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "show command errors", "show error log", "غلطیوں کی تاریخ دکھاؤ", "کمانڈ کی غلطیاں دکھاؤ" -> {
                val entries = errorStore.recent(10)
                if (entries.isEmpty()) "ابھی کوئی کمانڈ خرابی محفوظ نہیں۔"
                else entries.joinToString("\n\n") {
                    "کمانڈ: ${it.command}\nخرابی: ${it.error}"
                }
            }
            "clear command errors", "clear error log", "غلطیوں کی تاریخ صاف کرو", "کمانڈ کی غلطیاں حذف کرو" -> {
                errorStore.clear()
                "محفوظ شدہ کمانڈ خرابیوں کی تاریخ صاف کر دی گئی۔"
            }
            else -> null
        }
    }

    private fun handleMemory(raw: String): String? {
        val command = raw.trim()
        val normalized = command.lowercase(Locale.ROOT)
        val categoryPrefixes = listOf(
            "remember preference:" to "preference",
            "remember project:" to "project",
            "remember person:" to "person",
            "remember fact:" to "fact",
            "ترجیح یاد رکھو:" to "preference",
            "منصوبہ یاد رکھو:" to "project",
            "شخص یاد رکھو:" to "person",
            "حقیقت یاد رکھو:" to "fact"
        )
        val categoryPrefix = categoryPrefixes.firstOrNull { normalized.startsWith(it.first) }
        if (categoryPrefix != null) {
            val fact = command.drop(categoryPrefix.first.length).trim()
            if (fact.isBlank()) return "کیا محفوظ کرنا ہے؟"
            return try {
                memoryStore.remember(fact, category = categoryPrefix.second, source = "user")
                "یہ بات ${categoryPrefix.second} کی قسم میں آف لائن میموری میں محفوظ کر دی گئی۔"
            } catch (error: Exception) {
                "میموری محفوظ نہیں ہو سکی: " + (error.localizedMessage ?: "storage error")
            }
        }

        val rememberPrefix = listOf(
            "remember that ", "remember ", "یاد رکھو ", "یاد رکھنا ",
            "یہ یاد رکھو ", "یہ بات یاد رکھو ", "याद रखो ", "याद रखना "
        ).firstOrNull { normalized.startsWith(it) }
        if (rememberPrefix != null) {
            val fact = command.drop(rememberPrefix.length).trim()
            if (fact.isBlank()) return "Please tell me what you want me to remember."
            return try {
                memoryStore.remember(fact)
                "یہ بات اس فون کی آف لائن میموری میں محفوظ کر دی گئی۔"
            } catch (error: Exception) {
                "میموری محفوظ نہیں ہو سکی: " + (error.localizedMessage ?: "storage error")
            }
        }

        if (normalized in listOf(
                "what do you remember", "show memory", "list memories", "मेरी यादें बताओ",
                "تمہیں کیا یاد ہے", "کیا یاد ہے", "میری یادیں بتاؤ", "میری یادداشت دکھاؤ"
            )) {
            val memories = memoryStore.all()
            return if (memories.isEmpty()) "آپ کی مقامی میموری خالی ہے۔"
            else memories.joinToString("\n\n") { "• [${it.category}] ${it.content}" }
        }

        val categoryViews = mapOf(
            "show preference memory" to "preference",
            "show project memory" to "project",
            "show people memory" to "person",
            "show fact memory" to "fact",
            "ترجیحات دکھاؤ" to "preference",
            "منصوبوں کی یادداشت دکھاؤ" to "project",
            "لوگوں کی یادداشت دکھاؤ" to "person",
            "محفوظ حقائق دکھاؤ" to "fact"
        )
        val requestedCategory = categoryViews[normalized]
        if (requestedCategory != null) {
            val memories = memoryStore.byCategory(requestedCategory)
            return if (memories.isEmpty()) "اس قسم کی کوئی میموری محفوظ نہیں۔"
            else memories.joinToString("\n\n") { "• [${it.category}] ${it.content}" }
        }

        val categorySearch = Regex(
            "^(?:search|find) (preference|project|person|fact) memory for (.+)$",
            RegexOption.IGNORE_CASE
        ).find(command)
        if (categorySearch != null) {
            val category = categorySearch.groupValues[1].lowercase(Locale.ROOT)
            val query = categorySearch.groupValues[2].trim()
            val matches = memoryStore.byCategory(category).filter {
                it.content.contains(query, ignoreCase = true)
            }
            return if (matches.isEmpty()) "اس قسم میں اس عبارت سے ملتی میموری نہیں ملی۔"
            else matches.joinToString("\n\n") { "• ${it.content}" }
        }

        val searchPrefix = listOf(
            "search memory for ", "find memory ", "یادداشت میں تلاش کرو ", "یادوں میں تلاش کرو "
        ).firstOrNull { normalized.startsWith(it) }
        if (searchPrefix != null) {
            val query = command.drop(searchPrefix.length).trim()
            if (query.isBlank()) return "کیا تلاش کرنا ہے؟"
            val matches = memoryStore.search(query)
            return if (matches.isEmpty()) "اس موضوع پر کوئی محفوظ میموری نہیں ملی۔"
            else matches.joinToString("\n\n") { "• ${it.content}" }
        }

        val forgetPrefix = listOf(
            "forget memory ", "forget that ", "یہ بات بھول جاؤ ",
            "یادداشت سے حذف کرو ", "یہ یادداشت بھول جاؤ "
        ).firstOrNull { normalized.startsWith(it) }
        if (forgetPrefix != null) {
            val exactText = command.drop(forgetPrefix.length).trim()
            if (exactText.isBlank()) return "کون سی محفوظ بات حذف کرنی ہے؟"
            val deleted = memoryStore.forgetMatching(exactText)
            return if (deleted > 0) "منتخب میموری فون سے حذف کر دی گئی۔"
            else "اس عبارت سے ملتی ہوئی محفوظ میموری نہیں ملی۔"
        }

        if (normalized.startsWith("correct memory:") || normalized.startsWith("یادداشت درست کرو:")) {
            val correction = command.substringAfter(":", "").split("->", limit = 2)
            if (correction.size != 2) return "اس طرح لکھیں: correct memory: old text -> corrected text"
            return try {
                val changed = memoryStore.correct(correction[0], correction[1])
                if (changed) "میموری درست کر دی گئی اور correction history میں درج ہو گئی۔"
                else "درستی history میں محفوظ ہوئی، لیکن پرانی عبارت سے عین مطابق میموری نہیں ملی۔"
            } catch (error: Exception) {
                "درستی محفوظ نہیں ہو سکی: " + (error.localizedMessage ?: "storage error")
            }
        }

        if (normalized == "show correction history" || normalized == "درستیوں کی تاریخ دکھاؤ") {
            val corrections = memoryStore.correctionHistory()
            return if (corrections.isEmpty()) "ابھی کوئی درستی محفوظ نہیں۔"
            else corrections.joinToString("\n\n") { "• ${it.previousValue} → ${it.correctedValue}" }
        }
        return null
    }

    private fun handleLearning(raw: String): String? {
        val command = raw.trim()
        val normalized = command.lowercase(Locale.ROOT)
        val prefixes = listOf(
            "teach command:", "learn command:", "سیکھو:", "کمانڈ سکھاؤ:", "یہ کمانڈ یاد رکھو:"
        )
        val prefix = prefixes.firstOrNull { normalized.startsWith(it) }
        if (prefix != null) {
            val parts = command.drop(prefix.length).split("->", limit = 2)
            if (parts.size != 2) return "اس طرح سکھائیں: teach command: youtube chalao -> youtube kholo"
            val phrase = parts[0].trim()
            val target = parts[1].trim()
            if (phrase.isBlank() || target.isBlank()) return "نیا جملہ اور اس کی درست کمانڈ دونوں بتائیں۔"
            if (commandEngine.process(target).action == CommandAction.None) {
                return "یہ ہدف ابھی آف لائن انجن میں دستیاب نہیں۔ پہلے کوئی supported command دیں۔"
            }
            return try {
                learnedStore.remember(phrase, target)
                "یہ جملہ آف لائن کمانڈ کے طور پر سیکھ لیا: “$phrase”"
            } catch (error: Exception) {
                "کمانڈ محفوظ نہیں ہو سکی: " + (error.localizedMessage ?: "storage error")
            }
        }

        if (normalized in listOf(
                "show learned commands", "سیکھی ہوئی کمانڈز دکھاؤ", "میرے سیکھے ہوئے کمانڈ دکھاؤ"
            )) {
            val learned = learnedStore.all()
            return if (learned.isEmpty()) "ابھی کوئی نئی کمانڈ نہیں سکھائی گئی۔"
            else learned.entries.joinToString("\n") { "• “${it.key}” → “${it.value}”" }
        }

        val removePrefix = listOf("forget learned command:", "سیکھی کمانڈ بھول جاؤ:")
            .firstOrNull { normalized.startsWith(it) }
        if (removePrefix != null) {
            val phrase = command.drop(removePrefix.length).trim()
            return if (learnedStore.clear(phrase)) "سیکھا ہوا کمانڈ رول ہٹا دیا گیا۔"
            else "اس جملے کا کوئی محفوظ شدہ رول نہیں ملا۔"
        }
        return null
    }

    private fun handleAccessibility(raw: String): String? {
        val command = raw.trim()
        val lower = command.lowercase(Locale.ROOT)
        if (lower in listOf(
                "read screen", "what is on screen", "screen text", "اسکرین پڑھو",
                "اسکرین پر کیا ہے", "اسکرین پر کیا لکھا ہے", "جو اسکرین پر ہے پڑھو"
            )) {
            if (!IdraakAccessibilityService.isConnected()) return "اسکرین پڑھنے کے لیے Idraak Accessibility فعال کریں۔"
            val visible = IdraakAccessibilityService.readScreenText()
            return if (visible.isBlank()) "اسکرین پر پڑھنے کے قابل تحریر نہیں ملی۔"
            else "اسکرین پر نظر آنے والی تحریر یہ ہے:\n$visible"
        }

        val clickTarget = when {
            lower.matches(Regex("^(tap|click)( on)? .+")) ->
                command.replaceFirst(Regex("^(tap|click)( on)?\\s+"), "").trim()
            lower.matches(Regex("^.+(ٹیپ کرو|کلک کرو)$")) ->
                command.replace(Regex("(ٹیپ کرو|کلک کرو)$"), "")
                    .replace(Regex("^(اسکرین پر|screen)\\s*"), "")
                    .replace(Regex("\\s+کو$"), "").trim()
            else -> ""
        }
        if (clickTarget.isNotBlank()) {
            if (!IdraakAccessibilityService.isConnected()) return "اسکرین کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.clickText(clickTarget)) "اسکرین پر “$clickTarget” ٹیپ کر دیا۔"
            else "اسکرین پر “$clickTarget” نام کا قابلِ رسائی کنٹرول نہیں ملا۔"
        }

        val longPressTarget = when {
            lower.matches(Regex("^(long press|press and hold)( on)? .+")) ->
                command.replaceFirst(Regex("^(long press|press and hold)( on)?\\s+"), "").trim()
            lower.matches(Regex("^.+(دبا کر پکڑو|دیر تک دباؤ)$")) ->
                command.replace(Regex("(دبا کر پکڑو|دیر تک دباؤ)$"), "").trim()
            else -> ""
        }
        if (longPressTarget.isNotBlank()) {
            if (!IdraakAccessibilityService.isConnected()) return "اسکرین کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.longPressText(longPressTarget)) "“$longPressTarget” کو دیر تک دبایا۔"
            else "اسکرین پر اس نام کا قابلِ رسائی کنٹرول نہیں ملا۔"
        }

        if (lower in listOf("home", "go home", "ہوم پر جاؤ", "ہوم کھولو", "گھر واپس جاؤ")) {
            if (!IdraakAccessibilityService.isConnected()) return "Home کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.goHome()) "Home screen کھول دی۔" else "Home screen ایکشن دستیاب نہیں۔"
        }
        if (lower in listOf("back", "go back", "واپس جاؤ", "پیچھے جاؤ", "واپس")) {
            if (!IdraakAccessibilityService.isConnected()) return "Back کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.goBack()) "واپس چلا گیا۔" else "Back ایکشن دستیاب نہیں۔"
        }
        if (lower in listOf("show notifications", "open notifications", "نوٹیفکیشن دکھاؤ", "نوٹیفکیشن کھولو")) {
            if (!IdraakAccessibilityService.isConnected()) return "Notifications کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.showNotifications()) "Notifications panel کھول دیا۔"
            else "Notifications panel نہیں کھل سکا۔"
        }
        if (lower in listOf("quick settings", "open quick settings", "فوری سیٹنگز کھولو", "کوئک سیٹنگز کھولو")) {
            if (!IdraakAccessibilityService.isConnected()) return "Quick Settings کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.showQuickSettings()) "Quick Settings کھول دی۔"
            else "Quick Settings panel نہیں کھل سکا۔"
        }
        val down = lower in listOf("scroll down", "نیچے اسکرول کرو", "نیچے کرو", "swipe up", "اوپر سوائپ کرو")
        val up = lower in listOf("scroll up", "اوپر اسکرول کرو", "اوپر کرو", "swipe down", "نیچے سوائپ کرو")
        if (down || up) {
            if (!IdraakAccessibilityService.isConnected()) return "اسکرول کے لیے Idraak Accessibility فعال کریں۔"
            val moved = if (down) {
                IdraakAccessibilityService.scrollForward() || IdraakAccessibilityService.swipeUp()
            } else {
                IdraakAccessibilityService.scrollBackward() || IdraakAccessibilityService.swipeDown()
            }
            return if (moved) "اسکرین پر اسکرول ایکشن بھیج دیا۔" else "اس اسکرین پر اسکرول نہیں ہو سکا۔"
        }
        return null
    }
}
