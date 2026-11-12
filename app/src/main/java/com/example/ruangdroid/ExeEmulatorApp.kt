package com.example.ruangdroid

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

data class PeAnalysis(
    val isValidMz: Boolean,
    val isValidPe: Boolean,
    val arch: String,
    val peFormat: String,
    val subsystem: String,
    val sectionCount: Int,
    val importedDlls: List<String>,
    val portableAppKey: String?,
    val portableTitle: String?
)

object ExeEmulatorApp {
    private fun dp(ctx: Context, v: Int) = UiKit.dp(ctx, v)

    private val knownWinePackages = listOf(
        "Winlator (x86_64 Wine + Box64)" to listOf("com.winlator", "com.winlator.cmod", "com.winlator.glibc"),
        "WineHQ Official Android" to listOf("org.winehq.wine"),
        "Horizon Emu (Box64 / Wine)" to listOf("com.horizonemu.app"),
        "Termux (Mobox / Box64 Host)" to listOf("com.termux")
    )

    fun analyzePeFile(file: File): PeAnalysis {
        try {
            if (!file.exists() || file.length() < 64) {
                return PeAnalysis(false, false, "Unknown", "Raw Binary", "Unknown", 0, emptyList(), null, null)
            }
            val bytes = ByteArray(minOf(file.length(), 65536L).toInt())
            RandomAccessFile(file, "r").use { raf -> raf.readFully(bytes) }

            val isMz = bytes[0] == 'M'.code.toByte() && bytes[1] == 'Z'.code.toByte()
            if (!isMz) {
                return PeAnalysis(false, false, "Non-PE Binary", "Unknown", "Unknown", 0, emptyList(), null, null)
            }

            fun u16(off: Int): Int =
                if (off + 1 < bytes.size) (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8) else 0
            fun u32(off: Int): Int =
                if (off + 3 < bytes.size) u16(off) or (u16(off + 2) shl 16) else 0

            val peOffset = u32(0x3C)
            val isPe = peOffset in 0x40..(bytes.size - 28) &&
                bytes[peOffset] == 'P'.code.toByte() &&
                bytes[peOffset + 1] == 'E'.code.toByte() &&
                bytes[peOffset + 2] == 0.toByte() &&
                bytes[peOffset + 3] == 0.toByte()

            var arch = "DOS 16-bit MZ"
            var peFormat = "MZ Executable"
            var subsystem = "DOS / Console"
            var sections = 0

            if (isPe) {
                val machine = u16(peOffset + 4)
                arch = when (machine) {
                    0x014C -> "PE32 x86 (i386)"
                    0x8664 -> "PE32+ x64 (AMD64)"
                    0xAA64 -> "PE32+ ARM64"
                    0x01C4 -> "PE32 ARMv7"
                    else -> String.format(Locale.US, "PE Machine 0x%04X", machine)
                }
                sections = u16(peOffset + 6)
                val optMagic = u16(peOffset + 24)
                peFormat = when (optMagic) {
                    0x010B -> "PE32 (32-bit)"
                    0x020B -> "PE32+ (64-bit)"
                    else -> "PE Image"
                }
                val subCode = u16(peOffset + 24 + 68)
                subsystem = when (subCode) {
                    2 -> "Windows GUI (Win32)"
                    3 -> "Windows Console (CUI)"
                    else -> "Subsystem #$subCode"
                }
            }

            val ascii = String(bytes, Charsets.ISO_8859_1)
            val dllRegex = Regex("[A-Za-z0-9_\\-]{2,24}\\.(?:dll|DLL)")
            val dlls = dllRegex.findAll(ascii).map { it.value.uppercase(Locale.ROOT) }.distinct().take(12).toList()

            var pKey: String? = null
            var pTitle: String? = null
            val markerIdx = ascii.indexOf("RUANGDROID_EXE:")
            if (markerIdx >= 0) {
                val tail = ascii.substring(markerIdx + "RUANGDROID_EXE:".length).lineSequence().firstOrNull().orEmpty()
                val parts = tail.split("|", limit = 2)
                pKey = parts.getOrNull(0)?.trim()
                pTitle = parts.getOrNull(1)?.trim()
            }

            return PeAnalysis(isMz, isPe, arch, peFormat, subsystem, sections, dlls, pKey, pTitle)
        } catch (_: Throwable) {
            return PeAnalysis(false, false, "Read Error", "Unknown", "Unknown", 0, emptyList(), null, null)
        }
    }

