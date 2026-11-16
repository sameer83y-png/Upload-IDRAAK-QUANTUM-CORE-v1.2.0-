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

/**
 * IDRAAK settings screen.
 *
 * यह केवल settings UI और SettingsManager के बीच काम करता है।
 * Voice recognition और Gemini requests यहाँ execute नहीं होते।
 */
internal class SettingsView(
    context: Context,
    private val settingsManager: SettingsManager,
    private val onClose: () -> Unit
) : ScrollView(context) {

    private val content = LinearLayout(context)

    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var offlineSwitch: Switch
    private lateinit var wakeWordSwitch: Switch
    private lateinit var languageSpinner: Spinner
    private lateinit var speechRateSeekBar: SeekBar
    private lateinit var speechRateValue: TextView

    private val languages = listOf(
        "auto",
        "hi-IN",
        "en-US",
        "ur-PK",
        "ar-SA"
    )

    init {
        setFillViewport(true)
        setBackgroundColor(Color.rgb(5, 10, 28))

        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20), dp(24), dp(20), dp(28))

        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        buildSettings()
        loadSettings()
    }

    private fun buildSettings() {
        addTitle("IDRAAK Settings")
        addDescription("अपने voice assistant की preferences बदलें।")

        addSectionTitle("Gemini AI")

        apiKeyInput = createEditText(
            hint = "Gemini API key",
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        content.addView(apiKeyInput, matchWidth())

        modelInput = createEditText(
            hint = "Gemini model",
            inputType = InputType.TYPE_CLASS_TEXT
        )
        content.addView(modelInput, matchWidth())

        offlineSwitch = createSwitch("Offline mode")
        content.addView(offlineSwitch, matchWidth())

        addSectionTitle("Voice")

        wakeWordSwitch = createSwitch("Wake word IDRAAK enabled")
        content.addView(wakeWordSwitch, matchWidth())

        addLabel("Recognition / voice language")

        languageSpinner = Spinner(context)
        languageSpinner.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            languages
        )
        content.addView(languageSpinner, matchWidth())

        addLabel("Speech rate")

        speechRateValue = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        content.addView(speechRateValue, matchWidth())

        speechRateSeekBar = SeekBar(context).apply {
            max = 150
            progress = 50
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        val rate = 0.5f + progress / 100f
                        speechRateValue.text =
                            String.format("%.1fx", rate)
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) = Unit

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) = Unit
                }
            )
        }
        content.addView(speechRateSeekBar, matchWidth())

        val saveButton = createButton("Save Settings") {
            saveSettings()
        }
        content.addView(saveButton, matchWidth())

        val closeButton = createButton("Close") {
            onClose()
        }
        content.addView(closeButton, matchWidth())
    }

    private fun loadSettings() {
        apiKeyInput.setText(settingsManager.apiKey)
        modelInput.setText(settingsManager.geminiModel)

        offlineSwitch.isChecked = settingsManager.offlineMode
        wakeWordSwitch.isChecked = settingsManager.wakeWordEnabled

        val languageIndex = languages.indexOf(
            settingsManager.voiceLanguage
        ).coerceAtLeast(0)

        languageSpinner.setSelection(languageIndex)

        val progress = (
            (settingsManager.speechRate - 0.5f) * 100f
            ).toInt().coerceIn(0, 150)

        speechRateSeekBar.progress = progress
        speechRateValue.text =
            String.format("%.1fx", settingsManager.speechRate)
    }

    private fun saveSettings() {
        settingsManager.apiKey = apiKeyInput.text
            .toString()
            .trim()

        settingsManager.geminiModel = modelInput.text
            .toString()
            .trim()

        settingsManager.offlineMode = offlineSwitch.isChecked
        settingsManager.wakeWordEnabled = wakeWordSwitch.isChecked

        settingsManager.voiceLanguage =
            languageSpinner.selectedItem?.toString() ?: "auto"

        settingsManager.speechRate =
            0.5f + speechRateSeekBar.progress / 100f

        Toast.makeText(
            context,
            "Settings saved",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun addTitle(value: String) {
        val view = TextView(context).apply {
            text = value
            textSize = 25f
            setTextColor(Color.rgb(0, 225, 255))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(view, matchWidth())
    }

    private fun addDescription(value: String) {
        val view = TextView(context).apply {
            text = value
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(18))
        }
        content.addView(view, matchWidth())
    }

    private fun addSectionTitle(value: String) {
        val view = TextView(context).apply {
            text = value
            textSize = 18f
            setTextColor(Color.rgb(0, 225, 255))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(10))
        }
        content.addView(view, matchWidth())
    }

    private fun addLabel(value: String) {
        val view = TextView(context).apply {
            text = value
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(6))
        }
        content.addView(view, matchWidth())
    }

    private fun createEditText(
        hint: String,
        inputType: Int
    ): EditText {
        return EditText(context).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
    }

    private fun createSwitch(label: String): Switch {
        return Switch(context).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(10))
        }
    }

    private fun createButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener {
                action()
            }
        }
    }

    private fun matchWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
