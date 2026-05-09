# Council 1778342180 — session-wrap R2 verification (2026-05-09)

**Problem:** R1 council found 3 must-fix concerns (wrap memory file factual errors; gateway HMAC asymmetry; stage table needed 3-column status). R1 fixes have landed in commit `3a1fdbe`. R2 verifies (a) each must-fix actually closed, (b) no NEW gaps introduced, (c) anything R1 missed.

**Provider status at R2 run time:**
- 🟦 Gemini (`gemini-2.5-flash`): connected, 5/5 perspectives obtained.
  - `gemini-3.1-pro-preview` (default) was rate-limit-exhausted; downshifted to `gemini-2.5-flash` to honor `feedback_no_paid_apis.md` (free tier only).
- 🔳 OpenAI / 🟥 Grok / 🟩 Perplexity: API key not set (per `feedback_no_paid_apis.md` — no paid plan).

**This R2 council is multi-author Gemini (5 distinct stance prompts)** — stronger than R1's single-author-Claude review.

**Substrate verification consumed for R2** (first-hand, before agent fan-out):
- `gradle :test --rerun-tasks`: BUILD SUCCESSFUL, **539 tests / 0 fail / 0 skip / 0 errors** (1m24s).
- `git tag -l 'tutor/*'`: confirmed **6 tags** (layer-a-acceptance, layer-b0-clipboard, layer-b-acceptance, task-context-v0, task-context-v1, stage-f-gateway-cron).
- `git log --oneline -10`: confirmed R1 fix commit landed (`3a1fdbe`).
- `git diff HEAD~1 HEAD --stat`: confirmed scope (5 files: GatewayInbound +52, TutorRoutes +19, .gitignore +3, council cache +2 reports).
- Read GatewayInbound.kt — DaemonAuth.verify call wired correctly with HmacHeaders param + process-wide nonces.
- Read TutorRoutes.kt — rawBody captured before deserialization; HmacHeaders constructed only when X-Jarvis-Hmac present.
- Read DaemonAuth.kt — canonical string `METHOD\tPATH\tTIMESTAMP_MILLIS\tNONCE\tBODY_SHA256_HEX`, ±30s skew, NonceCache nonce-replay defense, constant-time compare.
- Read NonceCache (lives in EffectorValidator.kt:22): bounded-LRU via ConcurrentLinkedQueue + synchronized HashSet, capacity 1000 default (gateway uses 4096).
- Read CronRunner.kt — uses GATEWAY_TOOL_ALLOWLIST as read-only ceiling, intersection with skill-narrowed list.
- Read wrap memory file — verified all 4 R1-flagged facts now correct (tutor.db at `/root/.jarvis/`, loops marked RUNNING, gateway/cron/gws/quiet env split into "NOT in .env", 6 tags listed including layer-a-acceptance, 3-column stage table present).
- **Adversarial test**: ran a UTF-8 round-trip test (Java) for `bytes → String(UTF-8) → bytes(UTF-8)`. Round-trip is byte-perfect for ASCII, multi-byte UTF-8, BOM, NUL bytes. **BREAKS for invalid UTF-8 sequences** (replaced with U+FFFD `EF BF BD`).

---

## 🔴 Devil's Advocate (Gemini)

```
AGENT: Devil's Advocate
STANCE: REJECT
REASONING: The most critical regression is the potential for HMAC verification failure
due to character encoding discrepancies. By first decoding the incoming request body to
a `String` via `call.receiveText()` and then re-encoding it to `ByteArray(Charsets.UTF_8)`,
the server risks altering the payload bytes if the original content was not UTF-8 or
included a Byte Order Mark. This breaks the fundamental principle of HMAC, where both
client and server must sign over the exact same byte sequence, leading to legitimate
requests being rejected and undermining the entire HMAC integrity for certain payloads.
KEY CONCERN: HMAC body byte canonicalization mismatch.
```

**R2 first-hand validation of the canonicalization claim**: 
- BOM round-trips fine (`EF BB BF` survives).
- NUL bytes round-trip fine (`68 00 69` survives).
- All valid multi-byte UTF-8 round-trips fine.
- **Invalid UTF-8 sequences DO get mangled** (e.g., `C3 28` becomes `EF BF BD 28`).

