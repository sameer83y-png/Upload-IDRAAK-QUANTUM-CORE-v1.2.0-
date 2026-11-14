
package com.idraak.quantum

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import android.text.InputType
import org.json.JSONObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var launcherView: LauncherHomeView
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech

    private val executor = Executors.newSingleThreadExecutor()

    private var apiKey = ""
    private var model = "gemini-2.5-flash"
    private var gender = "Female"
    private var language = "Hindi"

    private var assistantActive = false
    private var voiceRunning = false

    private val bg = Color.rgb(3, 7, 28)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val spoken = intent?.getStringExtra(
                VoiceForegroundService.EXTRA_TEXT
            ) ?: return

            runOnUiThread {
                handleVoiceInput(spoken)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        tts = TextToSpeech(this, this)

        loadPrefs()
        buildUI()

        val filter = IntentFilter(
            VoiceForegroundService.ACTION_RESULT
        )

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                receiver,
                filter,
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences(
            "idraak",
            MODE_PRIVATE
        )

        apiKey = prefs.getString("key", "") ?: ""

        model = prefs.getString(
            "model",
            "gemini-2.5-flash"
        ) ?: "gemini-2.5-flash"

        gender = prefs.getString(
            "gender",
            "Female"
        ) ?: "Female"

        language = prefs.getString(
            "language",
            "Hindi"
        ) ?: "Hindi"
    }

    private fun savePrefs() {
        getSharedPreferences(
            "idraak",
            MODE_PRIVATE
        ).edit()
            .putString("key", apiKey)
            .putString("model", model)
            .putString("gender", gender)
            .putString("language", language)
            .apply()
    }

    private fun label(
        text: String,
        size: Float
    ): TextView {

        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }
    }

    private fun button(
        text: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 75, 120))
            setOnClickListener {
                action()
            }
        }
    }

    private fun buildUI() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        launcherView = LauncherHomeView(
            this,

            onSettingsClick = {
                openSettings()
            },

            onVoiceClick = {
                toggleVoiceService()
            }
        )

        root.addView(
            launcherView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val commandPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 5, 12, 8)
            setBackgroundColor(Color.rgb(8, 14, 35))
        }

        input = EditText(this).apply {
            hint = "अपनी कमांड लिखें..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundColor(Color.rgb(20, 30, 55))
        }

        commandPanel.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48
            )
        )

        commandPanel.addView(
            button("EXECUTE COMMAND") {
                executeCommand(input.text.toString())
            }
        )

        output = label(
            "IDRAAK QUANTUM CORE तैयार है।",
            12f
        )

        commandPanel.addView(
            output,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            commandPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    // WAKE WORD SYSTEM

    private fun normalizeSpeech(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun isWakePhrase(text: String): Boolean {

        val t = normalizeSpeech(text)

        val phrases = listOf(
            "idraak",
            "hey idraak",
            "hello idraak",
            "hi idraak",
            "hey इद्राक",
            "ओ idraak",
            "ओ इद्राक",
            "अरे idraak",
            "अरे इद्राक",
            "इद्राक",
            "इद्राक जागो",
            "आईड्राक जागो"
        )

        return phrases.any {
            t == it || t.startsWith("$it ")
        }
    }

    private fun isSleepPhrase(text: String): Boolean {

        val t = normalizeSpeech(text)

        return t.contains("सो जाओ idraak") ||
            t.contains("सो जाओ इद्राक") ||
            t.contains("sleep idraak") ||
            t.contains("go to sleep idraak") ||
            t.contains("standby idraak")
    }

    private fun handleVoiceInput(spoken: String) {

        if (isSleepPhrase(spoken)) {

            assistantActive = false

            launcherView.setLiveStatus(
                "● IDRAAK STANDBY"
            )

            output.text = "IDRAAK Standby Mode में चला गया।"

            speak("ठीक है। मैं Standby में जा रहा हूँ।")
            return
        }

        if (!assistantActive) {

            if (isWakePhrase(spoken)) {

                assistantActive = true

                launcherView.setLiveStatus(
                    "● IDRAAK ACTIVE"
                )

                output.text = "IDRAAK सक्रिय है। अब कमांड दीजिए।"

                speak("जी, मैं सक्रिय हूँ। बताइए!")

            } else {

                launcherView.setLiveStatus(
                    "● IDRAAK STANDBY"
                )
            }

            return
        }

        input.setText(spoken)
        executeCommand(spoken)
    }

    // MICROPHONE CONTROL

    private fun toggleVoiceService() {

        if (voiceRunning) {

            try {
                val stopIntent = Intent(
                    this,
                    VoiceForegroundService::class.java
                ).apply {
                    action = VoiceForegroundService.ACTION_STOP
                }

                startService(stopIntent)

                voiceRunning = false
                assistantActive = false

                launcherView.setVoiceStatus(false)
                output.text = "Microphone बंद कर दिया गया।"

            } catch (e: Exception) {
                output.text = "Microphone error: ${e.message}"
            }

        } else {
            requestMicAndStart()
        }
    }

    private fun requestMicAndStart() {

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )

            return
        }

        startVoiceService()
    }

    private fun startVoiceService() {

        val intent = Intent(
            this,
            VoiceForegroundService::class.java
        )

        try {

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            voiceRunning = true

            launcherView.setVoiceStatus(true)

            output.text =
                "Microphone service शुरू करने का अनुरोध भेजा गया।"

        } catch (e: Exception) {

            voiceRunning = false

            launcherView.setLiveStatus(
                "MICROPHONE ERROR"
            )

            output.text = e.message ?: "Microphone error"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {

            startVoiceService()

        } else {

            launcherView.setLiveStatus(
                "MICROPHONE PERMISSION DENIED"
            )
        }
    }

    // APP CONTROL

    private fun openApp(target: String): Boolean {

        val packages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "telegram" to "org.telegram.messenger"
        )

        if (target == "settings") {

            startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS)
            )

            return true
        }

        val packageName = packages[target] ?: return false

        val launch = packageManager.getLaunchIntentForPackage(
            packageName
        ) ?: return false

        startActivity(launch)

        return true
    }

    // COMMAND ENGINE

    private fun executeCommand(text: String) {

        if (text.isBlank()) return

        launcherView.setLiveStatus(
            "● COMMAND RECEIVED"
        )

        output.text = "कमांड का विश्लेषण किया जा रहा है..."

        executor.execute {

            try {

                val py = Python.getInstance()
                val module = py.getModule("idraak_core")

                val classified = JSONObject(
                    module.callAttr(
                        "classify",
                        text
                    ).toString()
                )

                when (classified.getString("type")) {

                    "open_app" -> {

                        val target = classified.getString("target")

                        runOnUiThread {

                            launcherView.setLiveStatus(
                                "● LAUNCHING $target"
                            )

                            val success = try {
                                openApp(target)
                            } catch (_: Exception) {
                                false
                            }

                            if (success) {

                                output.text =
                                    "$target खोलने का अनुरोध Android ने स्वीकार किया।"

                                launcherView.setLiveStatus(
                                    "● ACTION ACCEPTED"
                                )

                                speak("$target खोलने का अनुरोध पूरा हुआ।")

                            } else {

                                output.text =
                                    "$target नहीं खुला। ऐप इंस्टॉल है या नहीं जाँचें।"

                                launcherView.setLiveStatus(
                                    "● ACTION FAILED"
                                )

                                speak("ऐप नहीं खुल सका।")
                            }
                        }
                    }

                    "idraak_settings" -> {

                        runOnUiThread {
                            openSettings()
                        }
                    }

                    else -> {

                        runOnUiThread {

                            launcherView.setLiveStatus(
                                "● GEMINI THINKING"
                            )
                        }

                        val response = JSONObject(
                            module.callAttr(
                                "reply",
                                text,
                                apiKey,
                                model
                            ).toString()
                        )

                        val answer = response.optString(
                            "message",
                            "जवाब उपलब्ध नहीं है।"
                        )

                        runOnUiThread {

                            output.text = answer

                            launcherView.setLiveStatus(
                                "● ${response.optString("status", "DONE").uppercase()}"
                            )

                            speak(answer)
                        }
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    launcherView.setLiveStatus(
                        "● SYSTEM ERROR"
                    )

                    output.text = e.toString()
                }
            }
        }
    }

    // TEXT TO SPEECH

    private fun speak(text: String) {

        if (!::tts.isInitialized) return

        tts.language = when (language) {

            "Hindi" -> Locale("hi", "IN")
            "Urdu" -> Locale("ur", "PK")
            "Arabic" -> Locale("ar")
            else -> Locale.US
        }

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "idraak_speech"
        )
    }

    override fun onInit(statusCode: Int) {

        if (statusCode == TextToSpeech.SUCCESS) {
            applyVoice()
        }
    }

    private fun applyVoice() {

        if (!::tts.isInitialized) return

        val locale = when (language) {

            "Hindi" -> Locale("hi", "IN")
            "Urdu" -> Locale("ur", "PK")
            "Arabic" -> Locale("ar")
            else -> Locale.US
        }

        tts.language = locale

        val voices = tts.voices?.filter {
            it.locale.language == locale.language
        } ?: emptyList()

        val selected = voices.firstOrNull {

            if (gender == "Female") {
                it.name.contains("female", true)
            } else {
                it.name.contains("male", true)
            }

        } ?: voices.firstOrNull()

        if (selected != null) {
            tts.voice = selected
        }
    }

    // IDRAAK SETTINGS

    private fun openSettings() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 15, 22, 15)
            setBackgroundColor(bg)
        }

        val key = EditText(this).apply {
            hint = "Gemini API Key"
            setText(apiKey)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }

        val modelInput = EditText(this).apply {
            hint = "Gemini model ID"
            setText(model)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }

        val lang = Spinner(this)

        val langs = listOf(
            "Hindi",
            "English",
            "Urdu",
            "Arabic"
        )

        lang.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            langs
        )

        lang.setSelection(
            langs.indexOf(language).coerceAtLeast(0)
        )

        val voice = Spinner(this)

        val genders = listOf(
            "Female",
            "Male"
        )

        voice.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genders
        )

        voice.setSelection(
            genders.indexOf(gender).coerceAtLeast(0)
        )

        layout.addView(
            label("IDRAAK SETTINGS", 20f)
        )

        layout.addView(key)
        layout.addView(modelInput)

        layout.addView(label("Language", 14f))
        layout.addView(lang)

        layout.addView(label("Avatar / Voice", 14f))
        layout.addView(voice)

        layout.addView(
            button("SAVE SETTINGS") {

                apiKey = key.text.toString().trim()

                model = modelInput.text.toString().trim()
                    .ifBlank { "gemini-2.5-flash" }

                language = lang.selectedItem.toString()
                gender = voice.selectedItem.toString()

                savePrefs()
                applyVoice()

                launcherView.setLiveStatus(
                    "● SETTINGS SAVED"
                )

                Toast.makeText(
                    this,
                    "IDRAAK Settings saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        val scroll = ScrollView(this)
        scroll.addView(layout)

        AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("CLOSE", null)
            .show()
    }

    override fun onResume() {
        super.onResume()

        if (::launcherView.isInitialized) {
            launcherView.reloadApps()
        }
    }

    override fun onDestroy() {

        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }

        if (::tts.isInitialized) {
            tts.shutdown()
        }

        executor.shutdownNow()

        super.onDestroy()
    }
}
