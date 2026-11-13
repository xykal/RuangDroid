package dev.xykal.hyperdex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class SecurityVerdict(
    val verified: Boolean,
    val headline: String,
    val detail: String,
    val liveCertSha256: String,
    val rustReport: String,
    val cEnvReport: String,
    val cApkReport: String,
    val ccQuorumReport: String
)

object NativeCore {

    val cLoaded: Boolean
    val ccLoaded: Boolean
    val rustLoaded: Boolean

    init {
        cLoaded = try {
            System.loadLibrary("hyper_sys_c")
            true
        } catch (_: Throwable) {
            false
        }
        ccLoaded = try {
            System.loadLibrary("hyper_gfx_cc")
            true
        } catch (_: Throwable) {
            false
        }
        rustLoaded = try {
            System.loadLibrary("rust_hypercore")
            true
        } catch (_: Throwable) {
            false
        }
    }

    val allLoaded: Boolean
        get() = cLoaded && ccLoaded && rustLoaded

    // --- Pure C11 JNI (libhyper_sys_c.so) ---
    @JvmStatic external fun cReadKernelTelemetry(): String
    @JvmStatic external fun cVerifyProcessEnv(expectedPkg: String): String
    @JvmStatic external fun cInspectApkIntegrity(apkPath: String): String
    @JvmStatic external fun cFileHashReport(filePath: String): String
    @JvmStatic external fun cCpuBenchScore(rounds: Int): Long

    // --- Modern C++20 + OpenGL ES 3.0 JNI (libhyper_gfx_cc.so) ---
    @JvmStatic external fun ccGlInit()
    @JvmStatic external fun ccGlResize(width: Int, height: Int)
    @JvmStatic external fun ccGlRenderFrame(timeSec: Float, accentColor: Int)
    @JvmStatic external fun ccAcrylicBlurBitmap(bitmap: Bitmap, radius: Int, tintArgb: Int): Boolean
    @JvmStatic external fun ccAnalyzePeBinary(filePath: String): String
    @JvmStatic external fun ccVerifyBuildQuorum(liveCertSha256: String, livePkg: String): String

    // --- Safe Rust 2021 JNI (librust_hypercore.so) ---
    @JvmStatic external fun rustVerifySignature(certBytes: ByteArray, pkgName: String): String
    @JvmStatic external fun rustInspectZipArchive(filePath: String): String
    @JvmStatic external fun rustSha256File(filePath: String): String
    @JvmStatic external fun rustSha256Text(text: String): String
    @JvmStatic external fun rustEvalMath(expr: String): String
    @JvmStatic external fun rustBenchSha256(rounds: Int): String

    private val bgExec = Executors.newSingleThreadExecutor()

