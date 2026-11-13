package dev.xykal.hyperdex

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DesktopWindowState(
    val id: String,
    val appKey: String,
    var title: String,
    val iconId: String,
    val frame: LinearLayout,
    val headerBar: LinearLayout,
    val titleView: TextView,
    val maxBtnIcon: ImageView,
    var minimized: Boolean = false,
    var maximized: Boolean = false,
    var savedX: Int = 0,
    var savedY: Int = 0,
    var savedW: Int = 0,
    var savedH: Int = 0,
    var workspace: Int = 0,
    var onCloseCleanup: (() -> Unit)? = null
)

class FloatingWindowManager(
    private val context: Context,
    val stage: FrameLayout,
    private val prefs: DesktopPrefs,
    private val onWindowsChanged: () -> Unit
) {
    private val windows = mutableListOf<DesktopWindowState>()
    var activeWindowId: String? = null
        private set
    var currentWorkspace: Int = 0
        private set

    private val snapGhost = View(context).apply {
        visibility = View.GONE
    }

    init {
        stage.addView(snapGhost, FrameLayout.LayoutParams(0, 0))
    }

    fun openWindows(currentWorkspaceOnly: Boolean = false): List<DesktopWindowState> =
        if (currentWorkspaceOnly) windows.filter { it.workspace == currentWorkspace } else windows.toList()

    fun switchWorkspace(wsIndex: Int) {
        val targetWs = wsIndex.coerceIn(0, 2)
        if (targetWs == currentWorkspace) return
        currentWorkspace = targetWs
        for (w in windows) {
            if (w.workspace == currentWorkspace && !w.minimized) {
                w.frame.visibility = View.VISIBLE
                w.frame.alpha = 0f
                w.frame.animate().alpha(1f).setDuration(150).start()
            } else {
                w.frame.visibility = View.GONE
            }
        }
        activeWindowId = windows.lastOrNull { it.workspace == currentWorkspace && !it.minimized }?.id
        refreshHeaderStyles()
        onWindowsChanged()
    }

    fun moveWindowToWorkspace(id: String, targetWs: Int) {
        val w = windows.find { it.id == id } ?: return
        val ws = targetWs.coerceIn(0, 2)
        w.workspace = ws
        if (ws != currentWorkspace) {
            w.frame.visibility = View.GONE
            if (activeWindowId == id) {
                activeWindowId = windows.lastOrNull { it.workspace == currentWorkspace && !it.minimized }?.id
            }
        } else if (!w.minimized) {
            w.frame.visibility = View.VISIBLE
            focusWindow(w.id)
        }
        refreshHeaderStyles()
        onWindowsChanged()
    }

    fun cycleNextWindow(): DesktopWindowState? {
        val wsWins = windows.filter { it.workspace == currentWorkspace }
        if (wsWins.isEmpty()) return null
        val curIdx = wsWins.indexOfFirst { it.id == activeWindowId }
        val next = wsWins[(curIdx + 1) % wsWins.size]
        focusWindow(next.id)
        return next
    }
    private fun dp(v: Int): Int = UiKit.dp(context, v)

    @SuppressLint("ClickableViewAccessibility")
    fun openWindow(
        appKey: String,
        title: String,
        iconId: String,
        singleInstance: Boolean = false,
        preferredWidthDp: Int = 510,
        preferredHeightDp: Int = 345,
        onCloseCleanup: (() -> Unit)? = null,
        contentBuilder: (DesktopWindowState) -> View
    ): DesktopWindowState {
        if (singleInstance) {
            val existing = windows.find { it.appKey == appKey }
            if (existing != null) {
                focusWindow(existing.id)
                return existing
            }
        }

        val dm = context.resources.displayMetrics
        val stageW = stage.width.takeIf { it > 0 } ?: (dm.widthPixels - dp(16))
        val stageH = stage.height.takeIf { it > 0 } ?: (dm.heightPixels - dp(72)).coerceAtLeast(dp(240))
        val isNarrow = stageW < dp(500)
        val isShort = stageH < dp(360)

        val initW = if (isNarrow) {
            (stageW * 0.96f).toInt()
        } else {
            min(dp(preferredWidthDp), (stageW * 0.88f).toInt()).coerceAtLeast(dp(230))
        }
        val initH = if (isNarrow || isShort) {
            min(dp(preferredHeightDp), (stageH * 0.92f).toInt()).coerceAtLeast(dp(170))
        } else {
            min(dp(preferredHeightDp), (stageH * 0.88f).toInt()).coerceAtLeast(dp(190))
        }

        val cascade = (windows.size % 6) * dp(20)
        val initX = ((stageW - initW) / 2 + cascade).coerceIn(dp(4), max(dp(4), stageW - initW - dp(4)))
        val initY = ((stageH - initH) / 3 + cascade).coerceIn(dp(4), max(dp(4), stageH - initH - dp(4)))

        val theme = prefs.currentTheme
        val winFrame = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            elevation = dp(14).toFloat()
            background = UiKit.strokeRounded(context, Color.rgb(15, 23, 38), theme.accent, 13, 1)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(6), dp(4))
            background = UiKit.rounded(context, theme.windowHeaderActive, 12)
        }

        val appIcon = ImageView(context).apply {
            setImageDrawable(VectorIcons.get(context, iconId, dp(20), theme.accent))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(appIcon, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) })

        val titleTv = UiKit.label(context, title, 12f, UiKit.INK, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(titleTv, LinearLayout.LayoutParams(0, dp(26), 1f))

        val maxImg = ImageView(context)
        val id = UUID.randomUUID().toString()
        val state = DesktopWindowState(
            id = id,
            appKey = appKey,
            title = title,
            iconId = iconId,
            frame = winFrame,
            headerBar = header,
            titleView = titleTv,
            maxBtnIcon = maxImg,
            minimized = false,
            maximized = isNarrow,
            savedX = initX,
            savedY = initY,
            savedW = initW,
            savedH = initH,
            workspace = currentWorkspace,
            onCloseCleanup = onCloseCleanup
        )

        fun syncMaxIcon() {
            maxImg.setImageDrawable(
                VectorIcons.get(context, if (state.maximized) "win_restore" else "win_max", dp(18))
            )
        }
        syncMaxIcon()

        fun chromeBtn(vecId: String, bg: Int, customImg: ImageView? = null, onClick: () -> Unit): LinearLayout {
            return LinearLayout(context).apply {
                gravity = Gravity.CENTER
                background = UiKit.rounded(context, bg, 7)
                val iv = customImg ?: ImageView(context).apply {
                    setImageDrawable(VectorIcons.get(context, vecId, dp(18)))
                }
                addView(iv, LinearLayout.LayoutParams(dp(16), dp(16)))
                UiKit.attachPressBounce(this, 0.90f)
                setOnClickListener {
                    focusWindow(state.id)
                    onClick()
                }
            }
        }

        if (!isNarrow) {
            header.addView(chromeBtn("win_snap_l", Color.argb(30, 255, 255, 255)) {
                snapWindow(state, leftHalf = true)
            }, LinearLayout.LayoutParams(dp(26), dp(24)).apply { rightMargin = dp(3) })

            header.addView(chromeBtn("win_snap_r", Color.argb(30, 255, 255, 255)) {
                snapWindow(state, leftHalf = false)
            }, LinearLayout.LayoutParams(dp(26), dp(24)).apply { rightMargin = dp(5) })
        }

        header.addView(chromeBtn("win_min", Color.argb(36, 255, 255, 255)) {
            minimizeWindow(state.id)
        }, LinearLayout.LayoutParams(dp(28), dp(24)).apply { rightMargin = dp(4) })

        header.addView(chromeBtn("win_max", Color.argb(36, 255, 255, 255), customImg = maxImg) {
            toggleMaximize(state)
            syncMaxIcon()
        }, LinearLayout.LayoutParams(dp(28), dp(24)).apply { rightMargin = dp(4) })

        header.addView(chromeBtn("win_close", Color.argb(185, 220, 38, 38)) {
            closeWindow(state.id)
        }, LinearLayout.LayoutParams(dp(30), dp(24)))

        // Smooth Drag + Edge Aero Snap Preview + Double-Tap Maximize
        var dragStartX = 0f
        var dragStartY = 0f
        var startMarginX = 0
        var startMarginY = 0
        var lastDown = 0L
        var pendingSnapMode = 0 // 0=none, 1=left, 2=right, 3=max

        header.setOnTouchListener { _, ev ->
            val sw = stage.width.coerceAtLeast(dp(300))
            val sh = stage.height.coerceAtLeast(dp(220))
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    focusWindow(state.id)
                    val now = System.currentTimeMillis()
                    if (now - lastDown < 280L) {
                        toggleMaximize(state)
                        syncMaxIcon()
                        lastDown = 0L
                        return@setOnTouchListener true
                    }
                    lastDown = now
                    winFrame.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                    dragStartX = ev.rawX
                    dragStartY = ev.rawY
                    startMarginX = lp.leftMargin
                    startMarginY = lp.topMargin
                    pendingSnapMode = 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - dragStartX).toInt()
                    val dy = (ev.rawY - dragStartY).toInt()
                    if (state.maximized && (abs(dx) > dp(10) || abs(dy) > dp(10))) {
                        state.maximized = false
                        syncMaxIcon()
                        val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                        lp.width = state.savedW
                        lp.height = state.savedH
                        startMarginX = (ev.rawX.toInt() - state.savedW / 2).coerceIn(0, max(0, sw - state.savedW))
                        startMarginY = 0
                        dragStartX = ev.rawX
                        dragStartY = ev.rawY
                    }
                    if (!state.maximized) {
                        val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                        val nx = (startMarginX + dx).coerceIn(-lp.width + dp(85), sw - dp(85))
                        val ny = (startMarginY + dy).coerceIn(0, sh - dp(36))
                        lp.leftMargin = nx
                        lp.topMargin = ny
                        state.savedX = nx
                        state.savedY = ny
                        winFrame.layoutParams = lp

                        // Edge Aero Snap Ghost detection
                        val edgeZone = dp(22)
                        pendingSnapMode = when {
                            !isNarrow && ev.rawX <= edgeZone -> 1
                            !isNarrow && ev.rawX >= sw - edgeZone -> 2
                            ev.rawY <= dp(18) -> 3
                            else -> 0
                        }
                        updateSnapGhost(pendingSnapMode, sw, sh)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    winFrame.setLayerType(View.LAYER_TYPE_NONE, null)
                    snapGhost.visibility = View.GONE
                    when (pendingSnapMode) {
                        1 -> { snapWindow(state, leftHalf = true); syncMaxIcon() }
                        2 -> { snapWindow(state, leftHalf = false); syncMaxIcon() }
                        3 -> { if (!state.maximized) { toggleMaximize(state); syncMaxIcon() } }
                    }
                    pendingSnapMode = 0
                    true
                }
                else -> false
            }
        }

        winFrame.addView(header, LinearLayout.LayoutParams(-1, if (isShort) dp(32) else dp(36)))

        val bodyContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(13, 20, 34))
            setOnClickListener { focusWindow(state.id) }
        }

        val contentView = try {
            contentBuilder(state)
        } catch (t: Throwable) {
            UiKit.label(context, "Error memuat jendela: ${t.message}", 12f, Color.rgb(252, 165, 165), maxLines = 6).apply {
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
        }
        bodyContainer.addView(contentView, FrameLayout.LayoutParams(-1, -1))

        // Custom Diagonal Corner Resize Grip
        val resizeGrip = View(context).apply {
            background = UiKit.rounded(context, Color.argb(70, 148, 163, 184), 6)
        }
        var rsX = 0f
        var rsY = 0f
        var rsW = 0
        var rsH = 0
        resizeGrip.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    focusWindow(state.id)
                    winFrame.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    if (state.maximized) {
                        state.maximized = false
                        syncMaxIcon()
                    }
                    val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                    rsX = ev.rawX
                    rsY = ev.rawY
                    rsW = if (lp.width > 0) lp.width else stage.width
                    rsH = if (lp.height > 0) lp.height else stage.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                    val sw = stage.width.coerceAtLeast(dp(300))
                    val sh = stage.height.coerceAtLeast(dp(220))
                    val nw = (rsW + (ev.rawX - rsX).toInt()).coerceIn(dp(210), sw)
                    val nh = (rsH + (ev.rawY - rsY).toInt()).coerceIn(dp(155), sh)
                    lp.width = nw
                    lp.height = nh
                    state.savedW = nw
                    state.savedH = nh
                    winFrame.layoutParams = lp
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    winFrame.setLayerType(View.LAYER_TYPE_NONE, null)
                    true
                }
                else -> false
            }
        }
        bodyContainer.addView(resizeGrip, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(3); bottomMargin = dp(3)
        })

        winFrame.addView(bodyContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        val initialLp = if (state.maximized) {
            FrameLayout.LayoutParams(-1, -1)
        } else {
            FrameLayout.LayoutParams(initW, initH).apply {
                leftMargin = initX
                topMargin = initY
            }
        }

        winFrame.scaleX = 0.89f
        winFrame.scaleY = 0.89f
        winFrame.alpha = 0f

        windows.add(state)
        stage.addView(winFrame, initialLp)
        focusWindow(state.id)

        winFrame.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(190)
            .setInterpolator(UiKit.fluentInterpolator)
            .start()

        return state
    }

    private fun updateSnapGhost(mode: Int, sw: Int, sh: Int) {
        if (mode == 0) {
            snapGhost.visibility = View.GONE
            return
        }
        val accent = prefs.currentTheme.accent
        snapGhost.background = UiKit.strokeRounded(
            context,
            Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent)),
            accent,
            14,
            2
        )
        val pad = dp(6)
        val lp = when (mode) {
            1 -> FrameLayout.LayoutParams(sw / 2 - pad * 2, sh - pad * 2).apply { leftMargin = pad; topMargin = pad }
            2 -> FrameLayout.LayoutParams(sw / 2 - pad * 2, sh - pad * 2).apply { leftMargin = sw / 2 + pad; topMargin = pad }
            else -> FrameLayout.LayoutParams(sw - pad * 2, sh - pad * 2).apply { leftMargin = pad; topMargin = pad }
        }
        snapGhost.layoutParams = lp
        snapGhost.visibility = View.VISIBLE
        snapGhost.bringToFront()
        activeWindowId?.let { id -> windows.find { it.id == id }?.frame?.bringToFront() }
    }

    fun snapWindow(state: DesktopWindowState, leftHalf: Boolean) {
        val sw = stage.width.coerceAtLeast(dp(360))
        val sh = stage.height.coerceAtLeast(dp(220))
        state.maximized = false
        val halfW = sw / 2
        state.frame.layoutParams = FrameLayout.LayoutParams(halfW, sh).apply {
            leftMargin = if (leftHalf) 0 else halfW
            topMargin = 0
        }
        focusWindow(state.id)
    }

    fun toggleMaximize(state: DesktopWindowState) {
        state.maximized = !state.maximized
        state.frame.layoutParams = if (state.maximized) {
            FrameLayout.LayoutParams(-1, -1)
        } else {
            FrameLayout.LayoutParams(state.savedW, state.savedH).apply {
                leftMargin = state.savedX
                topMargin = state.savedY
            }
        }
        state.maxBtnIcon.setImageDrawable(
            VectorIcons.get(context, if (state.maximized) "win_restore" else "win_max", dp(18))
        )
        focusWindow(state.id)
    }

    fun focusWindow(id: String) {
        val target = windows.find { it.id == id } ?: return
        if (target.workspace != currentWorkspace) {
            switchWorkspace(target.workspace)
        }
        if (target.minimized) {
            target.minimized = false
            target.frame.visibility = View.VISIBLE
            target.frame.scaleX = 0.85f
            target.frame.scaleY = 0.85f
            target.frame.translationY = dp(60).toFloat()
            target.frame.alpha = 0f
            target.frame.animate()
                .scaleX(1f).scaleY(1f).translationY(0f).alpha(1f)
                .setDuration(180)
                .setInterpolator(UiKit.fluentInterpolator)
                .start()
        }
        target.frame.bringToFront()
        activeWindowId = id
        refreshHeaderStyles()
        onWindowsChanged()
    }

    fun toggleTaskbarWindow(id: String) {
        val target = windows.find { it.id == id } ?: return
        if (target.minimized) {
            focusWindow(id)
        } else if (activeWindowId == id) {
            minimizeWindow(id)
        } else {
            focusWindow(id)
        }
    }

    fun minimizeWindow(id: String) {
        val target = windows.find { it.id == id } ?: return
        target.minimized = true
        if (activeWindowId == id) {
            activeWindowId = windows.lastOrNull { !it.minimized }?.id
        }
        refreshHeaderStyles()
        onWindowsChanged()

        target.frame.animate()
            .scaleX(0.70f)
            .scaleY(0.70f)
            .translationY(dp(90).toFloat())
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                if (target.minimized) target.frame.visibility = View.GONE
                target.frame.translationY = 0f
                target.frame.scaleX = 1f
                target.frame.scaleY = 1f
            }
            .start()
    }

    fun minimizeAll() {
        windows.forEach {
            it.minimized = true
            it.frame.visibility = View.GONE
        }
        activeWindowId = null
        onWindowsChanged()
    }

    fun closeWindow(id: String) {
        val idx = windows.indexOfFirst { it.id == id }
        if (idx < 0) return
        val removed = windows.removeAt(idx)
        try {
            removed.onCloseCleanup?.invoke()
        } catch (_: Throwable) {
        }
        if (activeWindowId == id) {
            activeWindowId = windows.lastOrNull { !it.minimized }?.id
        }
        refreshHeaderStyles()
        onWindowsChanged()

        removed.frame.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .alpha(0f)
            .setDuration(140)
            .withEndAction { stage.removeView(removed.frame) }
            .start()
    }

    fun refreshHeaderStyles() {
        val theme = prefs.currentTheme
        for (w in windows) {
            val isActive = (w.id == activeWindowId && !w.minimized)
            w.headerBar.background = UiKit.rounded(
                context,
                if (isActive) theme.windowHeaderActive else theme.windowHeaderInactive,
                12
            )
            w.frame.background = UiKit.strokeRounded(
                context,
                Color.rgb(15, 23, 38),
                if (isActive) theme.accent else Color.argb(65, 255, 255, 255),
                13,
                1
            )
        }
    }
}
