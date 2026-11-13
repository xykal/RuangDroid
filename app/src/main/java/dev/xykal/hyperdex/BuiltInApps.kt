package dev.xykal.hyperdex

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable

data class BuiltInPcApp(
    val key: String,
    val title: String,
    val subtitle: String,
    val iconId: String,
    val accent: Int,
    val showOnDesktop: Boolean = true,
    val pinnedOnTaskbar: Boolean = false
) {
    fun icon(context: Context, sizePx: Int = UiKit.dp(context, 44)): Drawable =
        VectorIcons.get(context, iconId, sizePx, accent)
}

object BuiltInApps {
    val all = listOf(
        BuiltInPcApp(
            key = "explorer",
            title = "This PC",
            subtitle = "File Explorer C:\\",
            iconId = "explorer",
            accent = Color.rgb(245, 158, 11),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "native_hub",
            title = "HyperShield Core",
            subtitle = "Rust, C11 & C++20 Engine",
            iconId = "native_hub",
            accent = Color.rgb(16, 185, 129),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "shizuku_hub",
            title = "Shizuku & Termux",
            subtitle = "ADB Bridge & Floating HUD",
            iconId = "shizuku_hub",
            accent = Color.rgb(99, 102, 241),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "exe_center",
            title = "EXE & Wine",
            subtitle = "Installer & Plugin .EXE",
            iconId = "exe_center",
            accent = Color.rgb(190, 24, 93),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "browser",
            title = "Web Browser",
            subtitle = "Desktop Mode Browser",
            iconId = "browser",
            accent = Color.rgb(37, 99, 235),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "terminal",
            title = "Terminal CMD",
            subtitle = "DOS, Shizuku & Linux",
            iconId = "terminal",
            accent = Color.rgb(34, 197, 94),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "notepad",
            title = "Notepad",
            subtitle = "Code & Text Editor",
            iconId = "notepad",
            accent = Color.rgb(14, 165, 233),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "calculator",
            title = "Calculator",
            subtitle = "Scientific Calc",
            iconId = "calculator",
            accent = Color.rgb(16, 185, 129),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "paint",
            title = "Paint Studio",
            subtitle = "Canvas & Photo",
            iconId = "paint",
            accent = Color.rgb(236, 72, 153),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "media",
            title = "Media Player",
            subtitle = "Audio & Synth Deck",
            iconId = "media",
            accent = Color.rgb(139, 92, 246),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "taskmgr",
            title = "Task Manager",
            subtitle = "Monitor RAM & Window",
            iconId = "taskmgr",
            accent = Color.rgb(20, 184, 166),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "control",
            title = "Control Panel",
            subtitle = "UI Scale, Tema & Layar",
            iconId = "control",
            accent = Color.rgb(100, 116, 139),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        )
    )
}
