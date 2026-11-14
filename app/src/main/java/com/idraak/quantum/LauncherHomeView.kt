
package com.idraak.quantum

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos

@Suppress("UNUSED_PARAMETER")
class LauncherHomeView(
    context: Context,
    private val onSettingsClick: () -> Unit,
    onVoiceClick: () -> Unit
) : LinearLayout(context) {

    private lateinit var statusText: TextView
    private lateinit var orbView: LiveOrbView

    private val cyan = Color.rgb(0, 225, 255)

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER
        setPadding(dp(18), dp(20), dp(18), dp(14))

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(2, 5, 20),
                Color.rgb(7, 12, 35),
                Color.rgb(18, 5, 42)
            )
        )

        buildInterface()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildInterface() {

        val header = TextView(context).apply {
            text = "I D R A A K"
            textSize = 29f
            setTextColor(cyan)
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            gravity = android.view.Gravity.CENTER
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
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.14f
            setPadding(0, dp(5), 0, dp(12))
        }

        addView(
            subtitle,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        orbView = LiveOrbView(context)

        val handler = Handler(Looper.getMainLooper())
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        var startX = 0f
        var startY = 0f
        var moved = false
        var longPressTriggered = false

        val longPressAction = Runnable {
            if (!moved) {
                longPressTriggered = true
                onSettingsClick()
            }
        }

        orbView.setOnTouchListener { _, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    moved = false
                    longPressTriggered = false

                    handler.removeCallbacks(longPressAction)
                    handler.postDelayed(longPressAction, 2000L)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (
                        kotlin.math.abs(event.x - startX) > touchSlop ||
                        kotlin.math.abs(event.y - startY) > touchSlop
                    ) {
                        moved = true
                        handler.removeCallbacks(longPressAction)
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressAction)
                    true
                }

                else -> true
            }
        }

        val screenWidth =
            resources.displayMetrics.widthPixels

        val orbSize = min(
            screenWidth - dp(36),
            dp(310)
        )

        addView(
            orbView,
            LayoutParams(orbSize, orbSize).apply {
                gravity = android.view.Gravity.CENTER
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        )

        statusText = TextView(context).apply {
            text = "● IDRAAK STANDBY"
            textSize = 13f
            setTextColor(cyan)
            gravity = android.view.Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, dp(10), 0, dp(5))
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
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.16f
            setPadding(0, dp(5), 0, dp(16))
        }

        addView(
            instruction,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val footer = TextView(context).apply {
            text = "IDRAAK QUANTUM CORE • VOICE CONTROL"
            textSize = 8f
            setTextColor(Color.rgb(95, 120, 170))
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.08f
            setPadding(0, dp(6), 0, dp(2))
        }

        addView(
            footer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )
    }

    fun setAvatarGender(gender: String) {
        if (::orbView.isInitialized) {
            orbView.setAvatarGender(gender)
        }
    }

    fun reloadApps() {
        // Apps are opened through MainActivity voice commands.
        // No touch-based app drawer is displayed.
    }

    fun setLiveStatus(status: String) {
        if (::statusText.isInitialized) {
            statusText.text = status
        }
    }

    fun setVoiceStatus(active: Boolean) {
        if (::orbView.isInitialized) {
            orbView.setListening(active)
        }
    }

    override fun onDetachedFromWindow() {
        if (::orbView.isInitialized) {
            orbView.stopAnimation()
        }
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (::orbView.isInitialized) {
            orbView.startAnimation()
        }
    }
}