So the regression is REAL but narrower than Gemini stated: it only bites if a producer ever sends invalid UTF-8 in the body. JSON spec is UTF-8 strict, so `python-telegram-bot` / `teloxide` clients producing valid JSON will be safe. The vulnerability surface is: an attacker who can mutate bytes-in-flight (already need TLS-MITM) AND can craft a body with invalid UTF-8 that the server's `String` decoder normalizes to a body whose sha256 still matches the producer-signed digest. This is **not exploitable** because the mangled re-encoded bytes will produce a DIFFERENT sha256 from the producer-signed one, so the HMAC will FAIL closed (not open). The risk is **availability** (legitimate-but-malformed requests rejected) not **integrity** (forged requests accepted). Severity: **MEDIUM, not CRITICAL.**

---

## 📚 Domain Expert (Gemini)

```
AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The design implements a robust HMAC scheme, binding method, path, timestamp,
and nonce to the signature, which offers significantly stronger protection against replay
and tampering than simpler designs like GitHub's. This level of defense is appropriate
given the "LLM-tool-use blast radius." However, the reliance on `call.receiveText()` for
body capture is a critical flaw, as it prevents signing over the exact raw bytes,
potentially leading to signature mismatches or allowing data manipulation through encoding
differences, a common pitfall avoided by Stripe and Slack. Additionally, the lack of a
specified nonce format creates a significant vulnerability for replay attacks.
KEY CONCERN: The fundamental issue of signing over `call.receiveText()`'s String
representation rather than the raw request body bytes, coupled with the undefined nonce
format, are critical security vulnerabilities that undermine the entire HMAC scheme.
```

Recommended fix (verbatim from Gemini): Ktor `ApplicationPlugin` that captures raw bytes once via `call.receiveChannel().toByteArray()` and stashes in `call.attributes`, then HMAC + deserializer both pull from there.

Also flagged: nonce format/length not specified anywhere in the wrap or in code comments — producers must use UUIDv4 or 256-bit random hex; sequential nonces would let an attacker pre-burn future values.

---

## ⚙️ Pragmatist (Gemini)

```
AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: The wrap provides strong answers to "what's running," "next priority," and
"how to debug," leveraging dedicated sections like "Tutor surface," "Next likely
directions," and "Cmd cheats." The R1 fixes for persistence and env vars improve
accuracy. However, the critical 1-line tl;dr is still missing, forcing a deeper scan
for immediate context. The "Open work" section also lacks the requested tagging,
reducing its scannability.
KEY CONCERN: The absence of a concise, top-level tl;dr summary significantly hinders
immediate orientation speed, requiring the future-self to piece together the current
state and focus from multiple sections.
```

ORIENTATION SPEED: Fair — header gives architectural context, Cmd cheats + Next directions are excellent, but "## Session totals" as the first heading isn't what next-you needs first.

ACCURACY: Good — R1 facts are now correct.

ACTIONABILITY: Good — "Next likely directions" priority list is crystal clear.

WHAT'S MISSING (would save 10 minutes):
1. 1-line tl;dr at the top
2. Current overall system health snapshot ("All core services green, daemon connectivity intermittent")
3. Brief "What just shipped/changed this session" summary

---

## 🧱 First Principles (Gemini)

```
AGENT: First Principles
STANCE: REJECT
REASONING: R1's load-bearing insight ("Stage A is the only zero-gap stage") is
demonstrably **false** under the new 3-column framing. The table clearly states
Stage A requires a "real task" (a producer/trigger), contradicting the "zero-gap"
claim, while the wrap's reading note confirms Stage B (KnowledgeGraph) is the *only*
currently "closed-loop" stage delivering user benefit. User value fundamentally
requires *all three* conditions: Code Shipped, Wired, and Live Data; "Code + Wired"
alone delivers zero value without an active producer feeding requests. The reshape to
a 3-column table significantly improves clarity and honesty, making the picture much
clearer by explicitly breaking down the journey to live data and exposing dormant
stages. The proposed framing in question 4 accurately reflects this reality. A user
clicking a preset would activate Stage A, but Stage D requires an LLM action
(`wiki_append`), and Stage C would not activate as it depends on developer-provided
`SKILL.md` files.
KEY CONCERN: The primary framing gap was R1's initial conflation of "code wired"
with "user benefit delivered." The new 3-column structure correctly distinguishes
between these states, forcing an honest assessment of what is truly "live" and
delivering value.
```

