use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use std::fs::File;
use std::io::Read;
use std::time::Instant;

// ============================================================================
// librust_hypercore.so — Pure Safe Rust Native Security & Compute Engine
// 1. FIPS 180-4 SHA-256 & HMAC-SHA256 Constant-Time APK Signature Attestation
// 2. Streaming File & Text SHA-256 Checksum Engine
// 3. Recursive-Descent Scientific Math Expression Parser & Evaluator
// 4. Cryptographic Throughput Benchmark Engine
// ============================================================================

const OFFICIAL_CERT_SHA256: &str =
    "f4436a59c0f9aeeead537a697ef59e62aae2e390c734864fd5270f7859f4f2e6";
const OFFICIAL_EXPECTED_SEAL: &str =
    "29a29da3aec757a9b9dd2174a800be4df676f273b65a72298bc486be64069358";
const OFFICIAL_PACKAGE_ID: &str = "dev.xykal.hyperdex";

const K256: [u32; 64] = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
];

struct Sha256 {
    state: [u32; 8],
    buf: [u8; 64],
    buf_len: usize,
    total_len: u64,
}

impl Sha256 {
    fn new() -> Self {
        Self {
            state: [
                0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
                0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
            ],
            buf: [0u8; 64],
            buf_len: 0,
            total_len: 0,
        }
    }

    fn compress(state: &mut [u32; 8], block: &[u8; 64]) {
        let mut w = [0u32; 64];
        for i in 0..16 {
            let j = i * 4;
            w[i] = u32::from_be_bytes([block[j], block[j + 1], block[j + 2], block[j + 3]]);
        }
        for i in 16..64 {
            let s0 = w[i - 15].rotate_right(7) ^ w[i - 15].rotate_right(18) ^ (w[i - 15] >> 3);
            let s1 = w[i - 2].rotate_right(17) ^ w[i - 2].rotate_right(19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16]
                .wrapping_add(s0)
                .wrapping_add(w[i - 7])
                .wrapping_add(s1);
        }

        let [mut a, mut b, mut c, mut d, mut e, mut f, mut g, mut h] = *state;
        for i in 0..64 {
            let s1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let ch = (e & f) ^ ((!e) & g);
            let temp1 = h
                .wrapping_add(s1)
                .wrapping_add(ch)
                .wrapping_add(K256[i])
                .wrapping_add(w[i]);
            let s0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let maj = (a & b) ^ (a & c) ^ (b & c);
            let temp2 = s0.wrapping_add(maj);

            h = g;
            g = f;
            f = e;
            e = d.wrapping_add(temp1);
            d = c;
            c = b;
            b = a;
            a = temp1.wrapping_add(temp2);
        }

        state[0] = state[0].wrapping_add(a);
        state[1] = state[1].wrapping_add(b);
        state[2] = state[2].wrapping_add(c);
        state[3] = state[3].wrapping_add(d);
        state[4] = state[4].wrapping_add(e);
        state[5] = state[5].wrapping_add(f);
        state[6] = state[6].wrapping_add(g);
        state[7] = state[7].wrapping_add(h);
    }

    fn update(&mut self, mut data: &[u8]) {
        self.total_len = self.total_len.wrapping_add(data.len() as u64);
        if self.buf_len > 0 {
            let take = (64 - self.buf_len).min(data.len());
            self.buf[self.buf_len..self.buf_len + take].copy_from_slice(&data[..take]);
            self.buf_len += take;
            data = &data[take..];
            if self.buf_len == 64 {
                Self::compress(&mut self.state, &self.buf);
                self.buf_len = 0;
            }
        }
        while data.len() >= 64 {
            let mut block = [0u8; 64];
            block.copy_from_slice(&data[..64]);
            Self::compress(&mut self.state, &block);
            data = &data[64..];
        }
        if !data.is_empty() {
            self.buf[..data.len()].copy_from_slice(data);
            self.buf_len = data.len();
        }
    }

