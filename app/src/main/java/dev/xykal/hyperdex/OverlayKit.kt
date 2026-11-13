package dev.xykal.hyperdex

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.max
import kotlin.math.min

class OverlayKit(
    private val context: Context,
    private val hostLayer: FrameLayout,
    private val prefs: DesktopPrefs
) {
    private var activeBackdrop: FrameLayout? = null
    private fun dp(v: Int) = UiKit.dp(context, v)

    fun hasOpenOverlay(): Boolean = activeBackdrop != null

    fun dismissOverlay() {
        val current = activeBackdrop ?: return
        activeBackdrop = null
        current.animate()
            .alpha(0f)
            .setDuration(130)
            .withEndAction { hostLayer.removeView(current) }
            .start()
    }

    /**
     * Shows a custom Windows 11-style anchored panel (Start Menu, Quick Center, Modal)
     * inside our own FrameLayout compositor — ZERO Android Dialog!
     */
    fun showAnchoredPanel(
        panel: View,
        widthPx: Int,
        heightPx: Int = FrameLayout.LayoutParams.WRAP_CONTENT,
        gravity: Int = Gravity.CENTER,
        marginX: Int = 0,
        marginY: Int = 0
    ) {
        dismissOverlay()

        val backdrop = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(95, 4, 8, 18))
            isClickable = true
            alpha = 0f
            setOnClickListener { dismissOverlay() }
        }

        // Prevent clicks inside the panel from dismissing backdrop
        panel.isClickable = true
        panel.scaleX = 0.94f
        panel.scaleY = 0.94f
        panel.translationY = dp(18).toFloat()

        val lp = FrameLayout.LayoutParams(widthPx, heightPx, gravity).apply {
            if ((gravity and Gravity.START) == Gravity.START) leftMargin = marginX
            if ((gravity and Gravity.END) == Gravity.END) rightMargin = marginX
            if ((gravity and Gravity.BOTTOM) == Gravity.BOTTOM) bottomMargin = marginY
            if ((gravity and Gravity.TOP) == Gravity.TOP) topMargin = marginY
        }

        backdrop.addView(panel, lp)
        hostLayer.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        activeBackdrop = backdrop

        backdrop.animate().alpha(1f).setDuration(150).setInterpolator(UiKit.fluentInterpolator).start()
        panel.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(190)
            .setInterpolator(UiKit.fluentInterpolator)
            .start()
    }

    /**
     * Custom Windows 11 Acrylic Context Menu (replaces all AlertDialogs).
     */
    fun showContextMenu(
        title: String,
        items: List<Pair<String, () -> Unit>>,
        rawX: Float = -1f,
        rawY: Float = -1f
    ) {
        val theme = prefs.currentTheme
        val menuW = dp(245)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(16).toFloat()
            background = UiKit.gradientRounded(
                context,
                theme.panelColors,
                Color.argb(110, 255, 255, 255),
                14
            )
        }

        card.addView(
            UiKit.label(context, title, 12f, theme.accent, bold = true).apply {
                setPadding(dp(10), dp(4), dp(10), dp(6))
            }
        )
        card.addView(View(context).apply {
            setBackgroundColor(Color.argb(40, 255, 255, 255))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(4) })

        for ((label, action) in items) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = UiKit.rounded(context, Color.argb(20, 255, 255, 255), 9)
                UiKit.attachPressBounce(this, 0.97f)
                setOnClickListener {
                    dismissOverlay()
                    action()
                }
            }
            row.addView(UiKit.label(context, label, 12f, UiKit.INK, bold = false))
            card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) })
        }

        val hostW = hostLayer.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val hostH = hostLayer.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels

        if (rawX >= 0f && rawY >= 0f) {
            val estH = dp(46 + items.size * 38)
            val clampedX = min(max(dp(8), rawX.toInt()), max(dp(8), hostW - menuW - dp(8)))
            val clampedY = min(max(dp(8), rawY.toInt()), max(dp(8), hostH - estH - dp(8)))
            showAnchoredPanel(
                panel = card,
                widthPx = menuW,
                gravity = Gravity.TOP or Gravity.START,
                marginX = clampedX,
                marginY = clampedY
            )
        } else {
            showAnchoredPanel(panel = card, widthPx = menuW, gravity = Gravity.CENTER)
        }
    }

    /**
     * Custom Windows 11 Text Input Modal (Rename, New File, New Folder, Save As).
     */
    fun showInputPrompt(
        title: String,
        initialText: String = "",
        hintText: String = "",
        confirmLabel: String = "Simpan",
        onConfirm: (String) -> Unit
    ) {
        val theme = prefs.currentTheme
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(18).toFloat()
            background = UiKit.gradientRounded(context, theme.panelColors, theme.accent, 16)
        }

        card.addView(UiKit.label(context, title, 14f, UiKit.INK, bold = true))

        val input = EditText(context).apply {
            setText(initialText)
            hint = hintText
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 13f))
            setSingleLine(true)
            background = UiKit.strokeRounded(context, Color.rgb(10, 16, 28), Color.argb(80, 255, 255, 255), 10)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        card.addView(input, LinearLayout.LayoutParams(-1, dp(40)).apply {
            topMargin = dp(10)
            bottomMargin = dp(12)
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        actions.addView(UiKit.chipButton(context, "Batal") {
            dismissOverlay()
        }, LinearLayout.LayoutParams(-2, dp(32)).apply { rightMargin = dp(8) })

        actions.addView(UiKit.chipButton(
            context,
            confirmLabel,
            bgColor = Color.argb(180, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent)),
            strokeColor = theme.accent
        ) {
            val txt = input.text?.toString()?.trim().orEmpty()
            dismissOverlay()
            onConfirm(txt)
        }, LinearLayout.LayoutParams(-2, dp(32)))

        card.addView(actions)
        val w = min(dp(380), (context.resources.displayMetrics.widthPixels * 0.90f).toInt())
        showAnchoredPanel(card, widthPx = w, gravity = Gravity.CENTER)
        input.requestFocus()
    }

    /**
     * Custom Windows 11 Multi-Select Picker & Folder Editor (zero Android CheckBox/AlertDialog).
     */
    fun showMultiPicker(
        title: String,
        items: List<Pair<String, String>>, // id to label
        initialChecked: Set<String>,
        editableFolderName: String? = null,
        dangerButton: Pair<String, () -> Unit>? = null,
        onSave: (selectedIds: Set<String>, folderName: String) -> Unit
    ) {
        val theme = prefs.currentTheme
        val selected = initialChecked.toMutableSet()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(18).toFloat()
            background = UiKit.gradientRounded(context, theme.panelColors, Color.argb(95, 255, 255, 255), 18)
        }

        card.addView(UiKit.label(context, title, 14.5f, UiKit.INK, bold = true))

        var nameEdit: EditText? = null
        if (editableFolderName != null) {
            nameEdit = EditText(context).apply {
                setText(editableFolderName)
                hint = "Nama Folder Desktop…"
                setHintTextColor(UiKit.MUTED)
                setTextColor(UiKit.INK)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 13f))
                setSingleLine(true)
                background = UiKit.strokeRounded(context, Color.rgb(10, 16, 28), Color.argb(75, 255, 255, 255), 10)
                setPadding(dp(12), 0, dp(12), 0)
            }
            card.addView(nameEdit, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
        }

        val filterInput = EditText(context).apply {
            hint = "Filter daftar aplikasi…"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 12f))
            setSingleLine(true)
            background = UiKit.rounded(context, Color.argb(34, 255, 255, 255), 9)
            setPadding(dp(10), 0, dp(10), 0)
        }
        card.addView(filterInput, LinearLayout.LayoutParams(-1, dp(34)).apply {
            topMargin = dp(8); bottomMargin = dp(8)
        })

        val listCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        scroll.addView(listCol)

        val maxH = (context.resources.displayMetrics.heightPixels * 0.42f).toInt().coerceAtLeast(dp(150))
        card.addView(scroll, LinearLayout.LayoutParams(-1, maxH).apply { bottomMargin = dp(10) })

        fun renderItems(q: String) {
            listCol.removeAllViews()
            val filtered = items.filter { it.second.contains(q, ignoreCase = true) }
            for ((id, lbl) in filtered) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                }
                val badge = UiKit.label(context, "", 11f, Color.WHITE, bold = true).apply {
                    gravity = Gravity.CENTER
                }
                fun syncRow() {
                    val on = selected.contains(id)
                    badge.text = if (on) "✓" else ""
                    badge.background = UiKit.strokeRounded(
                        context,
                        if (on) theme.accent else Color.TRANSPARENT,
                        if (on) theme.accent else Color.argb(110, 255, 255, 255),
                        6
                    )
                    row.background = UiKit.rounded(
                        context,
                        if (on) Color.argb(42, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
                        else Color.argb(16, 255, 255, 255),
                        9
                    )
                }
                syncRow()
                row.setOnClickListener {
                    if (selected.contains(id)) selected.remove(id) else selected.add(id)
                    syncRow()
                }
                row.addView(badge, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(10) })
                row.addView(UiKit.label(context, lbl, 12f, UiKit.INK))
                listCol.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) })
            }
        }
        renderItems("")

        filterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderItems(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (dangerButton != null) {
            footer.addView(UiKit.chipButton(context, dangerButton.first, Color.argb(145, 220, 38, 38)) {
                dismissOverlay()
                dangerButton.second()
            }, LinearLayout.LayoutParams(-2, dp(32)).apply { rightMargin = dp(8) })
        }
        footer.addView(UiKit.chipButton(context, "Batal") { dismissOverlay() },
            LinearLayout.LayoutParams(-2, dp(32)).apply { rightMargin = dp(8) })
        footer.addView(UiKit.chipButton(
            context,
            "Simpan",
            bgColor = Color.argb(180, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent)),
            strokeColor = theme.accent
        ) {
            val fName = nameEdit?.text?.toString()?.trim().orEmpty().ifEmpty { "Folder" }
            dismissOverlay()
            onSave(selected, fName)
        }, LinearLayout.LayoutParams(-2, dp(32)))

        card.addView(footer)
        val w = min(dp(430), (context.resources.displayMetrics.widthPixels * 0.92f).toInt())
        showAnchoredPanel(card, widthPx = w, gravity = Gravity.CENTER)
    }

    /**
     * Windows 11 Bottom-Right Desktop Notification Banner (replaces stock Android Toast!).
     */
    fun notify(title: String, message: String) {
        val theme = prefs.currentTheme
        val toastCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(20).toFloat()
            setPadding(dp(12), dp(10), dp(14), dp(10))
            background = UiKit.gradientRounded(context, theme.panelColors, theme.accent, 13)
            translationX = dp(260).toFloat()
            alpha = 0f
        }

        val icon = ImageView(context).apply {
            setImageDrawable(VectorIcons.get(context, "start_orb", dp(26)))
        }
        toastCard.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(10) })

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(UiKit.label(context, title, 11.5f, UiKit.INK, bold = true))
        col.addView(UiKit.label(context, message, 10.8f, UiKit.MUTED, maxLines = 2))
        toastCard.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        val w = min(dp(300), (context.resources.displayMetrics.widthPixels * 0.85f).toInt())
        val lp = FrameLayout.LayoutParams(w, -2, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(14)
            bottomMargin = dp(14)
        }
        hostLayer.addView(toastCard, lp)

        toastCard.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(UiKit.fluentInterpolator)
            .start()

        toastCard.postDelayed({
            toastCard.animate()
                .translationX(dp(260).toFloat())
                .alpha(0f)
                .setDuration(200)
                .withEndAction { hostLayer.removeView(toastCard) }
                .start()
        }, 2600L)
    }
}
