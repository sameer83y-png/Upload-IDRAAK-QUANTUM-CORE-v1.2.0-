
package com.idraak.quantum

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var launcherView: LauncherHomeView
    private lateinit var tts: TextToSpeech

    private val executor = Executors.newSingleThreadExecutor()

    private var apiKey = ""
    private var model = "gemini-2.5-flash"
    private var gender = "Female"
    private var language = "Hindi"

    private var assistantActive = false
    private var voiceRunning = false
    private var permissionRequestInProgress = false

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

        window.statusBarColor = Color.rgb(3, 7, 28)
        window.navigationBarColor = Color.rgb(3, 7, 28)

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
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            ensureVoicePermissionAndStart()
        }, 700)
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences("idraak", MODE_PRIVATE)

        apiKey = prefs.getString("key", "") ?: ""

        model = prefs.getString(
            "model",
            "gemini-2.5-flash"
        ) ?: "gemini-2.5-flash"

        gender = prefs.getString("gender", "Female") ?: "Female"
        language = prefs.getString("language", "Hindi") ?: "Hindi"
    }

    private fun savePrefs() {
        getSharedPreferences("idraak", MODE_PRIVATE).edit()
            .putString("key", apiKey)
            .putString("model", model)
            .putString("gender", gender)
            .putString("language", language)
            .apply()
    }

    // HOME SCREEN

    private fun buildUI() {

        launcherView = LauncherHomeView(
            this,
            onSettingsClick = {
                openSettings()
            },
            onVoiceClick = {
                toggleVoiceService()
            }
        )

        launcherView.setAvatarGender(gender)
        setContentView(launcherView)
    }

    // SPEECH NORMALIZATION

    private fun normalizeSpeech(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    // WAKE WORD SYSTEM

    private fun wakePrefixes(): List<String> {
        return listOf(
            "hey idraak",
            "hello idraak",
            "hi idraak",
            "hey इद्राक",
            "ओ idraak",
            "ओ इद्राक",
            "अरे idraak",
            "अरे इद्राक",
            "idraak",
            "इद्राक",
            "आईड्राक"
        )
    }

    private fun extractWakeCommand(text: String): String? {

        val normalized = normalizeSpeech(text)

        for (prefix in wakePrefixes()) {
            if (
                normalized == prefix ||
                normalized.startsWith("$prefix ")
            ) {
                return normalized.removePrefix(prefix).trim()
            }
        }

        return null
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

            launcherView.setLiveStatus("● IDRAAK STANDBY")
            speak("ठीक है। मैं Standby में जा रहा हूँ।")
            return
        }

        if (!assistantActive) {

            val command = extractWakeCommand(spoken)

            if (command != null) {
                assistantActive = true
                launcherView.setLiveStatus("● IDRAAK ACTIVE")

                if (command.isBlank()) {
                    speak("जी, मैं सक्रिय हूँ। बताइए!")
                } else {
                    speak("जी, बताइए। मैं काम शुरू कर रहा हूँ।")
                    executeCommand(command)
                }
            } else {
                launcherView.setLiveStatus("● IDRAAK STANDBY")
            }

            return
        }

        executeCommand(spoken)
    }

    // MICROPHONE PERMISSIONS

    private fun ensureVoicePermissionAndStart() {

        if (isFinishing || isDestroyed) return

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (!permissionRequestInProgress) {
                permissionRequestInProgress = true

                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    101
                )
            }
            return
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >= 33) {
            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    102
                )
                return
            }
        }

        startVoiceService()
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

        if (requestCode == 101) {
            permissionRequestInProgress = false

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionIfNeeded()
            } else {
                launcherView.setLiveStatus(
                    "● MICROPHONE PERMISSION REQUIRED"
                )

                Toast.makeText(
                    this,
                    "IDRAAK को Voice के लिए Microphone अनुमति चाहिए।",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        if (requestCode == 102) {
            startVoiceService()
        }
    }

    private fun startVoiceService() {

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            launcherView.setLiveStatus(
                "● MICROPHONE PERMISSION REQUIRED"
            )
            return
        }

        try {
            val intent = Intent(
                this,
                VoiceForegroundService::class.java
            ).apply {
                putExtra(
                    VoiceForegroundService.EXTRA_LANGUAGE,
                    language
                )
            }

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            voiceRunning = true
            launcherView.setVoiceStatus(true)
            launcherView.setLiveStatus("● IDRAAK VOICE STARTING")

        } catch (e: Exception) {
            voiceRunning = false
            launcherView.setLiveStatus("● VOICE START FAILED")

            Toast.makeText(
                this,
                "Voice service error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopVoiceService() {

        try {
            val intent = Intent(
                this,
                VoiceForegroundService::class.java
            ).apply {
                action = VoiceForegroundService.ACTION_STOP
            }

            startService(intent)
        } catch (_: Exception) {
        }

        voiceRunning = false
        assistantActive = false

        launcherView.setVoiceStatus(false)
        launcherView.setLiveStatus("● MICROPHONE STOPPED")
    }

    private fun restartVoiceService() {

        try {
            stopService(
                Intent(this, VoiceForegroundService::class.java)
            )
        } catch (_: Exception) {
        }

        voiceRunning = false
        assistantActive = false

        launcherView.setVoiceStatus(false)
        launcherView.setLiveStatus("● UPDATING VOICE LANGUAGE")

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                startVoiceService()
            }
        }, 700)
    }

    private fun toggleVoiceService() {
        if (voiceRunning) {
            stopVoiceService()
        } else {
            ensureVoicePermissionAndStart()
        }
    }

    // ACCESSIBILITY COMMAND ENGINE

    private fun handleAccessibilityCommand(text: String): Boolean {

        val command = normalizeSpeech(text)

        if (command.isBlank()) return false

        var actionName = ""
        var success = false
        var recognized = true

        when {

            command in listOf(
                "scroll down",
                "स्क्रॉल डाउन",
                "नीचे स्क्रॉल करो",
                "नीचे स्क्रॉल",
                "नीचे करो",
                "पेज नीचे करो"
            ) -> {
                actionName = "SCROLL DOWN"
                success = IDRAAKAccessibilityService.scrollDown()
            }

            command in listOf(
                "scroll up",
                "स्क्रॉल अप",
                "ऊपर स्क्रॉल करो",
                "ऊपर स्क्रॉल",
                "ऊपर करो",
                "पेज ऊपर करो"
            ) -> {
                actionName = "SCROLL UP"
                success = IDRAAKAccessibilityService.scrollUp()
            }

            command in listOf(
                "back",
                "go back",
                "वापस जाओ",
                "बैक जाओ",
                "पीछे जाओ",
                "वापस चलो"
            ) -> {
                actionName = "BACK"
                success = IDRAAKAccessibilityService.goBack()
            }

            command in listOf(
                "home",
                "go home",
                "होम",
                "होम स्क्रीन खोलो",
                "होम पर जाओ"
            ) -> {
                actionName = "HOME"
                success = IDRAAKAccessibilityService.goHome()
            }

            command in listOf(
                "open notifications",
                "notifications",
                "नोटिफिकेशन खोलो",
                "नोटिफिकेशन पैनल खोलो"
            ) -> {
                actionName = "NOTIFICATIONS"
                success = IDRAAKAccessibilityService.openNotifications()
            }

            command in listOf(
                "quick settings",
                "open quick settings",
                "क्विक सेटिंग खोलो",
                "क्विक सेटिंग्स खोलो"
            ) -> {
                actionName = "QUICK SETTINGS"
                success = IDRAAKAccessibilityService.openQuickSettings()
            }

            else -> {
                recognized = false
            }
        }

        // CLICK TEXT COMMANDS

        if (!recognized) {

            val clickSuffixes = listOf(
                "पर क्लिक करो",
                "पर क्लिक करें",
                "को क्लिक करो",
                "को क्लिक करें",
                "पर दबाओ",
                "को दबाओ",
                "क्लिक करो",
                "क्लिक करें",
                "दबाओ",
                "tap",
                "click"
            )

            var target = command

            for (suffix in clickSuffixes) {
                if (target.endsWith(suffix)) {
                    target = target.removeSuffix(suffix).trim()
                    recognized = true
                    break
                }
            }

            val clickPrefixes = listOf(
                "स्क्रीन पर ",
                "स्क्रीन में ",
                "बटन ",
                "क्लिक ",
                "click ",
                "tap "
            )

            for (prefix in clickPrefixes) {
                if (target.startsWith(prefix)) {
                    target = target.removePrefix(prefix).trim()
                }
            }

            if (recognized && target.isNotBlank()) {
                actionName = "CLICK $target"
                success = IDRAAKAccessibilityService.clickText(target)
            } else {
                recognized = false
            }
        }

        // TYPE TEXT COMMANDS

        if (!recognized) {

            val typePrefixes = listOf(
                "लिखो ",
                "लिख दो ",
                "यह लिखो ",
                "टाइप करो ",
                "type ",
                "write "
            )

            var typedText: String? = null

            for (prefix in typePrefixes) {
                if (command.startsWith(prefix)) {
                    typedText = command.removePrefix(prefix).trim()
                    break
                }
            }

            if (typedText != null && typedText.isNotBlank()) {
                recognized = true
                actionName = "TYPE TEXT"
                success = IDRAAKAccessibilityService.typeText(typedText)
            }
        }

        if (!recognized) return false

        launcherView.setLiveStatus("● EXECUTING $actionName")

        if (success) {
            launcherView.setLiveStatus("● ACTION ACCEPTED")
            speak("जी, कार्रवाई का अनुरोध भेज दिया गया है।")
        } else {
            launcherView.setLiveStatus("● ACTION NOT COMPLETED")

            if (!IDRAAKAccessibilityService.isConnected()) {
                speak(
                    "पहले फोन की Accessibility Settings में IDRAAK Service चालू करें।"
                )
            } else {
                speak(
                    "यह कार्रवाई अभी पूरी नहीं हो सकी। स्क्रीन पर विकल्प उपलब्ध नहीं हो सकता।"
                )
            }
        }

        return true
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
            startActivity(Intent(Settings.ACTION_SETTINGS))
            return true
        }

        val packageName = packages[target] ?: return false

        val launch = packageManager.getLaunchIntentForPackage(
            packageName
        ) ?: return false

        startActivity(launch)
        return true
    }

    // MAIN COMMAND ENGINE

    private fun executeCommand(text: String) {

        if (text.isBlank()) return

        if (handleAccessibilityCommand(text)) return

        launcherView.setLiveStatus("● COMMAND RECEIVED")

        executor.execute {

            try {
                val py = Python.getInstance()
                val module = py.getModule("idraak_core")

                val classified = JSONObject(
                    module.callAttr("classify", text).toString()
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
                                launcherView.setLiveStatus(
                                    "● ACTION ACCEPTED"
                                )
                                speak("$target खोलने का अनुरोध भेज दिया गया है।")
                            } else {
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
                            launcherView.setLiveStatus(
                                "● ${
                                    response.optString(
                                        "status",
                                        "DONE"
                                    ).uppercase()
                                }"
                            )
                            speak(answer)
                        }
                    }
                }

            } catch (_: Exception) {

                runOnUiThread {
                    launcherView.setLiveStatus("● SYSTEM ERROR")
                    speak("माफ कीजिए, कमांड पूरी नहीं हो सकी।")
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
                it.name.contains("male", true) &&
                    !it.name.contains("female", true)
            }
        } ?: voices.firstOrNull()

        if (selected != null) {
            tts.voice = selected
        }
    }

    // ACCESSIBILITY SETTINGS

    private fun openAccessibilitySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "फोन की Settings में Accessibility खोलें।",
                Toast.LENGTH_LONG
            ).show()
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

        val genders = listOf("Female", "Male")

        voice.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genders
        )

        voice.setSelection(
            genders.indexOf(gender).coerceAtLeast(0)
        )

        layout.addView(
            TextView(this).apply {
                text = "IDRAAK SETTINGS"
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
        )

        layout.addView(key)
        layout.addView(modelInput)

        layout.addView(
            TextView(this).apply {
                text = "Language"
                setTextColor(Color.WHITE)
            }
        )

        layout.addView(lang)

        layout.addView(
            TextView(this).apply {
                text = "Avatar / Voice"
                setTextColor(Color.WHITE)
            }
        )

        layout.addView(voice)

        val saveButton = Button(this).apply {
            text = "SAVE SETTINGS"

            setOnClickListener {

                val restartVoice = voiceRunning

                apiKey = key.text.toString().trim()

                model = modelInput.text.toString().trim()
                    .ifBlank { "gemini-2.5-flash" }

                language = lang.selectedItem.toString()
                gender = voice.selectedItem.toString()

                savePrefs()

                launcherView.setAvatarGender(gender)
                applyVoice()

                launcherView.setLiveStatus("● SETTINGS SAVED")

                if (restartVoice) {
                    restartVoiceService()
                }

                Toast.makeText(
                    this@MainActivity,
                    "IDRAAK Settings saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        layout.addView(saveButton)

        val accessibilityButton = Button(this).apply {
            text = "ENABLE ACCESSIBILITY SERVICE"

            setOnClickListener {
                openAccessibilitySettings()
            }
        }

        layout.addView(accessibilityButton)

        val voiceButton = Button(this).apply {
            text = if (voiceRunning) {
                "STOP MICROPHONE"
            } else {
                "START MICROPHONE"
            }

            setOnClickListener {
                toggleVoiceService()

                this.text = if (voiceRunning) {
                    "STOP MICROPHONE"
                } else {
                    "START MICROPHONE"
                }
            }
        }

        layout.addView(voiceButton)

        val homeButton = Button(this).apply {
            text = "SET IDRAAK AS DEFAULT HOME"

            setOnClickListener {
                try {
                    startActivity(
                        Intent(Settings.ACTION_HOME_SETTINGS)
                    )
                } catch (_: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Home settings फोन की Settings में चुनें।",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        layout.addView(homeButton)

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
            launcherView.setAvatarGender(gender)

            if (!voiceRunning) {
                ensureVoicePermissionAndStart()
            }
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