The "REJECT" stance is on R1's framing, not on R2's fixes. Gemini explicitly says the 3-column reshape "significantly improves clarity and honesty." First Principles is APPROVING the R1 fix while REJECTING R1's own framing as too generous.

---

## ⚠️ Risk Analyst (Gemini)

```
AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: The new HMAC verification, while a step forward, is severely undermined
by a critical legacy fallback, and the attack surface for content injection and
integration flaws remains high. The system is not immediately exploitable due to
being unwired, but these risks become active upon deployment.
KEY CONCERN: The legacy bearer-token fallback path is the most critical vulnerability.
It allows an attacker who possesses the `JARVIS_GATEWAY_SECRET` to entirely bypass the
new HMAC verification by simply omitting the `X-Jarvis-Hmac` header, rendering the
HMAC "fix" ineffective for authentication.
Concrete Mitigation: The legacy bearer-token fallback path MUST be removed prior to
activating the gateway endpoint. If a transitional period is absolutely required,
separate, distinct secrets must be used for the legacy and HMAC authentication paths
to prevent downgrade attacks.
```

Critical sub-finding: HMAC and bearer paths share `JARVIS_GATEWAY_SECRET`. Anyone with that secret can downgrade by simply omitting the X-Jarvis-Hmac header. The fix is "transitional" but no removal date is documented.

---

## Sanity Check

**SANITY [Devil's Advocate]: PASS-with-caveat.** The encoding-mismatch concern is REAL, but R2's first-hand UTF-8 round-trip test shows the failure mode is `availability` (HMAC fails closed for invalid UTF-8) not `integrity` (forged requests accepted). The verbatim severity claim "undermining the entire HMAC integrity" overshoots; correct severity is MEDIUM, not REJECT-grade. Adjust judge weighting accordingly.

**SANITY [Domain Expert]: PASS.** Stripe / Slack pattern reference is accurate; nonce format omission is a real spec gap; recommended `call.receiveChannel().toByteArray()` fix is exactly the right Ktor primitive. Two distinct concerns properly named.

**SANITY [Pragmatist]: PASS.** Specific list of missing items (tl;dr, health snapshot, what-shipped summary) is concrete and actionable. R1's tagging recommendation indeed not implemented in the wrap, and Pragmatist correctly notes it's still two sub-sections.

**SANITY [First Principles]: PASS.** Sharp adversarial reading of R1's own claim — correctly points out the 3-column table contradicts R1's "Stage A is zero-gap" insight (Stage A is `❌ no chat yet`, Stage B is the only fully green row). REJECT verdict is on R1's prior framing, not R2's fixes — judge should weight as APPROVE on the R2 reshape.

**SANITY [Risk Analyst]: PASS.** Downgrade-via-omitted-header is a textbook flaw and shared-secret means there's no defense. The "no removal date documented" finding is also a real ops gap. Recommended mitigation (separate secrets per path OR remove fallback before activation) is sound.

---

## Judge

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
R1's three must-fixes are CLOSED (wrap factual errors corrected, gateway HMAC verify
wired through DaemonAuth, 3-column stage table present). However, the R1 fix
introduced ONE new must-fix (gateway HMAC bearer-token DOWNGRADE: shared secret +
omitted X-Jarvis-Hmac header bypasses HMAC entirely) and surfaced TWO real-but-
lower-severity gaps R1 missed (raw-body capture is post-receiveText, breaking HMAC
fail-closed for invalid UTF-8; nonce format unspecified). Wrap memory file is
substantially better but still missing the 1-line tl;dr R1 explicitly recommended.

