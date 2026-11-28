package com.idraak.quantum

import android.Manifest
import android.os.Build
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.ScrollView
import com.idraak.quantum.core.AndroidActionExecutor
import com.idraak.quantum.core.LocalMemoryStore
import com.idraak.quantum.core.ConversationHistoryStore
import com.idraak.quantum.core.OfflineCommandEngine
import com.idraak.quantum.core.OnDeviceVoiceController
import com.idraak.quantum.core.IdraakAccessibilityService
import com.idraak.quantum.core.ActionRecoveryPolicy
import com.idraak.quantum.core.GeminiBrainClient
import com.idraak.quantum.core.GeminiKeyStore
import com.idraak.quantum.core.LearnedCommandStore
import java.util.concurrent.Executors
import java.util.Locale
import java.util.Calendar

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_MICROPHONE = 4201
    }

    private val commandEngine = OfflineCommandEngine()
    private val memoryStore by lazy { LocalMemoryStore(applicationContext) }
    private val historyStore by lazy { ConversationHistoryStore(applicationContext) }
    private val learnedCommandStore by lazy { LearnedCommandStore(applicationContext) }
    private val geminiExecutor = Executors.newSingleThreadExecutor()
    private lateinit var actionExecutor: AndroidActionExecutor
    private lateinit var responseView: TextView
    private lateinit var commandInput: EditText
    private var voiceController: OnDeviceVoiceController? = null
    private var speechOutput: TextToSpeech? = null
    private var speechOutputReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionExecutor = AndroidActionExecutor(this)
        voiceController = OnDeviceVoiceController(
            this,
            onText = { spoken ->
                runOnUiThread {
                    commandInput.setText(spoken)
                    executeCommand(spoken)
                }
            },
            onStatus = { status -> runOnUiThread { responseView.text = status } }
        )
        speechOutput = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = speechOutput?.setLanguage(Locale("ur", "PK"))
                speechOutputReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        buildDashboard()
        requestNotificationPermissionIfNeeded()
    }

    private fun buildDashboard() {
        val preferences = getSharedPreferences("idraak_settings", MODE_PRIVATE)
        val accent = preferences.getInt("accent", Color.rgb(67, 218, 255))
        val background = preferences.getInt("background", Color.rgb(5, 10, 23))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 34, 28, 28)
            setBackgroundColor(background)
        }

        root.addView(TextView(this).apply {
            text = "IDRAAK"
            textSize = 34f
            setTextColor(accent)
            gravity = Gravity.CENTER
            letterSpacing = 0.22f
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "Q U A N T U M   C O R E"
            textSize = 10f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 26)
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "⚙  SETTINGS"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(24, 42, 65))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }, matchWrap().apply { topMargin = 8; bottomMargin = 14 })

        root.addView(ImageView(this).apply {
            setImageResource(com.idraak.quantum.R.drawable.idraak_core_logo)
            contentDescription = "Idraak Quantum Core logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 2, 0, 10)
        }, LinearLayout.LayoutParams(176, 176).apply {
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "LOCAL CORE  •  MEMORY READY"
            textSize = 12f
            setTextColor(Color.rgb(105, 235, 178))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 26)
        }, matchWrap())

        commandInput = EditText(this).apply {
            hint = "اپنا حکم یہاں لکھیں…"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            setPadding(18, 15, 18, 15)
            setBackgroundColor(Color.rgb(22, 34, 54))
        }
        root.addView(commandInput, matchWrap())

        root.addView(Button(this).apply {
            text = "RUN COMMAND  /  حکم چلائیں"
            setTextColor(Color.BLACK)
            setBackgroundColor(accent)
            setOnClickListener { executeCommand(commandInput.text.toString()) }
        }, matchWrap().apply { topMargin = 12 })

        root.addView(Button(this).apply {
            text = "🎙  اردو میں بولیں  •  ON-DEVICE VOICE"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 54, 82))
            setOnClickListener { startVoiceInput() }
        }, matchWrap().apply { topMargin = 10 })
        root.addView(Button(this).apply {
            text = "CHAT HISTORY  /  پچھلی گفتگو"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 48, 70))
            setOnClickListener { showConversationHistory() }
        }, matchWrap().apply { topMargin = 8 })

        responseView = TextView(this).apply {
            text = "السلام علیکم۔ اپنا حکم لکھیں یا دستیاب آف لائن وائس انجن سے بولیں۔"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(6, 24, 6, 0)
            gravity = Gravity.CENTER
        }
        root.addView(TextView(this).apply {
            text = "SMART SUGGESTIONS"
            textSize = 11f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }, matchWrap())

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val suggestions = when (hour) {
            in 5..10 -> listOf("صبح کی یاد دہانی لگاؤ" to "مجھے 30 منٹ بعد اپنا دن پلان کرنا یاد دلانا")
            in 11..16 -> listOf("YouTube تلاش" to "یوٹیوب پر آج کی ٹیک خبریں تلاش کرو")
            in 17..21 -> listOf("شام کا الارم" to "الارم 19:30 لگاؤ")
            else -> listOf("آواز کی سیٹنگز" to "ساؤنڈ سیٹنگز کھولو")
        }
        for ((label, command) in suggestions) {
            root.addView(Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(20, 38, 60))
                setOnClickListener {
                    commandInput.setText(command)
                    executeCommand(command)
                }
            }, matchWrap().apply { topMargin = 6 })
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            root.addView(Button(this).apply {
                text = "ENABLE VOICE INPUT"
                setOnClickListener { startVoiceInput() }
            }, matchWrap().apply { topMargin = 6 })
        }

        root.addView(responseView, matchWrap())

        root.addView(TextView(this).apply {
            text = "PRIVATE • ON-DEVICE MEMORY • PERMISSION-AWARE CONTROL"
            textSize = 9f
            setTextColor(Color.rgb(115, 135, 160))
            gravity = Gravity.CENTER
            setPadding(0, 34, 0, 0)
        }, matchWrap())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
    }


    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4202)
        }
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            return
        }
        voiceController?.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                voiceController?.start()
            } else {
                responseView.text = "مائیکروفون کی اجازت کے بغیر آواز نہیں سن سکتا۔"
            }
        }
    }

    private fun executeCommand(rawCommand: String) {
        val command = rawCommand.trim()
        if (command.isBlank()) {
            responseView.text = "براہ کرم اپنا حکم لکھیں یا بولیں۔"
            return
        }
        val accessibilityResponse = handleDirectAccessibilityCommand(command)
        val memoryResponse = if (accessibilityResponse == null) handleMemoryCommand(command) else null
        val learningResponse = if (accessibilityResponse == null && memoryResponse == null) handleLearningCommand(command) else null
        val response = accessibilityResponse ?: memoryResponse ?: learningResponse ?: run {
            val effectiveCommand = learnedCommandStore.resolve(command) ?: command
            val result = commandEngine.process(effectiveCommand)
            if (result.action == com.idraak.quantum.core.CommandAction.None) {
                val apiKey = GeminiKeyStore(this).read()
                if (!apiKey.isNullOrBlank()) {
                    responseView.text = "Gemini سے جواب حاصل کر رہا ہوں…"
                    historyStore.add("user", command)
                    val model = getSharedPreferences("idraak_settings", MODE_PRIVATE)
                        .getString("gemini_model", GeminiBrainClient.DEFAULT_MODEL)
                        ?: GeminiBrainClient.DEFAULT_MODEL
                    geminiExecutor.execute {
                        val reply = try {
                            GeminiBrainClient(apiKey, model).reply(command)
                        } catch (error: Exception) {
                            "Gemini سے جواب نہیں ملا: " + (error.message ?: "network یا API کی خرابی") +
                                "\n\n" + ActionRecoveryPolicy.responseForUnknownCommand(command, result.message)
                        }
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                responseView.text = reply
                                historyStore.add("assistant", reply)
                                speakUrdu(reply)
                            }
                        }
                    }
                    return
                }
                ActionRecoveryPolicy.responseForUnknownCommand(command, result.message)
            } else {
                val execution = actionExecutor.execute(result.action)
                if (execution.contains("compatible") ||
                    execution.contains("ایپ نہیں ملی") ||
                    execution.contains("Android نے مطلوبہ اجازت") ||
                    execution.contains("یہ کام مکمل نہیں ہو سکا")
                ) {
                    ActionRecoveryPolicy.responseForExecutionFailure(command, execution)
                } else {
                    result.message + "\n" + execution
                }
            }
        }
        responseView.text = response
        historyStore.add("user", command)
        historyStore.add("assistant", response)
        speakUrdu(response)
    }

    /** Learns only explicit aliases that point to a command already supported offline. */
    private fun handleLearningCommand(raw: String): String? {
        val command = raw.trim()
        val normalized = command.lowercase(Locale.ROOT)
        val prefixes = listOf(
            "teach command:", "learn command:", "سیکھو:", "کمانڈ سکھاؤ:", "یہ کمانڈ یاد رکھو:"
        )
        val prefix = prefixes.firstOrNull { normalized.startsWith(it) }
        if (prefix != null) {
            val parts = command.drop(prefix.length).split("->", limit = 2)
            if (parts.size != 2) {
                return "اس طرح سکھائیں: teach command: youtube chalao -> youtube kholo"
            }
            val phrase = parts[0].trim()
            val supportedCommand = parts[1].trim()
            if (phrase.isBlank() || supportedCommand.isBlank()) {
                return "غلط بولے گئے جملے اور درست کمانڈ، دونوں بتائیں۔"
            }
            if (commandEngine.process(supportedCommand).action == com.idraak.quantum.core.CommandAction.None) {
                return "یہ درست کمانڈ ابھی آف لائن انجن میں موجود نہیں۔ پہلے کوئی چلنے والی کمانڈ دیں، مثلاً: youtube kholo"
            }
            return try {
                learnedCommandStore.remember(phrase, supportedCommand)
                "ٹھیک ہے، میں نے یہ جملہ اپنے آف لائن کمانڈ رولز میں محفوظ کر لیا۔ آئندہ “$phrase” کو “$supportedCommand” سمجھوں گا۔"
            } catch (error: Exception) {
                "یہ کمانڈ محفوظ نہیں ہو سکی: " + (error.message ?: "storage error")
            }
        }

        if (normalized in listOf("show learned commands", "سیکھی ہوئی کمانڈز دکھاؤ", "میرے سیکھے ہوئے کمانڈ دکھاؤ")) {
            val learned = learnedCommandStore.all()
            return if (learned.isEmpty()) "ابھی کوئی نئی کمانڈ نہیں سکھائی گئی۔"
            else learned.entries.joinToString("\n") { "• “\${it.key}” → “\${it.value}”" }
        }

        val removePrefix = listOf("forget learned command:", "سیکھی کمانڈ بھول جاؤ:").firstOrNull {
            normalized.startsWith(it)
        }
        if (removePrefix != null) {
            val phrase = command.drop(removePrefix.length).trim()
            return if (learnedCommandStore.clear(phrase)) "یہ سیکھا ہوا کمانڈ رول ہٹا دیا۔"
            else "اس جملے کا کوئی محفوظ شدہ رول نہیں ملا۔"
        }
        return null
    }

    /**
     * Executes only explicit, single-step screen actions requested by the user.
     * It does not run hidden loops or tap unknown controls automatically.
     */
    private fun handleDirectAccessibilityCommand(raw: String): String? {
        val command = raw.trim()
        val lower = command.lowercase(Locale.ROOT)

        if (lower in listOf("read screen", "what is on screen", "screen text", "اسکرین پڑھو", "اسکرین پر کیا ہے", "اسکرین پر کیا لکھا ہے", "جو اسکرین پر ہے پڑھو")) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "اسکرین پڑھنے کے لیے Settings میں Idraak Accessibility فعال کریں۔"
            }
            val visibleText = IdraakAccessibilityService.readScreenText()
            return if (visibleText.isBlank()) "اسکرین پر پڑھنے کے قابل تحریر نہیں ملی۔"
            else "اسکرین پر نظر آنے والی تحریر یہ ہے:\\n$visibleText"
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
            if (!IdraakAccessibilityService.isConnected()) {
                return "اسکرین کنٹرول کے لیے پہلے Settings میں جا کر Idraak Accessibility کو فعال کریں۔"
            }
            return if (IdraakAccessibilityService.clickText(clickTarget)) {
                "اسکرین پر '$clickTarget' والا دستیاب بٹن ٹیپ کر دیا۔"
            } else {
                "اسکرین پر '$clickTarget' کے نام سے قابلِ رسائی بٹن نہیں ملا۔ ممکن ہے یہ چیز تصویر یا گیم کینوس میں ہو۔"
            }
        }

        if (lower in listOf("home", "go home", "ہوم پر جاؤ", "ہوم کھولو", "گھر واپس جاؤ")) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "Home کنٹرول کے لیے Settings میں Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.goHome()) "Home screen کھولنے کا حکم دے دیا۔"
            else "Home screen ایکشن اس وقت دستیاب نہیں۔"
        }

        if (lower in listOf("show notifications", "open notifications", "نوٹیفکیشن دکھاؤ", "نوٹیفکیشن کھولو")) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "Notifications کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.showNotifications()) "Notifications panel کھول دیا۔"
            else "Notifications panel نہیں کھل سکا۔"
        }

        if (lower in listOf("quick settings", "open quick settings", "کھول فوری سیٹنگز", "فوری سیٹنگز کھولو", "کوئک سیٹنگز کھولو")) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "Quick Settings کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.showQuickSettings()) "Quick Settings panel کھول دیا۔"
            else "Quick Settings panel نہیں کھل سکا۔"
        }

        val isBack = lower in listOf("back", "go back", "واپس جاؤ", "پیچھے جاؤ", "واپس")
        if (isBack) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "Back کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            }
            return if (IdraakAccessibilityService.goBack()) "Back کر دیا۔"
            else "اس وقت Back ایکشن دستیاب نہیں۔"
        }

        val isScrollDown = lower in listOf("scroll down", "نیچے اسکرول کرو", "نیچے کرو", "swipe up", "اوپر سوائپ کرو")
        val isScrollUp = lower in listOf("scroll up", "اوپر اسکرول کرو", "اوپر کرو", "swipe down", "نیچے سوائپ کرو")
        if (isScrollDown || isScrollUp) {
            if (!IdraakAccessibilityService.isConnected()) {
                return "اسکرول کنٹرول کے لیے Idraak Accessibility فعال کریں۔"
            }
            val moved = if (isScrollDown) {
                IdraakAccessibilityService.scrollForward() || IdraakAccessibilityService.swipeUp()
            } else {
                IdraakAccessibilityService.scrollBackward() || IdraakAccessibilityService.swipeDown()
            }
            return if (moved) "اسکرین پر اسکرول/سوائپ ایکشن بھیج دیا۔"
            else "اس اسکرین پر اسکرول یا سوائپ ایکشن نہیں ہو سکا۔"
        }
        return null
    }

    private fun showConversationHistory() {
        val entries = historyStore.recent(100)
        val content = if (entries.isEmpty()) {
            "ابھی کوئی گفتگو محفوظ نہیں ہے۔"
        } else {
            entries.asReversed().joinToString("\n\n") { entry ->
                val speaker = if (entry.role == "user") "آپ" else "Idraak"
                "$speaker: ${entry.message}"
            }
        }
        AlertDialog.Builder(this)
            .setTitle("CHAT HISTORY")
            .setMessage(content)
            .setPositiveButton("بند کریں", null)
            .show()
    }

    private fun speakUrdu(message: String) {
        if (speechOutputReady) {
            speechOutput?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "idraak-response")
        }
    }

    /** Memory changes happen only after an explicit remember/correct command. */
    private fun handleMemoryCommand(raw: String): String? {
        val command = raw.trim()
        val normalized = command.lowercase(Locale.ROOT)

        val rememberPrefix = listOf(
            "remember that ", "remember ", "یاد رکھو ", "یاد رکھنا ",
            "یہ یاد رکھو ", "یہ بات یاد رکھو ", "याद रखो ", "याद रखना "
        )
            .firstOrNull { normalized.startsWith(it) }
        if (rememberPrefix != null) {
            val fact = command.drop(rememberPrefix.length).trim()
            if (fact.isBlank()) return "Please tell me what you want me to remember."
            return try {
                memoryStore.remember(fact)
                "Saved in this device's offline memory."
            } catch (error: Exception) {
                "I couldn't save that memory: ${error.localizedMessage ?: "storage error"}"
            }
        }

        if (normalized in listOf(
                "what do you remember", "show memory", "list memories", "मेरी यादें बताओ",
                "تمہیں کیا یاد ہے", "کیا یاد ہے", "میری یادیں بتاؤ", "میری یادداشت دکھاؤ"
            )) {
            val memories = memoryStore.all()
            return if (memories.isEmpty()) "Your local memory is empty."
            else memories.joinToString(separator = "\n\n") { "• [${it.category}] ${it.content}" }
        }

        val searchPrefix = listOf("search memory for ", "find memory ", "یادداشت میں تلاش کرو ", "یادوں میں تلاش کرو ")
            .firstOrNull { normalized.startsWith(it) }
        if (searchPrefix != null) {
            val query = command.drop(searchPrefix.length).trim()
            if (query.isBlank()) return "Tell me what to search for."
            val matches = memoryStore.search(query)
            return if (matches.isEmpty()) "No matching memory found."
            else matches.joinToString(separator = "\n\n") { "• ${it.content}" }
        }

        val forgetPrefix = listOf(
            "forget memory ", "forget that ", "یہ بات بھول جاؤ ",
            "یادداشت سے حذف کرو ", "یہ یادداشت بھول جاؤ "
        )
            .firstOrNull { normalized.startsWith(it) }
        if (forgetPrefix != null) {
            val exactText = command.drop(forgetPrefix.length).trim()
            if (exactText.isBlank()) return "Tell me the exact memory you want removed."
            val deleted = memoryStore.forgetMatching(exactText)
            return if (deleted > 0) "Removed $deleted matching memory item(s) from this device."
            else "I couldn't find an exact matching memory to remove."
        }

        if (normalized.startsWith("correct memory:") || normalized.startsWith("یادداشت درست کرو:")) {
            val correction = command.substringAfter(":", "").split("->", limit = 2)
            if (correction.size != 2) return "Use this format: correct memory: old text -> corrected text"
            val changed = memoryStore.correct(correction[0], correction[1])
            return if (changed) "Memory corrected. The correction was added to the local history."
            else "Correction saved in history, but no exact existing memory matched the old text."
        }

        if (normalized == "show correction history" || normalized == "درستیوں کی تاریخ دکھاؤ") {
            val history = memoryStore.correctionHistory()
            return if (history.isEmpty()) "There are no saved corrections yet."
            else history.joinToString("\n\n") {
                "• ${it.previousValue} → ${it.correctedValue}"
            }
        }
        return null
    }

    override fun onResume() {
        super.onResume()
        if (::responseView.isInitialized) buildDashboard()
    }

    override fun onDestroy() {
        voiceController?.destroy()
        speechOutput?.stop()
        speechOutput?.shutdown()
        if (::responseView.isInitialized) {
            memoryStore.close()
            historyStore.close()
        }
        super.onDestroy()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
