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
        private const val REQ_ROLE_HOME = 1004

        private val KNOWN_OEM_LAUNCHERS = setOf(
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.mi.android.globallauncher",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.realme.launcher",
            "com.bbk.launcher2",
            "com.vivo.launcher",
            "com.huawei.android.launcher",
            "com.hihonor.android.launcher",
            "com.transsion.XOSLauncher",
            "com.transsion.hilauncher",
            "com.transsion.itel.launcher",
            "com.asus.launcher",
            "net.oneplus.launcher",
            "com.nothing.launcher",
            "com.motorola.launcher3"
        )
    }

    private lateinit var prefs: DesktopPrefs
    private lateinit var rootLayout: LinearLayout
    private lateinit var rootStack: FrameLayout
    private lateinit var bootStage: FrameLayout
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
    private val detectedHomePackages = HashSet<String>()
    private val scanExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var scanningApps = false
    private var wasImmersiveApplied = false
    private var isLockedByShield = false
    private var wasDefaultHomeLastCheck = false

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

        // Persistent Custom Desktop Compositor + Fullscreen Boot/Onboarding Stage
        rootStack = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(9, 11, 16))
        }

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 11, 16))
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

        taskbarHost = FrameLayout(this)
        rootLayout.addView(taskbarHost, LinearLayout.LayoutParams(-1, dp(prefs.taskbarHeightDp)))

        bootStage = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }

        rootStack.addView(rootLayout, FrameLayout.LayoutParams(-1, -1))
        rootStack.addView(bootStage, FrameLayout.LayoutParams(-1, -1))

        overlays = OverlayKit(this, overlayStage, prefs)
        windowManager = FloatingWindowManager(
            context = this,
            stage = windowStage,
            prefs = prefs,
            onWindowsChanged = { renderTaskbar() }
        )

        setContentView(rootStack)
        wasDefaultHomeLastCheck = isDefaultHomeActive()
        registerPredictiveBackLockSafe()

        applyOrientationAndBarsSafe()
        refreshDesktopUI()
        triggerAsyncAppScan()

        if (savedInstanceState == null) {
            runBootSplashSequence()
        } else if (!prefs.onboardingCompleted) {
            showOnboardingWizard()
        }

        if (prefs.systemOverlayEnabled && SystemFloatingOverlay.canDraw(this)) {
            SystemFloatingOverlay.show(this, prefs)
        }
    }

    override fun onResume() {
        super.onResume()
        applyOrientationAndBarsSafe()
        if (isLockedByShield) return
        try { activeGlView?.onResume() } catch (_: Throwable) {}

        val nowDefault = isDefaultHomeActive()
        if (nowDefault && !wasDefaultHomeLastCheck) {
            // User just set HyperDeX as Default Launcher -> Auto-Lock in HyperDeX!
            prefs.launcherLockEnabled = true
            prefs.onboardingCompleted = true
            if (bootStage.visibility == View.VISIBLE) {
                bootStage.animate().alpha(0f).setDuration(180).withEndAction {
                    bootStage.removeAllViews()
                    bootStage.visibility = View.GONE
                    bootStage.alpha = 1f
                }.start()
            }
            showToast("Launcher Terkunci", "HyperDeX aktif sebagai Default Home. Launcher bawaan dikunci otomatis.")
        }
        wasDefaultHomeLastCheck = nowDefault

        renderDesktopSurface()
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

    private fun registerPredictiveBackLockSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY
                ) {
                    handleBackIntercept()
                }
            } catch (_: Throwable) {}
        }
    }

    private fun handleBackIntercept() {
        if (isLockedByShield) {
            finishAffinity()
            return
        }
        if (bootStage.visibility == View.VISIBLE) return
        if (overlays.hasOpenOverlay()) {
            overlays.dismissOverlay()
            return
        }
        val activeId = windowManager.activeWindowId
        if (activeId != null) {
            windowManager.minimizeWindow(activeId)
            return
        }
        if (isDefaultHomeActive() && prefs.launcherLockEnabled) {
            showToast("Launcher Lock", "Terkunci di HyperDeX. Buka Control Panel untuk ganti Default Launcher.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackIntercept()
    }

    override fun isDefaultHomeActive(): Boolean {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            val ri = packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ri?.activityInfo?.packageName == packageName
        } catch (_: Throwable) {
            false
        }
    }

    private fun isStockLauncherPackage(pkg: String): Boolean {
        if (pkg == packageName) return false
        if (pkg in KNOWN_OEM_LAUNCHERS || pkg in detectedHomePackages) return true
        val low = pkg.lowercase(Locale.ROOT)
        return low.endsWith(".launcher") || low.endsWith(".launcher2") || low.endsWith(".launcher3") || low.contains("globallauncher")
    }

    override fun requestSetDefaultLauncher() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_ROLE_HOME)
                    return
                }
            }
        } catch (_: Throwable) {}
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {}
        }
    }

    override fun unlockAndSwitchDefaultLauncher() {
        val doSwitch = {
            prefs.launcherLockEnabled = false
            showToast("Launcher Dibuka", "Silakan pilih launcher bawaan HP di pengaturan Default Home.")
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Throwable) {}
            }
        }
        val pin = prefs.launcherLockPin
        if (pin.isNotEmpty()) {
            overlays.showInputPrompt(
                title = "Masukkan PIN Kunci Launcher",
                initialText = "",
                hintText = "PIN untuk membuka kunci ke launcher bawaan…",
                confirmLabel = "Buka Kunci"
            ) { entered ->
                if (entered == pin) doSwitch()
                else showToast("PIN Salah", "Kunci launcher tetap aktif di HyperDeX.")
            }
        } else {
            doSwitch()
        }
    }

    private fun runBootSplashSequence() {
        bootStage.removeAllViews()
        bootStage.alpha = 1f
        bootStage.visibility = View.VISIBLE
        val theme = prefs.currentTheme

        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(9, 11, 16))
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }

        val logoIv = ImageView(this).apply {
            setImageDrawable(VectorIcons.get(this@MainActivity, "brand_logo", dp(58), theme.accent))
            scaleX = 0.86f; scaleY = 0.86f; alpha = 0f
        }
        splash.addView(logoIv, LinearLayout.LayoutParams(dp(58), dp(58)).apply { bottomMargin = dp(12) })

        val wordmark = UiKit.label(this, "H Y P E R D E X", 14.5f, UiKit.INK, bold = true).apply {
            gravity = Gravity.CENTER
            alpha = 0f
        }
        splash.addView(wordmark)

        val sub = UiKit.label(this, "Polyglot Micro Desktop OS · v6.0", 9.6f, UiKit.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(16))
        }
        splash.addView(sub)

        val track = FrameLayout(this).apply {
            background = UiKit.rounded(this@MainActivity, Color.argb(32, 255, 255, 255), 3)
        }
        val fill = View(this).apply {
            background = UiKit.rounded(this@MainActivity, theme.accent, 3)
            scaleX = 0f
            pivotX = 0f
        }
        track.addView(fill, FrameLayout.LayoutParams(-1, -1))
        splash.addView(track, LinearLayout.LayoutParams(dp(160), dp(3)).apply { bottomMargin = dp(10) })

        val statusLine = UiKit.label(
            this,
            "Rust · C11 · C++20 GLES3 · 4-Layer Shield OK",
            8.8f,
            Color.rgb(100, 116, 139)
        ).apply { gravity = Gravity.CENTER }
        splash.addView(statusLine)

        bootStage.addView(splash, FrameLayout.LayoutParams(-1, -1))

        logoIv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(380).setInterpolator(UiKit.fluentInterpolator).start()
        wordmark.animate().alpha(1f).setStartDelay(120).setDuration(340).start()
        fill.animate().scaleX(1f).setDuration(820).setInterpolator(UiKit.fluentInterpolator).start()

        bootStage.postDelayed({
            if (isFinishing) return@postDelayed
            if (!prefs.onboardingCompleted) {
                showOnboardingWizard()
            } else {
                bootStage.animate().alpha(0f).setDuration(220).withEndAction {
                    bootStage.removeAllViews()
                    bootStage.visibility = View.GONE
                    bootStage.alpha = 1f
                }.start()
            }
        }, 940L)
    }

    override fun showOnboardingWizard() {
        bootStage.removeAllViews()
        bootStage.alpha = 1f
        bootStage.visibility = View.VISIBLE
        val theme = prefs.currentTheme
        val dm = resources.displayMetrics

        val outer = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(246, 9, 11, 16))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val cardScroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.strokeRounded(this@MainActivity, Color.rgb(16, 19, 26), Color.argb(45, 255, 255, 255), 10)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Top Brand Row
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(ImageView(this).apply {
            setImageDrawable(VectorIcons.get(this@MainActivity, "brand_logo", dp(30), theme.accent))
        }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(10) })
        val headText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headText.addView(UiKit.label(this, "Selamat Datang di HyperDeX v6.0", 12.2f, UiKit.INK, bold = true))
        headText.addView(UiKit.label(this, "Konfigurasi awal ukuran UI micro, Custom Floating Window & Kunci Default Launcher", 9.4f, UiKit.MUTED, maxLines = 2))
        head.addView(headText, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // Step 1: Micro UI Density Selection
        card.addView(UiKit.label(this, "1. Pilih Ukuran Antarmuka (Bisa diatur ulang kapan saja di Control Panel)", 10.0f, theme.accent, bold = true))
        val scaleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(8))
        }
        listOf("68% Ultra Micro" to 68, "78% Kecil PC" to 78, "84% Ideal" to 84, "96% Sedang" to 96).forEachIndexed { idx, (lbl, pct) ->
            val active = (prefs.uiScalePercent == pct)
            scaleRow.addView(UiKit.chipButton(
                this,
                lbl,
                bgColor = if (active) Color.argb(95, 14, 165, 233) else Color.rgb(24, 28, 37),
                strokeColor = if (active) theme.accent else Color.argb(36, 255, 255, 255)
            ) {
                prefs.uiScalePercent = pct
                VectorIcons.clearCache()
                refreshDesktopUI()
                showOnboardingWizard()
            }, LinearLayout.LayoutParams(0, dp(25), 1f).apply { if (idx < 3) rightMargin = dp(4) })
        }
        card.addView(scaleRow)

        // Step 2: Custom Floating Window Permission
        val canOv = SystemFloatingOverlay.canDraw(this)
        val step2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.strokeRounded(this@MainActivity, Color.rgb(21, 25, 34), Color.argb(30, 255, 255, 255), 7)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        val s2Col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        s2Col.addView(UiKit.label(this, "2. Custom Floating Window Engine (Tanpa Freeform Bawaan Android)", 10.0f, UiKit.INK, bold = true))
        s2Col.addView(UiKit.label(this, if (canOv) "Izin Overlay Aktif — Jendela custom bisa melayang di atas semua aplikasi" else "Aktifkan izin Overlay agar jendela custom HyperDeX bisa melayang bebas", 9.0f, if (canOv) Color.rgb(74, 222, 128) else UiKit.MUTED, maxLines = 2))
        step2.addView(s2Col, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        step2.addView(UiKit.chipButton(
            this,
            if (canOv) "Aktif ✓" else "Izinkan Overlay",
            bgColor = if (canOv) Color.argb(90, 16, 185, 129) else Color.rgb(28, 34, 46)
        ) {
            if (!canOv) SystemFloatingOverlay.requestOverlayPermission(this)
        }, LinearLayout.LayoutParams(-2, dp(25)))
        card.addView(step2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        // Step 3: Set Default Launcher & Automatic Launcher Lock
        val isHome = isDefaultHomeActive()
        val step3 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.strokeRounded(
                this@MainActivity,
                Color.rgb(21, 25, 34),
                if (isHome) Color.argb(120, 52, 211, 153) else Color.argb(75, 56, 189, 248),
                7
            )
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        step3.addView(UiKit.label(this, "3. Set Default Home & Kunci Otomatis di HyperDeX", 10.2f, UiKit.INK, bold = true))
        step3.addView(
            UiKit.label(
                this,
                "Begitu dijadikan Default Launcher, sistem otomatis mengunci di HyperDeX — tombol Home/Back maupun launcher bawaan HP diblokir sampai kamu memilih 'Unlock & Ganti ke Bawaan' di Control Panel.",
                9.0f,
                UiKit.MUTED,
                maxLines = 3
            ).apply { setPadding(0, dp(2), 0, dp(8)) }
        )

        val ctaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        ctaRow.addView(UiKit.chipButton(this, "Lanjut ke Desktop Dulu") {
            prefs.onboardingCompleted = true
            bootStage.animate().alpha(0f).setDuration(180).withEndAction {
                bootStage.removeAllViews()
                bootStage.visibility = View.GONE
                bootStage.alpha = 1f
            }.start()
        }, LinearLayout.LayoutParams(-2, dp(27)).apply { rightMargin = dp(6) })

        ctaRow.addView(UiKit.chipButton(
            this,
            if (isHome) "Sudah Default & Terkunci ✓ — Masuk" else "Set Default Launcher & Kunci",
            bgColor = Color.argb(175, 14, 165, 233),
            strokeColor = theme.accent
        ) {
            prefs.launcherLockEnabled = true
            prefs.onboardingCompleted = true
            if (isHome) {
                bootStage.visibility = View.GONE
                bootStage.removeAllViews()
            } else {
                requestSetDefaultLauncher()
            }
        }, LinearLayout.LayoutParams(-2, dp(27)))
        step3.addView(ctaRow)
        card.addView(step3)

        cardScroll.addView(card)
        val maxW = min(dp(440), (dm.widthPixels * 0.94f).toInt())
        outer.addView(cardScroll, FrameLayout.LayoutParams(maxW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        bootStage.addView(outer, FrameLayout.LayoutParams(-1, -1))
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
                // Scan installed HOME launchers so Launcher Lock can hide & block stock OEM launchers!
                val homeQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                @Suppress("DEPRECATION")
                val homeList = try { pm.queryIntentActivities(homeQuery, 0) } catch (_: Throwable) { emptyList() }
                val homePkgs = HashSet<String>()
                for (h in homeList) {
                    val hp = h?.activityInfo?.packageName ?: continue
                    if (hp != packageName) homePkgs.add(hp)
                }
                synchronized(detectedHomePackages) {
                    detectedHomePackages.clear()
                    detectedHomePackages.addAll(homePkgs)
                }

                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                val rawList = try { pm.queryIntentActivities(query, 0) } catch (_: Throwable) { emptyList() }

                val parsed = mutableListOf<AndroidAppEntry>()
                val seen = HashSet<String>()
                for (ri in rawList) {
                    val ai = ri?.activityInfo ?: continue
                    val pkg = ai.packageName ?: continue
                    val cls = ai.name ?: continue
                    if (pkg == packageName || isStockLauncherPackage(pkg) || !seen.add(pkg)) continue

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
        val tbHeight = dp(prefs.taskbarHeightDp)
        taskbarHost.layoutParams = LinearLayout.LayoutParams(-1, tbHeight)
        taskbarHost.setPadding(
            if (prefs.flushTaskbar) 0 else dp(8),
            if (prefs.flushTaskbar) 0 else dp(2),
            if (prefs.flushTaskbar) 0 else dp(8),
            if (prefs.flushTaskbar) 0 else dp(4)
        )
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

    // ==================== LAYER 1: CLEAN MICRO PC DESKTOP CANVAS (ZERO CLUTTER) ====================
    private fun renderDesktopSurface() {
        desktopSurfaceHost.removeAllViews()
        val theme = prefs.currentTheme
        val dm = resources.displayMetrics
        val availWidthUnits = dm.widthPixels / UiKit.unit(this)

        val stageCanvas = FrameLayout(this).apply {
            setPadding(dp(8), dp(6), dp(8), dp(4))
            setOnLongClickListener {
                showDesktopBlankContextMenu()
                true
            }
        }

        // Subtle Non-Intrusive Top-Right PC Clock & Launcher Lock Watermark
        val now = Date()
        val isLockedHome = isDefaultHomeActive() && prefs.launcherLockEnabled
        val watermark = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            alpha = 0.78f
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        watermark.addView(UiKit.label(this, SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 16.5f, UiKit.INK, bold = true))
        watermark.addView(
            UiKit.label(
                this,
                SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(now) + if (isLockedHome) " · LOCKED" else "",
                8.4f,
                if (isLockedHome) Color.rgb(74, 222, 128) else UiKit.MUTED,
                bold = true
            )
        )
        stageCanvas.addView(watermark, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))

        // Crisp Micro Desktop Grid (0=Micro[Default], 1=Small, 2=Medium, 3=Large)
        val gridWidthUnits = (availWidthUnits - 16f).coerceAtLeast(240f)
        val targetCellUnits = when (prefs.iconScale) {
            0 -> 58f
            1 -> 68f
            2 -> 80f
            else -> 94f
        }
        val cols = (gridWidthUnits / targetCellUnits).toInt().coerceIn(4, 14)
        val cellHeightDp = when (prefs.iconScale) {
            0 -> 54
            1 -> 62
            2 -> 72
            else -> 84
        }
        val iconSizeDp = when (prefs.iconScale) {
            0 -> 25
            1 -> 30
            2 -> 35
            else -> 42
        }

        val tiles = mutableListOf<View>()

        // 1. Built-in PC Apps
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
                                "Buka Jendela" to { launchBuiltInByKey(bApp.key) },
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
                                "Jalankan (.EXE)" to { openExeFile(exeFile) },
                                "Inspeksi PE Header" to {
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
                                "Buka Folder" to { openFolderFloatingWindow(folder) },
                                "Kelola Isi / Nama" to { showCreateOrEditFolderWindow(folder) },
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
        scroll.addView(UiKit.buildGrid(this, cols, cellHeightDp, tiles, hGapDp = 2, vGapDp = 3))
        stageCanvas.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        desktopSurfaceHost.addView(stageCanvas, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showDesktopBlankContextMenu(rawX: Float = -1f, rawY: Float = -1f) {
        val isHome = isDefaultHomeActive()
        overlays.showContextMenu(
            title = "HyperDeX Desktop",
            rawX = rawX,
            rawY = rawY,
            items = listOf(
                "Ukuran UI & Pengaturan" to { openControlPanel() },
                "Tambah Shortcut Aplikasi" to { showDesktopShortcutPickerWindow() },
                "Buat Folder Baru" to { showCreateOrEditFolderWindow(null) },
                "Install / Buka .EXE" to { openExeCenter(null) },
                "Ganti Tema Graphite" to { prefs.cycleTheme(); refreshDesktopUI() },
                (if (isHome && prefs.launcherLockEnabled) "Unlock & Ganti Launcher Bawaan" else "Set Default Launcher & Lock") to {
                    if (isHome && prefs.launcherLockEnabled) unlockAndSwitchDefaultLauncher()
                    else { prefs.launcherLockEnabled = true; requestSetDefaultLauncher() }
                },
                "Custom Floating & Shizuku" to { openShizukuHub() },
                "HyperShield Security Core" to { openNativeHub() },
                "Buka Terminal" to { openTerminal(null) }
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
            background = null // Clean transparent PC desktop tile (zero boxy AI-slop cards!)
            setPadding(dp(2), dp(3), dp(2), dp(2))
            setOnTouchListener { v, ev ->
                lastRawX = ev.rawX
                lastRawY = ev.rawY
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.background = UiKit.rounded(this@MainActivity, Color.argb(32, 255, 255, 255), 6)
                        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(75).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.background = null
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(UiKit.springInterpolator).start()
                    }
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
                setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(this@MainActivity, 9.1f))
                setTextColor(UiKit.INK)
                setShadowLayer(4f, 0f, 1.4f, Color.argb(225, 0, 0, 0))
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(1), dp(2), dp(1), 0)
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    // ==================== LAYER 4: SLIM GRAPHITE PC TASKBAR ====================
    private fun renderTaskbar() {
        taskbarHost.removeAllViews()
        val theme = prefs.currentTheme
        val outerWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (prefs.flushTaskbar) {
            outerWrap.addView(View(this).apply {
                setBackgroundColor(Color.argb(34, 255, 255, 255))
            }, LinearLayout.LayoutParams(-1, 1))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = if (prefs.flushTaskbar) {
                GradientDrawable().apply { setColor(theme.taskbarBg) }
            } else {
                UiKit.strokeRounded(this@MainActivity, theme.taskbarBg, Color.argb(42, 255, 255, 255), 9)
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }

        if (prefs.centerTaskbar && isWideDesktop()) {
            bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 0.35f))
        }

        val dm = resources.displayMetrics
        val availWUnits = dm.widthPixels / UiKit.unit(this)
        val compactBar = availWUnits < 480f
        val btnH = (dp(prefs.taskbarHeightDp) - dp(6)).coerceAtLeast(dp(22))

        // Isometric Titanium 'HX' Start Button
        val startBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = UiKit.rounded(this@MainActivity, Color.argb(22, 255, 255, 255), 6)
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(VectorIcons.get(this@MainActivity, "start_orb", dp(18), theme.accent))
            }, LinearLayout.LayoutParams(dp(18), dp(18)))
            UiKit.attachPressBounce(this, 0.90f)
            setOnClickListener { showStartMenuOverlay(focusSearch = false) }
        }
        bar.addView(startBtn, LinearLayout.LayoutParams(dp(28), btnH).apply { rightMargin = dp(3) })

        if (!compactBar) {
            val searchBtn = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = UiKit.rounded(this@MainActivity, Color.argb(18, 255, 255, 255), 6)
                setPadding(dp(7), 0, dp(8), 0)
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(VectorIcons.get(this@MainActivity, "search", dp(12), UiKit.MUTED))
                }, LinearLayout.LayoutParams(dp(12), dp(12)))
                if (isWideDesktop()) {
                    addView(UiKit.label(this@MainActivity, "  Cari", 9.4f, UiKit.MUTED))
                }
                UiKit.attachPressBounce(this, 0.92f)
                setOnClickListener { showStartMenuOverlay(focusSearch = true) }
            }
            bar.addView(searchBtn, LinearLayout.LayoutParams(-2, btnH).apply { rightMargin = dp(4) })
        }

        // Virtual Desktop Switcher
        if (compactBar) {
            val curWs = windowManager.currentWorkspace
            val wsCompact = UiKit.label(this, "${curWs + 1}", 9.2f, theme.accent, bold = true).apply {
                gravity = Gravity.CENTER
                background = UiKit.strokeRounded(this@MainActivity, Color.argb(22, 255, 255, 255), Color.argb(55, 255, 255, 255), 5)
                setOnClickListener {
                    val next = (windowManager.currentWorkspace + 1) % 3
                    windowManager.switchWorkspace(next)
                }
            }
            bar.addView(wsCompact, LinearLayout.LayoutParams(dp(22), btnH - dp(2)).apply { rightMargin = dp(3) })
        } else {
            val wsBox = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.rounded(this@MainActivity, Color.argb(18, 255, 255, 255), 6)
                setPadding(dp(2), dp(1), dp(2), dp(1))
            }
            for (ws in 0..2) {
                val activeWs = (ws == windowManager.currentWorkspace)
                val wsBtn = UiKit.label(
                    this,
                    "${ws + 1}",
                    8.8f,
                    if (activeWs) Color.WHITE else UiKit.MUTED,
                    bold = true
                ).apply {
                    gravity = Gravity.CENTER
                    background = UiKit.rounded(
                        this@MainActivity,
                        if (activeWs) Color.argb(165, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent)) else Color.TRANSPARENT,
                        4
                    )
                    setOnClickListener { windowManager.switchWorkspace(ws) }
                }
                wsBox.addView(wsBtn, LinearLayout.LayoutParams(dp(16), btnH - dp(4)).apply { if (ws < 2) rightMargin = dp(1) })
            }
            bar.addView(wsBox, LinearLayout.LayoutParams(-2, btnH - dp(2)).apply { rightMargin = dp(4) })
        }

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
                background = UiKit.rounded(this@MainActivity, Color.argb(14, 255, 255, 255), 6)
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(bApp.icon(this@MainActivity, dp(17)))
                }, LinearLayout.LayoutParams(dp(17), dp(17)))
                UiKit.attachPressBounce(this, 0.90f)
                setOnClickListener { launchBuiltInByKey(bApp.key) }
            }
            midRow.addView(holder, LinearLayout.LayoutParams(dp(27), btnH).apply { rightMargin = dp(3) })
        }

        for (pkg in prefs.getPinnedPackages()) {
            val app = appByPkg[pkg] ?: continue
            val holder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = UiKit.rounded(this@MainActivity, Color.argb(14, 255, 255, 255), 6)
                addView(ImageView(this@MainActivity).apply {
                    if (app.iconBitmap != null) setImageBitmap(app.iconBitmap)
                }, LinearLayout.LayoutParams(dp(17), dp(17)))
                UiKit.attachPressBounce(this, 0.90f)
                setOnClickListener { launchAndroidApp(app) }
                setOnLongClickListener { showAndroidAppContextMenu(app, -1f, -1f); true }
            }
            midRow.addView(holder, LinearLayout.LayoutParams(dp(27), btnH).apply { rightMargin = dp(3) })
        }

        val openWins = windowManager.openWindows(currentWorkspaceOnly = true)
        if (openWins.isNotEmpty()) {
            midRow.addView(View(this).apply {
                setBackgroundColor(Color.argb(38, 255, 255, 255))
            }, LinearLayout.LayoutParams(1, dp(15)).apply { leftMargin = dp(3); rightMargin = dp(4) })
        }

        for (win in openWins) {
            val isFocused = (win.id == windowManager.activeWindowId && !win.minimized)
            val tabWrap = FrameLayout(this).apply {
                background = UiKit.rounded(
                    this@MainActivity,
                    if (isFocused) Color.argb(36, 255, 255, 255) else Color.argb(14, 255, 255, 255),
                    6
                )
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
            val tabRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7), 0, dp(7), dp(2))
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(VectorIcons.get(this@MainActivity, win.iconId, dp(14)))
                }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { if (!prefs.compactTaskbarIconsOnly) rightMargin = dp(5) })

                if (!prefs.compactTaskbarIconsOnly) {
                    val shortTitle = if (win.title.length > 13) win.title.take(12) + "…" else win.title
                    addView(UiKit.label(this@MainActivity, shortTitle, 9.4f, if (win.minimized) UiKit.MUTED else UiKit.INK, bold = isFocused))
                }
            }
            tabWrap.addView(tabRow, FrameLayout.LayoutParams(-2, -1))
            // Crisp Win11 bottom active pill line
            tabWrap.addView(View(this).apply {
                background = UiKit.rounded(this@MainActivity, if (isFocused) theme.accent else Color.argb(95, 148, 163, 184), 2)
            }, FrameLayout.LayoutParams(if (isFocused) dp(16) else dp(6), dp(2), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 1 })

            midRow.addView(tabWrap, LinearLayout.LayoutParams(-2, btnH).apply { rightMargin = dp(3) })
        }

        midScroll.addView(midRow)
        bar.addView(midScroll, LinearLayout.LayoutParams(0, btnH, 1f))

        // Right Micro System Tray
        val (bat, chg) = batteryInfo()
        val now = Date()
        val tray = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(this@MainActivity, Color.argb(18, 255, 255, 255), 6)
            setPadding(dp(6), dp(1), dp(6), dp(1))
            UiKit.attachPressBounce(this, 0.94f)
            setOnClickListener { showQuickSettingsOverlay() }
        }
        if (isDefaultHomeActive() && prefs.launcherLockEnabled) {
            tray.addView(ImageView(this).apply {
                setImageDrawable(VectorIcons.get(this@MainActivity, "lock_home", dp(12), Color.rgb(74, 222, 128)))
            }, LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(4) })
        }
        tray.addView(ImageView(this).apply {
            setImageDrawable(VectorIcons.get(this@MainActivity, "tray_status", dp(13)))
        }, LinearLayout.LayoutParams(dp(13), dp(13)).apply { rightMargin = dp(4) })

        tray.addView(
            UiKit.label(this, "${if (chg) "+" else ""}$bat%", 9.0f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) }
        )

        val clockCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clockCol.addView(UiKit.label(this, SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 9.2f, UiKit.INK, bold = true))
        clockCol.addView(UiKit.label(this, SimpleDateFormat("dd/MM", Locale.getDefault()).format(now), 7.6f, UiKit.MUTED))
        tray.addView(clockCol)
        bar.addView(tray, LinearLayout.LayoutParams(-2, btnH).apply { leftMargin = dp(4) })

        // Show Desktop Sliver
        bar.addView(View(this).apply {
            background = UiKit.rounded(this@MainActivity, Color.argb(55, 255, 255, 255), 2)
            setOnClickListener { windowManager.minimizeAll() }
        }, LinearLayout.LayoutParams(dp(4), btnH - dp(6)).apply { leftMargin = dp(4) })

        outerWrap.addView(bar, LinearLayout.LayoutParams(-1, 0, 1f))
        taskbarHost.addView(outerWrap, FrameLayout.LayoutParams(-1, -1))
    }

    // ==================== COMPACT GRAPHITE START MENU & ACTION CENTER ====================
    private fun showStartMenuOverlay(focusSearch: Boolean) {
        val theme = prefs.currentTheme
        val dm = resources.displayMetrics
        val isHomeLocked = isDefaultHomeActive() && prefs.launcherLockEnabled

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(18).toFloat()
            setPadding(dp(11), dp(10), dp(11), dp(9))
            background = UiKit.strokeRounded(this@MainActivity, Color.argb(248, 17, 20, 27), Color.argb(46, 255, 255, 255), 9)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageDrawable(VectorIcons.get(this@MainActivity, "start_orb", dp(17), theme.accent))
        }, LinearLayout.LayoutParams(dp(17), dp(17)).apply { rightMargin = dp(6) })
        header.addView(
            UiKit.label(this, if (isHomeLocked) "HyperDeX · Locked" else "HyperDeX", 11.4f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        header.addView(UiKit.chipButton(this, "Shield") {
            overlays.dismissOverlay(); openNativeHub()
        }, LinearLayout.LayoutParams(-2, dp(22)).apply { rightMargin = dp(3) })
        header.addView(UiKit.chipButton(this, "Floating") {
            overlays.dismissOverlay(); openShizukuHub()
        }, LinearLayout.LayoutParams(-2, dp(22)).apply { rightMargin = dp(3) })
        header.addView(UiKit.chipButton(this, "Settings") {
            overlays.dismissOverlay(); openControlPanel()
        }, LinearLayout.LayoutParams(-2, dp(22)))
        panel.addView(header)

        val searchBox = EditText(this).apply {
            hint = "Ketik untuk mencari aplikasi atau .EXE…"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(this@MainActivity, 10.2f))
            setSingleLine(true)
            background = UiKit.strokeRounded(this@MainActivity, Color.rgb(24, 28, 37), Color.argb(35, 255, 255, 255), 6)
            setPadding(dp(9), 0, dp(9), 0)
        }
        panel.addView(searchBox, LinearLayout.LayoutParams(-1, dp(27)).apply {
            topMargin = dp(6); bottomMargin = dp(6)
        })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val gridHolder = FrameLayout(this)
        scroll.addView(gridHolder)
        val availStageH = overlayStage.height.takeIf { it > 0 } ?: (dm.heightPixels - dp(40))
        val menuH = (availStageH - dp(100)).coerceIn(dp(80), dp(245))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, menuH))

        val cols = if (isWideDesktop()) 6 else 4

        fun populate(q: String) {
            gridHolder.removeAllViews()
            val tiles = mutableListOf<View>()

            BuiltInApps.all.filter { it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true) }
                .forEach { bApp ->
                    tiles.add(
                        desktopTileView(bApp.title, bApp.icon(this, dp(26)), 26,
                            onClick = { overlays.dismissOverlay(); launchBuiltInByKey(bApp.key) },
                            onLongClick = { _, _ -> overlays.dismissOverlay(); launchBuiltInByKey(bApp.key) })
                    )
                }

            installedApps.filter { it.label.contains(q, ignoreCase = true) }
                .forEach { app ->
                    val drw: Drawable = if (app.iconBitmap != null) BitmapDrawable(resources, app.iconBitmap)
                    else VectorIcons.get(this, "explorer", dp(26))
                    tiles.add(
                        desktopTileView(app.label, drw, 26,
                            onClick = { overlays.dismissOverlay(); launchAndroidApp(app) },
                            onLongClick = { rx, ry -> showAndroidAppContextMenu(app, rx, ry) })
                    )
                }

            gridHolder.addView(UiKit.buildGrid(this, cols, 58, tiles, hGapDp = 2, vGapDp = 2))
        }
        populate("")

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = populate(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // Slim Footer Bar with Launcher Lock / Switch Control
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        footer.addView(
            UiKit.label(
                this,
                if (isHomeLocked) "Default Home Locked" else "Mode Preview (Belum Default)",
                8.8f,
                if (isHomeLocked) Color.rgb(74, 222, 128) else UiKit.MUTED,
                bold = true
            ),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        footer.addView(UiKit.chipButton(
            this,
            if (isHomeLocked) "Unlock & Ganti Launcher" else "Set Default & Lock",
            bgColor = if (isHomeLocked) Color.argb(95, 185, 28, 28) else Color.argb(120, 14, 165, 233)
        ) {
            overlays.dismissOverlay()
            if (isHomeLocked) unlockAndSwitchDefaultLauncher()
            else { prefs.launcherLockEnabled = true; requestSetDefaultLauncher() }
        }, LinearLayout.LayoutParams(-2, dp(23)))
        panel.addView(footer)

        val w = if (isWideDesktop()) min(dp(400), (dm.widthPixels * 0.62f).toInt()) else (dm.widthPixels * 0.92f).toInt()
        val grav = if (prefs.centerTaskbar && isWideDesktop()) (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL) else (Gravity.BOTTOM or Gravity.START)
        overlays.showAnchoredPanel(panel, widthPx = w, gravity = grav, marginX = dp(6), marginY = dp(4))
        if (focusSearch) searchBox.requestFocus()
    }

    private fun showQuickSettingsOverlay() {
        val theme = prefs.currentTheme
        val dm = resources.displayMetrics

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(18).toFloat()
            setPadding(dp(11), dp(10), dp(11), dp(10))
            background = UiKit.strokeRounded(this@MainActivity, Color.argb(248, 17, 20, 27), Color.argb(46, 255, 255, 255), 9)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(UiKit.label(this, "Action Center", 11.4f, UiKit.INK, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val (bat, chg) = batteryInfo()
        head.addView(UiKit.label(this, "${if (chg) "Charging " else ""}$bat%", 10.0f, theme.accent, bold = true))
        card.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        fun qTile(title: String, sub: String, active: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(7), dp(4))
            val bg = if (active) Color.argb(85, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
            else Color.rgb(24, 28, 37)
            background = UiKit.strokeRounded(this@MainActivity, bg, if (active) theme.accent else Color.argb(32, 255, 255, 255), 6)
            UiKit.attachPressBounce(this, 0.94f)
            setOnClickListener { action() }
            addView(UiKit.label(this@MainActivity, title, 9.8f, UiKit.INK, bold = true))
            addView(UiKit.label(this@MainActivity, sub, 8.2f, if (active) UiKit.INK else UiKit.MUTED))
        }

        val hudActive = prefs.systemOverlayEnabled && SystemFloatingOverlay.canDraw(this)
        val tiles = listOf(
            qTile("Wi-Fi", "Jaringan", true) { overlays.dismissOverlay(); openSystemAction(Settings.ACTION_WIFI_SETTINGS) },
            qTile("Bluetooth", "Perangkat", false) { overlays.dismissOverlay(); openSystemAction(Settings.ACTION_BLUETOOTH_SETTINGS) },
            qTile("Mode PC", if (prefs.landscapeLock) "Landscape" else "Adaptif", prefs.landscapeLock) {
                prefs.landscapeLock = !prefs.landscapeLock
                overlays.dismissOverlay()
                refreshDesktopUI()
            },
            qTile("Full Layar", if (prefs.immersiveMode) "Aktif" else "Standar", prefs.immersiveMode) {
                prefs.immersiveMode = !prefs.immersiveMode
                overlays.dismissOverlay()
                refreshDesktopUI()
            },
            qTile("Overlay", if (hudActive) "Aktif" else "Mati", hudActive) {
                overlays.dismissOverlay()
                if (!SystemFloatingOverlay.canDraw(this)) {
                    SystemFloatingOverlay.requestOverlayPermission(this)
                } else {
                    prefs.systemOverlayEnabled = !prefs.systemOverlayEnabled
                    if (prefs.systemOverlayEnabled) SystemFloatingOverlay.show(this, prefs) else SystemFloatingOverlay.hide()
                }
            },
            qTile("Tema", theme.name, false) {
                prefs.cycleTheme()
                refreshDesktopUI()
                showQuickSettingsOverlay()
            }
        )
        card.addView(UiKit.buildGrid(this, 3, 40, tiles, hGapDp = 4, vGapDp = 4), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxV = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curV = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxV)
            val volRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.rounded(this@MainActivity, Color.rgb(24, 28, 37), 6)
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
            volRow.addView(UiKit.label(this, "Vol", 9.4f, UiKit.INK, bold = true))
            volRow.addView(
                UiKit.buildFluentSlider(this, 0, maxV, curV, theme.accent) { v ->
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                },
                LinearLayout.LayoutParams(0, dp(22), 1f)
            )
            card.addView(volRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        } catch (_: Throwable) {
        }

        card.addView(
            UiKit.label(this, "${memorySummary()}  ·  UI Scale: ${prefs.uiScalePercent}%", 9.0f, UiKit.MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
        )

        val foot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        foot.addView(UiKit.chipButton(this, "Custom Floating") {
            overlays.dismissOverlay(); openShizukuHub()
        }, LinearLayout.LayoutParams(0, dp(25), 1f).apply { rightMargin = dp(4) })
        foot.addView(UiKit.chipButton(this, "Ukuran & Pengaturan") {
            overlays.dismissOverlay(); openControlPanel()
        }, LinearLayout.LayoutParams(0, dp(25), 1f))
        card.addView(foot)

        val availStageH = overlayStage.height.takeIf { it > 0 } ?: (dm.heightPixels - dp(40))
        val finalPanel: View = if (availStageH < dp(285)) {
            ScrollView(this).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                background = UiKit.strokeRounded(this@MainActivity, Color.argb(248, 17, 20, 27), Color.argb(46, 255, 255, 255), 9)
                card.background = null
                addView(card)
            }
        } else card
        val hCap = if (availStageH < dp(285)) (availStageH - dp(10)).coerceAtLeast(dp(130)) else FrameLayout.LayoutParams.WRAP_CONTENT
        val w = if (isWideDesktop()) min(dp(300), (dm.widthPixels * 0.80f).toInt()) else (dm.widthPixels * 0.92f).toInt()
        overlays.showAnchoredPanel(finalPanel, widthPx = w, heightPx = hCap, gravity = Gravity.BOTTOM or Gravity.END, marginX = dp(6), marginY = dp(4))
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

    // ==================== CUSTOM FLOATING CONTAINER & NATIVE APP LAUNCHER ====================
    private fun openAppInCustomFloatingContainer(app: AndroidAppEntry) {
        val url = SystemFloatingOverlay.cloudWebAppUrlForPackage(app.packageName, app.label)
        windowManager.openWindow(
            appKey = "custom_app_${app.packageName}",
            title = "${app.label} (Custom Window)",
            iconId = "browser",
            singleInstance = false,
            preferredWidthDp = 500,
            preferredHeightDp = 330
        ) { winState ->
            BrowserAndMedia.buildBrowserView(this, winState, url)
        }
    }

    private fun launchAndroidApp(app: AndroidAppEntry, preferShizukuWindow: Boolean = true) {
        // Hard Block stock OEM launchers when HyperDeX is locked as Default Home!
        if (isDefaultHomeActive() && prefs.launcherLockEnabled && isStockLauncherPackage(app.packageName)) {
            showToast("Launcher Terkunci", "Launcher bawaan diblokir selama HyperDeX menjadi Default Home.")
            return
        }

        if (SystemFloatingOverlay.canDraw(this)) {
            SystemFloatingOverlay.attachCustomAppTopBar(this, prefs, app.packageName, app.activityName, app.label)
        } else if (prefs.systemOverlayEnabled && SystemFloatingOverlay.canDraw(this)) {
            SystemFloatingOverlay.show(this, prefs)
        }

        val standardLaunch = {
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(app.packageName, app.activityName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                startActivity(intent)
            } catch (_: Throwable) {
                showToast("Launcher", "Aplikasi tidak dapat dibuka")
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
                "Custom Floating Window (Tanpa UI Android)" to { openAppInCustomFloatingContainer(app) },
                "Native App + Custom Floating Bar" to { launchAndroidApp(app, preferShizukuWindow = true) },
                "Buka Layar Penuh" to { launchAndroidApp(app, preferShizukuWindow = false) },
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
        if (requestCode == REQ_ROLE_HOME) {
            if (isDefaultHomeActive()) {
                prefs.launcherLockEnabled = true
                prefs.onboardingCompleted = true
                bootStage.visibility = View.GONE
                bootStage.removeAllViews()
                wasDefaultHomeLastCheck = true
                refreshDesktopUI()
                showToast("Launcher Terkunci", "HyperDeX kini Default Home. Launcher bawaan dikunci otomatis.")
            } else {
                refreshDesktopUI()
            }
            return
        }
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
