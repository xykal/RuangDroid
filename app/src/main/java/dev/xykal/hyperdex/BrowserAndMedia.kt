package dev.xykal.hyperdex

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
import android.util.TypedValue
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
            setBackgroundColor(Color.rgb(13, 20, 34))
        }

        val webView: WebView = try {
            WebView(context)
        } catch (t: Throwable) {
            return UiKit.label(
                context,
                "WebView sistem tidak ditemukan: ${t.message}",
                12.5f,
                UiKit.INK,
                maxLines = 5
            ).apply { setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16)) }
        }

        var desktopMode = true

        val navBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8), dp(context, 5), dp(context, 8), dp(context, 5))
            setBackgroundColor(Color.rgb(18, 28, 46))
        }

        val urlInput = EditText(context).apply {
            hint = "Ketik URL atau kata kunci pencarian…"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 12f))
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            background = UiKit.rounded(context, Color.rgb(10, 15, 26), 9)
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
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

        navBar.addView(UiKit.chipButton(context, "<") { if (webView.canGoBack()) webView.goBack() },
            LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 4) })
        navBar.addView(UiKit.chipButton(context, ">") { if (webView.canGoForward()) webView.goForward() },
            LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 4) })
        navBar.addView(UiKit.chipButton(context, "Reload") { webView.reload() },
            LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        navBar.addView(urlInput, LinearLayout.LayoutParams(0, dp(context, 30), 1f).apply { rightMargin = dp(context, 5) })

        navBar.addView(UiKit.chipButton(context, "Go", Color.argb(110, 37, 99, 235)) {
            navigateTo(urlInput.text?.toString().orEmpty())
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 4) })

        val uaBtn = UiKit.chipButton(context, "PC") {}
        uaBtn.setOnClickListener {
            desktopMode = !desktopMode
            uaBtn.text = if (desktopMode) "PC" else "Mobile"
            webView.settings.userAgentString = if (desktopMode) DESKTOP_UA else null
            webView.settings.useWideViewPort = desktopMode
            webView.reload()
        }
        navBar.addView(uaBtn, LinearLayout.LayoutParams(-2, dp(context, 28)))
        root.addView(navBar, LinearLayout.LayoutParams(-1, -2))

        val bmScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.rgb(15, 23, 38))
            setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 4))
        }
        val bmRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bookmarks = listOf(
            "Google" to "https://www.google.com",
            "YouTube" to "https://www.youtube.com",
            "GitHub" to "https://github.com/xykal/HyperDeX",
            "Shizuku" to "https://github.com/RikkaApps/Shizuku/releases",
            "Winlator" to "https://github.com/brunodev85/winlator/releases"
        )
        for ((name, link) in bookmarks) {
            bmRow.addView(UiKit.chipButton(context, name, Color.argb(26, 255, 255, 255)) {
                navigateTo(link)
            }, LinearLayout.LayoutParams(-2, dp(context, 24)).apply { rightMargin = dp(context, 5) })
        }
        bmScroll.addView(bmRow)
        root.addView(bmScroll, LinearLayout.LayoutParams(-1, -2))

        // Custom sleek progress line (zero stock Android ProgressBar!)
        val progressLine = View(context).apply {
            setBackgroundColor(Color.rgb(56, 189, 248))
            visibility = View.GONE
        }
        root.addView(progressLine, LinearLayout.LayoutParams(0, dp(context, 2)))

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
                if (newProgress in 1..99) {
                    progressLine.visibility = View.VISIBLE
                    val lp = progressLine.layoutParams
                    lp.width = ((root.width.coerceAtLeast(100)) * (newProgress / 100f)).toInt()
                    progressLine.layoutParams = lp
                } else {
                    progressLine.visibility = View.GONE
                }
            }
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                navigateTo(urlInput.text?.toString().orEmpty())
                true
            } else false
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
        host: DesktopHostCallbacks,
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

        if (initialImage != null) winState.titleView.text = "Paint Studio — ${initialImage.name}"

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13, 20, 34))
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
                    canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, width, height), null)
                }
                for (s in strokes) {
                    paint.color = s.color
                    paint.strokeWidth = s.widthPx
                    canvas.drawPath(s.path, paint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        val p = Path().apply { moveTo(event.x, event.y) }
                        activePath = p
                        strokes.add(StrokeItem(p, currentColor, currentWidth))
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        activePath?.lineTo(event.x, event.y)
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
                background = UiKit.strokeRounded(context, c, Color.WHITE, 11)
                setOnClickListener { currentColor = c }
            }
            toolBar.addView(swatch, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)).apply {
                rightMargin = dp(context, 5)
            })
        }

        toolBar.addView(UiKit.chipButton(context, "Eraser") {
            currentColor = Color.rgb(248, 250, 252)
            currentWidth = dp(context, 16).toFloat()
        }, LinearLayout.LayoutParams(-2, dp(context, 26)).apply { rightMargin = dp(context, 4) })

        toolBar.addView(UiKit.chipButton(context, "Tipis") { currentWidth = dp(context, 3).toFloat() },
            LinearLayout.LayoutParams(-2, dp(context, 26)).apply { rightMargin = dp(context, 4) })
        toolBar.addView(UiKit.chipButton(context, "Tebal") { currentWidth = dp(context, 9).toFloat() },
            LinearLayout.LayoutParams(-2, dp(context, 26)).apply { rightMargin = dp(context, 4) })

        toolBar.addView(UiKit.chipButton(context, "Undo") {
            if (strokes.isNotEmpty()) {
                strokes.removeAt(strokes.lastIndex)
                canvasView.invalidate()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 26)).apply { rightMargin = dp(context, 4) })

        toolBar.addView(UiKit.chipButton(context, "Clear") {
            strokes.clear(); bgBitmap = null; canvasView.invalidate()
        }, LinearLayout.LayoutParams(-2, dp(context, 26)).apply { rightMargin = dp(context, 4) })

        toolBar.addView(UiKit.chipButton(context, "Blur C++", Color.argb(95, 16, 185, 129)) {
            val w = canvasView.width.coerceAtLeast(320)
            val h = canvasView.height.coerceAtLeast(240)
            val snap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            canvasView.draw(Canvas(snap))
            if (NativeCore.ccLoaded) {
                NativeCore.ccAcrylicBlurBitmap(snap, 10, 0)
            }
            strokes.clear()
            bgBitmap = snap
            canvasView.invalidate()
        }, LinearLayout.LayoutParams(-2, dp(context, 26)).apply { rightMargin = dp(context, 4) })

        toolBar.addView(UiKit.chipButton(context, "Simpan PNG", Color.argb(100, 37, 99, 235)) {
            val w = canvasView.width.coerceAtLeast(320)
            val h = canvasView.height.coerceAtLeast(240)
            val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            canvasView.draw(Canvas(outBmp))
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(VirtualDrive.picturesDir(context), "Lukisan_$stamp.png")
            FileOutputStream(outFile).use { outBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            host.showToast("Paint Studio", "Tersimpan di Pictures\\${outFile.name}")
        }, LinearLayout.LayoutParams(-2, dp(context, 26)))

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
        var currentTrackName = initialAudio?.name ?: "Tidak ada track dipilih"

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(12, 18, 32))
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
        }

        val titleTv = UiKit.label(context, currentTrackName, 13.5f, UiKit.INK, bold = true)
        val stateTv = UiKit.label(context, "Pilih file audio atau mainkan PC Synth Pad di bawah", 11f, UiKit.MUTED, maxLines = 2)

        fun stopAndRelease() {
            try { player?.stop() } catch (_: Throwable) {}
            try { player?.release() } catch (_: Throwable) {}
            player = null
        }

        fun loadAndPlay(file: File) {
            stopAndRelease()
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                }
                player = mp
                currentTrackName = file.name
                titleTv.text = currentTrackName
                stateTv.text = "Memutar (${mp.duration / 1000} dtk)"
                winState.titleView.text = "Media Player — ${file.name}"
                mp.setOnCompletionListener { stateTv.text = "Selesai diputar" }
            } catch (t: Throwable) {
                stateTv.text = "Gagal memutar: ${t.message}"
            }
        }

        winState.onCloseCleanup = { stopAndRelease() }

        root.addView(titleTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 3) })
        root.addView(stateTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) })

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(UiKit.chipButton(context, "Pilih Audio", Color.argb(95, 37, 99, 235)) {
            host.pickAudioFromStorage()
        }, LinearLayout.LayoutParams(-2, dp(context, 32)).apply { rightMargin = dp(context, 6) })

        controls.addView(UiKit.chipButton(context, "Play / Pause") {
            val mp = player
            if (mp != null) {
                if (mp.isPlaying) { mp.pause(); stateTv.text = "Dijeda" }
                else { mp.start(); stateTv.text = "Memutar" }
            } else if (initialAudio != null && initialAudio.exists()) {
                loadAndPlay(initialAudio)
            } else {
                host.showToast("Media Player", "Pilih file audio terlebih dahulu")
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 32)).apply { rightMargin = dp(context, 6) })

        controls.addView(UiKit.chipButton(context, "Stop") {
            stopAndRelease()
            stateTv.text = "Dihentikan"
        }, LinearLayout.LayoutParams(-2, dp(context, 32)))

        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) })
        root.addView(UiKit.label(context, "Built-in PC Synth Deck:", 11f, UiKit.MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 5) })

        val notes = listOf(
            "C4" to ToneGenerator.TONE_DTMF_1,
            "D4" to ToneGenerator.TONE_DTMF_2,
            "E4" to ToneGenerator.TONE_DTMF_3,
            "F4" to ToneGenerator.TONE_DTMF_4,
            "G4" to ToneGenerator.TONE_DTMF_5,
            "A4" to ToneGenerator.TONE_DTMF_6,
            "B4" to ToneGenerator.TONE_DTMF_8,
            "C5" to ToneGenerator.TONE_DTMF_0
        )
        val padViews = notes.map { (name, tone) ->
            UiKit.chipButton(context, name, Color.argb(60, 99, 102, 241)) {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
                    tg.startTone(tone, 180)
                    root.postDelayed({ try { tg.release() } catch (_: Throwable) {} }, 240)
                } catch (_: Throwable) {
                }
            }
        }
        root.addView(UiKit.buildGrid(context, 4, 34, padViews), LinearLayout.LayoutParams(-1, -2))

        initialAudio?.takeIf { it.exists() }?.let { loadAndPlay(it) }
        return root
    }
}
