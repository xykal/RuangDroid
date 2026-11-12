package com.example.ruangdroid

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable

data class BuiltInPcApp(
    val key: String,
    val title: String,
    val subtitle: String,
    val glyph: String,
    val color1: Int,
    val color2: Int,
    val showOnDesktop: Boolean = true,
    val pinnedOnTaskbar: Boolean = false
) {
    fun icon(context: Context): Drawable =
        UiKit.builtInIcon(context, key, color1, color2, glyph)
}

object BuiltInApps {
    val all = listOf(
        BuiltInPcApp(
            key = "explorer",
            title = "This PC",
            subtitle = "File Explorer C:\\",
            glyph = "🗂️",
            color1 = Color.rgb(245, 158, 11),
            color2 = Color.rgb(217, 119, 6),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "exe_center",
            title = "EXE & Wine",
            subtitle = "Installer & Plugin .EXE",
            glyph = "🍷",
            color1 = Color.rgb(190, 24, 93),
            color2 = Color.rgb(126, 34, 206),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "browser",
            title = "Web Browser",
            subtitle = "Desktop Mode Browser",
            glyph = "🌐",
            color1 = Color.rgb(37, 99, 235),
            color2 = Color.rgb(6, 182, 212),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "notepad",
            title = "Notepad",
            subtitle = "Code & Text Editor",
            glyph = "📝",
            color1 = Color.rgb(14, 165, 233),
            color2 = Color.rgb(59, 130, 246),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "terminal",
            title = "Terminal CMD",
            subtitle = "DOS & Linux Shell",
            glyph = ">_",
            color1 = Color.rgb(30, 41, 59),
            color2 = Color.rgb(15, 23, 42),
            showOnDesktop = true,
            pinnedOnTaskbar = true
        ),
        BuiltInPcApp(
            key = "calculator",
            title = "Calculator",
            subtitle = "Scientific Calc",
            glyph = "🧮",
            color1 = Color.rgb(16, 185, 129),
            color2 = Color.rgb(5, 150, 105),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "paint",
            title = "Paint Studio",
            subtitle = "Canvas & Photo",
            glyph = "🎨",
            color1 = Color.rgb(236, 72, 153),
            color2 = Color.rgb(249, 115, 22),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "media",
            title = "Media Player",
            subtitle = "Audio & Synth Deck",
            glyph = "🎵",
            color1 = Color.rgb(139, 92, 246),
            color2 = Color.rgb(79, 70, 229),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "taskmgr",
            title = "Task Manager",
            subtitle = "Monitor RAM & Window",
            glyph = "📊",
            color1 = Color.rgb(20, 184, 166),
            color2 = Color.rgb(15, 118, 110),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        ),
        BuiltInPcApp(
            key = "control",
            title = "Control Panel",
            subtitle = "Tema & Pengaturan PC",
            glyph = "⚙️",
            color1 = Color.rgb(100, 116, 139),
            color2 = Color.rgb(51, 65, 85),
            showOnDesktop = true,
            pinnedOnTaskbar = false
        )
    )
}
