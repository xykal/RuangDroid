package dev.xykal.hyperdex

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface DesktopHostCallbacks {
    val overlays: OverlayKit
    fun showToast(title: String, message: String)
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
    fun openShizukuHub()
    fun openNativeHub()
    fun openExeCenter(targetExe: File? = null)
    fun pickExternalExeFile()
    fun pickCustomWallpaper()
    fun pickAudioFromStorage()
    fun refreshDesktopUI()
    fun isDefaultHomeActive(): Boolean
    fun requestSetDefaultLauncher()
    fun unlockAndSwitchDefaultLauncher()
    fun showOnboardingWizard()
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
            setBackgroundColor(Color.rgb(13, 20, 34))
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        }

        val placesScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val placesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val addressTv = UiKit.label(context, "", 11.2f, Color.rgb(147, 197, 253), bold = true).apply {
            background = UiKit.rounded(context, Color.rgb(9, 14, 24), 8)
            setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
        }

        val statusTv = UiKit.label(context, "", 10.5f, UiKit.MUTED)
        val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }

        fun renderDir() {
            val winPath = VirtualDrive.toWindowsPath(context, currentDir)
            addressTv.text = winPath
            winState.titleView.text = "Explorer — ${currentDir.name.ifEmpty { "C:\\" }}"

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
                    UiKit.label(context, "Folder ini kosong.", 12f, UiKit.MUTED).apply {
                        setPadding(dp(context, 12), dp(context, 18), dp(context, 12), dp(context, 18))
                    }
                )
            } else {
                val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                for (f in children) {
                    val ext = f.extension.lowercase(Locale.ROOT)
                    val iconKey = when {
                        f.isDirectory -> "dir_item"
                        ext in listOf("exe", "msi", "com") -> "exe_item"
                        ext in listOf("bat", "cmd", "sh") -> "file_bat"
                        ext in listOf("png", "jpg", "jpeg", "webp", "bmp") -> "file_img"
                        ext in listOf("mp3", "wav", "ogg", "m4a") -> "file_audio"
                        else -> "file_txt"
                    }

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(context, 9), dp(context, 7), dp(context, 9), dp(context, 7))
                        background = UiKit.rounded(context, Color.argb(22, 255, 255, 255), 9)
                        UiKit.attachPressBounce(this, 0.98f)
                        setOnClickListener {
                            if (f.isDirectory) {
                                currentDir = f
                                renderDir()
                            } else {
                                when (ext) {
                                    "exe", "msi", "com" -> host.openExeFile(f)
                                    "zip" -> {
                                        host.overlays.showContextMenu(
                                            title = "Arsip ZIP — ${f.name}",
                                            items = listOf(
                                                "Inspeksi Isi .ZIP (Rust Engine)" to {
                                                    val info = NativeCore.safeInspectZip(f)
                                                    host.overlays.showInputPrompt("Isi Arsip (${f.name})", info, "", "Tutup") {}
                                                },
                                                "Ekstrak .ZIP di Folder Ini" to {
                                                    try {
                                                        val outFolder = File(currentDir, f.nameWithoutExtension.ifEmpty { "Extracted" }).apply { mkdirs() }
                                                        val count = NativeCore.extractZipSafe(f, outFolder)
                                                        renderDir()
                                                        host.showToast("ZIP Extractor", "$count file diekstrak ke ${outFolder.name}")
                                                    } catch (t: Throwable) {
                                                        host.showToast("ZIP Error", t.message ?: "Gagal ekstrak")
                                                    }
                                                }
                                            )
                                        )
                                    }
                                    "bat", "cmd", "sh" -> host.openBatchScript(f)
                                    "png", "jpg", "jpeg", "webp", "bmp" -> host.openImageFile(f)
                                    "mp3", "wav", "ogg", "m4a" -> host.openAudioFile(f)
                                    else -> host.openNotepad(f)
                                }
                            }
                        }
                        setOnLongClickListener {
                            val menuList = mutableListOf<Pair<String, () -> Unit>>(
                                "Buka Item" to { performClick(); Unit },
                                "Salin (Copy)" to {
                                    clipboardFile = f
                                    host.showToast("Clipboard Explorer", "Disalin: ${f.name}")
                                },
                                "Kompres ke Arsip .ZIP" to {
                                    val destZip = File(currentDir, "${f.name}.zip")
                                    if (NativeCore.compressToZip(f, destZip)) {
                                        renderDir()
                                        host.showToast("ZIP Archiver", "Tersimpan: ${destZip.name}")
                                    } else {
                                        host.showToast("ZIP Error", "Gagal membuat arsip ZIP")
                                    }
                                }
                            )
                            if (f.isFile) {
                                menuList.add("Cek Hash SHA-256 & CRC32 (Rust/C)" to {
                                    val report = NativeCore.safeFileHashSummary(f)
                                    host.overlays.showInputPrompt("Hasil Hash Native (${f.name})", report, "", "Tutup") {}
                                })
                            }
                            menuList.add("Ubah Nama (Rename)" to {
                                host.overlays.showInputPrompt("Ubah Nama File/Folder", f.name) { newName ->
                                    if (newName.isNotEmpty()) {
                                        f.renameTo(File(currentDir, newName))
                                        renderDir()
                                    }
                                }
                            })
                            menuList.add("Hapus Permanen" to {
                                f.deleteRecursively()
                                renderDir()
                                host.refreshDesktopUI()
                            })
                            host.overlays.showContextMenu(
                                title = f.name,
                                items = menuList
                            )
                            true
                        }
                    }

                    val iv = ImageView(context).apply {
                        setImageDrawable(VectorIcons.get(context, iconKey, dp(context, 24)))
                    }
                    row.addView(iv, LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)).apply { rightMargin = dp(context, 10) })

                    val metaCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                    metaCol.addView(UiKit.label(context, f.name, 12f, UiKit.INK, bold = f.isDirectory))
                    val detail = if (f.isDirectory) {
                        val cnt = f.listFiles()?.size ?: 0
                        "Folder · $cnt item · ${df.format(Date(f.lastModified()))}"
                    } else {
                        "${formatSize(f.length())} · ${df.format(Date(f.lastModified()))}"
                    }
                    metaCol.addView(UiKit.label(context, detail, 10f, UiKit.MUTED))
                    row.addView(metaCol, LinearLayout.LayoutParams(0, -2, 1f))

                    if (!f.isDirectory && ext in listOf("exe", "msi")) {
                        row.addView(UiKit.chipButton(context, "Run EXE", Color.argb(90, 59, 130, 246)) {
                            host.openExeFile(f)
                        })
                    }

                    listContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 4) })
                }
            }
            val clipNote = clipboardFile?.let { "  |  Clipboard: ${it.name}" }.orEmpty()
            statusTv.text = "${children.size} item$clipNote  ·  Tahan item untuk menu konteks"
        }

        val bookmarks = listOf(
            "C:\\" to VirtualDrive.root(context),
            "Desktop" to VirtualDrive.desktopDir(context),
            "Documents" to VirtualDrive.docsDir(context),
            "Installers" to VirtualDrive.installersDir(context),
            "Program Files" to VirtualDrive.programFilesDir(context),
            "Pictures" to VirtualDrive.picturesDir(context),
            "Android Dir" to (context.getExternalFilesDir(null) ?: Environment.getExternalStorageDirectory())
        )
        for ((title, dir) in bookmarks) {
            placesRow.addView(
                UiKit.chipButton(context, title) {
                    if (dir.exists()) {
                        currentDir = dir
                        renderDir()
                    }
                },
                LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) }
            )
        }
        placesScroll.addView(placesRow)
        root.addView(placesScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 5) })

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actionRow.addView(UiKit.chipButton(context, "Naik") {
            currentDir.parentFile?.takeIf { it.canRead() }?.let {
                currentDir = it; renderDir()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        actionRow.addView(UiKit.chipButton(context, "+ Folder") {
            host.overlays.showInputPrompt("Buat Folder Baru", "", "Nama folder", "Buat") { name ->
                File(currentDir, name.ifEmpty { "New Folder" }).mkdirs()
                renderDir()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        actionRow.addView(UiKit.chipButton(context, "+ File") {
            host.overlays.showInputPrompt("Buat File Teks / Script", "Catatan_Baru.txt", "", "Buat") { name ->
                val target = File(currentDir, name.ifEmpty { "Catatan.txt" })
                if (!target.exists()) target.writeText("")
                renderDir()
                host.openNotepad(target)
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        actionRow.addView(UiKit.chipButton(context, "Paste") {
            val src = clipboardFile
            if (src != null && src.exists()) {
                try {
                    src.copyRecursively(File(currentDir, src.name), overwrite = true)
                    renderDir()
                } catch (_: Throwable) {
                }
            } else {
                host.showToast("Explorer", "Clipboard masih kosong")
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        actionRow.addView(UiKit.chipButton(context, "Refresh") { renderDir() },
            LinearLayout.LayoutParams(-2, dp(context, 28)))

        root.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 5) })
        root.addView(addressTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 5) })

        val scroll = ScrollView(context).apply { isFillViewport = true }
        scroll.addView(listContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(statusTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 5) })

        renderDir()
        return root
    }

    fun buildNotepadView(
        context: Context,
        host: DesktopHostCallbacks,
        winState: DesktopWindowState,
        initialFile: File?
    ): View {
        var activeFile: File? = initialFile
        var fontSizeSp = 13f
        var mono = true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 18, 30))
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        }

        val statusTv = UiKit.label(context, "", 10.5f, UiKit.MUTED)

        val editor = EditText(context).apply {
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.rgb(236, 242, 255))
            setHintTextColor(UiKit.MUTED)
            hint = "Ketik kode, dokumen, atau script .bat di sini…"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, fontSizeSp))
            typeface = Typeface.MONOSPACE
            background = UiKit.rounded(context, Color.rgb(8, 12, 22), 10)
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        }

        fun updateStatus() {
            val txt = editor.text?.toString().orEmpty()
            val lines = if (txt.isEmpty()) 1 else txt.count { it == '\n' } + 1
            val words = txt.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            val pathName = activeFile?.let { VirtualDrive.toWindowsPath(context, it) } ?: "Untitled.txt"
            statusTv.text = "$pathName  ·  Baris: $lines  |  Kata: $words  |  Karakter: ${txt.length}"
            winState.titleView.text = "Notepad — ${activeFile?.name ?: "Untitled.txt"}"
        }

        activeFile?.takeIf { it.exists() && it.isFile }?.let { f ->
            try { editor.setText(f.readText().take(120_000)) } catch (_: Throwable) {}
        }

        val barScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        bar.addView(UiKit.chipButton(context, "Baru") {
            activeFile = null
            editor.setText("")
            updateStatus()
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        bar.addView(UiKit.chipButton(context, "Buka") {
            val docs = VirtualDrive.docsDir(context).listFiles()?.filter { it.isFile }.orEmpty() +
                VirtualDrive.installersDir(context).listFiles()?.filter { it.extension in listOf("bat", "txt", "ini") }.orEmpty()
            if (docs.isEmpty()) {
                host.showToast("Notepad", "Belum ada file tersimpan di Documents")
            } else {
                host.overlays.showContextMenu(
                    title = "Pilih File untuk Dibuka",
                    items = docs.map { doc ->
                        doc.name to {
                            activeFile = doc
                            editor.setText(doc.readText().take(120_000))
                            updateStatus()
                        }
                    }
                )
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        fun promptSaveAs() {
            host.overlays.showInputPrompt("Simpan ke Documents", activeFile?.name ?: "Dokumen_Baru.txt", "", "Simpan") { name ->
                val target = File(VirtualDrive.docsDir(context), name.ifEmpty { "Dokumen.txt" })
                target.writeText(editor.text?.toString().orEmpty())
                activeFile = target
                updateStatus()
                host.showToast("Notepad", "Tersimpan ke ${target.name}")
            }
        }

        bar.addView(UiKit.chipButton(context, "Simpan", Color.argb(100, 37, 99, 235)) {
            val f = activeFile
            if (f != null) {
                f.writeText(editor.text?.toString().orEmpty())
                updateStatus()
                host.showToast("Notepad", "Perubahan disimpan")
            } else {
                promptSaveAs()
            }
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        bar.addView(UiKit.chipButton(context, "Save As") { promptSaveAs() },
            LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        bar.addView(UiKit.chipButton(context, "A+") {
            fontSizeSp = (fontSizeSp + 1.5f).coerceAtMost(22f)
            editor.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, fontSizeSp))
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        bar.addView(UiKit.chipButton(context, "A-") {
            fontSizeSp = (fontSizeSp - 1.5f).coerceAtLeast(10f)
            editor.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, fontSizeSp))
        }, LinearLayout.LayoutParams(-2, dp(context, 28)).apply { rightMargin = dp(context, 5) })

        bar.addView(UiKit.chipButton(context, "Font") {
            mono = !mono
            editor.typeface = if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF
        }, LinearLayout.LayoutParams(-2, dp(context, 28)))

        barScroll.addView(bar)
        root.addView(barScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 5) })
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(statusTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 5) })

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateStatus()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        updateStatus()
        return root
    }
}
