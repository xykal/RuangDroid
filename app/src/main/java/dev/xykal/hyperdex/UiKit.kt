package dev.xykal.hyperdex

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

object UiKit {
    const val INK = 0xFFEDF2F7.toInt()
    const val MUTED = 0xFF8B98AD.toInt()

    val fluentInterpolator = DecelerateInterpolator(1.8f)
    val springInterpolator = OvershootInterpolator(0.75f)

    /**
     * Micro-Density PC Unit (v6.0):
     * Targets a 465-unit short-edge reference + default 84% scale (~550 virtual px short edge),
     * so out-of-the-box UI is genuinely crisp, compact, and PC-sized on EVERY screen without bloating,
     * while remaining 100% adjustable from 55% (Ultra-Micro) to 135% (Comfort) in Display Settings.
     */
    fun unit(context: Context): Float {
        val dm = context.resources.displayMetrics
        val shortEdgePx = min(dm.widthPixels, dm.heightPixels).toFloat().coerceAtLeast(240f)
        val viewportUnit = shortEdgePx / 465f
        val blended = (dm.density * 0.32f) + (viewportUnit * 0.68f)
        val adaptiveBase = min(blended, shortEdgePx / 445f)
        return (adaptiveBase * DesktopPrefs.cachedScaleFactor).coerceIn(0.45f, 4.5f)
    }

    fun dp(context: Context, value: Int): Int =
        (value * unit(context)).toInt()

    fun spPx(context: Context, value: Float): Float =
        value * unit(context)

    fun rounded(context: Context, color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }

    fun strokeRounded(
        context: Context,
        color: Int,
        strokeColor: Int,
        radiusDp: Int,
        strokeDp: Int = 1
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, strokeDp).coerceAtLeast(1), strokeColor)
    }

    fun gradientRounded(
        context: Context,
        colors: IntArray,
        strokeColor: Int,
        radiusDp: Int
    ): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
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
        setTextSize(TypedValue.COMPLEX_UNIT_PX, spPx(context, sizeSp))
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachPressBounce(view: View, minScale: Float = 0.93f) {
        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(minScale).scaleY(minScale).setDuration(90).setInterpolator(fluentInterpolator).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(springInterpolator).start()
                }
            }
            false
        }
    }

    fun chipButton(
        context: Context,
        text: String,
        bgColor: Int = Color.rgb(28, 33, 43),
        strokeColor: Int = Color.argb(36, 255, 255, 255),
        textColor: Int = INK,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_PX, spPx(context, 10.0f))
        setTextColor(textColor)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3))
        background = strokeRounded(context, bgColor, strokeColor, 6)
        attachPressBounce(this, 0.96f)
        setOnClickListener { onClick() }
    }

    /**
     * Custom Precision PC Slider (100% custom — zero Android SeekBar).
     */
    @SuppressLint("ClickableViewAccessibility")
    fun buildFluentSlider(
        context: Context,
        minVal: Int,
        maxVal: Int,
        initialVal: Int,
        accentColor: Int,
        onValueChanged: (Int) -> Unit
    ): View {
        var current = initialVal.coerceIn(minVal, maxVal)
        val range = (maxVal - minVal).coerceAtLeast(1)

        return object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val h = height.toFloat()
                val pad = dp(context, 8).toFloat()
                val trackW = (w - pad * 2).coerceAtLeast(10f)
                val cy = h / 2f
                val trackH = dp(context, 3).toFloat().coerceAtLeast(2f)
                val frac = (current - minVal).toFloat() / range.toFloat()
                val thumbX = pad + trackW * frac

                paint.color = Color.argb(45, 255, 255, 255)
                canvas.drawRoundRect(RectF(pad, cy - trackH / 2, w - pad, cy + trackH / 2), trackH, trackH, paint)

                paint.color = accentColor
                canvas.drawRoundRect(RectF(pad, cy - trackH / 2, thumbX, cy + trackH / 2), trackH, trackH, paint)

                paint.color = Color.rgb(241, 245, 249)
                canvas.drawCircle(thumbX, cy, dp(context, 6).toFloat(), paint)
                paint.color = accentColor
                canvas.drawCircle(thumbX, cy, dp(context, 2).toFloat().coerceAtLeast(1.5f), paint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (event.actionMasked in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE)) {
                    val pad = dp(context, 10).toFloat()
                    val trackW = (width - pad * 2).coerceAtLeast(10f)
                    val frac = ((event.x - pad) / trackW).coerceIn(0f, 1f)
                    val next = (minVal + frac * range).toInt().coerceIn(minVal, maxVal)
                    if (next != current) {
                        current = next
                        onValueChanged(current)
                        invalidate()
                    }
                    return true
                }
                return super.onTouchEvent(event)
            }
        }
    }

    /**
     * Responsive grid built from LinearLayout rows — crash-proof on every screen & DPI.
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

    fun builtInIcon(context: Context, code: String, c1: Int, c2: Int, glyph: String): Drawable {
        return VectorIcons.get(context, code, dp(context, 48).coerceAtLeast(64), c1)
    }
}
