
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
    apps: List<LauncherApp>,
    private val onAppClick: (LauncherApp) -> Unit
) : BaseAdapter() {

    private var apps: List<LauncherApp> = apps.toList()

    private data class ViewHolder(
        val container: LinearLayout,
        val icon: ImageView,
        val title: TextView
    )

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): LauncherApp =
        apps[position]

    override fun getItemId(position: Int): Long =
        position.toLong()

    fun updateApps(newApps: List<LauncherApp>) {
        apps = newApps.toList()
        notifyDataSetChanged()
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val app = getItem(position)

        val holder: ViewHolder
        val itemView: View

        if (convertView == null) {
            holder = createViewHolder()
            itemView = holder.container
            itemView.tag = holder
        } else {
            itemView = convertView
            holder = convertView.tag as? ViewHolder
                ?: createViewHolder().also {
                    itemView.tag = it
                }
        }

        holder.title.text = app.name
        holder.container.contentDescription = app.name

        holder.icon.setImageDrawable(
            try {
                app.icon
                    ?: context.packageManager.defaultActivityIcon
            } catch (_: Exception) {
                context.packageManager.defaultActivityIcon
            }
        )

        holder.container.setOnClickListener {
            onAppClick(app)
        }

        return itemView
    }

    private fun createViewHolder(): ViewHolder {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(12), dp(6), dp(12))

            minimumHeight = dp(104)

            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 25, 48))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(40, 130, 190))
            }

            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        container.addView(
            icon,
            LinearLayout.LayoutParams(dp(56), dp(56))
        )

        val title = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(2), dp(8), dp(2), 0)

            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        container.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        return ViewHolder(
            container = container,
            icon = icon,
            title = title
        )
    }

    private fun dp(value: Int): Int {
        return (
            value * context.resources.displayMetrics.density
        ).toInt()
    }
}
