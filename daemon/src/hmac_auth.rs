// Mirrors Kotlin DaemonAuth.kt canonical-string format byte-for-byte.
//
// canonical = METHOD\tPATH\tTS_MILLIS\tNONCE\tBODY_SHA256_HEX
// signature = hex(HMAC-SHA256(secret, canonical))
//
// Verification rules in order; first failure short-circuits:
//   1. timestamp parses + within ±30s of `now`
//   2. nonce not in NonceCache
//   3. body sha256 matches
//   4. HMAC over canonical equals header
// A failure anywhere does NOT consume the nonce.

use std::time::{Duration, SystemTime};

use hmac::{Hmac, Mac};
use sha2::{Digest, Sha256};
use subtle_constant_time::ConstantTimeEquals;

use crate::nonce_cache::NonceCache;

const ALLOWED_SKEW: Duration = Duration::from_secs(30);

pub fn canonical(method: &str, path: &str, ts_millis: i64, nonce: &str, body: &[u8]) -> String {
    let body_hex = hex::encode(Sha256::digest(body));
    format!("{}\t{}\t{}\t{}\t{}", method.to_uppercase(), path, ts_millis, nonce, body_hex)
}

pub fn sign(secret: &[u8], canon: &str) -> String {
    let mut mac = <Hmac<Sha256> as Mac>::new_from_slice(secret).expect("hmac key");
    mac.update(canon.as_bytes());
    hex::encode(mac.finalize().into_bytes())
}

#[derive(Debug)]
pub enum AuthError {
    BadTimestamp(String),
    Skew(i64),
    MissingNonce,
    Replay,
    BadSignature,
}

impl std::fmt::Display for AuthError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AuthError::BadTimestamp(s) => write!(f, "bad timestamp: {s}"),
            AuthError::Skew(s) => write!(f, "skew {s}s"),
            AuthError::MissingNonce => write!(f, "missing nonce"),
            AuthError::Replay => write!(f, "nonce replay"),
            AuthError::BadSignature => write!(f, "hmac mismatch"),
        }
    }
}

pub fn verify(
    secret: &[u8],
    method: &str,
    path: &str,
    timestamp_str: &str,
    nonce: &str,
    body: &[u8],
    signature: &str,
    nonces: &NonceCache,
    now: SystemTime,
) -> Result<(), AuthError> {
    let ts: i64 = timestamp_str
        .parse()
        .map_err(|_| AuthError::BadTimestamp(timestamp_str.to_string()))?;
    let now_ms = now
        .duration_since(SystemTime::UNIX_EPOCH)
        .map_err(|e| AuthError::BadTimestamp(e.to_string()))?
        .as_millis() as i64;
    let skew = (now_ms - ts).abs();
    if skew > ALLOWED_SKEW.as_millis() as i64 {
        return Err(AuthError::Skew(skew / 1000));
    }
    if nonce.is_empty() { return Err(AuthError::MissingNonce); }
    if nonces.seen(nonce) { return Err(AuthError::Replay); }
    if signature.is_empty() { return Err(AuthError::BadSignature); }
    let expected = sign(secret, &canonical(method, path, ts, nonce, body));
    if !expected.ct_eq(signature) {
        return Err(AuthError::BadSignature);
    }
    nonces.record(nonce);
    Ok(())
}

mod subtle_constant_time {
    /// Constant-time hex-string compare. We compare hex strings (not raw
    /// bytes) so a length mismatch can short-circuit safely (length is
    /// already public from the header value).
    pub trait ConstantTimeEquals {
        fn ct_eq(&self, other: &str) -> bool;
    }
    impl ConstantTimeEquals for String {
        fn ct_eq(&self, other: &str) -> bool {
            if self.len() != other.len() { return false; }
            let mut diff: u8 = 0;
            for (a, b) in self.bytes().zip(other.bytes()) {
                diff |= a ^ b;
            }
            diff == 0
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::nonce_cache::NonceCache;
    use std::time::{Duration, SystemTime};

    const SECRET: &[u8] = b"test-secret-32bytes-min-padding";

    fn fixed_now() -> SystemTime {
        SystemTime::UNIX_EPOCH + Duration::from_millis(1_700_000_000_000)
    }

    fn signed(method: &str, path: &str, body: &[u8], nonce: &str) -> (i64, String) {
        let ts = fixed_now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_millis() as i64;
        let canon = canonical(method, path, ts, nonce, body);
        (ts, sign(SECRET, &canon))
    }

    #[test]
    fn roundtrip() {
        let nonces = NonceCache::new(16);
        let body = br#"{"x":1}"#;
        let (ts, sig) = signed("POST", "/effector/dispatch", body, "n1");
        verify(SECRET, "POST", "/effector/dispatch", &ts.to_string(), "n1",
               body, &sig, &nonces, fixed_now()).unwrap();
    }

    #[test]
    fn replay_rejected() {
        let nonces = NonceCache::new(16);
        let (ts, sig) = signed("GET", "/health", b"", "n2");
        verify(SECRET, "GET", "/health", &ts.to_string(), "n2", b"", &sig, &nonces, fixed_now()).unwrap();
        assert!(matches!(
            verify(SECRET, "GET", "/health", &ts.to_string(), "n2", b"", &sig, &nonces, fixed_now()),
            Err(AuthError::Replay)
        ));
    }

    #[test]
    fn skew_rejected() {
        let nonces = NonceCache::new(16);
        let stale_ts = fixed_now() - Duration::from_secs(45);
        let stale_ms = stale_ts.duration_since(SystemTime::UNIX_EPOCH).unwrap().as_millis() as i64;
        let canon = canonical("GET", "/x", stale_ms, "n3", b"");
        let sig = sign(SECRET, &canon);
        let r = verify(SECRET, "GET", "/x", &stale_ms.to_string(), "n3", b"", &sig, &nonces, fixed_now());
        assert!(matches!(r, Err(AuthError::Skew(_))));
    }

    #[test]
    fn body_mutation_rejected() {
        let nonces = NonceCache::new(16);
        let (ts, sig) = signed("POST", "/x", b"original", "n4");
        let r = verify(SECRET, "POST", "/x", &ts.to_string(), "n4", b"tampered", &sig, &nonces, fixed_now());
        assert!(matches!(r, Err(AuthError::BadSignature)));
    }

    #[test]
    fn failure_does_not_burn_nonce() {
        let nonces = NonceCache::new(16);
        let ts = fixed_now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_millis() as i64;
        // First try: bad sig.
        let _ = verify(SECRET, "GET", "/x", &ts.to_string(), "n5", b"", "bad-sig", &nonces, fixed_now());
        // Legitimate retry with same nonce passes.
        let canon = canonical("GET", "/x", ts, "n5", b"");
        let sig = sign(SECRET, &canon);
        verify(SECRET, "GET", "/x", &ts.to_string(), "n5", b"", &sig, &nonces, fixed_now()).unwrap();
    }
}
