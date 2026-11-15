
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
import android.util.Log
import android.view.Gravity
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

class MainActivity : Activity() {

    companion object {
        private const val TAG = "IDRAAK_MAIN"
        private const val REQUEST_AUDIO = 101
        private const val REQUEST_CAMERA = 102
    }

    private lateinit var homeView: LauncherHomeView
    private lateinit var voiceManager: VoiceManager
    private lateinit var voiceEngine: VoiceEngine

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
    private var speaking = false

    private var lastVoiceError: String? = null

    private val wakeAcknowledgement = Runnable {
        if (
            !isFinishing &&
            !isDestroyed &&
            wakeWordController.isActive() &&
            wakeWordController.getState() ==
                WakeWordController.VoiceState.ACTIVE
        ) {
            speak("जी, मैं सक्रिय हूँ। बताइए।")
        }
    }

    private val prefs by lazy {
        getSharedPreferences("idraak", MODE_PRIVATE)
    }

    private val voiceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            when (intent?.action) {

                VoiceForegroundService.ACTION_RESULT -> {

                    val recognizedText =
                        intent.getStringExtra(
                            VoiceForegroundService.EXTRA_TEXT
                        ) ?: return

                    Log.d(TAG, "Voice result received")

                    if (
                        recognizedText.isNotBlank() &&
                        !speaking &&
                        !isFinishing &&
                        !isDestroyed
                    ) {
                        wakeWordController.processRecognizedText(
                            recognizedText
                        )
                    }
                }

                VoiceForegroundService.ACTION_ERROR -> {

                    val errorCode = intent.getIntExtra(
                        VoiceForegroundService.EXTRA_ERROR_CODE,
                        -999
                    )

                    val errorMessage =
                        intent.getStringExtra(
                            VoiceForegroundService.EXTRA_ERROR_MESSAGE
                        ) ?: "Unknown voice recognition error"

                    lastVoiceError = errorMessage

                    Log.e(
                        TAG,
                        "Voice service error: code=$errorCode, message=$errorMessage"
                    )

                    if (!speaking && !isFinishing && !isDestroyed) {
                        homeView.setVoiceStatus(false)
                        homeView.setLiveStatus(
                            "VOICE ERROR • ${errorMessage.take(65)}"
                        )
                    }
                }

                VoiceForegroundService.ACTION_STATE -> {

                    val userStopped = intent.getBooleanExtra(
                        VoiceForegroundService.EXTRA_USER_STOPPED,
                        false
                    )

                    val running = intent.getBooleanExtra(
                        VoiceForegroundService.EXTRA_RUNNING,
                        false
                    )

                    if (running) {

                        voiceServiceStarted = true
                        lastVoiceError = null

                        if (!speaking) {
                            homeView.setVoiceStatus(true)

                            if (
                                wakeWordController.getState() ==
                                WakeWordController.VoiceState.SLEEPING
                            ) {
                                homeView.setLiveStatus(
                                    "IDRAAK STANDBY • LISTENING"
                                )
                            }
                        }

                    } else if (userStopped) {

                        voiceServiceStarted = false

                        if (!speaking) {
                            homeView.setVoiceStatus(false)
                            homeView.setLiveStatus(
                                "IDRAAK VOICE PAUSED"
                            )
                        }

                    } else if (!speaking) {

                        voiceServiceStarted = false

                        homeView.setVoiceStatus(false)

                        val error = lastVoiceError

                        if (!error.isNullOrBlank()) {
                            homeView.setLiveStatus(
                                "VOICE ERROR • ${error.take(65)}"
                            )
                        } else {
                            homeView.setLiveStatus(
                                "VOICE SERVICE NOT READY"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(3, 7, 28)
        window.navigationBarColor = Color.rgb(3, 7, 28)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        loadSettings()

        voiceManager = VoiceManager(this)

        softwareController = SoftwareController(this)
        screenController = ScreenController()
        systemController = SystemController(this)
        hardwareController = HardwareController(this)

        setupWakeWordController()
        buildInterface()
        registerVoiceReceiver()

        // VoiceEngine owns TextToSpeech.
        // VoiceManager continues to control the foreground service.
        voiceEngine = VoiceEngine(
            this,
            voiceManager,
            object : VoiceEngine.Listener {

                override fun onEngineReady() {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        voiceEngine.setLanguage(language)
                        voiceEngine.setVoiceGender(gender)

                        Log.d(TAG, "VoiceEngine ready")
                    }
                }

                override fun onSpeechStarted() {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        speaking = true

                        homeView.setVoiceStatus(false)
                        homeView.setLiveStatus(
                            "IDRAAK • SPEAKING"
                        )
                    }
                }

                override fun onSpeechCompleted() {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        speaking = false

                        wakeWordController.finishProcessing()

                        updateVoiceInterfaceAfterSpeech()
                    }
                }

                override fun onSpeechError(message: String) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread

                        speaking = false
                        lastVoiceError = message

                        Log.e(TAG, "VoiceEngine TTS error: $message")

                        wakeWordController.finishProcessing()

                        homeView.setLiveStatus(
                            "VOICE OUTPUT ERROR • ${message.take(45)}"
                        )

                        homeView.setVoiceStatus(false)
                    }
                }

                override fun onVoiceStateChanged(
                    state: VoiceEngine.EngineState
                ) {
                    Log.d(TAG, "VoiceEngine state: $state")
                }
            }
        )

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

