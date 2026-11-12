package com.example.ruangdroid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BrowserAndMedia {
    private fun dp(ctx: Context, v: Int) = UiKit.dp(ctx, v)

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    fun buildBrowserView(
        context: Context,
        winState: DesktopWindowState,
        initialUrl: String?
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 23, 38))
        }

        val webView: WebView = try {
            WebView(context)
        } catch (t: Throwable) {
            return UiKit.label(
                context,
                "Android System WebView tidak ditemukan di perangkat ini: ${t.message}",
                13f,
                UiKit.INK,
                maxLines = 5
            ).apply { setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16)) }
        }

        var desktopMode = true

        val navBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
            setBackgroundColor(Color.rgb(20, 30, 48))
        }

        val urlInput = EditText(context).apply {
            hint = "Ketik alamat web atau kata kunci pencarian…"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            textSize = 12.5f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            background = UiKit.rounded(context, Color.rgb(11, 17, 29), 10)
            setPadding(dp(context, 12), 0, dp(context, 12), 0)
        }

        fun navigateTo(raw: String) {
            val q = raw.trim()
            if (q.isEmpty()) return
            val finalUrl = when {
                q.startsWith("http://") || q.startsWith("https://") -> q
                q.contains(".") && !q.contains(" ") -> "https://$q"
                else -> "https://duckduckgo.com/?q=" + android.net.Uri.encode(q)
            }
            urlInput.setText(finalUrl)
            webView.loadUrl(finalUrl)
        }

        navBar.addView(UiKit.chipButton(context, "◀") { if (webView.canGoBack()) webView.goBack() },
            LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 4) })
        navBar.addView(UiKit.chipButton(context, "▶") { if (webView.canGoForward()) webView.goForward() },
            LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 4) })
        navBar.addView(UiKit.chipButton(context, "🔄") { webView.reload() },
            LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        navBar.addView(urlInput, LinearLayout.LayoutParams(0, dp(context, 32), 1f).apply { rightMargin = dp(context, 6) })

        navBar.addView(UiKit.chipButton(context, "Go", Color.argb(90, 37, 99, 235)) {
            navigateTo(urlInput.text?.toString().orEmpty())
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 4) })

        val uaBtn = UiKit.chipButton(context, "🖥️ PC") {}
        uaBtn.setOnClickListener {
            desktopMode = !desktopMode
            uaBtn.text = if (desktopMode) "🖥️ PC" else "📱 HP"
            webView.settings.userAgentString = if (desktopMode) DESKTOP_UA else null
            webView.settings.useWideViewPort = desktopMode
            webView.reload()
        }
        navBar.addView(uaBtn, LinearLayout.LayoutParams(-2, dp(context, 30)))
        root.addView(navBar, LinearLayout.LayoutParams(-1, -2))

        // Bookmarks bar
        val bmScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.rgb(16, 25, 42))
            setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 4))
        }
        val bmRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bookmarks = listOf(
            "🔎 Google" to "https://www.google.com",
            "▶️ YouTube" to "https://www.youtube.com",
            "🐙 GitHub" to "https://github.com/xykal/RuangDroid",
            "📚 Wikipedia" to "https://id.wikipedia.org",
            "🦆 DuckDuckGo" to "https://duckduckgo.com"
        )
        for ((name, link) in bookmarks) {
            bmRow.addView(UiKit.chipButton(context, name, Color.argb(28, 255, 255, 255)) {
                navigateTo(link)
            }, LinearLayout.LayoutParams(-2, dp(context, 26)).apply { rightMargin = dp(context, 6) })
        }
        bmScroll.addView(bmRow)
        root.addView(bmScroll, LinearLayout.LayoutParams(-1, -2))

        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(context, 3)))

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = DESKTOP_UA
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { urlInput.setText(it) }
                winState.titleView.text = "Browser — ${view?.title?.takeIf { it.isNotBlank() } ?: "Web"}"
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                navigateTo(urlInput.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        winState.onCloseCleanup = {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            } catch (_: Throwable) {
            }
        }

        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        navigateTo(initialUrl ?: "https://duckduckgo.com")
        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    fun buildPaintView(
        context: Context,
        winState: DesktopWindowState,
        initialImage: File?
    ): View {
        data class StrokeItem(val path: Path, val color: Int, val widthPx: Float)

        val strokes = mutableListOf<StrokeItem>()
        var currentColor = Color.rgb(56, 189, 248)
        var currentWidth = dp(context, 5).toFloat()
        var bgBitmap: Bitmap? = initialImage?.takeIf { it.exists() }?.let {
            try { BitmapFactory.decodeFile(it.absolutePath) } catch (_: Throwable) { null }
        }

        if (initialImage != null) {
            winState.titleView.text = "Paint Studio — ${initialImage.name}"
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 23, 38))
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 8))
        }

        val canvasView = object : View(context) {
            private var activePath: Path? = null
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.rgb(248, 250, 252))
                bgBitmap?.let { bmp ->
                    val dst = android.graphics.Rect(0, 0, width, height)
                    canvas.drawBitmap(bmp, null, dst, null)
                }
                for (s in strokes) {
                    paint.color = s.color
                    paint.strokeWidth = s.widthPx
                    canvas.drawPath(s.path, paint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val x = event.x
                val y = event.y
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        val p = Path().apply { moveTo(x, y) }
                        activePath = p
                        strokes.add(StrokeItem(p, currentColor, currentWidth))
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        activePath?.lineTo(x, y)
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        activePath = null
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }

        val toolScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val toolBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val palette = listOf(
            Color.rgb(15, 23, 42),
            Color.rgb(239, 68, 68),
            Color.rgb(249, 115, 22),
            Color.rgb(234, 179, 8),
            Color.rgb(34, 197, 94),
            Color.rgb(56, 189, 248),
            Color.rgb(99, 102, 241),
            Color.rgb(236, 72, 153)
        )
        for (c in palette) {
            val swatch = View(context).apply {
                background = UiKit.strokeRounded(context, c, Color.WHITE, 12)
                setOnClickListener { currentColor = c }
            }
            toolBar.addView(swatch, LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)).apply {
                rightMargin = dp(context, 5)
            })
        }

        toolBar.addView(UiKit.chipButton(context, "🧽 Eraser") {
            currentColor = Color.rgb(248, 250, 252)
            currentWidth = dp(context, 16).toFloat()
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        toolBar.addView(UiKit.chipButton(context, "Tipis") { currentWidth = dp(context, 3).toFloat() },
            LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 4) })
        toolBar.addView(UiKit.chipButton(context, "Tebal") { currentWidth = dp(context, 9).toFloat() },
            LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        toolBar.addView(UiKit.chipButton(context, "↩ Undo") {
            if (strokes.isNotEmpty()) {
                strokes.removeAt(strokes.lastIndex)
                canvasView.invalidate()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 4) })

        toolBar.addView(UiKit.chipButton(context, "🗑️ Clear") {
            strokes.clear()
            bgBitmap = null
            canvasView.invalidate()
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        toolBar.addView(UiKit.chipButton(context, "💾 Simpan PNG", Color.argb(90, 37, 99, 235)) {
            val w = canvasView.width.coerceAtLeast(320)
            val h = canvasView.height.coerceAtLeast(240)
            val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(outBmp)
            canvasView.draw(c)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(VirtualDrive.picturesDir(context), "Lukisan_$stamp.png")
            FileOutputStream(outFile).use { fos ->
                outBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            Toast.makeText(context, "Disimpan di C:\\Users\\Administrator\\Pictures\\${outFile.name}", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-2, dp(context, 28)))

        toolScroll.addView(toolBar)
        root.addView(toolScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })
        root.addView(canvasView, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    fun buildMediaPlayerView(
        context: Context,
        host: DesktopHostCallbacks,
        winState: DesktopWindowState,
        initialAudio: File?
    ): View {
        var player: MediaPlayer? = null
        var currentTrackName = initialAudio?.name ?: "Belum ada file audio dipilih"

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(14, 20, 34))
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
        }

        val titleTv = UiKit.label(context, "🎵 $currentTrackName", 14f, UiKit.INK, bold = true)
        val stateTv = UiKit.label(context, "Siap memutar audio lokal (.mp3/.wav/.m4a) atau Synth Pad di bawah", 11.5f, UiKit.MUTED, maxLines = 2)

        fun stopAndRelease() {
            try {
                player?.stop()
            } catch (_: Throwable) {
            }
            try {
                player?.release()
            } catch (_: Throwable) {
            }
            player = null
        }

        fun loadAndPlay(file: File) {
            stopAndRelease()
            try {
                val mp = MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                mp.start()
                player = mp
                currentTrackName = file.name
                titleTv.text = "🎵 $currentTrackName"
                stateTv.text = "▶ Sedang memutar (${mp.duration / 1000} detik)"
                winState.titleView.text = "Media Player — ${file.name}"
                mp.setOnCompletionListener {
                    stateTv.text = "⏹ Selesai diputar"
                }
            } catch (t: Throwable) {
                stateTv.text = "Gagal memutar file: ${t.message}"
            }
        }

        winState.onCloseCleanup = { stopAndRelease() }

        root.addView(titleTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })
        root.addView(stateTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) })

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(UiKit.chipButton(context, "📂 Pilih Audio HP", Color.argb(85, 37, 99, 235)) {
            host.pickAudioFromStorage()
        }, LinearLayout.LayoutParams(-2, dp(context, 34)).apply { rightMargin = dp(context, 8) })

        controls.addView(UiKit.chipButton(context, "⏯ Play/Pause") {
            val mp = player
            if (mp != null) {
                if (mp.isPlaying) {
                    mp.pause()
                    stateTv.text = "⏸ Dijeda"
                } else {
                    mp.start()
                    stateTv.text = "▶ Memutar"
                }
            } else if (initialAudio != null && initialAudio.exists()) {
                loadAndPlay(initialAudio)
            } else {
                Toast.makeText(context, "Pilih file audio dulu atau tekan Synth Pad!", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 34)).apply { rightMargin = dp(context, 8) })

        controls.addView(UiKit.chipButton(context, "⏹ Stop") {
            stopAndRelease()
            stateTv.text = "⏹ Dihentikan"
        }, LinearLayout.LayoutParams(-2, dp(context, 34)))

        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 14) })

        // Built-in PC Soundboard / Synth Pads so sound works immediately without external MP3s
        root.addView(
            UiKit.label(context, "🎹 Built-in PC Synth Deck (Klik untuk tes suara langsung):", 11.5f, UiKit.MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) }
        )

        val notes = listOf(
            "Do" to ToneGenerator.TONE_DTMF_1,
            "Re" to ToneGenerator.TONE_DTMF_2,
            "Mi" to ToneGenerator.TONE_DTMF_3,
            "Fa" to ToneGenerator.TONE_DTMF_4,
            "Sol" to ToneGenerator.TONE_DTMF_5,
            "La" to ToneGenerator.TONE_DTMF_6,
            "Si" to ToneGenerator.TONE_DTMF_8,
            "Do+" to ToneGenerator.TONE_DTMF_0
        )
        val padViews = notes.map { (name, tone) ->
            UiKit.chipButton(context, "♪ $name", Color.argb(55, 99, 102, 241)) {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
                    tg.startTone(tone, 190)
                    root.postDelayed({ try { tg.release() } catch (_: Throwable) {} }, 250)
                } catch (_: Throwable) {
                }
            }
        }
        root.addView(UiKit.buildGrid(context, 4, 38, padViews), LinearLayout.LayoutParams(-1, -2))

        initialAudio?.takeIf { it.exists() }?.let { loadAndPlay(it) }
        return root
    }
}
