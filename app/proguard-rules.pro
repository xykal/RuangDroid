# ==============================================================================
# HyperDeX v4.0.0 — R8 Hardening & JNI / Shizuku Preservation Rules
# ==============================================================================

# Obfuscate internal non-entry classes into compact namespace
-repackageclasses 'hx'
-allowaccessmodification

# Keep JNI entrypoints for Rust (librust_hypercore.so), C11 (libhyper_sys_c.so), and C++20 (libhyper_gfx_cc.so)
-keep class dev.xykal.hyperdex.NativeCore {
    *;
}

# Keep Application & Activity entrypoints referenced in AndroidManifest.xml
-keep public class dev.xykal.hyperdex.HyperDexApp { *; }
-keep public class dev.xykal.hyperdex.MainActivity { *; }
-keep public class dev.xykal.hyperdex.CrashActivity { *; }

# Keep Shizuku IPC & reflective newProcess methods intact
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Preserve ActivityOptions reflective multi-window call
-keepclassmembers class android.app.ActivityOptions {
    public *** setLaunchWindowingMode(...);
}
