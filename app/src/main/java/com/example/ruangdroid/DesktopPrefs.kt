package com.example.ruangdroid

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
    private val prefs = context.getSharedPreferences("ruangdroid_prefs", Context.MODE_PRIVATE)

    val themes = listOf(
        ThemePalette(
            name = "Win11 Midnight",
            bgColors = intArrayOf(Color.rgb(12, 24, 52), Color.rgb(22, 46, 88), Color.rgb(34, 68, 118)),
            accent = Color.rgb(96, 165, 250),
            taskbarBg = Color.argb(235, 11, 18, 34),
            panelColors = intArrayOf(Color.rgb(20, 32, 56), Color.rgb(14, 22, 40)),
            windowHeaderActive = Color.rgb(25, 42, 74),
            windowHeaderInactive = Color.rgb(18, 27, 46)
        ),
        ThemePalette(
            name = "DeX Carbon",
            bgColors = intArrayOf(Color.rgb(16, 18, 24), Color.rgb(28, 32, 42), Color.rgb(40, 45, 58)),
            accent = Color.rgb(129, 140, 248),
            taskbarBg = Color.argb(240, 14, 16, 22),
            panelColors = intArrayOf(Color.rgb(26, 29, 38), Color.rgb(18, 20, 27)),
            windowHeaderActive = Color.rgb(34, 38, 52),
            windowHeaderInactive = Color.rgb(22, 25, 34)
        ),
        ThemePalette(
            name = "Aurora Emerald",
            bgColors = intArrayOf(Color.rgb(8, 32, 38), Color.rgb(15, 54, 60), Color.rgb(22, 74, 76)),
            accent = Color.rgb(52, 211, 153),
            taskbarBg = Color.argb(236, 8, 22, 27),
            panelColors = intArrayOf(Color.rgb(15, 42, 48), Color.rgb(10, 28, 33)),
            windowHeaderActive = Color.rgb(18, 56, 62),
            windowHeaderInactive = Color.rgb(12, 36, 42)
        ),
        ThemePalette(
            name = "Sunset Cyber",
            bgColors = intArrayOf(Color.rgb(32, 16, 46), Color.rgb(58, 25, 68), Color.rgb(82, 34, 72)),
            accent = Color.rgb(251, 146, 60),
            taskbarBg = Color.argb(236, 22, 12, 32),
            panelColors = intArrayOf(Color.rgb(42, 22, 54), Color.rgb(26, 14, 36)),
            windowHeaderActive = Color.rgb(58, 28, 70),
            windowHeaderInactive = Color.rgb(34, 18, 44)
        ),
        ThemePalette(
            name = "XP Bliss Teal",
            bgColors = intArrayOf(Color.rgb(14, 62, 112), Color.rgb(24, 98, 142), Color.rgb(34, 118, 96)),
            accent = Color.rgb(74, 222, 128),
            taskbarBg = Color.argb(238, 14, 42, 84),
            panelColors = intArrayOf(Color.rgb(18, 52, 96), Color.rgb(12, 36, 70)),
            windowHeaderActive = Color.rgb(24, 72, 136),
            windowHeaderInactive = Color.rgb(18, 48, 92)
        ),
        ThemePalette(
            name = "Nordic Slate",
            bgColors = intArrayOf(Color.rgb(30, 41, 59), Color.rgb(45, 59, 82), Color.rgb(56, 74, 102)),
            accent = Color.rgb(56, 189, 248),
            taskbarBg = Color.argb(236, 18, 26, 38),
            panelColors = intArrayOf(Color.rgb(30, 41, 59), Color.rgb(20, 28, 42)),
            windowHeaderActive = Color.rgb(38, 52, 74),
            windowHeaderInactive = Color.rgb(26, 36, 52)
        )
    )

    var landscapeLock: Boolean
        get() = prefs.getBoolean("landscape_lock", true)
        set(value) = prefs.edit().putBoolean("landscape_lock", value).apply()

    var immersiveMode: Boolean
        get() = prefs.getBoolean("immersive_mode", false)
        set(value) = prefs.edit().putBoolean("immersive_mode", value).apply()

    var freeformExternalApps: Boolean
        get() = prefs.getBoolean("freeform_external", true)
        set(value) = prefs.edit().putBoolean("freeform_external", value).apply()

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
        if (prefs.getBoolean("seeded_v2_0", false) || installed.isEmpty()) return

        val sysPkgs = mutableListOf<String>()
        val mediaPkgs = mutableListOf<String>()
        val workPkgs = mutableListOf<String>()

        for ((pkg, label) in installed) {
            val key = "$pkg $label".lowercase(Locale.ROOT)
            when {
                listOf("setting", "setelan", "pengaturan", "file", "clock", "jam", "calc", "camera", "kamera", "phone", "telepon").any { key.contains(it) } ->
                    if (sysPkgs.size < 8) sysPkgs.add(pkg)
                listOf("chrome", "browser", "youtube", "music", "musik", "gallery", "galeri", "photo", "foto", "video", "map", "play").any { key.contains(it) } ->
                    if (mediaPkgs.size < 8) mediaPkgs.add(pkg)
                listOf("whatsapp", "telegram", "mail", "gmail", "pesan", "message", "calendar", "doc", "drive", "note").any { key.contains(it) } ->
                    if (workPkgs.size < 8) workPkgs.add(pkg)
            }
        }

        val allPkgs = installed.map { it.first }
        if (sysPkgs.isEmpty()) sysPkgs.addAll(allPkgs.take(3))
        if (mediaPkgs.isEmpty() && allPkgs.size > 3) mediaPkgs.addAll(allPkgs.drop(3).take(3))

        if (getFolders().isEmpty()) {
            val seeded = mutableListOf(
                DesktopFolder(UUID.randomUUID().toString(), "Android Sistem", sysPkgs),
                DesktopFolder(UUID.randomUUID().toString(), "Media & Sosial", (mediaPkgs + workPkgs).distinct().toMutableList())
            )
            saveFolders(seeded)
        }

        if (getPinnedPackages().isEmpty()) {
            savePinnedPackages(allPkgs.take(3))
        }
        if (getDesktopPackages().isEmpty()) {
            saveDesktopPackages(allPkgs.take(4))
        }

        prefs.edit().putBoolean("seeded_v2_0", true).apply()
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
