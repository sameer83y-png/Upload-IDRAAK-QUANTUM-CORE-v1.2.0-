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
class IdraakCommandProcessor(
    context: Context,
    private val onExecutionState: ((String, String) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val commandEngine = OfflineCommandEngine()
    private val memoryStore = LocalMemoryStore(appContext)
    private val historyStore = ConversationHistoryStore(appContext)
    private val errorStore = CommandErrorStore(appContext)
    private val learnedStore = LearnedCommandStore(appContext)
    private val codingEngine = CodeLearningEngine()
    private val actionExecutor = AndroidActionExecutor(appContext)
    private var pendingSensitiveClick: String? = null

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
            if (!ActionRecoveryPolicy.isUnknownCommand(result.message)) {
                // Greetings and other valid conversational replies are handled offline;
                // they must not fall through to the optional Gemini network path.
                return completed(command, result.message)
            }
            errorStore.record(command, result.message)
            return IdraakProcessingResult(
                false,
                ActionRecoveryPolicy.responseForUnknownCommand(command, result.message)
            )
        }

        onExecutionState?.invoke("executing", command)
        val execution = try {
            actionExecutor.execute(result.action)
        } catch (error: Exception) {
            onExecutionState?.invoke("error", command)
            val failure = "یہ کام مکمل نہیں ہو سکا: " +
                (error.localizedMessage ?: "نامعلوم خرابی")
            errorStore.record(command, failure)
            return completed(
                command,
                ActionRecoveryPolicy.responseForExecutionFailure(command, failure)
            )
        }
        val failed = ActionRecoveryPolicy.isExecutionFailure(execution)
        if (failed) {
            onExecutionState?.invoke("error", command)
            errorStore.record(command, execution)
        } else {
            onExecutionState?.invoke("completed", command)
        }
        val response = if (failed) {
            ActionRecoveryPolicy.responseForExecutionFailure(command, execution)
        } else {
            result.message + "\n" + execution
        }
        return completed(command, response)
    }

    fun recordExchange(command: String, response: String) {
        // Conversation history is optional; a storage error must not suppress
        // an otherwise successful command response or crash the voice callback.
        try {
            historyStore.add("user", command)
            historyStore.add("assistant", response)
        } catch (_: Exception) {
            // The command result remains usable even when history cannot be saved.
        }
    }

    fun close() {
        // Close each store independently so one cleanup failure cannot leak the other.
        try {
            memoryStore.close()
        } catch (_: Exception) {
            // Best-effort cleanup.
        }
        try {
            historyStore.close()
        } catch (_: Exception) {
            // Best-effort cleanup.
        }
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
            val matches = memoryStore.searchInCategory(category, query)
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

        val pendingClick = pendingSensitiveClick
        if (pendingClick != null) {
            if (lower in listOf("yes", "confirm", "do it", "ہاں", "جی ہاں", "تصدیق کرتا ہوں", "کر دو")) {
                pendingSensitiveClick = null
                if (!IdraakAccessibilityService.isConnected()) {
                    return "Accessibility ابھی فعال نہیں؛ پہلے اسے فعال کریں، پھر دوبارہ کہیں۔"
                }
                return if (IdraakAccessibilityService.clickText(pendingClick)) {
                    "ٹھیک ہے، “$pendingClick” پر عمل کر دیا۔"
                } else {
                    "یہ بٹن اس وقت نہیں ملا؛ اسکرین دیکھ کر دوبارہ کوشش کرتے ہیں۔"
                }
            }
            if (lower in listOf("no", "cancel", "نہیں", "منسوخ", "رہنے دو", "چھوڑ دو")) {
                pendingSensitiveClick = null
                return "ٹھیک ہے، یہ عمل منسوخ کر دیا۔"
            }
            return "اس عمل کی تصدیق درکار ہے۔ آگے بڑھنے کے لیے “ہاں” یا روکنے کے لیے “نہیں” کہیں۔"
        }

        if (SelectWordCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "لفظ منتخب کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.selectWordAtCursor()) {
                "کرسر کے پاس والا لفظ منتخب کر دیا۔"
            } else {
                "لفظ منتخب نہیں ہوا؛ کسی عام ٹیکسٹ فیلڈ میں لفظ کے اندر یا اس کے آخر میں کرسر رکھیں۔ پاس ورڈ فیلڈ محفوظ رہے گی۔"
            }
        }

        val cursorDirection = MoveCursorByCharacterCommandParser.parse(command)
        if (cursorDirection != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "کرسر منتقل کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.moveCursorByCharacter(cursorDirection == CursorStep.FORWARD)) {
                if (cursorDirection == CursorStep.FORWARD) "کرسر ایک حرف آگے کر دیا۔"
                else "کرسر ایک حرف پیچھے کر دیا۔"
            } else {
                "کرسر نہیں ہلا؛ عام ٹیکسٹ فیلڈ میں غیر منتخب کرسر رکھیں۔ پاس ورڈ فیلڈ محفوظ رہے گی۔"
            }
        }

        val wordDirection = MoveCursorByWordCommandParser.parse(command)
        if (wordDirection != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "لفظ کے درمیان کرسر منتقل کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.moveCursorByWord(wordDirection == WordCursorDirection.FORWARD)) {
                if (wordDirection == WordCursorDirection.FORWARD) "کرسر اگلے لفظ کے آخر تک منتقل کر دیا۔"
                else "کرسر پچھلے لفظ کے شروع تک منتقل کر دیا۔"
            } else {
                "کرسر لفظ کے اگلے/پچھلے کنارے تک نہیں جا سکا؛ عام ٹیکسٹ فیلڈ میں غیر منتخب کرسر رکھیں۔ پاس ورڈ فیلڈ محفوظ رہے گی۔"
            }
        }

        val deleteWordDirection = DeleteWordCommandParser.parse(command)
        if (deleteWordDirection != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "لفظ حذف کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.deleteWord(deleteWordDirection == DeleteDirection.FORWARD)) {
                if (deleteWordDirection == DeleteDirection.FORWARD) "کرسر کے آگے والا لفظ حذف کر دیا۔"
                else "کرسر کے پیچھے والا لفظ حذف کر دیا۔"
            } else {
                "لفظ حذف نہیں ہوا؛ عام ٹیکسٹ فیلڈ میں غیر منتخب کرسر رکھیں۔ پاس ورڈ فیلڈ اور منتخب متن محفوظ رہیں گے۔"
            }
        }

        val deleteDirection = DeleteCharacterCommandParser.parse(command)
        if (deleteDirection != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "حرف حذف کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.deleteCharacter(deleteDirection == DeleteDirection.FORWARD)) {
                if (deleteDirection == DeleteDirection.FORWARD) "کرسر کے آگے والا ایک حرف حذف کر دیا۔"
                else "کرسر کے پیچھے والا ایک حرف حذف کر دیا۔"
            } else {
                "حرف حذف نہیں ہوا؛ عام ٹیکسٹ فیلڈ میں غیر منتخب کرسر رکھیں۔ پاس ورڈ فیلڈ اور پہلے سے منتخب متن محفوظ رہیں گے۔"
            }
        }

        val selectionEdge = SelectToEdgeCommandParser.parse(command)
        if (selectionEdge != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "متن منتخب کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            val selected = if (selectionEdge == SelectionEdge.START) {
                IdraakAccessibilityService.selectFromCursorToStart()
            } else {
                IdraakAccessibilityService.selectFromCursorToEnd()
            }
            return if (selected) {
                if (selectionEdge == SelectionEdge.START) {
                    "کرسر سے متن کے شروع تک عبارت منتخب کر دی۔"
                } else {
                    "کرسر سے متن کے آخر تک عبارت منتخب کر دی۔"
                }
            } else {
                "متن منتخب نہیں ہوا؛ ایک عام ٹیکسٹ فیلڈ میں کرسر رکھیں، پہلے سے کوئی عبارت منتخب نہ ہو۔ پاس ورڈ فیلڈ محفوظ رہے گی۔"
            }
        }

        val cursorPosition = MoveCursorCommandParser.parse(command)
        if (cursorPosition != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "کرسر منتقل کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            val moved = if (cursorPosition == CursorPosition.START) {
                IdraakAccessibilityService.moveCursorToStart()
            } else {
                IdraakAccessibilityService.moveCursorToEnd()
            }
            return if (moved) {
                if (cursorPosition == CursorPosition.START) {
                    "کرسر ٹیکسٹ فیلڈ کے شروع میں منتقل کر دیا۔"
                } else {
                    "کرسر ٹیکسٹ فیلڈ کے آخر میں منتقل کر دیا۔"
                }
            } else {
                "کرسر منتقل نہیں ہوا؛ ایک عام، منتخب اور قابلِ تدوین ٹیکسٹ فیلڈ پر فوکس کریں۔ پاس ورڈ فیلڈ محفوظ رہے گی۔"
            }
        }

        val searchQuery = when {
            lower.startsWith("search for ") -> command.substringAfter("search for ").trim()
            lower.startsWith("search ") && !lower.startsWith("search memory ") &&
                !lower.startsWith("search preference memory ") &&
                !lower.startsWith("search project memory ") &&
                !lower.startsWith("search person memory ") &&
                !lower.startsWith("search fact memory ") -> command.substringAfter("search ").trim()
            lower.startsWith("تلاش کرو ") -> command.substringAfter("تلاش کرو ").trim()
            lower.startsWith("تلاش کریں ") -> command.substringAfter("تلاش کریں ").trim()
            lower.startsWith("یہ تلاش کرو ") -> command.substringAfter("یہ تلاش کرو ").trim()
            else -> ""
        }
        if (searchQuery.isNotBlank()) {
            if (!IdraakAccessibilityService.isConnected()) return "ایپ میں تلاش کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.searchCurrentApp(searchQuery)) {
                "موجودہ ایپ میں “$searchQuery” تلاش کے لیے درج کر دیا۔"
            } else {
                "اس اسکرین پر واضح Search فیلڈ نہیں ملی؛ پہلے سرچ باکس کھولیں یا اسے منتخب کریں۔"
            }
        }

        val fillMatch = Regex(
            "^(?:type|enter|fill) (.+) in (.+)$",
            RegexOption.IGNORE_CASE
        ).find(command)
        if (fillMatch != null) {
            val value = fillMatch.groupValues[1].trim()
            val field = fillMatch.groupValues[2].trim()
            if (!IdraakAccessibilityService.isConnected()) return "ٹیکسٹ درج کرنے کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.setText(field, value)) {
                "“$field” میں متن درج کر دیا۔"
            } else {
                "“$field” نام کا واضح editable فیلڈ نہیں ملا۔"
            }
        }

        val mediaAction = when (lower) {
            "play", "play video", "resume", "resume video",
            "ویڈیو چلاؤ", "ویڈیو چلائیں", "پلے کرو", "دوبارہ چلاؤ" -> "play"
            "pause", "pause video", "pause playback",
            "ویڈیو روک دو", "ویڈیو روکیں", "پاز کرو" -> "pause"
            "next", "next video", "اگلی ویڈیو", "اگلا ویڈیو", "اگلا" -> "next"
            "previous", "previous video", "پچھلی ویڈیو", "پچھلا ویڈیو", "پچھلا" -> "previous"
            else -> null
        }
        if (mediaAction != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "میڈیا کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.controlMedia(mediaAction)) {
                when (mediaAction) {
                    "play" -> "ویڈیو چلانے کا کنٹرول فعال کر دیا۔"
                    "pause" -> "ویڈیو روکنے کا کنٹرول فعال کر دیا۔"
                    "next" -> "اگلی ویڈیو کا کنٹرول فعال کر دیا۔"
                    else -> "پچھلی ویڈیو کا کنٹرول فعال کر دیا۔"
                }
            } else {
                "اس ایپ کی اسکرین پر متعلقہ میڈیا بٹن دستیاب نہیں ملا؛ کوئی دوسرا بٹن نہیں دبایا گیا۔"
            }
        }

        val seekOffsetSeconds = MediaSeekCommandParser.parseOffsetSeconds(command)
        if (seekOffsetSeconds != null) {
            if (!IdraakAccessibilityService.isConnected()) return "ویڈیو کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.seekBySeconds(seekOffsetSeconds)) {
                if (seekOffsetSeconds > 0) "ویڈیو کو $seekOffsetSeconds سیکنڈ آگے کر دیا۔"
                else "ویڈیو کو ${-seekOffsetSeconds} سیکنڈ پیچھے کر دیا۔"
            } else {
                "اس ایپ نے قابلِ استعمال ویڈیو ٹائم لائن فراہم نہیں کی؛ وقت تبدیل نہیں کیا گیا۔"
            }
        }

        if (ScreenshotCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "اسکرین شاٹ لینے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.takeScreenshot()) {
                "اسکرین شاٹ لینے کی درخواست بھیج دی؛ Android اسے محفوظ کرے گا۔"
            } else {
                "اس فون کے Android ورژن یا موجودہ سسٹم میں اسکرین شاٹ ایکشن دستیاب نہیں ہوا۔"
            }
        }

        val replacementText = ReplaceSelectedTextCommandParser.parse(command)
        if (replacementText != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "منتخب متن بدلنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.replaceSelectedText(replacementText)) {
                "منتخب عبارت کو نئے متن سے بدل دیا۔"
            } else {
                "متن نہیں بدلا؛ پہلے کسی عام editable فیلڈ میں عبارت منتخب کریں۔ پاس ورڈ فیلڈ محفوظ رہے گی۔"
            }
        }

        if (CutSelectedTextCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "منتخب متن کاٹنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.cutSelectedText()) {
                "منتخب متن کلپ بورڈ میں کاٹ کر محفوظ کر دیا۔"
            } else {
                "متن کاٹنے کے لیے پہلے کسی عام ٹیکسٹ فیلڈ میں عبارت منتخب کریں؛ پاس ورڈ فیلڈ محفوظ رہے گی۔"
            }
        }

        if (CopySelectedTextCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "منتخب متن کاپی کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.copySelectedText()) {
                "منتخب متن کلپ بورڈ میں کاپی کر دیا۔"
            } else {
                "کاپی کرنے کے لیے پہلے کسی عام ٹیکسٹ فیلڈ میں متن منتخب کریں؛ پاس ورڈ فیلڈ محفوظ رہے گی۔"
            }
        }

        if (SelectAllTextCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "متن منتخب کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.selectAllFocusedText()) {
                "منتخب ٹیکسٹ فیلڈ کا پورا متن منتخب کر دیا۔"
            } else {
                "متن منتخب نہیں ہوا؛ ایک عام، غیر خالی ٹیکسٹ فیلڈ منتخب کریں۔ پاس ورڈ فیلڈ کو نہیں چھیڑا جائے گا۔"
            }
        }

        if (ClearTextFieldCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "ٹیکسٹ فیلڈ صاف کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.clearFocusedTextField()) {
                "منتخب ٹیکسٹ فیلڈ کا متن صاف کر دیا۔"
            } else {
                "متن صاف نہیں ہوا؛ ایک عام، قابلِ تدوین ٹیکسٹ فیلڈ منتخب کریں۔ پاس ورڈ فیلڈ کو تبدیل نہیں کیا جائے گا۔"
            }
        }

        if (PasteClipboardCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "کلپ بورڈ سے پیسٹ کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.pasteClipboardText()) {
                "کلپ بورڈ کا متن موجودہ منتخب ٹیکسٹ فیلڈ میں پیسٹ کر دیا۔"
            } else {
                "پیسٹ نہیں ہو سکا؛ ایک عام ٹیکسٹ فیلڈ منتخب کریں اور کلپ بورڈ میں ٹیکسٹ کاپی کریں۔ پاس ورڈ فیلڈ میں پیسٹ نہیں کیا جائے گا۔"
            }
        }

        if (CopyScreenTextCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "اسکرین کا متن کاپی کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.copyScreenText()) {
                "اسکرین پر نظر آنے والا متن کلپ بورڈ میں کاپی کر دیا؛ اب آپ اسے پیسٹ کر سکتے ہیں۔"
            } else {
                "اسکرین پر کاپی کرنے کے قابل متن نہیں ملا۔"
            }
        }

        if (lower in listOf(
                "read screen", "what is on screen", "screen text", "اسکرین پڑھو",
                "اسکرین پر کیا ہے", "اسکرین پر کیا لکھا ہے", "جو اسکرین پر ہے پڑھو"
            )) {
            if (!IdraakAccessibilityService.isConnected()) return "اسکرین پڑھنے کے لیے Idraak Accessibility فعال کریں۔"
            val visible = IdraakAccessibilityService.readScreenText()
            return if (visible.isBlank()) "اسکرین پر پڑھنے کے قابل تحریر نہیں ملی۔"
            else "اسکرین پر نظر آنے والی تحریر یہ ہے:\n$visible"
        }

        val zoomIn = ZoomCommandParser.parse(command)
        if (zoomIn != null) {
            if (!IdraakAccessibilityService.isConnected()) return "زوم کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.pinchZoom(zoomIn)) {
                if (zoomIn) "اسکرین کو زوم اِن کر دیا۔" else "اسکرین کو زوم آؤٹ کر دیا۔"
            } else {
                "اس ایپ میں pinch zoom gesture دستیاب نہیں ہو سکا۔"
            }
        }

        val doubleTapTarget = DoubleTapCommandParser.parse(command)
        if (doubleTapTarget != null) {
            if (!IdraakAccessibilityService.isConnected()) return "ڈبل ٹیپ کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.doubleTapText(doubleTapTarget)) {
                "“$doubleTapTarget” پر ڈبل ٹیپ کر دیا۔"
            } else {
                "“$doubleTapTarget” کا واضح اور منفرد کنٹرول نہیں ملا؛ کوئی دوسرا کنٹرول نہیں دبایا گیا۔"
            }
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
            val sensitiveAction = Regex(
                "(?i)\\b(like|subscribe|follow|send|post|publish|delete|remove|block|report|buy|purchase|pay|unsubscribe)\\b"
            ).containsMatchIn(clickTarget) || listOf(
                "پسند", "لائک", "سبسکرائب", "فالو", "بھیج", "شائع", "حذف", "مٹاؤ",
                "خرید", "ادائیگی", "بلاک", "رپورٹ"
            ).any { clickTarget.contains(it, ignoreCase = true) }
            if (sensitiveAction) {
                pendingSensitiveClick = clickTarget
                return "یہ عمل آپ کے اکاؤنٹ یا مواد کو بدل سکتا ہے۔ کیا میں “$clickTarget” دباؤں؟ تصدیق کے لیے “ہاں” کہیں، یا روکنے کے لیے “نہیں”۔"
            }
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

        val recentAppTarget = RecentAppSelectionCommandParser.parse(command)
        if (recentAppTarget != null) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "Recent Apps سے ایپ منتخب کرنے کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.clickText(recentAppTarget)) {
                "Recent Apps میں “$recentAppTarget” منتخب کر لیا۔"
            } else {
                "“$recentAppTarget” کا واضح اور منفرد کارڈ نہیں ملا۔ پہلے Recent Apps کھولیں، پھر دوبارہ کہیں۔"
            }
        }

        if (RecentAppsCommandParser.matches(command)) {
            if (!IdraakAccessibilityService.isConnected()) return "Recent Apps کھولنے کے لیے Idraak Accessibility فعال کریں۔"
            return if (IdraakAccessibilityService.showRecentApps()) {
                "Recent Apps کھول دیے؛ اب مطلوبہ ایپ منتخب کریں۔"
            } else {
                "اس فون پر Recent Apps ایکشن دستیاب نہیں ہوا۔"
            }
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
        val horizontalSwipe = HorizontalSwipeCommandParser.parse(command)
        if (horizontalSwipe != null) {
            if (!IdraakAccessibilityService.isConnected()) return "سوائپ کے لیے Idraak Accessibility فعال کریں۔"
            val moved = if (horizontalSwipe) {
                IdraakAccessibilityService.swipeLeft()
            } else {
                IdraakAccessibilityService.swipeRight()
            }
            return if (moved) "اسکرین پر ${if (horizontalSwipe) "بائیں" else "دائیں"} سوائپ کر دیا۔"
            else "اس اسکرین پر سوائپ نہیں ہو سکا۔"
        }

        val repeatedScroll = RepeatedScrollCommandParser.parse(command)
        if (repeatedScroll != null) {
            if (!IdraakAccessibilityService.isConnected()) return "اسکرول کے لیے Idraak Accessibility فعال کریں۔"
            val moved = IdraakAccessibilityService.scrollByCount(
                repeatedScroll.forward,
                repeatedScroll.count
            )
            return if (moved == repeatedScroll.count) {
                "${if (repeatedScroll.forward) "نیچے" else "اوپر"} ${moved} بار اسکرول کر دیا۔"
            } else if (moved > 0) {
                "صرف ${moved} بار اسکرول ہو سکا؛ اسکرین نے مزید اسکرول قبول نہیں کیا۔"
            } else {
                "اس اسکرین پر اسکرول نہیں ہو سکا۔"
            }
        }

        val down = lower in listOf("scroll down", "نیچے اسکرول کرو", "نیچے کرو", "swipe up", "اوپر سوائپ کرو")
        val up = lower in listOf("scroll up", "اوپر اسکرول کرو", "اوپر کرو", "swipe down", "نیچے سوائپ کرو")
        if (down || up) {
            if (!IdraakAccessibilityService.isConnected()) return "اسکرول کے لیے Idraak Accessibility فعال کریں۔"
            val moved = if (down) {
                IdraakAccessibilityService.scrollByCount(true, 1) == 1
            } else {
                IdraakAccessibilityService.scrollByCount(false, 1) == 1
            }
            return if (moved) "اسکرین پر اسکرول ایکشن بھیج دیا۔" else "اس اسکرین پر اسکرول نہیں ہو سکا۔"
        }
        return null
    }
}
