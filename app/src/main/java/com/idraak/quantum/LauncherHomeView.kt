
package com.idraak.quantum

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.inputmethod.EditorInfo

class LauncherHomeView(
    context: Context,
    private val onSettingsClick: () -> Unit,
    private val onVoiceClick: () -> Unit
) : LinearLayout(context) {

    private val engine = LauncherEngine(context)
    private val allApps = engine.getInstalledApps()
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var statusText: TextView
    private lateinit var searchBox: EditText
    private lateinit var appGrid: GridView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(18, 20, 18, 12)

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(5, 9, 27),
                Color.rgb(12, 17, 42),
                Color.rgb(20, 10, 42)
            )
        )

        buildInterface()
    }

    private fun buildInterface() {

        val header = TextView(context).apply {
            text = "IDRAAK QUANTUM CORE"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(90, 220, 255))
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }

        addView(
            header,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(context).apply {
            text = "NEXT GENERATION AI LAUNCHER"
            textSize = 10f
            letterSpacing = 0.22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(160, 150, 230))
            setPadding(0, 5, 0, 0)
        }

        addView(subtitle)

        val orb = LiveOrbView(context)

        addView(
            orb,
            LayoutParams(190, 190).apply {
                gravity = Gravity.CENTER
                topMargin = 8
                bottomMargin = 4
            }
        )

        statusText = TextView(context).apply {
            text = "● IDRAAK SYSTEM READY"
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(70, 255, 190))
        }

        addView(
            statusText,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        searchBox = EditText(context).apply {
            hint = "Search installed apps..."
            setHintTextColor(Color.rgb(140, 155, 190))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(18, 8, 18, 8)

            background = GradientDrawable().apply {
                setColor(Color.rgb(22, 31, 57))
                cornerRadius = 24f
                setStroke(2, Color.rgb(45, 145, 210))
            }
        }

        addView(
            searchBox,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                52
            ).apply {
                topMargin = 15
                bottomMargin = 12
            }
        )

        val drawerTitle = TextView(context).apply {
            text = "YOUR APPLICATIONS"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setTextColor(Color.rgb(100, 205, 255))
            gravity = Gravity.CENTER_VERTICAL
        }

        addView(
            drawerTitle,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                30
            )
        )

        appGrid = GridView(context).apply {
            numColumns = 4
            horizontalSpacing = 9
            verticalSpacing = 10
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            clipToPadding = false
            setPadding(0, 8, 0, 8)
            selector = android.graphics.drawable.ColorDrawable(
                Color.TRANSPARENT
            )
        }

        adapter = AppDrawerAdapter(context, allApps) { app ->
            setLiveStatus("LAUNCHING ${app.name.uppercase()}...")
            val launched = engine.launchApp(app.packageName)

            if (launched) {
                setLiveStatus("● APP LAUNCH REQUEST SENT")
            } else {
                setLiveStatus("APP COULD NOT BE OPENED")
            }
        }

        appGrid.adapter = adapter

        addView(
            appGrid,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        searchBox.addTextChangedListener(object : TextWatcher {
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
                adapter.updateApps(filtered)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        val footer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }

        val voiceButton = createActionButton(
            "🎙 VOICE",
            Color.rgb(0, 125, 190)
        ) {
            onVoiceClick()
        }

        val settingsButton = createActionButton(
            "⚙ SETTINGS",
            Color.rgb(100, 65, 175)
        ) {
            onSettingsClick()
        }

        footer.addView(
            voiceButton,
            LayoutParams(0, 52, 1f).apply {
                marginEnd = 6
            }
        )

        footer.addView(
            settingsButton,
            LayoutParams(0, 52, 1f).apply {
                marginStart = 6
            }
        )

        addView(
            footer,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                58
            )
        )

        val footerText = TextView(context).apply {
            text = "POWERED BY IDRAAK QUANTUM ENGINE"
            textSize = 9f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(115, 130, 175))
            setPadding(0, 8, 0, 0)
        }

        addView(footerText)
    }

    private fun createActionButton(
        label: String,
        color: Int,
        action: () -> Unit
    ): Button {

        return Button(context).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD

            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 22f
                setStroke(1, Color.rgb(100, 205, 255))
            }

            setOnClickListener {
                action()
            }
        }
    }

    fun setLiveStatus(message: String) {
        if (::statusText.isInitialized) {
            statusText.text = message
        }
    }

    fun reloadApps() {
        val updated = engine.getInstalledApps()
        adapter.updateApps(updated)
    }

    fun setVoiceStatus(active: Boolean) {
        setLiveStatus(
            if (active) {
                "● IDRAAK LISTENING"
            } else {
                "● IDRAAK SYSTEM READY"
            }
        )
    }
}

class LiveOrbView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rotation = 0f
    private var pulse = 0f

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 7000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()

        addUpdateListener {
            rotation = it.animatedValue as Float
            pulse = (Math.sin(Math.toRadians(rotation.toDouble()))
                .toFloat() + 1f) / 2f
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = width.coerceAtMost(height) * 0.34f

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx,
            cy,
            radius * 1.8f,
            intArrayOf(
                Color.argb(100, 0, 210, 255),
                Color.argb(35, 100, 70, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, radius * 1.8f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.rgb(40, 190, 255)

        canvas.save()
        canvas.rotate(rotation, cx, cy)
        canvas.drawOval(
            cx - radius * 1.35f,
            cy - radius * 0.48f,
            cx + radius * 1.35f,
            cy + radius * 0.48f,
            paint
        )
        canvas.restore()

        paint.strokeWidth = 2f
        paint.color = Color.rgb(165, 85, 255)

        canvas.save()
        canvas.rotate(-rotation * 0.7f, cx, cy)
        canvas.drawOval(
            cx - radius * 0.55f,
            cy - radius * 1.3f,
            cx + radius * 0.55f,
            cy + radius * 1.3f,
            paint
        )
        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - radius * 0.25f,
            cy - radius * 0.25f,
            radius * 1.4f,
            intArrayOf(
                Color.rgb(120, 245, 255),
                Color.rgb(0, 125, 220),
                Color.rgb(35, 30, 105)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, radius * (0.78f + pulse * 0.05f), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(110, 230, 255)
        canvas.drawCircle(cx, cy, radius * 0.78f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = radius * 0.27f

        canvas.drawText(
            "IDRAAK",
            cx,
            cy + radius * 0.08f,
            paint
        )

        paint.textSize = radius * 0.13f
        paint.color = Color.rgb(150, 235, 255)

        canvas.drawText(
            "QUANTUM CORE",
            cx,
            cy + radius * 0.35f,
            paint
        )
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
