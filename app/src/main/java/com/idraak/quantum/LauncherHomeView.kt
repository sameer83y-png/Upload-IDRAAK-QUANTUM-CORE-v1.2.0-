
package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Suppress("UNUSED_PARAMETER")
class LauncherHomeView(
    context: Context,
    private val onSettingsClick: () -> Unit,
    onVoiceClick: () -> Unit
) : LinearLayout(context) {

    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(155, 65, 255)

    private lateinit var homePanel: LinearLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var coreView: LiveOrbView
    private lateinit var appList: LinearLayout
    private lateinit var appScroll: ScrollView

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop =
        ViewConfiguration.get(context).scaledTouchSlop

    private var startX = 0f
    private var startY = 0f
    private var coreMoved = false

    private val longPressRunnable = Runnable {
        if (!coreMoved) {
            onSettingsClick()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(2, 5, 18),
                Color.rgb(5, 13, 35),
                Color.rgb(19, 7, 42)
            )
        )

        buildHome()
        buildDrawer()
        showHome()
    }

    private fun buildHome() {
        homePanel = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }

        val header = TextView(context).apply {
            text = "IDRAAK"
            textSize = 30f
            setTextColor(cyan)
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            gravity = Gravity.CENTER
            letterSpacing = 0.20f
            setShadowLayer(18f, 0f, 0f, cyan)
        }

        homePanel.addView(
            header,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(context).apply {
            text = "PERSONAL AI CONTROL SYSTEM"
            textSize = 9f
            setTextColor(Color.rgb(145, 175, 220))
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
            setPadding(0, dp(5), 0, dp(12))
        }

        homePanel.addView(
            subtitle,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        coreView = LiveOrbView(context)

        coreView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    coreMoved = false
                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(longPressRunnable, 2000L)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (
                        abs(event.x) > touchSlop ||
                        abs(event.y) > touchSlop
                    ) {
                        coreMoved = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    true
                }

                else -> true
            }
        }

        val coreSize = min(
            resources.displayMetrics.widthPixels - dp(44),
            dp(310)
        )

        homePanel.addView(
            coreView,
            LayoutParams(coreSize, coreSize).apply {
                gravity = Gravity.CENTER
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        )

        statusText = TextView(context).apply {
            text = "● IDRAAK STANDBY"
            textSize = 13f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, dp(10), 0, dp(6))
        }

        homePanel.addView(
            statusText,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val instruction = TextView(context).apply {
            text = "SAY IDRAAK TO ACTIVATE"
            textSize = 10f
            setTextColor(Color.rgb(130, 160, 205))
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            setPadding(0, dp(5), 0, dp(12))
        }

        homePanel.addView(
            instruction,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val swipeHint = TextView(context).apply {
            text = "↑ SWIPE UP FOR ALL APPS"
            textSize = 9f
            setTextColor(Color.rgb(105, 190, 220))
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setPadding(0, dp(8), 0, dp(5))
        }

        homePanel.addView(
            swipeHint,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        addView(
            homePanel,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun buildDrawer() {
        drawerPanel = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(18), dp(16), dp(12))
            visibility = GONE
        }

        val title = TextView(context).apply {
            text = "APPLICATIONS"
            textSize = 23f
            setTextColor(cyan)
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            setShadowLayer(12f, 0f, 0f, cyan)
        }

        drawerPanel.addView(
            title,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(context).apply {
            text = "ALL AVAILABLE PHONE APPS"
            textSize = 9f
            setTextColor(Color.rgb(135, 165, 210))
            gravity = Gravity.CENTER
            letterSpacing = 0.13f
            setPadding(0, dp(5), 0, dp(14))
        }

        drawerPanel.addView(
            subtitle,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        appScroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        appList = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        // ScrollView child uses ViewGroup.LayoutParams.
        appScroll.addView(
            appList,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        drawerPanel.addView(
            appScroll,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val footer = TextView(context).apply {
            text = "↓ SWIPE DOWN TO CLOSE"
            textSize = 9f
            setTextColor(Color.rgb(105, 190, 220))
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setPadding(0, dp(12), 0, dp(6))
        }

        drawerPanel.addView(
            footer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        addView(
            drawerPanel,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        reloadApps()
    }

    private fun showHome() {
        homePanel.visibility = VISIBLE
        drawerPanel.visibility = GONE
        coreView.startAnimation()
    }

    private fun showDrawer() {
        handler.removeCallbacks(longPressRunnable)
        homePanel.visibility = GONE
        drawerPanel.visibility = VISIBLE
        appScroll.scrollTo(0, 0)
        reloadApps()
    }

    fun openAppDrawer() {
        showDrawer()
    }

    fun closeAppDrawer() {
        showHome()
    }

    fun isAppDrawerOpen(): Boolean {
        return drawerPanel.visibility == VISIBLE
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = event.rawX - startX
                val deltaY = event.rawY - startY

                val isVerticalSwipe =
                    abs(deltaY) > dp(85) &&
                    abs(deltaY) > abs(deltaX) * 1.25f

                if (isVerticalSwipe) {
                    if (deltaY < 0 && !isAppDrawerOpen()) {
                        showDrawer()
                    } else if (
                        deltaY > 0 &&
                        isAppDrawerOpen() &&
                        appScroll.scrollY == 0
                    ) {
                        showHome()
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
            }
        }

        return super.dispatchTouchEvent(event)
    }

    fun reloadApps() {
        if (!::appList.isInitialized) return

        appList.removeAllViews()

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = try {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(
                launchIntent,
                0
            )
        } catch (_: Exception) {
            emptyList()
        }.sortedBy {
            it.loadLabel(context.packageManager)
                .toString()
                .lowercase()
        }

        if (apps.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = "No launchable apps found"
                textSize = 14f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, dp(30), 0, dp(30))
            }

            appList.addView(
                emptyText,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
            )
            return
        }

        for (resolveInfo in apps) {
            val activityInfo = resolveInfo.activityInfo ?: continue

            val appName = resolveInfo.loadLabel(
                context.packageManager
            ).toString()

            val appIcon = try {
                resolveInfo.loadIcon(context.packageManager)
            } catch (_: Exception) {
                null
            }

            val appIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = android.content.ComponentName(
                    activityInfo.packageName,
                    activityInfo.name
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))

                background = GradientDrawable().apply {
                    setColor(Color.argb(45, 30, 100, 150))
                    cornerRadius = dp(13).toFloat()
                    setStroke(
                        dp(1),
                        Color.argb(75, 0, 225, 255)
                    )
                }

                isClickable = true
                isFocusable = true

                setOnClickListener {
                    try {
                        context.startActivity(appIntent)
                    } catch (_: Exception) {
                        // Launch failure is safely ignored.
                    }
                }
            }

            val iconView = ImageView(context).apply {
                if (appIcon != null) {
                    setImageDrawable(appIcon)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            row.addView(
                iconView,
                LayoutParams(dp(42), dp(42)).apply {
                    rightMargin = dp(14)
                }
            )

            val nameView = TextView(context).apply {
                text = appName
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 2
            }

            row.addView(
                nameView,
                LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val arrow = TextView(context).apply {
                text = "›"
                textSize = 25f
                setTextColor(cyan)
                gravity = Gravity.CENTER
            }

            row.addView(
                arrow,
                LayoutParams(dp(28), dp(42))
            )

            appList.addView(
                row,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
            )
        }
    }

    fun setAvatarGender(gender: String) {
        // Human avatar has been removed.
    }

    fun setLiveStatus(status: String) {
        if (::statusText.isInitialized) {
            statusText.text = status
        }
    }

    fun setVoiceStatus(active: Boolean) {
        if (::coreView.isInitialized) {
            coreView.setListening(active)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (::coreView.isInitialized) {
            coreView.startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)

        if (::coreView.isInitialized) {
            coreView.stopAnimation()
        }

        super.onDetachedFromWindow()
    }
}

/**
 * Abstract IDRAAK AI Core.
 * No human face, body or humanoid avatar.
 */
class LiveOrbView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cyan = Color.rgb(0, 225, 255)
    private val purple = Color.rgb(155, 65, 255)

    private var rotation = 0f
    private var pulse = 0f
    private var listening = false
    private var animationRunning = false

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!animationRunning) return

            rotation = (rotation + 1.2f) % 360f
            pulse += 0.055f

            invalidate()
            postDelayed(this, 35L)
        }
    }

    init {
        contentDescription =
            "IDRAAK AI Control Core. Long press for settings."
        startAnimation()
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
        val radius = min(width, height) * 0.29f

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

        paint.shader = android.graphics.RadialGradient(
            cx,
            cy,
            radius * 2.15f,
            intArrayOf(
                if (listening) Color.argb(110, 0, 255, 255)
                else Color.argb(65, 0, 160, 255),
                Color.argb(25, 115, 50, 255),
                Color.TRANSPARENT
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
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
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
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
        paint.strokeWidth = 2.3f

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
        paint.strokeWidth = 1f

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

        val angle = Math.toRadians(rotation.toDouble())

        canvas.drawCircle(
            cx + cos(angle).toFloat() * radius * 1.65f,
            cy + sin(angle).toFloat() * radius * 0.55f,
            4.5f,
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

        paint.shader = android.graphics.RadialGradient(
            cx - radius * 0.25f,
            cy - radius * 0.3f,
            radius * 1.45f,
            intArrayOf(
                Color.rgb(35, 115, 175),
                Color.rgb(12, 45, 105),
                Color.rgb(3, 10, 35)
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            cx,
            cy,
            radius * breathing,
            paint
        )

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = if (listening) Color.WHITE else cyan

        canvas.drawCircle(
            cx,
            cy,
            radius * breathing,
            paint
        )

        paint.style = Paint.Style.FILL

        paint.shader = android.graphics.RadialGradient(
            cx,
            cy,
            radius * 0.58f,
            intArrayOf(
                Color.argb(190, 0, 235, 255),
                Color.argb(65, 0, 120, 255),
                Color.TRANSPARENT
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
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

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.strokeCap = Paint.Cap.ROUND

        for (i in 0 until count) {
            val angle = i * (Math.PI * 2.0 / count)

            val wave = sin(
                angle * 5.0 + pulse * 2.0
            ).toFloat()

            val boost = if (listening) 1.7f else 1f
            val length = (5f + abs(wave) * 11f) * boost

            val x1 = cx + cos(angle).toFloat() * baseRadius
            val y1 = cy + sin(angle).toFloat() * baseRadius

            val x2 = cx + cos(angle).toFloat() *
                (baseRadius + length)

            val y2 = cy + sin(angle).toFloat() *
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
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(
            "sans-serif-black",
            Typeface.BOLD
        )

        paint.textSize = radius * 0.25f
        paint.color = Color.WHITE
        paint.setShadowLayer(12f, 0f, 0f, cyan)

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
