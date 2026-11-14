
package com.idraak.quantum

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import kotlin.math.abs
import kotlin.math.min

class LauncherHomeView(
    context: Context,
    private val onSettingsClick: () -> Unit,
    private val onVoiceClick: () -> Unit
) : LinearLayout(context) {

    private val engine = LauncherEngine(context)
    private var allApps: List<LauncherApp> = emptyList()

    private lateinit var appAdapter: AppDrawerAdapter
    private lateinit var searchBox: EditText
    private lateinit var statusText: TextView
    private lateinit var voiceButton: TextView
    private lateinit var appGrid: GridView

    private val backgroundColor = Color.rgb(5, 9, 25)
    private val cyan = Color.rgb(0, 220, 255)
    private val purple = Color.rgb(150, 75, 255)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(16, 12, 16, 10)

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(5, 9, 25),
                Color.rgb(10, 15, 42),
                Color.rgb(15, 8, 38)
            )
        )

        buildInterface()
        reloadApps()
    }

    private fun buildInterface() {

        val header = TextView(context).apply {
            text = "IDRAAK"
            textSize = 27f
            setTextColor(cyan)
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            gravity = Gravity.CENTER
            letterSpacing = 0.22f
        }

        addView(
            header,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(context).apply {
            text = "QUANTUM INTELLIGENCE SYSTEM"
            textSize = 10f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            setPadding(0, 2, 0, 8)
        }

        addView(
            subtitle,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val orb = LiveOrbView(context)

        val longPressHandler = Handler(Looper.getMainLooper())
        var longPressTriggered = false
        var startX = 0f
        var startY = 0f

        val longPressAction = Runnable {
            longPressTriggered = true
            onSettingsClick()
        }

        orb.setOnTouchListener { view, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    startX = event.x
                    startY = event.y

                    longPressHandler.postDelayed(
                        longPressAction,
                        2000
                    )
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (
                        abs(event.x - startX) > 25 ||
                        abs(event.y - startY) > 25
                    ) {
                        longPressHandler.removeCallbacks(
                            longPressAction
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(
                        longPressAction
                    )
                    true
                }

                else -> true
            }
        }

        addView(
            orb,
            LayoutParams(190, 190).apply {
                gravity = Gravity.CENTER
                topMargin = 5
                bottomMargin = 5
            }
        )

        statusText = TextView(context).apply {
            text = "● IDRAAK STANDBY"
            textSize = 13f
            setTextColor(Color.rgb(0, 200, 255))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 5, 0, 12)
        }

        addView(
            statusText,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        searchBox = EditText(context).apply {
            hint = "Search installed applications..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(16, 10, 16, 10)

            background = GradientDrawable().apply {
                setColor(Color.rgb(17, 25, 48))
                cornerRadius = 28f
                setStroke(2, Color.rgb(25, 120, 180))
            }
        }

        addView(
            searchBox,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                48
            ).apply {
                bottomMargin = 10
            }
        )

        appGrid = GridView(context).apply {
            numColumns = 4
            horizontalSpacing = 8
            verticalSpacing = 8
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            gravity = Gravity.CENTER
            clipToPadding = false
            selector = android.graphics.drawable.ColorDrawable(
                Color.TRANSPARENT
            )
        }

        appAdapter = AppDrawerAdapter(
            context,
            emptyList()
        ) { app ->
            engine.launchApp(app.packageName)
        }

        appGrid.adapter = appAdapter

        addView(
            appGrid,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        searchBox.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    val filtered = engine.searchApps(
                        s?.toString() ?: "",
                        allApps
                    )
                    appAdapter.updateApps(filtered)
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {}
            }
        )

        val bottomBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }

        val settingsButton = createActionButton(
            "⚙ SETTINGS",
            purple
        ) {
            onSettingsClick()
        }

        voiceButton = createActionButton(
            "🎙 ACTIVATE",
            cyan
        ) {
            onVoiceClick()
        }

        bottomBar.addView(
            settingsButton,
            LayoutParams(0, 48, 1f).apply {
                marginEnd = 6
            }
        )

        bottomBar.addView(
            voiceButton,
            LayoutParams(0, 48, 1f).apply {
                marginStart = 6
            }
        )

        addView(
            bottomBar,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun createActionButton(
        label: String,
        color: Int,
        action: () -> Unit
    ): TextView {

        return TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD

            background = GradientDrawable().apply {
                setColor(Color.rgb(17, 25, 48))
                cornerRadius = 24f
                setStroke(2, color)
            }

            isClickable = true
            isFocusable = true

            setOnClickListener {
                action()
            }
        }
    }

    fun reloadApps() {
        try {
            allApps = engine.getInstalledApps()

            if (::appAdapter.isInitialized) {
                val query = if (::searchBox.isInitialized) {
                    searchBox.text.toString()
                } else {
                    ""
                }

                appAdapter.updateApps(
                    engine.searchApps(query, allApps)
                )
            }
        } catch (_: Exception) {
            setLiveStatus("● APP DRAWER ERROR")
        }
    }

    fun setLiveStatus(status: String) {
        if (::statusText.isInitialized) {
            statusText.text = status
        }
    }

    fun setVoiceStatus(active: Boolean) {
        if (::voiceButton.isInitialized) {
            voiceButton.text = if (active) {
                "⏸ STOP VOICE"
            } else {
                "🎙 ACTIVATE"
            }
        }
    }
}

