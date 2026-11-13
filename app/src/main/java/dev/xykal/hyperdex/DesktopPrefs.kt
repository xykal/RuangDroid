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
        var cachedScaleFactor: Float = 0.84f
    }

    init {
        cachedScaleFactor = (prefs.getInt("ui_scale_pct", 84).coerceIn(55, 135)) / 100f
    }

    val themes = listOf(
        ThemePalette(
            name = "Graphite Titanium",
            bgColors = intArrayOf(Color.rgb(9, 11, 16), Color.rgb(15, 19, 27), Color.rgb(21, 26, 36)),
            accent = Color.rgb(56, 189, 248),
            taskbarBg = Color.argb(242, 11, 13, 18),
            panelColors = intArrayOf(Color.rgb(22, 25, 34), Color.rgb(16, 18, 25)),
            windowHeaderActive = Color.rgb(24, 28, 38),
            windowHeaderInactive = Color.rgb(17, 20, 27)
        ),
        ThemePalette(
            name = "Carbon Monochrome",
            bgColors = intArrayOf(Color.rgb(10, 11, 13), Color.rgb(18, 20, 24), Color.rgb(26, 29, 35)),
            accent = Color.rgb(203, 213, 225),
            taskbarBg = Color.argb(244, 10, 11, 14),
            panelColors = intArrayOf(Color.rgb(23, 25, 30), Color.rgb(16, 17, 21)),
            windowHeaderActive = Color.rgb(26, 29, 35),
            windowHeaderInactive = Color.rgb(18, 20, 24)
        ),
        ThemePalette(
            name = "Obsidian Emerald",
            bgColors = intArrayOf(Color.rgb(8, 13, 12), Color.rgb(13, 22, 20), Color.rgb(18, 30, 27)),
            accent = Color.rgb(52, 211, 153),
            taskbarBg = Color.argb(242, 9, 14, 13),
            panelColors = intArrayOf(Color.rgb(18, 26, 24), Color.rgb(13, 19, 18)),
            windowHeaderActive = Color.rgb(20, 30, 28),
            windowHeaderInactive = Color.rgb(14, 21, 20)
        ),
        ThemePalette(
            name = "Tungsten Amber",
            bgColors = intArrayOf(Color.rgb(13, 11, 9), Color.rgb(22, 18, 15), Color.rgb(30, 25, 20)),
            accent = Color.rgb(245, 158, 11),
            taskbarBg = Color.argb(242, 13, 11, 10),
            panelColors = intArrayOf(Color.rgb(26, 22, 19), Color.rgb(18, 15, 13)),
            windowHeaderActive = Color.rgb(30, 25, 21),
            windowHeaderInactive = Color.rgb(20, 17, 15)
        ),
        ThemePalette(
            name = "Nordic Slate",
            bgColors = intArrayOf(Color.rgb(14, 19, 27), Color.rgb(22, 30, 42), Color.rgb(30, 40, 56)),
            accent = Color.rgb(129, 161, 193),
            taskbarBg = Color.argb(242, 13, 17, 24),
            panelColors = intArrayOf(Color.rgb(24, 31, 43), Color.rgb(17, 22, 31)),
            windowHeaderActive = Color.rgb(28, 36, 50),
            windowHeaderInactive = Color.rgb(19, 25, 35)
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
        get() = prefs.getBoolean("live_shader_wp", false) // Clean matte Graphite default; toggleable in Settings
        set(value) = prefs.edit().putBoolean("live_shader_wp", value).apply()

    var uiScalePercent: Int
        get() = prefs.getInt("ui_scale_pct", 84).coerceIn(55, 135)
        set(value) {
            val v = value.coerceIn(55, 135)
            cachedScaleFactor = v / 100f
            prefs.edit().putInt("ui_scale_pct", v).apply()
        }

    var iconScale: Int
        get() = prefs.getInt("icon_scale", 0).coerceIn(0, 3) // 0=Micro(default), 1=Small, 2=Medium, 3=Large
        set(value) = prefs.edit().putInt("icon_scale", value.coerceIn(0, 3)).apply()

    var taskbarHeightDp: Int
        get() = prefs.getInt("taskbar_height_dp", 34).coerceIn(26, 46)
        set(value) = prefs.edit().putInt("taskbar_height_dp", value.coerceIn(26, 46)).apply()

    var flushTaskbar: Boolean
        get() = prefs.getBoolean("flush_taskbar", true)
        set(value) = prefs.edit().putBoolean("flush_taskbar", value).apply()

    var compactTaskbarIconsOnly: Boolean
        get() = prefs.getBoolean("compact_tb_icons", false)
        set(value) = prefs.edit().putBoolean("compact_tb_icons", value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_v6_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_v6_done", value).apply()

    var launcherLockEnabled: Boolean
        get() = prefs.getBoolean("launcher_lock_enabled", true)
        set(value) = prefs.edit().putBoolean("launcher_lock_enabled", value).apply()

    var launcherLockPin: String
        get() = prefs.getString("launcher_lock_pin", "") ?: ""
        set(value) = prefs.edit().putString("launcher_lock_pin", value.trim()).apply()

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
