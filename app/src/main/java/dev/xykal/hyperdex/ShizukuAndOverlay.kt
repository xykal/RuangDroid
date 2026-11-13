package dev.xykal.hyperdex

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

object ShizukuBridge {
    private val exec = Executors.newSingleThreadExecutor()

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") != null

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun privilegeLabel(): String {
        if (!isRunning()) return "Shizuku Service Tidak Aktif"
        return try {
            val uid = Shizuku.getUid()
            val mode = when (uid) {
                0 -> "ROOT (UID 0)"
                2000 -> "ADB SHELL (UID 2000)"
                else -> "UID $uid"
            }
            if (hasPermission()) "Terhubung · $mode (v${Shizuku.getVersion()})"
            else "Berjalan ($mode) · Menunggu Izin"
        } catch (_: Throwable) {
            "Status Tidak Diketahui"
        }
    }

    fun requestPermission(requestCode: Int = 701) {
        try {
            if (isRunning() && !hasPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Executes a shell command via Shizuku's privileged ADB/Root binder process.
     */
    fun runPrivilegedAsync(command: String, onResult: (exitCode: Int, output: String) -> Unit) {
        exec.execute {
            if (!hasPermission()) {
                onResult(-1, "Shizuku belum aktif atau izin belum diberikan.")
                return@execute
            }
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                val out = BufferedReader(InputStreamReader(proc.inputStream)).readText()
                val err = BufferedReader(InputStreamReader(proc.errorStream)).readText()
                val code = proc.waitFor()
                val combined = (out + if (err.isNotBlank()) "\n$err" else "").trim()
                onResult(code, combined.ifEmpty { "(Sukses · exit code $code)" })
            } catch (t: Throwable) {
                onResult(-2, "Gagal eksekusi Shizuku: ${t.message}")
            }
        }
    }

    fun launchAppFreeformViaShizuku(pkg: String, activity: String, onFallback: () -> Unit) {
        if (!hasPermission()) {
            onFallback()
            return
        }
        val cmd = "am start -n '$pkg/$activity' --windowingMode 5"
        runPrivilegedAsync(cmd) { code, _ ->
            if (code != 0) onFallback()
        }
    }
}

object TermuxBridge {
    fun isTermuxInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage("com.termux") != null

    fun isTermuxX11Installed(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage("com.termux.x11") != null

    fun sendCommandToTermux(context: Context, command: String, background: Boolean = false): Boolean {
        return try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Custom System-Wide Floating PC Window & Dock (TYPE_APPLICATION_OVERLAY).
 * Stays floating on top of ANY external Android app with 100% custom RuangDroid PC frame!
 */
object SystemFloatingOverlay {
    private var rootView: View? = null
    private var wm: WindowManager? = null

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context, prefs: DesktopPrefs) {
        if (!canDraw(context) || rootView != null) return
        val appCtx = context.applicationContext
        val windowManager = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager

        fun dp(v: Int) = UiKit.dp(appCtx, v)
        val theme = prefs.currentTheme
        var expanded = false

        val typeFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            typeFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(90)
        }

        val container = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(16).toFloat()
        }

        // Compact Floating Start Orb Pill
        val orbBar = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(10), dp(6))
            background = UiKit.strokeRounded(appCtx, Color.argb(235, 13, 20, 36), theme.accent, 18)
        }

        val orbImg = ImageView(appCtx).apply {
            setImageDrawable(VectorIcons.get(appCtx, "start_orb", dp(24)))
        }
        orbBar.addView(orbImg, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(6) })
        val orbLabel = UiKit.label(appCtx, "PC HUD", 11f, UiKit.INK, bold = true)
        orbBar.addView(orbLabel)

