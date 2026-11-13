package dev.xykal.hyperdex

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object ToolsApps {
    private fun dp(ctx: Context, v: Int) = UiKit.dp(ctx, v)

    // ==================== 1. SCIENTIFIC CALCULATOR ====================
    fun buildCalculatorView(context: Context): View {
        var expr = ""
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 18, 32))
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 10))
        }

        val historyTv = UiKit.label(context, "Scientific PC Calculator", 11f, UiKit.MUTED).apply {
            gravity = Gravity.END
        }
        val screenTv = TextView(context).apply {
            text = "0"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 22f))
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.INK)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            background = UiKit.rounded(context, Color.rgb(8, 12, 22), 10)
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            maxLines = 1
        }

        root.addView(historyTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })
        root.addView(screenTv, LinearLayout.LayoutParams(-1, dp(context, 48)).apply { bottomMargin = dp(context, 8) })

        val keys = listOf(
            "C", "(", ")", "DEL", "÷",
            "sin", "7", "8", "9", "×",
            "cos", "4", "5", "6", "-",
            "√", "1", "2", "3", "+",
            "π", "0", ".", "^", "="
        )

        fun formatDouble(v: Double): String {
            if (v.isNaN() || v.isInfinite()) return "Error"
            val asLong = v.toLong()
            return if (v == asLong.toDouble()) asLong.toString()
            else String.format(Locale.US, "%.8f", v).trimEnd('0').trimEnd('.')
        }

        val btnViews = keys.map { key ->
            val isOp = key in listOf("÷", "×", "-", "+", "=", "^")
            val isFn = key in listOf("C", "(", ")", "DEL", "sin", "cos", "√", "π")
            val bg = when {
                key == "=" -> Color.argb(190, 37, 99, 235)
                isOp -> Color.argb(85, 59, 130, 246)
                isFn -> Color.argb(46, 148, 163, 184)
                else -> Color.argb(32, 255, 255, 255)
            }
            UiKit.chipButton(context, key, bg) {
                when (key) {
                    "C" -> { expr = ""; historyTv.text = "Cleared"; screenTv.text = "0" }
                    "DEL" -> {
                        if (expr.isNotEmpty()) expr = expr.dropLast(1)
                        screenTv.text = expr.ifEmpty { "0" }
                    }
                    "=" -> {
                        if (expr.isNotBlank()) {
                            val norm = expr.replace("×", "*").replace("÷", "/").replace("π", "pi")
                            val rustRes = NativeCore.safeEvalMath(norm)
                            val res = if (!rustRes.startsWith("ERR")) {
                                rustRes
                            } else {
                                try {
                                    formatDouble(evalMath(expr.replace("×", "*").replace("÷", "/").replace("π", Math.PI.toString())))
                                } catch (_: Throwable) { "Syntax Error" }
                            }
                            historyTv.text = "$expr = (Rust Engine)"
                            screenTv.text = res
                            if (res != "Syntax Error" && !res.startsWith("ERR")) expr = res
                        }
                    }
                    "sin", "cos", "√" -> {
                        expr += if (key == "√") "sqrt(" else "$key("
                        screenTv.text = expr
                    }
                    else -> {
                        expr += key
                        screenTv.text = expr
                    }
                }
            }
        }

        val scroll = ScrollView(context).apply { isFillViewport = true }
        scroll.addView(UiKit.buildGrid(context, 5, 36, btnViews, hGapDp = 4, vGapDp = 4))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun evalMath(str: String): Double {
        return object {
            var pos = -1
            var ch = 0
            fun nextChar() { ch = if (++pos < str.length) str[pos].code else -1 }
            fun eat(c: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == c) { nextChar(); return true }
                return false
            }
            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected")
                return x
            }
            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }
            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> x /= parseFactor()
                        else -> return x
                    }
                }
            }
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val start = pos
                if (eat('('.code)) {
                    x = parseExpression(); eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(start, pos).toDouble()
                } else if (ch in 'a'.code..'z'.code) {
                    while (ch in 'a'.code..'z'.code) nextChar()
                    val fn = str.substring(start, pos)
                    x = parseFactor()
                    x = when (fn) {
                        "sqrt" -> sqrt(x)
                        "sin" -> sin(Math.toRadians(x))
                        "cos" -> cos(Math.toRadians(x))
                        "tan" -> tan(Math.toRadians(x))
                        else -> throw RuntimeException("fn")
                    }
                } else throw RuntimeException("tok")
                if (eat('^'.code)) x = x.pow(parseFactor())
                return x
            }
        }.parse()
    }

    // ==================== 2. TERMINAL CMD + SHIZUKU RISH + TERMUX ====================
    fun buildTerminalView(
        context: Context,
        host: DesktopHostCallbacks,
        winManager: FloatingWindowManager,
        initialCommand: String?
    ): View {
        var cwd = VirtualDrive.desktopDir(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 12, 20))
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        }

        val scroll = ScrollView(context).apply { isFillViewport = true }
        val outputTv = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 11.5f))
            setTextColor(Color.rgb(134, 239, 172))
            text = "HyperDeX Polyglot Shell [v5.0.0 — Kotlin / Rust / C11 / C++20 GLES3 / Shizuku]\nKetik 'help' untuk daftar perintah lengkap.\n\n"
        }
        scroll.addView(outputTv)

        val promptRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(context, Color.rgb(15, 23, 38), 8)
            setPadding(dp(context, 8), dp(context, 2), dp(context, 6), dp(context, 2))
        }
        val promptPrefix = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 11.5f))
            setTextColor(Color.rgb(96, 165, 250))
            text = "${VirtualDrive.toWindowsPath(context, cwd)}> "
        }
        val cmdInput = EditText(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 12f))
            setTextColor(Color.WHITE)
            setHintTextColor(UiKit.MUTED)
            hint = "help, dir, rish <adb_cmd>, termux <cmd>, wine…"
            setSingleLine(true)
            background = null
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        fun appendOut(msg: String) {
            outputTv.append(msg + "\n")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }

        fun runSingleCommand(rawLine: String) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("@echo") || line.startsWith("rem ", ignoreCase = true)) return
            appendOut("${VirtualDrive.toWindowsPath(context, cwd)}> $line")

            val parts = line.split(Regex("\\s+"), limit = 2)
            val cmd = parts[0].lowercase(Locale.ROOT)
            val arg = parts.getOrNull(1)?.trim().orEmpty()

            when (cmd) {
                "help" -> appendOut(
                    """
                    Perintah HyperDeX Polyglot Terminal v4.0:
                      dir / cd / mkdir / type / del / cls
                      native           Status modul Rust + C11 + C++20 & Anti-Clone Shield
                      sysinfo          Telemetri Kernel /proc & CPU via C11 (libhyper_sys_c.so)
                      bench            Benchmark kecepatan Rust SHA-256 + C11 Compute
                      calc <rumus>     Kalkulator saintifik via Rust (contoh: calc 2^16 + sqrt(81))
                      sha256 <teks>    Hash kriptografi SHA-256 via Rust (librust_hypercore.so)
                      rish <cmd>       Eksekusi perintah ADB/Root via Shizuku!
                      termux <cmd>     Kirim perintah bash ke Termux RunCommandService!
                      start <app>      explorer | shield | browser | notepad | calc | paint | shizuku | exe
                      wine <file.exe>  Jalankan .exe di Floating Window
                    """.trimIndent()
                )
                "cls", "clear" -> outputTv.text = ""
                "ver" -> appendOut("HyperDeX Ultimate OS v5.0.0 | Rust=${NativeCore.rustLoaded} C11=${NativeCore.cLoaded} C++20=${NativeCore.ccLoaded} | Shizuku: ${ShizukuBridge.privilegeLabel()}")
                "native" -> {
                    val v = NativeCore.verifyAppSecurity(context)
                    appendOut("Shield Status : ${v.headline}")
                    appendOut("Rust (.so)    : ${v.rustReport}")
                    appendOut("C11 (.so)     : ${v.cEnvReport}")
                    appendOut("C++20 (.so)   : ${v.ccQuorumReport}")
                }
                "bench" -> {
                    val cOps = if (NativeCore.cLoaded) NativeCore.cCpuBenchScore(3_000_000) else 0L
                    val rHash = if (NativeCore.rustLoaded) NativeCore.rustBenchSha256(30_000) else "offline"
                    appendOut("C11 Compute   : $cOps kOps/sec\n$rHash")
                }
                "calc" -> {
                    if (arg.isEmpty()) appendOut("Contoh: calc sqrt(144) + 2^10")
                    else appendOut("= ${NativeCore.safeEvalMath(arg)}")
                }
                "sha256" -> {
                    if (arg.isEmpty()) appendOut("Contoh: sha256 HyperDeX")
                    else appendOut(if (NativeCore.rustLoaded) NativeCore.rustSha256Text(arg) else "Rust engine offline")
                }
                "title" -> Unit
                "echo" -> appendOut(arg)
                "rish", "shizuku" -> {
                    if (arg.isEmpty()) {
                        appendOut("Status Shizuku: ${ShizukuBridge.privilegeLabel()}\nContoh: rish pm list packages -3")
                    } else {
                        ShizukuBridge.runPrivilegedAsync(arg) { code, out ->
                            root.post { appendOut("[Shizuku exit=$code]\n$out") }
                        }
                    }
                }
                "termux" -> {
                    if (arg.isEmpty()) {
                        host.openShizukuHub()
                    } else {
                        val sent = TermuxBridge.sendCommandToTermux(context, arg, background = false)
                        appendOut(if (sent) "Dikirim ke Termux: $arg" else "Gagal: pastikan Termux terpasang & allow-external-apps=true")
                    }
                }
                "sysinfo" -> {
                    val dm = context.resources.displayMetrics
                    appendOut("Device     : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                    appendOut("Display    : ${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi} dpi")
                    appendOut("Shizuku    : ${ShizukuBridge.privilegeLabel()}")
                    if (NativeCore.cLoaded) {
                        appendOut(NativeCore.cReadKernelTelemetry())
                    }
                }
                "tasklist" -> {
                    val wins = winManager.openWindows()
                    appendOut("ACTIVE WINDOWS (${wins.size}):")
                    wins.forEachIndexed { idx, w ->
                        appendOut("  [${idx + 1}] ${w.appKey.padEnd(14)} | ${if (w.minimized) "MINIMIZED" else "RUNNING  "} | ${w.title}")
                    }
                }
                "dir" -> {
                    val files = cwd.listFiles().orEmpty()
                    appendOut(" Directory of ${VirtualDrive.toWindowsPath(context, cwd)}")
                    files.forEach { f ->
                        val tag = if (f.isDirectory) "<DIR>     " else f.length().toString().padStart(10)
                        appendOut("  $tag  ${f.name}")
                    }
                }
                "cd" -> {
                    val target = when {
                        arg.isEmpty() -> cwd
                        arg == ".." -> cwd.parentFile ?: cwd
                        arg.equals("C:\\", ignoreCase = true) || arg == "/" -> VirtualDrive.root(context)
                        else -> File(cwd, arg)
                    }
                    if (target.exists() && target.isDirectory) {
                        cwd = target
                        promptPrefix.text = "${VirtualDrive.toWindowsPath(context, cwd)}> "
                    } else appendOut("Folder tidak ditemukan: $arg")
                }
                "mkdir" -> if (arg.isNotEmpty()) { File(cwd, arg).mkdirs(); appendOut("Dibuat: $arg") }
                "type", "cat" -> if (arg.isNotEmpty()) {
                    val f = File(cwd, arg)
                    if (f.exists() && f.isFile) appendOut(f.readText().take(4000)) else appendOut("Tidak ada file: $arg")
                }
                "del", "rm" -> if (arg.isNotEmpty()) {
                    val f = File(cwd, arg)
                    if (f.exists() && f.delete()) appendOut("Dihapus: $arg") else appendOut("Gagal menghapus: $arg")
                }
                "start" -> when (arg.lowercase(Locale.ROOT)) {
                    "explorer" -> host.openExplorer(cwd)
                    "shield", "native", "core" -> host.openNativeHub()
                    "browser" -> host.openBrowser(null)
                    "notepad" -> host.openNotepad(null)
                    "calc", "calculator" -> host.openCalculator()
                    "paint" -> host.openPaint(null)
                    "shizuku", "termux" -> host.openShizukuHub()
                    "exe", "wine" -> host.openExeCenter(null)
                    else -> appendOut("start explorer | shield | browser | notepad | calc | paint | shizuku | exe")
                }
                "wine" -> {
                    val target = if (arg.isNotEmpty()) File(cwd, arg).takeIf { it.exists() }
                        ?: File(VirtualDrive.installersDir(context), arg).takeIf { it.exists() }
                    else null
                    if (target != null) host.openExeFile(target) else host.openExeCenter(null)
                }
                else -> {
                    try {
                        val proc = ProcessBuilder("/system/bin/sh", "-c", line)
                            .directory(cwd)
                            .redirectErrorStream(true)
                            .start()
                        val out = BufferedReader(InputStreamReader(proc.inputStream)).readText().take(4000)
                        proc.waitFor()
                        appendOut(out.ifBlank { "(Selesai)" })
                    } catch (t: Throwable) {
                        appendOut("Perintah gagal: ${t.message}")
                    }
                }
            }
        }

        promptRow.addView(promptPrefix)
        promptRow.addView(cmdInput, LinearLayout.LayoutParams(0, dp(context, 32), 1f))
        promptRow.addView(UiKit.chipButton(context, "Run", Color.argb(110, 34, 197, 94)) {
            val txt = cmdInput.text?.toString().orEmpty()
            cmdInput.setText("")
            runSingleCommand(txt)
        }, LinearLayout.LayoutParams(-2, dp(context, 26)))

        cmdInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val txt = cmdInput.text?.toString().orEmpty()
                cmdInput.setText("")
                runSingleCommand(txt)
                true
            } else false
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(context, 5) })
        root.addView(promptRow, LinearLayout.LayoutParams(-1, -2))

        initialCommand?.lines()?.forEach { runSingleCommand(it) }
        return root
    }

    // ==================== 3. TASK MANAGER ====================
    fun buildTaskManagerView(
        context: Context,
        winState: DesktopWindowState,
        winManager: FloatingWindowManager
    ): View {
        val ramSamples = MutableList(28) { 45f }
        var alive = true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 18, 32))
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        }

        val statsHeader = UiKit.label(context, "Memuat metrik…", 11.5f, Color.rgb(125, 211, 252), bold = true)
        root.addView(statsHeader, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })

        val graphView = object : View(context) {
            private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 2).toFloat()
                color = Color.rgb(56, 189, 248)
            }
            private val gridPaint = Paint().apply {
                color = Color.argb(32, 255, 255, 255)
                strokeWidth = 1f
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.rgb(8, 12, 22))
                val w = width.toFloat(); val h = height.toFloat()
                for (i in 1..3) canvas.drawLine(0f, h * i / 4f, w, h * i / 4f, gridPaint)
                if (ramSamples.size >= 2) {
                    val path = Path()
                    val step = w / (ramSamples.size - 1)
                    ramSamples.forEachIndexed { idx, pct ->
                        val x = idx * step
                        val y = h - (pct.coerceIn(0f, 100f) / 100f) * (h - 10f) - 5f
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, linePaint)
                }
            }
        }
        root.addView(graphView, LinearLayout.LayoutParams(-1, dp(context, 72)).apply { bottomMargin = dp(context, 8) })

        val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { isFillViewport = true }
        scroll.addView(listContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun refreshStats() {
            if (!alive) return
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                val usedPct = ((mi.totalMem - mi.availMem).toFloat() / mi.totalMem.coerceAtLeast(1L) * 100f)
                ramSamples.removeAt(0); ramSamples.add(usedPct)
                graphView.invalidate()

                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val bat = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
                statsHeader.text = String.format(
                    Locale.US,
                    "RAM: %.0f%% (%.1f / %.1f GB)  |  Baterai: %d%%  |  Jendela: %d",
                    usedPct, (mi.totalMem - mi.availMem) / 1073741824.0, mi.totalMem / 1073741824.0, bat, winManager.openWindows().size
                )

                listContainer.removeAllViews()
                for (w in winManager.openWindows()) {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiKit.rounded(context, Color.argb(24, 255, 255, 255), 8)
                        setPadding(dp(context, 8), dp(context, 5), dp(context, 8), dp(context, 5))
                    }
                    row.addView(ImageView(context).apply {
                        setImageDrawable(VectorIcons.get(context, w.iconId, dp(context, 18)))
                    }, LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply { rightMargin = dp(context, 8) })

                    row.addView(UiKit.label(context, w.title, 11.5f, UiKit.INK, bold = true),
                        LinearLayout.LayoutParams(0, -2, 1f))

                    row.addView(UiKit.chipButton(context, "Fokus") { winManager.focusWindow(w.id) },
                        LinearLayout.LayoutParams(-2, dp(context, 24)).apply { rightMargin = dp(context, 4) })

                    if (w.id != winState.id) {
                        row.addView(UiKit.chipButton(context, "End Task", Color.argb(140, 220, 38, 38)) {
                            winManager.closeWindow(w.id)
                            refreshStats()
                        }, LinearLayout.LayoutParams(-2, dp(context, 24)))
                    }
                    listContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })
                }
            } catch (_: Throwable) {
            }
            root.postDelayed({ refreshStats() }, 1600)
        }

        winState.onCloseCleanup = { alive = false }
        refreshStats()
        return root
    }

    // ==================== 4. CONTROL PANEL (MICRO SCALE, TASKBAR, LAUNCHER LOCK & THEME) ====================
    fun buildControlPanelView(
        context: Context,
        prefs: DesktopPrefs,
        host: DesktopHostCallbacks
    ): View {
        val theme = prefs.currentTheme
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(14, 17, 23))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 12))
        }

        // 0. DEFAULT LAUNCHER & HARD LOCK GUARD CARD
        val isHome = host.isDefaultHomeActive()
        val lockCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.strokeRounded(
                context,
                Color.rgb(20, 24, 33),
                if (isHome && prefs.launcherLockEnabled) Color.argb(120, 52, 211, 153) else Color.argb(45, 255, 255, 255),
                8
            )
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        }
        lockCard.addView(
            UiKit.label(
                context,
                if (isHome && prefs.launcherLockEnabled) "Launcher Lock: AKTIF (Terkunci di HyperDeX — Launcher Bawaan Diblokir)"
                else if (isHome) "Default Launcher: HyperDeX (Mode Kunci Mati)"
                else "Default Launcher: Belum Diset ke HyperDeX",
                10.8f,
                if (isHome && prefs.launcherLockEnabled) Color.rgb(74, 222, 128) else Color.rgb(250, 204, 21),
                bold = true
            )
        )
        lockCard.addView(
            UiKit.label(
                context,
                "Saat HyperDeX dijadikan Default Home, pengguna tidak bisa keluar ke launcher bawaan HP sebelum menekan tombol 'Unlock & Ganti Launcher' di bawah ini.",
                9.4f,
                UiKit.MUTED,
                maxLines = 3
            ).apply { setPadding(0, dp(context, 2), 0, dp(context, 6)) }
        )

        val lockBtnsScroll = android.widget.HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val lockBtns = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        lockBtns.addView(UiKit.chipButton(context, "Set Default & Lock", Color.argb(130, 16, 185, 129)) {
            prefs.launcherLockEnabled = true
            host.requestSetDefaultLauncher()
        }, LinearLayout.LayoutParams(-2, dp(context, 25)).apply { rightMargin = dp(context, 5) })

        lockBtns.addView(UiKit.chipButton(context, "Unlock & Ganti ke Bawaan", Color.argb(120, 185, 28, 28)) {
            host.unlockAndSwitchDefaultLauncher()
        }, LinearLayout.LayoutParams(-2, dp(context, 25)).apply { rightMargin = dp(context, 5) })

        lockBtns.addView(UiKit.chipButton(
            context,
            if (prefs.launcherLockPin.isEmpty()) "Set PIN Kunci" else "Ubah/Hapus PIN"
        ) {
            host.overlays.showInputPrompt(
                title = "PIN Buka Kunci Launcher (Kosongkan untuk tanpa PIN)",
                initialText = prefs.launcherLockPin,
                hintText = "Contoh: 1234",
                confirmLabel = "Simpan PIN"
            ) { pin ->
                prefs.launcherLockPin = pin
                host.showToast("Launcher Lock", if (pin.isEmpty()) "PIN dinonaktifkan" else "PIN kunci tersimpan")
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 25)).apply { rightMargin = dp(context, 5) })

        lockBtns.addView(UiKit.chipButton(context, "Onboarding Awal") {
            host.showOnboardingWizard()
        }, LinearLayout.LayoutParams(-2, dp(context, 25)))

        lockBtnsScroll.addView(lockBtns)
        lockCard.addView(lockBtnsScroll, LinearLayout.LayoutParams(-1, -2))
        root.addView(lockCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) })

        // 1. Live Micro-Density UI Scaling Slider (55% .. 135%)
        val scaleHeader = UiKit.label(
            context,
            "Ukuran & Kerapatan UI Desktop: ${prefs.uiScalePercent}% (Default Kecil PC: 84%)",
            10.8f,
            UiKit.INK,
            bold = true
        )
        root.addView(scaleHeader)

        val scaleSliderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 2), 0, dp(context, 4))
        }
        var pendingPct = prefs.uiScalePercent
        val slider = UiKit.buildFluentSlider(context, 55, 130, prefs.uiScalePercent, theme.accent) { pct ->
            pendingPct = pct
            scaleHeader.text = "Ukuran & Kerapatan UI Desktop: $pct%"
        }
        scaleSliderRow.addView(slider, LinearLayout.LayoutParams(0, dp(context, 26), 1f).apply { rightMargin = dp(context, 6) })
        scaleSliderRow.addView(UiKit.chipButton(context, "Terapkan", Color.argb(140, 14, 165, 233)) {
            prefs.uiScalePercent = pendingPct
            VectorIcons.clearCache()
            host.refreshDesktopUI()
            host.showToast("UI Density", "Kerapatan UI diatur ke ${prefs.uiScalePercent}%")
        }, LinearLayout.LayoutParams(-2, dp(context, 25)))
        root.addView(scaleSliderRow)

        // Quick UI Scale Presets
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(context, 8))
        }
        listOf("65% Ultra Kecil" to 65, "75% Micro PC" to 75, "84% Default" to 84, "100% Sedang" to 100).forEachIndexed { idx, (lbl, v) ->
            presetRow.addView(UiKit.chipButton(context, lbl) {
                prefs.uiScalePercent = v
                VectorIcons.clearCache()
                host.refreshDesktopUI()
            }, LinearLayout.LayoutParams(0, dp(context, 23), 1f).apply { if (idx < 3) rightMargin = dp(context, 4) })
        }
        root.addView(presetRow)

        // Taskbar Height Slider (26dp .. 44dp)
        val tbHeader = UiKit.label(context, "Tinggi Taskbar: ${prefs.taskbarHeightDp}dp", 10.5f, UiKit.INK, bold = true)
        root.addView(tbHeader)
        val tbSlider = UiKit.buildFluentSlider(context, 26, 44, prefs.taskbarHeightDp, theme.accent) { h ->
            prefs.taskbarHeightDp = h
            tbHeader.text = "Tinggi Taskbar: ${h}dp"
            host.refreshDesktopUI()
        }
        root.addView(tbSlider, LinearLayout.LayoutParams(-1, dp(context, 24)).apply { bottomMargin = dp(context, 8) })

        // 2. Theme & Wallpaper
        root.addView(UiKit.label(context, "Palet Graphite & Wallpaper", 10.8f, UiKit.INK, bold = true))
        val themeButtons = prefs.themes.mapIndexed { index, palette ->
            UiKit.chipButton(
                context,
                palette.name,
                bgColor = palette.bgColors[1],
                strokeColor = if (prefs.themeIndex == index && !prefs.useCustomWallpaper) palette.accent else Color.argb(38, 255, 255, 255)
            ) {
                prefs.themeIndex = index
                prefs.clearCustomWallpaper()
                host.refreshDesktopUI()
            }
        }
        root.addView(UiKit.buildGrid(context, 3, 27, themeButtons), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(context, 4); bottomMargin = dp(context, 6)
        })

        val wpRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        wpRow.addView(UiKit.chipButton(context, "Wallpaper Galeri") {
            host.pickCustomWallpaper()
        }, LinearLayout.LayoutParams(0, dp(context, 26), 1f).apply { rightMargin = dp(context, 5) })
        wpRow.addView(UiKit.chipButton(context, "HyperShield Core") {
            host.openNativeHub()
        }, LinearLayout.LayoutParams(0, dp(context, 26), 1f).apply { rightMargin = dp(context, 5) })
        wpRow.addView(UiKit.chipButton(context, "Floating & Shizuku") {
            host.openShizukuHub()
        }, LinearLayout.LayoutParams(0, dp(context, 26), 1f))
        root.addView(wpRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) })

        // 3. Toggles
        fun toggleRow(title: String, sub: String, getVal: () -> Boolean, onFlip: () -> Unit): View {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.strokeRounded(context, Color.rgb(19, 23, 31), Color.argb(26, 255, 255, 255), 7)
                setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5))
            }
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(UiKit.label(context, title, 10.4f, UiKit.INK, bold = true))
            col.addView(UiKit.label(context, sub, 9.0f, UiKit.MUTED, maxLines = 2))
            box.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

            val btn = UiKit.chipButton(context, "") {}
            fun sync() {
                val on = getVal()
                btn.text = if (on) "ON" else "OFF"
                btn.background = UiKit.strokeRounded(
                    context,
                    if (on) Color.argb(120, 16, 185, 129) else Color.rgb(26, 30, 39),
                    if (on) Color.argb(140, 52, 211, 153) else Color.argb(40, 255, 255, 255),
                    6
                )
            }
            sync()
            btn.setOnClickListener { onFlip(); sync() }
            box.addView(btn, LinearLayout.LayoutParams(dp(context, 48), dp(context, 23)))
            return box
        }

        root.addView(toggleRow(
            "Kunci Launcher (Blokir Kembali ke Launcher Bawaan)",
            "Otomatis mengunci di HyperDeX selama masih diset sebagai Default Home",
            { prefs.launcherLockEnabled },
            { prefs.launcherLockEnabled = !prefs.launcherLockEnabled; host.refreshDesktopUI() }
        ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })

        root.addView(toggleRow(
            "Mode PC Landscape Otomatis",
            "Kunci tampilan mendatar penuh layaknya monitor PC",
            { prefs.landscapeLock },
            { prefs.landscapeLock = !prefs.landscapeLock; host.refreshDesktopUI() }
        ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })

        root.addView(toggleRow(
            "Full Window Immersive (Tanpa Bar Android)",
            "Sembunyikan status bar & navigation bar bawaan Android sepenuhnya",
            { prefs.immersiveMode },
            { prefs.immersiveMode = !prefs.immersiveMode; host.refreshDesktopUI() }
        ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })

        root.addView(toggleRow(
            "Taskbar Menempel Penuh (Flush Edge PC)",
            "Matikan untuk memakai gaya Floating Dock melayang",
            { prefs.flushTaskbar },
            { prefs.flushTaskbar = !prefs.flushTaskbar; host.refreshDesktopUI() }
        ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })

        root.addView(toggleRow(
            "Taskbar Rata Tengah & Tab Hanya Ikon",
            "Rapatkan ikon aplikasi ke tengah taskbar seperti Windows 11",
            { prefs.centerTaskbar },
            {
                prefs.centerTaskbar = !prefs.centerTaskbar
                prefs.compactTaskbarIconsOnly = prefs.centerTaskbar
                host.refreshDesktopUI()
            }
        ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })

        root.addView(toggleRow(
            "Live GPU Aurora Shader Wallpaper (OpenGL ES 3.0 C++)",
            "Render animasi shader di GPU lewat libhyper_gfx_cc.so",
            { prefs.liveShaderWallpaper },
            { prefs.liveShaderWallpaper = !prefs.liveShaderWallpaper; host.refreshDesktopUI() }
        ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })

        root.addView(toggleRow(
            "Efek Acrylic Frosted Wallpaper (C++20 Native)",
            "Proses StackBlur + Luminosity Tint langsung lewat libhyper_gfx_cc.so",
            { prefs.acrylicBlurWallpaper },
            { prefs.acrylicBlurWallpaper = !prefs.acrylicBlurWallpaper; host.refreshDesktopUI() }
        ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) })

        val iconScaleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        iconScaleRow.addView(UiKit.label(context, "Ukuran Ikon:", 10.4f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        listOf("Micro" to 0, "Kecil" to 1, "Sedang" to 2, "Besar" to 3).forEach { (lbl, sc) ->
            iconScaleRow.addView(UiKit.chipButton(context, lbl) {
                prefs.iconScale = sc
                host.refreshDesktopUI()
            }, LinearLayout.LayoutParams(-2, dp(context, 24)).apply { leftMargin = dp(context, 4) })
        }
        root.addView(iconScaleRow)

        scroll.addView(root)
        return scroll
    }
}
