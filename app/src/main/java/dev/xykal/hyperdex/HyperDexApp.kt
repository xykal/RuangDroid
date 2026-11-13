package dev.xykal.hyperdex

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class HyperDexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val trace = sw.toString().take(8000)
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra("trace", trace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, throwable)
            }
        }
        try {
            VirtualDrive.ensureInitialized(this)
        } catch (_: Throwable) {
        }
    }
}

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent?.getStringExtra("trace") ?: "Unknown error"
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 18, 32))
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }

        val statusHeader = TextView(this).apply {
            text = "HyperDeX Recovery Console"
            textSize = 18f
            setTextColor(Color.rgb(245, 248, 255))
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(statusHeader)

        root.addView(TextView(this).apply {
            text = "Terjadi kendala tak terduga. Kamu bisa salin log diagnostik atau reset preferensi untuk langsung kembali ke Desktop."
            textSize = 12.5f
            setTextColor(Color.rgb(180, 195, 220))
            setPadding(0, dp(6), 0, dp(12))
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 12, 22))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroll.addView(TextView(this).apply {
            text = trace
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(252, 165, 165))
        })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
        }

        fun btn(label: String, bg: Int, action: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(bg)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { action() }
        }

        row.addView(btn("Copy Log", Color.rgb(48, 72, 115)) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("HyperDeX Crash Log", trace))
            statusHeader.text = "HyperDeX Recovery Console (Log Disalin)"
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })

        row.addView(btn("Reset & Buka Desktop", Color.rgb(37, 99, 235)) {
            getSharedPreferences("hyperdex_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        })

        root.addView(row)
        setContentView(root)
    }
}

object VirtualDrive {
    fun root(context: Context): File = File(context.filesDir, "drive_c").apply { mkdirs() }
    fun desktopDir(context: Context): File = File(root(context), "Users/Administrator/Desktop").apply { mkdirs() }
    fun docsDir(context: Context): File = File(root(context), "Users/Administrator/Documents").apply { mkdirs() }
    fun downloadsDir(context: Context): File = File(root(context), "Users/Administrator/Downloads").apply { mkdirs() }
    fun picturesDir(context: Context): File = File(root(context), "Users/Administrator/Pictures").apply { mkdirs() }
    fun programFilesDir(context: Context): File = File(root(context), "Program Files").apply { mkdirs() }
    fun installersDir(context: Context): File = File(root(context), "Installers").apply { mkdirs() }

    fun toWindowsPath(context: Context, file: File): String {
        val base = root(context).absolutePath
        val full = file.absolutePath
        return if (full.startsWith(base)) {
            val rel = full.removePrefix(base).trimStart('/').replace('/', '\\')
            if (rel.isEmpty()) "C:\\" else "C:\\$rel"
        } else {
            "Z:" + full.replace('/', '\\')
        }
    }

    fun ensureInitialized(context: Context) {
        desktopDir(context)
        val docs = docsDir(context)
        downloadsDir(context)
        picturesDir(context)
        val pf = programFilesDir(context)
        val inst = installersDir(context)

        val welcome = File(docs, "Selamat_Datang_HyperDeX.txt")
        if (!welcome.exists()) {
            welcome.writeText(
                """
                =====================================================
                SELAMAT DATANG DI HYPERDEX DROID OS v4.0
                =====================================================
                Arsitektur Polyglot Native:
                - Kotlin Compositor : Custom Full-Window UI & Aero Snap
                - Rust Core (.so)   : SHA-256 / HMAC Anti-Clone Shield & Math Engine
                - C11 Core (.so)    : Kernel /proc Telemetry, CRC32 & Process Guard
                - C++20 Core (.so)  : Acrylic StackBlur & PE32/PE64 Binary Dissector
                - Shizuku & Termux  : Global Freeform Window, DPI Tweaker & Shell
                """.trimIndent()
            )
        }

        val batFile = File(inst, "HyperDeX_Diagnostics.bat")
        if (!batFile.exists()) {
            batFile.writeText(
                """
                @echo off
                title HyperDeX Polyglot Diagnostic Script
                ver
                native
                sysinfo
                bench
                echo Semua modul Kotlin + Rust + C11 + C++20 berjalan optimal!
                """.trimIndent()
            )
        }

        seedPortableExe(File(inst, "Minesweeper_XP.exe"), "minesweeper", "Minesweeper Classic PC", x64 = false)
        seedPortableExe(File(inst, "Arkanoid_DX.exe"), "arkanoid", "Arkanoid Breakout DX", x64 = true)
        seedPortableExe(File(inst, "SpaceShooter_DX.exe"), "spaceshooter", "Neon Space Shooter DX", x64 = true)
        seedPortableExe(File(inst, "WinCalc_Sci.exe"), "calculator", "Windows Scientific Calc", x64 = true)

        val setupLinux = File(inst, "Setup_Termux_Box64_X11.sh")
        if (!setupLinux.exists()) {
            setupLinux.writeText(
                """
                #!/data/data/com.termux/files/usr/bin/bash
                pkg update -y && pkg install -y x11-repo tur-repo proot-distro pulseaudio termux-x11-nightly
                echo "[HyperDeX] Termux X11 + Proot-Distro siap! Jalankan: termux-x11 :0 &"
                """.trimIndent()
            )
        }

        val preInstalled = File(pf, "Minesweeper/Minesweeper_XP.exe")
        if (!preInstalled.exists()) {
            preInstalled.parentFile?.mkdirs()
            seedPortableExe(preInstalled, "minesweeper", "Minesweeper Classic PC", x64 = false)
        }
    }

    /**
     * Generates a valid MZ + PE32/PE32+ header + authentic COFF Section Table (.text, .rdata)
     * so C++20 libhyper_gfx_cc.so dissects real PE sections, RVAs, and entropy.
     */
    fun seedPortableExe(target: File, appKey: String, title: String, x64: Boolean) {
        if (target.exists()) return
        val out = ByteArrayOutputStream()
        val buf = ByteArray(768)

        fun writeU16(off: Int, v: Int) {
            buf[off] = (v and 0xFF).toByte()
            buf[off + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        fun writeU32(off: Int, v: Int) {
            buf[off] = (v and 0xFF).toByte()
            buf[off + 1] = ((v ushr 8) and 0xFF).toByte()
            buf[off + 2] = ((v ushr 16) and 0xFF).toByte()
            buf[off + 3] = ((v ushr 24) and 0xFF).toByte()
        }

        // DOS Header 'MZ' + e_lfanew = 0x80
        buf[0] = 'M'.code.toByte()
        buf[1] = 'Z'.code.toByte()
        writeU32(0x3C, 0x80)

        // NT Signature 'PE\0\0'
        buf[0x80] = 'P'.code.toByte()
        buf[0x81] = 'E'.code.toByte()
        writeU16(0x84, if (x64) 0x8664 else 0x014C)
        writeU16(0x86, 2) // 2 COFF sections (.text, .rdata)
        val optSize = if (x64) 0xF0 else 0xE0
        writeU16(0x94, optSize)
        writeU16(0x96, 0x0022) // EXECUTABLE_IMAGE | LARGE_ADDRESS_AWARE

        // Optional Header at 0x98
        val optOff = 0x98
        writeU16(optOff, if (x64) 0x020B else 0x010B)
        writeU32(optOff + 16, 0x1000) // AddressOfEntryPoint RVA
        writeU16(optOff + 68, 2)      // Subsystem = WINDOWS_GUI

        // COFF Section Table at optOff + optSize
        val sec0 = optOff + optSize
        ".text".toByteArray().copyInto(buf, sec0)
        writeU32(sec0 + 8, 0x0800)  // VirtualSize
        writeU32(sec0 + 12, 0x1000) // VirtualAddress
        writeU32(sec0 + 16, 0x0200) // SizeOfRawData

        val sec1 = sec0 + 40
        ".rdata".toByteArray().copyInto(buf, sec1)
        writeU32(sec1 + 8, 0x0400)
        writeU32(sec1 + 12, 0x2000)
        writeU32(sec1 + 16, 0x0200)

        out.write(buf)
        val meta = "\nKERNEL32.dll\u0000USER32.dll\u0000GDI32.dll\u0000HYPERDEX_EXE:$appKey|$title\n"
        out.write(meta.toByteArray(Charsets.UTF_8))
        target.writeBytes(out.toByteArray())
    }
}
