package com.example.ruangdroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlin.math.cos
import kotlin.math.sin

object VectorIcons {
    private val cache = object : LruCache<String, Bitmap>(128) {}

    fun clearCache() {
        cache.evictAll()
    }

    fun get(context: Context, id: String, sizePx: Int, accentColor: Int = 0): Drawable {
        val s = sizePx.coerceIn(24, 256)
        val key = "$id@$s@$accentColor"
        var bmp = cache.get(key)
        if (bmp == null || bmp.isRecycled) {
            bmp = renderIcon(id, s, accentColor)
            cache.put(key, bmp)
        }
        return BitmapDrawable(context.resources, bmp)
    }

    private fun baseSquircle(canvas: Canvas, s: Float, c1: Int, c2: Int) {
        val pad = s * 0.06f
        val r = s * 0.24f
        val rect = RectF(pad, pad, s - pad, s - pad)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, s, s, c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, r, r, bg)

        // Subtle top specular highlight
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, pad, 0f, s * 0.52f,
                Color.argb(65, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, r, r, gloss)

        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (s * 0.025f).coerceAtLeast(1.2f)
            color = Color.argb(85, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, r, r, rim)
    }

    private fun renderIcon(id: String, size: Int, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        when (id) {
            "start_orb" -> {
                val gap = s * 0.06f
                val pad = s * 0.14f
                val box = (s - pad * 2 - gap) / 2f
                val rad = box * 0.22f
                val colors = intArrayOf(
                    Color.rgb(96, 165, 250),
                    Color.rgb(59, 130, 246),
                    Color.rgb(56, 189, 248),
                    Color.rgb(37, 99, 235)
                )
                val coords = arrayOf(
                    RectF(pad, pad, pad + box, pad + box),
                    RectF(pad + box + gap, pad, pad + box * 2 + gap, pad + box),
                    RectF(pad, pad + box + gap, pad + box, pad + box * 2 + gap),
                    RectF(pad + box + gap, pad + box + gap, pad + box * 2 + gap, pad + box * 2 + gap)
                )
                for (i in 0..3) {
                    p.shader = null
                    p.style = Paint.Style.FILL
                    p.color = colors[i]
                    c.drawRoundRect(coords[i], rad, rad, p)
                }
            }

            "search" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.09f).coerceAtLeast(2f)
                p.strokeCap = Paint.Cap.ROUND
                p.color = if (accent != 0) accent else Color.rgb(203, 213, 225)
                c.drawCircle(s * 0.43f, s * 0.43f, s * 0.23f, p)
                c.drawLine(s * 0.60f, s * 0.60f, s * 0.80f, s * 0.80f, p)
            }

            "explorer" -> {
                baseSquircle(c, s, Color.rgb(30, 58, 138), Color.rgb(15, 23, 42))
                // Back folder tab
                p.style = Paint.Style.FILL
                p.color = Color.rgb(217, 119, 6)
                val tabPath = Path().apply {
                    moveTo(s * 0.20f, s * 0.30f)
                    lineTo(s * 0.44f, s * 0.30f)
                    lineTo(s * 0.51f, s * 0.38f)
                    lineTo(s * 0.80f, s * 0.38f)
                    lineTo(s * 0.80f, s * 0.72f)
                    lineTo(s * 0.20f, s * 0.72f)
                    close()
                }
                c.drawPath(tabPath, p)
                // Front folder flap
                p.shader = LinearGradient(0f, s * 0.38f, 0f, s * 0.75f, Color.rgb(251, 191, 36), Color.rgb(245, 158, 11), Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(s * 0.18f, s * 0.40f, s * 0.82f, s * 0.75f), s * 0.06f, s * 0.06f, p)
                p.shader = null
                p.color = Color.rgb(56, 189, 248)
                c.drawRoundRect(RectF(s * 0.30f, s * 0.54f, s * 0.70f, s * 0.61f), s * 0.03f, s * 0.03f, p)
            }

            "folder", "dir_item" -> {
                p.style = Paint.Style.FILL
                p.color = Color.rgb(217, 119, 6)
                c.drawRoundRect(RectF(s * 0.12f, s * 0.22f, s * 0.50f, s * 0.42f), s * 0.06f, s * 0.06f, p)
                p.shader = LinearGradient(0f, s * 0.30f, 0f, s * 0.82f, Color.rgb(252, 211, 77), Color.rgb(234, 179, 8), Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(s * 0.10f, s * 0.30f, s * 0.90f, s * 0.82f), s * 0.09f, s * 0.09f, p)
                p.shader = null
                p.color = Color.argb(70, 255, 255, 255)
                c.drawRoundRect(RectF(s * 0.16f, s * 0.35f, s * 0.84f, s * 0.43f), s * 0.04f, s * 0.04f, p)
            }

            "exe_center", "exe_item" -> {
                baseSquircle(c, s, Color.rgb(157, 23, 77), Color.rgb(88, 28, 135))
                // Window box inside
                p.style = Paint.Style.FILL
                p.color = Color.argb(230, 248, 250, 252)
                c.drawRoundRect(RectF(s * 0.22f, s * 0.24f, s * 0.78f, s * 0.72f), s * 0.06f, s * 0.06f, p)
                p.color = Color.rgb(59, 130, 246)
                c.drawRoundRect(RectF(s * 0.22f, s * 0.24f, s * 0.78f, s * 0.36f), s * 0.06f, s * 0.06f, p)
                // Gear / EXE badge
                p.color = Color.rgb(15, 23, 42)
                p.typeface = Typeface.DEFAULT_BOLD
                p.textAlign = Paint.Align.CENTER
                p.textSize = s * 0.21f
                c.drawText("EXE", s * 0.50f, s * 0.61f, p)
            }

            "shizuku_hub" -> {
                baseSquircle(c, s, Color.rgb(49, 46, 129), Color.rgb(30, 64, 175))
                // Shield + Lightning Bolt
                val bolt = Path().apply {
                    moveTo(s * 0.55f, s * 0.18f)
                    lineTo(s * 0.30f, s * 0.53f)
                    lineTo(s * 0.48f, s * 0.53f)
                    lineTo(s * 0.43f, s * 0.82f)
                    lineTo(s * 0.70f, s * 0.46f)
                    lineTo(s * 0.52f, s * 0.46f)
                    close()
                }
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(0f, s * 0.2f, 0f, s * 0.8f, Color.rgb(165, 243, 252), Color.rgb(56, 189, 248), Shader.TileMode.CLAMP)
                c.drawPath(bolt, p)
            }

            "browser" -> {
                baseSquircle(c, s, Color.rgb(2, 132, 199), Color.rgb(37, 99, 235))
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.055f).coerceAtLeast(1.8f)
                p.color = Color.WHITE
                c.drawCircle(s * 0.5f, s * 0.5f, s * 0.27f, p)
                c.drawOval(RectF(s * 0.36f, s * 0.23f, s * 0.64f, s * 0.77f), p)
                c.drawLine(s * 0.23f, s * 0.5f, s * 0.77f, s * 0.5f, p)
            }

            "notepad", "file_txt" -> {
                baseSquircle(c, s, Color.rgb(14, 165, 233), Color.rgb(37, 99, 235))
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                c.drawRoundRect(RectF(s * 0.26f, s * 0.20f, s * 0.74f, s * 0.80f), s * 0.05f, s * 0.05f, p)
                p.color = Color.rgb(59, 130, 246)
                p.strokeWidth = (s * 0.045f).coerceAtLeast(1.5f)
                c.drawLine(s * 0.34f, s * 0.36f, s * 0.66f, s * 0.36f, p)
                c.drawLine(s * 0.34f, s * 0.49f, s * 0.66f, s * 0.49f, p)
                c.drawLine(s * 0.34f, s * 0.62f, s * 0.56f, s * 0.62f, p)
            }

            "terminal", "file_bat" -> {
                baseSquircle(c, s, Color.rgb(30, 41, 59), Color.rgb(9, 14, 24))
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.07f).coerceAtLeast(2f)
                p.strokeCap = Paint.Cap.ROUND
                p.strokeJoin = Paint.Join.ROUND
                p.color = Color.rgb(74, 222, 128)
                val chevron = Path().apply {
                    moveTo(s * 0.27f, s * 0.35f)
                    lineTo(s * 0.44f, s * 0.50f)
                    lineTo(s * 0.27f, s * 0.65f)
                }
                c.drawPath(chevron, p)
                c.drawLine(s * 0.50f, s * 0.65f, s * 0.72f, s * 0.65f, p)
            }

            "calculator" -> {
                baseSquircle(c, s, Color.rgb(16, 185, 129), Color.rgb(4, 120, 87))
                p.style = Paint.Style.FILL
                p.color = Color.argb(235, 255, 255, 255)
                c.drawRoundRect(RectF(s * 0.25f, s * 0.22f, s * 0.75f, s * 0.38f), s * 0.04f, s * 0.04f, p)
                val dots = arrayOf(0.32f to 0.50f, 0.50f to 0.50f, 0.68f to 0.50f, 0.32f to 0.68f, 0.50f to 0.68f)
                for ((dx, dy) in dots) c.drawCircle(s * dx, s * dy, s * 0.055f, p)
                p.color = Color.rgb(250, 204, 21)
                c.drawCircle(s * 0.68f, s * 0.68f, s * 0.065f, p)
            }

            "paint", "file_img" -> {
                baseSquircle(c, s, Color.rgb(236, 72, 153), Color.rgb(249, 115, 22))
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                c.drawCircle(s * 0.50f, s * 0.50f, s * 0.27f, p)
                val blobs = listOf(
                    Triple(0.40f, 0.40f, Color.rgb(239, 68, 68)),
                    Triple(0.56f, 0.37f, Color.rgb(59, 130, 246)),
                    Triple(0.61f, 0.53f, Color.rgb(34, 197, 94)),
                    Triple(0.43f, 0.58f, Color.rgb(234, 179, 8))
                )
                for ((bx, by, col) in blobs) {
                    p.color = col
                    c.drawCircle(s * bx, s * by, s * 0.055f, p)
                }
            }

            "media", "file_audio" -> {
                baseSquircle(c, s, Color.rgb(124, 58, 237), Color.rgb(67, 56, 202))
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                val play = Path().apply {
                    moveTo(s * 0.38f, s * 0.29f)
                    lineTo(s * 0.70f, s * 0.50f)
                    lineTo(s * 0.38f, s * 0.71f)
                    close()
                }
                c.drawPath(play, p)
            }

            "taskmgr" -> {
                baseSquircle(c, s, Color.rgb(13, 148, 136), Color.rgb(15, 118, 110))
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.065f).coerceAtLeast(2f)
                p.strokeJoin = Paint.Join.ROUND
                p.strokeCap = Paint.Cap.ROUND
                p.color = Color.rgb(167, 243, 208)
                val wave = Path().apply {
                    moveTo(s * 0.20f, s * 0.55f)
                    lineTo(s * 0.36f, s * 0.55f)
                    lineTo(s * 0.45f, s * 0.30f)
                    lineTo(s * 0.56f, s * 0.70f)
                    lineTo(s * 0.65f, s * 0.48f)
                    lineTo(s * 0.80f, s * 0.48f)
                }
                c.drawPath(wave, p)
            }

            "control" -> {
                baseSquircle(c, s, Color.rgb(71, 85, 105), Color.rgb(30, 41, 59))
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.07f).coerceAtLeast(2f)
                p.color = Color.WHITE
                c.drawCircle(s * 0.5f, s * 0.5f, s * 0.16f, p)
                for (deg in 0 until 360 step 45) {
                    val rad = Math.toRadians(deg.toDouble())
                    val x1 = s * 0.5f + cos(rad).toFloat() * s * 0.20f
                    val y1 = s * 0.5f + sin(rad).toFloat() * s * 0.20f
                    val x2 = s * 0.5f + cos(rad).toFloat() * s * 0.30f
                    val y2 = s * 0.5f + sin(rad).toFloat() * s * 0.30f
                    c.drawLine(x1, y1, x2, y2, p)
                }
            }

            "minesweeper" -> {
                baseSquircle(c, s, Color.rgb(30, 58, 138), Color.rgb(17, 24, 39))
                p.style = Paint.Style.FILL
                p.color = Color.rgb(248, 113, 113)
                c.drawCircle(s * 0.5f, s * 0.5f, s * 0.20f, p)
                p.strokeWidth = (s * 0.06f).coerceAtLeast(2f)
                p.color = Color.WHITE
                c.drawLine(s * 0.5f, s * 0.22f, s * 0.5f, s * 0.78f, p)
                c.drawLine(s * 0.22f, s * 0.5f, s * 0.78f, s * 0.5f, p)
            }

            "arkanoid" -> {
                baseSquircle(c, s, Color.rgb(124, 45, 18), Color.rgb(30, 41, 59))
                p.style = Paint.Style.FILL
                p.color = Color.rgb(249, 115, 22)
                c.drawRoundRect(RectF(s * 0.22f, s * 0.26f, s * 0.48f, s * 0.38f), 4f, 4f, p)
                p.color = Color.rgb(56, 189, 248)
                c.drawRoundRect(RectF(s * 0.52f, s * 0.26f, s * 0.78f, s * 0.38f), 4f, 4f, p)
                p.color = Color.rgb(250, 204, 21)
                c.drawCircle(s * 0.54f, s * 0.54f, s * 0.07f, p)
                p.color = Color.WHITE
                c.drawRoundRect(RectF(s * 0.28f, s * 0.70f, s * 0.72f, s * 0.78f), 6f, 6f, p)
            }

            // Crisp Window Chrome Vector Icons
            "win_close" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.09f).coerceAtLeast(1.8f)
                p.strokeCap = Paint.Cap.ROUND
                p.color = Color.WHITE
                c.drawLine(s * 0.32f, s * 0.32f, s * 0.68f, s * 0.68f, p)
                c.drawLine(s * 0.68f, s * 0.32f, s * 0.32f, s * 0.68f, p)
            }
            "win_max" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.08f).coerceAtLeast(1.6f)
                p.color = Color.WHITE
                c.drawRoundRect(RectF(s * 0.28f, s * 0.28f, s * 0.72f, s * 0.72f), s * 0.06f, s * 0.06f, p)
            }
            "win_restore" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.075f).coerceAtLeast(1.5f)
                p.color = Color.WHITE
                c.drawRoundRect(RectF(s * 0.25f, s * 0.35f, s * 0.63f, s * 0.73f), s * 0.05f, s * 0.05f, p)
                c.drawLine(s * 0.37f, s * 0.26f, s * 0.74f, s * 0.26f, p)
                c.drawLine(s * 0.74f, s * 0.26f, s * 0.74f, s * 0.63f, p)
            }
            "win_min" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.09f).coerceAtLeast(1.8f)
                p.strokeCap = Paint.Cap.ROUND
                p.color = Color.WHITE
                c.drawLine(s * 0.28f, s * 0.52f, s * 0.72f, s * 0.52f, p)
            }
            "win_snap_l" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.07f).coerceAtLeast(1.4f)
                p.color = Color.WHITE
                c.drawRoundRect(RectF(s * 0.22f, s * 0.26f, s * 0.78f, s * 0.74f), s * 0.06f, s * 0.06f, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(s * 0.24f, s * 0.28f, s * 0.50f, s * 0.72f), s * 0.04f, s * 0.04f, p)
            }
            "win_snap_r" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.07f).coerceAtLeast(1.4f)
                p.color = Color.WHITE
                c.drawRoundRect(RectF(s * 0.22f, s * 0.26f, s * 0.78f, s * 0.74f), s * 0.06f, s * 0.06f, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(s * 0.50f, s * 0.28f, s * 0.76f, s * 0.72f), s * 0.04f, s * 0.04f, p)
            }
            "tray_status" -> {
                // Crisp vector Wi-Fi + Speaker + Battery icon strip
                p.style = Paint.Style.STROKE
                p.strokeWidth = (s * 0.08f).coerceAtLeast(1.5f)
                p.strokeCap = Paint.Cap.ROUND
                p.color = Color.rgb(226, 232, 240)
                // Wifi arcs
                c.drawArc(RectF(s * 0.12f, s * 0.26f, s * 0.88f, s * 0.96f), 220f, 100f, false, p)
                c.drawArc(RectF(s * 0.26f, s * 0.42f, s * 0.74f, s * 0.90f), 220f, 100f, false, p)
                p.style = Paint.Style.FILL
                c.drawCircle(s * 0.50f, s * 0.74f, s * 0.07f, p)
            }
            else -> {
                baseSquircle(c, s, Color.rgb(59, 130, 246), Color.rgb(30, 64, 175))
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                c.drawCircle(s * 0.5f, s * 0.5f, s * 0.2f, p)
            }
        }
        return bmp
    }
}
