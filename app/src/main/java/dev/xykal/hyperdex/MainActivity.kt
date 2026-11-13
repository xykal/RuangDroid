package dev.xykal.hyperdex

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

data class AndroidAppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val iconBitmap: Bitmap?
)

class MainActivity : Activity(), DesktopHostCallbacks {

    companion object {
        private const val REQ_PICK_EXE = 1001
        private const val REQ_PICK_WALLPAPER = 1002
        private const val REQ_PICK_AUDIO = 1003
    }

    private lateinit var prefs: DesktopPrefs
    private lateinit var rootLayout: LinearLayout
    private lateinit var liveShaderHolder: FrameLayout
    private var activeGlView: android.opengl.GLSurfaceView? = null
    private lateinit var wallpaperView: ImageView
    private lateinit var desktopSurfaceHost: FrameLayout
    private lateinit var windowStage: FrameLayout
    private lateinit var overlayStage: FrameLayout
    private lateinit var taskbarHost: FrameLayout
    private lateinit var windowManager: FloatingWindowManager
    override lateinit var overlays: OverlayKit

    private val installedApps = mutableListOf<AndroidAppEntry>()
    private val appByPkg = mutableMapOf<String, AndroidAppEntry>()
    private val scanExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var scanningApps = false
    private var wasImmersiveApplied = false
    private var isLockedByShield = false

    private fun dp(v: Int): Int = UiKit.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DesktopPrefs(this)

