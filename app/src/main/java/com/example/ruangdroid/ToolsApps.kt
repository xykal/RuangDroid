package com.example.ruangdroid

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
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
            setBackgroundColor(Color.rgb(14, 20, 34))
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 10))
        }

        val historyTv = UiKit.label(context, "Scientific PC Calculator", 11.5f, UiKit.MUTED).apply {
            gravity = Gravity.END
        }
        val screenTv = TextView(context).apply {
            text = "0"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.INK)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            background = UiKit.rounded(context, Color.rgb(9, 14, 24), 10)
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            maxLines = 1
        }

        root.addView(historyTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })
        root.addView(screenTv, LinearLayout.LayoutParams(-1, dp(context, 52)).apply { bottomMargin = dp(context, 8) })

        val keys = listOf(
            "C", "(", ")", "⌫", "÷",
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
            val isFn = key in listOf("C", "(", ")", "⌫", "sin", "cos", "√", "π")
            val bg = when {
                key == "=" -> Color.argb(185, 37, 99, 235)
                isOp -> Color.argb(85, 59, 130, 246)
                isFn -> Color.argb(48, 148, 163, 184)
                else -> Color.argb(34, 255, 255, 255)
            }
            UiKit.chipButton(context, key, bg) {
                when (key) {
                    "C" -> {
                        expr = ""
                        historyTv.text = "Cleared"
                        screenTv.text = "0"
                    }
                    "⌫" -> {
                        if (expr.isNotEmpty()) expr = expr.dropLast(1)
                        screenTv.text = expr.ifEmpty { "0" }
                    }
                    "=" -> {
                        if (expr.isNotBlank()) {
                            val normalized = expr
                                .replace("×", "*")
                                .replace("÷", "/")
                                .replace("π", Math.PI.toString())
                            val res = try {
                                formatDouble(evalMath(normalized))
                            } catch (_: Throwable) {
                                "Syntax Error"
                            }
                            historyTv.text = "$expr ="
                            screenTv.text = res
                            if (res != "Syntax Error" && res != "Error") expr = res
                        }
                    }
                    "sin", "cos", "√" -> {
                        val fn = if (key == "√") "sqrt(" else "$key("
                        expr += fn
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
        scroll.addView(UiKit.buildGrid(context, 5, 40, btnViews, hGapDp = 5, vGapDp = 5))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun evalMath(str: String): Double {
        return object {
            var pos = -1
            var ch = 0
            fun nextChar() { ch = if (++pos < str.length) str[pos].code else -1 }
            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) { nextChar(); return true }
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
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else if (ch in 'a'.code..'z'.code) {
                    while (ch in 'a'.code..'z'.code) nextChar()
                    val func = str.substring(startPos, pos)
                    x = parseFactor()
                    x = when (func) {
                        "sqrt" -> sqrt(x)
                        "sin" -> sin(Math.toRadians(x))
                        "cos" -> cos(Math.toRadians(x))
                        "tan" -> tan(Math.toRadians(x))
                        else -> throw RuntimeException("Unknown fn")
                    }
                } else {
                    throw RuntimeException("Unexpected")
                }
                if (eat('^'.code)) x = x.pow(parseFactor())
                return x
            }
        }.parse()
    }

    // ==================== 2. COMMAND PROMPT / TERMINAL ====================
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
            textSize = 12f
            setTextColor(Color.rgb(134, 239, 172))
            text = "RuangDroid Command Prompt [Version 2.0.2600]\n(c) RuangDroid Desktop Environment. Ketik 'help' untuk daftar perintah.\n\n"
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
            textSize = 12f
            setTextColor(Color.rgb(96, 165, 250))
            text = "${VirtualDrive.toWindowsPath(context, cwd)}> "
        }
        val cmdInput = EditText(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12.5f
            setTextColor(Color.WHITE)
            setHintTextColor(UiKit.MUTED)
            hint = "Ketik perintah (help, dir, sysinfo, wine, uname -a)…"
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
                    Perintah Bawaan RuangDroid CMD:
                      dir / ls          Lihat isi direktori saat ini
                      cd <folder>       Pindah direktori (contoh: cd .. atau cd C:\)
                      type <file>       Tampilkan isi file teks
                      mkdir <nama>      Buat folder baru
                      del <nama>        Hapus file
                      cls / clear       Bersihkan layar terminal
                      ver               Versi sistem RuangDroid
                      sysinfo           Informasi CPU, Android SDK, RAM & Storage
                      tasklist          Daftar jendela aplikasi yang sedang aktif
                      start <app>       Buka app (explorer, browser, notepad, calc, paint, exe)
                      wine <file.exe>   Jalankan file .exe lewat subsistem EXE/Wine
                      Perintah Linux Android seperti 'uname -a', 'df -h', 'id' juga didukung!
                    """.trimIndent()
                )
                "cls", "clear" -> outputTv.text = ""
                "ver" -> appendOut("RuangDroid Semi-Desktop OS v2.0.0 (Android API ${Build.VERSION.SDK_INT} / ${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"})")
                "title" -> Unit
                "echo" -> appendOut(arg)
                "sysinfo" -> {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                    val stat = StatFs(Environment.getDataDirectory().path)
                    appendOut("Device     : ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendOut("Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendOut("CPU ABI    : ${Build.SUPPORTED_ABIS.joinToString()}")
                    appendOut(String.format(Locale.US, "Memory     : %.1f MB free / %.1f MB total", mi.availMem / 1048576.0, mi.totalMem / 1048576.0))
                    appendOut(String.format(Locale.US, "Disk Free  : %.1f GB / %.1f GB", stat.availableBytes / 1073741824.0, stat.totalBytes / 1073741824.0))
                }
                "tasklist" -> {
                    val wins = winManager.openWindows()
                    appendOut("OPEN DESKTOP WINDOWS (${wins.size}):")
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
                    appendOut("       ${files.size} item(s)")
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
                    } else {
                        appendOut("Sistem tidak menemukan folder: $arg")
                    }
                }
                "mkdir" -> if (arg.isNotEmpty()) {
                    File(cwd, arg).mkdirs()
                    appendOut("Folder dibuat: $arg")
                }
                "type", "cat" -> if (arg.isNotEmpty()) {
                    val f = File(cwd, arg)
                    if (f.exists() && f.isFile) appendOut(f.readText().take(4000))
                    else appendOut("File tidak ditemukan: $arg")
                }
                "del", "rm" -> if (arg.isNotEmpty()) {
                    val f = File(cwd, arg)
                    if (f.exists() && f.delete()) appendOut("Dihapus: $arg")
                    else appendOut("Gagal menghapus: $arg")
                }
                "start" -> when (arg.lowercase(Locale.ROOT)) {
                    "explorer" -> host.openExplorer(cwd)
                    "browser" -> host.openBrowser(null)
                    "notepad" -> host.openNotepad(null)
                    "calc", "calculator" -> host.openCalculator()
                    "paint" -> host.openPaint(null)
                    "exe", "wine" -> host.openExeCenter(null)
                    else -> appendOut("Gunakan: start explorer | browser | notepad | calc | paint | exe")
                }
                "wine" -> {
                    val target = if (arg.isNotEmpty()) File(cwd, arg).takeIf { it.exists() }
                        ?: File(VirtualDrive.installersDir(context), arg).takeIf { it.exists() }
                    else null
                    if (target != null) {
                        appendOut("Launching ${target.name} via RuangDroid EXE Subsystem...")
                        host.openExeFile(target)
                    } else {
                        host.openExeCenter(null)
                    }
                }
                else -> {
                    // Try executing as a real Android shell command safely
                    try {
                        val proc = ProcessBuilder("/system/bin/sh", "-c", line)
                            .directory(cwd)
                            .redirectErrorStream(true)
                            .start()
                        val out = BufferedReader(InputStreamReader(proc.inputStream)).readText().take(4000)
                        proc.waitFor()
                        appendOut(out.ifBlank { "(Selesai tanpa output)" })
                    } catch (t: Throwable) {
                        appendOut("Perintah tidak dikenali: $cmd (${t.message})")
                    }
                }
            }
        }

        promptRow.addView(promptPrefix)
        promptRow.addView(cmdInput, LinearLayout.LayoutParams(0, dp(context, 34), 1f))
        promptRow.addView(UiKit.chipButton(context, "Run", Color.argb(90, 34, 197, 94)) {
            val txt = cmdInput.text?.toString().orEmpty()
            cmdInput.setText("")
            runSingleCommand(txt)
        }, LinearLayout.LayoutParams(-2, dp(context, 28)))

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

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(context, 6) })
        root.addView(promptRow, LinearLayout.LayoutParams(-1, -2))

        initialCommand?.lines()?.forEach { l -> runSingleCommand(l) }
        return root
    }

    // ==================== 3. TASK MANAGER ====================
    fun buildTaskManagerView(
        context: Context,
        winState: DesktopWindowState,
        winManager: FloatingWindowManager
    ): View {
        val ramSamples = MutableList(24) { 45f }
        var alive = true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 21, 36))
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        }

        val statsHeader = UiKit.label(context, "Memuat metrik sistem…", 12f, Color.rgb(125, 211, 252), bold = true)
        root.addView(statsHeader, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })

        // Live Performance Graph View
        val graphView = object : View(context) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 2).toFloat()
                color = Color.rgb(56, 189, 248)
            }
            private val gridPaint = Paint().apply {
                color = Color.argb(35, 255, 255, 255)
                strokeWidth = 1f
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.rgb(9, 14, 24))
                val w = width.toFloat()
                val h = height.toFloat()
                for (i in 1..3) {
                    val y = h * i / 4f
                    canvas.drawLine(0f, y, w, y, gridPaint)
                }
                if (ramSamples.size >= 2) {
                    val path = Path()
                    val step = w / (ramSamples.size - 1)
                    ramSamples.forEachIndexed { idx, pct ->
                        val x = idx * step
                        val y = h - (pct.coerceIn(0f, 100f) / 100f) * (h - 12f) - 6f
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, fillPaint)
                }
            }
        }
        root.addView(graphView, LinearLayout.LayoutParams(-1, dp(context, 78)).apply { bottomMargin = dp(context, 8) })

        root.addView(
            UiKit.label(context, "Jendela Desktop Aktif (Klik End Task untuk menutup):", 11.5f, UiKit.MUTED, bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) }
        )

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
                ramSamples.removeAt(0)
                ramSamples.add(usedPct)
                graphView.invalidate()

                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val bat = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
                val usedGb = (mi.totalMem - mi.availMem) / 1073741824.0
                val totGb = mi.totalMem / 1073741824.0
                statsHeader.text = String.format(
                    Locale.US,
                    "RAM: %.0f%% (%.1f / %.1f GB)   |   Baterai: %d%%   |   Jendela: %d",
                    usedPct, usedGb, totGb, bat, winManager.openWindows().size
                )

                listContainer.removeAllViews()
                for (w in winManager.openWindows()) {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiKit.rounded(context, Color.argb(26, 255, 255, 255), 8)
                        setPadding(dp(context, 10), dp(context, 6), dp(context, 8), dp(context, 6))
                    }
                    row.addView(
                        UiKit.label(context, "${w.glyph} ${w.title}", 12f, UiKit.INK, bold = true),
                        LinearLayout.LayoutParams(0, -2, 1f)
                    )
                    row.addView(UiKit.chipButton(context, "Ke Depan") {
                        winManager.focusWindow(w.id)
                    }, LinearLayout.LayoutParams(-2, dp(context, 26)).apply { rightMargin = dp(context, 5) })

                    if (w.id != winState.id) {
                        row.addView(UiKit.chipButton(context, "End Task", Color.argb(120, 220, 38, 38)) {
                            winManager.closeWindow(w.id)
                            refreshStats()
                        }, LinearLayout.LayoutParams(-2, dp(context, 26)))
                    }
                    listContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })
                }
            } catch (_: Throwable) {
            }
            root.postDelayed({ refreshStats() }, 1800)
        }

        winState.onCloseCleanup = { alive = false }
        refreshStats()
        return root
    }

    // ==================== 4. CONTROL PANEL ====================
    fun buildControlPanelView(
        context: Context,
        prefs: DesktopPrefs,
        host: DesktopHostCallbacks
    ): View {
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(14, 21, 36))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 14))
        }

        root.addView(UiKit.label(context, "🎨 Personalisasi Tema & Wallpaper Desktop", 13.5f, UiKit.INK, bold = true))

        val themeButtons = prefs.themes.mapIndexed { index, palette ->
            UiKit.chipButton(
                context,
                palette.name,
                bgColor = palette.bgColors[1],
                strokeColor = if (prefs.themeIndex == index && !prefs.useCustomWallpaper) palette.accent else Color.argb(60, 255, 255, 255)
            ) {
                prefs.themeIndex = index
                prefs.clearCustomWallpaper()
                host.refreshDesktopUI()
            }
        }
        root.addView(UiKit.buildGrid(context, 3, 38, themeButtons), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(context, 6)
            bottomMargin = dp(context, 8)
        })

        val wpRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        wpRow.addView(UiKit.chipButton(context, "🖼️ Pilih Foto Wallpaper dari Galeri", Color.argb(85, 37, 99, 235)) {
            host.pickCustomWallpaper()
        }, LinearLayout.LayoutParams(0, dp(context, 34), 1f).apply { rightMargin = dp(context, 6) })

        wpRow.addView(UiKit.chipButton(context, "Reset Tema") {
            prefs.clearCustomWallpaper()
            host.refreshDesktopUI()
        }, LinearLayout.LayoutParams(-2, dp(context, 34)))
        root.addView(wpRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 14) })

        root.addView(UiKit.label(context, "🖥️ Pengaturan Layar, Orientasi & Multi-Window", 13.5f, UiKit.INK, bold = true))

        fun settingToggleRow(title: String, subtitle: String, stateProvider: () -> Boolean, onToggle: () -> Unit): View {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.rounded(context, Color.argb(26, 255, 255, 255), 10)
                setPadding(dp(context, 12), dp(context, 8), dp(context, 10), dp(context, 8))
            }
            val textCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            textCol.addView(UiKit.label(context, title, 12.5f, UiKit.INK, bold = true))
            textCol.addView(UiKit.label(context, subtitle, 10.5f, UiKit.MUTED, maxLines = 2))
            box.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

            val btn = UiKit.chipButton(context, if (stateProvider()) "ON" else "OFF") {}
            fun syncBtn() {
                val on = stateProvider()
                btn.text = if (on) "AKTIF" else "MATI"
                btn.background = UiKit.strokeRounded(
                    context,
                    if (on) Color.argb(110, 34, 197, 94) else Color.argb(40, 255, 255, 255),
                    Color.argb(90, 255, 255, 255),
                    9
                )
            }
            syncBtn()
            btn.setOnClickListener {
                onToggle()
                syncBtn()
            }
            box.addView(btn, LinearLayout.LayoutParams(dp(context, 74), dp(context, 32)))
            return box
        }

        root.addView(settingToggleRow(
            "Mode PC Landscape Otomatis",
            "Kunci tampilan mendatar ala PC / Samsung DeX",
            { prefs.landscapeLock },
            {
                prefs.landscapeLock = !prefs.landscapeLock
                host.refreshDesktopUI()
            }
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 6) })

        root.addView(settingToggleRow(
            "Fullscreen Immersive Mode",
            "Sembunyikan status bar & navigasi Android agar layar penuh",
            { prefs.immersiveMode },
            {
                prefs.immersiveMode = !prefs.immersiveMode
                host.refreshDesktopUI()
            }
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 6) })

        root.addView(settingToggleRow(
            "Freeform Window Aplikasi Android",
            "Mencoba membuka aplikasi Android terpasang dalam mode jendela mengambang (jika didukung ROM)",
            { prefs.freeformExternalApps },
            { prefs.freeformExternalApps = !prefs.freeformExternalApps }
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 6) })

        val scaleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 10), 0, 0)
        }
        scaleRow.addView(UiKit.label(context, "Ukuran Ikon Desktop:", 12f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        listOf("Kecil" to 0, "Normal" to 1, "Besar" to 2).forEach { (lbl, sc) ->
            scaleRow.addView(UiKit.chipButton(context, lbl) {
                prefs.iconScale = sc
                host.refreshDesktopUI()
            }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { leftMargin = dp(context, 6) })
        }
        root.addView(scaleRow)

        scroll.addView(root)
        return scroll
    }
}
