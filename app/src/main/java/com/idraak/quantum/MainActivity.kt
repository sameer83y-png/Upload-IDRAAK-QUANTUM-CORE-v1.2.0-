
package com.idraak.quantum

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val bg = Color.rgb(3, 7, 24)
    private val panel = Color.rgb(10, 22, 48)
    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(145, 75, 255)

    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var appGrid: GridLayout
    private lateinit var search: EditText
    private lateinit var orb: HoloOrb
    private lateinit var tts: TextToSpeech

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var apiKey = ""
    private var model = "gemini-2.5-flash"
    private var gender = "Female"
    private var language = "Hindi"
    private var allApps = listOf<android.content.pm.ResolveInfo>()
    private var receiverRegistered = false

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
        buildHomeScreen()
        registerVoiceReceiver()
        loadApps()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun loadPrefs() {
        val p = getSharedPreferences("idraak", MODE_PRIVATE)
        apiKey = p.getString("key", "") ?: ""
        model = p.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        gender = p.getString("gender", "Female") ?: "Female"
        language = p.getString("language", "Hindi") ?: "Hindi"
    }

    private fun savePrefs() {
        getSharedPreferences("idraak", MODE_PRIVATE).edit()
            .putString("key", apiKey)
            .putString("model", model)
            .putString("gender", gender)
            .putString("language", language)
            .apply()
    }

    private fun rounded(color: Int, radius: Int = 18): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(90, 0, 220, 255))
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int = Color.WHITE
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
    }

    private fun actionButton(
        value: String,
        action: () -> Unit
    ): TextView {
        return text(value, 14f, cyan).apply {
            background = rounded(panel, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { action() }
        }
    }

    private fun buildHomeScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }

        root.addView(
            text("IDRAAK", 30f, cyan),
            LinearLayout.LayoutParams(-1, dp(44))
        )

        root.addView(
            text("QUANTUM CORE  •  NEURAL HOME", 11f, purple)
        )

        val clock = text(
            android.text.format.DateFormat.format("hh:mm", System.currentTimeMillis()).toString(),
            25f
        )
        root.addView(clock)

        root.addView(
            text("YOUR PERSONAL AI HOME SYSTEM", 10f, Color.LTGRAY)
        )

        orb = HoloOrb(this).apply {
            gender = this@MainActivity.gender
        }

        root.addView(
            orb,
            LinearLayout.LayoutParams(dp(280), dp(270))
        )

        // Only the holographic globe supports the settings long press.
        orb.setOnTouchListener(object : View.OnTouchListener {
            private var moved = false
            private var downX = 0f
            private var downY = 0f

            private val longPress = Runnable {
                moved = true
                openSettings()
            }

            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        moved = false
                        downX = event.x
                        downY = event.y
                        handler.postDelayed(longPress, 2000)
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.x - downX) > dp(18) ||
                            kotlin.math.abs(event.y - downY) > dp(18)) {
                            moved = true
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

        status = text("● SYSTEM READY", 13f, cyan)
        root.addView(status)

        output = text(
            "IDRAAK तैयार है। Globe को 2 सेकंड दबाकर Settings खोलें।",
            13f
        )
        output.background = rounded(panel)
        root.addView(
            output,
            LinearLayout.LayoutParams(-1, dp(65)).apply {
                setMargins(0, dp(8), 0, dp(10))
            }
        )

        input = EditText(this).apply {
            hint = "अपनी कमांड लिखें..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(panel)
            setOnEditorActionListener { _, _, _ ->
                executeCommand(text.toString())
                true
            }
        }

        root.addView(
            input,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        root.addView(
            actionButton("⌁  EXECUTE COMMAND") {
                executeCommand(input.text.toString())
            },
            LinearLayout.LayoutParams(-1, dp(48)).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        )

        val micRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        micRow.addView(
            actionButton("MICROPHONE: START") {
                requestMicAndStart()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(0, 0, dp(5), 0)
            }
        )

        micRow.addView(
            actionButton("STOP") {
                stopVoiceService()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(5), 0, 0, 0)
            }
        )

        root.addView(micRow)

        root.addView(
            text("APPLICATIONS", 18f, cyan).apply {
                gravity = Gravity.START
                setPadding(0, dp(20), 0, dp(6))
            }
        )

        search = EditText(this).apply {
            hint = "Search installed apps..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            singleLine = true
            textSize = 14f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(panel)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) {
                    showApps(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(48))
        )

        appGrid = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }

        root.addView(
            appGrid,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(10), 0, 0)
            }
        )

        root.addView(
            text("IDRAAK QUANTUM CORE", 10f, Color.GRAY)
        )

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun registerVoiceReceiver() {
        val filter = IntentFilter(VoiceForegroundService.ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    private fun loadApps() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            @Suppress("DEPRECATION")
            allApps = packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .sortedBy {
                    it.loadLabel(packageManager).toString().lowercase()
                }

            showApps("")
        } catch (e: Exception) {
            status.text = "APP LIST ERROR"
            output.text = e.message ?: "ऐप सूची नहीं मिली।"
        }
    }

    private fun showApps(filter: String) {
        if (!::appGrid.isInitialized) return

        appGrid.removeAllViews()

        val visible = allApps.filter {
            it.loadLabel(packageManager).toString()
                .contains(filter.trim(), ignoreCase = true)
        }

        visible.forEach { info ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(10), dp(3), dp(10))
                background = rounded(Color.rgb(8, 17, 39), 12)
                isClickable = true
                isFocusable = true
                setOnClickListener { launchApp(info) }
            }

            val icon = ImageView(this).apply {
                try {
                    setImageDrawable(info.loadIcon(packageManager))
                } catch (_: Exception) {}
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            tile.addView(
                icon,
                LinearLayout.LayoutParams(dp(42), dp(42))
            )

            val name = text(
                info.loadLabel(packageManager).toString(),
                10f
            )
            name.maxLines = 2
            name.ellipsize = android.text.TextUtils.TruncateAt.END

            tile.addView(
                name,
                LinearLayout.LayoutParams(-1, dp(38))
            )

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(94)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }

            appGrid.addView(tile, params)
        }

        if (visible.isEmpty()) {
            appGrid.addView(
                text("कोई ऐप नहीं मिला", 13f, Color.LTGRAY),
                GridLayout.LayoutParams().apply {
                    columnSpec = GridLayout.spec(0, 4)
                }
            )
        }
    }

    private fun launchApp(info: android.content.pm.ResolveInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(
                    info.activityInfo.packageName,
                    info.activityInfo.name
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            status.text = "APP LAUNCH REQUESTED"
        } catch (e: Exception) {
            status.text = "APP LAUNCH FAILED"
            output.text = e.message ?: "ऐप नहीं खुल सका।"
        }
    }

    private fun requestMicAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 101
            )
            return
        }

        try {
            val intent = Intent(this, VoiceForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
            else startService(intent)
            status.text = "MICROPHONE START REQUESTED"
        } catch (e: Exception) {
            status.text = "MICROPHONE ERROR: ${e.message}"
        }
    }

    private fun stopVoiceService() {
        try {
            val intent = Intent(this, VoiceForegroundService::class.java).apply {
                action = VoiceForegroundService.ACTION_STOP
            }
            startService(intent)
            status.text = "MICROPHONE STOP REQUESTED"
        } catch (e: Exception) {
            status.text = "STOP ERROR: ${e.message}"
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
            requestMicAndStart()
        } else if (requestCode == 101) {
            status.text = "MICROPHONE PERMISSION DENIED"
        }
    }

    private fun executeCommand(command: String) {
        val commandText = command.trim()
        if (commandText.isBlank()) return

        status.text = "COMMAND RECEIVED"
        output.text = "कमांड समझी जा रही है..."

        executor.execute {
            try {
                val module = Python.getInstance().getModule("idraak_core")
                val classified = JSONObject(
                    module.callAttr("classify", commandText).toString()
                )

                when (classified.optString("type")) {
                    "open_app" -> {
                        val target = classified.optString("target")
                        runOnUiThread {
                            val success = openApp(target)
                            if (success) {
                                status.text = "ACTION ACCEPTED"
                                output.text = "$target खोलने का अनुरोध Android ने स्वीकार किया।"
                                speak("$target खोलने का अनुरोध पूरा हुआ।")
                            } else {
                                status.text = "ACTION FAILED"
                                output.text = "$target नहीं खुल सका।"
                                speak("ऐप नहीं खुल सका।")
                            }
                        }
                    }

                    "idraak_settings" -> runOnUiThread {
                        openSettings()
                    }

                    else -> {
                        runOnUiThread {
                            status.text = "GEMINI THINKING"
                            output.text = "जवाब तैयार किया जा रहा है..."
                        }

                        val response = JSONObject(
                            module.callAttr(
                                "reply", commandText, apiKey, model
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
                    status.text = "COMMAND ERROR"
                    output.text = e.message ?: e.toString()
                }
            }
        }
    }

    private fun openApp(target: String): Boolean {
        if (target == "settings") {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            return true
        }

        val packages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "telegram" to "org.telegram.messenger"
        )

        val packageName = packages[target] ?: return false
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return false

        startActivity(intent)
        return true
    }

    private fun speak(message: String) {
        if (!::tts.isInitialized) return

        tts.language = selectedLocale()
        tts.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "idraak_speech"
        )
    }

    private fun selectedLocale(): Locale {
        return when (language) {
            "Hindi" -> Locale("hi", "IN")
            "Urdu" -> Locale("ur", "PK")
            "Arabic" -> Locale("ar")
            else -> Locale.US
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) applyVoice()
    }

    private fun applyVoice() {
        if (!::tts.isInitialized) return

        val locale = selectedLocale()
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
            setPadding(dp(20), dp(16), dp(20), dp(16))
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
        val langs = listOf("Hindi", "English", "Urdu", "Arabic")
        lang.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            langs
        )
        lang.setSelection(langs.indexOf(language).coerceAtLeast(0))

        val voice = Spinner(this)
        val genders = listOf("Female", "Male")
        voice.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genders
        )
        voice.setSelection(genders.indexOf(gender).coerceAtLeast(0))

        layout.addView(text("IDRAAK SETTINGS", 21f, cyan))
        layout.addView(key)
        layout.addView(modelInput)
        layout.addView(text("Language", 14f))
        layout.addView(lang)
        layout.addView(text("Avatar / Voice", 14f))
        layout.addView(voice)

        val scroll = ScrollView(this)
        scroll.addView(layout)

        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton("SAVE") { _, _ ->
                apiKey = key.text.toString().trim()
                model = modelInput.text.toString().trim()
                    .ifBlank { "gemini-2.5-flash" }
                language = lang.selectedItem.toString()
                gender = voice.selectedItem.toString()

                savePrefs()
                applyVoice()
                orb.gender = gender
                orb.invalidate()

                status.text = "SETTINGS SAVED"
                Toast.makeText(
                    this, "IDRAAK Settings saved", Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::appGrid.isInitialized) loadApps()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }

        if (::tts.isInitialized) tts.shutdown()
        executor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

