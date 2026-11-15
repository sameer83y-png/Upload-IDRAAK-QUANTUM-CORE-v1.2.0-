package com.idraak.quantum

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

@Suppress("UNUSED_PARAMETER")
class LauncherHomeView(
    context: Context,
    private val onSettingsClick: () -> Unit,
    onVoiceClick: () -> Unit
) : LinearLayout(context) {

    private val cyan = Color.rgb(0, 225, 255)

    private val launcherEngine = LauncherEngine(context)

    private lateinit var homePanel: LinearLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var coreView: LiveOrbView
    private lateinit var appGrid: GridView
    private lateinit var appAdapter: AppDrawerAdapter

    private val handler = Handler(Looper.getMainLooper())

    private val touchSlop =
        ViewConfiguration.get(context).scaledTouchSlop

    private var startX = 0f
    private var startY = 0f

    private var coreStartX = 0f
    private var coreStartY = 0f
    private var coreMoved = false

    private var appLoadExecutor: ExecutorService? = null
    private var reloadRequestId = 0

    private val longPressRunnable = Runnable {
        if (coreView.isPressed && !coreMoved) {
            onSettingsClick()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

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
            setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(12)
            )
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

        coreView.setOnTouchListener { view, event ->

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    coreStartX = event.x
                    coreStartY = event.y
                    coreMoved = false

                    view.isPressed = true

                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(
                        longPressRunnable,
                        2000L
                    )

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val moveX = abs(event.x - coreStartX)
                    val moveY = abs(event.y - coreStartY)

                    if (
                        moveX > touchSlop ||
                        moveY > touchSlop
                    ) {
                        coreMoved = true
                        view.isPressed = false
                        handler.removeCallbacks(longPressRunnable)
                    }

                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    view.isPressed = false
                    handler.removeCallbacks(longPressRunnable)
                    true
                }

                else -> true
            }
        }

        val availableWidth =
            (resources.displayMetrics.widthPixels - dp(80))
                .coerceAtLeast(dp(1))

        val coreSize = min(
            dp(310),
            availableWidth
        )

        homePanel.addView(
            coreView,
            LayoutParams(
                coreSize,
                coreSize
            ).apply {
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
            setPadding(
                dp(16),
                dp(18),
                dp(16),
                dp(12)
            )
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

        val emptyText = TextView(context).apply {
            text = "No launchable apps found"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        appGrid = GridView(context).apply {
            numColumns = 3
            horizontalSpacing = dp(10)
            verticalSpacing = dp(10)
            columnWidth = dp(90)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            gravity = Gravity.CENTER
            clipToPadding = false
            setPadding(0, dp(5), 0, dp(5))
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        appAdapter = AppDrawerAdapter(
            context,
            emptyList()
        ) { app ->

            val launched = launcherEngine.launchApp(
                app.packageName
            )

            if (!launched) {
                Toast.makeText(
                    context,
                    "Unable to open ${app.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        appGrid.adapter = appAdapter
        appGrid.emptyView = emptyText

        drawerPanel.addView(
            appGrid,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        drawerPanel.addView(
            emptyText,
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
    }

    private fun showHome() {

        handler.removeCallbacks(longPressRunnable)

        homePanel.visibility = VISIBLE
        drawerPanel.visibility = GONE

        if (isAttachedToWindow) {
            coreView.startAnimation()
        }
    }

    private fun showDrawer() {

        handler.removeCallbacks(longPressRunnable)

        homePanel.visibility = GONE
        drawerPanel.visibility = VISIBLE

        coreView.stopAnimation()

        appGrid.setSelection(0)
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

    fun reloadApps() {

        if (!::appAdapter.isInitialized) return
        if (!isAttachedToWindow) return

        val requestId = ++reloadRequestId

        val executor = appLoadExecutor
            ?: Executors.newSingleThreadExecutor().also {
                appLoadExecutor = it
            }

        try {
            executor.execute {

                val loadedApps = try {
                    launcherEngine.getInstalledApps()
                } catch (_: Exception) {
                    emptyList()
                }

                handler.post {

                    if (
                        !isAttachedToWindow ||
                        requestId != reloadRequestId ||
                        !::appAdapter.isInitialized
                    ) {
                        return@post
                    }

                    appAdapter.updateApps(loadedApps)
                }
            }
        } catch (_: Exception) {
            appAdapter.updateApps(emptyList())
        }
    }

    override fun dispatchTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
            }

            MotionEvent.ACTION_UP -> {

                val deltaX = event.x - startX
                val deltaY = event.y - startY

                val verticalSwipe =
                    abs(deltaY) > dp(85) &&
                    abs(deltaY) > abs(deltaX) * 1.25f

                if (verticalSwipe) {

                    if (
                        deltaY < 0 &&
                        !isAppDrawerOpen()
                    ) {
                        showDrawer()

                    } else if (
                        deltaY > 0 &&
                        isAppDrawerOpen() &&
                        !startedInsideAppGrid()
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

    private fun startedInsideAppGrid(): Boolean {

        if (!::appGrid.isInitialized) return false

        val gridRect = Rect()

        appGrid.getHitRect(gridRect)

        gridRect.offset(
            drawerPanel.left,
            drawerPanel.top
        )

        return gridRect.contains(
            startX.toInt(),
            startY.toInt()
        )
    }

    fun setAvatarGender(gender: String) {
        // Human avatar intentionally removed.
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
            if (homePanel.visibility == VISIBLE) {
                coreView.startAnimation()
            } else {
                coreView.stopAnimation()
            }
        }

        reloadApps()
    }

    override fun onDetachedFromWindow() {

        handler.removeCallbacks(longPressRunnable)

        reloadRequestId++

        appLoadExecutor?.shutdownNow()
        appLoadExecutor = null

        if (::coreView.isInitialized) {
            coreView.stopAnimation()
        }

        super.onDetachedFromWindow()
    }
}