        language = prefs.getString(
            "language",
            "Hindi"
        ) ?: "Hindi"

        gender = prefs.getString(
            "gender",
            "Male"
        ) ?: "Male"
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
                // Voice activation is controlled by the wake word.
            }
        )

        homeView.setAvatarGender(gender)

        homeView.setLiveStatus(
            "IDRAAK SYSTEM INITIALIZING"
        )

        homeView.setVoiceStatus(false)

        setContentView(homeView)

        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    // -------------------------------------------------
    // WAKE WORD CONTROLLER
    // -------------------------------------------------

    private fun setupWakeWordController() {

        wakeWordController = WakeWordController(

            {
                runOnUiThread {

                    if (isFinishing || isDestroyed) return@runOnUiThread

                    handler.removeCallbacks(wakeAcknowledgement)

                    homeView.setLiveStatus("IDRAAK ACTIVE")
                    homeView.setVoiceStatus(true)

                    handler.postDelayed(
                        wakeAcknowledgement,
                        300L
                    )
                }
            },

            { command ->

                runOnUiThread {

                    if (isFinishing || isDestroyed) return@runOnUiThread

                    handler.removeCallbacks(wakeAcknowledgement)

                    executeCommand(command)
                }
            },

            {
                runOnUiThread {

                    handler.removeCallbacks(wakeAcknowledgement)

                    if (isFinishing || isDestroyed) return@runOnUiThread

                    homeView.setLiveStatus("IDRAAK STANDBY")
                    homeView.setVoiceStatus(false)

                    speak(
                        "ठीक है। मैं Standby में जा रहा हूँ।"
                    )
                }
            },

            { state ->

                runOnUiThread {

                    if (isFinishing || isDestroyed) return@runOnUiThread

                    when (state) {

                        WakeWordController.VoiceState.SLEEPING -> {
                            homeView.setLiveStatus(
                                "IDRAAK STANDBY"
                            )
                            homeView.setVoiceStatus(false)
                        }

                        WakeWordController.VoiceState.ACTIVE -> {
                            homeView.setLiveStatus(
                                "IDRAAK ACTIVE"
                            )
                            homeView.setVoiceStatus(true)
                        }

                        WakeWordController.VoiceState.PROCESSING -> {
                            homeView.setLiveStatus(
                                "IDRAAK THINKING"
                            )
                            homeView.setVoiceStatus(true)
                        }
                    }
                }
            }
        )
    }

    // -------------------------------------------------
    // VOICE RECEIVER
    // -------------------------------------------------

    private fun registerVoiceReceiver() {

        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(
                VoiceForegroundService.ACTION_RESULT
            )

            addAction(
                VoiceForegroundService.ACTION_STATE
            )

            addAction(
                VoiceForegroundService.ACTION_ERROR
            )
        }

        try {

            if (Build.VERSION.SDK_INT >= 33) {

                registerReceiver(
                    voiceReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )

            } else {

                @Suppress("DEPRECATION")
                registerReceiver(
                    voiceReceiver,
                    filter
                )
            }

            receiverRegistered = true

            Log.d(TAG, "Voice receiver registered")

        } catch (e: Exception) {

            receiverRegistered = false

            Log.e(TAG, "Voice receiver registration failed", e)
        }
    }

    // -------------------------------------------------
    // VOICE ENGINE INTEGRATION
    // -------------------------------------------------

    private fun startVoiceService() {

        if (!permissionGranted || voiceServiceStarted) return

        try {

            lastVoiceError = null

            voiceEngine.startListening(
                selectedLocale().toLanguageTag()
            )

            voiceServiceStarted = true

            homeView.setLiveStatus(
                "IDRAAK VOICE STARTING"
            )

        } catch (e: Exception) {

            voiceServiceStarted = false

            Log.e(TAG, "VoiceEngine start failed", e)

            homeView.setVoiceStatus(false)
            homeView.setLiveStatus(
                "VOICE SERVICE ERROR"
            )
        }
    }

    private fun resumeVoiceService() {

        if (!permissionGranted) return

        try {

            lastVoiceError = null

            voiceEngine.resumeListening()

            voiceServiceStarted = true

        } catch (e: Exception) {

            voiceServiceStarted = false

            Log.e(TAG, "VoiceEngine resume failed", e)

            homeView.setVoiceStatus(false)
            homeView.setLiveStatus(
                "VOICE RESUME ERROR"
            )
        }
    }

    private fun stopVoiceService() {

        try {
            voiceEngine.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "VoiceEngine stop failed", e)
        }

        voiceServiceStarted = false
    }

    // -------------------------------------------------
    // MICROPHONE PERMISSION
    // -------------------------------------------------

    private fun checkAudioPermission() {

        permissionGranted =
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

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
                        grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED

                if (permissionGranted) {

                    startVoiceService()

                } else {

                    homeView.setLiveStatus(
                        "MICROPHONE PERMISSION REQUIRED"
                    )

                    homeView.setVoiceStatus(false)
                }
            }

            REQUEST_CAMERA -> {

                val granted =
                    grantResults.isNotEmpty() &&
                        grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED

                if (granted) {

                    val done =
                        hardwareController.setFlashlight(true)

                    speak(
                        if (done) {
                            "फ्लैशलाइट चालू कर रहा हूँ।"
                        } else {
                            "फ्लैशलाइट चालू नहीं हो सकी।"
                        }
                    )

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
            .replace(
                Regex("[^\\p{L}\\p{N}\\s]"),
                ""
            )
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun executeCommand(rawCommand: String) {

        val command = rawCommand.trim()

        if (command.isBlank()) return

        handler.removeCallbacks(wakeAcknowledgement)

        wakeWordController.startProcessing()

        homeView.setLiveStatus(
            "IDRAAK • PROCESSING"
        )

        if (handlePhoneCommand(command)) return

        askGemini(command)
    }

    private fun handlePhoneCommand(
        rawText: String
    ): Boolean {

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

            val appName = command
                .removePrefix(openPrefix)
                .trim()

            if (appName.isNotBlank()) {

                val launched = invokeController(
                    softwareController,
                    listOf("openAppByName"),
                    arrayOf(appName)
                )

                speak(
                    if (launched) {
                        "जी, ऐप खोलने की कोशिश कर रहा हूँ।"
                    } else {
                        "मैं इस ऐप को नहीं खोल सका।"
                    }
                )
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

                speak(
                    "स्क्रीन पढ़ने के लिए IDRAAK Accessibility Service चालू करें।"
                )

            } else {

                val visibleText =
                    screenController.readScreen()

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

            speak(
                if (done) {
                    "नीचे स्क्रॉल कर दिया।"
                } else {
                    "स्क्रॉल नहीं हो सका।"
                }
            )

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

            speak(
                if (done) {
                    "ऊपर स्क्रॉल कर दिया।"
                } else {
                    "स्क्रॉल नहीं हो सका।"
                }
            )

            return true
        }

        // BACK / HOME

        if (
            command in listOf(
                "go back",
                "back",
                "वापस जाओ",
                "पीछे जाओ"
            )
        ) {

            val done = screenController.goBack()

            speak(
                if (done) "जी।"
                else "यह काम नहीं हो सका।"
            )

            return true
        }

        if (
            command in listOf(
                "go home",
                "home",
                "होम स्क्रीन खोलो"
            )
        ) {

            val done = screenController.goHome()

            speak(
                if (done) {
                    "होम स्क्रीन खोल रहा हूँ।"
                } else {
                    "होम नहीं खुल सका।"
                }
            )

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

            val done =
                screenController.openNotifications()

            speak(
                if (done) {
                    "नोटिफिकेशन खोल रहा हूँ।"
                } else {
                    "नोटिफिकेशन नहीं खुल सके।"
                }
            )

            return true
        }

        if (
            command in listOf(
                "open quick settings",
                "quick settings",
                "क्विक सेटिंग खोलो"
            )
        ) {

            val done =
                screenController.openQuickSettings()

            speak(
                if (done) {
                    "क्विक सेटिंग खोल रहा हूँ।"
                } else {
                    "क्विक सेटिंग नहीं खुल सकी।"
                }
            )

            return true
        }

        // TEXT INPUT

        if (
            command.startsWith("type ") ||
            command.startsWith("लिखो ")
        ) {

            val textToType =
                rawText.substringAfter(" ", "")

            val done =
                screenController.typeText(textToType)

            speak(
                if (done) {
                    "टेक्स्ट लिख दिया।"
                } else {
                    "टेक्स्ट लिखने के लिए कोई इनपुट बॉक्स नहीं मिला।"
                }
            )

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

            if (
                checkSelfPermission(
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {

                val done =
                    hardwareController.setFlashlight(true)

                speak(
                    if (done) {
                        "फ्लैशलाइट चालू है।"
                    } else {
                        "फ्लैशलाइट चालू नहीं हो सकी।"
                    }
                )

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

            val done =
                hardwareController.setFlashlight(false)

            speak(
                if (done) {
                    "फ्लैशलाइट बंद कर दी।"
                } else {
                    "फ्लैशलाइट बंद नहीं हो सकी।"
                }
            )

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

            speak(
                if (done) {
                    "कैमरा खोल रहा हूँ।"
                } else {
                    "कैमरा नहीं खुल सका।"
                }
            )

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

            startActivity(
                Intent(Settings.ACTION_SETTINGS)
            )

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
                listOf(
                    "openInternetSettings",
                    "openWifiSettings"
                )
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

    private fun invokeController(
        controller: Any,
        methodNames: List<String>,
        args: Array<Any> = emptyArray()
    ): Boolean {

        for (methodName in methodNames) {

            try {

                val method =
                    controller.javaClass.methods.firstOrNull {
                        it.name == methodName &&
                            it.parameterTypes.size == args.size
                    } ?: continue

                val result = method.invoke(
                    controller,
                    *args
                )

                return result as? Boolean ?: true

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Controller method failed: $methodName",
                    e
                )
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

            speak(
                "Gemini API Key दर्ज करने के लिए Settings खोलें।"
            )

            return
        }

        executor.execute {

            try {

                val screenText =
                    if (screenController.isAccessibilityEnabled()) {
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

                        homeView.setLiveStatus(
                            "IDRAAK • RESPONSE"
                        )

                        speak(answer)
                    }
                }

            } catch (e: Exception) {

                Log.e(TAG, "Gemini request failed", e)

                val errorMessage =
                    e.message ?: "Unknown Gemini error"

                runOnUiThread {

                    if (!isFinishing && !isDestroyed) {

                        homeView.setLiveStatus(
                            "GEMINI ERROR • ${errorMessage.take(55)}"
                        )

                        speak(
                            geminiFriendlyError(errorMessage)
                        )
                    }
                }
            }
        }
    }

    private fun geminiFriendlyError(message: String): String {

        val lower = message.lowercase(Locale.ROOT)

        return when {
            lower.contains("api key not valid") ||
                lower.contains("invalid api key") ||
                lower.contains("api_key_invalid") -> {
                "आपकी Gemini API Key गलत है। कृपया Settings में सही API Key दर्ज करें।"
            }

            lower.contains("permission_denied") ||
                lower.contains("permission denied") ||
                lower.contains("forbidden") -> {
                "Gemini API ने अनुमति नहीं दी। API Key और उसकी API permissions जाँचें।"
            }

            lower.contains("quota") ||
                lower.contains("resource_exhausted") ||
                lower.contains("429") -> {
                "Gemini API की उपयोग सीमा पूरी हो गई है। कृपया बाद में कोशिश करें या अपनी quota जाँचें।"
            }

            lower.contains("not_found") ||
                lower.contains("404") ||
                lower.contains("model not found") -> {
                "चुना हुआ Gemini Model उपलब्ध नहीं है। Settings में Model ID जाँचें।"
            }

            lower.contains("timeout") ||
                lower.contains("failed to connect") ||
                lower.contains("unable to resolve host") -> {
                "इंटरनेट कनेक्शन में समस्या है। कृपया अपना इंटरनेट जाँचें।"
            }

            lower.contains("400") -> {
                "Gemini ने अनुरोध स्वीकार नहीं किया। Model ID और API request जाँचें।"
            }

            else -> {
                "Gemini से जवाब नहीं मिला। त्रुटि का विवरण स्क्रीन पर देखें।"
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

        val encodedKey =
            URLEncoder.encode(key, "UTF-8")

        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "$safeModel:generateContent?key=$encodedKey"
        )

        val connection =
            url.openConnection() as HttpURLConnection

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
                            JSONObject().put(
                                "text",
                                systemPrompt
                            )
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
                                    JSONObject().put(
                                        "text",
                                        prompt
                                    )
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
                it.write(
                    body.toString().toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val responseCode = connection.responseCode

            val stream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                stream?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()

            if (responseCode !in 200..299) {

                val serverMessage = try {
                    JSONObject(responseText)
                        .optJSONObject("error")
                        ?.optString("message")
                        .orEmpty()
                } catch (_: Exception) {
                    ""
                }

                val safeMessage =
                    serverMessage.ifBlank {
                        "Gemini returned HTTP $responseCode"
                    }

                Log.e(
                    TAG,
                    "Gemini HTTP error $responseCode: $safeMessage"
                )

                throw Exception(
                    "HTTP $responseCode: $safeMessage"
                )
            }

            val json = JSONObject(responseText)

            val candidates =
                json.optJSONArray("candidates")
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
    // VOICE OUTPUT
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

    private fun speak(text: String) {

        if (text.isBlank()) return

        if (!::voiceEngine.isInitialized) {
            Log.w(TAG, "VoiceEngine is not initialized; speech skipped")
            return
        }

        handler.removeCallbacks(wakeAcknowledgement)

        voiceEngine.speak(text)
    }

    private fun updateVoiceInterfaceAfterSpeech() {

        if (isFinishing || isDestroyed) return

        when (wakeWordController.getState()) {

            WakeWordController.VoiceState.SLEEPING -> {
                homeView.setLiveStatus(
                    "IDRAAK STANDBY"
                )
                homeView.setVoiceStatus(false)
            }

            WakeWordController.VoiceState.ACTIVE -> {
                homeView.setLiveStatus(
                    "IDRAAK ACTIVE"
                )
                homeView.setVoiceStatus(true)
            }

            WakeWordController.VoiceState.PROCESSING -> {
                homeView.setLiveStatus(
                    "IDRAAK THINKING"
                )
                homeView.setVoiceStatus(true)
            }
        }
    }

    // -------------------------------------------------
    // SETTINGS DIALOG
    // -------------------------------------------------

    private fun openSettings() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(
                Color.rgb(3, 7, 28)
            )
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

        val languages = listOf(
            "Hindi",
            "English",
            "Urdu",
            "Arabic"
        )

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

        layout.addView(
            TextView(this).apply {
                text = "LANGUAGE"
                setTextColor(Color.CYAN)
            }
        )

        layout.addView(languageSpinner)

        layout.addView(
            TextView(this).apply {
                text = "VOICE"
                setTextColor(Color.CYAN)
            }
        )

        layout.addView(genderSpinner)

        AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->

                apiKey = keyInput.text.toString().trim()

                model = modelInput.text.toString()
                    .trim()
                    .ifBlank {
                        "gemini-2.5-flash"
                    }

                language =
                    languageSpinner.selectedItem.toString()

                gender =
                    genderSpinner.selectedItem.toString()

                saveSettings()

                voiceEngine.setLanguage(language)
                voiceEngine.setVoiceGender(gender)

                homeView.setAvatarGender(gender)

                if (permissionGranted) {
                    resumeVoiceService()
                }

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

        permissionGranted =
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (
            permissionGranted &&
            ::voiceEngine.isInitialized &&
            !voiceServiceStarted
        ) {
            startVoiceService()
        }

        if (::homeView.isInitialized) {
            homeView.reloadApps()
        }
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        if (receiverRegistered) {

            try {
                unregisterReceiver(voiceReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Receiver unregister failed", e)
            }

            receiverRegistered = false
        }

        if (::hardwareController.isInitialized) {
            try {
                hardwareController.release()
            } catch (e: Exception) {
                Log.w(TAG, "Hardware release failed", e)
            }
        }

        if (::voiceEngine.isInitialized) {
            voiceEngine.release()
        }

        executor.shutdownNow()

        // Foreground Service is intentionally not stopped here.

        super.onDestroy()
    }
}
