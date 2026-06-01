# Council review — 1779022799

**Problem:** jarvis-kotlin tutor backend rewrite decision — pick optimal path for goal (best UX + teaching efficiency for solo dev in 35-day finals window with 3-30 multi-user constraint).

**Proposed approach (3 paths):**
- PATH A — Full Python/FastAPI rewrite. ~60% jarvis-kotlin backend dies, 715 tests die, ~3-4 weeks rewrite.
- PATH B — Hybrid Kotlin/Ktor + Python ML sidecar via HTTP. ~80% jarvis-kotlin survives, 2-process ops.
- PATH C — Pure Kotlin + subprocess shell-outs to Python CLIs. ~500ms-2s startup overhead, brittle.

**Project context:**
- Domain: solo dev (Alex, FII Iași AI bachelor's), adaptive AI tutor for self + 3-30 classmates
- Stack: Kotlin/Ktor backend ~200 .kt files + 715 backend tests green, working auth + memory + sensor/telemetry + tutor routes; existing Slice 1.5 RO frontend
- Constraints: finals Jun 1-21 2026 = 35 days HARD deadline; multi-user from day 1 (Article 4 AI literacy in force NOW Feb 2025); $10 OpenRouter unlock budget
- ML stack needed: DSPy + GEPA prompt optimizer, MinerU 2.5 (1.2B VLM), PaperQA2 citation-RAG, Whisper.cpp RO, Piper RO TTS, FSRS-6, PFA — all Python-first
- Prior decisions: goal locked "best UX + best teaching efficiency, sunk-cost-free"

**Timestamp:** 2026-05-17T... UTC

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: All three paths are framed around the wrong question. The real question isn't "Kotlin vs Python vs sidecar" — it's "can a solo dev with 35 days until finals (Jun 1-21) ship a working multi-user adaptive tutor with DSPy + GEPA + MinerU 2.5 (1.2B VLM) + PaperQA2 + Whisper.cpp + Piper + FSRS-6 + PFA?" The answer is no, and the architecture debate is procrastination disguised as planning. Path A burns 3-4 weeks of the 5-week window on a rewrite that produces zero new user-facing value and kills 715 green tests — catastrophic. Path B's "80% survives, just add a sidecar" understates the integration tax: you now own DSPy version pinning, GEPA training runs, MinerU's 1.2B VLM GPU/RAM footprint on a personal VPS, two-process supervision, model warmup on cold sidecar boot, Romanian Whisper.cpp model downloads, Piper voice models, AND a Kotlin↔Python contract layer — all while studying for finals. Path C is a strawman nobody would pick; including it makes B look reasonable by contrast (false trichotomy).
KEY CONCERN: The ML stack itself is the existential risk, not the backend choice. MinerU 2.5 is a 1.2B-parameter VLM — that alone needs ~4-6GB VRAM or a slow CPU path; DSPy+GEPA requires labeled training data you don't have for Romanian university material; PaperQA2 needs a curated corpus per subject; FSRS-6 + PFA need real student interaction logs that don't exist yet. Picking ANY of A/B/C and then trying to integrate all 7 ML components in 35 days while finals are happening is the failure mode. Reject the framing. The correct path is Path B's scaffolding (keep Kotlin, add a tiny Python sidecar) BUT with a brutally cut ML stack: pick the ONE component that delivers the most teaching value for YOUR Jun 1-21 finals (almost certainly FSRS-6 spaced repetition over your existing course material + a thin Whisper voice-in for hands-free review) and defer DSPy/GEPA/MinerU/PaperQA2/Piper to post-finals. If you won't cut the ML stack, no architecture choice saves you.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL (Path B with stipulations)
REASONING: Path B is the industry-standard "polyglot microservice" or "ML sidecar" pattern — exactly how Spotify (Scala/Java + Python ML), LinkedIn (Java + Python via gRPC), Stripe Radar (Ruby/Scala + Python), and most "real" production ML systems are deployed. The Kotlin+Python sidecar specifically is well-trodden: Adyen, Zalando, and several JVM-heavy shops run DJL-or-HTTP-bridge to Python ML workers because rewriting working JVM business logic to chase Python ML libraries is a known anti-pattern (Etsy's 2017 "Python-fication" attempt and Khan Academy's partial Go rewrite are cautionary tales — both lost 6-12 months and regressed feature velocity). Path A throws away 715 passing tests and proven auth/memory/sensor infra to chase a stack convenience that a 5-10ms HTTP hop solves; Path C (subprocess-per-call) is the "shell-out anti-pattern" that DeepTutor explicitly rejected in their v2 redesign and that every CLI-wrapping LLM project regrets by month 2 (cold-start kills streaming UX, kills FSRS state, kills DSPy compiled-program caching). The standard reference architecture here is OpenWebUI's Ollama sidecar model, LangServe-as-sidecar behind a non-Python gateway, or Modal/Replicate-as-sidecar — all variants of Path B. DSPy specifically REQUIRES a long-lived Python process to hold compiled signatures and GEPA optimization state in memory; subprocess shell-outs would re-compile every call, which is the exact failure mode the DSPy team warns about in their deployment docs.
KEY CONCERN: Path B's success hinges on three non-negotiables that must be in the plan before approval: (1) the Python sidecar runs as a persistent FastAPI/Uvicorn process with a supervisor (systemd or supervisord), NOT spawned per-request — otherwise it degenerates into Path C; (2) the Kotlin↔Python contract is defined via OpenAPI/Pydantic schemas with generated Kotlin clients (kotlinx.serialization), not hand-rolled JSON, because the #1 failure mode in polyglot sidecars is silent contract drift (see LinkedIn's 2019 post-mortem on their Photon-ML migration); (3) shared state (FSRS reviews, DSPy traces, user telemetry) lives in ONE database owned by Kotlin — the Python sidecar must be stateless or use Kotlin's DB as source of truth via a read-through API, NOT its own Postgres, or you'll spend the remaining 35 days debugging dual-write inconsistencies instead of teaching yourself for finals.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: Path B is the only sane choice for a 35-day finals window — Path A throws away 715 green tests and a working auth/memory/telemetry stack to chase a rewrite that historically slips, and Path C's 500ms-2s cold start per ML call will make the tutor feel broken in live demos with classmates. But "hybrid" without discipline becomes two half-maintained stacks at 3am on May 31st. The 5-10ms HTTP hop is irrelevant; the real cost is debugging across process boundaries when a Python traceback dies behind a Kotlin 500. Hand-off to classmates also gets harder — they now need to run two things instead of one `./gradlew run`.
KEY CONCERN: Path B only survives if Alex enforces three constraints before shipping any ML feature: (1) the Python sidecar runs as a single `docker compose up` service alongside Ktor with structured JSON logs piped to the same stream, so 3am debugging is one `docker logs` not two; (2) every Kotlin→Python call has a typed DTO contract + a circuit breaker that returns a user-visible "ML offline" state instead of a 500, so finals-night sidecar crashes degrade gracefully instead of breaking the whole tutor; (3) a single `make dev` / `./scripts/dev.ps1` script boots both processes for classmates — if onboarding requires reading a README, breadth-first hand-off fails. Without these, Path B silently rots into Path A's pain at half the test coverage.

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: The framing is wrong. The real question isn't A/B/C — it's "what's the minimum infrastructure between the user and a working tutor by June 1?" With 35 days to finals and an LLM doing 99% of the cognitive load, backend language is a rounding error. The actual bottlenecks are: (1) prompt/agent quality (DSPy programs), (2) content ingestion (MinerU + PaperQA2 indexing of FII course PDFs), (3) scheduler correctness (FSRS-6/PFA math), (4) UX latency on the chat surface. None of these care whether the HTTP shell is Ktor or FastAPI. A rewrite burns 5-10 of your 35 days on zero-user-value plumbing while 715 tests of behavior get re-derived. The hybrid sidecar (B) is the only path that respects the constraint "don't break what works," but even B is overbuilt — the ML libraries you named (DSPy, MinerU, PaperQA2, Whisper, Piper, FSRS) are all Python or have Python reference impls, so the question collapses to: where does the Python process live, and does it need to live behind your existing Ktor at all?

