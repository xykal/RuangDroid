#include <jni.h>
#include <android/bitmap.h>
#include <GLES3/gl3.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

/* ============================================================================
 * libhyper_gfx_cc.so — Modern C++20 Native OpenGL ES 3.0 GPU Shader Engine,
 * Acrylic StackBlur, PE32/PE64 Dissector & XOR-Masked Build Quorum Verifier
 * ============================================================================ */

#ifndef HYPERDEX_BUILD_PEPPER
#define HYPERDEX_BUILD_PEPPER "UNOFFICIAL_LOCAL_OR_FORK_BUILD"
#endif

namespace {

// Rolling-key XOR obfuscated security pins (zero plaintext hashes in .rodata!)
static constexpr uint8_t kObfCert[64] = {
    0xc1, 0xf2, 0xd1, 0x37, 0x15, 0x23, 0x54, 0xb9, 0xfc, 0x8e, 0xbb, 0xc5, 0x7a, 0x5f, 0x3c, 0x1d,
    0xf6, 0xd2, 0xe0, 0xc7, 0x24, 0x53, 0x67, 0x49, 0xb8, 0xcb, 0xab, 0xd9, 0x32, 0x4f, 0x7f, 0x5a,
    0xe6, 0xc7, 0xa0, 0xd6, 0x66, 0x11, 0x78, 0x50, 0x1c, 0xa9, 0x8e, 0xe8, 0xc3, 0x2c, 0x0d, 0x3e,
    0x13, 0xa3, 0x87, 0xe3, 0xc3, 0x74, 0x06, 0x68, 0x5a, 0xb7, 0xcb, 0xf8, 0x8d, 0x38, 0x4c, 0x7e
};

static constexpr uint8_t kObfSeal[64] = {
    0x95, 0xff, 0x84, 0x36, 0x1a, 0x26, 0x00, 0xb3, 0xfe, 0xdb, 0xbe, 0xcb, 0x2e, 0x0d, 0x38, 0x41,
    0xf5, 0x8f, 0xb1, 0x90, 0x21, 0x03, 0x66, 0x44, 0xee, 0x96, 0xfd, 0xdc, 0x69, 0x4f, 0x7d, 0x0c,
    0xe1, 0x90, 0xf2, 0xd2, 0x65, 0x10, 0x76, 0x53, 0x1d, 0xa8, 0x88, 0xbd, 0xcc, 0x28, 0x0b, 0x61,
    0x4f, 0xf4, 0xd6, 0xe0, 0xcb, 0x24, 0x53, 0x35, 0x59, 0xba, 0x9d, 0xfa, 0xd2, 0x39, 0x1c, 0x70
};

static constexpr uint8_t kObfPkg[18] = {
    0xc3, 0xa3, 0x93, 0x2a, 0x5b, 0x3b, 0x0a, 0xe1, 0xf3, 0x90, 0xb5, 0x85, 0x6b, 0x5f, 0x2b, 0x1c,
    0xf2, 0xce
};

std::string deobf_str(const uint8_t* src, size_t len) {
    std::string out(len, '\0');
    for (size_t i = 0; i < len; ++i) {
        uint8_t mask = uint8_t((0xA7u + (i * 31u)) & 0xFFu);
        out[i] = char(src[i] ^ mask);
    }
    return out;
}

void wipe_str(std::string& s) {
    volatile char* p = s.data();
    for (size_t i = 0; i < s.size(); ++i) p[i] = 0;
}

struct Sha256Ctx {
    uint32_t state[8];
    uint64_t bitlen = 0;
    uint8_t data[64];
    size_t datalen = 0;
};

static constexpr uint32_t kSha256K[64] = {
    0x428a2f98u,0x71374491u,0xb5c0fbcfu,0xe9b5dba5u,0x3956c25bu,0x59f111f1u,0x923f82a4u,0xab1c5ed5u,
    0xd807aa98u,0x12835b01u,0x243185beu,0x550c7dc3u,0x72be5d74u,0x80deb1feu,0x9bdc06a7u,0xc19bf174u,
    0xe49b69c1u,0xefbe4786u,0x0fc19dc6u,0x240ca1ccu,0x2de92c6fu,0x4a7484aau,0x5cb0a9dcu,0x76f988dau,
    0x983e5152u,0xa831c66du,0xb00327c8u,0xbf597fc7u,0xc6e00bf3u,0xd5a79147u,0x06ca6351u,0x14292967u,
    0x27b70a85u,0x2e1b2138u,0x4d2c6dfcu,0x53380d13u,0x650a7354u,0x766a0abbu,0x81c2c92eu,0x92722c85u,
    0xa2bfe8a1u,0xa81a664bu,0xc24b8b70u,0xc76c51a3u,0xd192e819u,0xd6990624u,0xf40e3585u,0x106aa070u,
    0x19a4c116u,0x1e376c08u,0x2748774cu,0x34b0bcb5u,0x391c0cb3u,0x4ed8aa4au,0x5b9cca4fu,0x682e6ff3u,
    0x748f82eeu,0x78a5636fu,0x84c87814u,0x8cc70208u,0x90befffau,0xa4506cebu,0xbef9a3f7u,0xc67178f2u
};

inline uint32_t rotr32(uint32_t x, uint32_t n) { return (x >> n) | (x << (32u - n)); }

void sha256_transform(Sha256Ctx& ctx, const uint8_t data[64]) {
    uint32_t m[64];
    for (int i = 0, j = 0; i < 16; ++i, j += 4) {
        m[i] = (uint32_t(data[j]) << 24) | (uint32_t(data[j + 1]) << 16) |
               (uint32_t(data[j + 2]) << 8) | uint32_t(data[j + 3]);
    }
    for (int i = 16; i < 64; ++i) {
        uint32_t s0 = rotr32(m[i - 15], 7) ^ rotr32(m[i - 15], 18) ^ (m[i - 15] >> 3);
        uint32_t s1 = rotr32(m[i - 2], 17) ^ rotr32(m[i - 2], 19) ^ (m[i - 2] >> 10);
        m[i] = m[i - 16] + s0 + m[i - 7] + s1;
    }
    uint32_t a = ctx.state[0], b = ctx.state[1], c = ctx.state[2], d = ctx.state[3];
    uint32_t e = ctx.state[4], f = ctx.state[5], g = ctx.state[6], h = ctx.state[7];

    for (int i = 0; i < 64; ++i) {
        uint32_t S1 = rotr32(e, 6) ^ rotr32(e, 11) ^ rotr32(e, 25);
        uint32_t ch = (e & f) ^ ((~e) & g);
        uint32_t temp1 = h + S1 + ch + kSha256K[i] + m[i];
        uint32_t S0 = rotr32(a, 2) ^ rotr32(a, 13) ^ rotr32(a, 22);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t temp2 = S0 + maj;
        h = g; g = f; f = e; e = d + temp1;
        d = c; c = b; b = a; a = temp1 + temp2;
    }
    ctx.state[0] += a; ctx.state[1] += b; ctx.state[2] += c; ctx.state[3] += d;
    ctx.state[4] += e; ctx.state[5] += f; ctx.state[6] += g; ctx.state[7] += h;
}

void sha256_init(Sha256Ctx& ctx) {
    ctx.datalen = 0;
    ctx.bitlen = 0;
    ctx.state[0] = 0x6a09e667u; ctx.state[1] = 0xbb67ae85u;
    ctx.state[2] = 0x3c6ef372u; ctx.state[3] = 0xa54ff53au;
    ctx.state[4] = 0x510e527fu; ctx.state[5] = 0x9b05688cu;
    ctx.state[6] = 0x1f83d9abu; ctx.state[7] = 0x5be0cd19u;
}

void sha256_update(Sha256Ctx& ctx, const uint8_t* data, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        ctx.data[ctx.datalen++] = data[i];
        if (ctx.datalen == 64) {
            sha256_transform(ctx, ctx.data);
            ctx.bitlen += 512;
            ctx.datalen = 0;
        }
    }
}