class HoloOrb(context: Context) : View(context) {

    var gender: String = "Female"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scale = resources.displayMetrics.density
        fun d(v: Float) = v * scale

        val cx = width / 2f
        val cy = height / 2f - d(5f)

        // Outer holographic rings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d(2f)
        paint.color = Color.rgb(0, 225, 255)
        canvas.drawCircle(cx, cy, d(108f), paint)
        canvas.drawCircle(cx, cy, d(117f), paint)

        paint.color = Color.rgb(145, 75, 255)
        paint.strokeWidth = d(3f)
        canvas.drawArc(
            cx - d(125f), cy - d(125f),
            cx + d(125f), cy + d(125f),
            20f, 220f, false, paint
        )

        // Avatar glow
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(35, 0, 220, 255)
        canvas.drawCircle(cx, cy - d(25f), d(75f), paint)

        // Head
        paint.color = Color.rgb(45, 190, 255)
        canvas.drawCircle(
            cx - d(8f), cy - d(42f),
            d(if (gender == "Female") 27f else 30f), paint
        )

        // Neck and shoulders
        paint.color = Color.rgb(30, 130, 220)
        val body = Path().apply {
            moveTo(cx - d(48f), cy + d(65f))
            quadTo(
                cx - d(45f), cy - d(5f),
                cx - d(8f), cy + d(1f)
            )
            quadTo(
                cx + d(40f), cy - d(5f),
                cx + d(48f), cy + d(65f)
            )
            close()
        }
        canvas.drawPath(body, paint)

        // Glowing globe
        val gx = cx + d(67f)
        val gy = cy + d(20f)

        paint.color = Color.argb(55, 0, 240, 255)
        canvas.drawCircle(gx, gy, d(35f), paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(0, 245, 255)
        paint.strokeWidth = d(3f)
        canvas.drawCircle(gx, gy, d(27f), paint)
        canvas.drawOval(
            gx - d(12f), gy - d(27f),
            gx + d(12f), gy + d(27f), paint
        )
        canvas.drawLine(
            gx - d(25f), gy, gx + d(25f), gy, paint
        )

        // Globe title
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = d(12f)
        canvas.drawText("IDRAAK AI", cx, height - d(5f), paint)
    }
}
