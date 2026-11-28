package com.idraak.quantum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated command HUD for the Home Launcher.
 * Loads the original home image from res/drawable-nodpi without density scaling.
 */
class HomeLauncherView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val panel = RectF()
    private var mode = "idle"
    private var detail = ""
    private var backgroundBitmap: Bitmap? = loadBackground()
    private var phase = 0f

    fun setState(nextMode: String, text: String) {
        mode = nextMode.lowercase().take(20)
        detail = text.take(110)
        invalidate()
    }

    private fun loadBackground(): Bitmap? {
        val id = resources.getIdentifier("idraak_home_background", "drawable", context.packageName)
        if (id == 0) return null
        return try { BitmapFactory.decodeResource(resources, id) } catch (_: Exception) { null }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val image = backgroundBitmap
        if (image != null && !image.isRecycled) {
            // Keep the complete supplied portrait visible. Letterbox areas stay dark
            // instead of zooming/cropping the person at the screen edges.
            canvas.drawColor(0xFF050B14.toInt())
            val scale = min(w / image.width, h / image.height)
            val dw = image.width * scale
            val dh = image.height * scale
            val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            canvas.drawBitmap(image, null, dst, paint)
            paint.shader = LinearGradient(0f, 0f, 0f, h, 0x33020A18, 0x88020A18, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        } else {
            paint.shader = LinearGradient(0f, 0f, w, h, 0xFF06142B.toInt(), 0xFF120A27.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            drawFallbackCity(canvas, w, h)
        }

        phase = (SystemClock.uptimeMillis() % 6000L) / 6000f
        drawHud(canvas, w, h)
        postInvalidateDelayed(40L)
    }

    private fun drawFallbackCity(canvas: Canvas, w: Float, h: Float) {
        paint.color = 0x5530BFFF
        paint.strokeWidth = 1.2f
        val step = w / 12f
        for (i in 0..11) {
            val x = i * step
            val top = h * (0.12f + ((i * 17) % 35) / 100f)
            canvas.drawRect(x, top, x + step * 0.55f, h * 0.78f, paint)
        }
        paint.color = 0xAA12DFFF.toInt()
        canvas.drawLine(0f, h * 0.52f, w, h * 0.48f, paint)
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val left = w * 0.09f
        val right = w * 0.91f
        val top = h * 0.60f
        val bottom = h * 0.79f
        panel.set(left, top, right, bottom)

        paint.color = 0xC0061830.toInt()
        canvas.drawRoundRect(panel, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0xFF20CFFF.toInt()
        paint.setShadowLayer(10f, 0f, 0f, 0xFF087DFF.toInt())
        canvas.drawRoundRect(panel, 16f, 16f, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL

        val titleSize = min(w * 0.042f, 17f)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = titleSize
        paint.color = 0xFF8DEBFF.toInt()
        paint.setShadowLayer(5f, 0f, 0f, 0xFF087DFF.toInt())
        canvas.drawText("IDRAAK  //  QUANTUM CORE", left + 15f, top + 27f, paint)
        paint.clearShadowLayer()

        val label = when (mode) {
            "listening" -> "LISTENING  •  VOICE INPUT"
            "speaking" -> "SPEAKING  •  URDU VOICE"
            "processing" -> "PROCESSING  •  COMMAND"
            "executing" -> "EXECUTING  •  DEVICE ACTION"
            "confirming" -> "WAITING  •  CONFIRMATION"
            "explaining" -> "RESPONSE  •  EXPLAINING"
            "error" -> "ATTENTION  •  ACTION NEEDS HELP"
            else -> "SYSTEM ONLINE  •  READY"
        }
        paint.textSize = min(w * 0.035f, 14f)
        canvas.drawText(label, left + 15f, top + 51f, paint)

        paint.color = 0xFFD8F7FF.toInt()
        paint.textSize = min(w * 0.031f, 12f)
        val safeDetail = detail.replace('\n', ' ').take(52)
        if (safeDetail.isNotBlank()) canvas.drawText(safeDetail, left + 15f, top + 73f, paint)

        val stateColor = when (mode) {
            "error" -> 0xFFFF6B72.toInt()
            "confirming" -> 0xFFFFD166.toInt()
            "processing", "executing" -> 0xFF55C8FF.toInt()
            "speaking", "explaining" -> 0xFFB88CFF.toInt()
            else -> 0xFF38DFFF.toInt()
        }

        // A compact animated signal trace reacts to the current assistant state.
        val signalActive = mode != "idle" && mode != "error"
        val pulse = (sin(phase * 6.28318f) + 1f) * 0.5f
        val signalLeft = left + 16f
        val signalRight = right - 16f
        val signalWidth = (signalRight - signalLeft) / 26f
        val baseline = bottom - 18f
        paint.color = stateColor
        paint.strokeWidth = 2.4f
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 26) {
            val wave = sin((i * 0.62f) + (phase * 12.566f))
            val envelope = if (signalActive) 0.35f + pulse * 0.65f else 0.18f
            val height = (3f + kotlin.math.abs(wave) * 12f * envelope)
            val x = signalLeft + signalWidth * i + signalWidth / 2f
            canvas.drawLine(x, baseline - height, x, baseline + height, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT

        // The scan line travels more slowly while the system is idle.
        val scanProgress = if (mode == "idle") phase else (phase * 1.7f) % 1f
        val scanY = top + 5f + (bottom - top - 10f) * scanProgress
        paint.color = if (signalActive) 0x9952DFFF.toInt() else 0x4452DFFF
        canvas.drawRect(left + 4f, scanY, right - 4f, scanY + 2f, paint)

        paint.color = stateColor
        paint.setShadowLayer(7f + pulse * 5f, 0f, 0f, stateColor)
        canvas.drawCircle(right - 18f, top + 20f, 4f + (pulse * 2.5f), paint)
        paint.clearShadowLayer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // No background thread or audio resource is owned by this view.
    }
}
