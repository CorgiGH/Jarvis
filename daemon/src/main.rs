// Jarvis Tutor Layer B1 — local effector daemon.
//
// Headless HTTP server bound to 127.0.0.1 only. Mirrors the canonical
// HMAC scheme defined server-side in DaemonAuth.kt so requests signed
// by the Kotlin server validate here byte-for-byte.
//
// Council R3 fixes wired in:
//  - HMAC scheme: METHOD\tPATH\tTS\tNONCE\tBODY_SHA256_HEX, ±30s skew
//  - Bind 127.0.0.1; Host header check rejects DNS-rebinding
//  - Path blocklist mirrors Kotlin EffectorValidator
//  - Kill switch: ~/.jarvis/KILL file presence aborts every dispatch
//  - Single mutating endpoint POST /effector/dispatch
//
// Out of scope for B1 v0:
//  - VPS-unreachable kill switch (defer to systemd unit + healthcheck)
//  - Telegram + Ctrl+Alt+J kill (require GUI hook; defer)
//  - Rotation endpoint (defer to UI commit)

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use axum::{
    body::Bytes,
    extract::State,
    http::{HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use clap::Parser;
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::sync::Mutex;
use tokio::net::TcpListener;

mod effector;
mod hmac_auth;
mod kill_switch;
mod nonce_cache;
mod path_blocklist;

#[derive(Parser, Debug)]
#[command(name = "jarvis-daemon", version, about = "Tutor Layer B1 effector daemon")]
struct Args {
    /// Port to bind on 127.0.0.1.
    #[arg(long, default_value_t = 7331, env = "JARVIS_DAEMON_PORT")]
    port: u16,

    /// Optional explicit secret for the HMAC key. If unset, the daemon
    /// reads the secret from the OS keychain under the key `jarvis-daemon`.
    /// CLI override is for testing + provisioning.
    #[arg(long, env = "JARVIS_DAEMON_SECRET")]
    secret: Option<String>,

    /// Optional rate limit (requests / minute). Default: 100.
    #[arg(long, default_value_t = 100, env = "JARVIS_DAEMON_RATE_PER_MIN")]
    rate_per_min: usize,
}

#[derive(Clone)]
struct AppState {
    secret: Arc<Vec<u8>>,
    nonces: Arc<nonce_cache::NonceCache>,
    rate: Arc<Mutex<VecDeque<i64>>>,
    rate_per_min: usize,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env()
            .add_directive("jarvis_daemon=info".parse().unwrap()))
        .init();

    let args = Args::parse();

    let secret = match &args.secret {
        Some(s) => s.as_bytes().to_vec(),
        None => keyring::Entry::new("jarvis-daemon", "default")?
            .get_password()
            .map_err(|e| {
                tracing::error!("keychain read failed: {e:?}; pass --secret or run jarvis-daemon provision-key");
                e
            })?
            .into_bytes(),
    };

    let state = AppState {
        secret: Arc::new(secret),
        nonces: Arc::new(nonce_cache::NonceCache::new(1024)),
        rate: Arc::new(Mutex::new(VecDeque::new())),
        rate_per_min: args.rate_per_min,
    };

    let app = Router::new()
        .route("/health", get(health))
        .route("/effector/dispatch", post(dispatch))
        .with_state(state);

    let addr = SocketAddr::from(([127, 0, 0, 1], args.port));
    tracing::info!("jarvis-daemon listening on http://{}", addr);
    let listener = TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

async fn health() -> &'static str {
    "ok"
}

#[derive(Debug, Deserialize)]
struct DispatchRequest {
    #[serde(rename = "effectorId")]
    effector_id: String,
    #[serde(rename = "targetUri")]
    target_uri: String,
    #[serde(rename = "newText")]
    new_text: String,
}

#[derive(Debug, Serialize)]
struct DispatchResponse {
    ok: bool,
    outcome: String,
    detail: Option<String>,
}

async fn dispatch(
    State(state): State<AppState>,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    // 1. Kill switch: ~/.jarvis/KILL aborts every dispatch.
    if kill_switch::active() {
        return (StatusCode::SERVICE_UNAVAILABLE, "kill switch active").into_response();
    }

    // 2. Host header check — defends against DNS-rebinding even though
    //    we already bind 127.0.0.1. A browser that resolves
    //    foo.attacker.com → 127.0.0.1 still sends Host: foo.attacker.com.
    let host = headers
        .get("host")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    let allowed = host.starts_with("127.0.0.1") || host.starts_with("localhost");
    if !allowed {
        return (StatusCode::FORBIDDEN, format!("host not allowed: {host}")).into_response();
    }

    // 3. HMAC verification.
    let ts = headers.get("x-jarvis-timestamp").and_then(|v| v.to_str().ok()).unwrap_or("");
    let nonce = headers.get("x-jarvis-nonce").and_then(|v| v.to_str().ok()).unwrap_or("");
    let sig = headers.get("x-jarvis-hmac").and_then(|v| v.to_str().ok()).unwrap_or("");
    match hmac_auth::verify(
        &state.secret,
        "POST",
        "/effector/dispatch",
        ts,
        nonce,
        body.as_ref(),
        sig,
        &state.nonces,
        SystemTime::now(),
    ) {
        Ok(()) => {}
        Err(e) => return (StatusCode::UNAUTHORIZED, format!("hmac: {e}")).into_response(),
    }

    // 4. Rate limit.
    if !rate_check(&state) {
        return (StatusCode::TOO_MANY_REQUESTS, "rate limited").into_response();
    }

    // 5. Parse + validate.
    let req: DispatchRequest = match serde_json::from_slice(body.as_ref()) {
        Ok(r) => r,
        Err(e) => return (StatusCode::BAD_REQUEST, format!("bad json: {e}")).into_response(),
    };
    if path_blocklist::denied(&req.target_uri) {
        return (StatusCode::FORBIDDEN, "path on blocklist").into_response();
    }

    // 6. Effector v1 — keystroke injection. v0 (clipboard write) is
    //    server-side only; daemon's reason to exist is v1.
    match effector::inject_keystrokes(&req.new_text) {
        Ok(()) => Json(DispatchResponse {
            ok: true,
            outcome: "SUCCESS".into(),
            detail: Some(format!("dispatched effector {}", req.effector_id)),
        }).into_response(),
        Err(e) => Json(DispatchResponse {
            ok: false,
            outcome: "FAILED".into(),
            detail: Some(format!("inject failed: {e}")),
        }).into_response(),
    }
}

fn rate_check(state: &AppState) -> bool {
    let mut q = state.rate.lock().unwrap();
    let now_secs = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64;
    while let Some(&front) = q.front() {
        if now_secs - front > 60 { q.pop_front(); } else { break; }
    }
    if q.len() >= state.rate_per_min { return false; }
    q.push_back(now_secs);
    true
}
