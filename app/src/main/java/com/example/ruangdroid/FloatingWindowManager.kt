package com.example.ruangdroid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class DesktopWindowState(
    val id: String,
    val appKey: String,
    var title: String,
    val glyph: String,
    val frame: LinearLayout,
    val headerBar: LinearLayout,
    val titleView: TextView,
    var minimized: Boolean = false,
    var maximized: Boolean = false,
    var savedX: Int = 0,
    var savedY: Int = 0,
    var savedW: Int = 0,
    var savedH: Int = 0,
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

    fun openWindows(): List<DesktopWindowState> = windows.toList()

    private fun dp(v: Int): Int = UiKit.dp(context, v)

    /**
     * Opens a new floating window (or focuses existing if singleInstance == true and already open).
     */
    @SuppressLint("ClickableViewAccessibility")
    fun openWindow(
        appKey: String,
        title: String,
        glyph: String,
        singleInstance: Boolean = false,
        preferredWidthDp: Int = 500,
        preferredHeightDp: Int = 340,
        onCloseCleanup: (() -> Unit)? = null,
        contentBuilder: (DesktopWindowState) -> View
    ): DesktopWindowState {
        if (singleInstance) {
            val existing = windows.find { it.appKey == appKey }
            if (existing != null) {
                existing.minimized = false
                existing.frame.visibility = View.VISIBLE
                focusWindow(existing.id)
                return existing
            }
        }

        val dm = context.resources.displayMetrics
        val stageW = if (stage.width > 0) stage.width else dm.widthPixels - dp(24)
        val stageH = if (stage.height > 0) stage.height else (dm.heightPixels - dp(84)).coerceAtLeast(dp(260))

        val isNarrow = stageW < dp(540)
        val initW = if (isNarrow) {
            (stageW * 0.96f).toInt()
        } else {
            min(dp(preferredWidthDp), (stageW * 0.88f).toInt()).coerceAtLeast(dp(280))
        }
        val initH = if (isNarrow) {
            (stageH * 0.86f).toInt()
        } else {
            min(dp(preferredHeightDp), (stageH * 0.86f).toInt()).coerceAtLeast(dp(210))
        }

        val cascadeOffset = (windows.size % 6) * dp(22)
        val initX = ((stageW - initW) / 2 + cascadeOffset).coerceIn(dp(4), max(dp(4), stageW - initW - dp(4)))
        val initY = ((stageH - initH) / 3 + cascadeOffset).coerceIn(dp(4), max(dp(4), stageH - initH - dp(4)))

        val theme = prefs.currentTheme
        val winFrame = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            elevation = dp(12).toFloat()
            background = UiKit.strokeRounded(
                context,
                Color.rgb(17, 25, 42),
                Color.argb(110, 255, 255, 255),
                14,
                1
            )
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(8), dp(6))
            background = UiKit.rounded(context, theme.windowHeaderActive, 13)
        }

        val glyphView = TextView(context).apply {
            text = glyph
            textSize = 14f
            setPadding(0, 0, dp(8), 0)
        }
        header.addView(glyphView)

        val titleTv = UiKit.label(context, title, 12.5f, UiKit.INK, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(titleTv, LinearLayout.LayoutParams(0, dp(28), 1f))

        val id = UUID.randomUUID().toString()
        val state = DesktopWindowState(
            id = id,
            appKey = appKey,
            title = title,
            glyph = glyph,
            frame = winFrame,
            headerBar = header,
            titleView = titleTv,
            minimized = false,
            maximized = isNarrow, // Auto-maximize on narrow portrait phones for usability, still restorable!
            savedX = initX,
            savedY = initY,
            savedW = initW,
            savedH = initH,
            onCloseCleanup = onCloseCleanup
        )

        fun winBtn(symbol: String, bg: Int, onClick: () -> Unit) = TextView(context).apply {
            text = symbol
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.INK)
            gravity = Gravity.CENTER
            background = UiKit.rounded(context, bg, 8)
            setOnClickListener {
                focusWindow(state.id)
                onClick()
            }
        }

        // Aero Snap Left / Right buttons on wide landscape screens
        if (!isNarrow) {
            header.addView(winBtn("◧", Color.argb(32, 255, 255, 255)) {
                snapWindow(state, leftHalf = true)
            }, LinearLayout.LayoutParams(dp(28), dp(26)).apply { rightMargin = dp(4) })

            header.addView(winBtn("◨", Color.argb(32, 255, 255, 255)) {
                snapWindow(state, leftHalf = false)
            }, LinearLayout.LayoutParams(dp(28), dp(26)).apply { rightMargin = dp(6) })
        }

        // Minimize (—)
        header.addView(winBtn("—", Color.argb(38, 255, 255, 255)) {
            minimizeWindow(state.id)
        }, LinearLayout.LayoutParams(dp(30), dp(26)).apply { rightMargin = dp(4) })

        // Maximize / Restore (▢)
        val maxBtn = winBtn(if (state.maximized) "❐" else "▢", Color.argb(38, 255, 255, 255)) {
            toggleMaximize(state)
        }
        header.addView(maxBtn, LinearLayout.LayoutParams(dp(30), dp(26)).apply { rightMargin = dp(4) })

        // Close (✕)
        header.addView(winBtn("✕", Color.argb(175, 220, 38, 38)) {
            closeWindow(state.id)
        }, LinearLayout.LayoutParams(dp(32), dp(26)))

        // Dragging TitleBar + Double-Tap to Maximize
        var dragStartX = 0f
        var dragStartY = 0f
        var startMarginX = 0
        var startMarginY = 0
        var lastDownTime = 0L

        header.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    focusWindow(state.id)
                    val now = System.currentTimeMillis()
                    if (now - lastDownTime < 280L) {
                        toggleMaximize(state)
                        maxBtn.text = if (state.maximized) "❐" else "▢"
                        lastDownTime = 0L
                        return@setOnTouchListener true
                    }
                    lastDownTime = now
                    val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                    dragStartX = ev.rawX
                    dragStartY = ev.rawY
                    startMarginX = lp.leftMargin
                    startMarginY = lp.topMargin
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - dragStartX).toInt()
                    val dy = (ev.rawY - dragStartY).toInt()
                    if (state.maximized && (kotlin.math.abs(dx) > dp(10) || kotlin.math.abs(dy) > dp(10))) {
                        // Unmaximize when dragged away from top
                        state.maximized = false
                        maxBtn.text = "▢"
                        val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                        lp.width = state.savedW
                        lp.height = state.savedH
                        startMarginX = (ev.rawX.toInt() - state.savedW / 2).coerceAtLeast(0)
                        startMarginY = 0
                        dragStartX = ev.rawX
                        dragStartY = ev.rawY
                    }
                    if (!state.maximized) {
                        val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                        val sw = stage.width.coerceAtLeast(dp(300))
                        val sh = stage.height.coerceAtLeast(dp(220))
                        val newX = (startMarginX + dx).coerceIn(-lp.width + dp(90), sw - dp(90))
                        val newY = (startMarginY + dy).coerceIn(0, sh - dp(40))
                        lp.leftMargin = newX
                        lp.topMargin = newY
                        state.savedX = newX
                        state.savedY = newY
                        winFrame.layoutParams = lp
                    }
                    true
                }
                else -> false
            }
        }

        winFrame.addView(header, LinearLayout.LayoutParams(-1, dp(38)))

        // Body Wrapper (Content + Bottom-Right Resize Grip)
        val bodyContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(15, 23, 38))
            setOnClickListener { focusWindow(state.id) }
        }

        val contentView = try {
            contentBuilder(state)
        } catch (t: Throwable) {
            UiKit.label(context, "Gagal memuat konten: ${t.message}", 12f, Color.rgb(252, 165, 165), maxLines = 6).apply {
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
        }
        bodyContainer.addView(contentView, FrameLayout.LayoutParams(-1, -1))

        // Resize corner handle (◢)
        val resizeHandle = TextView(context).apply {
            text = "◢"
            textSize = 13f
            setTextColor(Color.argb(145, 185, 205, 240))
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(4), dp(4), dp(5), dp(2))
        }
        var resStartX = 0f
        var resStartY = 0f
        var resStartW = 0
        var resStartH = 0
        resizeHandle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    focusWindow(state.id)
                    if (state.maximized) {
                        state.maximized = false
                        maxBtn.text = "▢"
                    }
                    val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                    resStartX = ev.rawX
                    resStartY = ev.rawY
                    resStartW = if (lp.width > 0) lp.width else stage.width
                    resStartH = if (lp.height > 0) lp.height else stage.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val lp = winFrame.layoutParams as FrameLayout.LayoutParams
                    val sw = stage.width.coerceAtLeast(dp(300))
                    val sh = stage.height.coerceAtLeast(dp(220))
                    val newW = (resStartW + (ev.rawX - resStartX).toInt()).coerceIn(dp(250), sw)
                    val newH = (resStartH + (ev.rawY - resStartY).toInt()).coerceIn(dp(185), sh)
                    lp.width = newW
                    lp.height = newH
                    state.savedW = newW
                    state.savedH = newH
                    winFrame.layoutParams = lp
                    true
                }
                else -> false
            }
        }
        bodyContainer.addView(resizeHandle, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM or Gravity.END))

        winFrame.addView(bodyContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        val initialLp = if (state.maximized) {
            FrameLayout.LayoutParams(-1, -1)
        } else {
            FrameLayout.LayoutParams(initW, initH).apply {
                leftMargin = initX
                topMargin = initY
            }
        }

        windows.add(state)
        stage.addView(winFrame, initialLp)
        focusWindow(state.id)
        return state
    }

    fun snapWindow(state: DesktopWindowState, leftHalf: Boolean) {
        val sw = stage.width.coerceAtLeast(dp(360))
        val sh = stage.height.coerceAtLeast(dp(220))
        state.maximized = false
        val halfW = sw / 2
        val lp = FrameLayout.LayoutParams(halfW, sh).apply {
            leftMargin = if (leftHalf) 0 else halfW
            topMargin = 0
        }
        winApply(state, lp)
    }

    fun toggleMaximize(state: DesktopWindowState) {
        state.maximized = !state.maximized
        val lp = if (state.maximized) {
            FrameLayout.LayoutParams(-1, -1)
        } else {
            FrameLayout.LayoutParams(state.savedW, state.savedH).apply {
                leftMargin = state.savedX
                topMargin = state.savedY
            }
        }
        winApply(state, lp)
    }

    private fun winApply(state: DesktopWindowState, lp: FrameLayout.LayoutParams) {
        state.frame.layoutParams = lp
        focusWindow(state.id)
    }

    fun focusWindow(id: String) {
        val target = windows.find { it.id == id } ?: return
        if (target.minimized) {
            target.minimized = false
            target.frame.visibility = View.VISIBLE
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
        target.frame.visibility = View.GONE
        if (activeWindowId == id) {
            activeWindowId = windows.lastOrNull { !it.minimized }?.id
        }
        refreshHeaderStyles()
        onWindowsChanged()
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
        stage.removeView(removed.frame)
        if (activeWindowId == id) {
            activeWindowId = windows.lastOrNull { !it.minimized }?.id
        }
        refreshHeaderStyles()
        onWindowsChanged()
    }

    fun refreshHeaderStyles() {
        val theme = prefs.currentTheme
        for (w in windows) {
            val isActive = (w.id == activeWindowId && !w.minimized)
            w.headerBar.background = UiKit.rounded(
                context,
                if (isActive) theme.windowHeaderActive else theme.windowHeaderInactive,
                13
            )
            w.frame.background = UiKit.strokeRounded(
                context,
                Color.rgb(17, 25, 42),
                if (isActive) theme.accent else Color.argb(65, 255, 255, 255),
                14,
                1
            )
        }
    }
}