AGENT CONSENSUS: 2 REJECT, 3 CONDITIONAL — 0 agents flagged
  (Devil's Advocate REJECT down-graded to MEDIUM by sanity check; First Principles
   REJECT is on R1's framing not R2's fixes — effectively APPROVE on the reshape.)

KEY ISSUES:
  1. CRITICAL: Bearer-token fallback shares JARVIS_GATEWAY_SECRET with HMAC path.
     Anyone with the secret can omit X-Jarvis-Hmac and bypass HMAC entirely. Must
     either: (a) remove fallback before JARVIS_GATEWAY_ENABLED ever flips on, OR
     (b) split into two secrets (JARVIS_GATEWAY_HMAC_SECRET vs JARVIS_GATEWAY_BEARER
     for transitional clients) so leaking one doesn't enable downgrade. Document a
     removal date for the bearer path either way.

  2. MEDIUM: Raw-body capture happens via call.receiveText() then .toByteArray(UTF-8)
     instead of call.receiveChannel().toByteArray(). For valid UTF-8 (BOM, NUL,
     multi-byte) this round-trips byte-perfect. For invalid UTF-8 it does NOT —
     bytes get replaced with U+FFFD. Failure mode is fail-CLOSED (legitimate-but-
     malformed-UTF-8 requests rejected; no integrity bypass). Fix: install a
     RawBodyCapture plugin per Domain Expert recommendation, or document that
     producers MUST send strictly valid UTF-8 JSON (which spec already requires).

  3. MEDIUM: Nonce format/length not specified. Server accepts any string. Producers
     should be told: "X-Jarvis-Nonce MUST be a UUIDv4 OR 32+ random hex chars; never
     sequential; never derived from time." A 1-line spec note in GatewayInbound or
     a public webhook spec doc closes this.

  4. LOW: Wrap memory file still missing 1-line tl;dr at top. R1 specifically called
     this out; not fixed in R2. Adds ~30s to next-session orientation. Cosmetic.

  5. LOW: "Open work" not tagged with [code-debt]/[user-action]/[infra] per R1
     recommendation. Same orientation friction.

RECOMMENDED PATH:
  Before flipping JARVIS_GATEWAY_ENABLED=1 in production:
    A. Remove the bearer-token fallback OR split secrets (Issue #1, REQUIRED).
    B. Switch raw-body capture to call.receiveChannel().toByteArray() (Issue #2,
       SHOULD — no exploit today, but defense-in-depth + matches Stripe/Slack).
    C. Document nonce min-entropy requirement in GatewayInbound class doc + wrap
       memory file (Issue #3, SHOULD).
  
  Wrap memory file polish (Issues #4, #5):
    D. Add 1-line tl;dr at top (5-min edit).
    E. Tag "Open work" items with [code-debt]/[user-action]/[infra] (5-min edit).

  All 5 fixes are <90 minutes of work total. After they land, the body of work is
  APPROVE-grade.

  Note for future-you: gateway is currently UNWIRED on production (code-only, no
  producer, JARVIS_GATEWAY_ENABLED unset). Issue #1 is a "must-fix-before-activation"
  not a "must-fix-now." However, the wrap memory file should reference Issue #1 in
  the "How to dogfood gateway" section so a future activation flow doesn't skip it.

CONFIDENCE: 8
This R2 review is multi-author (5 distinct Gemini-2.5-flash perspectives), in
contrast to R1's single-author single-Claude review. The gateway code, wrap file,
and DaemonAuth were all read first-hand. UTF-8 round-trip behavior was empirically
tested in Java, not assumed. Test count (539/0) and tag count (6) were verified by
executing gradle + git commands. What would push to 9-10: an actual integration test
that POSTs a valid HMAC + nonce + ts request through the gateway path and confirms
end-to-end (the existing GatewayInboundTest.kt has a placeholder rate-limit test
but doesn't exercise the new HMAC code path).
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

**R2 conclusion:** R1 must-fixes ARE all closed. ONE new must-fix (gateway downgrade) + 2 lower-severity gaps. R2 is FLAWED-with-clear-fixes; loop should NOT terminate quietly without addressing Issue #1. After Issue #1 lands, this becomes APPROVE-grade.