    fn finalize(mut self) -> [u8; 32] {
        let bit_len = self.total_len.wrapping_mul(8);
        self.buf[self.buf_len] = 0x80;
        self.buf_len += 1;
        if self.buf_len > 56 {
            for b in &mut self.buf[self.buf_len..64] {
                *b = 0;
            }
            Self::compress(&mut self.state, &self.buf);
            self.buf_len = 0;
        }
        for b in &mut self.buf[self.buf_len..56] {
            *b = 0;
        }
        self.buf[56..64].copy_from_slice(&bit_len.to_be_bytes());
        Self::compress(&mut self.state, &self.buf);

        let mut out = [0u8; 32];
        for (i, word) in self.state.iter().enumerate() {
            out[i * 4..i * 4 + 4].copy_from_slice(&word.to_be_bytes());
        }
        out
    }
}

fn to_hex(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut s = String::with_capacity(bytes.len() * 2);
    for &b in bytes {
        s.push(HEX[(b >> 4) as usize] as char);
        s.push(HEX[(b & 0x0f) as usize] as char);
    }
    s
}

fn sha256_bytes(data: &[u8]) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(data);
    h.finalize()
}

fn hmac_sha256_hex(key: &[u8], msg: &[u8]) -> String {
    let mut k_block = [0u8; 64];
    if key.len() > 64 {
        let hashed = sha256_bytes(key);
        k_block[..32].copy_from_slice(&hashed);
    } else {
        k_block[..key.len()].copy_from_slice(key);
    }

    let mut i_pad = [0u8; 64];
    let mut o_pad = [0u8; 64];
    for i in 0..64 {
        i_pad[i] = k_block[i] ^ 0x36;
        o_pad[i] = k_block[i] ^ 0x5c;
    }

    let mut inner = Sha256::new();
    inner.update(&i_pad);
    inner.update(msg);
    let inner_digest = inner.finalize();

    let mut outer = Sha256::new();
    outer.update(&o_pad);
    outer.update(&inner_digest);
    to_hex(&outer.finalize())
}

fn ct_eq(a: &str, b: &str) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (x, y) in a.bytes().zip(b.bytes()) {
        diff |= x ^ y;
    }
    diff == 0
}

fn ret_jstring(env: &JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|j| j.into_raw())
        .unwrap_or(core::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_dev_xykal_hyperdex_NativeCore_rustVerifySignature<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    cert_bytes: JByteArray<'local>,
    pkg_name: JString<'local>,
) -> jstring {
    let raw_cert = env.convert_byte_array(&cert_bytes).unwrap_or_default();
    let pkg: String = env.get_string(&pkg_name).map(Into::into).unwrap_or_default();

    let live_sha256 = to_hex(&sha256_bytes(&raw_cert));
    let pepper = option_env!("HYPERDEX_BUILD_PEPPER").unwrap_or("UNOFFICIAL_LOCAL_OR_FORK_BUILD");
    let msg = format!("{}:{}", live_sha256, pkg);
    let live_seal = hmac_sha256_hex(pepper.as_bytes(), msg.as_bytes());

    let pkg_ok = ct_eq(&pkg, OFFICIAL_PACKAGE_ID);
    let cert_ok = ct_eq(&live_sha256, OFFICIAL_CERT_SHA256);
    let seal_ok = ct_eq(&live_seal, OFFICIAL_EXPECTED_SEAL);

    let status = if pkg_ok && cert_ok && seal_ok {
        format!(
            "VERIFIED|cert={}|pkg={}|seal={}",
            live_sha256,
            pkg,
            &live_seal[..16]
        )
    } else {
        let reason = if !pkg_ok {
            "PACKAGE_ID_CLONED"
        } else if !cert_ok {
            "UNTRUSTED_APK_SIGNATURE"
        } else {
            "UNOFFICIAL_FORK_BUILD_SEAL"
        };
        format!(
            "LOCKED|reason={}|cert={}|pkg={}",
            reason, live_sha256, pkg
        )
    };

    ret_jstring(&env, &status)
}

