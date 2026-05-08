#!/usr/bin/env python3
"""
PC-side relay server for jarvis-kotlin's RelayLlm. Wraps the local
`claude` CLI as an HTTP service so a Tailscale-reachable VPS bot can
consume the user's Claude Max subscription via this PC's residential
IP. The OAuth token never leaves the PC; the VPS just sees a JSON
request/response over the private tailnet.

Endpoints:
  POST /complete  Bearer-auth'd. Body: {"messages": [...], "max_tokens": N}.
                  Spawns `claude --print` with the serialized prompt on
                  stdin. Returns {"reply": str, "model": str}.
  GET  /healthz   Bearer-auth'd. Round-trips a 1-token "ping" through
                  claude --print under a 15s cap. 200 if OK, 503 if not.
                  Run from a daily cron, NOT per-request — per-request
                  /healthz wastes Max quota on liveness probes.

Env (REQUIRED unless marked optional):
  JARVIS_RELAY_TOKEN      bearer token shared with VPS
  JARVIS_RELAY_HOST       optional, bind address, default 0.0.0.0
                          (Tailscale ACLs gate real exposure; binding
                          0.0.0.0 avoids hardcoding the tailnet IP that
                          Tailscale chooses)
  JARVIS_RELAY_PORT       optional, default 9999
  JARVIS_CLAUDE_BIN       optional, claude binary path or PATH name,
                          default 'claude'
  JARVIS_CLAUDE_MODEL     optional, model arg passed to --model
  JARVIS_RELAY_TIMEOUT_S  optional, per-/complete timeout, default 120

Council 2026-05-08 mitigations applied:
  - /healthz round-trips a real claude call (DE KEY CONCERN: TCP-
    reachable != CLI-healthy).
  - 502 on subprocess non-zero exit so the VPS RelayLlm sees a clear
    error and FallbackLlm engages (RA mitigation d).
  - Verbatim stderr echoed in 502 body so a future "session expired"
    pattern can be detected by the VPS-side fail-fast (RA mitigation
    c, deferred to RelayLlm side).

Usage:
  JARVIS_RELAY_TOKEN=$(openssl rand -hex 32) python tools/pc-relay-server.py

Stop with Ctrl-C.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("JARVIS_RELAY_TOKEN")
HOST = os.environ.get("JARVIS_RELAY_HOST", "0.0.0.0")
PORT = int(os.environ.get("JARVIS_RELAY_PORT", "9999"))
CLAUDE_BIN = os.environ.get("JARVIS_CLAUDE_BIN", "claude")
CLAUDE_MODEL = os.environ.get("JARVIS_CLAUDE_MODEL")
TIMEOUT_S = int(os.environ.get("JARVIS_RELAY_TIMEOUT_S", "120"))

if not TOKEN:
    print("FATAL: JARVIS_RELAY_TOKEN not set", file=sys.stderr)
    sys.exit(1)

# Resolve claude binary up front so we fail fast at startup, not on the
# first chat turn.
if not shutil.which(CLAUDE_BIN) and not os.path.exists(CLAUDE_BIN):
    print(f"FATAL: claude binary '{CLAUDE_BIN}' not found on PATH", file=sys.stderr)
    sys.exit(1)


def serialize_messages(messages):
    """Mirror ClaudeMaxLlm.serialize() so multi-turn chats produce the
    same role-tagged prompt the rest of the bot already trains on."""
    if len(messages) == 1 and messages[0].get("role") == "user":
        return messages[0].get("content", "")
    parts = []
    for m in messages:
        role = m.get("role", "user")
        tag = {
            "system": "[INSTRUCTIONS]",
            "user": "[USER]",
            "assistant": "[ASSISTANT_PRIOR_TURN]",
        }.get(role, f"[{role.upper()}]")
        parts.append(f"{tag}\n{m.get('content', '')}\n")
    parts.append("[ASSISTANT_REPLY]\n")
    return "\n".join(parts)


def call_claude(prompt, timeout):
    cmd = [CLAUDE_BIN, "--print", "--output-format", "text"]
    if CLAUDE_MODEL:
        cmd += ["--model", CLAUDE_MODEL]
    proc = subprocess.run(
        cmd,
        input=prompt,
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=timeout,
    )
    if proc.returncode != 0:
        raise RuntimeError(
            f"claude CLI exit {proc.returncode}: "
            f"{(proc.stderr or proc.stdout)[:400].strip()}"
        )
    return proc.stdout.strip()


class Handler(BaseHTTPRequestHandler):
    server_version = "JarvisRelay/1.0"

    def _auth_ok(self):
        header = self.headers.get("Authorization", "")
        return header == f"Bearer {TOKEN}"

    def _send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_text(self, status, text):
        body = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path != "/complete":
            self._send_text(404, "not found")
            return
        if not self._auth_ok():
            self._send_text(401, "unauthorized")
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._send_text(400, "bad content-length")
            return
        if length <= 0 or length > 4_000_000:
            self._send_text(400, "missing or oversized body")
            return
        try:
            req = json.loads(self.rfile.read(length))
        except json.JSONDecodeError as e:
            self._send_text(400, f"bad json: {e}")
            return

        messages = req.get("messages")
        if not isinstance(messages, list) or not messages:
            self._send_text(400, "messages must be non-empty list")
            return

        prompt = serialize_messages(messages)
        try:
            reply = call_claude(prompt, TIMEOUT_S)
        except subprocess.TimeoutExpired:
            self._send_text(504, f"claude timed out after {TIMEOUT_S}s")
            return
        except RuntimeError as e:
            self._send_text(502, str(e))
            return
        except Exception as e:
            self._send_text(500, f"internal error: {e}")
            return

        model_tag = f"claude-max-relay/{CLAUDE_MODEL}" if CLAUDE_MODEL else "claude-max-relay"
        self._send_json(200, {"reply": reply, "model": model_tag})

    def do_GET(self):
        if self.path != "/healthz":
            self._send_text(404, "not found")
            return
        if not self._auth_ok():
            self._send_text(401, "unauthorized")
            return
        try:
            reply = call_claude("ping", timeout=15)
        except Exception as e:
            self._send_text(503, f"claude unhealthy: {e}")
            return
        self._send_json(200, {"status": "ok", "reply_excerpt": reply[:80]})

    def log_message(self, fmt, *args):
        sys.stderr.write(f"[{self.log_date_time_string()}] {fmt % args}\n")


def main():
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"jarvis pc-relay listening on http://{HOST}:{PORT}", flush=True)
    print(f"  claude binary: {CLAUDE_BIN}", flush=True)
    print(f"  model:         {CLAUDE_MODEL or '(default)'}", flush=True)
    print(f"  timeout:       {TIMEOUT_S}s", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("shutting down", flush=True)
        server.shutdown()


if __name__ == "__main__":
    main()
