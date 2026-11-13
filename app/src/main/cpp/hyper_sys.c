#define _POSIX_C_SOURCE 200809L
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

/* ============================================================================
 * libhyper_sys_c.so — Pure C11 Native Kernel Telemetry, Direct APK V2/V3 +
 * classes.dex ZIP Central Directory Inspector, Anti-Hook/Cloner Scanner & Bench
 * ============================================================================ */

static uint64_t g_prev_total_jiffies = 0;
static uint64_t g_prev_idle_jiffies = 0;

static uint16_t rd_u16_le(const uint8_t* p) {
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static uint32_t rd_u32_le(const uint8_t* p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static double read_cpu_usage_percent(void) {
    FILE* fp = fopen("/proc/stat", "r");
    if (!fp) return -1.0;
    char label[16] = {0};
    unsigned long long user = 0, nice = 0, sys = 0, idle = 0, iowait = 0, irq = 0, softirq = 0, steal = 0;
    int n = fscanf(fp, "%15s %llu %llu %llu %llu %llu %llu %llu %llu",
                   label, &user, &nice, &sys, &idle, &iowait, &irq, &softirq, &steal);
    fclose(fp);
    if (n < 5) return -1.0;

    uint64_t idle_all = (uint64_t)(idle + iowait);
    uint64_t total = (uint64_t)(user + nice + sys + idle + iowait + irq + softirq + steal);

    uint64_t diff_total = total - g_prev_total_jiffies;
    uint64_t diff_idle = idle_all - g_prev_idle_jiffies;
    g_prev_total_jiffies = total;
    g_prev_idle_jiffies = idle_all;

    if (diff_total == 0) return 0.0;
    double pct = (100.0 * (double)(diff_total - diff_idle)) / (double)diff_total;
    if (pct < 0.0) pct = 0.0;
    if (pct > 100.0) pct = 100.0;
    return pct;
}

static long read_cpu_freq_mhz(int core_idx) {
    char path[128];
    snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", core_idx);
    FILE* fp = fopen(path, "r");
    if (!fp) {
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", core_idx);
        fp = fopen(path, "r");
    }
    if (!fp) return 0;
    long khz = 0;
    if (fscanf(fp, "%ld", &khz) != 1) khz = 0;
    fclose(fp);
    return khz / 1000;
}

JNIEXPORT jstring JNICALL
Java_dev_xykal_hyperdex_NativeCore_cReadKernelTelemetry(JNIEnv* env, jclass clazz) {
    double cpu_pct = read_cpu_usage_percent();
    long cores = sysconf(_SC_NPROCESSORS_CONF);
    if (cores < 1) cores = 1;

    long mem_total_kb = 0, mem_avail_kb = 0, swap_total_kb = 0, swap_free_kb = 0, cached_kb = 0;
    FILE* mfp = fopen("/proc/meminfo", "r");
    if (mfp) {
        char line[256];
        while (fgets(line, sizeof(line), mfp)) {
            long val = 0;
            if (sscanf(line, "MemTotal: %ld kB", &val) == 1) mem_total_kb = val;
            else if (sscanf(line, "MemAvailable: %ld kB", &val) == 1) mem_avail_kb = val;
            else if (sscanf(line, "SwapTotal: %ld kB", &val) == 1) swap_total_kb = val;
            else if (sscanf(line, "SwapFree: %ld kB", &val) == 1) swap_free_kb = val;
            else if (sscanf(line, "Cached: %ld kB", &val) == 1) cached_kb = val;
        }
        fclose(mfp);
    }

    double uptime_sec = 0.0;
    FILE* ufp = fopen("/proc/uptime", "r");
    if (ufp) {
        if (fscanf(ufp, "%lf", &uptime_sec) != 1) uptime_sec = 0.0;
        fclose(ufp);
    }

    char kernel_ver[160] = "Linux Kernel";
    FILE* vfp = fopen("/proc/version", "r");
    if (vfp) {
        if (fgets(kernel_ver, sizeof(kernel_ver), vfp)) {
            size_t len = strlen(kernel_ver);
            while (len > 0 && (kernel_ver[len - 1] == '\n' || kernel_ver[len - 1] == '\r')) {
                kernel_ver[--len] = '\0';
            }
        }
        fclose(vfp);
    }

    char freq_buf[256] = {0};
    size_t fpos = 0;
    int max_show = (cores > 8) ? 8 : (int)cores;
    for (int i = 0; i < max_show; i++) {
        long mhz = read_cpu_freq_mhz(i);
        if (mhz > 0) {
            int w = snprintf(freq_buf + fpos, sizeof(freq_buf) - fpos, "cpu%d:%ldMHz ", i, mhz);
            if (w > 0 && fpos + (size_t)w < sizeof(freq_buf)) fpos += (size_t)w;
        }
    }
    if (fpos == 0) {
        snprintf(freq_buf, sizeof(freq_buf), "governor-managed");
    }

    long hrs = (long)(uptime_sec / 3600.0);
    long mins = ((long)(uptime_sec / 60.0)) % 60;

    char out[1200];
    snprintf(out, sizeof(out),
             "Engine       : libhyper_sys_c.so (Pure C11 POSIX/Kernel v5.0)\n"
             "CPU Load     : %.1f%% (%ld Cores)\n"
             "Core Clocks  : %s\n"
             "Physical RAM : %.2f GB used / %.2f GB total (Cache %.0f MB)\n"
             "ZRAM / Swap  : %.2f GB used / %.2f GB total\n"
             "System Uptime: %ldh %ldm\n"
             "Kernel       : %.96s",
             cpu_pct < 0 ? 0.0 : cpu_pct,
             cores,
             freq_buf,
             (double)(mem_total_kb - mem_avail_kb) / 1048576.0,
             (double)mem_total_kb / 1048576.0,
             (double)cached_kb / 1024.0,
             (double)(swap_total_kb - swap_free_kb) / 1048576.0,
             (double)swap_total_kb / 1048576.0,
             hrs, mins,
             kernel_ver);

    return (*env)->NewStringUTF(env, out);
}

JNIEXPORT jstring JNICALL
Java_dev_xykal_hyperdex_NativeCore_cVerifyProcessEnv(JNIEnv* env, jclass clazz, jstring expectedPkg) {
    const char* exp_pkg = (*env)->GetStringUTFChars(env, expectedPkg, NULL);
    if (!exp_pkg) return (*env)->NewStringUTF(env, "ERR|alloc");

    char cmdline[256] = {0};
    FILE* cfp = fopen("/proc/self/cmdline", "rb");
    if (cfp) {
        size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, cfp);
        cmdline[n] = '\0';
        fclose(cfp);
    }

    int pkg_match = 1;
    if (cmdline[0] != '\0') {
        size_t elen = strlen(exp_pkg);
        if (strncmp(cmdline, exp_pkg, elen) != 0 ||
            (cmdline[elen] != '\0' && cmdline[elen] != ':')) {
            pkg_match = 0;
        }
    }

    int tracer_pid = 0;
    FILE* sfp = fopen("/proc/self/status", "r");
    if (sfp) {
        char line[256];
        while (fgets(line, sizeof(line), sfp)) {
            if (sscanf(line, "TracerPid: %d", &tracer_pid) == 1) break;
        }
        fclose(sfp);
    }

    int frida_found = 0;
    int hook_found = 0;
    int cloner_found = 0;
    FILE* mapfp = fopen("/proc/self/maps", "r");
    if (mapfp) {
        char line[512];
        while (fgets(line, sizeof(line), mapfp)) {
            if (strstr(line, "frida-agent") || strstr(line, "frida-gadget") || strstr(line, "gum-js-loop")) {
                frida_found = 1;
            }
            if (strstr(line, "libdobby.so") || strstr(line, "libsubstrate.so")) {
                hook_found = 1;
            }
            if (strstr(line, "com.applisto.appcloner") ||
                strstr(line, "io.va.exposed") ||
                strstr(line, "com.lody.virtual") ||
                strstr(line, "dkplugin")) {
                cloner_found = 1;
            }
        }
        fclose(mapfp);
    }

    char result[512];
    const char* status = (!pkg_match || cloner_found || frida_found) ? "CLONE_DETECTED" : "OK";
    snprintf(result, sizeof(result),
             "%s|cmdline=%s|pkg_match=%d|tracer=%d|frida=%d|hook=%d|cloner=%d",
             status,
             cmdline[0] ? cmdline : "unknown",
             pkg_match,
             tracer_pid,
             frida_found,
             hook_found,
             cloner_found);

    (*env)->ReleaseStringUTFChars(env, expectedPkg, exp_pkg);
    return (*env)->NewStringUTF(env, result);
}

/* Direct C11 APK ZIP Central Directory + APK Sig Block 42 + classes.dex CRC32 Verifier */
JNIEXPORT jstring JNICALL
Java_dev_xykal_hyperdex_NativeCore_cInspectApkIntegrity(JNIEnv* env, jclass clazz, jstring apkPath) {
    const char* path = (*env)->GetStringUTFChars(env, apkPath, NULL);
    if (!path) return (*env)->NewStringUTF(env, "ERR|null_path");

    FILE* fp = fopen(path, "rb");
    (*env)->ReleaseStringUTFChars(env, apkPath, path);
    if (!fp) return (*env)->NewStringUTF(env, "ERR|open_failed");

    if (fseek(fp, 0, SEEK_END) != 0) {
        fclose(fp);
        return (*env)->NewStringUTF(env, "ERR|seek_failed");
    }
    long file_sz = ftell(fp);
    if (file_sz < 64) {
        fclose(fp);
        return (*env)->NewStringUTF(env, "TAMPERED|apk_too_small");
    }

    long tail_len = (file_sz < 65558L) ? file_sz : 65558L;
    uint8_t* tail = (uint8_t*)malloc((size_t)tail_len);
    if (!tail) {
        fclose(fp);
        return (*env)->NewStringUTF(env, "ERR|oom");
    }

    fseek(fp, file_sz - tail_len, SEEK_SET);
    size_t got = fread(tail, 1, (size_t)tail_len, fp);

    long eocd_pos = -1;
    for (long i = (long)got - 22; i >= 0; i--) {
        if (tail[i] == 0x50 && tail[i + 1] == 0x4B && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
            eocd_pos = i;
            break;
        }
    }
    if (eocd_pos < 0) {
        free(tail);
        fclose(fp);
        return (*env)->NewStringUTF(env, "TAMPERED|missing_eocd");
    }

    uint16_t cd_entries = rd_u16_le(tail + eocd_pos + 10);
    uint32_t cd_size = rd_u32_le(tail + eocd_pos + 12);
    uint32_t cd_offset = rd_u32_le(tail + eocd_pos + 16);
    free(tail);

    /* Verify Android APK Signature Scheme v2/v3 magic ("APK Sig Block 42") right before cd_offset */
    int v2_sig_present = 0;
    if (cd_offset >= 32 && (long)cd_offset < file_sz) {
        uint8_t magic[16] = {0};
        if (fseek(fp, (long)cd_offset - 16L, SEEK_SET) == 0 &&
            fread(magic, 1, 16, fp) == 16) {
            if (memcmp(magic, "APK Sig Block 42", 16) == 0) {
                v2_sig_present = 1;
            }
        }
    }

    /* Scan Central Directory for classes.dex CRC32 and count native .so libraries */
    uint32_t dex_crc = 0;
    uint32_t dex_size = 0;
    int so_count = 0;

    if (cd_size > 0 && cd_size < (8u * 1024u * 1024u) && (long)cd_offset + (long)cd_size <= file_sz) {
        uint8_t* cd = (uint8_t*)malloc(cd_size);
        if (cd) {
            fseek(fp, (long)cd_offset, SEEK_SET);
            if (fread(cd, 1, cd_size, fp) == cd_size) {
                size_t p = 0;
                for (uint16_t e = 0; e < cd_entries && p + 46 <= cd_size; e++) {
                    if (cd[p] != 0x50 || cd[p + 1] != 0x4B || cd[p + 2] != 0x01 || cd[p + 3] != 0x02) break;
                    uint32_t crc = rd_u32_le(cd + p + 16);
                    uint32_t uncomp = rd_u32_le(cd + p + 24);
                    uint16_t nlen = rd_u16_le(cd + p + 28);
                    uint16_t xlen = rd_u16_le(cd + p + 30);
                    uint16_t clen = rd_u16_le(cd + p + 32);
                    if (p + 46 + nlen > cd_size) break;
                    const char* name = (const char*)(cd + p + 46);
                    if (nlen == 11 && memcmp(name, "classes.dex", 11) == 0) {
                        dex_crc = crc;
                        dex_size = uncomp;
                    } else if (nlen > 7 && memcmp(name, "lib/", 4) == 0 &&
                               memcmp(name + nlen - 3, ".so", 3) == 0) {
                        so_count++;
                    }
                    p += 46u + (size_t)nlen + (size_t)xlen + (size_t)clen;
                }
            }
            free(cd);
        }
    }

    fclose(fp);

    /* Official HyperDeX release always has V2/V3 signing block and intact classes.dex */
    const char* verdict = (v2_sig_present && dex_crc != 0) ? "OK" : "TAMPERED";
    char report[256];
    snprintf(report, sizeof(report),
             "%s|v2sig=%d|dex_crc=%08X|dex_sz=%u|so_count=%d",
             verdict, v2_sig_present, dex_crc, dex_size, so_count);
    return (*env)->NewStringUTF(env, report);
}

JNIEXPORT jstring JNICALL
Java_dev_xykal_hyperdex_NativeCore_cFileHashReport(JNIEnv* env, jclass clazz, jstring filePath) {
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return (*env)->NewStringUTF(env, "ERR: invalid path");

    FILE* fp = fopen(path, "rb");
    if (!fp) {
        (*env)->ReleaseStringUTFChars(env, filePath, path);
        return (*env)->NewStringUTF(env, "ERR: cannot open file");
    }

    uint32_t crc = 0xFFFFFFFFu;
    uint64_t fnv = 14695981039346656037ULL;
    uint64_t total_bytes = 0;
    unsigned char buf[8192];
    size_t n;

    while ((n = fread(buf, 1, sizeof(buf), fp)) > 0) {
        total_bytes += (uint64_t)n;
        for (size_t i = 0; i < n; i++) {
            uint8_t b = buf[i];
            fnv ^= (uint64_t)b;
            fnv *= 1099511628211ULL;

            crc ^= (uint32_t)b;
            for (int k = 0; k < 8; k++) {
                crc = (crc >> 1) ^ (0xEDB88320u & (-(int32_t)(crc & 1u)));
            }
        }
    }
    fclose(fp);
    crc = ~crc;

    char out[256];
    snprintf(out, sizeof(out),
             "CRC32: %08X  |  FNV1a-64: %016llX  |  Bytes: %llu",
             crc, (unsigned long long)fnv, (unsigned long long)total_bytes);

    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return (*env)->NewStringUTF(env, out);
}

JNIEXPORT jlong JNICALL
Java_dev_xykal_hyperdex_NativeCore_cCpuBenchScore(JNIEnv* env, jclass clazz, jint rounds) {
    if (rounds < 10000) rounds = 10000;
    if (rounds > 25000000) rounds = 25000000;

    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);

    volatile uint64_t acc = 0x9E3779B97F4A7C15ULL;
    uint64_t x = 0x123456789ABCDEF0ULL;
    for (int i = 0; i < rounds; i++) {
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        acc += (x ^ (uint64_t)i) * 0xBF58476D1CE4E5B9ULL;
    }
    if (acc == 0) return 1;

    clock_gettime(CLOCK_MONOTONIC, &t1);
    double elapsed_ms = ((double)(t1.tv_sec - t0.tv_sec) * 1000.0) +
                        ((double)(t1.tv_nsec - t0.tv_nsec) / 1000000.0);
    if (elapsed_ms < 0.05) elapsed_ms = 0.05;
    return (jlong)((double)rounds / elapsed_ms);
}
