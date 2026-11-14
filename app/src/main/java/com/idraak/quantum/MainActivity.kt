
package com.idraak.quantum

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        private const val REQUEST_AUDIO = 101
        private const val REQUEST_CAMERA = 102

        private const val ACTION_VOICE_RESULT =
            "com.idraak.quantum.VOICE_RESULT"

        private const val ACTION_VOICE_STOP =
            "com.idraak.quantum.STOP_VOICE"

        private const val EXTRA_VOICE_TEXT =
            "recognized_text"

        private const val EXTRA_VOICE_LANGUAGE =
            "idraak_language"
    }

    private lateinit var homeView: LauncherHomeView
    private lateinit var tts: TextToSpeech

    private lateinit var wakeWordController: WakeWordController
    private lateinit var softwareController: SoftwareController
    private lateinit var screenController: ScreenController
    private lateinit var systemController: SystemController
    private lateinit var hardwareController: HardwareController

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var apiKey = ""
    private var model = "gemini-2.5-flash"
    private var language = "Hindi"
    private var gender = "Male"

    private var permissionGranted = false
    private var receiverRegistered = false
    private var voiceServiceStarted = false
    private var ttsReady = false
    private var speaking = false
    private var pendingSpeech: String? = null

    private val prefs by lazy {
        getSharedPreferences("idraak", MODE_PRIVATE)
    }

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_VOICE_RESULT) return

            val recognizedText = intent.getStringExtra(EXTRA_VOICE_TEXT)
                ?: return

            if (recognizedText.isNotBlank() && !speaking) {
                wakeWordController.processRecognizedText(recognizedText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(3, 7, 28)
        window.navigationBarColor = Color.rgb(3, 7, 28)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        loadSettings()

        softwareController = SoftwareController(this)
        screenController = ScreenController()
        systemController = SystemController(this)
        hardwareController = HardwareController(this)

        setupWakeWordController()
        registerVoiceReceiver()

        tts = TextToSpeech(this, this)

        buildInterface()
        checkAudioPermission()
    }

    // -------------------------------------------------
    // SETTINGS
    // -------------------------------------------------

    private fun loadSettings() {
        apiKey = prefs.getString("key", "") ?: ""

        model = prefs.getString(
            "model",
            "gemini-2.5-flash"
        ) ?: "gemini-2.5-flash"

        language = prefs.getString("language", "Hindi") ?: "Hindi"
        gender = prefs.getString("gender", "Male") ?: "Male"
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("key", apiKey)
            .putString("model", model)
            .putString("language", language)
            .putString("gender", gender)
            .apply()
    }

    // -------------------------------------------------
    // MAIN LAUNCHER INTERFACE
    // -------------------------------------------------

    private fun buildInterface() {
        homeView = LauncherHomeView(
            this,
            onSettingsClick = {
                openSettings()
            },
            onVoiceClick = {
                // Voice is controlled by the IDRAAK wake word.
                // No visible voice button is added.
            }
        )

        homeView.setAvatarGender(gender)
        homeView.setLiveStatus("IDRAAK SYSTEM INITIALIZING")
        homeView.setVoiceStatus(false)

        setContentView(homeView)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    // -------------------------------------------------
    // WAKE WORD CONTROLLER
    // -------------------------------------------------

    private fun setupWakeWordController() {
        wakeWordController = WakeWordController(
            {
                runOnUiThread {
                    homeView.setLiveStatus("IDRAAK ACTIVE")
                    homeView.setVoiceStatus(true)
                    speak("जी, मैं सक्रिय हूँ। बताइए।")
                }
            },
            { command ->
                runOnUiThread {
                    executeCommand(command)
                }
            },
            {
                runOnUiThread {
                    homeView.setLiveStatus("IDRAAK STANDBY")
                    homeView.setVoiceStatus(false)
                    speak("ठीक है। मैं Standby में जा रहा हूँ।")
                }
            },
            { state ->
                runOnUiThread {
                    when (state.toString()) {
                        "SLEEPING" -> {
                            homeView.setLiveStatus("IDRAAK STANDBY")
                            homeView.setVoiceStatus(false)
                        }

                        "ACTIVE" -> {
                            homeView.setLiveStatus("IDRAAK ACTIVE")
                            homeView.setVoiceStatus(true)
                        }

                        "PROCESSING" -> {
                            homeView.setLiveStatus("IDRAAK THINKING")
                            homeView.setVoiceStatus(true)
                        }
                    }
                }
            }
        )
    }

    // -------------------------------------------------
    // VOICE FOREGROUND SERVICE
    // -------------------------------------------------

    private fun registerVoiceReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(ACTION_VOICE_RESULT)

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    voiceReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(voiceReceiver, filter)
            }

            receiverRegistered = true
        } catch (_: Exception) {
            receiverRegistered = false
        }
    }

    private fun startVoiceService() {
        if (!permissionGranted || voiceServiceStarted) return

        val serviceIntent = Intent(
            this,
            VoiceForegroundService::class.java
        ).apply {
            putExtra(
                EXTRA_VOICE_LANGUAGE,
                selectedLocale().toLanguageTag()
            )
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            voiceServiceStarted = true
            homeView.setVoiceStatus(true)
            homeView.setLiveStatus("IDRAAK STANDBY • LISTENING")
        } catch (_: Exception) {
            voiceServiceStarted = false
            homeView.setLiveStatus("VOICE SERVICE ERROR")
        }
    }

    private fun stopVoiceService() {
        if (!voiceServiceStarted) return

        try {
            val stopIntent = Intent(
                this,
                VoiceForegroundService::class.java
            ).apply {
                action = ACTION_VOICE_STOP
            }

            startService(stopIntent)
        } catch (_: Exception) {
        }

        voiceServiceStarted = false
    }

    // -------------------------------------------------
    // MICROPHONE PERMISSION
    // -------------------------------------------------

    private fun checkAudioPermission() {
        permissionGranted =
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            startVoiceService()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO
            )
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

        when (requestCode) {
            REQUEST_AUDIO -> {
                permissionGranted =
                    grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED

                if (permissionGranted) {
                    startVoiceService()
                } else {
                    homeView.setLiveStatus("MICROPHONE PERMISSION REQUIRED")
                    homeView.setVoiceStatus(false)
                }
            }

            REQUEST_CAMERA -> {
                val granted =
                    grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    hardwareController.setFlashlight(true)
                    speak("फ्लैशलाइट चालू कर रहा हूँ।")
                } else {
                    speak("कैमरा अनुमति नहीं मिली।")
                }
            }
        }
    }

    // -------------------------------------------------
    // COMMAND PROCESSING
    // -------------------------------------------------

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun executeCommand(rawCommand: String) {
        val command = rawCommand.trim()
        if (command.isBlank()) return

        wakeWordController.startProcessing()
        homeView.setLiveStatus("IDRAAK • PROCESSING")

        if (handlePhoneCommand(command)) return

        askGemini(command)
    }

    private fun handlePhoneCommand(rawText: String): Boolean {
        val command = normalize(rawText)

        // APP LAUNCH
        val openPrefixes = listOf(
            "open ",
            "launch ",
            "खोलो ",
            "खोल ",
            "चालू करो "
        )

        val openPrefix = openPrefixes.firstOrNull {
            command.startsWith(it)
        }

        if (openPrefix != null) {
            val appName = command.removePrefix(openPrefix).trim()

            if (appName.isNotBlank()) {
                val launched = invokeController(
                    softwareController,
                    listOf("openAppByName"),
                    arrayOf(appName)
                )

                if (launched) {
                    speak("जी, ऐप खोलने की कोशिश कर रहा हूँ।")
                } else {
                    speak("मैं इस ऐप को नहीं खोल सका।")
                }
            }
            return true
        }

        // APP DRAWER
        if (
            command in listOf(
                "open app drawer",
                "show apps",
                "ऐप ड्रॉअर खोलो",
                "सभी ऐप दिखाओ",
                "ऐप दिखाओ"
            )
        ) {
            homeView.openAppDrawer()
            speak("आपके ऐप्स स्क्रीन पर हैं।")
            return true
        }

        if (
            command in listOf(
                "close app drawer",
                "बंद करो ऐप ड्रॉअर",
                "होम पर जाओ"
            )
        ) {
            homeView.closeAppDrawer()
            speak("जी।")
            return true
        }

        // SCREEN READING
        if (
            command in listOf(
                "read screen",
                "read this screen",
                "स्क्रीन पढ़ो",
                "स्क्रीन पर क्या लिखा है"
            )
        ) {
            if (!screenController.isAccessibilityEnabled()) {
                speak("स्क्रीन पढ़ने के लिए IDRAAK Accessibility Service चालू करें।")
            } else {
                val visibleText = screenController.readScreen()
                speak(
                    visibleText.ifBlank {
                        "मुझे स्क्रीन पर पढ़ने योग्य टेक्स्ट नहीं मिला।"
                    }
                )
            }
            return true
        }

        // SCREEN SCROLL
        if (
            command in listOf(
                "scroll down",
                "नीचे स्क्रॉल करो",
                "नीचे जाओ"
            )
        ) {
            val done = screenController.scrollDown()
            speak(if (done) "नीचे स्क्रॉल कर दिया।" else "स्क्रॉल नहीं हो सका।")
            return true
        }

        if (
            command in listOf(
                "scroll up",
                "ऊपर स्क्रॉल करो",
                "ऊपर जाओ"
            )
        ) {
            val done = screenController.scrollUp()
            speak(if (done) "ऊपर स्क्रॉल कर दिया।" else "स्क्रॉल नहीं हो सका।")
            return true
        }

        // BACK / HOME
        if (command in listOf("go back", "back", "वापस जाओ", "पीछे जाओ")) {
            val done = screenController.goBack()
            speak(if (done) "जी।" else "यह काम नहीं हो सका।")
            return true
        }

        if (command in listOf("go home", "home", "होम स्क्रीन खोलो")) {
            val done = screenController.goHome()
            speak(if (done) "होम स्क्रीन खोल रहा हूँ।" else "होम नहीं खुल सका।")
            return true
        }

        // NOTIFICATIONS / QUICK SETTINGS
        if (
            command in listOf(
                "open notifications",
                "show notifications",
                "नोटिफिकेशन खोलो"
            )
        ) {
            val done = screenController.openNotifications()
            speak(if (done) "नोटिफिकेशन खोल रहा हूँ।" else "नोटिफिकेशन नहीं खुल सके।")
            return true
        }

        if (
            command in listOf(
                "open quick settings",
                "quick settings",
                "क्विक सेटिंग खोलो"
            )
        ) {
            val done = screenController.openQuickSettings()
            speak(if (done) "क्विक सेटिंग खोल रहा हूँ।" else "क्विक सेटिंग नहीं खुल सकी।")
            return true
        }

        // TEXT INPUT
        if (
            command.startsWith("type ") ||
            command.startsWith("लिखो ")
        ) {
            val textToType = when {
                command.startsWith("type ") ->
                    rawText.substringAfter(" ", "")

                command.startsWith("लिखो ") ->
                    rawText.substringAfter(" ", "")

                else -> ""
            }

            val done = screenController.typeText(textToType)
            speak(if (done) "टेक्स्ट लिख दिया।" else "टेक्स्ट लिखने के लिए कोई इनपुट बॉक्स नहीं मिला।")
            return true
        }

        // FLASHLIGHT
        if (
            command in listOf(
                "turn on flashlight",
                "flashlight on",
                "torch on",
                "टॉर्च चालू करो",
                "फ्लैशलाइट चालू करो"
            )
        ) {
            if (checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                val done = hardwareController.setFlashlight(true)
                speak(if (done) "फ्लैशलाइट चालू है।" else "फ्लैशलाइट चालू नहीं हो सकी।")
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA
                )
            }
            return true
        }

        if (
            command in listOf(
                "turn off flashlight",
                "flashlight off",
                "torch off",
                "टॉर्च बंद करो",
                "फ्लैशलाइट बंद करो"
            )
        ) {
            val done = hardwareController.setFlashlight(false)
            speak(if (done) "फ्लैशलाइट बंद कर दी।" else "फ्लैशलाइट बंद नहीं हो सकी।")
            return true
        }

        // CAMERA
        if (
            command in listOf(
                "open camera",
                "camera kholo",
                "कैमरा खोलो"
            )
        ) {
            val done = hardwareController.openCamera()
            speak(if (done) "कैमरा खोल रहा हूँ।" else "कैमरा नहीं खुल सका।")
            return true
        }

        // SETTINGS
        if (
            command in listOf(
                "settings",
                "open settings",
                "फोन सेटिंग खोलो",
                "सेटिंग खोलो"
            )
        ) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            speak("फोन की सेटिंग खोल रहा हूँ।")
            return true
        }

        if (
            command in listOf(
                "open wifi settings",
                "वाईफाई सेटिंग खोलो"
            )
        ) {
            invokeController(
                systemController,
                listOf("openInternetSettings", "openWifiSettings")
            )
            speak("वाईफाई सेटिंग खोल रहा हूँ।")
            return true
        }

        if (
            command in listOf(
                "open bluetooth settings",
                "ब्लूटूथ सेटिंग खोलो"
            )
        ) {
            invokeController(
                systemController,
                listOf("openBluetoothSettings")
            )
            speak("ब्लूटूथ सेटिंग खोल रहा हूँ।")
            return true
        }

        if (
            command in listOf(
                "open brightness settings",
                "ब्राइटनेस सेटिंग खोलो"
            )
        ) {
            invokeController(
                systemController,
                listOf("openDisplaySettings")
            )
            speak("डिस्प्ले सेटिंग खोल रहा हूँ।")
            return true
        }

        return false
    }

    // -------------------------------------------------
    // CONTROLLER METHOD BRIDGE
    // -------------------------------------------------

    /*
     * This bridge checks the actual public method at runtime.
     * It avoids assuming a return type for controller methods.
     */
    private fun invokeController(
        controller: Any,
        methodNames: List<String>,
        args: Array<Any> = emptyArray()
    ): Boolean {
        for (methodName in methodNames) {
            try {
                val method = controller.javaClass.methods.firstOrNull {
                    it.name == methodName &&
                        it.parameterTypes.size == args.size
                } ?: continue

                val result = method.invoke(controller, *args)
                return result as? Boolean ?: true
            } catch (_: Exception) {
            }
        }
        return false
    }

    // -------------------------------------------------
    // GEMINI AI
    // -------------------------------------------------

    private fun askGemini(prompt: String) {
        val currentKey = apiKey
        val currentModel = model

        if (currentKey.isBlank()) {
            speak("Gemini API Key दर्ज करने के लिए Settings खोलें।")
            return
        }

        executor.execute {
            try {
                val screenText = if (screenController.isAccessibilityEnabled()) {
                    screenController.readScreen().take(3000)
                } else {
                    ""
                }

                val answer = askMayaAI(
                    prompt,
                    currentKey,
                    currentModel,
                    screenText
                )

                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        homeView.setLiveStatus("IDRAAK • RESPONSE")
                        speak(answer)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        speak("माफ कीजिए, AI से कनेक्शन नहीं हो सका।")
                    }
                }
            }
        }
    }

    private fun askMayaAI(
        prompt: String,
        key: String,
        modelId: String,
        screenText: String
    ): String {
        val safeModel = modelId.trim().ifBlank {
            "gemini-2.5-flash"
        }

        val encodedKey = URLEncoder.encode(key, "UTF-8")

        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "$safeModel:generateContent?key=$encodedKey"
        )

        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            val systemPrompt = """
                You are IDRAAK, an advanced Android personal AI assistant.

                You communicate in the user's selected language: $language.
                Understand Hindi, English, Urdu and Arabic.

                You can use Android controllers for supported actions.
                Never claim an action was completed unless the Android app
                actually confirmed that action.

                If visible screen text is supplied, use it only to answer
                the user's current question about the screen.

                Visible screen text:
                $screenText
            """.trimIndent()

            val body = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", systemPrompt)
                        )
                    )
                )

                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", prompt)
                                )
                            )
                        }
                    )
                )

                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.7)
                        put("maxOutputTokens", 2048)
                    }
                )
            }

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode

            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream.bufferedReader().use {
                it.readText()
            }

            if (responseCode !in 200..299) {
                throw Exception("Gemini HTTP $responseCode")
            }

            val json = JSONObject(responseText)
            val candidates = json.optJSONArray("candidates")
                ?: throw Exception("No AI response")

            if (candidates.length() == 0) {
                throw Exception("Empty AI response")
            }

            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .optJSONArray("parts")
                ?: throw Exception("No response text")

            val result = StringBuilder()

            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i)
                val text = part?.optString("text", "") ?: ""

                if (text.isNotBlank()) {
                    result.append(text)
                }
            }

            return result.toString().trim().ifBlank {
                "मुझे अभी जवाब नहीं मिला।"
            }
        } finally {
            connection.disconnect()
        }
    }

    // -------------------------------------------------
    // TEXT TO SPEECH
    // -------------------------------------------------

    private fun selectedLocale(): Locale {
        return when (language) {
            "Hindi" -> Locale("hi", "IN")
            "English" -> Locale.US
            "Urdu" -> Locale("ur", "PK")
            "Arabic" -> Locale("ar")
            else -> Locale.US
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            applyVoice()
            setupTtsListener()

            pendingSpeech?.let {
                pendingSpeech = null
                speak(it)
            }
        } else {
            ttsReady = false
        }
    }

    private fun applyVoice() {
        if (!ttsReady) return

        val locale = selectedLocale()
        val availability = tts.isLanguageAvailable(locale)

        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = locale
        } else {
            tts.language = Locale.US
        }

        val voices = tts.voices?.filter {
            it.locale.language == locale.language
        } ?: emptyList()

        val selectedVoice = voices.firstOrNull {
            if (gender == "Female") {
                it.name.contains("female", true)
            } else {
                it.name.contains("male", true) &&
                    !it.name.contains("female", true)
            }
        } ?: voices.firstOrNull()

        if (selectedVoice != null) {
            tts.voice = selectedVoice
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return

        if (!ttsReady) {
            pendingSpeech = text
            return
        }

        speaking = true
        stopVoiceService()

        homeView.setLiveStatus("IDRAAK • SPEAKING")
        homeView.setVoiceStatus(false)

        applyVoice()

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "idraak_response"
        )
    }

    private fun setupTtsListener() {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        speaking = false
                        wakeWordController.finishProcessing()

                        if (!isFinishing && !isDestroyed) {
                            homeView.setLiveStatus(
                                if (wakeWordController.isActive()) {
                                    "IDRAAK ACTIVE"
                                } else {
                                    "IDRAAK STANDBY"
                                }
                            )

                            startVoiceService()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        speaking = false
                        wakeWordController.finishProcessing()
                        startVoiceService()
                    }
                }
            }
        )
    }

    // -------------------------------------------------
    // SETTINGS DIALOG
    // -------------------------------------------------

    private fun openSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.rgb(3, 7, 28))
        }

        val title = TextView(this).apply {
            text = "IDRAAK • SETTINGS"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.CYAN)
        }

        val keyInput = EditText(this).apply {
            hint = "Gemini API Key"
            setSingleLine(true)
            setText(apiKey)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }

        val modelInput = EditText(this).apply {
            hint = "Gemini Model ID"
            setSingleLine(true)
            setText(model)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }

        val languages = listOf("Hindi", "English", "Urdu", "Arabic")
        val languageSpinner = Spinner(this)

        languageSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages
        )

        languageSpinner.setSelection(
            languages.indexOf(language).coerceAtLeast(0)
        )

        val genders = listOf("Male", "Female")
        val genderSpinner = Spinner(this)

        genderSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genders
        )

        genderSpinner.setSelection(
            genders.indexOf(gender).coerceAtLeast(0)
        )

        layout.addView(title)
        layout.addView(keyInput)
        layout.addView(modelInput)

        layout.addView(TextView(this).apply {
            text = "LANGUAGE"
            setTextColor(Color.CYAN)
        })
        layout.addView(languageSpinner)

        layout.addView(TextView(this).apply {
            text = "VOICE"
            setTextColor(Color.CYAN)
        })
        layout.addView(genderSpinner)

        AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                apiKey = keyInput.text.toString().trim()

                model = modelInput.text.toString().trim()
                    .ifBlank { "gemini-2.5-flash" }

                language = languageSpinner.selectedItem.toString()
                gender = genderSpinner.selectedItem.toString()

                saveSettings()
                applyVoice()

                homeView.setAvatarGender(gender)

                // Restart the service so it uses the new recognition language.
                stopVoiceService()
                startVoiceService()

                Toast.makeText(
                    this,
                    "IDRAAK SETTINGS SAVED",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    // -------------------------------------------------
    // LIFECYCLE
    // -------------------------------------------------

    override fun onResume() {
        super.onResume()

        if (permissionGranted && !voiceServiceStarted) {
            startVoiceService()
        }

        homeView.reloadApps()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        if (receiverRegistered) {
            try {
                unregisterReceiver(voiceReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }

        try {
            hardwareController.release()
        } catch (_: Exception) {
        }

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        executor.shutdownNow()

        // Do not stop the foreground voice service here.
        // It is designed to continue under Android's foreground-service rules.

        super.onDestroy()
    }
}