    fun installExeToProgramFiles(context: Context, prefs: DesktopPrefs, sourceFile: File): InstalledExeEntry {
        val analysis = analyzePeFile(sourceFile)
        val cleanBase = sourceFile.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_\\- ]"), "").trim().ifEmpty { "App" }
        val targetFolder = File(VirtualDrive.programFilesDir(context), cleanBase).apply { mkdirs() }
        val targetExe = File(targetFolder, sourceFile.name.ifEmpty { "$cleanBase.exe" })
        if (sourceFile.absolutePath != targetExe.absolutePath) {
            sourceFile.copyTo(targetExe, overwrite = true)
        }

        val displayTitle = analysis.portableTitle ?: sourceFile.name
        val entry = InstalledExeEntry(
            id = UUID.randomUUID().toString(),
            title = displayTitle,
            exePath = targetExe.absolutePath,
            arch = analysis.arch
        )
        val current = prefs.getInstalledExes()
        current.removeAll { it.exePath == targetExe.absolutePath }
        current.add(entry)
        prefs.saveInstalledExes(current)
        return entry
    }

    fun launchExeInWindow(
        context: Context,
        prefs: DesktopPrefs,
        host: DesktopHostCallbacks,
        winManager: FloatingWindowManager,
        exeFile: File
    ) {
        val info = analyzePeFile(exeFile)
        when (info.portableAppKey) {
            "minesweeper" -> {
                winManager.openWindow(
                    appKey = "exe_minesweeper",
                    title = "${exeFile.name} (Win32 Subsystem)",
                    glyph = "💣",
                    singleInstance = false,
                    preferredWidthDp = 370,
                    preferredHeightDp = 410
                ) { buildMinesweeperView(context) }
            }
            "arkanoid" -> {
                winManager.openWindow(
                    appKey = "exe_arkanoid",
                    title = "${exeFile.name} (DirectX 2D Layer)",
                    glyph = "🧱",
                    singleInstance = false,
                    preferredWidthDp = 460,
                    preferredHeightDp = 360
                ) { state -> buildArkanoidView(context, state) }
            }
            "calculator" -> host.openCalculator()
            else -> {
                winManager.openWindow(
                    appKey = "exe_runner_${exeFile.name}",
                    title = "Wine / PE Loader — ${exeFile.name}",
                    glyph = "🍷",
                    singleInstance = false,
                    preferredWidthDp = 500,
                    preferredHeightDp = 360
                ) {
                    buildPeInspectorView(context, prefs, host, exeFile, info)
                }
            }
        }
    }

    private fun detectInstalledWinePackage(context: Context): Pair<String, String>? {
        val pm = context.packageManager
        for ((title, pkgs) in knownWinePackages) {
            for (pkg in pkgs) {
                val launch = pm.getLaunchIntentForPackage(pkg)
                if (launch != null) return title to pkg
            }
        }
        return null
    }

    fun buildPeInspectorView(
        context: Context,
        prefs: DesktopPrefs,
        host: DesktopHostCallbacks,
        exeFile: File,
        info: PeAnalysis = analyzePeFile(exeFile)
    ): View {
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(13, 19, 32))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 14))
        }

        root.addView(UiKit.label(context, "🍷 RuangDroid PE32 / Wine Execution Loader", 14f, UiKit.INK, bold = true))
        root.addView(
            UiKit.label(context, "Path: ${VirtualDrive.toWindowsPath(context, exeFile)}", 11.5f, Color.rgb(147, 197, 253)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 4); bottomMargin = dp(context, 10) }
        )

        val metaCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(context, Color.rgb(9, 14, 24), 10)
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        }
        val detailText = buildString {
            appendLine("Header MZ Valid  : ${if (info.isValidMz) "YA (DOS MZ)" else "TIDAK"}")
            appendLine("Header PE Valid  : ${if (info.isValidPe) "YA (${info.peFormat})" else "TIDAK"}")
            appendLine("Arsitektur CPU   : ${info.arch}")
            appendLine("Windows Subsystem: ${info.subsystem}")
            appendLine("Jumlah Section   : ${info.sectionCount}")
            appendLine("Ukuran File      : ${exeFile.length() / 1024} KB")
            append("Imported DLLs    : ${if (info.importedDlls.isEmpty()) "(Tidak terdeteksi / Packed)" else info.importedDlls.joinToString(", ")}")
        }
        metaCard.addView(TextView(context).apply {
            text = detailText
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(167, 243, 208))
        })
        root.addView(metaCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) })

        val detectedEngine = detectInstalledWinePackage(context)
        val btnRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        btnRow.addView(UiKit.chipButton(context, "⬇ Install ke C:\\Program Files", Color.argb(95, 37, 99, 235)) {
            val installed = installExeToProgramFiles(context, prefs, exeFile)
            host.refreshDesktopUI()
            Toast.makeText(context, "Terpasang di Desktop: ${installed.title}", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, dp(context, 36), 1f).apply { rightMargin = dp(context, 6) })

        if (detectedEngine != null) {
            val (engineName, pkg) = detectedEngine
            btnRow.addView(UiKit.chipButton(context, "🚀 Jalankan di $engineName", Color.argb(110, 34, 197, 94)) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                }
            }, LinearLayout.LayoutParams(0, dp(context, 36), 1f))
        } else {
            btnRow.addView(UiKit.chipButton(context, "🌐 Pasang Plugin Winlator (x86)", Color.argb(95, 168, 85, 247)) {
                host.openBrowser("https://github.com/brunodev85/winlator/releases")
            }, LinearLayout.LayoutParams(0, dp(context, 36), 1f))
        }
        root.addView(btnRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) })

        root.addView(
            UiKit.label(
                context,
                if (detectedEngine != null)
                    "Plugin kontainer x86 (${detectedEngine.first}) terdeteksi! Letakkan installer di folder Download agar kontainer Wine dapat membacanya langsung."
                else
                    "Catatan: Binary Windows x86/x64 penuh membutuhkan kontainer translasi CPU Wine + Box64 (~700 MB) seperti Winlator. Klik tombol ungu di atas untuk mengunduh plugin APK resminya.",
                11f,
                UiKit.MUTED,
                maxLines = 5
            )
        )

        scroll.addView(root)
        return scroll
    }

    // ==================== EXE PLUGIN & INSTALLER HUB ====================
    fun buildExeCenterView(
        context: Context,
        prefs: DesktopPrefs,
        host: DesktopHostCallbacks,
        winManager: FloatingWindowManager
    ): View {
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(14, 20, 34))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 14))
        }

        val installedContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val installersContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun refreshLists() {
            installedContainer.removeAllViews()
            val installed = prefs.getInstalledExes()
            if (installed.isEmpty()) {
                installedContainer.addView(UiKit.label(context, "Belum ada program .exe yang di-install.", 11.5f, UiKit.MUTED))
            } else {
                for (entry in installed) {
                    val f = File(entry.exePath)
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = UiKit.rounded(context, Color.argb(28, 255, 255, 255), 10)
                        setPadding(dp(context, 10), dp(context, 7), dp(context, 8), dp(context, 7))
                    }
                    val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                    col.addView(UiKit.label(context, "🍷 ${entry.title}", 12.5f, UiKit.INK, bold = true))
                    col.addView(UiKit.label(context, "${entry.arch}  ·  ${VirtualDrive.toWindowsPath(context, f)}", 10.5f, UiKit.MUTED))
                    row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

                    row.addView(UiKit.chipButton(context, "▶ Buka", Color.argb(100, 34, 197, 94)) {
                        if (f.exists()) launchExeInWindow(context, prefs, host, winManager, f)
                    }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

                    row.addView(UiKit.chipButton(context, "🗑️") {
                        val list = prefs.getInstalledExes().filterNot { it.id == entry.id }
                        prefs.saveInstalledExes(list)
                        host.refreshDesktopUI()
                        refreshLists()
                    }, LinearLayout.LayoutParams(-2, dp(context, 28)))

                    installedContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 5) })
                }
            }

            installersContainer.removeAllViews()
            val pkgFiles = VirtualDrive.installersDir(context).listFiles()
                ?.filter { it.extension.lowercase(Locale.ROOT) in listOf("exe", "msi") }
                .orEmpty()
            for (pkg in pkgFiles) {
                val pe = analyzePeFile(pkg)
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = UiKit.rounded(context, Color.argb(22, 255, 255, 255), 10)
                    setPadding(dp(context, 10), dp(context, 7), dp(context, 8), dp(context, 7))
                }
                val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                col.addView(UiKit.label(context, "📦 ${pkg.name}", 12f, UiKit.INK, bold = true))
                col.addView(UiKit.label(context, "${pe.arch} · ${pe.portableTitle ?: pe.subsystem}", 10.5f, UiKit.MUTED))
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

                row.addView(UiKit.chipButton(context, "⬇ Install", Color.argb(90, 37, 99, 235)) {
                    val item = installExeToProgramFiles(context, prefs, pkg)
                    host.refreshDesktopUI()
                    refreshLists()
                    Toast.makeText(context, "${item.title} berhasil di-install ke Desktop & C:\\Program Files!", Toast.LENGTH_SHORT).show()
                }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

                row.addView(UiKit.chipButton(context, "▶ Test") {
                    launchExeInWindow(context, prefs, host, winManager, pkg)
                }, LinearLayout.LayoutParams(-2, dp(context, 28)))

                installersContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 5) })
            }
        }

        // Top Import Button
        val topCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.strokeRounded(context, Color.argb(55, 37, 99, 235), Color.argb(100, 147, 197, 253), 12)
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        }
        val topInfo = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        topInfo.addView(UiKit.label(context, "🍷 Import & Install File .EXE dari HP", 13f, UiKit.INK, bold = true))
        topInfo.addView(UiKit.label(context, "Pilih installer .exe dari penyimpanan Android untuk dipasang ke C:\\Program Files & Desktop", 11f, UiKit.MUTED, maxLines = 2))
        topCard.addView(topInfo, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(context, 8) })
        topCard.addView(UiKit.chipButton(context, "📂 Pilih .EXE", Color.argb(180, 37, 99, 235)) {
            host.pickExternalExeFile()
        }, LinearLayout.LayoutParams(-2, dp(context, 34)))
        root.addView(topCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) })

        // Wine / Box64 External Engine Plugin Status
        val engine = detectInstalledWinePackage(context)
        val pluginBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(context, Color.rgb(10, 15, 26), 10)
            setPadding(dp(context, 12), dp(context, 8), dp(context, 10), dp(context, 8))
        }
        pluginBar.addView(
            UiKit.label(
                context,
                if (engine != null) "✅ Engine Eksternal Aktif: ${engine.first}"
                else "🔌 Engine x86 Eksternal (Winlator/Wine): Belum terpasang",
                11.5f,
                if (engine != null) Color.rgb(134, 239, 172) else Color.rgb(253, 224, 71),
                bold = true
            ),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        pluginBar.addView(
            UiKit.chipButton(
                context,
                if (engine != null) "🚀 Buka Engine" else "🌐 Get Winlator"
            ) {
                if (engine != null) {
                    context.packageManager.getLaunchIntentForPackage(engine.second)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(it)
                    }
                } else {
                    host.openBrowser("https://github.com/brunodev85/winlator/releases")
                }
            },
            LinearLayout.LayoutParams(-2, dp(context, 28))
        )
        root.addView(pluginBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) })

        root.addView(UiKit.label(context, "🗂️ Program .EXE Terpasang (C:\\Program Files):", 12.5f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })
        root.addView(installedContainer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) })

        root.addView(UiKit.label(context, "📦 Paket Installer Bawaan Siap Pasang (C:\\Installers):", 12.5f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })
        root.addView(installersContainer, LinearLayout.LayoutParams(-1, -2))

        refreshLists()
        scroll.addView(root)
        return scroll
    }

    // ==================== BUILT-IN .EXE #1: MINESWEEPER XP ====================
    private fun buildMinesweeperView(context: Context): View {
        val rows = 8
        val cols = 8
        val totalMines = 10
        val mines = Array(rows) { BooleanArray(cols) }
        val revealed = Array(rows) { BooleanArray(cols) }
        val flagged = Array(rows) { BooleanArray(cols) }
        var flagMode = false
        var gameOver = false

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 23, 38))
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 10))
        }

        val statusTv = UiKit.label(context, "🙂 Siap bermain! (10 Ranjau)", 12.5f, UiKit.INK, bold = true)
        val buttons = Array(rows) { Array<TextView?>(cols) { null } }

        fun neighborMines(r: Int, c: Int): Int {
            var cnt = 0
            for (dr in -1..1) for (dc in -1..1) {
                val nr = r + dr; val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols && mines[nr][nc]) cnt++
            }
            return cnt
        }

        fun syncBoard() {
            for (r in 0 until rows) for (c in 0 until cols) {
                val tv = buttons[r][c] ?: continue
                when {
                    revealed[r][c] && mines[r][c] -> {
                        tv.text = "💣"
                        tv.background = UiKit.rounded(context, Color.rgb(185, 28, 28), 6)
                    }
                    revealed[r][c] -> {
                        val n = neighborMines(r, c)
                        tv.text = if (n == 0) "" else n.toString()
                        tv.setTextColor(
                            when (n) {
                                1 -> Color.rgb(96, 165, 250)
                                2 -> Color.rgb(74, 222, 128)
                                3 -> Color.rgb(248, 113, 113)
                                else -> Color.rgb(250, 204, 21)
                            }
                        )
                        tv.background = UiKit.rounded(context, Color.rgb(10, 15, 26), 6)
                    }
                    flagged[r][c] -> {
                        tv.text = "🚩"
                        tv.background = UiKit.rounded(context, Color.rgb(30, 58, 95), 6)
                    }
                    else -> {
                        tv.text = ""
                        tv.background = UiKit.strokeRounded(context, Color.rgb(32, 48, 76), Color.argb(65, 255, 255, 255), 6)
                    }
                }
            }
        }

        fun resetGame() {
            gameOver = false
            for (r in 0 until rows) for (c in 0 until cols) {
                mines[r][c] = false
                revealed[r][c] = false
                flagged[r][c] = false
            }
            var placed = 0
            while (placed < totalMines) {
                val r = Random.nextInt(rows)
                val c = Random.nextInt(cols)
                if (!mines[r][c]) {
                    mines[r][c] = true
                    placed++
                }
            }
            statusTv.text = "🙂 Minesweeper XP · 10💣"
            syncBoard()
        }

        fun floodReveal(r: Int, c: Int) {
            if (r !in 0 until rows || c !in 0 until cols || revealed[r][c] || flagged[r][c]) return
            revealed[r][c] = true
            if (neighborMines(r, c) == 0 && !mines[r][c]) {
                for (dr in -1..1) for (dc in -1..1) if (dr != 0 || dc != 0) floodReveal(r + dr, c + dc)
            }
        }

        fun checkWin() {
            var unrevealedSafe = 0
            for (r in 0 until rows) for (c in 0 until cols) {
                if (!mines[r][c] && !revealed[r][c]) unrevealedSafe++
            }
            if (unrevealedSafe == 0) {
                gameOver = true
                statusTv.text = "😎 MENANG! Semua area aman terbuka!"
            }
        }

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(statusTv, LinearLayout.LayoutParams(0, -2, 1f))

        val flagBtn = UiKit.chipButton(context, "⛏️ Buka") {}
        flagBtn.setOnClickListener {
            flagMode = !flagMode
            flagBtn.text = if (flagMode) "🚩 Bendera" else "⛏️ Buka"
        }
        topBar.addView(flagBtn, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })
        topBar.addView(UiKit.chipButton(context, "🔄 Reset") { resetGame() }, LinearLayout.LayoutParams(-2, dp(context, 30)))
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) })

        val cells = mutableListOf<View>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val tv = TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        if (gameOver) return@setOnClickListener
                        if (flagMode) {
                            if (!revealed[r][c]) flagged[r][c] = !flagged[r][c]
                        } else if (!flagged[r][c]) {
                            if (mines[r][c]) {
                                for (rr in 0 until rows) for (cc in 0 until cols) if (mines[rr][cc]) revealed[rr][cc] = true
                                gameOver = true
                                statusTv.text = "💥 BOOM! Kena ranjau!"
                            } else {
                                floodReveal(r, c)
                                checkWin()
                            }
                        }
                        syncBoard()
                    }
                }
                buttons[r][c] = tv
                cells.add(tv)
            }
        }

        val scroll = ScrollView(context).apply { isFillViewport = true }
        scroll.addView(UiKit.buildGrid(context, cols, 34, cells, hGapDp = 3, vGapDp = 3))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        resetGame()
        return root
    }

    // ==================== BUILT-IN .EXE #2: ARKANOID DX ====================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildArkanoidView(context: Context, winState: DesktopWindowState): View {
        var running = true
        val brickRows = 4
        val brickCols = 7
        val bricks = Array(brickRows) { BooleanArray(brickCols) { true } }
        var score = 0
        var lives = 3

        val gameView = object : View(context) {
            private var paddleX = 0.5f // normalized 0..1
            private var ballX = 0.5f
            private var ballY = 0.72f
            private var vx = 0.011f
            private var vy = -0.015f
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val rowColors = intArrayOf(
                Color.rgb(239, 68, 68),
                Color.rgb(249, 115, 22),
                Color.rgb(34, 197, 94),
                Color.rgb(56, 189, 248)
            )

            private val tick = object : Runnable {
                override fun run() {
                    if (!running) return
                    if (lives > 0 && bricks.any { row -> row.any { it } }) {
                        ballX += vx
                        ballY += vy
                        if (ballX < 0.03f || ballX > 0.97f) vx = -vx
                        if (ballY < 0.08f) vy = abs(vy)

                        // Paddle collision
                        if (ballY in 0.87f..0.92f && abs(ballX - paddleX) < 0.14f) {
                            vy = -abs(vy)
                            vx = ((ballX - paddleX) * 0.14f).coerceIn(-0.022f, 0.022f)
                        }

                        // Brick collisions
                        for (r in 0 until brickRows) {
                            val top = 0.14f + r * 0.08f
                            val bot = top + 0.06f
                            if (ballY in top..bot) {
                                val c = (ballX * brickCols).toInt().coerceIn(0, brickCols - 1)
                                if (bricks[r][c]) {
                                    bricks[r][c] = false
                                    vy = -vy
                                    score += 10
                                }
                            }
                        }

                        if (ballY > 0.99f) {
                            lives--
                            ballX = paddleX
                            ballY = 0.75f
                            vy = -0.015f
                        }
                        invalidate()
                    }
                    postDelayed(this, 16L)
                }
            }

            init {
                post(tick)
            }

            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val h = height.toFloat()
                canvas.drawColor(Color.rgb(9, 14, 24))

                // HUD
                paint.color = Color.WHITE
                paint.textSize = dp(context, 12).toFloat()
                paint.typeface = Typeface.DEFAULT_BOLD
                val msg = when {
                    lives <= 0 -> "GAME OVER — Tap layar untuk main lagi!  |  Skor: $score"
                    bricks.none { row -> row.any { it } } -> "STAGE CLEAR! 🏆 Skor: $score — Tap untuk ulang"
                    else -> "Skor: $score   |   Nyawa: ${"❤️".repeat(lives)}   (Geser jari untuk gerak paddle)"
                }
                canvas.drawText(msg, dp(context, 10).toFloat(), dp(context, 18).toFloat(), paint)

                // Bricks
                val bw = w / brickCols
                for (r in 0 until brickRows) {
                    paint.color = rowColors[r % rowColors.size]
                    val top = h * (0.14f + r * 0.08f)
                    val bot = top + h * 0.055f
                    for (c in 0 until brickCols) {
                        if (bricks[r][c]) {
                            canvas.drawRoundRect(
                                RectF(c * bw + 4f, top, (c + 1) * bw - 4f, bot),
                                8f, 8f, paint
                            )
                        }
                    }
                }

                // Paddle
                paint.color = Color.rgb(96, 165, 250)
                val pw = w * 0.24f
                val px = (paddleX * w).coerceIn(pw / 2f, w - pw / 2f)
                canvas.drawRoundRect(RectF(px - pw / 2f, h * 0.89f, px + pw / 2f, h * 0.93f), 12f, 12f, paint)

                // Ball
                paint.color = Color.rgb(250, 204, 21)
                canvas.drawCircle(ballX * w, ballY * h, dp(context, 6).toFloat(), paint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (width > 0) {
                    paddleX = (event.x / width).coerceIn(0.12f, 0.88f)
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                    (lives <= 0 || bricks.none { row -> row.any { it } })
                ) {
                    lives = 3
                    score = 0
                    for (r in 0 until brickRows) for (c in 0 until brickCols) bricks[r][c] = true
                    ballX = 0.5f; ballY = 0.72f
                    invalidate()
                }
                return true
            }
        }

        winState.onCloseCleanup = { running = false }
        return gameView
    }
}
