package com.aborayan.matchclock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    const val GREEN = 0xFF2F9C4D.toInt()
    const val GREEN_DARK = 0xFF237A37.toInt()
    const val BLUE = 0xFF2571C2.toInt()
    const val AMBER = 0xFFC07F22.toInt()
    const val RED = 0xFFC1433F.toInt()
    const val LIGHT_BG = 0xFFF4F5EF.toInt()
    const val LIGHT_SURFACE = Color.WHITE
    const val LIGHT_INK = 0xFF16241B.toInt()
    const val LIGHT_MUTED = 0xFF65746A.toInt()
    const val DARK_BG = 0xFF080F0C.toInt()
    const val DARK_SURFACE = 0xFF122019.toInt()
    const val DARK_INK = 0xFFF2F6F1.toInt()
    const val DARK_MUTED = 0xFFA9BAB1.toInt()

    fun dp(c: Context, value: Int): Int = (value * c.resources.displayMetrics.density + 0.5f).toInt()

    fun bg(color: Int, radiusDp: Int, c: Context, strokeColor: Int? = null, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(c, strokeDp), strokeColor)
        }

    fun label(c: Context, text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(c).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(c, 4), dp(c, 2), dp(c, 4), dp(c, 2))
    }

    fun button(c: Context, text: String, fill: Int, textColor: Int, radius: Int = 13, minHeight: Int = 48, stroke: Int? = null): Button =
        Button(c).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
            minHeight = dp(c, minHeight)
            minimumHeight = dp(c, minHeight)
            minimumWidth = 0
            background = bg(fill, radius, c, stroke)
            setPadding(dp(c, 8), 0, dp(c, 8), 0)
        }

    fun horizontal(c: Context, gap: Int = 8): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = object : android.graphics.drawable.ColorDrawable(Color.TRANSPARENT) {
            override fun getIntrinsicWidth(): Int = dp(c, gap)
        }
    }

    fun vertical(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun weight(view: View, weight: Float = 1f, height: Int = LinearLayout.LayoutParams.WRAP_CONTENT): View = view.apply {
        layoutParams = LinearLayout.LayoutParams(0, height, weight)
    }

    fun formatClock(ms: Long, tenths: Boolean = false): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSec = safe / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (tenths) "%02d:%02d.%d".format(min, sec, (safe % 1000) / 100) else "%02d:%02d".format(min, sec)
    }

    fun formatLost(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
