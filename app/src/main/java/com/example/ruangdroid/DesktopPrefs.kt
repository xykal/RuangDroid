package com.example.ruangdroid

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class DesktopFolder(
    val id: String,
    var name: String,
    val packages: MutableList<String>
)

data class ThemePalette(
    val name: String,
    val bgColors: IntArray,
    val accent: Int,
    val taskbarBg: Int,
    val panelColors: IntArray
)

class DesktopPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ruangdroid_prefs", Context.MODE_PRIVATE)

    val themes = listOf(
        ThemePalette(
            name = "Win11 Midnight",
            bgColors = intArrayOf(Color.rgb(14, 24, 46), Color.rgb(24, 42, 74), Color.rgb(32, 54, 92)),
            accent = Color.rgb(110, 168, 255),
            taskbarBg = Color.argb(232, 12, 19, 34),
            panelColors = intArrayOf(Color.rgb(22, 34, 58), Color.rgb(15, 24, 42))
        ),
        ThemePalette(
            name = "DeX Carbon",
            bgColors = intArrayOf(Color.rgb(18, 20, 26), Color.rgb(29, 33, 43), Color.rgb(38, 43, 56)),
            accent = Color.rgb(130, 180, 255),
            taskbarBg = Color.argb(236, 15, 17, 22),
            panelColors = intArrayOf(Color.rgb(28, 31, 40), Color.rgb(20, 22, 29))
        ),
        ThemePalette(
            name = "Aurora Emerald",
            bgColors = intArrayOf(Color.rgb(10, 33, 38), Color.rgb(17, 52, 58), Color.rgb(24, 68, 72)),
            accent = Color.rgb(94, 234, 212),
            taskbarBg = Color.argb(232, 9, 24, 29),
            panelColors = intArrayOf(Color.rgb(16, 44, 50), Color.rgb(11, 30, 35))
        ),
        ThemePalette(
            name = "Sunset Slate",
            bgColors = intArrayOf(Color.rgb(34, 20, 44), Color.rgb(54, 29, 62), Color.rgb(72, 38, 68)),
            accent = Color.rgb(251, 146, 60),
            taskbarBg = Color.argb(232, 24, 14, 32),
            panelColors = intArrayOf(Color.rgb(44, 25, 52), Color.rgb(28, 16, 36))
        )
    )

    var landscapeLock: Boolean
        get() = prefs.getBoolean("landscape_lock", true)
        set(value) = prefs.edit().putBoolean("landscape_lock", value).apply()

    var immersiveMode: Boolean
        get() = prefs.getBoolean("immersive_mode", false)
        set(value) = prefs.edit().putBoolean("immersive_mode", value).apply()

    var themeIndex: Int
        get() = prefs.getInt("theme_index", 0).coerceIn(0, themes.lastIndex)
        set(value) = prefs.edit().putInt("theme_index", value % themes.size).apply()

    val currentTheme: ThemePalette
        get() = themes[themeIndex]

    fun cycleTheme(): ThemePalette {
        themeIndex = (themeIndex + 1) % themes.size
        return currentTheme
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
                for (j in 0 until pArr.length()) {
                    pkgs.add(pArr.getString(j))
                }
                result.add(
                    DesktopFolder(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Folder"),
                        packages = pkgs
                    )
                )
            }
        } catch (_: Exception) {
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

    fun ensureSeeded(installed: List<Pair<String, String>>) {
        if (prefs.getBoolean("seeded_v1_1", false) || installed.isEmpty()) return

        val sysPkgs = mutableListOf<String>()
        val mediaPkgs = mutableListOf<String>()
        val workPkgs = mutableListOf<String>()

        for ((pkg, label) in installed) {
            val key = "$pkg $label".lowercase(Locale.ROOT)
            when {
                listOf("setting", "setelan", "pengaturan", "file", "clock", "jam", "calc", "kalkulator", "camera", "kamera", "phone", "telepon", "contact", "kontak").any { key.contains(it) } ->
                    if (sysPkgs.size < 8) sysPkgs.add(pkg)
                listOf("chrome", "browser", "youtube", "music", "musik", "gallery", "galeri", "photo", "foto", "video", "map", "play").any { key.contains(it) } ->
                    if (mediaPkgs.size < 8) mediaPkgs.add(pkg)
                listOf("whatsapp", "telegram", "mail", "gmail", "pesan", "message", "calendar", "kalender", "doc", "drive", "note", "catatan").any { key.contains(it) } ->
                    if (workPkgs.size < 8) workPkgs.add(pkg)
            }
        }

        val allPkgs = installed.map { it.first }
        if (sysPkgs.isEmpty()) sysPkgs.addAll(allPkgs.take(3))
        if (mediaPkgs.isEmpty() && allPkgs.size > 3) mediaPkgs.addAll(allPkgs.drop(3).take(3))

        val seededFolders = mutableListOf(
            DesktopFolder(UUID.randomUUID().toString(), "Sistem & Alat", sysPkgs),
            DesktopFolder(UUID.randomUUID().toString(), "Internet & Media", mediaPkgs)
        )
        if (workPkgs.isNotEmpty()) {
            seededFolders.add(DesktopFolder(UUID.randomUUID().toString(), "Kerja & Sosial", workPkgs))
        }
        saveFolders(seededFolders)

        if (getPinnedPackages().isEmpty()) {
            savePinnedPackages(allPkgs.take(5))
        }
        if (getDesktopPackages().isEmpty()) {
            saveDesktopPackages(allPkgs.take(10))
        }

        prefs.edit().putBoolean("seeded_v1_1", true).apply()
    }

    private fun readStringList(key: String): MutableList<String> {
        val raw = prefs.getString(key, null) ?: return mutableListOf()
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) list.add(arr.getString(i))
        } catch (_: Exception) {
        }
        return list
    }

    private fun writeStringList(key: String, values: List<String>) {
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
