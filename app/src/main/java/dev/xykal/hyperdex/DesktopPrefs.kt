package dev.xykal.hyperdex

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

data class DesktopFolder(
    val id: String,
    var name: String,
    val packages: MutableList<String>
)

data class InstalledExeEntry(
    val id: String,
    val title: String,
    val exePath: String,
    val arch: String
)

data class ThemePalette(
    val name: String,
    val bgColors: IntArray,
    val accent: Int,
    val taskbarBg: Int,
    val panelColors: IntArray,
    val windowHeaderActive: Int,
    val windowHeaderInactive: Int
)

class DesktopPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("hyperdex_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        var cachedScaleFactor: Float = 1.0f
    }

    init {
        cachedScaleFactor = (prefs.getInt("ui_scale_pct", 100).coerceIn(75, 135)) / 100f
    }

    val themes = listOf(
        ThemePalette(
            name = "Win11 Bloom",
            bgColors = intArrayOf(Color.rgb(10, 22, 48), Color.rgb(20, 44, 88), Color.rgb(32, 66, 120)),
            accent = Color.rgb(96, 165, 250),
            taskbarBg = Color.argb(232, 10, 16, 30),
            panelColors = intArrayOf(Color.rgb(19, 30, 54), Color.rgb(13, 20, 38)),
            windowHeaderActive = Color.rgb(23, 39, 70),
            windowHeaderInactive = Color.rgb(16, 25, 44)
        ),
        ThemePalette(
            name = "DeX Obsidian",
            bgColors = intArrayOf(Color.rgb(14, 16, 22), Color.rgb(25, 29, 39), Color.rgb(36, 41, 54)),
            accent = Color.rgb(129, 140, 248),
            taskbarBg = Color.argb(238, 12, 14, 20),
            panelColors = intArrayOf(Color.rgb(24, 27, 36), Color.rgb(16, 18, 25)),
            windowHeaderActive = Color.rgb(32, 36, 50),
            windowHeaderInactive = Color.rgb(20, 23, 32)
        ),
        ThemePalette(
            name = "Aurora Emerald",
            bgColors = intArrayOf(Color.rgb(7, 29, 35), Color.rgb(14, 50, 56), Color.rgb(20, 70, 72)),
            accent = Color.rgb(52, 211, 153),
            taskbarBg = Color.argb(235, 8, 20, 25),
            panelColors = intArrayOf(Color.rgb(14, 40, 46), Color.rgb(9, 26, 31)),
            windowHeaderActive = Color.rgb(17, 52, 58),
            windowHeaderInactive = Color.rgb(11, 34, 40)
        ),
        ThemePalette(
            name = "Cyber Neon",
            bgColors = intArrayOf(Color.rgb(28, 14, 44), Color.rgb(52, 22, 66), Color.rgb(76, 30, 70)),
            accent = Color.rgb(244, 114, 182),
            taskbarBg = Color.argb(235, 19, 10, 29),
            panelColors = intArrayOf(Color.rgb(38, 19, 50), Color.rgb(24, 12, 34)),
            windowHeaderActive = Color.rgb(54, 25, 68),
            windowHeaderInactive = Color.rgb(32, 16, 42)
        ),
        ThemePalette(
            name = "XP Bliss Modern",
            bgColors = intArrayOf(Color.rgb(12, 58, 108), Color.rgb(22, 92, 136), Color.rgb(30, 112, 92)),
            accent = Color.rgb(74, 222, 128),
            taskbarBg = Color.argb(236, 12, 38, 78),
            panelColors = intArrayOf(Color.rgb(16, 48, 90), Color.rgb(11, 32, 64)),
            windowHeaderActive = Color.rgb(22, 68, 130),
            windowHeaderInactive = Color.rgb(16, 44, 86)
        ),
        ThemePalette(
            name = "Nordic Frost",
            bgColors = intArrayOf(Color.rgb(26, 36, 52), Color.rgb(40, 54, 76), Color.rgb(52, 70, 96)),
            accent = Color.rgb(56, 189, 248),
            taskbarBg = Color.argb(235, 16, 23, 35),
            panelColors = intArrayOf(Color.rgb(28, 38, 56), Color.rgb(18, 26, 40)),
            windowHeaderActive = Color.rgb(36, 50, 72),
            windowHeaderInactive = Color.rgb(24, 34, 50)
        )
    )

    var landscapeLock: Boolean
        get() = prefs.getBoolean("landscape_lock", true)
        set(value) = prefs.edit().putBoolean("landscape_lock", value).apply()

    var immersiveMode: Boolean
        get() = prefs.getBoolean("immersive_mode", true) // True PC full-window feel by default!
        set(value) = prefs.edit().putBoolean("immersive_mode", value).apply()

    var centerTaskbar: Boolean
        get() = prefs.getBoolean("center_taskbar", false)
        set(value) = prefs.edit().putBoolean("center_taskbar", value).apply()

    var systemOverlayEnabled: Boolean
        get() = prefs.getBoolean("system_overlay_dock", false)
        set(value) = prefs.edit().putBoolean("system_overlay_dock", value).apply()

    var acrylicBlurWallpaper: Boolean
        get() = prefs.getBoolean("acrylic_blur_wp", false)
        set(value) = prefs.edit().putBoolean("acrylic_blur_wp", value).apply()

    var liveShaderWallpaper: Boolean
        get() = prefs.getBoolean("live_shader_wp", true) // OpenGL ES 3.0 GPU Aurora Shader on by default in v5.0!
        set(value) = prefs.edit().putBoolean("live_shader_wp", value).apply()

    var uiScalePercent: Int
        get() = prefs.getInt("ui_scale_pct", 100).coerceIn(75, 135)
        set(value) {
            val v = value.coerceIn(75, 135)
            cachedScaleFactor = v / 100f
            prefs.edit().putInt("ui_scale_pct", v).apply()
        }

    var iconScale: Int
        get() = prefs.getInt("icon_scale", 1).coerceIn(0, 2)
        set(value) = prefs.edit().putInt("icon_scale", value.coerceIn(0, 2)).apply()

    var themeIndex: Int
        get() = prefs.getInt("theme_index", 0).coerceIn(0, themes.lastIndex)
        set(value) = prefs.edit().putInt("theme_index", ((value % themes.size) + themes.size) % themes.size).apply()

    val currentTheme: ThemePalette
        get() = themes[themeIndex]

    fun cycleTheme(): ThemePalette {
        themeIndex = (themeIndex + 1) % themes.size
        clearCustomWallpaper()
        return currentTheme
    }

    fun customWallpaperFile(): File = File(context.filesDir, "custom_wallpaper.jpg")

    var useCustomWallpaper: Boolean
        get() = prefs.getBoolean("use_custom_wallpaper", false) && customWallpaperFile().exists()
        set(value) = prefs.edit().putBoolean("use_custom_wallpaper", value).apply()

    fun clearCustomWallpaper() {
        useCustomWallpaper = false
    }

    fun getPinnedPackages(): MutableList<String> = readStringList("pinned_packages")
    fun savePinnedPackages(list: List<String>) = writeStringList("pinned_packages", list.distinct())

    fun getDesktopPackages(): MutableList<String> = readStringList("desktop_packages")
    fun saveDesktopPackages(list: List<String>) = writeStringList("desktop_packages", list.distinct())

    fun getFolders(): MutableList<DesktopFolder> {
        val raw = prefs.getString("desktop_folders", null) ?: return mutableListOf()
        val result = mutableListOf<DesktopFolder>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkgs = mutableListOf<String>()
                val pArr = obj.optJSONArray("packages") ?: JSONArray()
                for (j in 0 until pArr.length()) pkgs.add(pArr.getString(j))
                result.add(
                    DesktopFolder(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Folder"),
                        packages = pkgs
                    )
                )
            }
        } catch (_: Throwable) {
        }
        return result
    }

    fun saveFolders(folders: List<DesktopFolder>) {
        val arr = JSONArray()
        for (f in folders) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("name", f.name)
            val pArr = JSONArray()
            f.packages.distinct().forEach { pArr.put(it) }
            obj.put("packages", pArr)
            arr.put(obj)
        }
        prefs.edit().putString("desktop_folders", arr.toString()).apply()
    }

    fun getInstalledExes(): MutableList<InstalledExeEntry> {
        val raw = prefs.getString("installed_exes_v2", null)
        val result = mutableListOf<InstalledExeEntry>()
        if (raw == null) {
            val defaultExe = File(VirtualDrive.programFilesDir(context), "Minesweeper/Minesweeper_XP.exe")
            if (defaultExe.exists()) {
                result.add(InstalledExeEntry("minesweeper_default", "Minesweeper XP.exe", defaultExe.absolutePath, "PE32 x86"))
                saveInstalledExes(result)
            }
            return result
        }
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.optString("exePath")
                if (path.isNotEmpty() && File(path).exists()) {
                    result.add(
                        InstalledExeEntry(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            title = o.optString("title", "Program.exe"),
                            exePath = path,
                            arch = o.optString("arch", "PE32 x86")
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }
        return result
    }

    fun saveInstalledExes(entries: List<InstalledExeEntry>) {
        val arr = JSONArray()
        for (e in entries.distinctBy { it.exePath }) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("title", e.title)
            o.put("exePath", e.exePath)
            o.put("arch", e.arch)
            arr.put(o)
        }
        prefs.edit().putString("installed_exes_v2", arr.toString()).apply()
    }

    fun ensureSeeded(installed: List<Pair<String, String>>) {
        if (prefs.getBoolean("seeded_v3_0", false) || installed.isEmpty()) return

        val sysPkgs = mutableListOf<String>()
        val mediaPkgs = mutableListOf<String>()

        for ((pkg, label) in installed) {
            val key = "$pkg $label".lowercase(Locale.ROOT)
            when {
                listOf("setting", "setelan", "pengaturan", "file", "clock", "jam", "camera", "kamera", "phone", "shizuku", "termux").any { key.contains(it) } ->
                    if (sysPkgs.size < 8) sysPkgs.add(pkg)
                listOf("chrome", "browser", "youtube", "music", "gallery", "photo", "whatsapp", "telegram", "play").any { key.contains(it) } ->
                    if (mediaPkgs.size < 8) mediaPkgs.add(pkg)
            }
        }

        val allPkgs = installed.map { it.first }
        if (sysPkgs.isEmpty()) sysPkgs.addAll(allPkgs.take(3))
        if (mediaPkgs.isEmpty() && allPkgs.size > 3) mediaPkgs.addAll(allPkgs.drop(3).take(3))

        if (getFolders().isEmpty()) {
            saveFolders(
                listOf(
                    DesktopFolder(UUID.randomUUID().toString(), "Apps Sistem", sysPkgs),
                    DesktopFolder(UUID.randomUUID().toString(), "Apps Favorit", mediaPkgs)
                )
            )
        }

        if (getPinnedPackages().isEmpty()) savePinnedPackages(allPkgs.take(2))
        if (getDesktopPackages().isEmpty()) saveDesktopPackages(allPkgs.take(3))

        prefs.edit().putBoolean("seeded_v3_0", true).apply()
    }

    private fun readStringList(key: String): MutableList<String> {
        val raw = prefs.getString(key, null) ?: return mutableListOf()
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) list.add(arr.getString(i))
        } catch (_: Throwable) {
        }
        return list
    }

    private fun writeStringList(key: String, values: List<String>) {
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