std::array<uint8_t, 32> sha256_final(Sha256Ctx& ctx) {
    size_t i = ctx.datalen;
    if (ctx.datalen < 56) {
        ctx.data[i++] = 0x80;
        while (i < 56) ctx.data[i++] = 0x00;
    } else {
        ctx.data[i++] = 0x80;
        while (i < 64) ctx.data[i++] = 0x00;
        sha256_transform(ctx, ctx.data);
        std::memset(ctx.data, 0, 56);
    }
    ctx.bitlen += ctx.datalen * 8ULL;
    for (int k = 0; k < 8; ++k) {
        ctx.data[63 - k] = uint8_t((ctx.bitlen >> (k * 8)) & 0xFFu);
    }
    sha256_transform(ctx, ctx.data);

    std::array<uint8_t, 32> hash{};
    for (int j = 0; j < 8; ++j) {
        hash[j * 4 + 0] = uint8_t((ctx.state[j] >> 24) & 0xFFu);
        hash[j * 4 + 1] = uint8_t((ctx.state[j] >> 16) & 0xFFu);
        hash[j * 4 + 2] = uint8_t((ctx.state[j] >> 8) & 0xFFu);
        hash[j * 4 + 3] = uint8_t(ctx.state[j] & 0xFFu);
    }
    return hash;
}