        // Expandable Custom Mini Floating Window Body
        val miniWindow = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = UiKit.strokeRounded(appCtx, Color.argb(242, 14, 22, 38), theme.accent, 14)
        }

        val noteArea = EditText(appCtx).apply {
            hint = "Catatan cepat floating di atas semua app…"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(appCtx, 11.5f))
            background = UiKit.rounded(appCtx, Color.rgb(9, 14, 24), 8)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            minLines = 3
            maxLines = 4
        }

        val quickActions = LinearLayout(appCtx).apply { orientation = LinearLayout.HORIZONTAL }
        quickActions.addView(UiKit.chipButton(appCtx, "Ke Desktop") {
            val home = Intent(appCtx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appCtx.startActivity(home)
        }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { rightMargin = dp(5) })

        quickActions.addView(UiKit.chipButton(appCtx, "Tutup HUD", Color.argb(140, 220, 38, 38)) {
            prefs.systemOverlayEnabled = false
            hide()
        }, LinearLayout.LayoutParams(-2, dp(30)))

        miniWindow.addView(UiKit.label(appCtx, "HyperDeX Floating Overlay Window", 11.5f, theme.accent, bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        miniWindow.addView(noteArea, LinearLayout.LayoutParams(dp(230), -2).apply { bottomMargin = dp(8) })
        miniWindow.addView(quickActions, LinearLayout.LayoutParams(-1, -2))

        fun syncExpanded() {
            miniWindow.visibility = if (expanded) View.VISIBLE else View.GONE
            params.flags = if (expanded) {
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
            try {
                windowManager.updateViewLayout(container, params)
            } catch (_: Throwable) {
            }
        }

        var dX = 0f
        var dY = 0f
        var origX = 0
        var origY = 0
        var moved = false

        orbBar.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = ev.rawX; dY = ev.rawY
                    origX = params.x; origY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (ev.rawX - dX).toInt()
                    val my = (ev.rawY - dY).toInt()
                    if (kotlin.math.abs(mx) > dp(6) || kotlin.math.abs(my) > dp(6)) moved = true
                    params.x = origX + mx
                    params.y = origY + my
                    try { windowManager.updateViewLayout(container, params) } catch (_: Throwable) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        expanded = !expanded
                        syncExpanded()
                    }
                    true
                }
                else -> false
            }
        }

        container.addView(orbBar, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(4) })
        container.addView(miniWindow, LinearLayout.LayoutParams(-2, -2))

        try {
            windowManager.addView(container, params)
            rootView = container
        } catch (_: Throwable) {
        }
    }

    fun hide() {
        val v = rootView ?: return
        rootView = null
        try {
            wm?.removeView(v)
        } catch (_: Throwable) {
        }
    }

    // ==================== SHIZUKU & TERMUX HUB WINDOW UI ====================
    fun buildShizukuTermuxHubView(
        context: Context,
        prefs: DesktopPrefs,
        host: DesktopHostCallbacks
    ): View {
        fun dp(v: Int) = UiKit.dp(context, v)
        val theme = prefs.currentTheme

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(12, 18, 32))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }

        // 1. Shizuku Status & Privilege Card
        val shizukuCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.strokeRounded(context, Color.rgb(18, 28, 48), theme.accent, 13)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val statusTv = UiKit.label(context, "Shizuku: ${ShizukuBridge.privilegeLabel()}", 12.5f, UiKit.INK, bold = true)
        shizukuCard.addView(statusTv)
        shizukuCard.addView(
            UiKit.label(
                context,
                "Shizuku membuka akses ADB (UID 2000) / Root untuk memaksa semua aplikasi Android jalan di Freeform Window & kustomisasi DPI layar langsung.",
                10.8f,
                UiKit.MUTED,
                maxLines = 3
            ),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(8) }
        )

        val logTv = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 11f))
            setTextColor(Color.rgb(134, 239, 172))
            background = UiKit.rounded(context, Color.rgb(8, 12, 22), 9)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            text = "[Ready] Output eksekusi Shizuku & Termux akan tampil di sini."
        }

        val shizukuBtns = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        shizukuBtns.addView(UiKit.chipButton(context, "Minta Izin Shizuku", Color.argb(110, 59, 130, 246)) {
            if (!ShizukuBridge.isInstalled(context)) {
                host.openBrowser("https://github.com/RikkaApps/Shizuku/releases")
            } else if (!ShizukuBridge.isRunning()) {
                context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            } else {
                ShizukuBridge.requestPermission(701)
                root.postDelayed({ statusTv.text = "Shizuku: ${ShizukuBridge.privilegeLabel()}" }, 900)
            }
        }, LinearLayout.LayoutParams(0, dp(32), 1f).apply { rightMargin = dp(6) })

        shizukuBtns.addView(UiKit.chipButton(context, "Unlock Freeform Global", Color.argb(110, 16, 185, 129)) {
            val cmd = "settings put global enable_freeform_support 1 && settings put global force_resizable_activities 1 && settings put global enable_non_resizable_multi_window 1"
            ShizukuBridge.runPrivilegedAsync(cmd) { _, out ->
                root.post {
                    statusTv.text = "Shizuku: ${ShizukuBridge.privilegeLabel()}"
                    logTv.text = "> Unlock Freeform Global:\n$out"
                }
            }
        }, LinearLayout.LayoutParams(0, dp(32), 1f))
        shizukuCard.addView(shizukuBtns, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        // Shizuku Real DPI Tweaker Row
        val dpiRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("DPI PC (320)" to "wm density 320", "DPI Sedang (380)" to "wm density 380", "Reset DPI Asli" to "wm density reset").forEachIndexed { i, (lbl, cmd) ->
            dpiRow.addView(UiKit.chipButton(context, lbl) {
                ShizukuBridge.runPrivilegedAsync(cmd) { _, out ->
                    root.post { logTv.text = "> $cmd\n$out" }
                }
            }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { if (i < 2) rightMargin = dp(5) })
        }
        shizukuCard.addView(dpiRow)
        root.addView(shizukuCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // 2. Custom System-Wide Floating Overlay Window Card
        val overlayCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(context, Color.rgb(18, 28, 48), 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val ovText = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        ovText.addView(UiKit.label(context, "Custom Floating PC HUD (Di Atas Semua App)", 12f, UiKit.INK, bold = true))
        ovText.addView(UiKit.label(context, "Tampilkan jendela mini HyperDeX custom di atas WhatsApp/Game/Termux tanpa UI bawaan Android", 10.5f, UiKit.MUTED, maxLines = 2))
        overlayCard.addView(ovText, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })

        val ovBtn = UiKit.chipButton(context, if (prefs.systemOverlayEnabled && canDraw(context)) "AKTIF" else "AKTIFKAN", Color.argb(120, 99, 102, 241)) {
            if (!canDraw(context)) {
                requestOverlayPermission(context)
            } else {
                prefs.systemOverlayEnabled = !prefs.systemOverlayEnabled
                if (prefs.systemOverlayEnabled) show(context, prefs) else hide()
                host.showToast("Floating PC HUD", if (prefs.systemOverlayEnabled) "Floating HUD aktif di atas semua aplikasi!" else "Floating HUD dimatikan")
            }
        }
        overlayCard.addView(ovBtn, LinearLayout.LayoutParams(-2, dp(34)))
        root.addView(overlayCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // 3. Termux & Termux:X11 Integration Card
        val tInstalled = TermuxBridge.isTermuxInstalled(context)
        val x11Installed = TermuxBridge.isTermuxX11Installed(context)
        val termuxCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(context, Color.rgb(18, 28, 48), 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        termuxCard.addView(
            UiKit.label(
                context,
                "Termux Bridge: ${if (tInstalled) "Terpasang" else "Belum Terpasang"}  |  Termux:X11: ${if (x11Installed) "Terpasang" else "Belum"}",
                12f,
                UiKit.INK,
                bold = true
            )
        )
        val tRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        tRow.addView(UiKit.chipButton(context, if (tInstalled) "Buka Termux" else "Get Termux APK") {
            if (tInstalled) {
                context.packageManager.getLaunchIntentForPackage("com.termux")?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            } else {
                host.openBrowser("https://github.com/termux/termux-app/releases")
            }
        }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { rightMargin = dp(5) })

        tRow.addView(UiKit.chipButton(context, if (x11Installed) "Buka Termux:X11" else "Get Termux:X11") {
            if (x11Installed) {
                context.packageManager.getLaunchIntentForPackage("com.termux.x11")?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            } else {
                host.openBrowser("https://github.com/termux/termux-x11/releases")
            }
        }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { rightMargin = dp(5) })

        tRow.addView(UiKit.chipButton(context, "Run pkg update") {
            val ok = TermuxBridge.sendCommandToTermux(context, "pkg update -y && neofetch", background = false)
            logTv.text = if (ok) "> Perintah dikirim ke Termux RunCommandService!"
            else "> Pastikan Termux terpasang & set 'allow-external-apps = true' di ~/.termux/termux.properties"
        }, LinearLayout.LayoutParams(0, dp(30), 1f))
        termuxCard.addView(tRow)
        root.addView(termuxCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // 4. Direct Privileged Shell Runner (Shizuku ADB Shell)
        val cmdRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cmdEdit = EditText(context).apply {
            hint = "Perintah ADB Shizuku (contoh: wm size, pm list packages -3, dumpsys battery)"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 11.5f))
            setSingleLine(true)
            background = UiKit.rounded(context, Color.rgb(9, 14, 24), 9)
            setPadding(dp(10), 0, dp(10), 0)
        }
        cmdRow.addView(cmdEdit, LinearLayout.LayoutParams(0, dp(34), 1f).apply { rightMargin = dp(6) })
        cmdRow.addView(UiKit.chipButton(context, "Run ADB", Color.argb(140, 37, 99, 235)) {
            val c = cmdEdit.text?.toString()?.trim().orEmpty()
            if (c.isNotEmpty()) {
                logTv.text = "Menjalankan via Shizuku: $c …"
                ShizukuBridge.runPrivilegedAsync(c) { code, out ->
                    root.post { logTv.text = "[Exit $code] $ $c\n$out" }
                }
            }
        }, LinearLayout.LayoutParams(-2, dp(34)))
        root.addView(cmdRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        root.addView(logTv, LinearLayout.LayoutParams(-1, -2))

        scroll.addView(root)
        return scroll
    }
}
