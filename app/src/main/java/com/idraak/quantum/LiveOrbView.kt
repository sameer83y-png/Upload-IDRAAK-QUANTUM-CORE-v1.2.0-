package com.idraak.quantum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * IDRAAK AI Core Orb
 *
 * जिम्मेदारी:
 * - Orb rendering
 * - Visual waveform
 * - Listening state
 * - Orb animation
 *
 * इसमें voice recognition या command execution नहीं है।
 */
class LiveOrbView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(155, 65, 255)

    private val density =
        resources.displayMetrics.density

    private var rotation = 0f
    private var pulse = 0f

    private var listening = false
    private var animationRunning = false

    private fun dp(value: Float): Float {
        return value * density
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!animationRunning || !isAttachedToWindow) {
                return
            }

            rotation = (rotation + 1.2f) % 360f
            pulse += 0.055f

            invalidate()
            postDelayed(this, 35L)
        }
    }

    init {
        contentDescription =
            "IDRAAK AI Control Core. Long press for settings."

        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setListening(active: Boolean) {
        if (listening == active) return

        listening = active
        invalidate()
    }

    fun startAnimation() {
        if (!isAttachedToWindow || animationRunning) {
            return
        }

        animationRunning = true

        removeCallbacks(animationRunnable)
        post(animationRunnable)
    }

    fun stopAnimation() {
        animationRunning = false
        removeCallbacks(animationRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        val cx = width / 2f
        val cy = height / 2f

        val radius = min(width, height) * 0.29f

        if (radius <= 0f) return

        drawGlow(canvas, cx, cy, radius)
        drawRings(canvas, cx, cy, radius)
        drawCore(canvas, cx, cy, radius)
        drawWaveform(canvas, cx, cy, radius)
        drawText(canvas, cx, cy, radius)
    }

    private fun drawGlow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        paint.style = Paint.Style.FILL

        paint.shader = RadialGradient(
            cx,
            cy,
            radius * 2.15f,
            intArrayOf(
                if (listening) {
                    Color.argb(110, 0, 255, 255)
                } else {
                    Color.argb(65, 0, 160, 255)
                },
                Color.argb(25, 115, 50, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, radius * 2.15f, paint)
        paint.shader = null
    }

    private fun drawRings(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = cyan
        paint.alpha = 175

        canvas.drawCircle(
            cx,
            cy,
            radius * (1.38f + 0.025f * sin(pulse)),
            paint
        )

        canvas.save()
        canvas.rotate(rotation, cx, cy)

        paint.color = purple
        paint.strokeWidth = dp(2.3f)

        canvas.drawOval(
            RectF(
                cx - radius * 1.65f,
                cy - radius * 0.55f,
                cx + radius * 1.65f,
                cy + radius * 0.55f
            ),
            paint
        )

        paint.color = Color.WHITE
        paint.strokeWidth = dp(1f)

        canvas.drawOval(
            RectF(
                cx - radius * 1.45f,
                cy - radius * 0.38f,
                cx + radius * 1.45f,
                cy + radius * 0.38f
            ),
            paint
        )

        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.color = cyan
        paint.alpha = 255

        val angle = Math.toRadians(rotation.toDouble())

        canvas.drawCircle(
            cx + cos(angle).toFloat() * radius * 1.65f,
            cy + sin(angle).toFloat() * radius * 0.55f,
            dp(4.5f),
            paint
        )
    }

    private fun drawCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        val breathing = 1f + 0.035f * sin(pulse)

        paint.style = Paint.Style.FILL

        paint.shader = RadialGradient(
            cx - radius * 0.25f,
            cy - radius * 0.3f,
            radius * 1.45f,
            intArrayOf(
                Color.rgb(35, 115, 175),
                Color.rgb(12, 45, 105),
                Color.rgb(3, 10, 35)
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
        paint.strokeWidth = dp(2f)
        paint.color = if (listening) Color.WHITE else cyan

        canvas.drawCircle(
            cx,
            cy,
            radius * breathing,
            paint
        )

        paint.style = Paint.Style.FILL

        paint.shader = RadialGradient(
            cx,
            cy,
            radius * 0.58f,
            intArrayOf(
                Color.argb(190, 0, 235, 255),
                Color.argb(65, 0, 120, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, radius * 0.58f, paint)
        paint.shader = null
    }

    private fun drawWaveform(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        val count = 48
        val baseRadius = radius * 0.76f

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.2f)
        paint.strokeCap = Paint.Cap.ROUND

        for (i in 0 until count) {
            val angle = i * (Math.PI * 2.0 / count)

            val wave = sin(
                angle * 5.0 + pulse * 2.0
            ).toFloat()

            val boost = if (listening) 1.7f else 1f

            val length = (
                dp(5f) + abs(wave) * dp(11f)
            ) * boost

            val x1 =
                cx + cos(angle).toFloat() * baseRadius

            val y1 =
                cy + sin(angle).toFloat() * baseRadius

            val x2 =
                cx + cos(angle).toFloat() *
                (baseRadius + length)

            val y2 =
                cy + sin(angle).toFloat() *
                (baseRadius + length)

            paint.color = if (i % 4 == 0) purple else cyan
            paint.alpha = if (listening) 255 else 175

            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun drawText(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER

        paint.typeface = Typeface.create(
            "sans-serif-black",
            Typeface.BOLD
        )

        paint.textSize = radius * 0.25f
        paint.color = Color.WHITE

        paint.setShadowLayer(dp(12f), 0f, 0f, cyan)

        canvas.drawText(
            "IDRAAK",
            cx,
            cy + radius * 0.10f,
            paint
        )

        paint.clearShadowLayer()

        paint.textSize = radius * 0.075f
        paint.color = if (listening) Color.WHITE else cyan

        canvas.drawText(
            if (listening) "LISTENING" else "AI CORE",
            cx,
            cy + radius * 0.35f,
            paint
        )

        paint.textSize = radius * 0.055f
        paint.color = Color.rgb(155, 190, 225)

        canvas.drawText(
            "VOICE CONTROL SYSTEM",
            cx,
            cy + radius * 1.95f,
            paint
        )
    }
}