std::string hmac_sha256_hex(const std::string& key, const std::string& msg) {
    uint8_t k_block[64] = {0};
    if (key.size() > 64) {
        Sha256Ctx kc;
        sha256_init(kc);
        sha256_update(kc, reinterpret_cast<const uint8_t*>(key.data()), key.size());
        auto kh = sha256_final(kc);
        std::memcpy(k_block, kh.data(), 32);
    } else {
        std::memcpy(k_block, key.data(), key.size());
    }

    uint8_t o_pad[64], i_pad[64];
    for (int i = 0; i < 64; ++i) {
        o_pad[i] = k_block[i] ^ 0x5cu;
        i_pad[i] = k_block[i] ^ 0x36u;
    }

    Sha256Ctx ic;
    sha256_init(ic);
    sha256_update(ic, i_pad, 64);
    sha256_update(ic, reinterpret_cast<const uint8_t*>(msg.data()), msg.size());
    auto inner = sha256_final(ic);

    Sha256Ctx oc;
    sha256_init(oc);
    sha256_update(oc, o_pad, 64);
    sha256_update(oc, inner.data(), inner.size());
    auto digest = sha256_final(oc);

    static const char* hex = "0123456789abcdef";
    std::string out(64, '0');
    for (size_t i = 0; i < 32; ++i) {
        out[i * 2] = hex[(digest[i] >> 4) & 0xF];
        out[i * 2 + 1] = hex[digest[i] & 0xF];
    }
    return out;
}

bool ct_equal(const std::string& a, const std::string& b) {
    if (a.size() != b.size()) return false;
    uint8_t diff = 0;
    for (size_t i = 0; i < a.size(); ++i) {
        diff |= uint8_t(a[i]) ^ uint8_t(b[i]);
    }
    return diff == 0;
}

double compute_entropy(const uint8_t* data, size_t len) {
    if (len == 0) return 0.0;
    size_t counts[256] = {0};
    for (size_t i = 0; i < len; ++i) counts[data[i]]++;
    double ent = 0.0;
    double inv = 1.0 / double(len);
    for (size_t c : counts) {
        if (c > 0) {
            double p = double(c) * inv;
            ent -= p * std::log2(p);
        }
    }
    return ent;
}

/* ---------------- OpenGL ES 3.0 GPU Live Shader Wallpaper State ---------------- */
GLuint g_program = 0;
GLuint g_vao = 0;
GLint g_uTime = -1;
GLint g_uRes = -1;
GLint g_uAccent = -1;
float g_width = 1280.0f;
float g_height = 720.0f;

static const char* kVertShaderEs3 = R"(#version 300 es
precision highp float;
void main() {
    vec2 pos = vec2((gl_VertexID == 1) ? 3.0 : -1.0, (gl_VertexID == 2) ? 3.0 : -1.0);
    gl_Position = vec4(pos, 0.0, 1.0);
}
)";

static const char* kFragShaderEs3 = R"(#version 300 es
precision mediump float;
uniform float uTime;
uniform vec2 uRes;
uniform vec3 uAccent;
out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / max(uRes, vec2(1.0));
    vec2 p = (gl_FragCoord.xy * 2.0 - uRes) / max(uRes.y, 1.0);

    float t = uTime * 0.45;
    float w1 = sin(p.x * 2.4 + t) * cos(p.y * 2.1 - t * 0.8);
    float w2 = sin((p.x + p.y) * 3.1 - t * 1.2);
    float ribbon = exp(-abs(p.y + 0.28 * sin(p.x * 2.2 + t) + 0.15 * w1) * 3.8);
    float ribbon2 = exp(-abs(p.y - 0.22 * cos(p.x * 1.8 - t * 0.9) + 0.12 * w2) * 4.5);

    vec3 deepBg = mix(vec3(0.03, 0.05, 0.11), vec3(0.06, 0.11, 0.22), uv.y);
    vec3 glow1 = uAccent * (0.55 * ribbon + 0.16 * (w1 * 0.5 + 0.5));
    vec3 glow2 = vec3(0.12, 0.78, 0.65) * (0.42 * ribbon2);

    vec3 col = deepBg + glow1 + glow2;
    col *= 1.0 - 0.18 * dot(p * 0.45, p * 0.45);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
)";