    private fun extractSigningCertBytes(context: Context): ByteArray {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val info = pi.signingInfo
                val sigs = if (info != null) {
                    if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
                } else null
                sigs?.firstOrNull()?.toByteArray() ?: ByteArray(0)
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                pi.signatures?.firstOrNull()?.toByteArray() ?: ByteArray(0)
            }
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }

    private fun sha256HexFallback(bytes: ByteArray): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256").digest(bytes)
            md.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            "unavailable"
        }
    }

    /**
     * 4-Layer Polyglot Anti-Clone Hard Lock Verification (v5.0):
     * 1. Rust XOR-masked X.509 SHA-256 + CI HMAC-SHA256 Build Seal
     * 2. C11 /proc/self/cmdline + Frida/Substrate/AppCloner memory map scanner
     * 3. C11 Direct base.apk APK Sig Block 42 (V2/V3) + classes.dex CRC32 ZIP inspector
     * 4. C++20 XOR-masked Build Quorum check
     */
    fun verifyAppSecurity(context: Context): SecurityVerdict {
        val pkg = context.packageName
        val certBytes = extractSigningCertBytes(context)
        val certSha = sha256HexFallback(certBytes)
        val apkPath = try { context.applicationInfo.sourceDir ?: "" } catch (_: Throwable) { "" }

        if (!allLoaded) {
            return SecurityVerdict(
                verified = false,
                headline = "NATIVE SECURITY ENGINE MISSING / TAMPERED",
                detail = "Salah satu native library (Rust=$rustLoaded, C11=$cLoaded, C++20=$ccLoaded) hilang atau dimodifikasi.",
                liveCertSha256 = certSha,
                rustReport = "loaded=$rustLoaded",
                cEnvReport = "loaded=$cLoaded",
                cApkReport = "loaded=$cLoaded",
                ccQuorumReport = "loaded=$ccLoaded"
            )
        }

        val rustRes = try { rustVerifySignature(certBytes, pkg) } catch (t: Throwable) { "ERR|${t.message}" }
        val cEnvRes = try { cVerifyProcessEnv("dev.xykal.hyperdex") } catch (t: Throwable) { "ERR|${t.message}" }
        val cApkRes = if (apkPath.isNotEmpty()) {
            try { cInspectApkIntegrity(apkPath) } catch (t: Throwable) { "ERR|${t.message}" }
        } else "OK|skip_path"
        val ccRes = try { ccVerifyBuildQuorum(certSha, pkg) } catch (t: Throwable) { "ERR|${t.message}" }

        val rustOk = rustRes.startsWith("VERIFIED|")
        val cEnvOk = cEnvRes.startsWith("OK|")
        val cApkOk = cApkRes.startsWith("OK|")
        val ccOk = ccRes.startsWith("VERIFIED|")

        if (rustOk && cEnvOk && cApkOk && ccOk) {
            return SecurityVerdict(
                verified = true,
                headline = "OFFICIAL HYPERDEX v5.0 VERIFIED (4/4 QUORUM)",
                detail = "Rust + C11 + C++20 mengonfirmasi sertifikat rilis resmi, APK Sig Block v2/v3, dan integritas classes.dex.",
                liveCertSha256 = certSha,
                rustReport = rustRes,
                cEnvReport = cEnvRes,
                cApkReport = cApkRes,
                ccQuorumReport = ccRes
            )
        }

        val reason = when {
            !cEnvOk -> "Terdeteksi App Cloner / Frida / perubahan nama proses ($cEnvRes)."
            !cApkOk -> " Struktur biner APK V2/V3 atau classes.dex telah dimodifikasi ($cApkRes)."
            !rustOk -> "Sertifikat tanda tangan APK atau Build Seal tidak cocok dengan rilis resmi HyperDeX ($rustRes)."
            else -> "Quorum C++20 mendeteksi build fork/clone tidak resmi ($ccRes)."
        }

        return SecurityVerdict(
            verified = false,
            headline = "CLONED / TAMPERED BUILD BLOCKED",
            detail = reason,
            liveCertSha256 = certSha,
            rustReport = rustRes,
            cEnvReport = cEnvRes,
            cApkReport = cApkRes,
            ccQuorumReport = ccRes
        )
    }

    /**
     * OpenGL ES 3.0 GPU Live Aurora Shader Wallpaper powered directly by C++20 (libhyper_gfx_cc.so).
     */
    fun createLiveShaderWallpaperView(context: Context, prefs: DesktopPrefs): GLSurfaceView? {
        if (!ccLoaded) return null
        return try {
            object : GLSurfaceView(context) {
                private val tStart = SystemClock.elapsedRealtime()
                init {
                    setEGLContextClientVersion(3)
                    preserveEGLContextOnPause = true
                    setRenderer(object : Renderer {
                        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                            try { ccGlInit() } catch (_: Throwable) {}
                        }
                        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                            try { ccGlResize(width, height) } catch (_: Throwable) {}
                        }
                        override fun onDrawFrame(gl: GL10?) {
                            val sec = (SystemClock.elapsedRealtime() - tStart) / 1000f
                            try { ccGlRenderFrame(sec, prefs.currentTheme.accent) } catch (_: Throwable) {}
                        }
                    })
                    renderMode = RENDERMODE_CONTINUOUSLY
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun safeApplyAcrylicBlur(src: Bitmap, radius: Int = 18, tintArgb: Int = Color.argb(48, 8, 14, 28)): Bitmap {
        if (!ccLoaded) return src
        return try {
            val targetW = (src.width / 3).coerceIn(160, 640)
            val targetH = (src.height / 3).coerceIn(100, 400)
            val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true)
            val mutable = if (scaled.isMutable && scaled.config == Bitmap.Config.ARGB_8888) {
                scaled
            } else {
                scaled.copy(Bitmap.Config.ARGB_8888, true)
            }
            ccAcrylicBlurBitmap(mutable, radius, tintArgb)
            mutable
        } catch (_: Throwable) {
            src
        }
    }

    fun safeFileHashSummary(file: File): String {
        val rustH = if (rustLoaded) try { rustSha256File(file.absolutePath) } catch (t: Throwable) { "ERR: ${t.message}" } else "unavailable"
        val cH = if (cLoaded) try { cFileHashReport(file.absolutePath) } catch (t: Throwable) { "ERR: ${t.message}" } else "unavailable"
        return "File    : ${file.name}\nSHA-256 : $rustH\n$cH"
    }

    fun safeInspectZip(zipFile: File): String {
        if (!rustLoaded) return "ERR: Rust engine offline"
        return try { rustInspectZipArchive(zipFile.absolutePath) } catch (t: Throwable) { "ERR: ${t.message}" }
    }

    fun compressToZip(source: File, destZip: File): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
                fun addNode(node: File, relPrefix: String) {
                    val entryName = if (relPrefix.isEmpty()) node.name else "$relPrefix/${node.name}"
                    if (node.isDirectory) {
                        val children = node.listFiles().orEmpty()
                        if (children.isEmpty()) {
                            zos.putNextEntry(ZipEntry("$entryName/"))
                            zos.closeEntry()
                        } else {
                            for (c in children) addNode(c, entryName)
                        }
                    } else {
                        zos.putNextEntry(ZipEntry(entryName))
                        BufferedInputStream(FileInputStream(node)).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                addNode(source, "")
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun extractZipSafe(zipFile: File, destDir: File): Int {
        var count = 0
        val canonicalBase = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalBase)) {
                    throw SecurityException("Zip-Slip blocked")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile)).use { zis.copyTo(it) }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    fun safeEvalMath(expr: String): String {
        if (!rustLoaded) return "ERR: Rust engine offline"
        return try { rustEvalMath(expr) } catch (t: Throwable) { "ERR: ${t.message}" }
    }

    fun buildHardLockShieldView(activity: Activity, verdict: SecurityVerdict): View {
        fun dp(v: Int): Int = UiKit.dp(activity, v)

        val outer = FrameLayout(activity).apply {
            setBackgroundColor(Color.rgb(10, 12, 22))
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.strokeRounded(
                activity,
                Color.rgb(18, 16, 28),
                Color.argb(190, 239, 68, 68),
                18
            )
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        val topRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(ImageView(activity).apply {
            setImageDrawable(VectorIcons.get(activity, "native_hub", dp(38)))
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) })

        val headCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        headCol.addView(UiKit.label(activity, "HyperDeX Security Shield v5.0 — Hard Lock", 15.5f, Color.rgb(248, 113, 113), bold = true))
        headCol.addView(UiKit.label(activity, verdict.headline, 12f, UiKit.INK, bold = true))
        topRow.addView(headCol, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(topRow)

        card.addView(
            UiKit.label(
                activity,
                "Aplikasi ini terdeteksi sebagai hasil clone, re-sign, modifikasi classes.dex, atau build fork tanpa kunci kriptografi resmi Xykal. Eksekusi desktop dikunci oleh 4-Layer Native Quorum (Rust + C11 + C++20).",
                11.5f,
                UiKit.MUTED,
                maxLines = 5
            ).apply { setPadding(0, dp(10), 0, dp(10)) }
        )

        val logScroll = ScrollView(activity).apply {
            background = UiKit.rounded(activity, Color.rgb(9, 11, 18), 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        logScroll.addView(TextView(activity).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(activity, 10.5f))
            setTextColor(Color.rgb(252, 165, 165))
            text = buildString {
                appendLine("Reason       : ${verdict.detail}")
                appendLine("Package ID   : ${activity.packageName}")
                appendLine("Live SHA-256 : ${verdict.liveCertSha256}")
                appendLine("Rust Guard   : ${verdict.rustReport}")
                appendLine("C11 Env      : ${verdict.cEnvReport}")
                appendLine("C11 APK/DEX  : ${verdict.cApkReport}")
                append("C++20 Quorum : ${verdict.ccQuorumReport}")
            }
        })
        card.addView(logScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }
        actions.addView(UiKit.chipButton(activity, "Download APK Resmi di GitHub", Color.argb(175, 37, 99, 235)) {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xykal/HyperDeX/releases"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {}
        }, LinearLayout.LayoutParams(-2, dp(34)).apply { rightMargin = dp(8) })

        actions.addView(UiKit.chipButton(activity, "Tutup", Color.argb(135, 220, 38, 38)) {
            activity.finishAffinity()
        }, LinearLayout.LayoutParams(-2, dp(34)))

        card.addView(actions)
        outer.addView(card, FrameLayout.LayoutParams(-1, -1))
        return outer
    }

    fun buildNativeHubView(
        context: Context,
        prefs: DesktopPrefs,
        host: DesktopHostCallbacks
    ): View {
        fun dp(v: Int): Int = UiKit.dp(context, v)
        val verdict = verifyAppSecurity(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val scroll = ScrollView(context).apply { isFillViewport = true }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val secCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.strokeRounded(
                context,
                Color.argb(46, 16, 185, 129),
                if (verdict.verified) Color.argb(120, 52, 211, 153) else Color.argb(140, 248, 113, 113),
                13
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        secCard.addView(
            UiKit.label(
                context,
                verdict.headline,
                12.5f,
                if (verdict.verified) Color.rgb(74, 222, 128) else Color.rgb(248, 113, 113),
                bold = true
            )
        )
        secCard.addView(
            UiKit.label(
                context,
                "Engines: Rust(XOR+ZIP)=${if (rustLoaded) "ON" else "OFF"} · C11(APK-V2+DEX)=${if (cLoaded) "ON" else "OFF"} · C++20(GLES3+Blur)=${if (ccLoaded) "ON" else "OFF"}",
                10.2f,
                UiKit.INK,
                maxLines = 2
            ).apply { setPadding(0, dp(3), 0, dp(4)) }
        )
        secCard.addView(
            TextView(context).apply {
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 9.8f))
                setTextColor(UiKit.MUTED)
                text = "Pkg       : ${context.packageName}\nCertSHA   : ${verdict.liveCertSha256.take(32)}…\nC11 Env   : ${verdict.cEnvReport}\nC11 APK   : ${verdict.cApkReport}\nC++ Quorum: ${verdict.ccQuorumReport}"
            }
        )
        col.addView(secCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val kernCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(context, Color.argb(30, 255, 255, 255), 13)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val kernHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        kernHeader.addView(UiKit.label(context, "C11 Linux Kernel & CPU Telemetry", 12f, prefs.currentTheme.accent, bold = true),
            LinearLayout.LayoutParams(0, -2, 1f))

        val kernText = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 10.2f))
            setTextColor(Color.rgb(190, 230, 255))
            text = if (cLoaded) try { cReadKernelTelemetry() } catch (t: Throwable) { "${t.message}" } else "C11 module offline"
        }
        kernHeader.addView(UiKit.chipButton(context, "Refresh /proc") {
            if (cLoaded) kernText.text = try { cReadKernelTelemetry() } catch (t: Throwable) { "${t.message}" }
        }, LinearLayout.LayoutParams(-2, dp(26)))
        kernCard.addView(kernHeader, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        kernCard.addView(kernText)
        col.addView(kernCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val benchCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(context, Color.argb(30, 255, 255, 255), 13)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        benchCard.addView(UiKit.label(context, "GPU OpenGL ES 3.0 Shader & Polyglot Benchmark", 12f, prefs.currentTheme.accent, bold = true))
        val benchOut = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 10.2f))
            setTextColor(Color.rgb(167, 243, 208))
            text = "Klik 'Run Benchmark' untuk menguji kecepatan Rust + C11 + C++20, atau nyalakan Live GPU Shader Wallpaper."
            setPadding(0, dp(5), 0, dp(7))
        }
        benchCard.addView(benchOut)

        val btnRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(UiKit.chipButton(context, "Run Benchmark", Color.argb(120, 79, 70, 229)) {
            benchOut.text = "Menjalankan benchmark native…"
            bgExec.execute {
                val cScore = if (cLoaded) cCpuBenchScore(4_000_000) else 0L
                val rScore = if (rustLoaded) rustBenchSha256(40_000) else "offline"
                val t0 = System.nanoTime()
                if (ccLoaded) {
                    val bmp = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.rgb(37, 99, 235))
                    ccAcrylicBlurBitmap(bmp, 14, Color.argb(60, 15, 23, 42))
                    bmp.recycle()
                }
                val ccMs = (System.nanoTime() - t0) / 1_000_000.0
                val abis = Build.SUPPORTED_ABIS.joinToString(", ")
                benchOut.post {
                    benchOut.text = buildString {
                        appendLine("Target ABI   : $abis")
                        appendLine("C11 Compute  : $cScore kOps/sec")
                        appendLine(rScore)
                        append("C++20 Blur   : ${"%.2f".format(ccMs)} ms (320x200 Separable StackBlur)")
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { rightMargin = dp(5) })

        btnRow.addView(UiKit.chipButton(
            context,
            if (prefs.liveShaderWallpaper) "GPU Shader: ON" else "GPU Shader: OFF",
            if (prefs.liveShaderWallpaper) Color.argb(130, 56, 189, 248) else Color.argb(50, 255, 255, 255)
        ) {
            prefs.liveShaderWallpaper = !prefs.liveShaderWallpaper
            host.refreshDesktopUI()
            host.showToast("OpenGL ES 3.0 C++", if (prefs.liveShaderWallpaper) "Live GPU Aurora Shader Aktif!" else "Mode Wallpaper Statis")
        }, LinearLayout.LayoutParams(0, dp(30), 1f))
        benchCard.addView(btnRow)
        col.addView(benchCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val labCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(context, Color.argb(30, 255, 255, 255), 13)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        labCard.addView(UiKit.label(context, "Rust Expression & Crypto Hash Console", 12f, prefs.currentTheme.accent, bold = true))

        val labInput = EditText(context).apply {
            setText("sqrt(144) + 2^10 * cos(0)")
            setSingleLine(true)
            setTextColor(UiKit.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.spPx(context, 11.5f))
            background = UiKit.rounded(context, Color.argb(40, 0, 0, 0), 9)
            setPadding(dp(10), 0, dp(10), 0)
        }
        labCard.addView(labInput, LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(6); bottomMargin = dp(6) })

        val labOut = UiKit.label(context, "Hasil Rust akan tampil di sini…", 10.8f, UiKit.INK, maxLines = 3)
        val labBtns = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        labBtns.addView(UiKit.chipButton(context, "Hitung Rumus (Rust)") {
            val q = labInput.text?.toString().orEmpty()
            labOut.text = "Hasil = ${safeEvalMath(q)}"
        }, LinearLayout.LayoutParams(0, dp(28), 1f).apply { rightMargin = dp(6) })

        labBtns.addView(UiKit.chipButton(context, "Hash SHA-256 (Rust)") {
            val q = labInput.text?.toString().orEmpty()
            val h = if (rustLoaded) rustSha256Text(q) else "offline"
            labOut.text = "SHA-256 = $h"
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        labCard.addView(labBtns, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        labCard.addView(labOut)
        col.addView(labCard)

        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }
}
