package com.idraak.quantum

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * IDRAAK Settings Screen
 *
 * Responsibilities:
 * - Display and update application settings.
 * - Configure Gemini, voice language, gender and speech rate.
 * - Configure conversation history and permanent memory preferences.
 *
 * Actual conversation history and permanent memory data are handled
 * by their dedicated managers, not by this view.
 */
internal class SettingsView(
    context: Context,
    private val settingsManager: SettingsManager,
    private val onClose: () -> Unit
) : ScrollView(context) {

    private val content = LinearLayout(context)

    private val apiKeyInput = EditText(context)
    private val modelInput = EditText(context)

    private val languageSpinner = Spinner(context)
    private val genderSpinner = Spinner(context)

    private val speechRateLabel = TextView(context)
    private val speechRateSeekBar = SeekBar(context)

    private val offlineSwitch = Switch(context)
    private val wakeWordSwitch = Switch(context)
    private val historySwitch = Switch(context)
    private val memorySwitch = Switch(context)

    private var initializing = true

    private val languageValues = listOf(
        "auto",
        "hi-IN",
        "en-US",
        "en-IN",
        "ur-PK",
        "ar-SA"
    )

    private val languageLabels = listOf(
        "Automatic",
        "Hindi",
        "English (US)",
        "English (India)",
        "Urdu",
        "Arabic"
    )

    private val genderValues = listOf(
        "Male",
        "Female"
    )

    init {
        configureScreen()
        buildScreen()
        loadSettings()
        initializing = false
    }

    private fun configureScreen() {
        setBackgroundColor(Color.rgb(8, 12, 28))
        isFillViewport = true
        clipToPadding = false

        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20), dp(24), dp(20), dp(28))
        content.setBackgroundColor(Color.rgb(8, 12, 28))

        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun buildScreen() {
        addTitle("IDRAAK SETTINGS")
        addDescription("Configure your assistant")

        addSection("GEMINI AI")

        addLabel("Gemini API Key")
        apiKeyInput.hint = "Enter your Gemini API key"
        apiKeyInput.inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        apiKeyInput.singleLine = true
        styleInput(apiKeyInput)
        content.addView(apiKeyInput, matchWidth())

        addLabel("Gemini Model")
        modelInput.hint = SettingsManager.DEFAULT_MODEL
        modelInput.singleLine = true
        modelInput.inputType = InputType.TYPE_CLASS_TEXT
        styleInput(modelInput)
        content.addView(modelInput, matchWidth())

        val saveAiButton = createButton("SAVE AI SETTINGS")
        saveAiButton.setOnClickListener {
            saveAiSettings()
        }
        content.addView(saveAiButton, matchWidth())

        addSection("VOICE SETTINGS")

        addLabel("Voice Language")
        configureSpinner(
            languageSpinner,
            languageLabels
        )
        content.addView(languageSpinner, matchWidth())

        addLabel("Voice Gender")
        configureSpinner(
            genderSpinner,
            genderValues
        )
        content.addView(genderSpinner, matchWidth())

        addLabel("Speech Rate")
        speechRateLabel.textSize = 14f
        speechRateLabel.setTextColor(Color.WHITE)
        content.addView(speechRateLabel, matchWidth())

        speechRateSeekBar.max = 150
        speechRateSeekBar.progress = 50

        speechRateSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val rate = 0.5f + (progress / 100f)

                    speechRateLabel.text =
                        "Speech speed: ${"%.2f".format(rate)}x"

                    if (fromUser && !initializing) {
                        settingsManager.speechRate = rate
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    // No action required.
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    // Value is saved during progress changes.
                }
            }
        )

        content.addView(speechRateSeekBar, matchWidth())

        addSection("ASSISTANT BEHAVIOUR")

        configureSwitch(
            offlineSwitch,
            "Offline Mode",
            "Use offline commands without calling Gemini."
        )

        configureSwitch(
            wakeWordSwitch,
            "Wake Word",
            "Allow IDRAAK to respond to its wake word."
        )

        addSection("DATA & MEMORY")

        configureSwitch(
            historySwitch,
            "Conversation History",
            "Allow ordinary conversation history to be retained."
        )

        configureSwitch(
            memorySwitch,
            "Permanent Memory",
            "Allow explicitly saved memories to be retained."
        )

        addDescription(
            "Conversation History and Permanent Memory are separate. " +
                "Clearing ordinary conversation history must not delete " +
                "explicitly saved memories."
        )

        val closeButton = createButton("CLOSE SETTINGS")
        closeButton.setOnClickListener {
            onClose()
        }

        content.addView(closeButton, matchWidth())
    }

    private fun loadSettings() {
        apiKeyInput.setText(settingsManager.apiKey)
        modelInput.setText(settingsManager.geminiModel)

        offlineSwitch.isChecked = settingsManager.offlineMode
        wakeWordSwitch.isChecked = settingsManager.wakeWordEnabled

        historySwitch.isChecked =
            settingsManager.conversationHistoryEnabled

        memorySwitch.isChecked =
            settingsManager.permanentMemoryEnabled

        val languageIndex = languageValues.indexOf(
            settingsManager.voiceLanguage
        ).coerceAtLeast(0)

        languageSpinner.setSelection(languageIndex)

        val genderIndex = genderValues.indexOf(
            settingsManager.voiceGender
        ).coerceAtLeast(0)

        genderSpinner.setSelection(genderIndex)

        val rate = settingsManager.speechRate
        val progress = ((rate - 0.5f) * 100f)
            .roundToInt()
            .coerceIn(0, 150)

        speechRateSeekBar.progress = progress

        speechRateLabel.text =
            "Speech speed: ${"%.2f".format(rate)}x"
    }

    private fun saveAiSettings() {
        try {
            settingsManager.apiKey = apiKeyInput.text
                .toString()
                .trim()

            settingsManager.geminiModel = modelInput.text
                .toString()
                .trim()

            Toast.makeText(
                context,
                "AI settings saved",
                Toast.LENGTH_SHORT
            ).show()

        } catch (exception: Exception) {
            Toast.makeText(
                context,
                "Could not save API key: ${exception.message ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun configureSpinner(
        spinner: Spinner,
        items: List<String>
    ) {
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            items
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinner.adapter = adapter

        spinner.setBackgroundColor(Color.rgb(25, 34, 58))
    }

    private fun configureSwitch(
        switch: Switch,
        title: String,
        description: String
    ) {
        switch.text = title
        switch.textSize = 16f
        switch.setTextColor(Color.WHITE)
        switch.setPadding(dp(4), dp(10), dp(4), dp(10))

        content.addView(switch, matchWidth())
        addDescription(description)

        switch.setOnCheckedChangeListener { _, checked ->
            if (initializing) return@setOnCheckedChangeListener

            when (switch) {
                offlineSwitch -> settingsManager.offlineMode = checked

                wakeWordSwitch ->
                    settingsManager.wakeWordEnabled = checked

                historySwitch ->
                    settingsManager.conversationHistoryEnabled = checked

                memorySwitch ->
                    settingsManager.permanentMemoryEnabled = checked
            }
        }
    }

    private fun addTitle(value: String) {
        val view = TextView(context)
        view.text = value
        view.textSize = 25f
        view.setTextColor(Color.rgb(80, 190, 255))
        view.typeface = Typeface.DEFAULT_BOLD
        view.gravity = Gravity.CENTER
        view.setPadding(0, dp(8), 0, dp(8))

        content.addView(view, matchWidth())
    }

    private fun addDescription(value: String) {
        val view = TextView(context)
        view.text = value
        view.textSize = 13f
        view.setTextColor(Color.LTGRAY)
        view.setPadding(0, dp(5), 0, dp(10))

        content.addView(view, matchWidth())
    }

    private fun addSection(value: String) {
        val view = TextView(context)
        view.text = value
        view.textSize = 15f
        view.typeface = Typeface.DEFAULT_BOLD
        view.setTextColor(Color.rgb(80, 190, 255))
        view.setPadding(0, dp(22), 0, dp(10))

        content.addView(view, matchWidth())
    }

    private fun addLabel(value: String) {
        val view = TextView(context)
        view.text = value
        view.textSize = 14f
        view.setTextColor(Color.WHITE)
        view.setPadding(0, dp(10), 0, dp(5))

        content.addView(view, matchWidth())
    }

    private fun styleInput(input: EditText) {
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)
        input.textSize = 14f
        input.setPadding(dp(12), dp(10), dp(12), dp(10))
        input.setBackgroundColor(Color.rgb(25, 34, 58))
    }

    private fun createButton(label: String): Button {
        val button = Button(context)
        button.text = label
        button.textSize = 14f
        button.setTextColor(Color.WHITE)
        button.setBackgroundColor(Color.rgb(35, 95, 160))
        button.isAllCaps = false

        return button
    }

    private fun matchWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
