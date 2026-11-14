
package com.idraak.quantum

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AppDrawerAdapter(
    private val context: Context,
    private var apps: List<LauncherApp>,
    private val onAppClick: (LauncherApp) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): LauncherApp = apps[position]

    override fun getItemId(position: Int): Long =
        apps[position].packageName.hashCode().toLong()

    fun updateApps(newApps: List<LauncherApp>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup?
    ): View {

        val app = getItem(position)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6, 12, 6, 12)

            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 25, 48))
                cornerRadius = 20f
                setStroke(1, Color.rgb(40, 130, 190))
            }

            isClickable = true
            isFocusable = true
            contentDescription = app.name
            setOnClickListener {
                onAppClick(app)
            }
        }

        val icon = ImageView(context).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        container.addView(
            icon,
            LinearLayout.LayoutParams(56, 56)
        )

        val title = TextView(context).apply {
            text = app.name
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(2, 8, 2, 0)
        }

        container.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        return container
    }
}
