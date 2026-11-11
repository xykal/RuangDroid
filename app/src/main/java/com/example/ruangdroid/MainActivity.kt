package com.example.ruangdroid

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val ink = Color.rgb(240, 244, 255)
    private val muted = Color.rgb(177, 190, 215)
    private val appItems = mutableListOf<ResolveInfo>()
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 28, 49)
        window.navigationBarColor = Color.rgb(16, 24, 43)
        loadApps()
        showDesktop()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        showDesktop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Home launcher stays on the desktop when Back is pressed.
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val found = packageManager.queryIntentActivities(intent, 0)
        appItems.clear()
        appItems.addAll(
            found.filter { it.activityInfo.packageName != packageName }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
        )
    }

    private fun batteryPercent(): String {
        return try {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 0..100) "$pct%" else "--%"
        } catch (_: Exception) {
            "--%"
        }
    }

    private fun showDesktop() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(18, 29, 52), Color.rgb(29, 45, 76), Color.rgb(35, 51, 83))
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.argb(42, 215, 229, 255), dp(22))
            setPadding(dp(18), dp(15), dp(18), dp(15))
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(text("RuangDroid Desktop", 14f, muted, true), LinearLayout.LayoutParams(0, dp(22), 1f))
        val batLabel = text("⚡ ${batteryPercent()}", 12f, ink, true)
        topBar.addView(batLabel)
        header.addView(topBar)

        val clockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val time = text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 38f, ink, true)
        clockRow.addView(time, LinearLayout.LayoutParams(0, dp(52), 1f))
        val dateCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        dateCol.addView(text(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()), 14f, ink, true))
        dateCol.addView(text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()), 13f, muted, false))
        clockRow.addView(dateCol)
        header.addView(clockRow)
        header.addView(text("Semi-desktop workspace siap dipakai.", 13f, muted, false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(header)

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(20), dp(2), dp(8))
        }
        section.addView(text("Shortcut Desktop", 16f, ink, true), LinearLayout.LayoutParams(0, dp(30), 1f))
        section.addView(text("${appItems.size} app terpasang", 11f, muted, false))
        root.addView(section)

        val shortcutsScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val grid = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        val max = minOf(appItems.size, 12)
        for (item in appItems.take(max)) {
            grid.addView(appTile(item, false, null), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(104)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(2), dp(5), dp(2), dp(5))
            })
        }
        shortcutsScroll.addView(grid)
        root.addView(shortcutsScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val taskbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(230, 14, 21, 37), dp(22))
            setPadding(dp(8), dp(7), dp(12), dp(7))
        }
        val start = TextView(this).apply {
            text = "⊞"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(119, 164, 255))
            background = rounded(Color.argb(48, 131, 170, 255), dp(16))
            contentDescription = "Start Menu"
            setOnClickListener { showStartMenu() }
        }
        taskbar.addView(start, LinearLayout.LayoutParams(dp(46), dp(44)))

        val search = TextView(this).apply {
            text = "  🔍 Cari aplikasi & pengaturan…"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(38, 210, 225, 255), dp(15))
            setOnClickListener { showStartMenu() }
        }
        taskbar.addView(search, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(dp(8), 0, dp(8), 0)
        })

        val settingsBtn = TextView(this).apply {
            text = "⚙"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(ink)
            background = rounded(Color.argb(32, 210, 225, 255), dp(14))
            setOnClickListener { openSystemAction(Settings.ACTION_SETTINGS) }
        }
        taskbar.addView(settingsBtn, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            setMargins(0, 0, dp(8), 0)
        })

        val tray = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        tray.addView(text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 12f, ink, true))
        tray.addView(text(SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date()), 10f, muted, false))
        taskbar.addView(tray)
        root.addView(taskbar)

        setContentView(root)
        start.postDelayed(object : Runnable {
            override fun run() {
                if (start.isAttachedToWindow) {
                    val now = Date()
                    time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                    batLabel.text = "⚡ ${batteryPercent()}"
                    (tray.getChildAt(0) as TextView).text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                    (tray.getChildAt(1) as TextView).text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(now)
                    start.postDelayed(this, 30_000)
                }
            }
        }, 30_000)
    }

    private fun showStartMenu() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(25, 37, 61), Color.rgb(18, 27, 46))
            ).apply {
                val r = dp(24).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text("Start Menu", 20f, ink, true), LinearLayout.LayoutParams(0, dp(34), 1f))
        titleRow.addView(text("${appItems.size} app", 12f, muted, false))
        panel.addView(titleRow)

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(4))
        }
        quickRow.addView(quickChip("Wi-Fi") { dialog.dismiss(); openSystemAction(Settings.ACTION_WIFI_SETTINGS) },
            LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(0, 0, dp(6), 0) })
        quickRow.addView(quickChip("Bluetooth") { dialog.dismiss(); openSystemAction(Settings.ACTION_BLUETOOTH_SETTINGS) },
            LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(0, 0, dp(6), 0) })
        quickRow.addView(quickChip("Settings") { dialog.dismiss(); openSystemAction(Settings.ACTION_SETTINGS) },
            LinearLayout.LayoutParams(0, dp(36), 1f))
        panel.addView(quickRow)

        val input = EditText(this).apply {
            hint = "Ketik nama aplikasi…"
            setHintTextColor(muted)
            setTextColor(ink)
            textSize = 15f
            setSingleLine(true)
            background = rounded(Color.argb(38, 210, 225, 255), dp(15))
            setPadding(dp(14), 0, dp(14), 0)
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(46)).apply {
            setMargins(0, dp(10), 0, dp(12))
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val appGrid = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        scroll.addView(appGrid)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun populate(query: String) {
            appGrid.removeAllViews()
            val filtered = appItems.filter {
                it.loadLabel(packageManager).toString().contains(query, ignoreCase = true)
            }
            filtered.forEach { item ->
                appGrid.addView(appTile(item, true, dialog), GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(98)
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
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.80f).toInt())
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.80f).toInt())
    }

    private fun quickChip(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        setTextColor(ink)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = rounded(Color.argb(42, 131, 170, 255), dp(12))
        setOnClickListener { onClick() }
    }

    private fun openSystemAction(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "Pengaturan tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appTile(item: ResolveInfo, drawer: Boolean, dialog: Dialog?): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rounded(Color.argb(if (drawer) 14 else 0, 210, 225, 255), dp(16))
            setOnClickListener {
                dialog?.dismiss()
                launchApp(item)
            }
        }
        val icon = ImageView(this).apply {
            setImageDrawable(item.loadIcon(packageManager))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = item.loadLabel(packageManager).toString()
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)))
        tile.addView(TextView(this).apply {
            text = item.loadLabel(packageManager).toString()
            textSize = 11f
            setTextColor(ink)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2), dp(5), dp(2), 0)
        }, LinearLayout.LayoutParams(-1, dp(38)))
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

    private fun dp(value: Int): Int = (value * density).toInt()
}
