package com.idraak.quantum

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import org.json.JSONObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val cyan = Color.rgb(0, 220, 255)
    private val bg = Color.rgb(3, 7, 28)
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private lateinit var orb: HoloOrb
    private val executor = Executors.newSingleThreadExecutor()

    private var apiKey = ""
    private var model = "gemini-2.5-flash"
    private var gender = "Female"
    private var language = "Hindi"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val spoken = intent?.getStringExtra(
                VoiceForegroundService.EXTRA_TEXT
            ) ?: return
            runOnUiThread {
                input.setText(spoken)
                executeCommand(spoken)
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

        val filter = IntentFilter(VoiceForegroundService.ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences("idraak", MODE_PRIVATE)
        apiKey = prefs.getString("key", "") ?: ""
        model = prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
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

    private fun label(text: String, size: Float): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }
    }

    private fun button(text: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 75, 120))
            setOnClickListener { action() }
        }
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(bg)
        }

        root.addView(label("IDRAAK  QUANTUM CORE", 23f))
        root.addView(label("NEURAL INTERFACE  •  v1.2.0", 12f))

        orb = HoloOrb(this)
        orb.gender = gender
        root.addView(
            orb,
            LinearLayout.LayoutParams(
                270,
                300
            )
        )

        orb.setOnTouchListener(object : View.OnTouchListener {
            private val handler = Handler(Looper.getMainLooper())
            private var triggered = false
            private var downX = 0f
            private var downY = 0f

            private val longPress = Runnable {
                triggered = true
                openSettings()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        triggered = false
                        downX = event.x
                        downY = event.y
                        handler.postDelayed(longPress, 2000)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.x - downX) > 25 ||
                            kotlin.math.abs(event.y - downY) > 25) {
                            handler.removeCallbacks(longPress)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPress)
                        return true
                    }
                }
                return true
            }
        })

        status = label("SYSTEM READY", 14f)
        root.addView(status)

        input = EditText(this).apply {
            hint = "अपनी कमांड लिखें..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            setBackgroundColor(Color.rgb(15, 25, 55))
        }
        root.addView(input)

        root.addView(button("EXECUTE COMMAND") {
            executeCommand(input.text.toString())
        })

        root.addView(button("MICROPHONE: START / STOP") {
            requestMicAndStart()
        })

        root.addView(button("IDRAAK SETTINGS") { openSettings() })

        output = label(
            "IDRAAK तैयार है। Globe को 2 सेकंड दबाकर Settings खोलें।",
            14f
        )
        root.addView(output)

        setContentView(root)
    }

    private fun requestMicAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
            return
        }
        startVoiceService()
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
            else startService(intent)
            status.text = "MICROPHONE SERVICE REQUESTED"
        } catch (e: Exception) {
            status.text = "MICROPHONE ERROR: " + e.message
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceService()
        } else {
            status.text = "MICROPHONE PERMISSION DENIED"
        }
    }

    private fun openApp(target: String): Boolean {
        val packages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "telegram" to "org.telegram.messenger"
        )

        if (target == "settings") {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            return true
        }

        val packageName = packages[target] ?: return false
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: return false

        startActivity(launch)
        return true
    }

    private fun executeCommand(text: String) {
        if (text.isBlank()) return

        status.text = "कमांड प्राप्त हुई..."
        output.text = "योजना बनाई जा रही है..."

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
                            status.text = "$target खोलने की कोशिश..."
                        }

                        val success = try {
                            openApp(target)
                        } catch (_: Exception) {
                            false
                        }

                        runOnUiThread {
                            if (success) {
                                output.text = "$target खोलने का अनुरोध Android ने स्वीकार किया।"
                                status.text = "ACTION ACCEPTED"
                                speak("$target खोलने का अनुरोध पूरा हुआ।")
                            } else {
                                output.text = "$target नहीं खुला। ऐप इंस्टॉल है या नहीं जाँचें।"
                                status.text = "ACTION FAILED"
                                speak("ऐप नहीं खुल सका।")
                            }
                        }
                    }

                    "idraak_settings" -> runOnUiThread { openSettings() }

                    else -> {
                        runOnUiThread {
                            status.text = "Gemini से जवाब तैयार हो रहा है..."
                        }

                        val response = JSONObject(
                            module.callAttr(
                                "reply", text, apiKey, model
                            ).toString()
                        )

                        val answer = response.optString(
                            "message", "जवाब उपलब्ध नहीं है।"
                        )

                        runOnUiThread {
                            output.text = answer
                            status.text = response.optString("status", "DONE")
                            speak(answer)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "ERROR"
                    output.text = e.toString()
                }
            }
        }
    }

    private fun speak(text: String) {
        if (!::tts.isInitialized) return
        tts.language = when (language) {
            "Hindi" -> Locale("hi", "IN")
            "Urdu" -> Locale("ur", "PK")
            "Arabic" -> Locale("ar")
            else -> Locale.US
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "idraak_speech")
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
            if (gender == "Female") it.name.contains("female", true)
            else it.name.contains("male", true)
        } ?: voices.firstOrNull()

        if (selected != null) tts.voice = selected
    }

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
        }

        val lang = Spinner(this)
        val langs = listOf("Hindi", "English", "Urdu", "Arabic")
        lang.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            langs
        )
        lang.setSelection(langs.indexOf(language).coerceAtLeast(0))

        val voice = Spinner(this)
        val genders = listOf("Female", "Male")
        voice.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genders
        )
        voice.setSelection(genders.indexOf(gender).coerceAtLeast(0))

        layout.addView(label("IDRAAK SETTINGS", 20f))
        layout.addView(key)
        layout.addView(modelInput)
        layout.addView(label("Language", 14f))
        layout.addView(lang)
        layout.addView(label("Avatar / Voice", 14f))
        layout.addView(voice)
        layout.addView(button("SAVE SETTINGS") {
            apiKey = key.text.toString().trim()
            model = modelInput.text.toString().trim()
            language = lang.selectedItem.toString()
            gender = voice.selectedItem.toString()
            savePrefs()
            applyVoice()
            orb.gender = gender
            orb.invalidate()
            status.text = "SETTINGS SAVED"
            Toast.makeText(this, "IDRAAK Settings saved", Toast.LENGTH_SHORT).show()
        })

        val scroll = ScrollView(this)
        scroll.addView(layout)

        android.app.AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("CLOSE", null)
            .show()
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        if (::tts.isInitialized) tts.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }
}

