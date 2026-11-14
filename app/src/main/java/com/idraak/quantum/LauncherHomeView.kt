
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
    private lateinit var appGrid: GridView
    private lateinit var orbView: LiveOrbView
    private lateinit var drawerTitle: TextView

    private var drawerOpen = false

    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(155, 75, 255)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(18, 20, 18, 14)

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(2, 5, 20),
                Color.rgb(7, 12, 35),
                Color.rgb(18, 5, 42)
            )
        )

        buildInterface()
        reloadApps()
    }

    private fun buildInterface() {

        val header = TextView(context).apply {
            text = "I D R A A K"
            textSize = 29f
            setTextColor(cyan)
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
            setShadowLayer(18f, 0f, 0f, cyan)
        }

        addView(
            header,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(context).apply {
            text = "QUANTUM AI • PERSONAL INTELLIGENCE"
            textSize = 9f
            setTextColor(Color.rgb(150, 175, 220))
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            setPadding(0, 5, 0, 18)
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
        var moved = false

        val longPressAction = Runnable {
            onSettingsClick()
        }

        orbView.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    moved = false

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
                        moved = true
                        longPressHandler.removeCallbacks(
                            longPressAction
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(
                        longPressAction
                    )

                    val deltaY = event.y - startY

                    if (moved && deltaY < -45) {
                        showDrawer()
                    } else if (moved && deltaY > 45) {
                        hideDrawer()
                    }

                    true
                }

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
            LayoutParams(
                min(
                    resources.displayMetrics.widthPixels - 50,
                    300
                ),
                min(
                    resources.displayMetrics.widthPixels - 50,
                    300
                )
            ).apply {
                gravity = Gravity.CENTER
                topMargin = 8
                bottomMargin = 8
            }
        )

        statusText = TextView(context).apply {
            text = "● IDRAAK STANDBY"
            textSize = 13f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, 10, 0, 5)
        }

        addView(
            statusText,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val instruction = TextView(context).apply {
            text = "VOICE CORE READY"
            textSize = 10f
            setTextColor(Color.rgb(130, 155, 205))
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
            setPadding(0, 4, 0, 16)
        }

        addView(
            instruction,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        drawerTitle = TextView(context).apply {
            text = "APPLICATION MATRIX"
            textSize = 12f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 12, 0, 8)
            visibility = View.GONE
        }

        addView(
            drawerTitle,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        searchBox = EditText(context).apply {
            hint = "Search applications..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(16, 8, 16, 8)

            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 22, 48))
                cornerRadius = 24f
                setStroke(2, Color.rgb(0, 150, 210))
            }

            visibility = View.GONE
        }

        addView(
            searchBox,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                46
            ).apply {
                bottomMargin = 8
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
            visibility = View.GONE
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
                    appAdapter.updateApps(
                        engine.searchApps(
                            s?.toString() ?: "",
                            allApps
                        )
                    )
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )

        val drawerButton = TextView(context).apply {
            text = "⌃   APPLICATIONS   ⌃"
            textSize = 11f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setPadding(15, 13, 15, 13)

            background = GradientDrawable().apply {
                setColor(Color.rgb(10, 20, 45))
                cornerRadius = 28f
                setStroke(1, cyan)
            }

            setOnClickListener {
                if (drawerOpen) hideDrawer() else showDrawer()
            }
        }

        addView(
            drawerButton,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                48
            ).apply {
                topMargin = 8
                bottomMargin = 8
            }
        )

        val footer = TextView(context).apply {
            text = "IDRAAK QUANTUM CORE  •  SYSTEM ONLINE"
            textSize = 8f
            setTextColor(Color.rgb(95, 120, 170))
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
            setPadding(0, 6, 0, 2)
        }

        addView(
            footer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun showDrawer() {
        drawerOpen = true
        drawerTitle.visibility = View.VISIBLE
        searchBox.visibility = View.VISIBLE
        appGrid.visibility = View.VISIBLE
    }

    private fun hideDrawer() {
        drawerOpen = false
        drawerTitle.visibility = View.GONE
        searchBox.visibility = View.GONE
        appGrid.visibility = View.GONE
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
                appAdapter.updateApps(
                    engine.searchApps(
                        if (::searchBox.isInitialized) {
                            searchBox.text.toString()
                        } else {
                            ""
                        },
                        allApps
                    )
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
        setLiveStatus(
            if (active) {
                "● IDRAAK VOICE ACTIVE"
            } else {
                "● IDRAAK STANDBY"
            }
        )
    }
}

class LiveOrbView(context: Context) : View(context) {

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
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
    private var pulse = 0f
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
        val radius = min(width, height) * 0.34f

        pulse += 0.045f
        val pulseSize = 1f + 0.035f * kotlin.math.sin(pulse)

        val glow = RadialGradient(
            cx,
            cy,
            radius * 2f,
            intArrayOf(
                Color.argb(100, 0, 220, 255),
                Color.argb(45, 100, 50, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        orbPaint.shader = glow
        canvas.drawCircle(cx, cy, radius * 2f, orbPaint)
        orbPaint.shader = null

        val globeGradient = RadialGradient(
            cx - radius * 0.28f,
            cy - radius * 0.3f,
            radius * 1.5f,
            intArrayOf(
                Color.rgb(75, 220, 255),
                Color.rgb(28, 80, 175),
                Color.rgb(8, 10, 42)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        orbPaint.shader = globeGradient
        canvas.drawCircle(
            cx,
            cy,
            radius * pulseSize,
            orbPaint
        )
        orbPaint.shader = null

        ringPaint.color = cyan
        ringPaint.alpha = 220
        ringPaint.strokeWidth = 2.5f

        canvas.drawCircle(
            cx,
            cy,
            radius * 1.08f,
            ringPaint
        )

        canvas.save()
        canvas.rotate(rotation, cx, cy)

        ringPaint.color = purple
        ringPaint.alpha = 230
        ringPaint.strokeWidth = 2.2f

        canvas.drawOval(
            RectF(
                cx - radius * 1.42f,
                cy - radius * 0.42f,
                cx + radius * 1.42f,
                cy + radius * 0.42f
            ),
            ringPaint
        )

        canvas.restore()

        ringPaint.color = cyan
        ringPaint.alpha = 150
        ringPaint.strokeWidth = 1.4f

        canvas.drawCircle(
            cx,
            cy,
            radius * 1.27f,
            ringPaint
        )

        drawAvatar(canvas, cx, cy, radius)

        canvas.drawText(
            "IDRAAK",
            cx,
            cy + radius * 0.78f,
            textPaint
        )

        canvas.drawText(
            if (avatarGender == "female") {
                "FEMALE AI CORE"
            } else {
                "MALE AI CORE"
            },
            cx,
            cy + radius * 1.02f,
            smallTextPaint
        )

        rotation += 1.1f
        if (rotation >= 360f) rotation = 0f

        postInvalidateDelayed(40)
    }

    private fun drawAvatar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        val scale = radius / 70f

        canvas.save()
        canvas.translate(cx, cy - radius * 0.10f)
        canvas.scale(scale, scale)

        avatarPaint.shader = LinearGradient(
            -25f,
            -35f,
            25f,
            50f,
            intArrayOf(
                Color.rgb(200, 250, 255),
                Color.rgb(45, 175, 255),
                Color.rgb(80, 70, 220)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        avatarPaint.style = Paint.Style.FILL

        canvas.drawRoundRect(
            RectF(-9f, 19f, 9f, 38f),
            5f,
            5f,
            avatarPaint
        )

        val shoulders = Path().apply {
            moveTo(-9f, 30f)
            cubicTo(-28f, 32f, -38f, 43f, -40f, 55f)
            lineTo(40f, 55f)
            cubicTo(38f, 43f, 28f, 32f, 9f, 30f)
            close()
        }

        canvas.drawPath(shoulders, avatarPaint)

        canvas.drawOval(
            RectF(-20f, -28f, 20f, 23f),
            avatarPaint
        )

        avatarPaint.shader = null

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

        avatarPaint.color = cyan
        avatarPaint.strokeWidth = 2.5f
        avatarPaint.style = Paint.Style.STROKE

        canvas.drawLine(-11f, 0f, -5f, 0f, avatarPaint)
        canvas.drawLine(5f, 0f, 11f, 0f, avatarPaint)

        avatarPaint.style = Paint.Style.FILL
        avatarPaint.color = Color.rgb(190, 250, 255)

        canvas.drawCircle(-8f, 0f, 2f, avatarPaint)
        canvas.drawCircle(8f, 0f, 2f, avatarPaint)

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
