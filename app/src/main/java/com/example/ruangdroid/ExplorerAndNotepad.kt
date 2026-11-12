package com.example.ruangdroid

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface DesktopHostCallbacks {
    fun openNotepad(file: File? = null)
    fun openExeFile(file: File)
    fun openBatchScript(file: File)
    fun openImageFile(file: File)
    fun openAudioFile(file: File)
    fun openExplorer(startDir: File? = null)
    fun openBrowser(initialUrl: String? = null)
    fun openTerminal(initialCommand: String? = null)
    fun openCalculator()
    fun openPaint(imageFile: File? = null)
    fun openMediaPlayer(audioFile: File? = null)
    fun openTaskManager()
    fun openControlPanel()
    fun openExeCenter(targetExe: File? = null)
    fun pickExternalExeFile()
    fun pickCustomWallpaper()
    fun pickAudioFromStorage()
    fun refreshDesktopUI()
}

object ExplorerAndNotepad {
    private var clipboardFile: File? = null

    private fun dp(ctx: Context, v: Int) = UiKit.dp(ctx, v)

    fun buildExplorerView(
        context: Context,
        host: DesktopHostCallbacks,
        winState: DesktopWindowState,
        initialDir: File?
    ): View {
        var currentDir = initialDir?.takeIf { it.exists() && it.isDirectory } ?: VirtualDrive.root(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 23, 38))
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        }

        // Quick Bookmarks Strip
        val placesScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val placesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val addressTv = UiKit.label(context, "", 11.5f, Color.rgb(147, 197, 253), bold = true).apply {
            background = UiKit.rounded(context, Color.rgb(10, 16, 28), 8)
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        }

        val statusTv = UiKit.label(context, "", 11f, UiKit.MUTED)
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }

        fun renderDir() {
            val winPath = VirtualDrive.toWindowsPath(context, currentDir)
            addressTv.text = "📍 $winPath"
            winState.titleView.text = "File Explorer — ${currentDir.name.ifEmpty { "C:\\" }}"

            listContainer.removeAllViews()
            val children = try {
                currentDir.listFiles()?.sortedWith(
                    compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })
                ) ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            }

            if (children.isEmpty()) {
                listContainer.addView(
                    UiKit.label(context, "Folder kosong atau akses dibatasi sistem Android.", 12f, UiKit.MUTED, maxLines = 2).apply {
                        setPadding(dp(context, 12), dp(context, 20), dp(context, 12), dp(context, 20))
                    }
                )
            } else {
                val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                for (f in children) {
                    val ext = f.extension.lowercase(Locale.ROOT)
                    val icon = when {
                        f.isDirectory -> "📁"
                        ext in listOf("exe", "msi", "com") -> "🍷"
                        ext in listOf("bat", "cmd", "sh") -> "⚡"
                        ext in listOf("png", "jpg", "jpeg", "webp", "bmp") -> "🖼️"
                        ext in listOf("mp3", "wav", "ogg", "m4a") -> "🎵"
                        else -> "📄"
                    }

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
                        background = UiKit.rounded(context, Color.argb(24, 255, 255, 255), 9)
                        setOnClickListener {
                            if (f.isDirectory) {
                                currentDir = f
                                renderDir()
                            } else {
                                when (ext) {
                                    "exe", "msi", "com" -> host.openExeFile(f)
                                    "bat", "cmd", "sh" -> host.openBatchScript(f)
                                    "png", "jpg", "jpeg", "webp", "bmp" -> host.openImageFile(f)
                                    "mp3", "wav", "ogg", "m4a" -> host.openAudioFile(f)
                                    else -> host.openNotepad(f)
                                }
                            }
                        }
                        setOnLongClickListener {
                            val items = arrayOf("Buka", "Salin (Copy)", "Ubah Nama (Rename)", "Hapus (Delete)")
                            AlertDialog.Builder(context)
                                .setTitle(f.name)
                                .setItems(items) { _, which ->
                                    when (which) {
                                        0 -> performClick()
                                        1 -> {
                                            clipboardFile = f
                                            Toast.makeText(context, "Disalin: ${f.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        2 -> {
                                            val input = EditText(context).apply {
                                                setText(f.name)
                                                setSingleLine(true)
                                            }
                                            AlertDialog.Builder(context)
                                                .setTitle("Ubah Nama")
                                                .setView(input)
                                                .setPositiveButton("Simpan") { _, _ ->
                                                    val newName = input.text?.toString()?.trim().orEmpty()
                                                    if (newName.isNotEmpty()) {
                                                        f.renameTo(File(currentDir, newName))
                                                        renderDir()
                                                    }
                                                }
                                                .setNegativeButton("Batal", null)
                                                .show()
                                        }
                                        3 -> {
                                            f.deleteRecursively()
                                            renderDir()
                                            host.refreshDesktopUI()
                                        }
                                    }
                                }
                                .show()
                            true
                        }
                    }

                    row.addView(UiKit.label(context, icon, 16f), LinearLayout.LayoutParams(dp(context, 28), -2))

                    val metaCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                    metaCol.addView(UiKit.label(context, f.name, 12.5f, UiKit.INK, bold = f.isDirectory))
                    val detail = if (f.isDirectory) {
                        val cnt = f.listFiles()?.size ?: 0
                        "Folder · $cnt item · ${df.format(Date(f.lastModified()))}"
                    } else {
                        "${formatSize(f.length())} · ${df.format(Date(f.lastModified()))}"
                    }
                    metaCol.addView(UiKit.label(context, detail, 10.5f, UiKit.MUTED))
                    row.addView(metaCol, LinearLayout.LayoutParams(0, -2, 1f))

                    if (!f.isDirectory && ext in listOf("exe", "msi")) {
                        row.addView(UiKit.chipButton(context, "▶ Run EXE", Color.argb(80, 59, 130, 246)) {
                            host.openExeFile(f)
                        })
                    }

                    listContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(context, 4)
                    })
                }
            }
            val clipNote = clipboardFile?.let { "  |  Clipboard: ${it.name}" }.orEmpty()
            statusTv.text = "${children.size} item di folder ini$clipNote  (Klik tahan item untuk menu)"
        }

        val bookmarks = listOf(
            "💻 C:\\" to VirtualDrive.root(context),
            "🖥️ Desktop" to VirtualDrive.desktopDir(context),
            "📄 Documents" to VirtualDrive.docsDir(context),
            "📦 Installers (.exe)" to VirtualDrive.installersDir(context),
            "🗂️ Program Files" to VirtualDrive.programFilesDir(context),
            "🖼️ Pictures" to VirtualDrive.picturesDir(context),
            "📱 Android SD" to (context.getExternalFilesDir(null) ?: Environment.getExternalStorageDirectory())
        )
        for ((title, dir) in bookmarks) {
            placesRow.addView(
                UiKit.chipButton(context, title) {
                    if (dir.exists()) {
                        currentDir = dir
                        renderDir()
                    } else {
                        Toast.makeText(context, "Direktori tidak tersedia", Toast.LENGTH_SHORT).show()
                    }
                },
                LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) }
            )
        }
        placesScroll.addView(placesRow)
        root.addView(placesScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })

        // Action Toolbar
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actionRow.addView(UiKit.chipButton(context, "⬆ Naik") {
            currentDir.parentFile?.takeIf { it.canRead() }?.let {
                currentDir = it
                renderDir()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        actionRow.addView(UiKit.chipButton(context, "📁+ Folder") {
            val input = EditText(context).apply { hint = "Nama folder baru"; setSingleLine(true) }
            AlertDialog.Builder(context)
                .setTitle("Buat Folder Baru")
                .setView(input)
                .setPositiveButton("Buat") { _, _ ->
                    val name = input.text?.toString()?.trim().orEmpty().ifEmpty { "New Folder" }
                    File(currentDir, name).mkdirs()
                    renderDir()
                }
                .setNegativeButton("Batal", null)
                .show()
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        actionRow.addView(UiKit.chipButton(context, "📄+ File") {
            val input = EditText(context).apply { setText("Catatan_Baru.txt"); setSingleLine(true) }
            AlertDialog.Builder(context)
                .setTitle("Buat File Baru")
                .setView(input)
                .setPositiveButton("Buat & Edit") { _, _ ->
                    val name = input.text?.toString()?.trim().orEmpty().ifEmpty { "Catatan.txt" }
                    val target = File(currentDir, name)
                    if (!target.exists()) target.writeText("")
                    renderDir()
                    host.openNotepad(target)
                }
                .setNegativeButton("Batal", null)
                .show()
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        actionRow.addView(UiKit.chipButton(context, "📋 Paste") {
            val src = clipboardFile
            if (src == null || !src.exists()) {
                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val dst = File(currentDir, src.name)
                    src.copyRecursively(dst, overwrite = true)
                    renderDir()
                } catch (e: Throwable) {
                    Toast.makeText(context, "Gagal menyalin: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        actionRow.addView(UiKit.chipButton(context, "🔄") { renderDir() },
            LinearLayout.LayoutParams(-2, dp(context, 30)))

        root.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })
        root.addView(addressTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })

        val scroll = ScrollView(context).apply { isFillViewport = true }
        scroll.addView(listContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(statusTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 6) })

        renderDir()
        return root
    }

    fun buildNotepadView(
        context: Context,
        winState: DesktopWindowState,
        initialFile: File?
    ): View {
        var activeFile: File? = initialFile
        var fontSizeSp = 13.5f
        var mono = true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13, 19, 32))
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        }

        val statusTv = UiKit.label(context, "", 11f, UiKit.MUTED)

        val editor = EditText(context).apply {
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.rgb(236, 242, 255))
            setHintTextColor(UiKit.MUTED)
            hint = "Ketik catatan, kode, atau script .bat di sini…"
            textSize = fontSizeSp
            typeface = Typeface.MONOSPACE
            background = UiKit.rounded(context, Color.rgb(9, 14, 24), 10)
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        }

        fun updateStatus() {
            val txt = editor.text?.toString().orEmpty()
            val lines = if (txt.isEmpty()) 1 else txt.count { it == '\n' } + 1
            val words = txt.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            val pathName = activeFile?.let { VirtualDrive.toWindowsPath(context, it) } ?: "Belum disimpan (Untitled)"
            statusTv.text = "$pathName  ·  Baris: $lines  |  Kata: $words  |  Karakter: ${txt.length}  |  UTF-8"
            winState.titleView.text = "Notepad — ${activeFile?.name ?: "Untitled.txt"}"
        }

        activeFile?.takeIf { it.exists() && it.isFile }?.let { f ->
            try {
                editor.setText(f.readText().take(120_000))
            } catch (e: Throwable) {
                editor.setText("Gagal membaca file: ${e.message}")
            }
        }

        val barScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        bar.addView(UiKit.chipButton(context, "📄 Baru") {
            activeFile = null
            editor.setText("")
            updateStatus()
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        bar.addView(UiKit.chipButton(context, "📂 Buka") {
            val docs = VirtualDrive.docsDir(context).listFiles()?.filter { it.isFile }.orEmpty() +
                VirtualDrive.installersDir(context).listFiles()?.filter { it.extension in listOf("bat", "txt", "ini") }.orEmpty()
            if (docs.isEmpty()) {
                Toast.makeText(context, "Belum ada file di Documents", Toast.LENGTH_SHORT).show()
            } else {
                val names = docs.map { it.name }.toTypedArray()
                AlertDialog.Builder(context)
                    .setTitle("Buka File")
                    .setItems(names) { _, idx ->
                        activeFile = docs[idx]
                        editor.setText(docs[idx].readText().take(120_000))
                        updateStatus()
                    }
                    .show()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        fun promptSaveAs() {
            val input = EditText(context).apply {
                setText(activeFile?.name ?: "Dokumen_Baru.txt")
                setSingleLine(true)
            }
            AlertDialog.Builder(context)
                .setTitle("Simpan ke C:\\Users\\Administrator\\Documents")
                .setView(input)
                .setPositiveButton("Simpan") { _, _ ->
                    val name = input.text?.toString()?.trim().orEmpty().ifEmpty { "Dokumen.txt" }
                    val target = File(VirtualDrive.docsDir(context), name)
                    target.writeText(editor.text?.toString().orEmpty())
                    activeFile = target
                    updateStatus()
                    Toast.makeText(context, "Tersimpan: ${target.name}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        bar.addView(UiKit.chipButton(context, "💾 Simpan", Color.argb(85, 37, 99, 235)) {
            val f = activeFile
            if (f != null) {
                f.writeText(editor.text?.toString().orEmpty())
                updateStatus()
                Toast.makeText(context, "Tersimpan!", Toast.LENGTH_SHORT).show()
            } else {
                promptSaveAs()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        bar.addView(UiKit.chipButton(context, "💾 Save As") { promptSaveAs() },
            LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        bar.addView(UiKit.chipButton(context, "A+") {
            fontSizeSp = (fontSizeSp + 1.5f).coerceAtMost(24f)
            editor.textSize = fontSizeSp
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        bar.addView(UiKit.chipButton(context, "A-") {
            fontSizeSp = (fontSizeSp - 1.5f).coerceAtLeast(10f)
            editor.textSize = fontSizeSp
        }, LinearLayout.LayoutParams(-2, dp(context, 30)).apply { rightMargin = dp(context, 6) })

        bar.addView(UiKit.chipButton(context, "Font") {
            mono = !mono
            editor.typeface = if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF
        }, LinearLayout.LayoutParams(-2, dp(context, 30)))

        barScroll.addView(bar)
        root.addView(barScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(statusTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 6) })

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateStatus()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        updateStatus()
        return root
    }
}
