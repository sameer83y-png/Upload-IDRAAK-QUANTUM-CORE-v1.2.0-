package com.idraak.quantum

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.idraak.quantum.core.AndroidActionExecutor
import com.idraak.quantum.core.LocalMemoryStore
import com.idraak.quantum.core.OfflineCommandEngine
import java.util.Locale

class MainActivity : Activity() {
    private val commandEngine = OfflineCommandEngine()
    private val memoryStore by lazy { LocalMemoryStore(applicationContext) }
    private lateinit var actionExecutor: AndroidActionExecutor
    private lateinit var responseView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionExecutor = AndroidActionExecutor(this)
        buildDashboard()
    }

    private fun buildDashboard() {
        val accent = Color.rgb(71, 208, 255)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 44, 28, 28)
            setBackgroundColor(Color.rgb(7, 12, 24))
        }
        root.addView(TextView(this).apply {
            text = "IDRAAK"
            textSize = 32f
            setTextColor(accent)
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "QUANTUM CORE  •  OFFLINE FOUNDATION"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 34)
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "◉"
            textSize = 76f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "CORE ONLINE  •  LOCAL MEMORY READY"
            textSize = 13f
            setTextColor(Color.rgb(105, 235, 178))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }, matchWrap())

        val commandInput = EditText(this).apply {
            hint = "Type a command…"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            setPadding(18, 14, 18, 14)
            setBackgroundColor(Color.rgb(24, 34, 52))
        }
        root.addView(commandInput, matchWrap())

        root.addView(Button(this).apply {
            text = "RUN COMMAND"
            setTextColor(Color.BLACK)
            setBackgroundColor(accent)
            setOnClickListener {
                val command = commandInput.text.toString().trim()
                val memoryResponse = handleMemoryCommand(command)
                if (memoryResponse != null) {
                    responseView.text = memoryResponse
                } else {
                    val result = commandEngine.process(command)
                    val execution = actionExecutor.execute(result.action)
                    responseView.text = result.message + "\n" + execution
                }
            }
        }, matchWrap().apply { topMargin = 14 })

        responseView = TextView(this).apply {
            text = "Ready. Try: open settings, open camera, search YouTube, or say “remember that…”"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(4, 24, 4, 0)
            gravity = Gravity.CENTER
        }
        root.addView(responseView, matchWrap())
        setContentView(root)
    }

    /**
     * Memory writes require an explicit remember/correct command from the user.
     * Passwords and other credentials must not be stored here.
     */
    private fun handleMemoryCommand(raw: String): String? {
        val command = raw.trim()
        val normalized = command.lowercase(Locale.ROOT)

        val rememberPrefix = listOf("remember that ", "remember ", "याद रखो ", "याद रखना ")
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

        if (normalized in listOf("what do you remember", "show memory", "list memories", "मेरी यादें बताओ")) {
            val memories = memoryStore.all()
            return if (memories.isEmpty()) {
                "Your local memory is empty."
            } else {
                memories.joinToString(separator = "\n\n") { "• [${it.category}] ${it.content}" }
            }
        }

        val searchPrefix = listOf("search memory for ", "find memory ")
            .firstOrNull { normalized.startsWith(it) }
        if (searchPrefix != null) {
            val query = command.drop(searchPrefix.length).trim()
            if (query.isBlank()) return "Tell me what to search for."
            val matches = memoryStore.search(query)
            return if (matches.isEmpty()) "No matching memory found."
            else matches.joinToString(separator = "\n\n") { "• ${it.content}" }
        }

        val forgetPrefix = listOf("forget memory ", "forget that ")
            .firstOrNull { normalized.startsWith(it) }
        if (forgetPrefix != null) {
            val exactText = command.drop(forgetPrefix.length).trim()
            if (exactText.isBlank()) return "Tell me the exact memory you want removed."
            val deleted = memoryStore.forgetMatching(exactText)
            return if (deleted > 0) "Removed $deleted matching memory item(s) from this device."
            else "I couldn't find an exact matching memory to remove."
        }

        if (normalized.startsWith("correct memory:")) {
            val correction = command.substringAfter(":", "").split("->", limit = 2)
            if (correction.size != 2) return "Use this format: correct memory: old text -> corrected text"
            val changed = memoryStore.correct(correction[0], correction[1])
            return if (changed) "Memory corrected. The correction was added to the local history."
            else "Correction saved in history, but no exact existing memory matched the old text."
        }

        if (normalized == "show correction history") {
            val history = memoryStore.correctionHistory()
            return if (history.isEmpty()) "There are no saved corrections yet."
            else history.joinToString("\n\n") {
                "• ${it.previousValue} → ${it.correctedValue}"
            }
        }
        return null
    }

    override fun onDestroy() {
        if (::responseView.isInitialized) memoryStore.close()
        super.onDestroy()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