class LiveOrbView(context: Context) : View(context) {

    private val orbPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    )

    private val ringPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val textPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(
            "sans-serif-black",
            Typeface.BOLD
        )
        textSize = 17f
    }

    private val smallTextPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = Color.rgb(0, 225, 255)
        textAlign = Paint.Align.CENTER
        textSize = 8f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var rotation = 0f

    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(155, 65, 255)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.37f

        canvas.save()

        val glow = RadialGradient(
            cx,
            cy,
            radius * 1.65f,
            intArrayOf(
                Color.argb(90, 0, 220, 255),
                Color.argb(35, 110, 50, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        orbPaint.shader = glow
        canvas.drawCircle(
            cx,
            cy,
            radius * 1.65f,
            orbPaint
        )
        orbPaint.shader = null

        val globeGradient = RadialGradient(
            cx - radius * 0.28f,
            cy - radius * 0.3f,
            radius * 1.5f,
            intArrayOf(
                Color.rgb(65, 205, 255),
                Color.rgb(24, 75, 165),
                Color.rgb(12, 12, 48)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        orbPaint.shader = globeGradient
        canvas.drawCircle(
            cx,
            cy,
            radius,
            orbPaint
        )
        orbPaint.shader = null

        ringPaint.color = cyan
        ringPaint.alpha = 210
        ringPaint.strokeWidth = 2.5f

        canvas.drawCircle(
            cx,
            cy,
            radius,
            ringPaint
        )

        canvas.save()
        canvas.rotate(rotation, cx, cy)

        ringPaint.color = purple
        ringPaint.alpha = 230
        ringPaint.strokeWidth = 2.2f

        val ovalRect = RectF(
            cx - radius * 1.25f,
            cy - radius * 0.38f,
            cx + radius * 1.25f,
            cy + radius * 0.38f
        )

        canvas.drawOval(
            ovalRect,
            ringPaint
        )

        canvas.restore()

        ringPaint.color = cyan
        ringPaint.alpha = 170
        ringPaint.strokeWidth = 1.5f

        canvas.drawCircle(
            cx,
            cy,
            radius * 1.18f,
            ringPaint
        )

        canvas.drawText(
            "IDRAAK",
            cx,
            cy + 5f,
            textPaint
        )

        canvas.drawText(
            "QUANTUM CORE",
            cx,
            cy + radius * 0.52f,
            smallTextPaint
        )

        canvas.restore()

        rotation += 1.2f
        if (rotation >= 360f) {
            rotation = 0f
        }

        postInvalidateDelayed(35)
    }
}
