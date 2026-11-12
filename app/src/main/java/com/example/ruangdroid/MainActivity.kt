package com.example.ruangdroid

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
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
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : Activity() {
    private val ink = Color.rgb(242, 246, 255)
    private val muted = Color.rgb(174, 188, 214)
    private val appItems = mutableListOf<ResolveInfo>()
    private val appByPkg = mutableMapOf<String, ResolveInfo>()
    private lateinit var prefs: DesktopPrefs
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DesktopPrefs(this)
        applyWindowAndOrientation()
        loadApps()
        showDesktop()
    }

    override fun onResume() {
        super.onResume()
        applyWindowAndOrientation()
        loadApps()
        showDesktop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        showDesktop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Keep Home launcher on desktop workspace.
    }

    private fun applyWindowAndOrientation() {
        requestedOrientation = if (prefs.landscapeLock) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        val theme = prefs.currentTheme
        window.statusBarColor = theme.bgColors.first()
        window.navigationBarColor = Color.rgb(12, 18, 32)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (prefs.immersiveMode) {
                controller?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (prefs.immersiveMode) {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    private fun isPcLandscape(): Boolean {
        val cfg = resources.configuration
        return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE || cfg.screenWidthDp >= 600
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val found = packageManager.queryIntentActivities(intent, 0)
        appItems.clear()
        appByPkg.clear()

        val sorted = found
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }

        appItems.addAll(sorted)
        for (item in sorted) {
            appByPkg[item.activityInfo.packageName] = item
        }

        prefs.ensureSeeded(
            sorted.map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
        )
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        return try {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            val charging = bm.isCharging
            pct to charging
        } catch (_: Exception) {
            100 to false
        }
    }

    private fun memorySummary(): String {
        return try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val usedGb = (info.totalMem - info.availMem).toDouble() / (1024.0 * 1024.0 * 1024.0)
            val totalGb = info.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
            String.format(Locale.US, "RAM %.1f / %.1f GB", usedGb, totalGb)
        } catch (_: Exception) {
            "RAM tersedia"
        }
    }

    private fun storageSummary(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = stat.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val totalGb = stat.totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            String.format(Locale.US, "Storage %.0f GB kosong dari %.0f GB", freeGb, totalGb)
        } catch (_: Exception) {
            "Storage internal aktif"
        }
    }

    private fun showDesktop() {
        val theme = prefs.currentTheme
        val pcMode = isPcLandscape()
        val widthDp = resources.configuration.screenWidthDp

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, theme.bgColors)
        }

        val workspaceArea = LinearLayout(this).apply {
            orientation = if (pcMode) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }

        // Main desktop surface (Folders + App Shortcuts)
        val desktopSurface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Top action strip above icons
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        val modeBadgeText = if (prefs.landscapeLock) "🖥️ RuangDroid PC Mode" else "📱 RuangDroid Hybrid"
        topBar.addView(text(modeBadgeText, 13f, ink, true), LinearLayout.LayoutParams(0, dp(28), 1f))

        topBar.addView(smallActionButton("📁+ Folder") {
            showCreateOrEditFolderDialog(null)
        }, LinearLayout.LayoutParams(-2, dp(30)).apply { setMargins(0, 0, dp(6), 0) })

        topBar.addView(smallActionButton("➕ Shortcut") {
            showDesktopShortcutPicker()
        }, LinearLayout.LayoutParams(-2, dp(30)))

        desktopSurface.addView(topBar)

        if (!pcMode) {
            desktopSurface.addView(buildCompactHeader(theme), LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(10))
            })
        }

        val cols = when {
            widthDp >= 840 -> 7
            pcMode -> 5
            else -> 4
        }

        val gridScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val desktopGrid = GridLayout(this).apply {
            columnCount = cols
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }

        // 1. Render Desktop Folders first
        val folders = prefs.getFolders()
        for (folder in folders) {
            desktopGrid.addView(folderTile(folder, theme), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(96)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(4), dp(3), dp(4))
            })
        }

        // 2. Render Desktop App Shortcuts
        val desktopApps = prefs.getDesktopPackages().mapNotNull { appByPkg[it] }
        val fallbackApps = if (desktopApps.isEmpty()) appItems.take(8) else desktopApps
        for (item in fallbackApps) {
            desktopGrid.addView(appTile(item, false, null), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(96)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(4), dp(3), dp(4))
            })
        }

        gridScroll.addView(desktopGrid)
        desktopSurface.addView(gridScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        if (pcMode) {
            workspaceArea.addView(desktopSurface, LinearLayout.LayoutParams(0, -1, 1f).apply {
                setMargins(0, 0, dp(12), 0)
            })
            workspaceArea.addView(buildPcSideWidget(theme), LinearLayout.LayoutParams(dp(220), -1))
        } else {
            workspaceArea.addView(desktopSurface, LinearLayout.LayoutParams(-1, -1))
        }

        root.addView(workspaceArea, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(0, 0, 0, dp(8))
        })

        // Windows 11 / DeX Taskbar
        val taskbar = buildTaskbar(theme)
        root.addView(taskbar, LinearLayout.LayoutParams(-1, dp(52)))

        setContentView(root)
    }

    private fun buildCompactHeader(theme: ThemePalette): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = strokeRounded(Color.argb(46, 215, 229, 255), Color.argb(55, 255, 255, 255), dp(18))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 30f, ink, true))
        left.addView(text(SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date()), 12f, muted, false))
        card.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        val (bat, charging) = batteryInfo()
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        right.addView(text("${if (charging) "⚡" else "🔋"} $bat%", 13f, theme.accent, true))
        right.addView(text(memorySummary(), 11f, muted, false))
        card.addView(right)
        return card
    }

    private fun buildPcSideWidget(theme: ThemePalette): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = strokeRounded(Color.argb(48, 16, 26, 46), Color.argb(50, 255, 255, 255), dp(20))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        panel.addView(text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 34f, ink, true))
        panel.addView(text(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()), 13f, theme.accent, true))
        panel.addView(text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()), 12f, muted, false))

        val divider = View(this).apply {
            setBackgroundColor(Color.argb(36, 255, 255, 255))
        }
        panel.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
            setMargins(0, dp(12), 0, dp(12))
        })

        val (bat, charging) = batteryInfo()
        panel.addView(text("Status Perangkat", 12f, ink, true))
        panel.addView(text("${if (charging) "⚡ Mengisi daya" else "🔋 Baterai"}: $bat%", 11f, muted, false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        panel.addView(text(memorySummary(), 11f, muted, false).apply {
            setPadding(0, dp(3), 0, 0)
        })
        panel.addView(text(storageSummary(), 11f, muted, false).apply {
            setPadding(0, dp(3), 0, 0)
        })

        panel.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        val hint = text("Tip: Klik tahan ikon app untuk pin taskbar / masukkan folder.", 10f, muted, false).apply {
            maxLines = 3
        }
        panel.addView(hint, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, dp(8))
        })

        val quickBtn = TextView(this).apply {
            text = "🎛️ Buka Quick Panel"
            textSize = 12f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(Color.argb(65, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent)), dp(12))
            setOnClickListener { showQuickSettingsPanel() }
        }
        panel.addView(quickBtn, LinearLayout.LayoutParams(-1, dp(36)))

        return panel
    }

    private fun buildTaskbar(theme: ThemePalette): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = strokeRounded(theme.taskbarBg, Color.argb(45, 255, 255, 255), dp(18))
            setPadding(dp(8), dp(5), dp(10), dp(5))
        }

        // Start button
        val startBtn = TextView(this).apply {
            text = "⊞"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(theme.accent)
            background = rounded(Color.argb(48, 131, 170, 255), dp(13))
            contentDescription = "Start Menu"
            setOnClickListener { showStartMenu(focusSearch = false) }
        }
        bar.addView(startBtn, LinearLayout.LayoutParams(dp(42), dp(40)))

        // Search button
        val searchPill = TextView(this).apply {
            text = if (isPcLandscape()) "  🔍 Cari aplikasi…" else " 🔍"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(34, 210, 225, 255), dp(13))
            setPadding(dp(8), 0, dp(10), 0)
            setOnClickListener { showStartMenu(focusSearch = true) }
        }
        bar.addView(searchPill, LinearLayout.LayoutParams(if (isPcLandscape()) dp(150) else dp(40), dp(38)).apply {
            setMargins(dp(6), 0, dp(8), 0)
        })

        // Pinned Apps Dock in middle of taskbar
        val pinnedScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val pinnedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pinnedApps = prefs.getPinnedPackages().mapNotNull { appByPkg[it] }
        for (item in pinnedApps) {
            val iconHolder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.argb(26, 255, 255, 255), dp(11))
                setOnClickListener { launchApp(item) }
                setOnLongClickListener {
                    showAppContextMenu(item)
                    true
                }
            }
            val iv = ImageView(this).apply {
                setImageDrawable(item.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            iconHolder.addView(iv, LinearLayout.LayoutParams(dp(26), dp(26)))
            pinnedRow.addView(iconHolder, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                setMargins(0, 0, dp(5), 0)
            })
        }
        pinnedScroll.addView(pinnedRow)
        bar.addView(pinnedScroll, LinearLayout.LayoutParams(0, dp(40), 1f))

        // Right System Tray -> Opens Bottom-Right Quick Settings Panel!
        val (bat, charging) = batteryInfo()
        val trayPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(38, 215, 229, 255), dp(13))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { showQuickSettingsPanel() }
        }

        val statusIcons = text("📶 🔊 ${if (charging) "⚡" else "🔋"}$bat%", 11f, ink, false)
        trayPill.addView(statusIcons, LinearLayout.LayoutParams(-2, -2).apply {
            setMargins(0, 0, dp(8), 0)
        })

        val clockCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val trayTime = text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 11f, ink, true)
        val trayDate = text(SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date()), 9f, muted, false)
        clockCol.addView(trayTime)
        clockCol.addView(trayDate)
        trayPill.addView(clockCol)

        bar.addView(trayPill, LinearLayout.LayoutParams(-2, dp(40)).apply {
            setMargins(dp(6), 0, 0, 0)
        })

        bar.postDelayed(object : Runnable {
            override fun run() {
                if (bar.isAttachedToWindow) {
                    val now = Date()
                    val (b, ch) = batteryInfo()
                    statusIcons.text = "📶 🔊 ${if (ch) "⚡" else "🔋"}$b%"
                    trayTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                    trayDate.text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(now)
                    bar.postDelayed(this, 30_000)
                }
            }
        }, 30_000)

        return bar
    }

    // Bottom-Right Quick Settings Panel (Action Center ala Windows 11 / DeX)
    private fun showQuickSettingsPanel() {
        val theme = prefs.currentTheme
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = strokeRoundedGradient(theme.panelColors, Color.argb(65, 255, 255, 255), dp(22))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("Quick Settings", 17f, ink, true), LinearLayout.LayoutParams(0, dp(28), 1f))
        val (bat, charging) = batteryInfo()
        header.addView(text("${if (charging) "⚡ Charging" else "🔋"} $bat%", 12f, theme.accent, true))
        card.addView(header)

        // 2x3 Quick Tiles Grid
        val tileGrid = GridLayout(this).apply {
            columnCount = 3
            setPadding(0, dp(10), 0, dp(8))
        }

        fun addTile(label: String, sub: String, active: Boolean, onClick: () -> Unit) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                val bg = if (active) {
                    Color.argb(95, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
                } else {
                    Color.argb(34, 215, 229, 255)
                }
                background = strokeRounded(bg, Color.argb(if (active) 120 else 35, 255, 255, 255), dp(14))
                setOnClickListener { onClick() }
            }
            tile.addView(text(label, 12f, ink, true))
            tile.addView(text(sub, 10f, if (active) ink else muted, false))
            tileGrid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(56)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }

        addTile("📶 Wi-Fi", "Jaringan", true) {
            dialog.dismiss()
            openSystemAction(Settings.ACTION_WIFI_SETTINGS)
        }
        addTile("🔵 Bluetooth", "Perangkat", false) {
            dialog.dismiss()
            openSystemAction(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        addTile("🖥️ Mode PC", if (prefs.landscapeLock) "Landscape" else "Auto/HP", prefs.landscapeLock) {
            prefs.landscapeLock = !prefs.landscapeLock
            dialog.dismiss()
            applyWindowAndOrientation()
            showDesktop()
        }
        addTile("⛶ Fullscreen", if (prefs.immersiveMode) "Aktif" else "Standar", prefs.immersiveMode) {
            prefs.immersiveMode = !prefs.immersiveMode
            dialog.dismiss()
            applyWindowAndOrientation()
            showDesktop()
        }
        addTile("🎨 Tema", theme.name, false) {
            prefs.cycleTheme()
            dialog.dismiss()
            applyWindowAndOrientation()
            showDesktop()
            showQuickSettingsPanel()
        }
        addTile("☀️ Layar", "Kecerahan", false) {
            dialog.dismiss()
            openSystemAction(Settings.ACTION_DISPLAY_SETTINGS)
        }

        card.addView(tileGrid, LinearLayout.LayoutParams(-1, -2))

        // Live Media Volume Slider
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVol)

        val volRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(28, 215, 229, 255), dp(14))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val volLabel = text("🔊", 15f, ink, false)
        volRow.addView(volLabel)
        val seek = SeekBar(this).apply {
            max = maxVol
            progress = curVol
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        volRow.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(volRow, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(4), 0, dp(10))
        })

        // Resource summary strip
        val sysBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.argb(24, 215, 229, 255), dp(12))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        sysBox.addView(text(memorySummary(), 11f, ink, true))
        sysBox.addView(text(storageSummary(), 11f, muted, false))
        card.addView(sysBox, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, dp(10))
        })

        // Footer actions
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        footer.addView(smallActionButton("📁+ Buat Folder") {
            dialog.dismiss()
            showCreateOrEditFolderDialog(null)
        }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(0, 0, dp(8), 0) })

        footer.addView(smallActionButton("⚙ Semua Pengaturan") {
            dialog.dismiss()
            openSystemAction(Settings.ACTION_SETTINGS)
        }, LinearLayout.LayoutParams(0, dp(34), 1f))
        card.addView(footer)

        dialog.setContentView(card)
        val panelWidth = if (isPcLandscape()) dp(360) else (resources.displayMetrics.widthPixels * 0.94f).toInt()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val lp = attributes
            lp.gravity = Gravity.BOTTOM or Gravity.END
            lp.x = dp(14)
            lp.y = dp(66)
            lp.width = panelWidth
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            attributes = lp
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
        }
        dialog.show()
    }

    // Folder tile on Desktop
    private fun folderTile(folder: DesktopFolder, theme: ThemePalette): View {
        val validCount = folder.packages.count { appByPkg.containsKey(it) }
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rounded(Color.argb(22, 255, 255, 255), dp(16))
            setOnClickListener { showFolderWindow(folder) }
            setOnLongClickListener {
                showCreateOrEditFolderDialog(folder)
                true
            }
        }

        val iconBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = strokeRounded(
                Color.argb(55, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent)),
                Color.argb(90, 255, 255, 255),
                dp(14)
            )
        }
        iconBox.addView(TextView(this).apply {
            text = "📁"
            textSize = 20f
            gravity = Gravity.CENTER
        })
        iconBox.addView(TextView(this).apply {
            text = "$validCount"
            textSize = 9f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        tile.addView(iconBox, LinearLayout.LayoutParams(dp(46), dp(46)))
        tile.addView(TextView(this).apply {
            text = folder.name
            textSize = 11f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(3), dp(4), dp(3), 0)
        }, LinearLayout.LayoutParams(-1, dp(36)))
        return tile
    }

    // Floating PC-style Folder Explorer Window
    private fun showFolderWindow(folder: DesktopFolder) {
        val theme = prefs.currentTheme
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val win = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            background = strokeRoundedGradient(theme.panelColors, Color.argb(75, 255, 255, 255), dp(22))
        }

        // Window titlebar
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBar.addView(text("📁 ${folder.name}", 17f, ink, true), LinearLayout.LayoutParams(0, dp(32), 1f))

        titleBar.addView(smallActionButton("✏️ Kelola") {
            dialog.dismiss()
            showCreateOrEditFolderDialog(folder)
        }, LinearLayout.LayoutParams(-2, dp(30)).apply { setMargins(0, 0, dp(8), 0) })

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(ink)
            gravity = Gravity.CENTER
            background = rounded(Color.argb(45, 255, 95, 95), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        titleBar.addView(closeBtn, LinearLayout.LayoutParams(dp(32), dp(30)))
        win.addView(titleBar)

        val appsInFolder = folder.packages.mapNotNull { appByPkg[it] }
        if (appsInFolder.isEmpty()) {
            win.addView(text("Folder masih kosong. Klik '✏️ Kelola' untuk memilih aplikasi.", 12f, muted, false).apply {
                setPadding(0, dp(24), 0, dp(24))
            })
        } else {
            val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
            val cols = if (isPcLandscape()) 5 else 4
            val grid = GridLayout(this).apply {
                columnCount = cols
                alignmentMode = GridLayout.ALIGN_BOUNDS
                setPadding(0, dp(10), 0, 0)
            }
            for (item in appsInFolder) {
                grid.addView(appTile(item, true, dialog), GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(96)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            scroll.addView(grid)
            win.addView(scroll, LinearLayout.LayoutParams(-1, dp(250)))
        }

        dialog.setContentView(win)
        val w = if (isPcLandscape()) dp(520) else (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
        }
        dialog.show()
    }

    // Create or Edit Folder + Multi-select App Picker
    private fun showCreateOrEditFolderDialog(existing: DesktopFolder?) {
        val theme = prefs.currentTheme
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = strokeRoundedGradient(theme.panelColors, Color.argb(75, 255, 255, 255), dp(22))
        }

        panel.addView(text(if (existing == null) "Buat Folder Desktop Baru" else "Kelola Folder", 17f, ink, true))

        val nameInput = EditText(this).apply {
            hint = "Nama folder (contoh: Game, Kerja, Sosial)"
            setText(existing?.name ?: "")
            setHintTextColor(muted)
            setTextColor(ink)
            textSize = 14f
            setSingleLine(true)
            background = rounded(Color.argb(38, 210, 225, 255), dp(12))
            setPadding(dp(12), 0, dp(12), 0)
        }
        panel.addView(nameInput, LinearLayout.LayoutParams(-1, dp(42)).apply {
            setMargins(0, dp(10), 0, dp(10))
        })

        panel.addView(text("Pilih aplikasi di dalam folder:", 12f, muted, false))

        val selected = (existing?.packages ?: emptyList()).toMutableSet()
        val listScroll = ScrollView(this)
        val checkContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (item in appItems) {
            val pkg = item.activityInfo.packageName
            val cb = CheckBox(this).apply {
                text = item.loadLabel(packageManager).toString()
                setTextColor(ink)
                textSize = 13f
                isChecked = selected.contains(pkg)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(pkg) else selected.remove(pkg)
                }
            }
            checkContainer.addView(cb)
        }
        listScroll.addView(checkContainer)
        panel.addView(listScroll, LinearLayout.LayoutParams(-1, dp(190)).apply {
            setMargins(0, dp(6), 0, dp(10))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (existing != null) {
            actions.addView(smallActionButton("🗑️ Hapus Folder") {
                val updated = prefs.getFolders().filterNot { it.id == existing.id }
                prefs.saveFolders(updated)
                dialog.dismiss()
                showDesktop()
            }, LinearLayout.LayoutParams(-2, dp(36)).apply { setMargins(0, 0, dp(8), 0) })
        }

        actions.addView(smallActionButton("Batal") { dialog.dismiss() },
            LinearLayout.LayoutParams(-2, dp(36)).apply { setMargins(0, 0, dp(8), 0) })

        actions.addView(smallActionButton("💾 Simpan") {
            val folderName = nameInput.text?.toString()?.trim().orEmpty().ifEmpty { "Folder Baru" }
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
            dialog.dismiss()
            showDesktop()
        }, LinearLayout.LayoutParams(-2, dp(36)))

        panel.addView(actions)

        dialog.setContentView(panel)
        val w = if (isPcLandscape()) dp(460) else (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
        }
        dialog.show()
    }

    private fun showDesktopShortcutPicker() {
        val selected = prefs.getDesktopPackages().toMutableSet()
        val labels = appItems.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val checked = BooleanArray(appItems.size) { idx ->
            selected.contains(appItems[idx].activityInfo.packageName)
        }
        AlertDialog.Builder(this)
            .setTitle("Pilih Shortcut Aplikasi di Desktop")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = appItems[which].activityInfo.packageName
                if (isChecked) selected.add(pkg) else selected.remove(pkg)
            }
            .setPositiveButton("Simpan") { _, _ ->
                prefs.saveDesktopPackages(selected.toList())
                showDesktop()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Windows 11 / DeX Start Menu (Bottom-Left in PC Landscape)
    private fun showStartMenu(focusSearch: Boolean) {
        val theme = prefs.currentTheme
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = strokeRoundedGradient(theme.panelColors, Color.argb(65, 255, 255, 255), dp(22))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text("⊞ Start Menu", 18f, ink, true), LinearLayout.LayoutParams(0, dp(30), 1f))
        titleRow.addView(text("${appItems.size} aplikasi", 11f, muted, false))
        panel.addView(titleRow)

        val input = EditText(this).apply {
            hint = "Ketik nama aplikasi…"
            setHintTextColor(muted)
            setTextColor(ink)
            textSize = 14f
            setSingleLine(true)
            background = rounded(Color.argb(38, 210, 225, 255), dp(14))
            setPadding(dp(14), 0, dp(14), 0)
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(42)).apply {
            setMargins(0, dp(10), 0, dp(10))
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val cols = if (isPcLandscape()) 5 else 4
        val appGrid = GridLayout(this).apply {
            columnCount = cols
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        scroll.addView(appGrid)
        val gridHeight = (resources.displayMetrics.heightPixels * 0.50f).toInt().coerceAtLeast(dp(180))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, gridHeight))

        fun populate(query: String) {
            appGrid.removeAllViews()
            val filtered = appItems.filter {
                it.loadLabel(packageManager).toString().contains(query, ignoreCase = true)
            }
            filtered.forEach { item ->
                appGrid.addView(appTile(item, true, dialog), GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(92)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                })
            }
        }
        populate("")
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populate(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        dialog.setContentView(panel)
        val menuWidth = if (isPcLandscape()) dp(480) else (resources.displayMetrics.widthPixels * 0.94f).toInt()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val lp = attributes
            lp.gravity = Gravity.BOTTOM or Gravity.START
            lp.x = dp(14)
            lp.y = dp(66)
            lp.width = menuWidth
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            attributes = lp
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.4f)
            if (focusSearch) {
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }
        }
        dialog.show()
        if (focusSearch) input.requestFocus()
    }

    // Long-press Desktop Context Menu for any app
    private fun showAppContextMenu(item: ResolveInfo) {
        val pkg = item.activityInfo.packageName
        val label = item.loadLabel(packageManager).toString()
        val pinned = prefs.getPinnedPackages()
        val onDesktop = prefs.getDesktopPackages()

        val isPinned = pinned.contains(pkg)
        val isDesktop = onDesktop.contains(pkg)

        val options = arrayOf(
            if (isPinned) "📌 Lepas Pin dari Taskbar" else "📌 Pin ke Taskbar",
            if (isDesktop) "🖥️ Hapus Shortcut dari Desktop" else "🖥️ Tambah Shortcut ke Desktop",
            "📁 Masukkan ke Folder…",
            "ℹ️ Info Aplikasi",
            "🗑️ Uninstall Aplikasi"
        )

        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (isPinned) pinned.remove(pkg) else pinned.add(pkg)
                        prefs.savePinnedPackages(pinned)
                        showDesktop()
                    }
                    1 -> {
                        if (isDesktop) onDesktop.remove(pkg) else onDesktop.add(pkg)
                        prefs.saveDesktopPackages(onDesktop)
                        showDesktop()
                    }
                    2 -> showPickFolderForApp(pkg, label)
                    3 -> {
                        try {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", pkg, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                        }
                    }
                    4 -> {
                        try {
                            startActivity(
                                Intent(Intent.ACTION_DELETE)
                                    .setData(Uri.fromParts("package", pkg, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            .show()
    }

    private fun showPickFolderForApp(pkg: String, appLabel: String) {
        val folders = prefs.getFolders()
        val names = (folders.map { "📁 ${it.name}" } + "➕ Buat Folder Baru…").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Masukkan $appLabel ke Folder")
            .setItems(names) { _, idx ->
                if (idx == folders.size) {
                    val created = DesktopFolder(UUID.randomUUID().toString(), "Folder Baru", mutableListOf(pkg))
                    folders.add(created)
                    prefs.saveFolders(folders)
                    showDesktop()
                    showCreateOrEditFolderDialog(created)
                } else {
                    val target = folders[idx]
                    if (!target.packages.contains(pkg)) target.packages.add(pkg)
                    prefs.saveFolders(folders)
                    showDesktop()
                    Toast.makeText(this, "Ditambahkan ke ${target.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun smallActionButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        setTextColor(ink)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        background = strokeRounded(Color.argb(42, 215, 229, 255), Color.argb(55, 255, 255, 255), dp(11))
        setOnClickListener { onClick() }
    }

    private fun openSystemAction(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "Pengaturan tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appTile(item: ResolveInfo, insideWindow: Boolean, parentDialog: Dialog?): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rounded(Color.argb(if (insideWindow) 18 else 10, 215, 229, 255), dp(15))
            setOnClickListener {
                parentDialog?.dismiss()
                launchApp(item)
            }
            setOnLongClickListener {
                parentDialog?.dismiss()
                showAppContextMenu(item)
                true
            }
        }
        val icon = ImageView(this).apply {
            setImageDrawable(item.loadIcon(packageManager))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = item.loadLabel(packageManager).toString()
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
        tile.addView(TextView(this).apply {
            text = item.loadLabel(packageManager).toString()
            textSize = 11f
            setTextColor(ink)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(3), dp(4), dp(3), 0)
        }, LinearLayout.LayoutParams(-1, dp(34)))
        return tile
    }

    private fun launchApp(item: ResolveInfo) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(item.activityInfo.packageName, item.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Aplikasi tidak bisa dibuka", Toast.LENGTH_SHORT).show()
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun strokeRounded(color: Int, strokeColor: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }

    private fun strokeRoundedGradient(colors: IntArray, strokeColor: Int, radiusDp: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1).coerceAtLeast(1), strokeColor)
        }

    private fun dp(value: Int): Int = (value * density).toInt()
}