#[no_mangle]
pub extern "system" fn Java_dev_xykal_hyperdex_NativeCore_rustSha256File<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    file_path: JString<'local>,
) -> jstring {
    let path: String = env.get_string(&file_path).map(Into::into).unwrap_or_default();
    let mut file = match File::open(&path) {
        Ok(f) => f,
        Err(e) => return ret_jstring(&env, &format!("ERR: {}", e)),
    };

    let mut hasher = Sha256::new();
    let mut buf = [0u8; 16384];
    loop {
        match file.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => hasher.update(&buf[..n]),
            Err(e) => return ret_jstring(&env, &format!("ERR: {}", e)),
        }
    }
    ret_jstring(&env, &to_hex(&hasher.finalize()))
}

#[no_mangle]
pub extern "system" fn Java_dev_xykal_hyperdex_NativeCore_rustSha256Text<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    text: JString<'local>,
) -> jstring {
    let input: String = env.get_string(&text).map(Into::into).unwrap_or_default();
    ret_jstring(&env, &to_hex(&sha256_bytes(input.as_bytes())))
}

#[no_mangle]
pub extern "system" fn Java_dev_xykal_hyperdex_NativeCore_rustBenchSha256<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    rounds: jint,
) -> jstring {
    let count = rounds.clamp(1000, 500_000) as usize;
    let start = Instant::now();
    let mut digest = [0x5Au8; 32];
    for i in 0..count {
        digest[0] ^= (i & 0xFF) as u8;
        digest = sha256_bytes(&digest);
    }
    let elapsed_ms = start.elapsed().as_secs_f64() * 1000.0;
    let kh_per_sec = (count as f64) / elapsed_ms.max(0.05);
    ret_jstring(
        &env,
        &format!(
            "Rust SHA-256: {:.1} kH/s ({} rounds in {:.2} ms, tail={})",
            kh_per_sec,
            count,
            elapsed_ms,
            &to_hex(&digest[..4])
        ),
    )
}

// ---------------- Scientific Math Parser in Safe Rust ----------------
struct MathParser<'a> {
    src: &'a [u8],
    pos: usize,
}

impl<'a> MathParser<'a> {
    fn new(s: &'a str) -> Self {
        Self {
            src: s.as_bytes(),
            pos: 0,
        }
    }

    fn skip_ws(&mut self) {
        while self.pos < self.src.len() && self.src[self.pos].is_ascii_whitespace() {
            self.pos += 1;
        }
    }

