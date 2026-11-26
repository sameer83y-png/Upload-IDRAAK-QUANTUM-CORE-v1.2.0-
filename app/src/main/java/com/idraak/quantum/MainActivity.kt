package com.idraak.quantum

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.idraak.quantum.core.AndroidActionExecutor
import com.idraak.quantum.core.OfflineCommandEngine

class MainActivity : Activity() {
    private val commandEngine = OfflineCommandEngine()
    private lateinit var actionExecutor: AndroidActionExecutor
    private lateinit var responseView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionExecutor = AndroidActionExecutor(this)
        buildDashboard()
    }

    private fun buildDashboard() {
        val accent = Color.rgb(71, 208, 255)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 44, 28, 28)
            setBackgroundColor(Color.rgb(7, 12, 24))
        }
        root.addView(TextView(this).apply {
            text = "IDRAAK"
            textSize = 32f
            setTextColor(accent)
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "QUANTUM CORE  •  OFFLINE FOUNDATION"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 34)
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "◉"
            textSize = 76f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "CORE ONLINE"
            textSize = 13f
            setTextColor(Color.rgb(105, 235, 178))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }, matchWrap())

        val commandInput = EditText(this).apply {
            hint = "Type a command…"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            setPadding(18, 14, 18, 14)
            setBackgroundColor(Color.rgb(24, 34, 52))
        }
        root.addView(commandInput, matchWrap())

        root.addView(Button(this).apply {
            text = "RUN COMMAND"
            setTextColor(Color.BLACK)
            setBackgroundColor(accent)
            setOnClickListener {
                val result = commandEngine.process(commandInput.text.toString())
                val execution = actionExecutor.execute(result.action)
                responseView.text = result.message + "\n" + execution
            }
        }, matchWrap().apply { topMargin = 14 })

        responseView = TextView(this).apply {
            text = "Ready. Try: open settings, open camera, or search YouTube for music."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(4, 24, 4, 0)
            gravity = Gravity.CENTER
        }
        root.addView(responseView, matchWrap())
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
