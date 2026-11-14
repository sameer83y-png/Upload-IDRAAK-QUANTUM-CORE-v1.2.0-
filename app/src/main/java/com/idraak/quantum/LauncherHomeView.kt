
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
import kotlin.math.sin
import kotlin.math.cos

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
            text = "SAY IDRAAK TO ACTIVATE"
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
            text = "IDRAAK QUANTUM CORE • SYSTEM ONLINE"
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
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var rotation = 0f
    private var pulse = 0f
    private var avatarGender = "male"

    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(155, 65, 255)
    private val paleBlue = Color.rgb(190, 250, 255)

    init {
        contentDescription = "IDRAAK holographic AI globe"
    }

    fun setAvatarGender(gender: String) {
        avatarGender =
            if (
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
        val radius = min(width, height) * 0.31f

        pulse += 0.045f
        val breathing = 1f + 0.025f * sin(pulse)

        drawAtmosphere(canvas, cx, cy, radius)
        drawGlobe(canvas, cx, cy, radius, breathing)
        drawOrbitRings(canvas, cx, cy, radius)
        drawHolographicAvatar(canvas, cx, cy, radius)
        drawInterfaceDetails(canvas, cx, cy, radius)

        rotation += 0.65f
        if (rotation >= 360f) rotation = 0f

        postInvalidateDelayed(40)
    }

    private fun drawAtmosphere(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        orbPaint.shader = RadialGradient(
            cx,
            cy,
            radius * 2.25f,
            intArrayOf(
                Color.argb(100, 0, 225, 255),
                Color.argb(45, 100, 50, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            cx,
            cy,
            radius * 2.25f,
            orbPaint
        )

        orbPaint.shader = null
    }

    private fun drawGlobe(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        breathing: Float
    ) {
        orbPaint.shader = RadialGradient(
            cx - radius * 0.3f,
            cy - radius * 0.35f,
            radius * 1.65f,
            intArrayOf(
                Color.rgb(40, 150, 205),
                Color.rgb(15, 55, 125),
                Color.rgb(5, 10, 40)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            cx,
            cy,
            radius * breathing,
            orbPaint
        )

        orbPaint.shader = null

        orbPaint.style = Paint.Style.STROKE
        orbPaint.strokeWidth = 1.5f
        orbPaint.color = Color.argb(180, 0, 225, 255)

        canvas.drawCircle(
            cx,
            cy,
            radius * breathing,
            orbPaint
        )

        orbPaint.style = Paint.Style.FILL

        canvas.save()
        canvas.clipPath(
            Path().apply {
                addCircle(cx, cy, radius * 0.96f, Path.Direction.CW)
            }
        )

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 100, 230, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        for (i in -3..3) {
            val offset = i * radius * 0.25f
            canvas.drawLine(
                cx - radius,
                cy + offset,
                cx + radius,
                cy + offset,
                gridPaint
            )
            canvas.drawLine(
                cx + offset,
                cy - radius,
                cx + offset,
                cy + radius,
                gridPaint
            )
        }

        canvas.restore()
    }

    private fun drawOrbitRings(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeCap = Paint.Cap.ROUND
        ringPaint.strokeWidth = 2.2f
        ringPaint.color = cyan
        ringPaint.alpha = 210

        canvas.drawCircle(
            cx,
            cy,
            radius * 1.12f,
            ringPaint
        )

        canvas.save()
        canvas.rotate(rotation, cx, cy)

        ringPaint.color = purple
        ringPaint.alpha = 230
        ringPaint.strokeWidth = 2.4f

        canvas.drawOval(
            RectF(
                cx - radius * 1.48f,
                cy - radius * 0.42f,
                cx + radius * 1.48f,
                cy + radius * 0.42f
            ),
            ringPaint
        )

        ringPaint.color = paleBlue
        ringPaint.strokeWidth = 1.1f
        ringPaint.alpha = 170

        canvas.drawOval(
            RectF(
                cx - radius * 1.35f,
                cy - radius * 0.25f,
                cx + radius * 1.35f,
                cy + radius * 0.25f
            ),
            ringPaint
        )

        canvas.restore()

        ringPaint.color = cyan
        ringPaint.strokeWidth = 1.3f
        ringPaint.alpha = 120

        canvas.drawCircle(
            cx,
            cy,
            radius * 1.34f,
            ringPaint
        )

        ringPaint.style = Paint.Style.FILL

        val dotAngle = Math.toRadians(rotation.toDouble())
        val dotX = cx + cos(dotAngle).toFloat() * radius * 1.48f
        val dotY = cy + sin(dotAngle).toFloat() * radius * 0.42f

        orbPaint.color = paleBlue
        canvas.drawCircle(dotX, dotY, 4f, orbPaint)
    }

    private fun drawHolographicAvatar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        val scale = radius / 72f

        canvas.save()
        canvas.translate(cx, cy - radius * 0.04f)
        canvas.scale(scale, scale)

        drawAvatarGlow(canvas)
        drawShoulders(canvas)
        drawNeck(canvas)
        drawFace(canvas)
        drawHair(canvas)
        drawFaceDetails(canvas)
        drawCircuitLines(canvas)

        canvas.restore()
    }

    private fun drawAvatarGlow(canvas: Canvas) {
        facePaint.shader = RadialGradient(
            0f,
            -5f,
            72f,
            intArrayOf(
                Color.argb(75, 0, 235, 255),
                Color.argb(25, 0, 100, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(0f, 0f, 72f, facePaint)
        facePaint.shader = null
    }

    private fun drawShoulders(canvas: Canvas) {
        val shoulders = Path().apply {
            moveTo(-12f, 34f)
            cubicTo(-40f, 38f, -48f, 49f, -52f, 65f)
            lineTo(52f, 65f)
            cubicTo(48f, 49f, 40f, 38f, 12f, 34f)
            close()
        }

        facePaint.shader = LinearGradient(
            -40f,
            30f,
            40f,
            65f,
            intArrayOf(
                Color.argb(240, 110, 245, 255),
                Color.argb(210, 20, 115, 210),
                Color.argb(230, 70, 55, 190)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(shoulders, facePaint)
        facePaint.shader = null

        facePaint.style = Paint.Style.STROKE
        facePaint.strokeWidth = 1.5f
        facePaint.color = paleBlue
        facePaint.alpha = 190
        canvas.drawPath(shoulders, facePaint)
        facePaint.style = Paint.Style.FILL
    }

    private fun drawNeck(canvas: Canvas) {
        facePaint.shader = LinearGradient(
            -10f,
            15f,
            10f,
            40f,
            intArrayOf(
                paleBlue,
                Color.rgb(30, 140, 220),
                Color.rgb(20, 45, 125)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            RectF(-11f, 18f, 11f, 43f),
            5f,
            5f,
            facePaint
        )

        facePaint.shader = null
    }

    private fun drawFace(canvas: Canvas) {
        facePaint.shader = LinearGradient(
            -25f,
            -35f,
            25f,
            28f,
            intArrayOf(
                Color.rgb(220, 255, 255),
                Color.rgb(95, 205, 245),
                Color.rgb(25, 95, 180)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawOval(
            RectF(-24f, -34f, 24f, 27f),
            facePaint
        )

        facePaint.shader = null

        facePaint.style = Paint.Style.STROKE
        facePaint.strokeWidth = 1.3f
        facePaint.color = paleBlue
        facePaint.alpha = 220

        canvas.drawOval(
            RectF(-24f, -34f, 24f, 27f),
            facePaint
        )

        facePaint.style = Paint.Style.FILL
    }

    private fun drawHair(canvas: Canvas) {
        facePaint.color = Color.rgb(8, 24, 65)

        val hair = Path()

        if (avatarGender == "female") {
            hair.moveTo(-24f, -9f)
            hair.quadTo(-32f, -43f, 0f, -43f)
            hair.quadTo(32f, -43f, 24f, -8f)
            hair.lineTo(27f, 44f)
            hair.quadTo(19f, 37f, 18f, 15f)
            hair.lineTo(18f, -20f)
            hair.quadTo(0f, -8f, -18f, -20f)
            hair.lineTo(-18f, 15f)
            hair.quadTo(-19f, 37f, -27f, 44f)
            hair.close()
        } else {
            hair.moveTo(-23f, -15f)
            hair.quadTo(-28f, -43f, 0f, -40f)
            hair.quadTo(28f, -43f, 23f, -15f)
            hair.quadTo(10f, -25f, -7f, -19f)
            hair.quadTo(-16f, -17f, -23f, -15f)
            hair.close()
        }

        canvas.drawPath(hair, facePaint)

        facePaint.style = Paint.Style.STROKE
        facePaint.strokeWidth = 1.1f
        facePaint.color = Color.argb(190, 0, 225, 255)
        canvas.drawPath(hair, facePaint)
        facePaint.style = Paint.Style.FILL
    }

    private fun drawFaceDetails(canvas: Canvas) {
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeWidth = 2f
        linePaint.color = cyan
        linePaint.alpha = 230

        canvas.drawLine(-13f, -2f, -5f, -2f, linePaint)
        canvas.drawLine(5f, -2f, 13f, -2f, linePaint)

        linePaint.style = Paint.Style.FILL
        linePaint.color = Color.WHITE

        canvas.drawCircle(-9f, -2f, 2f, linePaint)
        canvas.drawCircle(9f, -2f, 2f, linePaint)

        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 1.5f
        linePaint.color = paleBlue

        val nose = Path().apply {
            moveTo(0f, 0f)
            lineTo(-2f, 9f)
            lineTo(2f, 10f)
        }

        canvas.drawPath(nose, linePaint)

        val mouth = Path().apply {
            moveTo(-6f, 17f)
            quadTo(0f, 21f, 6f, 17f)
        }

        canvas.drawPath(mouth, linePaint)

        linePaint.style = Paint.Style.FILL
    }

    private fun drawCircuitLines(canvas: Canvas) {
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 1.1f
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.color = cyan
        linePaint.alpha = 190

        val leftCircuit = Path().apply {
            moveTo(-20f, 2f)
            lineTo(-29f, 8f)
            lineTo(-29f, 18f)
            lineTo(-36f, 23f)
        }

        val rightCircuit = Path().apply {
            moveTo(20f, 2f)
            lineTo(29f, 8f)
            lineTo(29f, 18f)
            lineTo(36f, 23f)
        }

        canvas.drawPath(leftCircuit, linePaint)
        canvas.drawPath(rightCircuit, linePaint)

        linePaint.style = Paint.Style.FILL

        canvas.drawCircle(-36f, 23f, 2f, linePaint)
        canvas.drawCircle(36f, 23f, 2f, linePaint)

        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 0.8f
        linePaint.alpha = 130

        canvas.drawArc(
            RectF(-18f, -27f, 18f, 25f),
            205f,
            130f,
            false,
            linePaint
        )

        linePaint.style = Paint.Style.FILL
    }

    private fun drawInterfaceDetails(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(
            "sans-serif-black",
            Typeface.BOLD
        )
        textPaint.color = Color.WHITE
        textPaint.textSize = 12f
        textPaint.setShadowLayer(10f, 0f, 0f, cyan)

        canvas.drawText(
            "IDRAAK",
            cx,
            cy + radius * 0.80f,
            textPaint
        )

        textPaint.clearShadowLayer()
        textPaint.color = cyan
        textPaint.textSize = 8f
        textPaint.letterSpacingCompat()

        canvas.drawText(
            if (avatarGender == "female") {
                "FEMALE AI CORE"
            } else {
                "MALE AI CORE"
            },
            cx,
            cy + radius * 1.05f,
            textPaint
        )

        textPaint.color = Color.argb(180, 150, 220, 255)
        textPaint.textSize = 7f

        canvas.drawText(
            "QUANTUM HOLOGRAPHIC INTERFACE",
            cx,
            cy + radius * 1.55f,
            textPaint
        )
    }

    private fun Paint.letterSpacingCompat() {
        // Canvas text spacing is kept compatible with Android 9.
    }
}
