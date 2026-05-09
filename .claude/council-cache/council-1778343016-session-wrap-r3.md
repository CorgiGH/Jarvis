# Council R3 — session wrap final verification (2026-05-09)

## Synthesis: TERMINATE LOOP

All three R2 must-fixes landed clean (commit `c888bcf`). Bearer fallback is gone, raw-bytes path uses `call.receive<ByteArray>()` (bypasses ContentNegotiation since no `ByteArray` serializer is registered), nonce min-entropy spec'd in class doc. HMAC roundtrip test exists and exercises the SAME `DaemonAuth.canonical/sign` pair the server's `verify()` uses. Wrap memory has TL;DR (one line, semicolon-glued but parses as a sentence) and Open Work is tagged with all 6 categories. Tests = 540 / 0 fails. No new must-fix surfaces.

**Verdict: APPROVE. Loop terminates.**

Single nit (NOT a must-fix, do whenever): `TutorRoutes.kt:426` comment says "via receiveChannel" but code on :429 is `call.receive<ByteArray>()`. Comment is doc-stale; behavior is identical. Fix on next pass.

---

## Per-perspective verdicts

### Devil's Advocate — APPROVE
- `constantTimeEquals` is private inside `DaemonAuth.kt` and only called from `DaemonAuth.verify()` itself. R2 deletion of the bearer-fallback branch did NOT orphan or break any other call site (grep confirms zero other usages).
- HmacHeaders dataclass `equals/hashCode` not overridden but ByteArray field — fine, only used as a value carrier across the route→preflight boundary, never compared.
- No regression: the 5-test gateway suite (incl. the new HMAC roundtrip) all pass; total suite holds at 540/0.

### Domain Expert — APPROVE
- `call.receive<ByteArray>()` IS the canonical Ktor 3.x raw-bytes idiom. Ktor's `ByteArrayContentConverter` is registered out-of-the-box; ContentNegotiation only intercepts when a Kotlinx-Serialization-registered type is requested. Requesting `ByteArray` short-circuits to the raw transport and returns the on-the-wire bytes uninterpreted.
- Equivalent to `receiveChannel().toByteArray()` for this purpose; `receive<ByteArray>()` is preferred (idiomatic, less ceremony).
- HMAC body-binding is now bit-exact. Prior `receiveText()` issue (UTF-8 replacement-char roundtrip silently corrupting signature inputs) is resolved.

### Pragmatist — APPROVE
- TL;DR + first H2 ("Session totals") together convey: 38 commits, 540/56/16 tests across 3 toolchains, 6 tags, 4 council reports, single Stage closed-loop in prod (B). A returning operator picks up the picture in <90s.
- Stage table's 3-column legend (Code / Wired / Live data) makes "what's left to do" one glance away. Open Work block tags are skim-friendly. Pickup-ready.

### Risk Analyst — APPROVE
- Residual gateway attack surface with bearer fallback removed: an attacker now needs (a) the secret, (b) a valid HMAC over the canonical including ts + nonce + body-sha256, (c) ts within ±30s of server clock, (d) a non-replayed nonce in the 4096-entry LRU. That's the full HMAC contract — no downgrade path exists.
- **Same-function audit confirmed:** `GatewayInbound.preflight()` calls `DaemonAuth.verify(...)` — the SAME function used to verify daemon-bound effector requests. One bearer-removal patch closed both surfaces' downgrade vector. No need to re-audit daemon path separately.
- Independent surface (`/api/v1/effector/dispatch` → daemon HMAC) was already HMAC-only and untouched by this fix; no new exposure introduced.

### First Principles — APPROVE
- TL;DR text: "Tutor Layer B + task-context V0/V1 + 6 stages (...) shipped across ~38 commits + 6 tutor tags + 540 backend tests; live tool_use chat works via /tutor/ (click PA preset → chat → tools fire), most other surfaces dark pending one of {producer setup, OAuth, SKILL.md authoring}."
- Single line in the source (line 9). Yes it has a semicolon, but it's grammatically one sentence ending in one period. Meets the "1-line tl;dr" bar. Not padded.

---

## Forbidden-must-fix dismissals (not raised, noted for transparency)

- Stage F dark / no Telegram daemon: explicitly `[user-action]`. Out of scope.
- Council single-author this round: explicitly noted as `[ops]` debt in wrap. Out of scope.
- PWA manifest missing: explicitly `[ux]`. Out of scope.
- GwsEffector lacking HMAC: local subprocess, no network surface. Different threat model. Scope creep.

---

## Termination check

- [x] No NEW must-fix introduced since R2.
- [x] All R2 must-fixes verified shipped (5/5: bearer drop, raw-bytes, nonce spec, TL;DR, tags).
- [x] Tests green (540/0).
- [x] Commit landed (`c888bcf`).

**Loop terminates here.**
