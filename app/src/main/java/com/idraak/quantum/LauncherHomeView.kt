
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
    private lateinit var orbView: LiveOrbView

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
            setTextColor(Color.rgb(0, 220, 255))
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

        orbView = LiveOrbView(context)

        val longPressHandler = Handler(Looper.getMainLooper())
        var startX = 0f
        var startY = 0f

        val longPressAction = Runnable {
            onSettingsClick()
        }

        orbView.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
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
            orbView,
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
            Color.rgb(155, 75, 255)
        ) {
            onSettingsClick()
        }

        voiceButton = createActionButton(
            "🎙 ACTIVATE",
            Color.rgb(0, 220, 255)
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

    fun setAvatarGender(gender: String) {
        if (::orbView.isInitialized) {
            orbView.setAvatarGender(gender)
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

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(
            "sans-serif-black",
            Typeface.BOLD
        )
        textSize = 12f
    }

    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 225, 255)
        textAlign = Paint.Align.CENTER
        textSize = 8f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var rotation = 0f
    private var avatarGender = "male"

    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(155, 65, 255)

    fun setAvatarGender(gender: String) {
        avatarGender = if (
            gender.equals("female", true) ||
            gender.equals("महिला", true)
        ) {
            "female"
        } else {
            "male"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.37f

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

        canvas.drawOval(
            RectF(
                cx - radius * 1.25f,
                cy - radius * 0.38f,
                cx + radius * 1.25f,
                cy + radius * 0.38f
            ),
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

        drawAvatar(canvas, cx, cy, radius)

        canvas.drawText(
            "IDRAAK",
            cx,
            cy + radius * 0.72f,
            textPaint
        )

        canvas.drawText(
            if (avatarGender == "female") {
                "FEMALE AI CORE"
            } else {
                "MALE AI CORE"
            },
            cx,
            cy + radius * 0.93f,
            smallTextPaint
        )

        rotation += 1.2f
        if (rotation >= 360f) {
            rotation = 0f
        }

        postInvalidateDelayed(35)
    }

    private fun drawAvatar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        val scale = radius / 70f

        canvas.save()
        canvas.translate(cx, cy - radius * 0.12f)
        canvas.scale(scale, scale)

        avatarPaint.shader = LinearGradient(
            -25f,
            -35f,
            25f,
            50f,
            intArrayOf(
                Color.rgb(190, 250, 255),
                Color.rgb(45, 175, 255),
                Color.rgb(80, 70, 220)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        avatarPaint.style = Paint.Style.FILL

        // Neck
        canvas.drawRoundRect(
            RectF(-9f, 19f, 9f, 38f),
            5f,
            5f,
            avatarPaint
        )

        // Shoulders
        val shoulders = Path().apply {
            moveTo(-9f, 30f)
            cubicTo(-28f, 32f, -38f, 43f, -40f, 55f)
            lineTo(40f, 55f)
            cubicTo(38f, 43f, 28f, 32f, 9f, 30f)
            close()
        }
        canvas.drawPath(shoulders, avatarPaint)

        // Head
        canvas.drawOval(
            RectF(-20f, -28f, 20f, 23f),
            avatarPaint
        )

        avatarPaint.shader = null

        // Hair style
        avatarPaint.color = if (avatarGender == "female") {
            Color.rgb(25, 35, 100)
        } else {
            Color.rgb(12, 30, 75)
        }

        val hair = Path()

        if (avatarGender == "female") {
            hair.moveTo(-20f, -4f)
            hair.quadTo(-28f, -37f, 0f, -35f)
            hair.quadTo(28f, -37f, 20f, -4f)
            hair.lineTo(25f, 32f)
            hair.quadTo(17f, 24f, 17f, 10f)
            hair.lineTo(17f, -15f)
            hair.quadTo(0f, -4f, -17f, -15f)
            hair.lineTo(-17f, 10f)
            hair.quadTo(-17f, 24f, -25f, 32f)
            hair.close()
        } else {
            hair.moveTo(-20f, -10f)
            hair.quadTo(-24f, -35f, 0f, -34f)
            hair.quadTo(24f, -35f, 20f, -10f)
            hair.quadTo(5f, -19f, -8f, -12f)
            hair.close()
        }

        canvas.drawPath(hair, avatarPaint)

        // Glowing eyes
        avatarPaint.color = cyan
        avatarPaint.strokeWidth = 2.5f
        avatarPaint.style = Paint.Style.STROKE

        canvas.drawLine(
            -11f, 0f, -5f, 0f, avatarPaint
        )
        canvas.drawLine(
            5f, 0f, 11f, 0f, avatarPaint
        )

        avatarPaint.style = Paint.Style.FILL
        avatarPaint.color = Color.rgb(190, 250, 255)

        canvas.drawCircle(-8f, 0f, 2f, avatarPaint)
        canvas.drawCircle(8f, 0f, 2f, avatarPaint)

        // Face light
        avatarPaint.color = Color.argb(180, 0, 225, 255)
        avatarPaint.strokeWidth = 1.2f
        avatarPaint.style = Paint.Style.STROKE

        canvas.drawArc(
            RectF(-7f, 7f, 7f, 15f),
            10f,
            160f,
            false,
            avatarPaint
        )

        avatarPaint.style = Paint.Style.FILL
        canvas.restore()
    }
}