GLuint compile_shader(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    if (!sh) return 0;
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_dev_xykal_hyperdex_NativeCore_ccGlInit(JNIEnv* env, jclass clazz) {
    GLuint vs = compile_shader(GL_VERTEX_SHADER, kVertShaderEs3);
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, kFragShaderEs3);
    if (!vs || !fs) return;

    g_program = glCreateProgram();
    glAttachShader(g_program, vs);
    glAttachShader(g_program, fs);
    glLinkProgram(g_program);
    glDeleteShader(vs);
    glDeleteShader(fs);

    g_uTime = glGetUniformLocation(g_program, "uTime");
    g_uRes = glGetUniformLocation(g_program, "uRes");
    g_uAccent = glGetUniformLocation(g_program, "uAccent");

    glGenVertexArrays(1, &g_vao);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_xykal_hyperdex_NativeCore_ccGlResize(JNIEnv* env, jclass clazz, jint width, jint height) {
    g_width = float(std::max(1, int(width)));
    g_height = float(std::max(1, int(height)));
    glViewport(0, 0, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_xykal_hyperdex_NativeCore_ccGlRenderFrame(
    JNIEnv* env, jclass clazz, jfloat timeSec, jint accentColor) {
    glClearColor(0.04f, 0.06f, 0.12f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (!g_program) return;

    glUseProgram(g_program);
    const float ar = float((accentColor >> 16) & 0xFF) / 255.0f;
    const float ag = float((accentColor >> 8) & 0xFF) / 255.0f;
    const float ab = float(accentColor & 0xFF) / 255.0f;

    if (g_uTime >= 0) glUniform1f(g_uTime, timeSec);
    if (g_uRes >= 0) glUniform2f(g_uRes, g_width, g_height);
    if (g_uAccent >= 0) glUniform3f(g_uAccent, ar, ag, ab);

    glBindVertexArray(g_vao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_xykal_hyperdex_NativeCore_ccAcrylicBlurBitmap(
    JNIEnv* env, jclass clazz, jobject bitmap, jint radius, jint tintArgb) {
    if (!bitmap) return JNI_FALSE;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || info.width < 2 || info.height < 2) return JNI_FALSE;

    void* pixels_ptr = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels_ptr) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels_ptr) {
        return JNI_FALSE;
    }

    const int w = int(info.width);
    const int h = int(info.height);
    const int stride = int(info.stride / 4);
    const int r = std::clamp(int(radius), 2, 40);
    const int div = 2 * r + 1;

    auto* px = static_cast<uint32_t*>(pixels_ptr);
    std::vector<uint32_t> tmp(size_t(w) * size_t(h));

    for (int pass = 0; pass < 2; ++pass) {
        for (int y = 0; y < h; ++y) {
            int rsum = 0, gsum = 0, bsum = 0;
            const uint32_t* row = px + y * stride;
            for (int i = -r; i <= r; ++i) {
                uint32_t c = row[std::clamp(i, 0, w - 1)];
                rsum += int(c & 0xFFu);
                gsum += int((c >> 8) & 0xFFu);
                bsum += int((c >> 16) & 0xFFu);
            }
            for (int x = 0; x < w; ++x) {
                tmp[y * w + x] = 0xFF000000u |
                                 uint32_t((bsum / div) << 16) |
                                 uint32_t((gsum / div) << 8) |
                                 uint32_t(rsum / div);
                uint32_t c_out = row[std::clamp(x - r, 0, w - 1)];
                uint32_t c_in = row[std::clamp(x + r + 1, 0, w - 1)];
                rsum += int(c_in & 0xFFu) - int(c_out & 0xFFu);
                gsum += int((c_in >> 8) & 0xFFu) - int((c_out >> 8) & 0xFFu);
                bsum += int((c_in >> 16) & 0xFFu) - int((c_out >> 16) & 0xFFu);
            }
        }
        for (int x = 0; x < w; ++x) {
            int rsum = 0, gsum = 0, bsum = 0;
            for (int i = -r; i <= r; ++i) {
                uint32_t c = tmp[std::clamp(i, 0, h - 1) * w + x];
                rsum += int(c & 0xFFu);
                gsum += int((c >> 8) & 0xFFu);
                bsum += int((c >> 16) & 0xFFu);
            }
            for (int y = 0; y < h; ++y) {
                px[y * stride + x] = 0xFF000000u |
                                     uint32_t((bsum / div) << 16) |
                                     uint32_t((gsum / div) << 8) |
                                     uint32_t(rsum / div);
                uint32_t c_out = tmp[std::clamp(y - r, 0, h - 1) * w + x];
                uint32_t c_in = tmp[std::clamp(y + r + 1, 0, h - 1) * w + x];
                rsum += int(c_in & 0xFFu) - int(c_out & 0xFFu);
                gsum += int((c_in >> 8) & 0xFFu) - int((c_out >> 8) & 0xFFu);
                bsum += int((c_in >> 16) & 0xFFu) - int((c_out >> 16) & 0xFFu);
            }
        }
    }

    const uint32_t ta = (uint32_t(tintArgb) >> 24) & 0xFFu;
    if (ta > 0) {
        const uint32_t tr = (uint32_t(tintArgb) >> 16) & 0xFFu;
        const uint32_t tg = (uint32_t(tintArgb) >> 8) & 0xFFu;
        const uint32_t tb = uint32_t(tintArgb) & 0xFFu;
        const uint32_t inv_a = 255u - ta;
        for (int y = 0; y < h; ++y) {
            uint32_t* row = px + y * stride;
            for (int x = 0; x < w; ++x) {
                uint32_t c = row[x];
                uint32_t r0 = (c & 0xFFu);
                uint32_t g0 = ((c >> 8) & 0xFFu);
                uint32_t b0 = ((c >> 16) & 0xFFu);
                uint32_t rn = (r0 * inv_a + tr * ta) / 255u;
                uint32_t gn = (g0 * inv_a + tg * ta) / 255u;
                uint32_t bn = (b0 * inv_a + tb * ta) / 255u;
                row[x] = 0xFF000000u | (bn << 16) | (gn << 8) | rn;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_xykal_hyperdex_NativeCore_ccAnalyzePeBinary(JNIEnv* env, jclass clazz, jstring filePath) {
    const char* cpath = env->GetStringUTFChars(filePath, nullptr);
    if (!cpath) return env->NewStringUTF("ERR: null path");

    std::ifstream ifs(cpath, std::ios::binary | std::ios::ate);
    env->ReleaseStringUTFChars(filePath, cpath);
    if (!ifs) return env->NewStringUTF("ERR: file tidak dapat dibuka");

    const auto total_size = size_t(ifs.tellg());
    ifs.seekg(0, std::ios::beg);
    const size_t read_cap = std::min<size_t>(total_size, 262144);
    std::vector<uint8_t> buf(read_cap);
    if (read_cap > 0) ifs.read(reinterpret_cast<char*>(buf.data()), std::streamsize(read_cap));

    auto u16 = [&](size_t off) -> uint16_t {
        if (off + 2 > buf.size()) return 0;
        return uint16_t(buf[off]) | (uint16_t(buf[off + 1]) << 8);
    };
    auto u32 = [&](size_t off) -> uint32_t {
        if (off + 4 > buf.size()) return 0;
        return uint32_t(buf[off]) | (uint32_t(buf[off + 1]) << 8) |
               (uint32_t(buf[off + 2]) << 16) | (uint32_t(buf[off + 3]) << 24);
    };

    const double entropy = compute_entropy(buf.data(), buf.size());
    std::ostringstream ss;
    ss << "Engine          : libhyper_gfx_cc.so (C++20 PE32/PE64 + GLES3 Engine)\n";
    ss << "File Size       : " << total_size << " bytes\n";
    ss << "Shannon Entropy : " << std::fixed << std::setprecision(3) << entropy
       << " bits/byte (" << (entropy > 7.1 ? "Packed/Compressed" : "Normal Native Image") << ")\n";

    if (buf.size() < 0x40 || buf[0] != 'M' || buf[1] != 'Z') {
        ss << "DOS Signature   : INVALID (Bukan MZ Executable)\n";
        return env->NewStringUTF(ss.str().c_str());
    }

    const uint32_t pe_off = u32(0x3C);
    ss << "DOS Header      : Valid 'MZ' (e_lfanew = 0x" << std::hex << std::uppercase << pe_off << std::dec << ")\n";
    if (size_t(pe_off) + 24 > buf.size() || buf[pe_off] != 'P' || buf[pe_off + 1] != 'E') {
        ss << "NT Header       : Legacy 16-bit / Non-PE Image\n";
        return env->NewStringUTF(ss.str().c_str());
    }

    const uint16_t machine = u16(pe_off + 4);
    const uint16_t num_sec = u16(pe_off + 6);
    const uint16_t opt_sz = u16(pe_off + 20);
    const uint16_t chars = u16(pe_off + 22);
    const size_t opt_off = size_t(pe_off) + 24;
    const uint16_t opt_magic = u16(opt_off);
    const uint32_t entry_rva = u32(opt_off + 16);
    const uint16_t subsys = u16(opt_off + 68);

    const char* arch = "Unknown";
    if (machine == 0x8664) arch = "x86_64 (AMD64 / PE32+)";
    else if (machine == 0x014C) arch = "x86 (i386 / PE32)";
    else if (machine == 0xAA64) arch = "ARM64 (Windows on ARM)";

    const char* subsys_str = "Native / Unknown";
    if (subsys == 2) subsys_str = "WINDOWS_GUI (Desktop Windowed)";
    else if (subsys == 3) subsys_str = "WINDOWS_CUI (Console / Terminal)";

    ss << "Target Arch     : " << arch << "\n";
    ss << "Optional Header : " << (opt_magic == 0x20B ? "PE32+ (64-bit)" : "PE32 (32-bit)")
       << "  |  Entry RVA: 0x" << std::hex << entry_rva << std::dec << "\n";
    ss << "Subsystem       : " << subsys_str << "  |  Flags: 0x" << std::hex << chars << std::dec << "\n";
    ss << "COFF Sections   : " << num_sec << "\n";

    const size_t sec_table = opt_off + size_t(opt_sz);
    const uint16_t max_sec = std::min<uint16_t>(num_sec, 12);
    for (uint16_t i = 0; i < max_sec; ++i) {
        size_t s = sec_table + size_t(i) * 40;
        if (s + 40 > buf.size()) break;
        char sname[9] = {0};
        std::memcpy(sname, &buf[s], 8);
        uint32_t vsize = u32(s + 8);
        uint32_t rva = u32(s + 12);
        uint32_t rsize = u32(s + 16);
        ss << "  [" << i << "] " << std::left << std::setw(8) << (sname[0] ? sname : ".sec")
           << " RVA=0x" << std::hex << rva << " VSize=" << std::dec << vsize << " Raw=" << rsize << "\n";
    }

    return env->NewStringUTF(ss.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_xykal_hyperdex_NativeCore_ccVerifyBuildQuorum(
    JNIEnv* env, jclass clazz, jstring liveCertSha256, jstring livePkg) {
    const char* cert_c = env->GetStringUTFChars(liveCertSha256, nullptr);
    const char* pkg_c = env->GetStringUTFChars(livePkg, nullptr);
    std::string cert = cert_c ? cert_c : "";
    std::string pkg = pkg_c ? pkg_c : "";
    if (cert_c) env->ReleaseStringUTFChars(liveCertSha256, cert_c);
    if (pkg_c) env->ReleaseStringUTFChars(livePkg, pkg_c);

    for (auto& ch : cert) {
        if (ch >= 'A' && ch <= 'F') ch = char(ch - 'A' + 'a');
    }

    std::string exp_pkg = deobf_str(kObfPkg, sizeof(kObfPkg));
    std::string exp_cert = deobf_str(kObfCert, sizeof(kObfCert));
    std::string exp_seal = deobf_str(kObfSeal, sizeof(kObfSeal));

    const bool pkg_ok = ct_equal(pkg, exp_pkg);
    const bool cert_ok = ct_equal(cert, exp_cert);
    const std::string computed_seal = hmac_sha256_hex(HYPERDEX_BUILD_PEPPER, cert + ":" + pkg);
    const bool seal_ok = ct_equal(computed_seal, exp_seal);

    wipe_str(exp_pkg);
    wipe_str(exp_cert);
    wipe_str(exp_seal);

    std::ostringstream out;
    if (pkg_ok && cert_ok && seal_ok) {
        out << "VERIFIED|quorum=4/4|seal=" << computed_seal.substr(0, 16);
    } else {
        out << "LOCKED|pkg_ok=" << (pkg_ok ? 1 : 0)
            << "|cert_ok=" << (cert_ok ? 1 : 0)
            << "|seal_ok=" << (seal_ok ? 1 : 0);
    }
    return env->NewStringUTF(out.str().c_str());
}