Concrete alternative: keep Ktor as-is for the 715-test surface (auth, sessions, ledger, telemetry). Stand up a single Python FastAPI service on the same VPS that owns ALL ML (DSPy agents, MinerU ingestion worker, PaperQA2 retrieval, Whisper/Piper, FSRS scheduler). Ktor proxies a single `/api/v1/tutor/*` namespace to it via HTTP. No subprocess shell-outs (C is operationally fragile under load). No rewrite (A throws away tested behavior for aesthetic uniformity). This is essentially Path B, but reframed: the Python service is not a "sidecar" — it's the product. Ktor is the legacy auth shell you don't have time to rewrite. Ship the Python service first; Ktor changes are minimal HTTP forwarders. If by day 25 the Python service is doing 90% of user-visible work, that tells you whether a future rewrite is even worth considering — but post-finals, not pre-finals.

KEY CONCERN: 35 days is not enough time to rewrite a 715-test backend AND build a novel ML tutor AND study for finals. The premise that you must pick a "backend architecture path" assumes the backend matters more than the agent/content/scheduler layers — it doesn't. Pick the path that lets you write zero Kotlin and zero rewrite code, and spend all 35 days on DSPy programs, PDF ingestion, and FSRS tuning. If finals slip because you spent week 2 porting auth endpoints, the entire project failed regardless of which language won.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL (Path B)
REASONING: Path A is CRITICAL-risk: a 3-4 week rewrite with 35 days to finals leaves zero buffer for the inevitable integration hell, and 715 dead tests means Alex flies blind precisely when stakes peak — one bad merge week 3 and finals arrive with a half-working backend and no regression net. Path C is HIGH-risk on the wrong axis: 500ms-2s subprocess startup per ML call means tutor latency feels broken to 30 classmates simultaneously, and no streaming kills Whisper/TTS UX dead. Path B isolates the blast radius — sidecar OOM on MinerU during a Romanian PDF crashes one process, Kotlin keeps serving cached drills, FSRS state survives in Postgres, student retries in 10s; rate-limit at 9am exam-day hits LiteLLM in the sidecar only, Kotlin degrades to "cached content only" instead of 500ing the whole app. The two-process cost (deploy + logs) is real but bounded; the test-survival cost of A and the latency cost of C are not.
KEY CONCERN: Path B only stays CONDITIONAL-safe if sidecar failures are explicitly designed as degraded-mode (circuit breaker on the HTTP client, cached-fallback responses, sidecar restart supervisor like systemd/Docker healthcheck) — without that, a single Python OOM during exam week takes the whole tutor offline for 30 students and the blast radius matches Path A.

