package com.example.ruangdroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object UiKit {
    const val INK = 0xFFF2F6FF.toInt()
    const val MUTED = 0xFFAEBCD6.toInt()

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(context: Context, color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }

    fun strokeRounded(context: Context, color: Int, strokeColor: Int, radiusDp: Int, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            setStroke(dp(context, strokeDp).coerceAtLeast(1), strokeColor)
        }

    fun gradientRounded(context: Context, colors: IntArray, strokeColor: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != 0) {
                setStroke(dp(context, 1).coerceAtLeast(1), strokeColor)
            }
        }

    fun label(
        context: Context,
        text: String,
        sizeSp: Float = 12f,
        color: Int = INK,
        bold: Boolean = false,
        maxLines: Int = 1
    ): TextView = TextView(context).apply {
        this.text = text
        this.textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
    }

    fun chipButton(
        context: Context,
        text: String,
        bgColor: Int = Color.argb(48, 215, 229, 255),
        strokeColor: Int = Color.argb(65, 255, 255, 255),
        textColor: Int = INK,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = 11.5f
        setTextColor(textColor)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(context, 11), dp(context, 5), dp(context, 11), dp(context, 5))
        background = strokeRounded(context, bgColor, strokeColor, 10)
        setOnClickListener { onClick() }
    }

    /**
     * Crash-proof responsive grid built from horizontal LinearLayout rows.
     * Eliminates all Android GridLayout measurement edge-cases.
     */
    fun buildGrid(
        context: Context,
        columns: Int,
        cellHeightDp: Int,
        views: List<View>,
        hGapDp: Int = 4,
        vGapDp: Int = 4
    ): LinearLayout {
        val safeCols = columns.coerceAtLeast(1)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (views.isEmpty()) return container

        val hGap = dp(context, hGapDp)
        val vGap = dp(context, vGapDp)
        val cellH = dp(context, cellHeightDp)

        var idx = 0
        while (idx < views.size) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (c in 0 until safeCols) {
                val cellView = if (idx < views.size) views[idx++] else View(context)
                row.addView(cellView, LinearLayout.LayoutParams(0, cellH, 1f).apply {
                    setMargins(hGap / 2, vGap / 2, hGap / 2, vGap / 2)
                })
            }
            container.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return container
    }

    /**
     * Safely converts any Drawable (AdaptiveIconDrawable, VectorDrawable, etc.) into a bounded Bitmap.
     */
    fun safeDrawableToBitmap(drawable: Drawable?, sizePx: Int): Bitmap? {
        if (drawable == null) return null
        return try {
            val s = sizePx.coerceIn(24, 192)
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, s, s)
            drawable.draw(canvas)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Generates crisp Windows 11-style vector-rendered icons for Built-in PC Apps & .EXE programs.
     */
    fun builtInIcon(context: Context, code: String, c1: Int, c2: Int, glyph: String): Drawable {
        val size = dp(context, 48).coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
        val pad = size * 0.06f
        val rect = RectF(pad, pad, size - pad, size - pad)
        val r = size * 0.24f
        canvas.drawRoundRect(rect, r, r, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (size * 0.03f).coerceAtLeast(1.5f)
            color = Color.argb(90, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, r, r, borderPaint)

        // Subtle glass highlight on top half
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(pad, pad, size - pad, size * 0.48f), r, r, shinePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = if (glyph.length <= 2) size * 0.44f else size * 0.28f
        }
        val fm = textPaint.fontMetrics
        val cy = (size / 2f) - (fm.ascent + fm.descent) / 2f
        canvas.drawText(glyph, size / 2f, cy, textPaint)

        if (code == "exe_item") {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 24, 40) }
            val badgeRect = RectF(size * 0.44f, size * 0.66f, size * 0.94f, size * 0.94f)
            canvas.drawRoundRect(badgeRect, size * 0.08f, size * 0.08f, badgePaint)
            val exeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(125, 211, 252)
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = size * 0.18f
            }
            canvas.drawText("EXE", badgeRect.centerX(), badgeRect.centerY() + size * 0.06f, exeText)
        }

        return BitmapDrawable(context.resources, bmp)
    }
}
