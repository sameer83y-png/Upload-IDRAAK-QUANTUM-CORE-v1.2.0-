
package com.idraak.quantum

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        private const val REQUEST_AUDIO = 101
    }

    private lateinit var orbView: JarvisOrbView
    private lateinit var tts: TextToSpeech

    private var recognizer: SpeechRecognizer? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var apiKey = ""
    private var model = "gemini-2.5-flash"

    private var language = "Hindi"
    private var gender = "Male"

    private var assistantActive = false
    private var ttsReady = false
    private var listening = false
    private var speaking = false
    private var permissionGranted = false
    private var activityVisible = false

    private var pendingSpeech: String? = null

    private val prefs by lazy {
        getSharedPreferences("idraak", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(3, 7, 28)
        window.navigationBarColor = Color.rgb(3, 7, 28)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        loadSettings()

        tts = TextToSpeech(this, this)

        buildInterface()

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            setupSpeechRecognizer()
        } else {
            orbView.setStatus("VOICE RECOGNITION UNAVAILABLE")
        }

        checkAudioPermission()
    }

    // SETTINGS STORAGE

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

    // JARVIS INTERFACE

    private fun buildInterface() {

        orbView = JarvisOrbView(this) {
            openSettings()
        }

        orbView.setStatus("IDRAAK SYSTEM INITIALIZING")

        setContentView(orbView)

        window.decorView.systemUiVisibility =
            (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    // MICROPHONE PERMISSION

    private fun checkAudioPermission() {

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            permissionGranted = true
            orbView.setStatus("IDRAAK STANDBY")
            startListening()
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

        if (requestCode == REQUEST_AUDIO) {

            permissionGranted =
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED

            if (permissionGranted) {
                startListening()
            } else {
                orbView.setStatus("MICROPHONE PERMISSION REQUIRED")
            }
        }
    }

    // VOICE RECOGNITION

    private fun recognitionIntent(): Intent {

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                selectedLocale().toLanguageTag()
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                selectedLocale().toLanguageTag()
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
            )
        }
    }

    private fun setupSpeechRecognizer() {

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true

                    if (!speaking) {
                        orbView.setStatus(
                            if (assistantActive)
                                "IDRAAK ACTIVE • LISTENING"
                            else
                                "IDRAAK STANDBY • LISTENING"
                        )
                    }
                }

                override fun onBeginningOfSpeech() {
                    orbView.setStatus("VOICE DETECTED")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    orbView.setVoiceLevel(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    listening = false
                    orbView.setVoiceLevel(0f)
                }

                override fun onError(error: Int) {
                    listening = false

                    if (activityVisible && !speaking) {
                        scheduleListening(800)
                    }
                }

                override fun onResults(results: Bundle?) {

                    listening = false

                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val spoken = matches?.firstOrNull()

                    if (!spoken.isNullOrBlank()) {
                        handleVoiceInput(spoken)
                    } else {
                        scheduleListening(500)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )
    }

    private fun startListening() {

        if (!activityVisible) return
        if (!permissionGranted) return
        if (speaking || listening) return
        if (recognizer == null) return

        try {
            recognizer?.startListening(recognitionIntent())
        } catch (_: Exception) {
            scheduleListening(1200)
        }
    }

    private fun scheduleListening(delay: Long) {

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({

            if (
                activityVisible &&
                permissionGranted &&
                !speaking
            ) {
                startListening()
            }

        }, delay)
    }

    // SPEECH PROCESSING

    private fun normalize(text: String): String {

        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun hasWakeWord(text: String): Boolean {

        val normalized = normalize(text)

        val words = listOf(
            "idraak",
            "इद्राक",
            "आईड्राक",
            "इत्राक"
        )

        return words.any {
            normalized == it ||
                normalized.startsWith("$it ")
        }
    }

    private fun removeWakeWord(text: String): String {

        var result = normalize(text)

        val words = listOf(
            "idraak",
            "इद्राक",
            "आईड्राक",
            "इत्राक"
        )

        for (word in words) {

            if (
                result == word ||
                result.startsWith("$word ")
            ) {
                result = result.removePrefix(word).trim()
                break
            }
        }

        return result
    }

    private fun isSleepCommand(text: String): Boolean {

        return normalize(text) in listOf(
            "सो जाओ",
            "सो जाओ idraak",
            "सो जाओ इद्राक",
            "so jao",
            "so jao idraak",
            "sleep",
            "sleep idraak",
            "go to sleep",
            "standby"
        )
    }

    private fun handleVoiceInput(spoken: String) {

        if (isSleepCommand(spoken)) {

            assistantActive = false

            orbView.setStatus("IDRAAK STANDBY")

            speak("ठीक है। मैं Standby में जा रहा हूँ।")

            return
        }

        if (!assistantActive) {

            if (hasWakeWord(spoken)) {

                assistantActive = true

                val command = removeWakeWord(spoken)

                orbView.setStatus("IDRAAK ACTIVE")

                if (command.isBlank()) {
                    speak("जी, मैं सक्रिय हूँ। बताइए!")
                } else {
                    executeCommand(command)
                }
            }

            return
        }

        executeCommand(spoken)
    }

    // COMMAND ENGINE

    private fun executeCommand(command: String) {

        if (command.isBlank()) return

        if (handlePhoneCommand(command)) return

        orbView.setStatus("MAYA AI • THINKING")

        val currentKey = apiKey
        val currentModel = model

        if (currentKey.isBlank()) {

            orbView.setStatus("API KEY REQUIRED")

            speak(
                "कृपया Settings में Gemini API Key दर्ज करें।"
            )

            return
        }

        executor.execute {

            try {

                val answer = askMayaAI(
                    command,
                    currentKey,
                    currentModel
                )

                runOnUiThread {

                    if (!isFinishing && !isDestroyed) {
                        orbView.setStatus("MAYA AI • RESPONSE")
                        speak(answer)
                    }
                }

            } catch (_: Exception) {

                runOnUiThread {

                    if (!isFinishing && !isDestroyed) {

                        orbView.setStatus("AI CONNECTION ERROR")

                        speak(
                            "माफ कीजिए, AI से कनेक्शन नहीं हो सका।"
                        )
                    }
                }
            }
        }
    }

    // GEMINI AI ENGINE

    private fun askMayaAI(
        prompt: String,
        key: String,
        modelId: String
    ): String {

        val safeModel = modelId.trim().ifBlank {
            "gemini-2.5-flash"
        }

        val encodedKey = URLEncoder.encode(
            key,
            "UTF-8"
        )

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
                You are IDRAAK, an advanced personal AI assistant
                inspired by the conversational and agent-like
                experience of MAYA AI / The Hunter AI and the
                futuristic personality of JARVIS.

                You are intelligent, helpful, respectful and natural.

                You communicate in the user's selected language:
                $language.

                Understand Hindi, English, Urdu and Arabic.
                Give clear, useful and natural answers.

                Do not claim that an action was performed unless
                the Android application actually performed it.

                You are running inside an Android assistant app.
            """.trimIndent()

            val body = JSONObject().apply {

                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        org.json.JSONArray().put(
                            JSONObject().put(
                                "text",
                                systemPrompt
                            )
                        )
                    )
                )

                put(
                    "contents",
                    org.json.JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")

                            put(
                                "parts",
                                org.json.JSONArray().put(
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

            val content = candidates
                .getJSONObject(0)
                .getJSONObject("content")

            val parts = content.optJSONArray("parts")
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

    // BASIC PHONE CONTROL

    private fun handlePhoneCommand(text: String): Boolean {

        val command = normalize(text)

        val apps = mapOf(
            "whatsapp" to "com.whatsapp",
            "व्हाट्सएप" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "यूट्यूब" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "क्रोम" to "com.android.chrome",
            "telegram" to "org.telegram.messenger",
            "टेलीग्राम" to "org.telegram.messenger"
        )

        if (
            command.startsWith("open ") ||
            command.startsWith("खोलो ") ||
            command.startsWith("खोल ")
        ) {

            val target = command
                .removePrefix("open ")
                .removePrefix("खोलो ")
                .removePrefix("खोल ")
                .trim()

            val packageName = apps[target]

            if (packageName != null) {

                val launchIntent =
                    packageManager.getLaunchIntentForPackage(
                        packageName
                    )

                if (launchIntent != null) {

                    try {
                        startActivity(launchIntent)

                        orbView.setStatus("APPLICATION LAUNCHED")

                        speak("जी, ऐप खोल रहा हूँ।")

                    } catch (_: Exception) {
                        speak("ऐप नहीं खुल सका।")
                    }

                } else {
                    speak("यह ऐप फोन में उपलब्ध नहीं है।")
                }

                return true
            }
        }

        if (
            command == "settings" ||
            command == "सेटिंग खोलो" ||
            command == "फोन सेटिंग खोलो"
        ) {

            startActivity(
                Intent(Settings.ACTION_SETTINGS)
            )

            return true
        }

        return false
    }

    // TEXT TO SPEECH

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

            // IMPORTANT: SPEAKING ANIMATION CALLBACK
            setupTtsAnimation()

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

        if (!ttsReady) {
            pendingSpeech = text
            return
        }

        speaking = true
        listening = false

        try {
            recognizer?.cancel()
        } catch (_: Exception) {}

        orbView.setStatus("IDRAAK • SPEAKING")
        orbView.setSpeaking(true)

        applyVoice()

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "idraak_response"
        )
    }

    // TTS ANIMATION CALLBACK

    private fun setupTtsAnimation() {

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {

                    runOnUiThread {

                        speaking = false

                        orbView.setSpeaking(false)

                        orbView.setStatus(
                            if (assistantActive)
                                "IDRAAK ACTIVE"
                            else
                                "IDRAAK STANDBY"
                        )

                        scheduleListening(400)
                    }
                }

                override fun onError(utteranceId: String?) {

                    runOnUiThread {

                        speaking = false

                        orbView.setSpeaking(false)

                        scheduleListening(500)
                    }
                }
            }
        )
    }

    // SETTINGS - LONG PRESS ORB

    private fun openSettings() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.rgb(3, 7, 28))
        }

        val title = TextView(this).apply {
            text = "IDRAAK • JARVIS SETTINGS"
            textSize = 20f
            gravity = android.view.Gravity.CENTER
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

        val genderSpinner = Spinner(this)

        val genders = listOf("Male", "Female")

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

        val save = Button(this).apply {

            text = "SAVE SETTINGS"

            setOnClickListener {

                apiKey = keyInput.text.toString().trim()

                model = modelInput.text.toString().trim()
                    .ifBlank { "gemini-2.5-flash" }

                language = languageSpinner.selectedItem.toString()

                gender = genderSpinner.selectedItem.toString()

                saveSettings()

                applyVoice()

                Toast.makeText(
                    this@MainActivity,
                    "IDRAAK SETTINGS SAVED",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        layout.addView(save)

        AlertDialog.Builder(this)
            .setView(layout)
            .setNegativeButton("CLOSE", null)
            .show()
    }

    // ACTIVITY LIFECYCLE

    override fun onResume() {
        super.onResume()

        activityVisible = true

        if (permissionGranted) {
            scheduleListening(500)
        }
    }

    override fun onPause() {
        super.onPause()

        activityVisible = false
        listening = false

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {

        activityVisible = false

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.destroy()
            recognizer = null
        } catch (_: Exception) {}

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        executor.shutdownNow()

        super.onDestroy()
    }
}

// ==========================================
// JARVIS HOLOGRAPHIC ORB
// ==========================================

class JarvisOrbView(
    context: android.content.Context,
    private val onLongPress: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val handler = Handler(Looper.getMainLooper())

    private var phase = 0f
    private var voiceLevel = 0f
    private var speaking = false

    private var status = "IDRAAK INITIALIZING"

    private val longPressRunnable = Runnable {
        onLongPress()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        handler.post(animationRunnable)
    }

    private val animationRunnable = object : Runnable {

        override fun run() {

            phase += if (speaking) 0.13f else 0.035f

            if (phase > 1000f) phase = 0f

            invalidate()

            handler.postDelayed(this, 35)
        }
    }

    fun setStatus(value: String) {
        status = value.uppercase(Locale.ROOT)
        invalidate()
    }

    fun setSpeaking(value: Boolean) {
        speaking = value
        invalidate()
    }

    fun setVoiceLevel(value: Float) {
        voiceLevel = value.coerceIn(0f, 12f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0f || h <= 0f) return

        canvas.drawColor(Color.rgb(3, 7, 28))

        val cx = w / 2f
        val cy = h * 0.43f

        val radius = min(w * 0.32f, h * 0.22f)
            .coerceAtLeast(75f)

        // BACKGROUND PARTICLES

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0, 100, 170)

        for (i in 0 until 45) {

            val angle = i * 2.399f + phase * 0.1f

            val distance = radius * (
                1.5f + 0.4f * sin(phase + i)
            )

            val x = cx + cos(angle) * distance
            val y = cy + sin(angle) * distance

            paint.alpha = 35 +
                (abs(sin(phase + i)) * 90).toInt()

            canvas.drawCircle(
                x.toFloat(),
                y.toFloat(),
                1.5f,
                paint
            )
        }

        // OUTER GLOW

        paint.shader = RadialGradient(
            cx,
            cy,
            radius * 1.8f,
            intArrayOf(
                Color.argb(55, 0, 180, 255),
                Color.argb(18, 0, 100, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            cx,
            cy,
            radius * 1.8f,
            paint
        )

        paint.shader = null

        // OUTER RINGS

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f

        paint.color = Color.argb(150, 0, 220, 255)

        canvas.drawCircle(
            cx,
            cy,
            radius * 1.30f,
            paint
        )

        paint.color = Color.argb(90, 0, 150, 255)

        canvas.drawCircle(
            cx,
            cy,
            radius * 1.45f,
            paint
        )

        // ROTATING RING

        val rotation = phase * 35f

        val rect = RectF(
            cx - radius * 1.38f,
            cy - radius * 1.38f,
            cx + radius * 1.38f,
            cy + radius * 1.38f
        )

        paint.color = Color.CYAN
        paint.strokeWidth = 3f

        canvas.drawArc(
            rect,
            rotation,
            100f,
            false,
            paint
        )

        canvas.drawArc(
            rect,
            rotation + 180f,
            55f,
            false,
            paint
        )

        // MAIN ORB

        paint.style = Paint.Style.FILL

        paint.shader = RadialGradient(
            cx - radius * 0.25f,
            cy - radius * 0.3f,
            radius * 1.5f,
            intArrayOf(
                Color.rgb(20, 110, 165),
                Color.rgb(4, 35, 85),
                Color.rgb(1, 8, 28)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        paint.shader = null

        // INNER RING

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(180, 0, 230, 255)

        canvas.drawCircle(
            cx,
            cy,
            radius * 0.82f,
            paint
        )

        // JARVIS ENERGY WAVE

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.CYAN

        val wave = Path()

        for (i in 0..120) {

            val x = cx - radius * 0.68f +
                i * (radius * 1.36f / 120f)

            val normalized = i / 120f

            val envelope = sin(normalized * PI).toFloat()

            val amplitude = if (speaking) {
                radius * 0.22f
            } else {
                radius * 0.07f
            }

            val y = cy + sin(
                normalized * 24f + phase * 3f
            ) * amplitude * envelope *
                (1f + voiceLevel / 15f)

            if (i == 0) {
                wave.moveTo(x, y.toFloat())
            } else {
                wave.lineTo(x, y.toFloat())
            }
        }

        canvas.drawPath(wave, paint)

        // CENTRAL CORE

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE

        canvas.drawCircle(
            cx,
            cy,
            4f + abs(sin(phase * 2f)) * 3f,
            paint
        )

        // IDRAAK LABEL

        paint.color = Color.CYAN
        paint.textAlign = Paint.Align.CENTER

        paint.typeface = Typeface.create(
            "sans-serif-medium",
            Typeface.BOLD
        )

        paint.textSize = 22f

        canvas.drawText(
            "IDRAAK",
            cx,
            cy + radius * 0.56f,
            paint
        )

        // STATUS

        paint.color = Color.WHITE
        paint.textSize = 14f

        paint.typeface = Typeface.create(
            "sans-serif",
            Typeface.NORMAL
        )

        canvas.drawText(
            status,
            cx,
            h * 0.76f,
            paint
        )

        // INSTRUCTION

        paint.color = Color.argb(150, 120, 210, 255)
        paint.textSize = 11f

        canvas.drawText(
            "VOICE CONTROL • JARVIS INTELLIGENCE",
            cx,
            h * 0.81f,
            paint
        )

        canvas.drawText(
            "LONG PRESS ORB FOR SETTINGS",
            cx,
            h * 0.86f,
            paint
        )
    }

    // ONLY LONG PRESS OPENS SETTINGS

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                handler.postDelayed(
                    longPressRunnable,
                    2000
                )

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                handler.removeCallbacks(longPressRunnable)

                return true
            }
        }

        return true
    }

    override fun onDetachedFromWindow() {

        handler.removeCallbacksAndMessages(null)

        super.onDetachedFromWindow()
    }
}