class LiveOrbView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var rotation = 0f
    private var pulse = 0f
    private var avatarGender = "male"
    private var listening = false
    private var animationRunning = false

    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(155, 65, 255)
    private val paleBlue = Color.rgb(190, 250, 255)

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!animationRunning) return

            rotation += 0.65f
            if (rotation >= 360f) rotation = 0f

            pulse += 0.045f
            invalidate()

            postDelayed(this, 40L)
        }
    }

    init {
        contentDescription =
            "IDRAAK holographic AI globe. Long press for settings."
        startAnimation()
    }

    fun setAvatarGender(gender: String) {
        avatarGender =
            if (
                gender.equals("female", true) ||
                gender.equals("महिला", true)
            ) "female" else "male"

        invalidate()
    }

    fun setListening(active: Boolean) {
        listening = active
        invalidate()
    }

    fun startAnimation() {
        if (!animationRunning) {
            animationRunning = true
            removeCallbacks(animationRunnable)
            post(animationRunnable)
        }
    }

    fun stopAnimation() {
        animationRunning = false
        removeCallbacks(animationRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.31f

        drawAtmosphere(canvas, cx, cy, radius)
        drawGlobe(canvas, cx, cy, radius)
        drawOrbit(canvas, cx, cy, radius)
        drawAvatar(canvas, cx, cy, radius)
        drawLabels(canvas, cx, cy, radius)
    }

    private fun drawAtmosphere(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        paint.shader = RadialGradient(
            cx,
            cy,
            radius * 2.2f,
            intArrayOf(
                if (listening) Color.argb(125, 0, 255, 255)
                else Color.argb(85, 0, 225, 255),
                Color.argb(35, 100, 50, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, radius * 2.2f, paint)
        paint.shader = null
    }

    private fun drawGlobe(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        val breathing = 1f + 0.025f * sin(pulse)

        paint.shader = RadialGradient(
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
            paint
        )

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.color = if (listening) Color.WHITE else cyan
        canvas.drawCircle(cx, cy, radius * breathing, paint)
        paint.style = Paint.Style.FILL

        canvas.save()
        val clip = Path().apply {
            addCircle(cx, cy, radius * 0.96f, Path.Direction.CW)
        }
        canvas.clipPath(clip)

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 100, 230, 255)
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
                grid
            )

            canvas.drawLine(
                cx + offset,
                cy - radius,
                cx + offset,
                cy + radius,
                grid
            )
        }

        canvas.restore()
    }

    private fun drawOrbit(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = cyan
        paint.alpha = 210

        canvas.drawCircle(cx, cy, radius * 1.12f, paint)

        canvas.save()
        canvas.rotate(rotation, cx, cy)

        paint.color = purple
        paint.alpha = 230
        paint.strokeWidth = 2.4f

        canvas.drawOval(
            RectF(
                cx - radius * 1.48f,
                cy - radius * 0.42f,
                cx + radius * 1.48f,
                cy + radius * 0.42f
            ),
            paint
        )

        paint.color = paleBlue
        paint.strokeWidth = 1.1f

        canvas.drawOval(
            RectF(
                cx - radius * 1.35f,
                cy - radius * 0.25f,
                cx + radius * 1.35f,
                cy + radius * 0.25f
            ),
            paint
        )

        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.color = paleBlue

        val angle = Math.toRadians(rotation.toDouble())

        canvas.drawCircle(
            cx + cos(angle).toFloat() * radius * 1.48f,
            cy + sin(angle).toFloat() * radius * 0.42f,
            4f,
            paint
        )
    }

    private fun drawAvatar(
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
        drawHead(canvas)
        drawHair(canvas)
        drawFaceDetails(canvas)
        drawCircuits(canvas)

        canvas.restore()
    }

    private fun drawAvatarGlow(canvas: Canvas) {
        paint.shader = RadialGradient(
            0f,
            -5f,
            72f,
            intArrayOf(
                Color.argb(85, 0, 235, 255),
                Color.argb(25, 0, 100, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(0f, 0f, 72f, paint)
        paint.shader = null
    }

    private fun drawShoulders(canvas: Canvas) {
        val body = Path().apply {
            moveTo(-12f, 34f)
            cubicTo(-40f, 38f, -48f, 49f, -52f, 65f)
            lineTo(52f, 65f)
            cubicTo(48f, 49f, 40f, 38f, 12f, 34f)
            close()
        }

        paint.shader = LinearGradient(
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

        canvas.drawPath(body, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = paleBlue
        canvas.drawPath(body, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawNeck(canvas: Canvas) {
        paint.shader = LinearGradient(
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
            paint
        )

        paint.shader = null
    }

    private fun drawHead(canvas: Canvas) {
        paint.shader = LinearGradient(
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
            paint
        )

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f
        paint.color = paleBlue
        canvas.drawOval(
            RectF(-24f, -34f, 24f, 27f),
            paint
        )
        paint.style = Paint.Style.FILL
    }

    private fun drawHair(canvas: Canvas) {
        paint.color = Color.rgb(8, 24, 65)

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

        canvas.drawPath(hair, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f
        paint.color = cyan
        canvas.drawPath(hair, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawFaceDetails(canvas: Canvas) {
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeWidth = 2f
        linePaint.color = cyan

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

    private fun drawCircuits(canvas: Canvas) {
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 1.1f
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.color = cyan

        val left = Path().apply {
            moveTo(-20f, 2f)
            lineTo(-29f, 8f)
            lineTo(-29f, 18f)
            lineTo(-36f, 23f)
        }

        val right = Path().apply {
            moveTo(20f, 2f)
            lineTo(29f, 8f)
            lineTo(29f, 18f)
            lineTo(36f, 23f)
        }

        canvas.drawPath(left, linePaint)
        canvas.drawPath(right, linePaint)

        linePaint.style = Paint.Style.FILL
        canvas.drawCircle(-36f, 23f, 2f, linePaint)
        canvas.drawCircle(36f, 23f, 2f, linePaint)

        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 0.8f

        canvas.drawArc(
            RectF(-18f, -27f, 18f, 25f),
            205f,
            130f,
            false,
            linePaint
        )

        linePaint.style = Paint.Style.FILL
    }

    private fun drawLabels(
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
        textPaint.color = if (listening) Color.WHITE else cyan
        textPaint.textSize = 8f

        canvas.drawText(
            if (listening) "VOICE SYSTEM ACTIVE"
            else "VOICE SYSTEM STANDBY",
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
}