class HoloOrb(context: Context) : View(context) {

    var gender: String = "Female"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.CYAN
        canvas.drawCircle(cx, cy, 100f, paint)
        canvas.drawCircle(cx, cy, 112f, paint)

        paint.color = Color.rgb(100, 50, 255)
        paint.strokeWidth = 2f
        canvas.drawArc(
            cx - 122, cy - 122, cx + 122, cy + 122,
            25f, 230f, false, paint
        )

        // Holographic avatar silhouette
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(25, 190, 255)

        val headRadius = if (gender == "Female") 28f else 31f
        canvas.drawCircle(cx, cy - 35f, headRadius, paint)

        val body = Path()
        body.moveTo(cx - 48, cy + 60)
        body.quadTo(cx - 43, cy - 2, cx, cy + 3)
        body.quadTo(cx + 43, cy - 2, cx + 48, cy + 60)
        body.close()
        canvas.drawPath(body, paint)

        // Glowing globe in the avatar's hand
        paint.color = Color.rgb(0, 245, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawCircle(cx + 67, cy + 20, 27f, paint)
        canvas.drawOval(
            cx + 48, cy + 4, cx + 86, cy + 36, paint
        )
        canvas.drawLine(cx + 40, cy + 20, cx + 94, cy + 20, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 13f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("IDRAAK AI", cx, height - 10f, paint)
    }
}