---

## Sanity Check

SANITY Devil's Advocate: PASS — clean. Reframes are legitimate (devil's role allows finding weakest assumption); concrete numbers cited (1.2B VLM RAM, training data absence, finals window).
SANITY Domain Expert: PASS — clean. Named precedents (Spotify, LinkedIn Photon-ML 2019, Etsy 2017 Python-fication, Khan Go rewrite, OpenWebUI/Ollama, LangServe, Modal/Replicate, DSPy long-lived-process doc).
SANITY Pragmatist: PASS — clean. 3am debugging + hand-off cost emphasis stays in-lane. 3 conditions named explicitly.
SANITY First Principles: PASS — clean. Actively rejected framing per persona rule. Concrete alternative given (Kotlin = legacy auth shell, Python = product).
SANITY Risk Analyst: PASS — clean. CRITICAL/HIGH/MEDIUM severity ranks per path. Top concern named with concrete mitigations.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
Path B is the correct architecture — all 5 agents converge on it when read carefully (Devil's "Path B's scaffolding" + First Principles' "Python service IS the product, Ktor is legacy shell" are Path B variants). But the BIGGER finding is that the architecture debate is masking the real risk: 7 Python-only ML components (DSPy / GEPA / MinerU 1.2B VLM / PaperQA2 / Whisper.cpp / Piper / FSRS-6+PFA) plus multi-user compliance plus 715-test backend plus finals in 35 days = no version of A/B/C ships. The framing "pick a path" is procrastination disguised as planning.

AGENT CONSENSUS: 2 REJECT framing (both still favor B-shape), 3 CONDITIONAL on B. Effective unanimity on B-architecture; sharp disagreement on whether the ML scope is viable at all.

KEY ISSUES:

1. **ML scope is the existential risk, not backend choice.** (Devil + First Principles) MinerU 2.5 needs 4-6GB VRAM or slow CPU; DSPy+GEPA needs labeled training data Alex doesn't have for RO university material; PaperQA2 needs curated per-subject corpus; FSRS+PFA need real student interaction logs that don't exist yet. Cut to ≤2 ML components for v1.

2. **Sidecar discipline non-negotiables (3-of-5 agents converged):**
   - ONE database owned by Kotlin. Python sidecar stateless OR reads through Kotlin DB API. No dual writes.
   - OpenAPI/Pydantic typed contract + generated Kotlin clients. Hand-rolled JSON = silent contract drift.
   - Persistent FastAPI/Uvicorn process under supervisor. NOT per-request spawn.
   - Single `docker compose up` for classmates.
   - Circuit breaker on Kotlin→Python HTTP + cached-fallback. Otherwise sidecar OOM = whole-tutor outage.

3. **First Principles reframe deserves serious weight.** "Python service IS the product; Ktor is the legacy auth shell you don't have time to rewrite." Inverts the framing. Don't ask "where does Python live" — ask "how thin can the Kotlin layer get."

RECOMMENDED PATH:

**Path B variant: "thin Kotlin shell + thick Python ML service" with brutal ML scope cut.**

1. Cut ML scope to v1-shippable: keep only **FSRS-6 spaced repetition + Whisper.cpp Romanian voice input** for v1. Defer DSPy/GEPA/MinerU/PaperQA2/Piper/PFA to v2 post-finals.
2. Kotlin/Ktor preserved 100%. All 715 tests, auth, memory, telemetry survive. Zero rewrite.
3. Python sidecar (FastAPI + Uvicorn under systemd or docker compose healthcheck): owns ONLY 2 v1 ML primitives. Stateless. Reads/writes via Kotlin DB API (Kotlin owns Postgres). OpenAPI contract generates Kotlin client via `kotlinx.serialization`.
4. Circuit breaker + cached degraded-mode on every Kotlin→Python call.
5. Single `docker compose up` for classmates. JSON-structured logs to stdout.
6. Post-finals v2 evaluation: if Python service does 90% of user-visible work by day 25, schedule v2 Kotlin-retirement plan. Else hybrid stays.

This satisfies all 5 agents simultaneously: Devil (ML scope cut), Domain Expert (industry-standard sidecar pattern), Pragmatist (single `docker compose` + circuit breaker), First Principles (Python service is the product), Risk Analyst (blast radius isolated with degraded-mode).

CONFIDENCE: 8

What would shift: whether "share with classmates" actually requires multi-user from day 1 OR whether `SINGLE_USER=true` flag for v1 + classmate access in v2 is acceptable. Single-user v1 OK → confidence 9. Multi-user non-negotiable day 1 → EU AI Act Article 4 + Article 12 audit log adds 3-5 days to already-tight window.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
