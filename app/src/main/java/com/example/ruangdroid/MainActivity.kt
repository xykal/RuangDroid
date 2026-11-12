package com.example.ruangdroid

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.Dialog
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
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

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
    private lateinit var wallpaperView: ImageView
    private lateinit var desktopSurfaceHost: FrameLayout
    private lateinit var windowStage: FrameLayout
    private lateinit var taskbarHost: FrameLayout
    private lateinit var windowManager: FloatingWindowManager

    private val installedApps = mutableListOf<AndroidAppEntry>()
    private val appByPkg = mutableMapOf<String, AndroidAppEntry>()
    private val scanExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var scanningApps = false

    private fun dp(v: Int): Int = UiKit.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DesktopPrefs(this)
        try {
            VirtualDrive.ensureInitialized(this)
        } catch (_: Throwable) {
        }

        // Build persistent 3-layer PC Desktop Compositor ONCE so open windows stay alive
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 20, 36))
        }

        val stageStack = FrameLayout(this)

        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        stageStack.addView(wallpaperView, FrameLayout.LayoutParams(-1, -1))

        desktopSurfaceHost = FrameLayout(this)
        stageStack.addView(desktopSurfaceHost, FrameLayout.LayoutParams(-1, -1))

        windowStage = FrameLayout(this)
        stageStack.addView(windowStage, FrameLayout.LayoutParams(-1, -1))

        rootLayout.addView(stageStack, LinearLayout.LayoutParams(-1, 0, 1f))

        taskbarHost = FrameLayout(this).apply {
            setPadding(dp(10), dp(2), dp(10), dp(6))
        }
        rootLayout.addView(taskbarHost, LinearLayout.LayoutParams(-1, dp(58)))

        windowManager = FloatingWindowManager(
            context = this,
            stage = windowStage,
            prefs = prefs,
            onWindowsChanged = { renderTaskbar() }
        )

        setContentView(rootLayout)

        // Apply orientation & system bars safely AFTER setContentView
        applyOrientationAndBarsSafe()
        refreshDesktopUI()
        triggerAsyncAppScan()
    }

    override fun onResume() {
        super.onResume()
        applyOrientationAndBarsSafe()
        renderTaskbar()
        triggerAsyncAppScan()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshDesktopUI()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val activeId = windowManager.activeWindowId
        if (activeId != null) {
            windowManager.minimizeWindow(activeId)
        }
    }

    private var wasImmersiveApplied = false

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
            window.statusBarColor = prefs.currentTheme.bgColors.first()
            window.navigationBarColor = Color.rgb(10, 15, 28)
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
        val iconPx = dp(48).coerceIn(48, 144)
        scanExecutor.execute {
            try {
                val pm = packageManager
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                val rawList = try {
                    pm.queryIntentActivities(query, 0)
                } catch (_: Throwable) {
                    emptyList()
                }

                val parsed = mutableListOf<AndroidAppEntry>()
                val seen = HashSet<String>()
                for (ri in rawList) {
                    val ai = ri?.activityInfo ?: continue
                    val pkg = ai.packageName ?: continue
                    val cls = ai.name ?: continue
                    if (pkg == packageName || !seen.add(pkg)) continue

                    val lbl = try {
                        ri.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: pkg
                    } catch (_: Throwable) {
                        pkg
                    }
                    val bmp = try {
                        UiKit.safeDrawableToBitmap(ri.loadIcon(pm), iconPx)
                    } catch (_: Throwable) {
                        null
                    }
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

    private fun isPcLandscape(): Boolean {
        val cfg = resources.configuration
        return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE || cfg.screenWidthDp >= 600
    }

    override fun refreshDesktopUI() {
        applyOrientationAndBarsSafe()
        applyWallpaper()
        renderDesktopSurface()
        renderTaskbar()
        windowManager.refreshHeaderStyles()
    }

    private fun applyWallpaper() {
        val theme = prefs.currentTheme
        if (prefs.useCustomWallpaper) {
            val f = prefs.customWallpaperFile()
            val bmp = try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Throwable) { null }
            if (bmp != null) {
                wallpaperView.setImageBitmap(bmp)
                return
            }
        }
        wallpaperView.setImageDrawable(
            GradientDrawable(GradientDrawable.Orientation.TL_BR, theme.bgColors)
        )
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        return try {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            pct to bm.isCharging
        } catch (_: Throwable) {
            100 to false
        }
    }

    private fun memorySummary(): String {
        return try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val used = (mi.totalMem - mi.availMem) / 1073741824.0
            val tot = mi.totalMem / 1073741824.0
            String.format(Locale.US, "RAM %.1f / %.1f GB", used, tot)
        } catch (_: Throwable) {
            "RAM aktif"
        }
    }

    private fun storageSummary(): String {
        return try {
            val st = StatFs(Environment.getDataDirectory().path)
            val free = st.availableBytes / 1073741824.0
            val tot = st.totalBytes / 1073741824.0
            String.format(Locale.US, "Disk %.0f GB kosong / %.0f GB", free, tot)
        } catch (_: Throwable) {
            "Storage aktif"
        }
    }

    // ==================== LAYER 1: DESKTOP ICONS & WIDGET ====================
    private fun renderDesktopSurface() {
        desktopSurfaceHost.removeAllViews()
        val theme = prefs.currentTheme
        val pcMode = isPcLandscape()
        val widthDp = resources.configuration.screenWidthDp

        val wrapper = LinearLayout(this).apply {
            orientation = if (pcMode) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }

        val leftDesktop = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Top quick desktop bar
        val topStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(6))
        }
        val statusTitle = if (prefs.landscapeLock) "🖥️ RuangDroid PC OS v2.0" else "📱 RuangDroid Hybrid v2.0"
        topStrip.addView(UiKit.label(this, statusTitle, 12.5f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, dp(28), 1f))

        topStrip.addView(UiKit.chipButton(this, "🍷 Install .EXE", Color.argb(85, 190, 24, 93)) {
            openExeCenter(null)
        }, LinearLayout.LayoutParams(-2, dp(28)).apply { rightMargin = dp(6) })

        topStrip.addView(UiKit.chipButton(this, "📁+ Folder") {
            showCreateOrEditFolderDialog(null)
        }, LinearLayout.LayoutParams(-2, dp(28)).apply { rightMargin = dp(6) })

        topStrip.addView(UiKit.chipButton(this, "➕ App Android") {
            showDesktopShortcutPicker()
        }, LinearLayout.LayoutParams(-2, dp(28)))

        leftDesktop.addView(topStrip)

        val scale = prefs.iconScale
        val baseCols = when {
            widthDp >= 860 -> 7
            pcMode -> 6
            else -> 4
        }
        val cols = (baseCols + (1 - scale)).coerceIn(3, 8)
        val cellHeightDp = when (scale) {
            0 -> 78
            2 -> 102
            else -> 88
        }
        val iconSizeDp = when (scale) {
            0 -> 34
            2 -> 48
            else -> 40
        }

        val allTiles = mutableListOf<View>()

        // 1. Built-in PC Apps
        for (bApp in BuiltInApps.all.filter { it.showOnDesktop }) {
            allTiles.add(
                desktopTileView(
                    label = bApp.title,
                    drawable = bApp.icon(this),
                    iconSizeDp = iconSizeDp,
                    onClick = { launchBuiltInByKey(bApp.key) },
                    onLongClick = { launchBuiltInByKey(bApp.key) }
                )
            )
        }

        // 2. Installed .EXE Programs on Desktop
        for (exe in prefs.getInstalledExes()) {
            val exeFile = File(exe.exePath)
            if (!exeFile.exists()) continue
            val exeIcon = UiKit.builtInIcon(
                this,
                "exe_item",
                Color.rgb(30, 64, 175),
                Color.rgb(109, 40, 217),
                if (exe.title.contains("Mine", ignoreCase = true)) "💣"
                else if (exe.title.contains("Arkanoid", ignoreCase = true)) "🧱"
                else "⚙️"
            )
            allTiles.add(
                desktopTileView(
                    label = exe.title,
                    drawable = exeIcon,
                    iconSizeDp = iconSizeDp,
                    onClick = { openExeFile(exeFile) },
                    onLongClick = {
                        AlertDialog.Builder(this)
                            .setTitle(exe.title)
                            .setItems(arrayOf("▶ Jalankan Window (.EXE)", "🔍 Analisis PE Header", "🗑️ Hapus dari Desktop")) { _, w ->
                                when (w) {
                                    0 -> openExeFile(exeFile)
                                    1 -> windowManager.openWindow("pe_${exe.id}", "PE Header — ${exe.title}", "🍷", singleInstance = true) {
                                        ExeEmulatorApp.buildPeInspectorView(this, prefs, this, exeFile)
                                    }
                                    2 -> {
                                        prefs.saveInstalledExes(prefs.getInstalledExes().filterNot { it.id == exe.id })
                                        refreshDesktopUI()
                                    }
                                }
                            }
                            .show()
                    }
                )
            )
        }

        // 3. Desktop Folders (open inside Floating Windows!)
        for (folder in prefs.getFolders()) {
            val count = folder.packages.count { appByPkg.containsKey(it) }
            val folderIcon = UiKit.builtInIcon(
                this,
                "folder",
                Color.rgb(234, 179, 8),
                Color.rgb(202, 138, 4),
                "📁"
            )
            allTiles.add(
                desktopTileView(
                    label = "${folder.name} ($count)",
                    drawable = folderIcon,
                    iconSizeDp = iconSizeDp,
                    onClick = { openFolderFloatingWindow(folder) },
                    onLongClick = { showCreateOrEditFolderDialog(folder) }
                )
            )
        }

        // 4. Android App Shortcuts on Desktop
        for (pkg in prefs.getDesktopPackages()) {
            val app = appByPkg[pkg] ?: continue
            val drw: Drawable = if (app.iconBitmap != null) {
                BitmapDrawable(resources, app.iconBitmap)
            } else {
                UiKit.builtInIcon(this, "apk", Color.rgb(59, 130, 246), Color.rgb(37, 99, 235), "🤖")
            }
            allTiles.add(
                desktopTileView(
                    label = app.label,
                    drawable = drw,
                    iconSizeDp = iconSizeDp,
                    onClick = { launchAndroidApp(app) },
                    onLongClick = { showAndroidAppContextMenu(app) }
                )
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(UiKit.buildGrid(this, cols, cellHeightDp, allTiles, hGapDp = 4, vGapDp = 4))
        leftDesktop.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        if (pcMode) {
            wrapper.addView(leftDesktop, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(10) })
            wrapper.addView(buildPcWidgetSidebar(theme), LinearLayout.LayoutParams(dp(205), -1))
        } else {
            wrapper.addView(leftDesktop, LinearLayout.LayoutParams(-1, -1))
        }

        desktopSurfaceHost.addView(wrapper, FrameLayout.LayoutParams(-1, -1))
    }

    private fun desktopTileView(
        label: String,
        drawable: Drawable,
        iconSizeDp: Int,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = UiKit.rounded(this@MainActivity, Color.argb(18, 255, 255, 255), 12)
            setPadding(dp(3), dp(4), dp(3), dp(3))
            setOnClickListener { onClick() }
            setOnLongClickListener {
                onLongClick()
                true
            }
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)))

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 10.5f
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
                Color.argb(95, 12, 19, 34),
                Color.argb(55, 255, 255, 255),
                18
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val now = Date()
        card.addView(UiKit.label(this, SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 30f, UiKit.INK, bold = true))
        card.addView(UiKit.label(this, SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(now), 11.5f, theme.accent, bold = true))

        val sep = View(this).apply { setBackgroundColor(Color.argb(35, 255, 255, 255)) }
        card.addView(sep, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(8); bottomMargin = dp(8)
        })

        val (bat, chg) = batteryInfo()
        card.addView(UiKit.label(this, "${if (chg) "⚡ Charging" else "🔋 Baterai"}: $bat%", 11f, UiKit.INK, bold = true))
        card.addView(UiKit.label(this, memorySummary(), 10.5f, UiKit.MUTED).apply { setPadding(0, dp(2), 0, 0) })
        card.addView(UiKit.label(this, storageSummary(), 10.5f, UiKit.MUTED).apply { setPadding(0, dp(2), 0, 0) })

        card.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        card.addView(UiKit.chipButton(this, "📊 Task Manager", Color.argb(55, 20, 184, 166)) {
            openTaskManager()
        }, LinearLayout.LayoutParams(-1, dp(32)).apply { bottomMargin = dp(6) })

        card.addView(UiKit.chipButton(
            this,
            "🎛️ Quick Panel",
            Color.argb(75, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
        ) {
            showQuickSettingsPanel()
        }, LinearLayout.LayoutParams(-1, dp(32)))

        return card
    }

    // ==================== LAYER 3: WINDOWS 11 TASKBAR ====================
    private fun renderTaskbar() {
        taskbarHost.removeAllViews()
        val theme = prefs.currentTheme

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.strokeRounded(
                this@MainActivity,
                theme.taskbarBg,
                Color.argb(55, 255, 255, 255),
                16
            )
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        // Start Button (⊞)
        val startBtn = TextView(this).apply {
            text = "⊞"
            textSize = 21f
            setTextColor(theme.accent)
            gravity = Gravity.CENTER
            background = UiKit.rounded(this@MainActivity, Color.argb(48, 131, 170, 255), 11)
            setOnClickListener { showStartMenu(focusSearch = false) }
        }
        bar.addView(startBtn, LinearLayout.LayoutParams(dp(40), dp(38)).apply { rightMargin = dp(5) })

        // Search Button
        val searchBtn = TextView(this).apply {
            text = if (isPcLandscape()) " 🔍 Cari" else "🔍"
            textSize = 11.5f
            setTextColor(UiKit.MUTED)
            gravity = Gravity.CENTER
            background = UiKit.rounded(this@MainActivity, Color.argb(34, 215, 229, 255), 11)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { showStartMenu(focusSearch = true) }
        }
        bar.addView(searchBtn, LinearLayout.LayoutParams(-2, dp(36)).apply { rightMargin = dp(8) })

        // Scrollable Middle Bar: Pinned Icons + Running Floating Window Tabs
        val midScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val midRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Pinned Built-in PC Apps
        for (bApp in BuiltInApps.all.filter { it.pinnedOnTaskbar }) {
            val holder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = UiKit.rounded(this@MainActivity, Color.argb(24, 255, 255, 255), 10)
                setOnClickListener { launchBuiltInByKey(bApp.key) }
            }
            holder.addView(ImageView(this).apply {
                setImageDrawable(bApp.icon(this@MainActivity))
            }, LinearLayout.LayoutParams(dp(26), dp(26)))
            midRow.addView(holder, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(4) })
        }

        // Pinned Android Apps
        for (pkg in prefs.getPinnedPackages()) {
            val app = appByPkg[pkg] ?: continue
            val holder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = UiKit.rounded(this@MainActivity, Color.argb(22, 255, 255, 255), 10)
                setOnClickListener { launchAndroidApp(app) }
                setOnLongClickListener { showAndroidAppContextMenu(app); true }
            }
            holder.addView(ImageView(this).apply {
                if (app.iconBitmap != null) setImageBitmap(app.iconBitmap)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            midRow.addView(holder, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(4) })
        }

        // Divider if windows are open
        val openWins = windowManager.openWindows()
        if (openWins.isNotEmpty()) {
            midRow.addView(View(this).apply {
                setBackgroundColor(Color.argb(55, 255, 255, 255))
            }, LinearLayout.LayoutParams(dp(1), dp(24)).apply {
                leftMargin = dp(4); rightMargin = dp(6)
            })
        }

        // Running Floating Windows Tabs!
        for (win in openWins) {
            val isFocused = (win.id == windowManager.activeWindowId && !win.minimized)
            val tab = TextView(this).apply {
                val shortTitle = if (win.title.length > 14) win.title.take(13) + "…" else win.title
                text = "${win.glyph} $shortTitle"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (win.minimized) UiKit.MUTED else UiKit.INK)
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                background = UiKit.strokeRounded(
                    this@MainActivity,
                    if (isFocused) Color.argb(95, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
                    else Color.argb(30, 255, 255, 255),
                    if (isFocused) theme.accent else Color.argb(45, 255, 255, 255),
                    10
                )
                setOnClickListener { windowManager.toggleTaskbarWindow(win.id) }
                setOnLongClickListener {
                    windowManager.closeWindow(win.id)
                    true
                }
            }
            midRow.addView(tab, LinearLayout.LayoutParams(-2, dp(34)).apply { rightMargin = dp(5) })
        }

        midScroll.addView(midRow)
        bar.addView(midScroll, LinearLayout.LayoutParams(0, dp(38), 1f))

        // System Tray -> Opens Bottom-Right Quick Settings Panel
        val (bat, chg) = batteryInfo()
        val now = Date()
        val tray = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(this@MainActivity, Color.argb(38, 215, 229, 255), 11)
            setPadding(dp(9), dp(3), dp(9), dp(3))
            setOnClickListener { showQuickSettingsPanel() }
        }
        tray.addView(
            UiKit.label(this, "📶 🔊 ${if (chg) "⚡" else "🔋"}$bat%", 10.5f, UiKit.INK),
            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(7) }
        )
        val clockStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clockStack.addView(UiKit.label(this, SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 10.5f, UiKit.INK, bold = true))
        clockStack.addView(UiKit.label(this, SimpleDateFormat("dd/MM", Locale.getDefault()).format(now), 9f, UiKit.MUTED))
        tray.addView(clockStack)
        bar.addView(tray, LinearLayout.LayoutParams(-2, dp(38)).apply { leftMargin = dp(6) })

        // Far-right Windows "Show Desktop" sliver button
        val showDesktopBtn = View(this).apply {
            background = UiKit.rounded(this@MainActivity, Color.argb(65, 255, 255, 255), 4)
            setOnClickListener { windowManager.minimizeAll() }
        }
        bar.addView(showDesktopBtn, LinearLayout.LayoutParams(dp(6), dp(32)).apply { leftMargin = dp(5) })

        taskbarHost.addView(bar, FrameLayout.LayoutParams(-1, -1))
    }

    // ==================== BUILT-IN FLOATING APP DISPATCHERS ====================
    private fun launchBuiltInByKey(key: String) {
        when (key) {
            "explorer" -> openExplorer(null)
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
        windowManager.openWindow("explorer", "File Explorer", "🗂️", singleInstance = false, preferredWidthDp = 560, preferredHeightDp = 360) {
            ExplorerAndNotepad.buildExplorerView(this, this, it, startDir)
        }
    }

    override fun openNotepad(file: File?) {
        windowManager.openWindow("notepad", "Notepad", "📝", singleInstance = false, preferredWidthDp = 500, preferredHeightDp = 340) {
            ExplorerAndNotepad.buildNotepadView(this, it, file)
        }
    }

    override fun openBrowser(initialUrl: String?) {
        windowManager.openWindow("browser", "Desktop Browser", "🌐", singleInstance = false, preferredWidthDp = 620, preferredHeightDp = 390) {
            BrowserAndMedia.buildBrowserView(this, it, initialUrl)
        }
    }

    override fun openTerminal(initialCommand: String?) {
        windowManager.openWindow("terminal", "Command Prompt", ">_", singleInstance = false, preferredWidthDp = 530, preferredHeightDp = 340) {
            ToolsApps.buildTerminalView(this, this, windowManager, initialCommand)
        }
    }

    override fun openCalculator() {
        windowManager.openWindow("calculator", "Calculator", "🧮", singleInstance = true, preferredWidthDp = 320, preferredHeightDp = 360) {
            ToolsApps.buildCalculatorView(this)
        }
    }

    override fun openPaint(imageFile: File?) {
        windowManager.openWindow("paint", "Paint Studio", "🎨", singleInstance = false, preferredWidthDp = 540, preferredHeightDp = 360) {
            BrowserAndMedia.buildPaintView(this, it, imageFile)
        }
    }

    override fun openMediaPlayer(audioFile: File?) {
        windowManager.openWindow("media", "Media Player", "🎵", singleInstance = true, preferredWidthDp = 440, preferredHeightDp = 310) {
            BrowserAndMedia.buildMediaPlayerView(this, this, it, audioFile)
        }
    }

    override fun openTaskManager() {
        windowManager.openWindow("taskmgr", "Task Manager", "📊", singleInstance = true, preferredWidthDp = 480, preferredHeightDp = 350) {
            ToolsApps.buildTaskManagerView(this, it, windowManager)
        }
    }

    override fun openControlPanel() {
        windowManager.openWindow("control", "Control Panel", "⚙️", singleInstance = true, preferredWidthDp = 520, preferredHeightDp = 370) {
            ToolsApps.buildControlPanelView(this, prefs, this)
        }
    }

    override fun openExeCenter(targetExe: File?) {
        if (targetExe != null) {
            ExeEmulatorApp.launchExeInWindow(this, prefs, this, windowManager, targetExe)
            return
        }
        windowManager.openWindow("exe_center", "EXE & Wine Plugin Center", "🍷", singleInstance = true, preferredWidthDp = 560, preferredHeightDp = 380) {
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
            glyph = "📁",
            singleInstance = true,
            preferredWidthDp = 460,
            preferredHeightDp = 320
        ) { state ->
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(UiKit.label(this, "${folder.packages.size} item terdaftar", 11.5f, UiKit.MUTED),
                LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(UiKit.chipButton(this, "✏️ Kelola Isi Folder") {
                windowManager.closeWindow(state.id)
                showCreateOrEditFolderDialog(folder)
            }, LinearLayout.LayoutParams(-2, dp(28)))
            wrap.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

            val apps = folder.packages.mapNotNull { appByPkg[it] }
            if (apps.isEmpty()) {
                wrap.addView(UiKit.label(this, "Folder ini kosong. Klik '✏️ Kelola Isi Folder' untuk menambah aplikasi.", 12f, UiKit.MUTED, maxLines = 3))
            } else {
                val tiles = apps.map { a ->
                    val drw: Drawable = if (a.iconBitmap != null) BitmapDrawable(resources, a.iconBitmap)
                    else UiKit.builtInIcon(this, "apk", Color.rgb(59, 130, 246), Color.rgb(37, 99, 235), "🤖")
                    desktopTileView(a.label, drw, 38, onClick = { launchAndroidApp(a) }, onLongClick = { showAndroidAppContextMenu(a) })
                }
                val sc = ScrollView(this).apply { isFillViewport = true }
                sc.addView(UiKit.buildGrid(this, 4, 82, tiles))
                wrap.addView(sc, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            wrap
        }
    }

    // ==================== STORAGE PICKERS (EXE, WALLPAPER, AUDIO) ====================
    override fun pickExternalExeFile() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PICK_EXE)
        } catch (t: Throwable) {
            Toast.makeText(this, "File picker tidak tersedia: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun pickCustomWallpaper() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PICK_WALLPAPER)
        } catch (t: Throwable) {
            Toast.makeText(this, "Galeri tidak tersedia: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun pickAudioFromStorage() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PICK_AUDIO)
        } catch (t: Throwable) {
            Toast.makeText(this, "Pemilih audio tidak tersedia: ${t.message}", Toast.LENGTH_SHORT).show()
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
        } catch (_: Throwable) {
            null
        } finally {
            cursor?.close()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_PICK_EXE -> {
                try {
                    val rawName = queryDisplayName(uri) ?: "Imported_Program.exe"
                    val safeName = if (rawName.contains('.')) rawName else "$rawName.exe"
                    val dest = File(VirtualDrive.installersDir(this), safeName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { out -> input.copyTo(out) }
                    }
                    val entry = ExeEmulatorApp.installExeToProgramFiles(this, prefs, dest)
                    refreshDesktopUI()
                    Toast.makeText(this, "Berhasil import & install: ${entry.title}", Toast.LENGTH_SHORT).show()
                    ExeEmulatorApp.launchExeInWindow(this, prefs, this, windowManager, File(entry.exePath))
                } catch (t: Throwable) {
                    Toast.makeText(this, "Gagal import file: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
            REQ_PICK_WALLPAPER -> {
                try {
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
                    var sample = 1
                    while (boundsOpts.outWidth / sample > 1920 || boundsOpts.outHeight / sample > 1920) {
                        sample *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                    if (bmp != null) {
                        FileOutputStream(prefs.customWallpaperFile()).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        prefs.useCustomWallpaper = true
                        refreshDesktopUI()
                        Toast.makeText(this, "Wallpaper desktop diperbarui!", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    Toast.makeText(this, "Gagal memuat gambar: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
            REQ_PICK_AUDIO -> {
                try {
                    val name = queryDisplayName(uri) ?: "Track.mp3"
                    val dest = File(VirtualDrive.downloadsDir(this), name)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { out -> input.copyTo(out) }
                    }
                    windowManager.openWindows().find { it.appKey == "media" }?.let { windowManager.closeWindow(it.id) }
                    openMediaPlayer(dest)
                } catch (t: Throwable) {
                    Toast.makeText(this, "Gagal membuka audio: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== START MENU & QUICK PANEL ====================
    private fun showStartMenu(focusSearch: Boolean) {
        val theme = prefs.currentTheme
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = UiKit.gradientRounded(this@MainActivity, theme.panelColors, Color.argb(75, 255, 255, 255), 20)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(UiKit.label(this, "⊞ RuangDroid Start", 17f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(UiKit.chipButton(this, "⚙️ Settings PC") {
            dialog.dismiss()
            openControlPanel()
        }, LinearLayout.LayoutParams(-2, dp(28)))
        panel.addView(header)

        val searchBox = EditText(this).apply {
            hint = "Ketik nama aplikasi PC atau Android…"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            textSize = 13.5f
            setSingleLine(true)
            background = UiKit.rounded(this@MainActivity, Color.argb(38, 215, 229, 255), 12)
            setPadding(dp(12), 0, dp(12), 0)
        }
        panel.addView(searchBox, LinearLayout.LayoutParams(-1, dp(38)).apply {
            topMargin = dp(8); bottomMargin = dp(8)
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val gridHolder = FrameLayout(this)
        scroll.addView(gridHolder)
        val menuH = (resources.displayMetrics.heightPixels * 0.54f).toInt().coerceAtLeast(dp(200))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, menuH))

        val cols = if (isPcLandscape()) 5 else 4

        fun populate(q: String) {
            gridHolder.removeAllViews()
            val tiles = mutableListOf<View>()

            // Built-in PC Apps first
            BuiltInApps.all.filter { it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true) }
                .forEach { bApp ->
                    tiles.add(
                        desktopTileView(bApp.title, bApp.icon(this), 38,
                            onClick = { dialog.dismiss(); launchBuiltInByKey(bApp.key) },
                            onLongClick = { dialog.dismiss(); launchBuiltInByKey(bApp.key) })
                    )
                }

            // Installed Android Apps
            installedApps.filter { it.label.contains(q, ignoreCase = true) }
                .forEach { app ->
                    val drw: Drawable = if (app.iconBitmap != null) BitmapDrawable(resources, app.iconBitmap)
                    else UiKit.builtInIcon(this, "apk", Color.rgb(59, 130, 246), Color.rgb(37, 99, 235), "🤖")
                    tiles.add(
                        desktopTileView(app.label, drw, 38,
                            onClick = { dialog.dismiss(); launchAndroidApp(app) },
                            onLongClick = { dialog.dismiss(); showAndroidAppContextMenu(app) })
                    )
                }

            gridHolder.addView(UiKit.buildGrid(this, cols, 82, tiles))
        }
        populate("")

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = populate(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })

        dialog.setContentView(panel)
        val w = if (isPcLandscape()) dp(480) else (resources.displayMetrics.widthPixels * 0.94f).toInt()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val lp = attributes
            lp.gravity = Gravity.BOTTOM or Gravity.START
            lp.x = dp(12)
            lp.y = dp(62)
            lp.width = w
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            attributes = lp
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
            if (focusSearch) setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dialog.show()
        if (focusSearch) searchBox.requestFocus()
    }

    private fun showQuickSettingsPanel() {
        val theme = prefs.currentTheme
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = UiKit.gradientRounded(this@MainActivity, theme.panelColors, Color.argb(75, 255, 255, 255), 20)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(UiKit.label(this, "🎛️ Quick Action Center", 15.5f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val (bat, chg) = batteryInfo()
        head.addView(UiKit.label(this, "${if (chg) "⚡" else "🔋"} $bat%", 12f, theme.accent, bold = true))
        card.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        fun qTile(title: String, sub: String, active: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            val bg = if (active) Color.argb(95, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
            else Color.argb(32, 215, 229, 255)
            background = UiKit.strokeRounded(this@MainActivity, bg, Color.argb(if (active) 130 else 40, 255, 255, 255), 12)
            setOnClickListener { action() }
            addView(UiKit.label(this@MainActivity, title, 11.5f, UiKit.INK, bold = true))
            addView(UiKit.label(this@MainActivity, sub, 9.5f, if (active) UiKit.INK else UiKit.MUTED))
        }

        val tiles = listOf(
            qTile("📶 Wi-Fi", "Jaringan", true) { dialog.dismiss(); openSystemAction(Settings.ACTION_WIFI_SETTINGS) },
            qTile("🔵 Bluetooth", "Perangkat", false) { dialog.dismiss(); openSystemAction(Settings.ACTION_BLUETOOTH_SETTINGS) },
            qTile("🖥️ Mode PC", if (prefs.landscapeLock) "Landscape" else "Auto", prefs.landscapeLock) {
                prefs.landscapeLock = !prefs.landscapeLock
                dialog.dismiss()
                refreshDesktopUI()
            },
            qTile("⛶ Fullscreen", if (prefs.immersiveMode) "Aktif" else "Standar", prefs.immersiveMode) {
                prefs.immersiveMode = !prefs.immersiveMode
                dialog.dismiss()
                refreshDesktopUI()
            },
            qTile("🎨 Tema", theme.name, false) {
                prefs.cycleTheme()
                dialog.dismiss()
                refreshDesktopUI()
                showQuickSettingsPanel()
            },
            qTile("☀️ Layar", "Brightness", false) { dialog.dismiss(); openSystemAction(Settings.ACTION_DISPLAY_SETTINGS) }
        )
        card.addView(UiKit.buildGrid(this, 3, 52, tiles), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        // Media Volume Slider
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxV = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curV = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxV)
            val volRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.rounded(this@MainActivity, Color.argb(28, 255, 255, 255), 12)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            volRow.addView(UiKit.label(this, "🔊", 14f))
            volRow.addView(SeekBar(this).apply {
                max = maxV
                progress = curV
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar?) = Unit
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(volRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        } catch (_: Throwable) {
        }

        card.addView(UiKit.label(this, "${memorySummary()}  ·  ${storageSummary()}", 10.5f, UiKit.MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val foot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        foot.addView(UiKit.chipButton(this, "⚙️ Control Panel PC") {
            dialog.dismiss(); openControlPanel()
        }, LinearLayout.LayoutParams(0, dp(32), 1f).apply { rightMargin = dp(6) })
        foot.addView(UiKit.chipButton(this, "📱 Setelan Android") {
            dialog.dismiss(); openSystemAction(Settings.ACTION_SETTINGS)
        }, LinearLayout.LayoutParams(0, dp(32), 1f))
        card.addView(foot)

        dialog.setContentView(card)
        val w = if (isPcLandscape()) dp(355) else (resources.displayMetrics.widthPixels * 0.94f).toInt()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val lp = attributes
            lp.gravity = Gravity.BOTTOM or Gravity.END
            lp.x = dp(12)
            lp.y = dp(62)
            lp.width = w
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            attributes = lp
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
        }
        dialog.show()
    }

    // ==================== EXTERNAL ANDROID APPS & FOLDERS ====================
    private fun launchAndroidApp(app: AndroidAppEntry, forceFreeform: Boolean = false) {
        val baseIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (prefs.freeformExternalApps || forceFreeform) {
            try {
                val dm = resources.displayMetrics
                val rect = Rect(
                    (dm.widthPixels * 0.12f).toInt(),
                    (dm.heightPixels * 0.08f).toInt(),
                    (dm.widthPixels * 0.88f).toInt(),
                    (dm.heightPixels * 0.86f).toInt()
                )
                val opts = ActivityOptions.makeBasic().setLaunchBounds(rect)
                // Attempt Android freeform windowingMode = 5 when supported by device/ROM
                try {
                    val m = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    m.invoke(opts, 5)
                } catch (_: Throwable) {
                }
                val freeformIntent = Intent(baseIntent).addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                startActivity(freeformIntent, opts.toBundle())
                return
            } catch (_: Throwable) {
            }
        }

        try {
            startActivity(baseIntent)
        } catch (_: Throwable) {
            Toast.makeText(this, "Aplikasi tidak bisa dibuka", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAndroidAppContextMenu(app: AndroidAppEntry) {
        val pkg = app.packageName
        val pinned = prefs.getPinnedPackages()
        val desk = prefs.getDesktopPackages()
        val isPinned = pinned.contains(pkg)
        val isDesk = desk.contains(pkg)

        val items = arrayOf(
            "🪟 Buka Mode Freeform Window",
            if (isPinned) "📌 Lepas Pin dari Taskbar" else "📌 Pin ke Taskbar",
            if (isDesk) "🖥️ Hapus dari Desktop" else "🖥️ Tambah ke Desktop",
            "📁 Masukkan ke Folder…",
            "ℹ️ Info Aplikasi Android",
            "🗑️ Uninstall Aplikasi"
        )
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> launchAndroidApp(app, forceFreeform = true)
                    1 -> {
                        if (isPinned) pinned.remove(pkg) else pinned.add(pkg)
                        prefs.savePinnedPackages(pinned)
                        renderTaskbar()
                    }
                    2 -> {
                        if (isDesk) desk.remove(pkg) else desk.add(pkg)
                        prefs.saveDesktopPackages(desk)
                        renderDesktopSurface()
                    }
                    3 -> {
                        val folders = prefs.getFolders()
                        val names = (folders.map { "📁 ${it.name}" } + "➕ Folder Baru…").toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Pilih Folder")
                            .setItems(names) { _, idx ->
                                if (idx == folders.size) {
                                    val nf = DesktopFolder(UUID.randomUUID().toString(), "Folder Baru", mutableListOf(pkg))
                                    folders.add(nf)
                                    prefs.saveFolders(folders)
                                    renderDesktopSurface()
                                    showCreateOrEditFolderDialog(nf)
                                } else {
                                    if (!folders[idx].packages.contains(pkg)) folders[idx].packages.add(pkg)
                                    prefs.saveFolders(folders)
                                    renderDesktopSurface()
                                }
                            }
                            .show()
                    }
                    4 -> {
                        try {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", pkg, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Throwable) {
                        }
                    }
                    5 -> {
                        try {
                            startActivity(
                                Intent(Intent.ACTION_DELETE)
                                    .setData(Uri.fromParts("package", pkg, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            .show()
    }

    private fun showDesktopShortcutPicker() {
        if (installedApps.isEmpty()) {
            Toast.makeText(this, "Daftar aplikasi sedang dimuat…", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = prefs.getDesktopPackages().toMutableSet()
        val labels = installedApps.map { it.label }.toTypedArray()
        val checked = BooleanArray(installedApps.size) { selected.contains(installedApps[it].packageName) }

        AlertDialog.Builder(this)
            .setTitle("Pilih Aplikasi Android di Desktop")
            .setMultiChoiceItems(labels, checked) { _, idx, isChecked ->
                val p = installedApps[idx].packageName
                if (isChecked) selected.add(p) else selected.remove(p)
            }
            .setPositiveButton("Simpan") { _, _ ->
                prefs.saveDesktopPackages(selected.toList())
                renderDesktopSurface()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showCreateOrEditFolderDialog(existing: DesktopFolder?) {
        val theme = prefs.currentTheme
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = UiKit.gradientRounded(this@MainActivity, theme.panelColors, Color.argb(75, 255, 255, 255), 18)
        }
        panel.addView(UiKit.label(this, if (existing == null) "Buat Folder Desktop" else "Kelola Folder", 16f, UiKit.INK, bold = true))

        val nameInput = EditText(this).apply {
            hint = "Nama Folder"
            setText(existing?.name ?: "")
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            setSingleLine(true)
            background = UiKit.rounded(this@MainActivity, Color.argb(38, 255, 255, 255), 10)
            setPadding(dp(12), 0, dp(12), 0)
        }
        panel.addView(nameInput, LinearLayout.LayoutParams(-1, dp(38)).apply {
            topMargin = dp(8); bottomMargin = dp(8)
        })

        val selected = (existing?.packages ?: emptyList()).toMutableSet()
        val sc = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (app in installedApps) {
            col.addView(CheckBox(this).apply {
                text = app.label
                setTextColor(UiKit.INK)
                isChecked = selected.contains(app.packageName)
                setOnCheckedChangeListener { _, chk ->
                    if (chk) selected.add(app.packageName) else selected.remove(app.packageName)
                }
            })
        }
        sc.addView(col)
        panel.addView(sc, LinearLayout.LayoutParams(-1, dp(180)).apply { bottomMargin = dp(10) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (existing != null) {
            row.addView(UiKit.chipButton(this, "🗑️ Hapus", Color.argb(130, 220, 38, 38)) {
                prefs.saveFolders(prefs.getFolders().filterNot { it.id == existing.id })
                dialog.dismiss()
                renderDesktopSurface()
            }, LinearLayout.LayoutParams(-2, dp(32)).apply { rightMargin = dp(6) })
        }
        row.addView(UiKit.chipButton(this, "Batal") { dialog.dismiss() },
            LinearLayout.LayoutParams(-2, dp(32)).apply { rightMargin = dp(6) })
        row.addView(UiKit.chipButton(this, "💾 Simpan", Color.argb(120, 37, 99, 235)) {
            val nm = nameInput.text?.toString()?.trim().orEmpty().ifEmpty { "Folder" }
            val all = prefs.getFolders()
            if (existing == null) {
                all.add(DesktopFolder(UUID.randomUUID().toString(), nm, selected.toMutableList()))
            } else {
                all.find { it.id == existing.id }?.let {
                    it.name = nm
                    it.packages.clear()
                    it.packages.addAll(selected)
                }
            }
            prefs.saveFolders(all)
            dialog.dismiss()
            renderDesktopSurface()
        }, LinearLayout.LayoutParams(-2, dp(32)))

        panel.addView(row)
        dialog.setContentView(panel)
        val w = if (isPcLandscape()) dp(430) else (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        dialog.show()
    }

    private fun openSystemAction(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            Toast.makeText(this, "Pengaturan tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }
}
