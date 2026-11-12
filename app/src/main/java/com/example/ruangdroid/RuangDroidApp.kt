package com.example.ruangdroid

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
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class RuangDroidApp : Application() {
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
            setBackgroundColor(Color.rgb(16, 24, 40))
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "⚠️ RuangDroid Recovery Mode"
            textSize = 19f
            setTextColor(Color.rgb(245, 248, 255))
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Terjadi error tak terduga. Kamu bisa salin log atau reset pengaturan untuk langsung kembali ke Desktop."
            textSize = 13f
            setTextColor(Color.rgb(180, 195, 220))
            setPadding(0, dp(6), 0, dp(12))
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 15, 26))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroll.addView(TextView(this).apply {
            text = trace
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(255, 165, 165))
        })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
        }

        fun btn(label: String, bg: Int, action: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(bg)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { action() }
        }

        row.addView(btn("📋 Copy Log", Color.rgb(48, 72, 115)) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("RuangDroid Crash Log", trace))
            Toast.makeText(this, "Log disalin", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })

        row.addView(btn("🔄 Reset & Buka Desktop", Color.rgb(37, 99, 235)) {
            getSharedPreferences("ruangdroid_prefs", Context.MODE_PRIVATE).edit().clear().commit()
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

        val welcome = File(docs, "Selamat_Datang_RuangDroid.txt")
        if (!welcome.exists()) {
            welcome.writeText(
                """
                =====================================================
                SELAMAT DATANG DI RUANGDROID DESKTOP OS v2.0
                =====================================================
                Fitur Utama:
                1. Multi-Window Floating PC:
                   - Geser jendela lewat Title Bar atas
                   - Ubah ukuran lewat pojok kanan bawah (◢)
                   - Minimize (—) ke Taskbar, Maximize (▢), atau Close (✕)
                2. Aplikasi Bawaan Lengkap:
                   - File Explorer, Web Browser, Notepad, Paint Studio,
                     Calculator, Command Prompt, Media Player,
                     Task Manager, Control Panel, dan EXE/Wine Plugin.
                3. Dukungan File .EXE & Wine Plugin:
                   - Buka folder C:\Installers di File Explorer atau
                     buka aplikasi "EXE & Wine Plugin" untuk meng-install
                     dan menjalankan paket .exe bawaan maupun import .exe
                     dari penyimpanan Android kamu.
                """.trimIndent()
            )
        }

        val batFile = File(inst, "System_Check.bat")
        if (!batFile.exists()) {
            batFile.writeText(
                """
                @echo off
                title RuangDroid Diagnostic Script
                echo [OK] Checking Virtual Drive C:\ ...
                echo [OK] Checking Multi-Window Compositor ...
                echo [OK] Checking PE32 / Wine Bridge Subsystem ...
                ver
                sysinfo
                echo Done! Semua sistem berjalan normal.
                """.trimIndent()
            )
        }

        // Seed portable PE32 .exe installers so user can install & run .exe apps out of the box
        seedPortableExe(File(inst, "Minesweeper_XP.exe"), "minesweeper", "Minesweeper Classic PC", x64 = false)
        seedPortableExe(File(inst, "Arkanoid_DX.exe"), "arkanoid", "Arkanoid Breakout DX", x64 = true)
        seedPortableExe(File(inst, "WinCalc_Sci.exe"), "calculator", "Windows Scientific Calc", x64 = false)

        // Pre-install Minesweeper into Program Files as a ready example
        val preInstalled = File(pf, "Minesweeper/Minesweeper_XP.exe")
        if (!preInstalled.exists()) {
            preInstalled.parentFile?.mkdirs()
            seedPortableExe(preInstalled, "minesweeper", "Minesweeper Classic PC", x64 = false)
        }
    }

    /**
     * Generates a valid MZ + PE32/PE32+ header binary containing embedded RuangDroid Portable metadata
     * so PE binary inspection shows authentic PE headers AND the built-in EXE subsystem can execute it natively.
     */
    fun seedPortableExe(target: File, appKey: String, title: String, x64: Boolean) {
        if (target.exists()) return
        val out = ByteArrayOutputStream()
        val buf = ByteArray(512)
        // DOS Header 'MZ'
        buf[0] = 'M'.code.toByte()
        buf[1] = 'Z'.code.toByte()
        // e_lfanew at offset 0x3C -> 0x80
        buf[0x3C] = 0x80.toByte()
        // PE Signature 'PE\0\0' at 0x80
        buf[0x80] = 'P'.code.toByte()
        buf[0x81] = 'E'.code.toByte()
        buf[0x82] = 0
        buf[0x83] = 0
        // COFF Machine: 0x8664 (AMD64) or 0x014C (i386)
        if (x64) {
            buf[0x84] = 0x64.toByte()
            buf[0x85] = 0x86.toByte()
        } else {
            buf[0x84] = 0x4C.toByte()
            buf[0x85] = 0x01.toByte()
        }
        // NumberOfSections = 2
        buf[0x86] = 2
        // SizeOfOptionalHeader
        buf[0x94] = if (x64) 0xF0.toByte() else 0xE0.toByte()
        // Characteristics: EXECUTABLE_IMAGE (0x0002)
        buf[0x96] = 0x02
        // Optional Header Magic at 0x98: PE32 (0x010B) or PE32+ (0x020B)
        buf[0x98] = 0x0B
        buf[0x99] = if (x64) 0x02 else 0x01
        // Subsystem at OptionalHeader + 0x44 (0xDC): WINDOWS_GUI (2)
        buf[0xDC] = 0x02

        out.write(buf)
        val meta = "\nKERNEL32.dll\u0000USER32.dll\u0000GDI32.dll\u0000RUANGDROID_EXE:$appKey|$title\n"
        out.write(meta.toByteArray(Charsets.UTF_8))
        target.writeBytes(out.toByteArray())
    }
}