        // 3-Layer Polyglot Anti-Clone & Official Release Signature Hard Lock (Rust + C11 + C++20)
        val verdict = NativeCore.verifyAppSecurity(this)
        if (!verdict.verified) {
            isLockedByShield = true
            rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(NativeCore.buildHardLockShieldView(this@MainActivity, verdict), LinearLayout.LayoutParams(-1, -1))
            }
            setContentView(rootLayout)
            applyOrientationAndBarsSafe()
            return
        }

        try {
            VirtualDrive.ensureInitialized(this)
        } catch (_: Throwable) {
        }

        // Persistent 4-Layer Custom Desktop Compositor (100% Full Window — Zero Stock Android UI)
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 16, 30))
        }

        val stageStack = FrameLayout(this)

        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        stageStack.addView(wallpaperView, FrameLayout.LayoutParams(-1, -1))

        liveShaderHolder = FrameLayout(this)
        stageStack.addView(liveShaderHolder, FrameLayout.LayoutParams(-1, -1))

        desktopSurfaceHost = FrameLayout(this)
        stageStack.addView(desktopSurfaceHost, FrameLayout.LayoutParams(-1, -1))

        windowStage = FrameLayout(this)
        stageStack.addView(windowStage, FrameLayout.LayoutParams(-1, -1))

        overlayStage = FrameLayout(this)
        stageStack.addView(overlayStage, FrameLayout.LayoutParams(-1, -1))

        rootLayout.addView(stageStack, LinearLayout.LayoutParams(-1, 0, 1f))

        taskbarHost = FrameLayout(this).apply {
            setPadding(dp(8), dp(2), dp(8), dp(5))
        }
        rootLayout.addView(taskbarHost, LinearLayout.LayoutParams(-1, dp(52)))

        overlays = OverlayKit(this, overlayStage, prefs)
        windowManager = FloatingWindowManager(
            context = this,
            stage = windowStage,
            prefs = prefs,
            onWindowsChanged = { renderTaskbar() }
        )

        setContentView(rootLayout)

        applyOrientationAndBarsSafe()
        refreshDesktopUI()
        triggerAsyncAppScan()

        if (prefs.systemOverlayEnabled && SystemFloatingOverlay.canDraw(this)) {
            SystemFloatingOverlay.show(this, prefs)
        }
    }

    override fun onResume() {
        super.onResume()
        applyOrientationAndBarsSafe()
        if (isLockedByShield) return
        try { activeGlView?.onResume() } catch (_: Throwable) {}
        renderTaskbar()
        triggerAsyncAppScan()
        if (prefs.systemOverlayEnabled && SystemFloatingOverlay.canDraw(this)) {
            SystemFloatingOverlay.show(this, prefs)
        }
    }

    override fun onPause() {
        try { activeGlView?.onPause() } catch (_: Throwable) {}
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isLockedByShield && event.action == KeyEvent.ACTION_DOWN) {
            // Alt + Tab -> Cycle Window Focus
            if (event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_TAB) {
                val next = windowManager.cycleNextWindow()
                if (next != null) showToast("Alt+Tab Switcher", "Fokus: ${next.title}")
                return true
            }
            // Meta/Win Key or Ctrl + Esc -> Toggle Start Menu
            if (event.keyCode == KeyEvent.KEYCODE_META_LEFT || event.keyCode == KeyEvent.KEYCODE_META_RIGHT ||
                (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_ESCAPE)
            ) {
                if (overlays.hasOpenOverlay()) overlays.dismissOverlay() else showStartMenuOverlay(focusSearch = false)
                return true
            }
            // Ctrl + 1/2/3 -> Switch Virtual Desktop
            if (event.isCtrlPressed && !event.isAltPressed) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_1 -> { windowManager.switchWorkspace(0); showToast("Virtual Desktop", "Workspace 1"); return true }
                    KeyEvent.KEYCODE_2 -> { windowManager.switchWorkspace(1); showToast("Virtual Desktop", "Workspace 2"); return true }
                    KeyEvent.KEYCODE_3 -> { windowManager.switchWorkspace(2); showToast("Virtual Desktop", "Workspace 3"); return true }
                }
            }
            // Ctrl + Alt + T -> Terminal
            if (event.isCtrlPressed && event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_T) {
                openTerminal(null)
                return true
            }
            // Ctrl + Alt + Del -> Task Manager
            if (event.isCtrlPressed && event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
                openTaskManager()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (!isLockedByShield && ev.actionButton == MotionEvent.BUTTON_SECONDARY &&
            ev.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
        ) {
            showDesktopBlankContextMenu(ev.rawX, ev.rawY)
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isLockedByShield) return
        overlays.dismissOverlay()
        refreshDesktopUI()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isLockedByShield) {
            finishAffinity()
            return
        }
        if (overlays.hasOpenOverlay()) {
            overlays.dismissOverlay()
            return
        }
        val activeId = windowManager.activeWindowId
        if (activeId != null) {
            windowManager.minimizeWindow(activeId)
        }
    }

    override fun showToast(title: String, message: String) {
        overlays.notify(title, message)
    }

    private fun applyOrientationAndBarsSafe() {
        try {
            requestedOrientation = if (prefs.landscapeLock) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } catch (_: Throwable) {
        }

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.rgb(8, 12, 22)
        } catch (_: Throwable) {
        }

        if (!prefs.immersiveMode && !wasImmersiveApplied) return

        rootLayout.post {
            try {
                if (!isFinishing && window?.decorView?.isAttachedToWindow == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val controller = window.insetsController
                        if (prefs.immersiveMode) {
                            wasImmersiveApplied = true
                            controller?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                            controller?.systemBarsBehavior =
                                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else if (wasImmersiveApplied) {
                            wasImmersiveApplied = false
                            controller?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = if (prefs.immersiveMode) {
                            wasImmersiveApplied = true
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        } else {
                            wasImmersiveApplied = false
                            View.SYSTEM_UI_FLAG_VISIBLE
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun triggerAsyncAppScan() {
        if (scanningApps) return
        scanningApps = true
        val iconPx = dp(46).coerceIn(48, 144)
        scanExecutor.execute {
            try {
                val pm = packageManager
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                val rawList = try { pm.queryIntentActivities(query, 0) } catch (_: Throwable) { emptyList() }

                val parsed = mutableListOf<AndroidAppEntry>()
                val seen = HashSet<String>()
                for (ri in rawList) {
                    val ai = ri?.activityInfo ?: continue
                    val pkg = ai.packageName ?: continue
                    val cls = ai.name ?: continue
                    if (pkg == packageName || !seen.add(pkg)) continue

                    val lbl = try {
                        ri.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: pkg
                    } catch (_: Throwable) { pkg }

                    val bmp = try {
                        UiKit.safeDrawableToBitmap(ri.loadIcon(pm), iconPx)
                    } catch (_: Throwable) { null }

                    parsed.add(AndroidAppEntry(pkg, cls, lbl, bmp))
                }
                parsed.sortBy { it.label.lowercase(Locale.getDefault()) }
                prefs.ensureSeeded(parsed.map { it.packageName to it.label })

                runOnUiThread {
                    val changed = parsed.size != installedApps.size ||
                        parsed.map { it.packageName } != installedApps.map { it.packageName }
                    installedApps.clear()
                    appByPkg.clear()
                    installedApps.addAll(parsed)
                    for (a in parsed) appByPkg[a.packageName] = a
                    scanningApps = false
                    if (changed) {
                        renderDesktopSurface()
                        renderTaskbar()
                    }
                }
            } catch (_: Throwable) {
                scanningApps = false
            }
        }
    }

    private fun isWideDesktop(): Boolean {
        val dm = resources.displayMetrics
        return dm.widthPixels > dm.heightPixels || (dm.widthPixels / UiKit.unit(this)) >= 580f
    }

    override fun refreshDesktopUI() {
        val dm = resources.displayMetrics
        val availHUnits = dm.heightPixels / UiKit.unit(this)
        val tbHeight = if (availHUnits < 420f) dp(44) else dp(50)
        taskbarHost.layoutParams = LinearLayout.LayoutParams(-1, tbHeight)
        applyOrientationAndBarsSafe()
        applyWallpaper()
        renderDesktopSurface()
        renderTaskbar()
        windowManager.refreshHeaderStyles()
    }

    private fun applyWallpaper() {
        val theme = prefs.currentTheme

        // Manage OpenGL ES 3.0 GPU Live Shader Layer (libhyper_gfx_cc.so)
        val wantShader = prefs.liveShaderWallpaper && !prefs.useCustomWallpaper && NativeCore.ccLoaded
        if (wantShader) {
            if (activeGlView == null) {
                val gl = NativeCore.createLiveShaderWallpaperView(this, prefs)
                if (gl != null) {
                    liveShaderHolder.removeAllViews()
                    liveShaderHolder.addView(gl, FrameLayout.LayoutParams(-1, -1))
                    activeGlView = gl
                }
            }
            liveShaderHolder.visibility = View.VISIBLE
        } else {
            try { activeGlView?.onPause() } catch (_: Throwable) {}
            liveShaderHolder.removeAllViews()
            activeGlView = null
            liveShaderHolder.visibility = View.GONE
        }

        if (prefs.useCustomWallpaper) {
            val bmp = try { BitmapFactory.decodeFile(prefs.customWallpaperFile().absolutePath) } catch (_: Throwable) { null }
            if (bmp != null) {
                wallpaperView.setImageBitmap(if (prefs.acrylicBlurWallpaper) NativeCore.safeApplyAcrylicBlur(bmp) else bmp)
                return
            }
        }
        if (prefs.acrylicBlurWallpaper && NativeCore.ccLoaded) {
            val art = Bitmap.createBitmap(360, 220, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(art)
            GradientDrawable(GradientDrawable.Orientation.TL_BR, theme.bgColors).apply {
                setBounds(0, 0, 360, 220)
                draw(canvas)
            }
            val orbPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(135, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
            }
            canvas.drawCircle(95f, 70f, 58f, orbPaint)
            orbPaint.color = Color.argb(95, 255, 255, 255)
            canvas.drawCircle(265f, 150f, 68f, orbPaint)
            NativeCore.ccAcrylicBlurBitmap(art, 22, Color.argb(42, 8, 14, 26))
            wallpaperView.setImageBitmap(art)
            return
        }
        wallpaperView.setImageDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR, theme.bgColors))
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        return try {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100) to bm.isCharging
        } catch (_: Throwable) {
            100 to false
        }
    }

    private fun memorySummary(): String {
        return try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            String.format(Locale.US, "RAM %.1f / %.1f GB", (mi.totalMem - mi.availMem) / 1073741824.0, mi.totalMem / 1073741824.0)
        } catch (_: Throwable) { "RAM Aktif" }
    }

    private fun storageSummary(): String {
        return try {
            val st = StatFs(Environment.getDataDirectory().path)
            String.format(Locale.US, "Disk %.0f / %.0f GB Free", st.availableBytes / 1073741824.0, st.totalBytes / 1073741824.0)
        } catch (_: Throwable) { "Storage Aktif" }
    }

    // ==================== LAYER 1: RESPONSIVE DESKTOP WORKSPACE ====================
    private fun renderDesktopSurface() {
        desktopSurfaceHost.removeAllViews()
        val theme = prefs.currentTheme
        val wide = isWideDesktop()
        val dm = resources.displayMetrics
        val availWidthUnits = dm.widthPixels / UiKit.unit(this)

        val wrapper = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(4))
            // Right-click / Long-press empty desktop surface -> Desktop Context Menu!
            setOnLongClickListener {
                showDesktopBlankContextMenu()
                true
            }
        }

        val leftDesktop = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Sleek Responsive Desktop Header Bar (HorizontalScrollView prevents clipping on 320dp-360dp screens)
        val topStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(6))
        }
        val titleBadge = UiKit.label(
            this,
            if (availWidthUnits < 430f) "HyperDeX 5.1" else "HyperDeX Ultimate OS 5.1",
            11.4f,
            UiKit.INK,
            bold = true
        )
        topStrip.addView(titleBadge, LinearLayout.LayoutParams(-2, dp(26)).apply { rightMargin = dp(8) })

        val actionsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        actionsRow.addView(UiKit.chipButton(this, "Shield / Core", Color.argb(90, 16, 185, 129)) {
            openNativeHub()
        }, LinearLayout.LayoutParams(-2, dp(25)).apply { rightMargin = dp(5) })

        actionsRow.addView(UiKit.chipButton(this, "Shizuku / HUD", Color.argb(85, 79, 70, 229)) {
            openShizukuHub()
        }, LinearLayout.LayoutParams(-2, dp(25)).apply { rightMargin = dp(5) })

        actionsRow.addView(UiKit.chipButton(this, "+ .EXE", Color.argb(85, 190, 24, 93)) {
            openExeCenter(null)
        }, LinearLayout.LayoutParams(-2, dp(25)).apply { rightMargin = dp(5) })

        actionsRow.addView(UiKit.chipButton(this, "+ Folder") {
            showCreateOrEditFolderWindow(null)
        }, LinearLayout.LayoutParams(-2, dp(25)).apply { rightMargin = dp(5) })

        actionsRow.addView(UiKit.chipButton(this, "+ Shortcut") {
            showDesktopShortcutPickerWindow()
        }, LinearLayout.LayoutParams(-2, dp(25)))

        actionsScroll.addView(actionsRow)
        topStrip.addView(actionsScroll, LinearLayout.LayoutParams(0, dp(26), 1f))

        leftDesktop.addView(topStrip)

        // Dynamic Responsive Column Calculation for ANY Resolution / Aspect Ratio / Scale
        val sidebarUnits = if (wide && availWidthUnits >= 650f) 195f else 0f
        val gridWidthUnits = (availWidthUnits - sidebarUnits - 24f).coerceAtLeast(260f)
        val targetCellUnits = when (prefs.iconScale) {
            0 -> 78f
            2 -> 108f
            else -> 92f
        }
        val cols = (gridWidthUnits / targetCellUnits).toInt().coerceIn(3, 10)
        val cellHeightDp = when (prefs.iconScale) {
            0 -> 72
            2 -> 94
            else -> 82
        }
        val iconSizeDp = when (prefs.iconScale) {
            0 -> 32
            2 -> 44
            else -> 38
        }

        val tiles = mutableListOf<View>()

        // 1. Built-in PC Apps (Crisp Custom Vector Icons)
        for (bApp in BuiltInApps.all.filter { it.showOnDesktop }) {
            tiles.add(
                desktopTileView(
                    label = bApp.title,
                    drawable = bApp.icon(this, dp(iconSizeDp)),
                    iconSizeDp = iconSizeDp,
                    onClick = { launchBuiltInByKey(bApp.key) },
                    onLongClick = { rawX, rawY ->
                        overlays.showContextMenu(
                            title = bApp.title,
                            items = listOf(
                                "Buka Jendela Baru" to { launchBuiltInByKey(bApp.key) },
                                "Pengaturan Skala & Tema" to { openControlPanel() }
                            ),
                            rawX = rawX,
                            rawY = rawY
                        )
                    }
                )
            )
        }

        // 2. Installed .EXE Programs
        for (exe in prefs.getInstalledExes()) {
            val exeFile = File(exe.exePath)
            if (!exeFile.exists()) continue
            val iconKey = when {
                exe.title.contains("Mine", ignoreCase = true) -> "minesweeper"
                exe.title.contains("Arkanoid", ignoreCase = true) -> "arkanoid"
                else -> "exe_item"
            }
            tiles.add(
                desktopTileView(
                    label = exe.title,
                    drawable = VectorIcons.get(this, iconKey, dp(iconSizeDp)),
                    iconSizeDp = iconSizeDp,
                    onClick = { openExeFile(exeFile) },
                    onLongClick = { rawX, rawY ->
                        overlays.showContextMenu(
                            title = exe.title,
                            items = listOf(
                                "Jalankan Window (.EXE)" to { openExeFile(exeFile) },
                                "Inspeksi Binary PE Header" to {
                                    windowManager.openWindow("pe_${exe.id}", "PE Header — ${exe.title}", "exe_center", singleInstance = true) {
                                        ExeEmulatorApp.buildPeInspectorView(this, prefs, this, exeFile)
                                    }
                                },
                                "Lepas dari Desktop" to {
                                    prefs.saveInstalledExes(prefs.getInstalledExes().filterNot { it.id == exe.id })
                                    refreshDesktopUI()
                                }
                            ),
                            rawX = rawX,
                            rawY = rawY
                        )
                    }
                )
            )
        }

        // 3. Desktop Folders
        for (folder in prefs.getFolders()) {
            val count = folder.packages.count { appByPkg.containsKey(it) }
            tiles.add(
                desktopTileView(
                    label = "${folder.name} ($count)",
                    drawable = VectorIcons.get(this, "folder", dp(iconSizeDp)),
                    iconSizeDp = iconSizeDp,
                    onClick = { openFolderFloatingWindow(folder) },
                    onLongClick = { rawX, rawY ->
                        overlays.showContextMenu(
                            title = folder.name,
                            items = listOf(
                                "Buka Jendela Folder" to { openFolderFloatingWindow(folder) },
                                "Kelola Isi / Ubah Nama" to { showCreateOrEditFolderWindow(folder) },
                                "Hapus Folder" to {
                                    prefs.saveFolders(prefs.getFolders().filterNot { it.id == folder.id })
                                    renderDesktopSurface()
                                }
                            ),
                            rawX = rawX,
                            rawY = rawY
                        )
                    }
                )
            )
        }

        // 4. Android App Shortcuts
        for (pkg in prefs.getDesktopPackages()) {
            val app = appByPkg[pkg] ?: continue
            val drw: Drawable = if (app.iconBitmap != null) BitmapDrawable(resources, app.iconBitmap)
            else VectorIcons.get(this, "explorer", dp(iconSizeDp))
            tiles.add(
                desktopTileView(
                    label = app.label,
                    drawable = drw,
                    iconSizeDp = iconSizeDp,
                    onClick = { launchAndroidApp(app) },
                    onLongClick = { rawX, rawY -> showAndroidAppContextMenu(app, rawX, rawY) }
                )
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setOnLongClickListener { showDesktopBlankContextMenu(); true }
        }
        scroll.addView(UiKit.buildGrid(this, cols, cellHeightDp, tiles, hGapDp = 4, vGapDp = 4))
        leftDesktop.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        if (sidebarUnits > 0f) {
            wrapper.addView(leftDesktop, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(10) })
            wrapper.addView(buildPcWidgetSidebar(theme), LinearLayout.LayoutParams(dp(190), -1))
        } else {
            wrapper.addView(leftDesktop, LinearLayout.LayoutParams(-1, -1))
        }

        desktopSurfaceHost.addView(wrapper, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showDesktopBlankContextMenu(rawX: Float = -1f, rawY: Float = -1f) {
        overlays.showContextMenu(
            title = "Menu Desktop HyperDeX v5.0",
            rawX = rawX,
            rawY = rawY,
            items = listOf(
                "Buat Folder Baru" to { showCreateOrEditFolderWindow(null) },
                "Tambah Shortcut Aplikasi" to { showDesktopShortcutPickerWindow() },
                "Ganti Tema Berikutnya" to { prefs.cycleTheme(); refreshDesktopUI() },
                "Toggle Live GPU Shader Wallpaper (GLES3)" to { prefs.liveShaderWallpaper = !prefs.liveShaderWallpaper; refreshDesktopUI() },
                "Toggle Acrylic Blur Wallpaper (C++)" to { prefs.acrylicBlurWallpaper = !prefs.acrylicBlurWallpaper; refreshDesktopUI() },
                "HyperShield & Native Core (Rust/C/C++)" to { openNativeHub() },
                "Skala & Pengaturan Layar" to { openControlPanel() },
                "Shizuku & Floating HUD" to { openShizukuHub() },
                "Buka Terminal CMD" to { openTerminal(null) }
            )
        )
    }

    private fun desktopTileView(
        label: String,
        drawable: Drawable,
        iconSizeDp: Int,
        onClick: () -> Unit,
        onLongClick: (Float, Float) -> Unit
    ): View {
        var lastRawX = -1f
        var lastRawY = -1f
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = UiKit.rounded(this@MainActivity, Color.argb(16, 255, 255, 255), 11)
            setPadding(dp(3), dp(4), dp(3), dp(3))
            setOnTouchListener { v, ev ->
                lastRawX = ev.rawX
                lastRawY = ev.rawY
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(85).start()
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(130).setInterpolator(UiKit.springInterpolator).start()
                }
                false
            }
            setOnClickListener { onClick() }
            setOnLongClickListener {
                onLongClick(lastRawX, lastRawY)
                true
            }
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)))

            addView(TextView(this@MainActivity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(this@MainActivity, 10f))
                setTextColor(UiKit.INK)
                setShadowLayer(4f, 0f, 1f, Color.BLACK)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(3), dp(2), 0)
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun buildPcWidgetSidebar(theme: ThemePalette): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.strokeRounded(
                this@MainActivity,
                Color.argb(105, 10, 16, 30),
                Color.argb(55, 255, 255, 255),
                16
            )
            setPadding(dp(13), dp(11), dp(13), dp(11))
        }

        val now = Date()
        card.addView(UiKit.label(this, SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 27f, UiKit.INK, bold = true))
        card.addView(UiKit.label(this, SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(now), 11f, theme.accent, bold = true))

        card.addView(View(this).apply {
            setBackgroundColor(Color.argb(35, 255, 255, 255))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(7); bottomMargin = dp(7) })

        val (bat, chg) = batteryInfo()
        card.addView(UiKit.label(this, "${if (chg) "Charging" else "Battery"}: $bat%", 10.8f, UiKit.INK, bold = true))
        card.addView(UiKit.label(this, memorySummary(), 10f, UiKit.MUTED).apply { setPadding(0, dp(2), 0, 0) })
        card.addView(UiKit.label(this, storageSummary(), 10f, UiKit.MUTED).apply { setPadding(0, dp(2), 0, 0) })
        card.addView(
            UiKit.label(
                this,
                if (NativeCore.allLoaded) "Native: Rust + C11 + C++20" else "Native: Partial",
                9.8f,
                Color.rgb(52, 211, 153),
                bold = true
            ).apply { setPadding(0, dp(3), 0, 0) }
        )
        card.addView(
            UiKit.label(
                this,
                if (ShizukuBridge.hasPermission()) "Shizuku: Aktif" else "Shizuku: Standby",
                9.8f,
                if (ShizukuBridge.hasPermission()) Color.rgb(74, 222, 128) else UiKit.MUTED,
                bold = true
            ).apply { setPadding(0, dp(2), 0, 0) }
        )

        card.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        card.addView(UiKit.chipButton(this, "Task Manager", Color.argb(55, 20, 184, 166)) {
            openTaskManager()
        }, LinearLayout.LayoutParams(-1, dp(29)).apply { bottomMargin = dp(5) })

        card.addView(UiKit.chipButton(
            this,
            "Action Center",
            Color.argb(75, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
        ) {
            showQuickSettingsOverlay()
        }, LinearLayout.LayoutParams(-1, dp(29)))

        return card
    }

    // ==================== LAYER 4: WINDOWS 11 FLUENT TASKBAR ====================
    private fun renderTaskbar() {
        taskbarHost.removeAllViews()
        val theme = prefs.currentTheme

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.strokeRounded(
                this@MainActivity,
                theme.taskbarBg,
                Color.argb(60, 255, 255, 255),
                15
            )
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }

        if (prefs.centerTaskbar && isWideDesktop()) {
            bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 0.35f))
        }

        val dm = resources.displayMetrics
        val availWUnits = dm.widthPixels / UiKit.unit(this)
        val availHUnits = dm.heightPixels / UiKit.unit(this)
        val compactBar = availWUnits < 460f
        val btnH = if (availHUnits < 420f) dp(30) else dp(33)

        // Vector Start Orb Button
        val startBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = UiKit.rounded(this@MainActivity, Color.argb(42, 131, 170, 255), 10)
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(VectorIcons.get(this@MainActivity, "start_orb", dp(22)))
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
            UiKit.attachPressBounce(this, 0.88f)
            setOnClickListener { showStartMenuOverlay(focusSearch = false) }
        }
        bar.addView(startBtn, LinearLayout.LayoutParams(dp(34), btnH).apply { rightMargin = dp(4) })

        // Vector Search Pill (hidden on ultra-narrow portrait so pinned/window tabs have generous room)
        if (!compactBar) {
            val searchBtn = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = UiKit.rounded(this@MainActivity, Color.argb(32, 215, 229, 255), 10)
                setPadding(dp(8), 0, dp(9), 0)
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(VectorIcons.get(this@MainActivity, "search", dp(16), UiKit.MUTED))
                }, LinearLayout.LayoutParams(dp(14), dp(14)))
                if (isWideDesktop()) {
                    addView(UiKit.label(this@MainActivity, "  Cari", 10.6f, UiKit.MUTED))
                }
                UiKit.attachPressBounce(this, 0.92f)
                setOnClickListener { showStartMenuOverlay(focusSearch = true) }
            }
            bar.addView(searchBtn, LinearLayout.LayoutParams(-2, btnH).apply { rightMargin = dp(5) })
        }

        // Virtual Desktop Workspace Switcher: compact [D1] cycle pill on narrow portrait, full [1][2][3] on wide
        if (compactBar) {
            val curWs = windowManager.currentWorkspace
            val wsCompact = UiKit.label(this, "D${curWs + 1}", 9.8f, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER
                background = UiKit.rounded(this@MainActivity, theme.accent, 8)
                setOnClickListener {
                    val next = (windowManager.currentWorkspace + 1) % 3
                    windowManager.switchWorkspace(next)
                    showToast("Virtual Desktop", "Workspace ${next + 1}")
                }
                setOnLongClickListener {
                    overlays.showContextMenu(
                        title = "Pilih Virtual Desktop",
                        items = listOf(
                            "Workspace 1" to { windowManager.switchWorkspace(0) },
                            "Workspace 2" to { windowManager.switchWorkspace(1) },
                            "Workspace 3" to { windowManager.switchWorkspace(2) }
                        )
                    )
                    true
                }
            }
            bar.addView(wsCompact, LinearLayout.LayoutParams(dp(28), btnH - dp(2)).apply { rightMargin = dp(4) })
        } else {
            val wsBox = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.rounded(this@MainActivity, Color.argb(26, 255, 255, 255), 9)
                setPadding(dp(2), dp(2), dp(2), dp(2))
            }
            for (ws in 0..2) {
                val activeWs = (ws == windowManager.currentWorkspace)
                val wsBtn = UiKit.label(
                    this,
                    "${ws + 1}",
                    9.8f,
                    if (activeWs) Color.WHITE else UiKit.MUTED,
                    bold = true
                ).apply {
                    gravity = Gravity.CENTER
                    background = UiKit.rounded(
                        this@MainActivity,
                        if (activeWs) theme.accent else Color.TRANSPARENT,
                        7
                    )
                    setOnClickListener { windowManager.switchWorkspace(ws) }
                }
                wsBox.addView(wsBtn, LinearLayout.LayoutParams(dp(19), btnH - dp(6)).apply { if (ws < 2) rightMargin = dp(2) })
            }
            bar.addView(wsBox, LinearLayout.LayoutParams(-2, btnH - dp(2)).apply { rightMargin = dp(5) })
        }

        // Pinned Icons + Running Window Tabs
        val midScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val midRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        for (bApp in BuiltInApps.all.filter { it.pinnedOnTaskbar }) {
            val holder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = UiKit.rounded(this@MainActivity, Color.argb(22, 255, 255, 255), 9)
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(bApp.icon(this@MainActivity, dp(22)))
                }, LinearLayout.LayoutParams(dp(22), dp(22)))
                UiKit.attachPressBounce(this, 0.90f)
                setOnClickListener { launchBuiltInByKey(bApp.key) }
            }
            midRow.addView(holder, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(4) })
        }

        for (pkg in prefs.getPinnedPackages()) {
            val app = appByPkg[pkg] ?: continue
            val holder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = UiKit.rounded(this@MainActivity, Color.argb(20, 255, 255, 255), 9)
                addView(ImageView(this@MainActivity).apply {
                    if (app.iconBitmap != null) setImageBitmap(app.iconBitmap)
                }, LinearLayout.LayoutParams(dp(22), dp(22)))
                UiKit.attachPressBounce(this, 0.90f)
                setOnClickListener { launchAndroidApp(app) }
                setOnLongClickListener { showAndroidAppContextMenu(app, -1f, -1f); true }
            }
            midRow.addView(holder, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(4) })
        }

        val openWins = windowManager.openWindows(currentWorkspaceOnly = true)
        if (openWins.isNotEmpty()) {
            midRow.addView(View(this).apply {
                setBackgroundColor(Color.argb(55, 255, 255, 255))
            }, LinearLayout.LayoutParams(dp(1), dp(20)).apply { leftMargin = dp(3); rightMargin = dp(5) })
        }

        for (win in openWins) {
            val isFocused = (win.id == windowManager.activeWindowId && !win.minimized)
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(9), 0)
                background = UiKit.strokeRounded(
                    this@MainActivity,
                    if (isFocused) Color.argb(95, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
                    else Color.argb(28, 255, 255, 255),
                    if (isFocused) theme.accent else Color.argb(42, 255, 255, 255),
                    9
                )
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(VectorIcons.get(this@MainActivity, win.iconId, dp(16)))
                }, LinearLayout.LayoutParams(dp(15), dp(15)).apply { rightMargin = dp(5) })

                val shortTitle = if (win.title.length > 13) win.title.take(12) + "…" else win.title
                addView(UiKit.label(this@MainActivity, shortTitle, 10.5f, if (win.minimized) UiKit.MUTED else UiKit.INK, bold = true))

                UiKit.attachPressBounce(this, 0.94f)
                setOnClickListener { windowManager.toggleTaskbarWindow(win.id) }
                setOnLongClickListener {
                    overlays.showContextMenu(
                        title = win.title,
                        items = listOf(
                            "Fokus / Tampilkan" to { windowManager.focusWindow(win.id) },
                            "Minimize" to { windowManager.minimizeWindow(win.id) },
                            "Maximize / Restore" to { windowManager.toggleMaximize(win) },
                            "Pindah ke Desktop 1" to { windowManager.moveWindowToWorkspace(win.id, 0) },
                            "Pindah ke Desktop 2" to { windowManager.moveWindowToWorkspace(win.id, 1) },
                            "Pindah ke Desktop 3" to { windowManager.moveWindowToWorkspace(win.id, 2) },
                            "Tutup Jendela" to { windowManager.closeWindow(win.id) }
                        )
                    )
                    true
                }
            }
            midRow.addView(tab, LinearLayout.LayoutParams(-2, dp(32)).apply { rightMargin = dp(4) })
        }

        midScroll.addView(midRow)
        bar.addView(midScroll, LinearLayout.LayoutParams(0, dp(36), 1f))

        // Right System Tray (Vector Wifi + Battery % + Clock)
        val (bat, chg) = batteryInfo()
        val now = Date()
        val tray = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(this@MainActivity, Color.argb(36, 215, 229, 255), 10)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            UiKit.attachPressBounce(this, 0.94f)
            setOnClickListener { showQuickSettingsOverlay() }
        }
        tray.addView(ImageView(this).apply {
            setImageDrawable(VectorIcons.get(this@MainActivity, "tray_status", dp(16)))
        }, LinearLayout.LayoutParams(dp(15), dp(15)).apply { rightMargin = dp(4) })

        tray.addView(
            UiKit.label(this, "${if (chg) "+" else ""}$bat%", 10f, theme.accent, bold = true),
            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(7) }
        )

        val clockCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clockCol.addView(UiKit.label(this, SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 10.2f, UiKit.INK, bold = true))
        clockCol.addView(UiKit.label(this, SimpleDateFormat("dd/MM", Locale.getDefault()).format(now), 8.5f, UiKit.MUTED))
        tray.addView(clockCol)
        bar.addView(tray, LinearLayout.LayoutParams(-2, dp(34)).apply { leftMargin = dp(5) })

        // Show Desktop Sliver
        bar.addView(View(this).apply {
            background = UiKit.rounded(this@MainActivity, Color.argb(75, 255, 255, 255), 3)
            setOnClickListener { windowManager.minimizeAll() }
        }, LinearLayout.LayoutParams(dp(5), dp(28)).apply { leftMargin = dp(5) })

        taskbarHost.addView(bar, FrameLayout.LayoutParams(-1, -1))
    }

    // ==================== CUSTOM OVERLAY START MENU & QUICK CENTER (ZERO DIALOG) ====================
    private fun showStartMenuOverlay(focusSearch: Boolean) {
        val theme = prefs.currentTheme
        val dm = resources.displayMetrics

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(20).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiKit.gradientRounded(this@MainActivity, theme.panelColors, Color.argb(90, 255, 255, 255), 18)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageDrawable(VectorIcons.get(this@MainActivity, "start_orb", dp(22)))
        }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) })
        header.addView(UiKit.label(this, if (isWideDesktop()) "HyperDeX Start" else "Start", 14.2f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(UiKit.chipButton(this, "Shield") {
            overlays.dismissOverlay(); openNativeHub()
        }, LinearLayout.LayoutParams(-2, dp(25)).apply { rightMargin = dp(4) })
        header.addView(UiKit.chipButton(this, "Shizuku") {
            overlays.dismissOverlay(); openShizukuHub()
        }, LinearLayout.LayoutParams(-2, dp(25)).apply { rightMargin = dp(4) })
        header.addView(UiKit.chipButton(this, "Settings") {
            overlays.dismissOverlay(); openControlPanel()
        }, LinearLayout.LayoutParams(-2, dp(25)))
        panel.addView(header)

        val searchBox = EditText(this).apply {
            hint = "Cari aplikasi PC, .EXE, atau aplikasi terpasang…"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(this@MainActivity, 12f))
            setSingleLine(true)
            background = UiKit.rounded(this@MainActivity, Color.argb(36, 215, 229, 255), 11)
            setPadding(dp(12), 0, dp(12), 0)
        }
        panel.addView(searchBox, LinearLayout.LayoutParams(-1, dp(34)).apply {
            topMargin = dp(7); bottomMargin = dp(7)
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val gridHolder = FrameLayout(this)
        scroll.addView(gridHolder)
        val availStageH = overlayStage.height.takeIf { it > 0 } ?: (dm.heightPixels - dp(48))
        val menuH = (availStageH - dp(104)).coerceIn(dp(85), dp(300))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, menuH))

        val cols = if (isWideDesktop()) 5 else 4

        fun populate(q: String) {
            gridHolder.removeAllViews()
            val tiles = mutableListOf<View>()

            BuiltInApps.all.filter { it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true) }
                .forEach { bApp ->
                    tiles.add(
                        desktopTileView(bApp.title, bApp.icon(this, dp(36)), 36,
                            onClick = { overlays.dismissOverlay(); launchBuiltInByKey(bApp.key) },
                            onLongClick = { _, _ -> overlays.dismissOverlay(); launchBuiltInByKey(bApp.key) })
                    )
                }

            installedApps.filter { it.label.contains(q, ignoreCase = true) }
                .forEach { app ->
                    val drw: Drawable = if (app.iconBitmap != null) BitmapDrawable(resources, app.iconBitmap)
                    else VectorIcons.get(this, "explorer", dp(36))
                    tiles.add(
                        desktopTileView(app.label, drw, 36,
                            onClick = { overlays.dismissOverlay(); launchAndroidApp(app) },
                            onLongClick = { rx, ry -> showAndroidAppContextMenu(app, rx, ry) })
                    )
                }

            gridHolder.addView(UiKit.buildGrid(this, cols, 76, tiles))
        }
        populate("")

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = populate(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val w = if (isWideDesktop()) min(dp(460), (dm.widthPixels * 0.65f).toInt()) else (dm.widthPixels * 0.94f).toInt()
        val grav = if (prefs.centerTaskbar && isWideDesktop()) (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL) else (Gravity.BOTTOM or Gravity.START)
        overlays.showAnchoredPanel(panel, widthPx = w, gravity = grav, marginX = dp(10), marginY = dp(6))
        if (focusSearch) searchBox.requestFocus()
    }

    private fun showQuickSettingsOverlay() {
        val theme = prefs.currentTheme
        val dm = resources.displayMetrics

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(20).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiKit.gradientRounded(this@MainActivity, theme.panelColors, Color.argb(90, 255, 255, 255), 18)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(UiKit.label(this, "Quick Action Center", 14f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val (bat, chg) = batteryInfo()
        head.addView(UiKit.label(this, "${if (chg) "Charging " else ""}$bat%", 11.5f, theme.accent, bold = true))
        card.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        fun qTile(title: String, sub: String, active: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(5), dp(9), dp(5))
            val bg = if (active) Color.argb(100, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
            else Color.argb(30, 215, 229, 255)
            background = UiKit.strokeRounded(this@MainActivity, bg, Color.argb(if (active) 140 else 40, 255, 255, 255), 11)
            UiKit.attachPressBounce(this, 0.94f)
            setOnClickListener { action() }
            addView(UiKit.label(this@MainActivity, title, 11f, UiKit.INK, bold = true))
            addView(UiKit.label(this@MainActivity, sub, 9.2f, if (active) UiKit.INK else UiKit.MUTED))
        }

        val hudActive = prefs.systemOverlayEnabled && SystemFloatingOverlay.canDraw(this)
        val tiles = listOf(
            qTile("Wi-Fi", "Jaringan", true) { overlays.dismissOverlay(); openSystemAction(Settings.ACTION_WIFI_SETTINGS) },
            qTile("Bluetooth", "Perangkat", false) { overlays.dismissOverlay(); openSystemAction(Settings.ACTION_BLUETOOTH_SETTINGS) },
            qTile("Mode PC", if (prefs.landscapeLock) "Landscape" else "Auto", prefs.landscapeLock) {
                prefs.landscapeLock = !prefs.landscapeLock
                overlays.dismissOverlay()
                refreshDesktopUI()
            },
            qTile("Full Window", if (prefs.immersiveMode) "Immersive" else "Standar", prefs.immersiveMode) {
                prefs.immersiveMode = !prefs.immersiveMode
                overlays.dismissOverlay()
                refreshDesktopUI()
            },
            qTile("Floating HUD", if (hudActive) "Aktif" else "Mati", hudActive) {
                overlays.dismissOverlay()
                if (!SystemFloatingOverlay.canDraw(this)) {
                    SystemFloatingOverlay.requestOverlayPermission(this)
                } else {
                    prefs.systemOverlayEnabled = !prefs.systemOverlayEnabled
                    if (prefs.systemOverlayEnabled) SystemFloatingOverlay.show(this, prefs) else SystemFloatingOverlay.hide()
                    showToast("Floating PC HUD", if (prefs.systemOverlayEnabled) "Aktif di atas semua app" else "Dinonaktifkan")
                }
            },
            qTile("Tema", theme.name, false) {
                prefs.cycleTheme()
                refreshDesktopUI()
                showQuickSettingsOverlay()
            }
        )
        card.addView(UiKit.buildGrid(this, 3, 46, tiles), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        // Custom Fluent Volume Slider (Zero Stock Android SeekBar!)
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxV = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curV = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxV)
            val volRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.rounded(this@MainActivity, Color.argb(26, 255, 255, 255), 11)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            volRow.addView(UiKit.label(this, "Vol", 10.5f, UiKit.INK, bold = true))
            volRow.addView(
                UiKit.buildFluentSlider(this, 0, maxV, curV, theme.accent) { v ->
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                },
                LinearLayout.LayoutParams(0, dp(26), 1f)
            )
            card.addView(volRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        } catch (_: Throwable) {
        }

        card.addView(
            UiKit.label(this, "${memorySummary()}  ·  Skala UI: ${prefs.uiScalePercent}%", 10f, UiKit.MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        )

        val foot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        foot.addView(UiKit.chipButton(this, "Shizuku & Termux") {
            overlays.dismissOverlay(); openShizukuHub()
        }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { rightMargin = dp(5) })
        foot.addView(UiKit.chipButton(this, "Control Panel") {
            overlays.dismissOverlay(); openControlPanel()
        }, LinearLayout.LayoutParams(0, dp(30), 1f))
        card.addView(foot)

        val availStageH = overlayStage.height.takeIf { it > 0 } ?: (dm.heightPixels - dp(48))
        val finalPanel: View = if (availStageH < dp(325)) {
            ScrollView(this).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                background = UiKit.gradientRounded(this@MainActivity, theme.panelColors, Color.argb(90, 255, 255, 255), 18)
                card.background = null
                addView(card)
            }
        } else card
        val hCap = if (availStageH < dp(325)) (availStageH - dp(12)).coerceAtLeast(dp(140)) else FrameLayout.LayoutParams.WRAP_CONTENT
        val w = if (isWideDesktop()) min(dp(345), (dm.widthPixels * 0.85f).toInt()) else (dm.widthPixels * 0.94f).toInt()
        overlays.showAnchoredPanel(finalPanel, widthPx = w, heightPx = hCap, gravity = Gravity.BOTTOM or Gravity.END, marginX = dp(10), marginY = dp(6))
    }

    // ==================== FLOATING WINDOW LAUNCHERS ====================
    private fun launchBuiltInByKey(key: String) {
        when (key) {
            "explorer" -> openExplorer(null)
            "native_hub" -> openNativeHub()
            "shizuku_hub" -> openShizukuHub()
            "exe_center" -> openExeCenter(null)
            "browser" -> openBrowser(null)
            "notepad" -> openNotepad(null)
            "terminal" -> openTerminal(null)
            "calculator" -> openCalculator()
            "paint" -> openPaint(null)
            "media" -> openMediaPlayer(null)
            "taskmgr" -> openTaskManager()
            "control" -> openControlPanel()
        }
    }

    override fun openExplorer(startDir: File?) {
        windowManager.openWindow("explorer", "File Explorer", "explorer", singleInstance = false, preferredWidthDp = 540, preferredHeightDp = 350) {
            ExplorerAndNotepad.buildExplorerView(this, this, it, startDir)
        }
    }

    override fun openNativeHub() {
        windowManager.openWindow("native_hub", "HyperShield & Native Core (Rust / C11 / C++20)", "native_hub", singleInstance = true, preferredWidthDp = 560, preferredHeightDp = 375) {
            NativeCore.buildNativeHubView(this, prefs, this)
        }
    }

    override fun openShizukuHub() {
        windowManager.openWindow("shizuku_hub", "Shizuku, Termux & Floating HUD", "shizuku_hub", singleInstance = true, preferredWidthDp = 550, preferredHeightDp = 370) {
            SystemFloatingOverlay.buildShizukuTermuxHubView(this, prefs, this)
        }
    }

    override fun openNotepad(file: File?) {
        windowManager.openWindow("notepad", "Notepad", "notepad", singleInstance = false, preferredWidthDp = 490, preferredHeightDp = 335) {
            ExplorerAndNotepad.buildNotepadView(this, this, it, file)
        }
    }

    override fun openBrowser(initialUrl: String?) {
        windowManager.openWindow("browser", "Desktop Browser", "browser", singleInstance = false, preferredWidthDp = 600, preferredHeightDp = 380) {
            BrowserAndMedia.buildBrowserView(this, it, initialUrl)
        }
    }

    override fun openTerminal(initialCommand: String?) {
        windowManager.openWindow("terminal", "Terminal CMD & Shizuku", "terminal", singleInstance = false, preferredWidthDp = 520, preferredHeightDp = 335) {
            ToolsApps.buildTerminalView(this, this, windowManager, initialCommand)
        }
    }

    override fun openCalculator() {
        windowManager.openWindow("calculator", "Calculator", "calculator", singleInstance = true, preferredWidthDp = 310, preferredHeightDp = 345) {
            ToolsApps.buildCalculatorView(this)
        }
    }

    override fun openPaint(imageFile: File?) {
        windowManager.openWindow("paint", "Paint Studio", "paint", singleInstance = false, preferredWidthDp = 530, preferredHeightDp = 350) {
            BrowserAndMedia.buildPaintView(this, this, it, imageFile)
        }
    }

    override fun openMediaPlayer(audioFile: File?) {
        windowManager.openWindow("media", "Media Player", "media", singleInstance = true, preferredWidthDp = 430, preferredHeightDp = 300) {
            BrowserAndMedia.buildMediaPlayerView(this, this, it, audioFile)
        }
    }

    override fun openTaskManager() {
        windowManager.openWindow("taskmgr", "Task Manager", "taskmgr", singleInstance = true, preferredWidthDp = 470, preferredHeightDp = 340) {
            ToolsApps.buildTaskManagerView(this, it, windowManager)
        }
    }

    override fun openControlPanel() {
        windowManager.openWindow("control", "Control Panel", "control", singleInstance = true, preferredWidthDp = 520, preferredHeightDp = 365) {
            ToolsApps.buildControlPanelView(this, prefs, this)
        }
    }

    override fun openExeCenter(targetExe: File?) {
        if (targetExe != null) {
            ExeEmulatorApp.launchExeInWindow(this, prefs, this, windowManager, targetExe)
            return
        }
        windowManager.openWindow("exe_center", "EXE & Wine Plugin Center", "exe_center", singleInstance = true, preferredWidthDp = 540, preferredHeightDp = 365) {
            ExeEmulatorApp.buildExeCenterView(this, prefs, this, windowManager)
        }
    }

    override fun openExeFile(file: File) {
        ExeEmulatorApp.launchExeInWindow(this, prefs, this, windowManager, file)
    }

    override fun openBatchScript(file: File) {
        val script = try { file.readText() } catch (_: Throwable) { "echo Gagal membaca script" }
        openTerminal(script)
    }

    override fun openImageFile(file: File) = openPaint(file)
    override fun openAudioFile(file: File) = openMediaPlayer(file)

    private fun openFolderFloatingWindow(folder: DesktopFolder) {
        windowManager.openWindow(
            appKey = "folder_${folder.id}",
            title = "Folder — ${folder.name}",
            iconId = "folder",
            singleInstance = true,
            preferredWidthDp = 450,
            preferredHeightDp = 310
        ) { state ->
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(UiKit.label(this, "${folder.packages.size} item", 11f, UiKit.MUTED),
                LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(UiKit.chipButton(this, "Kelola Isi Folder") {
                windowManager.closeWindow(state.id)
                showCreateOrEditFolderWindow(folder)
            }, LinearLayout.LayoutParams(-2, dp(26)))
            wrap.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

            val apps = folder.packages.mapNotNull { appByPkg[it] }
            if (apps.isEmpty()) {
                wrap.addView(UiKit.label(this, "Folder ini kosong. Klik 'Kelola Isi Folder' untuk memilih aplikasi.", 11.5f, UiKit.MUTED, maxLines = 3))
            } else {
                val tiles = apps.map { a ->
                    val drw: Drawable = if (a.iconBitmap != null) BitmapDrawable(resources, a.iconBitmap)
                    else VectorIcons.get(this, "explorer", dp(36))
                    desktopTileView(a.label, drw, 36,
                        onClick = { launchAndroidApp(a) },
                        onLongClick = { rx, ry -> showAndroidAppContextMenu(a, rx, ry) })
                }
                val sc = ScrollView(this).apply { isFillViewport = true }
                sc.addView(UiKit.buildGrid(this, 4, 78, tiles))
                wrap.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            wrap
        }
    }

    // ==================== EXTERNAL ANDROID APP LAUNCH (SHIZUKU + FREEFORM) ====================
    private fun launchAndroidApp(app: AndroidAppEntry, preferShizukuWindow: Boolean = true) {
        if (prefs.systemOverlayEnabled && SystemFloatingOverlay.canDraw(this)) {
            SystemFloatingOverlay.show(this, prefs)
        }

        val standardLaunch = {
            try {
                val dm = resources.displayMetrics
                val rect = Rect(
                    (dm.widthPixels * 0.10f).toInt(),
                    (dm.heightPixels * 0.08f).toInt(),
                    (dm.widthPixels * 0.90f).toInt(),
                    (dm.heightPixels * 0.88f).toInt()
                )
                val opts = ActivityOptions.makeBasic().setLaunchBounds(rect)
                try {
                    val m = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    m.invoke(opts, 5)
                } catch (_: Throwable) {
                }
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(app.packageName, app.activityName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                startActivity(intent, opts.toBundle())
            } catch (_: Throwable) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(app.packageName, app.activityName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Throwable) {
                    showToast("Launcher", "Aplikasi tidak dapat dibuka")
                }
            }
        }

        if (preferShizukuWindow && ShizukuBridge.hasPermission()) {
            ShizukuBridge.launchAppFreeformViaShizuku(app.packageName, app.activityName) {
                runOnUiThread { standardLaunch() }
            }
        } else {
            standardLaunch()
        }
    }

    private fun showAndroidAppContextMenu(app: AndroidAppEntry, rawX: Float, rawY: Float) {
        val pkg = app.packageName
        val pinned = prefs.getPinnedPackages()
        val desk = prefs.getDesktopPackages()
        val isPinned = pinned.contains(pkg)
        val isDesk = desk.contains(pkg)

        overlays.showContextMenu(
            title = app.label,
            rawX = rawX,
            rawY = rawY,
            items = listOf(
                "Buka Freeform Window (Shizuku/PC)" to { launchAndroidApp(app, preferShizukuWindow = true) },
                "Buka Layar Penuh Biasa" to { launchAndroidApp(app, preferShizukuWindow = false) },
                (if (isPinned) "Lepas Pin dari Taskbar" else "Pin ke Taskbar") to {
                    if (isPinned) pinned.remove(pkg) else pinned.add(pkg)
                    prefs.savePinnedPackages(pinned)
                    renderTaskbar()
                },
                (if (isDesk) "Hapus Shortcut Desktop" else "Tambah Shortcut ke Desktop") to {
                    if (isDesk) desk.remove(pkg) else desk.add(pkg)
                    prefs.saveDesktopPackages(desk)
                    renderDesktopSurface()
                },
                "Masukkan ke Folder Desktop…" to {
                    val folders = prefs.getFolders()
                    val fItems = folders.map { f ->
                        f.name to {
                            if (!f.packages.contains(pkg)) f.packages.add(pkg)
                            prefs.saveFolders(folders)
                            renderDesktopSurface()
                            showToast("Folder", "Ditambahkan ke ${f.name}")
                        }
                    } + ("+ Buat Folder Baru" to {
                        val nf = DesktopFolder(UUID.randomUUID().toString(), "Folder Baru", mutableListOf(pkg))
                        folders.add(nf)
                        prefs.saveFolders(folders)
                        renderDesktopSurface()
                        showCreateOrEditFolderWindow(nf)
                    })
                    overlays.showContextMenu("Pilih Folder Tujuan", fItems)
                },
                "Info Aplikasi Sistem" to {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", pkg, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Throwable) {}
                },
                "Uninstall Aplikasi" to {
                    try {
                        startActivity(
                            Intent(Intent.ACTION_DELETE)
                                .setData(Uri.fromParts("package", pkg, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Throwable) {}
                }
            )
        )
    }

    private fun showDesktopShortcutPickerWindow() {
        if (installedApps.isEmpty()) {
            showToast("Desktop", "Daftar aplikasi sedang dimuat…")
            return
        }
        overlays.showMultiPicker(
            title = "Pilih Shortcut Aplikasi di Desktop",
            items = installedApps.map { it.packageName to it.label },
            initialChecked = prefs.getDesktopPackages().toSet(),
            onSave = { selected, _ ->
                prefs.saveDesktopPackages(selected.toList())
                renderDesktopSurface()
            }
        )
    }

    private fun showCreateOrEditFolderWindow(existing: DesktopFolder?) {
        overlays.showMultiPicker(
            title = if (existing == null) "Buat Folder Desktop Baru" else "Kelola Folder Desktop",
            items = installedApps.map { it.packageName to it.label },
            initialChecked = (existing?.packages ?: emptyList()).toSet(),
            editableFolderName = existing?.name ?: "Folder Baru",
            dangerButton = existing?.let {
                "Hapus Folder" to {
                    prefs.saveFolders(prefs.getFolders().filterNot { f -> f.id == it.id })
                    renderDesktopSurface()
                }
            },
            onSave = { selected, folderName ->
                val all = prefs.getFolders()
                if (existing == null) {
                    all.add(DesktopFolder(UUID.randomUUID().toString(), folderName, selected.toMutableList()))
                } else {
                    all.find { it.id == existing.id }?.let {
                        it.name = folderName
                        it.packages.clear()
                        it.packages.addAll(selected)
                    }
                }
                prefs.saveFolders(all)
                renderDesktopSurface()
            }
        )
    }

    // ==================== FILE & MEDIA PICKERS ====================
    override fun pickExternalExeFile() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_PICK_EXE)
        } catch (t: Throwable) {
            showToast("File Picker", "Tidak tersedia: ${t.message}")
        }
    }

    override fun pickCustomWallpaper() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_PICK_WALLPAPER)
        } catch (t: Throwable) {
            showToast("Galeri", "Tidak tersedia: ${t.message}")
        }
    }

    override fun pickAudioFromStorage() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), REQ_PICK_AUDIO)
        } catch (t: Throwable) {
            showToast("Audio Picker", "Tidak tersedia: ${t.message}")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } catch (_: Throwable) { null } finally { cursor?.close() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_PICK_EXE -> {
                try {
                    val rawName = queryDisplayName(uri) ?: "Program.exe"
                    val safeName = if (rawName.contains('.')) rawName else "$rawName.exe"
                    val dest = File(VirtualDrive.installersDir(this), safeName)
                    contentResolver.openInputStream(uri)?.use { inp -> FileOutputStream(dest).use { inp.copyTo(it) } }
                    val entry = ExeEmulatorApp.installExeToProgramFiles(this, prefs, dest)
                    refreshDesktopUI()
                    showToast("EXE Terpasang", entry.title)
                    ExeEmulatorApp.launchExeInWindow(this, prefs, this, windowManager, File(entry.exePath))
                } catch (t: Throwable) {
                    showToast("Import Gagal", t.message ?: "Error")
                }
            }
            REQ_PICK_WALLPAPER -> {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1920) sample *= 2
                    val bmp = contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                    if (bmp != null) {
                        FileOutputStream(prefs.customWallpaperFile()).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        prefs.useCustomWallpaper = true
                        refreshDesktopUI()
                        showToast("Wallpaper", "Wallpaper desktop diperbarui!")
                    }
                } catch (t: Throwable) {
                    showToast("Wallpaper", "Gagal memuat gambar")
                }
            }
            REQ_PICK_AUDIO -> {
                try {
                    val name = queryDisplayName(uri) ?: "Track.mp3"
                    val dest = File(VirtualDrive.downloadsDir(this), name)
                    contentResolver.openInputStream(uri)?.use { inp -> FileOutputStream(dest).use { inp.copyTo(it) } }
                    windowManager.openWindows().find { it.appKey == "media" }?.let { windowManager.closeWindow(it.id) }
                    openMediaPlayer(dest)
                } catch (_: Throwable) {
                    showToast("Media Player", "Gagal memuat audio")
                }
            }
        }
    }

    private fun openSystemAction(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            showToast("Sistem", "Menu pengaturan tidak ditemukan")
        }
    }
}