    fn parse_expr(&mut self) -> Result<f64, &'static str> {
        let mut v = self.parse_term()?;
        loop {
            self.skip_ws();
            if self.pos >= self.src.len() {
                break;
            }
            match self.src[self.pos] {
                b'+' => {
                    self.pos += 1;
                    v += self.parse_term()?;
                }
                b'-' => {
                    self.pos += 1;
                    v -= self.parse_term()?;
                }
                _ => break,
            }
        }
        Ok(v)
    }

    fn parse_term(&mut self) -> Result<f64, &'static str> {
        let mut v = self.parse_power()?;
        loop {
            self.skip_ws();
            if self.pos >= self.src.len() {
                break;
            }
            match self.src[self.pos] {
                b'*' => {
                    self.pos += 1;
                    v *= self.parse_power()?;
                }
                b'/' => {
                    self.pos += 1;
                    let rhs = self.parse_power()?;
                    if rhs.abs() < 1e-15 {
                        return Err("Division by zero");
                    }
                    v /= rhs;
                }
                b'%' => {
                    self.pos += 1;
                    let rhs = self.parse_power()?;
                    if rhs.abs() < 1e-15 {
                        return Err("Modulo by zero");
                    }
                    v %= rhs;
                }
                _ => break,
            }
        }
        Ok(v)
    }

    fn parse_power(&mut self) -> Result<f64, &'static str> {
        let base = self.parse_unary()?;
        self.skip_ws();
        if self.pos < self.src.len() && self.src[self.pos] == b'^' {
            self.pos += 1;
            let exp = self.parse_power()?;
            Ok(base.powf(exp))
        } else {
            Ok(base)
        }
    }

    fn parse_unary(&mut self) -> Result<f64, &'static str> {
        self.skip_ws();
        if self.pos < self.src.len() {
            if self.src[self.pos] == b'-' {
                self.pos += 1;
                return Ok(-self.parse_unary()?);
            }
            if self.src[self.pos] == b'+' {
                self.pos += 1;
                return self.parse_unary();
            }
        }
        self.parse_atom()
    }

    fn parse_atom(&mut self) -> Result<f64, &'static str> {
        self.skip_ws();
        if self.pos >= self.src.len() {
            return Err("Unexpected end");
        }
        if self.src[self.pos] == b'(' {
            self.pos += 1;
            let v = self.parse_expr()?;
            self.skip_ws();
            if self.pos >= self.src.len() || self.src[self.pos] != b')' {
                return Err("Missing ')'");
            }
            self.pos += 1;
            return Ok(v);
        }

        if self.src[self.pos].is_ascii_alphabetic() {
            let start = self.pos;
            while self.pos < self.src.len()
                && (self.src[self.pos].is_ascii_alphanumeric() || self.src[self.pos] == b'_')
            {
                self.pos += 1;
            }
            let ident = std::str::from_utf8(&self.src[start..self.pos])
                .unwrap_or("")
                .to_ascii_lowercase();
            if ident == "pi" {
                return Ok(std::f64::consts::PI);
            }
            if ident == "e" {
                return Ok(std::f64::consts::E);
            }
            self.skip_ws();
            if self.pos >= self.src.len() || self.src[self.pos] != b'(' {
                return Err("Unknown identifier");
            }
            self.pos += 1;
            let arg = self.parse_expr()?;
            self.skip_ws();
            if self.pos >= self.src.len() || self.src[self.pos] != b')' {
                return Err("Missing ')'");
            }
            self.pos += 1;
            return match ident.as_str() {
                "sqrt" => Ok(arg.sqrt()),
                "sin" => Ok(arg.sin()),
                "cos" => Ok(arg.cos()),
                "tan" => Ok(arg.tan()),
                "abs" => Ok(arg.abs()),
                "ln" => Ok(arg.ln()),
                "log" | "log10" => Ok(arg.log10()),
                "floor" => Ok(arg.floor()),
                "ceil" => Ok(arg.ceil()),
                _ => Err("Unknown function"),
            };
        }

        let start = self.pos;
        while self.pos < self.src.len()
            && (self.src[self.pos].is_ascii_digit() || self.src[self.pos] == b'.')
        {
            self.pos += 1;
        }
        if start == self.pos {
            return Err("Invalid token");
        }
        let num_str = std::str::from_utf8(&self.src[start..self.pos]).map_err(|_| "UTF-8 err")?;
        num_str.parse::<f64>().map_err(|_| "Invalid number")
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_xykal_hyperdex_NativeCore_rustEvalMath<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    expr: JString<'local>,
) -> jstring {
    let raw: String = env.get_string(&expr).map(Into::into).unwrap_or_default();
    let cleaned = raw.replace('×', "*").replace('÷', "/");
    let mut parser = MathParser::new(&cleaned);
    let out = match parser.parse_expr() {
        Ok(v) => {
            parser.skip_ws();
            if parser.pos != parser.src.len() {
                "ERR: Syntax error".to_string()
            } else if !v.is_finite() {
                "ERR: NaN / Infinity".to_string()
            } else if (v - v.round()).abs() < 1e-11 && v.abs() < 1e15 {
                format!("{}", v.round() as i64)
            } else {
                format!("{:.8}", v)
                    .trim_end_matches('0')
                    .trim_end_matches('.')
                    .to_string()
            }
        }
        Err(msg) => format!("ERR: {}", msg),
    };
    ret_jstring(&env, &out)
}
