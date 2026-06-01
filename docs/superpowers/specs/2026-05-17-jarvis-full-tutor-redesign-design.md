# Spec: Jarvis Full Tutor Redesign — Novice Pedagogy + Brutalist UX + Multi-User

**Date:** 2026-05-17 · **Author:** Alex (Corgi) + brainstorming skill · **Status:** draft
**Plan:** *(pending — writing-plans skill is next step after user approval)*
**Research wiki:** `docs/superpowers/research/2026-05-17-novice-pedagogy-tutor-redesign-research.md` (3,500+ lines, Rounds 1-9, 8 parallel subagents)
**Council verdict:** `.claude/council-cache/council-1779022799-backend-path-decision.md` (Path B hybrid)

---

## 1. Problem

Current `jarvis-kotlin` tutor (Slice 1.5 shipped 2026-05-11) does not teach a novice from zero. PDF chrome dominates the surface, terminology lands without scaffolding, drills are recency-prioritized not need-prioritized, no metacognitive close-loop, no shareable cohort layer, no multi-user infra, no AI Act / GDPR compliance surface, no Romanian voice mode, no spaced repetition, no mastery model, no misconception catalog, no formal placement, no day-of-exam mode. The user has 35 days to FII Iași finals (Jun 1-21 2026) across 5 subjects (PA / PS / POO / ALO / SO+RC) and wants the same tool to scale to 3-30 classmates in the same grupa.

The current architecture (Ktor + SQLite + Exposed + ~715 backend tests + Slice 1.5 Romanian-language frontend) is load-bearing but missing:

- mastery-state model (PFA), spaced-repetition (FSRS-6), feedback ladder, misconception ribbon
- Python-only ML primitives: DSPy + GEPA, MinerU 2.5, PaperQA2, Whisper.cpp RO, Piper RO
- multi-user auth (Auth.js v5), RLS (Postgres), per-user budget enforcement (LiteLLM)
- pedagogical scaffolding: 5-phase Learning Rhythm, mode FSM (Normal/Crunch/Day-Of), implementation-intention onboarding, AI-literacy gate
- 28-surface pedagogy inventory with brutalist R1-R10 + V1-V20 visual primitives compliance
- compliance scaffolding: AI Act Article 4/11/12/14, GDPR Articles 15/17/18/22, Romanian Law 190/2018, Schrems II TIA, Hetzner DPA, Resend DPA, OpenRouter EU endpoint + ZDR

The redesign is a full replatform of the user-facing pedagogy + ML + compliance layers on top of the surviving Kotlin/Ktor backend.

## 2. Goals + non-goals

### 2.1 Goals (locked)

- **Best possible UX + teaching efficiency** — any current work is deprecatable if it does not serve this goal
- **Multi-user from day 1** — share with ~10 friends from same year (subset of grupa, not whole grupa)
- **Full ML scope** — no deferral of DSPy / GEPA / RAG / RO voice
- **Brutalist mono UI** — same palette as Slice 1.5 but shaped better and implemented better
- **Zero-knowledge entry** — expect no prior CS knowledge from a novice; preserve formal terminology
- **Real-content grounding** — drill from existing FII materials (Lucanu/Craus PA, Vidrașcu SO labs, Frasinaru POO, past papers 2015-2024), not placeholders
- **No paid LLM API spend** (banked feedback) — Runtime LLM = OpenRouter `:free` only. GEPA judge + smoke eval = Claude Opus 4.7 via `claude -p` headless, routine-triggered (not cron) on push / new-label-threshold / PR / deploy / curator-promote events. Authenticated with existing ClaudeMax sub. Cross-family bias preserved (Opus vs DeepSeek). Zero new subscriptions.
- **Romanian-language primary** with EN toggle — comma-below diacritics ț ș mandatory (NOT cedilla)
- **EU AI Act + GDPR + Law 190/2018 compliant** before multi-user goes live
- **Pre-finals execution** — finals: ALO Jun 3 / SO+RC Jun 8 / PA Jun 10 / POO Jun 12* / PS Jun 17* (* = unconfirmed)

### 2.2 Non-goals

- **No streaks, no XP, no badges, no leaderboards** (banked decision, Duolingo shallow-learning-trap critique)
- **No real-time chat between cohort members** (async forum-shape only; Discord remains separate)
- **No peer-graded code** (cohort sees AST-normalized peer solutions only AFTER own grade returned)
- **No emotion recognition** (Article 5(1)(f) AI Act HARD BAN in education)
- **No CNP / health / biometric / location / financial / employment data — tutor-scope only.** Jarvis is intended to grow into a multi-subsystem personal OS (tutor + dietician + finance advisor + ...). Future subsystems WILL need health/location/financial categories. This spec covers the tutor subsystem only and excludes those categories from tutor's scope. Cross-subsystem data isolation = future jarvis-platform design problem (not deferred for tutor, but explicitly out of tutor's spec). Per-subsystem RLS + per-subsystem consent records anticipated as the eventual pattern.
- **No formal grading reportable to FII faculty** (private personal tool, not institutional)
- **No cohort growth past ~10 friends** (hard-cap 12 in code; this is a personal friend-group tool, not a class-wide rollout)
- **No deadline-framing nag copy** (banked: never relitigate "should you study instead")
- **No always-listening voice mode** (push-to-talk locked)
- **No PDF buttons on lesson surfaces** (user quote: "I don't want to click PDFs, I want knowledge spoonfed into my brain")
- **No Drupal / Strapi / Payload CMS** for content authoring (git-tracked YAML wins on every axis)
- **No autoplay animations** (every visualization is discrete frames with manual advance per V2)
- **No cohort percentile comparison** (research bans anchoring under stress)

## 3. User-visible acceptance

The full system has 28 pedagogy surfaces (see §6.2). Visual acceptance is sliced into 10 ship-gates (§14 execution order). Per the Slice 1 ghost-component lesson (2026-05-11), each ship-gate carries:

- **§3.2 first-paint `data-testid` checklist** — every NEW component + every modified-mount-site has a row
- **§3.3 interaction-smoke checklist** — every click target lists expected action + forbidden outcomes (4xx/5xx, "404", "error" text)

These are detailed per-slice in the plan doc (writing-plans skill next step). At spec-level the acceptance contract is:

1. All 10 ship-gates pass §3.2 + §3.3 against live URL via Playwright headless
2. Pedagogy LLM-as-judge eval ≥0.85 weighted on 250-item nightly suite
3. Romanian-correctness CI gate (`tools/check-romanian.sh`) zero cedilla pollution in source/locale
4. RLS cross-tenant CI test green (no user can read another user's drills/AI/audit)
5. AI literacy first-login gate enforces version+annual-reconfirmation before any LLM endpoint
6. All 17 F-deliverables shipped to repo (paths in §11)
7. All 15 E-compliance docs shipped to `docs/compliance/`

## 4. Architecture

### 4.1 Backend path: Path B hybrid (council-locked)

`.claude/council-cache/council-1779022799-backend-path-decision.md` verdict: Path B (Kotlin/Ktor + Python ML sidecar). Path A (full Python rewrite) kills 715 tests + risks pre-finals window. Path C (subprocess shell-outs) brittle. Path B retains ~80% of jarvis-kotlin, adds Python sidecar process for ML primitives only.

```
┌────────────────────────────┐         ┌────────────────────────────┐
│ Frontend (Slice 1.5++)     │ HTTPS   │ Caddy 2.11.2 (CVE-patched) │
│ Next.js / Svelte           │────────▶│ TLS 1.3 + HSTS preload     │
│ brutalist mono UI          │         │ CSP + rate limit 100/min   │
└────────────────────────────┘         └─────────────┬──────────────┘
                                                     │
                                                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Ktor backend (jarvis-kotlin, surviving ~80%)                       │
│  ─ Auth.js v5 + Drizzle + Resend magic-link (PRIMARY auth)         │
│  ─ Exposed + Postgres 17 (tutor domain tables, RLS enforced)       │
│  ─ Caffeine in-process cache (NOT Redis)                           │
│  ─ Micrometer Prometheus :8080/metrics                             │
│  ─ OTel traces → :4317 otel-collector                              │
│  ─ HikariCP pool sized to CX22 RAM                                 │
│  ─ /api/v1/{tutor,curator,me,admin,sensor} routes                  │
└─────────────────┬──────────────────────────────┬────────────────────┘
                  │                              │
   pgcrypto       │                              │ OpenAPI HTTP
   pgvector 0.8   ▼                              ▼ (cached degraded
   pgaudit  ┌────────────────────┐   ┌──────────────────────────────┐
   pg_cron  │ Postgres 17.3      │   │ Python ML sidecar             │
            │  ─ 18 core tables  │   │  ─ FastAPI under supervisor   │
            │  ─ RLS on all      │   │  ─ DSPy + GEPA optimizer      │
            │    user-scoped     │   │  ─ MinerU 2.5 (1.2B VLM)      │
            │  ─ append-only     │   │  ─ PaperQA2 (citation RAG)    │
            │    audit chain     │   │  ─ Whisper.cpp medium-RO Q5   │
            │  ─ pg_cron 90d /   │   │  ─ Piper ro_RO-mihai-medium   │
            │    14d / 5y        │   │  ─ FSRS-6 + PFA               │
            └────────────────────┘   │  ─ Circuit breaker + cached   │
                                     │    degraded-mode fallback     │
                                     └───────────┬───────────────────┘
                                                 │
                                                 ▼
                                ┌──────────────────────────────┐
                                │ LiteLLM proxy 1.55.x         │
                                │  ─ Per-user virtual keys     │
                                │  ─ TPM/RPM/budget enforce    │
                                │  ─ Fallback chain (DeepSeek  │
                                │    V3 → Qwen 3 Coder →       │
                                │    Gemini 2.5 Flash → V4)    │
                                │  ─ 5 OpenRouter free keys    │
                                │    rotate (5400 RPD agg)     │
                                └───────────┬──────────────────┘
                                            │
                                            ▼
                              eu.openrouter.ai (ZDR enforced)
                              → DeepSeek / Qwen / Google / etc.
```

**Single `docker compose up`** orchestrates: Caddy / Ktor / Postgres / sidecar / LiteLLM / Prometheus / Loki / Promtail / Grafana / OTel-collector / Whisper STT / Piper TTS. **Existing VPS reused:** `root@46.247.109.91`, live at `https://corgflix.duckdns.org`. Already provisioned, already paid for. No new hosting spend.

### 4.2 Data ownership

Postgres owned by Kotlin. Sidecar is stateless — every ML call passes context via OpenAPI-typed JSON request, sidecar returns prediction, Kotlin persists. Sidecar holds model weights + RAG vectors only.

Cross-process contract: OpenAPI 3.1 schema in `contracts/sidecar.openapi.yml`. Kotlin generates client via `openapi-generator-cli`; Python sidecar validates via Pydantic. Same `src/test/resources/fixtures/sidecar-{endpoint}.json` fixture validated by both sides — contract drift caught by CI.

### 4.3 Frontend

Existing Slice 1.5 + Phase 2/2.5 design tokens preserved. Routes added: `/curator/*` (admin SPA for content authoring), `/me/*` (settings + GDPR rights), `/welcome/ai-literacy/{ro,en}`, `/cohort/*` (multi-user surfaces), `/admin/experiments/*` (Thompson sampling dashboard).

Component library: React Flow for concept-graph editor, `react-tinder-card` for swipe queues, `motion@12.x` (mini variant 2.3KB; replaces deprecated `motion-one` naming) for animations, MathJax 4 with `a11y/explorer` + `a11y/speech` for interactive complex equations (lazy-loaded), KaTeX for inline math (already shipped), `mafs@0.21.x` for interactive math viz, `@visx/{hierarchy,network,shape,scale,group}@3.12.x` for React+D3 declarative components, `d3@7.x` (5 submodules tree-shaken — `d3-scale`, `d3-shape`, `d3-hierarchy`, `d3-array`, `d3-format`) for math/layouts, Mermaid for source-controlled diagrams (lazy), Three.js + `@react-three/fiber` + `@react-three/drei` for 3D wireframe (lazy per-route), `xstate@5.x` for state machines, custom React SVG for 12 V-vocabulary primitives (V1-V20 rules — §6.4). Total first-paint viz cost ~125KB gzipped. Plotly (`plotly.js-dist-min@3.5.1`, ~1MB, blue chrome) deprecated in favor of `@visx/shape`.

## 5. Database schema

### 5.1 Tables (18 core, all RLS-enforced where user-scoped)

```sql
-- Auth (Auth.js v5 / Drizzle owns these)
users (id UUID PK, email TEXT UNIQUE, display_name TEXT, lang TEXT DEFAULT 'ro',
       created_at TIMESTAMPTZ, last_login_at TIMESTAMPTZ)
sessions (sid TEXT PK, user_id UUID FK, expires_at TIMESTAMPTZ)
accounts (user_id UUID FK, provider TEXT, provider_account_id TEXT)
verification_tokens (identifier TEXT, token TEXT, expires_at TIMESTAMPTZ)

-- Cohort (multi-user, k=3 enforced)
cohorts (id UUID PK, name TEXT, created_at TIMESTAMPTZ, subjects TEXT[],
         budget_eur NUMERIC, current_semester TEXT)
cohort_membership (cohort_id UUID FK, user_id UUID FK, role TEXT CHECK (role IN
         ('student','curator','admin')), joined_at TIMESTAMPTZ, left_at TIMESTAMPTZ,
         active BOOL DEFAULT true, PRIMARY KEY (cohort_id, user_id))
invites (token_hash CHAR(64) PK, email TEXT, role TEXT, expires_at TIMESTAMPTZ,
         created_by UUID, consumed_at TIMESTAMPTZ)
presence (user_id UUID, kc_id TEXT, last_seen_at TIMESTAMPTZ, PRIMARY KEY (user_id, kc_id))

-- Pedagogy
kcs (id TEXT PK, subject TEXT, name_ro TEXT, name_en TEXT, cluster TEXT,
     bloom_level TEXT, difficulty INT, time_minutes INT, exam_weight NUMERIC,
     source JSONB, version INT)
kc_prerequisites (kc_id TEXT FK, prereq_kc_id TEXT FK, rationale TEXT,
                  PRIMARY KEY (kc_id, prereq_kc_id))
misconceptions (id TEXT PK, kc_id TEXT FK, label_ro TEXT, label_en TEXT,
                trigger JSONB, refutation JSONB, common_at_pct NUMERIC, version INT)
templates (id TEXT PK, kc_id TEXT FK, params_schema JSONB, stem_template_ro TEXT,
           stem_template_en TEXT, expected_solution_template TEXT,
           grader_rules JSONB, seed_examples JSONB, difficulty INT, version INT)
attempts (id BIGSERIAL PK, user_id UUID FK, kc_id TEXT FK, template_id TEXT,
          submission JSONB, correct BOOL, confidence TEXT CHECK
          (confidence IN ('definitely','maybe','guess','idk')),
          hint_level INT, gave_up BOOL, variant_id TEXT, session_id UUID,
          attempted_at TIMESTAMPTZ)
fsrs_cards (user_id UUID FK, kc_id TEXT FK, stability NUMERIC, difficulty NUMERIC,
            elapsed_days INT, scheduled_days INT, reps INT, lapses INT, state TEXT,
            last_review TIMESTAMPTZ, due TIMESTAMPTZ, PRIMARY KEY (user_id, kc_id))
mastery_pfa (user_id UUID FK, kc_id TEXT FK, successes INT, failures INT,
             beta NUMERIC, gamma NUMERIC, last_update TIMESTAMPTZ,
             PRIMARY KEY (user_id, kc_id))

-- Q&A + sessions
questions (id BIGSERIAL PK, cohort_id UUID FK, author_id UUID, kc_id TEXT,
           body TEXT, anonymous BOOL DEFAULT true, attachment JSONB,
           created_at TIMESTAMPTZ)
answers (id BIGSERIAL PK, question_id BIGINT FK, author_id UUID, body TEXT,
         helped_marker BOOL, promoted_to_misconception_id TEXT, created_at TIMESTAMPTZ)
study_sessions (id UUID PK, cohort_id UUID FK, kc_id TEXT, window_starts_at TIMESTAMPTZ,
                window_ends_at TIMESTAMPTZ, prompt TEXT, created_by UUID)
session_discussion (id BIGSERIAL PK, session_id UUID FK, author_id UUID, body TEXT,
                    created_at TIMESTAMPTZ)

-- Experiments (Thompson sampling)
variant_posterior (user_id UUID, experiment_id TEXT, variant_id TEXT,
                   alpha DOUBLE PRECISION DEFAULT 1.0,
                   beta DOUBLE PRECISION DEFAULT 1.0,
                   updated_at TIMESTAMPTZ,
                   PRIMARY KEY (user_id, experiment_id, variant_id))

-- Compliance
consent_log (id BIGSERIAL PK, user_id UUID FK, consent_type TEXT, granted BOOL,
             granted_at TIMESTAMPTZ, ip INET, user_agent_hash CHAR(64))
ai_literacy_confirmation (user_id UUID PK FK, version TEXT, confirmed_at TIMESTAMPTZ,
                           lang TEXT)
ai_interactions (id BIGSERIAL PK, user_id UUID FK, model TEXT, prompt TEXT,
                 response TEXT, tokens_in INT, tokens_out INT, cost_estimate NUMERIC,
                 created_at TIMESTAMPTZ)
                 -- pg_cron purge >90d
audit_log (id BIGSERIAL PK, user_id UUID, event TEXT NOT NULL, payload JSONB,
           occurred_at TIMESTAMPTZ, previous_hash CHAR(64), this_hash CHAR(64) UNIQUE,
           article_load_bearing BOOL DEFAULT false)
           -- BEFORE UPDATE/DELETE triggers raise exception (append-only)
           -- pg_cron purge non-load-bearing >6mo
user_preferences (user_id UUID PK FK, hint_mode TEXT, voice_tts_per_drill BOOL,
                  logging_paused_until TIMESTAMPTZ, notification_topics TEXT[],
                  exam_dates JSONB)

-- pgvector
documents (id BIGSERIAL PK, cohort_id UUID, source_path TEXT, content TEXT,
           embedding vector(768), metadata JSONB)
```

### 5.2 RLS pattern (all user-scoped tables)

```sql
ALTER TABLE attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY attempts_owner ON attempts
  USING (user_id = current_setting('app.current_user_id')::UUID);

-- Same shape for: ai_interactions, fsrs_cards, mastery_pfa, consent_log,
-- ai_literacy_confirmation, user_preferences, variant_posterior, presence

-- Cohort-scoped tables (questions, answers, study_sessions, etc) use:
CREATE POLICY questions_cohort ON questions
  USING (cohort_id IN (
    SELECT cohort_id FROM cohort_membership
    WHERE user_id = current_setting('app.current_user_id')::UUID AND active = true
  ));
```

Application role `jarvis_app` `NOBYPASSRLS`; migration role `jarvis_migrate` `BYPASSRLS`. CI integration test mandatory: cross-tenant assertion on every user-scoped table.

### 5.3 Migration plan (SQLite → Postgres)

Existing SQLite + Exposed retained for tutor domain. Migration via Flyway 10.x:

- `V001__baseline_from_sqlite.sql` — replicates existing UsersTable/TasksTable/FsrsCardsTable/AuditLinesTable schema in Postgres syntax
- `V002__add_cohort_layer.sql` — cohorts/membership/invites/presence
- `V003__pedagogy_tables.sql` — kcs/prerequisites/misconceptions/templates/attempts/fsrs_cards/mastery_pfa
- `V004__compliance.sql` — consent_log/ai_literacy_confirmation/ai_interactions/audit_log/user_preferences
- `V005__experiments.sql` — variant_posterior
- `V006__pgvector.sql` — documents + IVFFlat index
- `V007__rls_policies.sql` — enable + force + policies on all user-scoped tables
- `V008__pg_cron_retention.sql` — 90d/14d/5y purge jobs
- `V009__audit_log_triggers.sql` — block UPDATE/DELETE on audit_log

All migrations additive within a release; subtractive changes only in next release after rollback target no longer needs the dropped column.

## 6. Pedagogy

### 6.0 Four pedagogy axioms (anchor every decision in §6)

Every surface, every drill, every visualization is graded against four axioms with documented effect sizes:

| Axiom | Source | Hattie d | What it demands |
|---|---|---|---|
| **Dual-coding** | Paivio 1971 | **1.39** | Concept encoded simultaneously in linguistic + visuospatial channels = ~2× retention vs text-alone. **Highest-leverage pedagogy lever after feedback.** |
| **Spatial/temporal contiguity** | Mayer 2001 | 1.12 | Words + pictures appear together in space AND time. Legend-elsewhere or text-delayed-by-2s loses most lift. |
| **Concrete-before-abstract (CRA)** | Bruner | 0.67 | Show specific instance moving FIRST. Generalize after manipulation. Pure abstraction = WORST starting state. |
| **Explorable explanation + testing effect** | Victor 2011 / Roediger & Karpicke | 0.61 (testing) | Predict-then-reveal converts reading into hypothesis-testing. Manipulation > observation. |

These four axioms drive: §6.1 5-phase Rhythm, §6.4 visual primitive ruleset, §6.5 interaction grammar, §6.6 feedback ladder, every component in §6.4's library.

### 6.1 5-phase Learning Rhythm

Every drill on every KC traverses 5 phases driven by mastery state:

1. **Pretest** (mastery 0 OR FSRS-due) — concrete problem, no jargon, "solve however you want"
2. **Concrete** (post-pretest) — student's intuition replayed with one technical term landed (e.g. "you just used a greedy algorithm")
3. **Abstract** (mastery 0.3-0.5) — formal definition + invariant + edge cases
4. **Elaborate** (mastery 0.5-0.8) — variation problems, counterexamples, Posner conceptual change (refutation text)
5. **Retrieve** (mastery >0.5, FSRS scheduled) — recall gate without notes, 5-15s answer window

Phase transition driven by PFA + FSRS state, NOT by user clicking "next". The Learner Queue picks the next-best KC at the next-best phase via Bayesian Thompson sampling over variant prompts.

### 6.2 28 surfaces inventory

| # | Surface | Phase | Ship-tier |
|---|---|---|---|
| 0a | LearnerQueue (today) | meta | **shipped (Slice 1.5)** |
| 0b | Subject Map | meta | **shipped** |
| 0c | Lesson Entry (mastery 0) | pretest | **shipped** |
| 0d | Mid-lesson (term landing) | concrete | **shipped** |
| 0e | Retrieval gate (inline + confidence) | retrieve | **shipped** |
| 0f | Misconception ribbon | elaborate | **shipped** |
| 0g | Mock exam | retrieve | banked partial → §9 |
| 0h | Ledger drawer | meta | **shipped** |
| 0i | Mobile bottom tabs | meta | **shipped** |
| 1 | Feedback ladder (5-level L0-L4) | every phase | **ships** |
| 2 | Hint design (static/LLM/just-show-me) | every phase | **ships** |
| 3 | Day-Of mode | meta | **ships** |
| 4 | First-time onboarding (5 steps) | meta | **ships** |
| 5 | End-of-session wrap pane | meta | **ships** |
| 6 | Confidence calibration plot | meta | **ships** |
| 7 | Lab sandbox (V86 + xterm.js) | concrete/abstract | **ships** |
| 8 | Scratchpad (Type / Draw / Cornell / Photo) | all | **ships** (all 4 modes) |
| 9 | Mock-exam grading | retrieve | **ships** (high-stakes) |
| 10 | Cold-start placement primer | pretest | **ships** |
| 11 | Voice mode (push-to-talk + Feynman + drill + podcast overview) | all | **ships** |
| 12 | Settings / Me tab | meta | **ships** (GDPR + AI Act required) |
| 13 | Empty states (3 variants) | all | **ships** (foundational) |
| 14 | Error states (degraded modes) | all | **ships** (foundational) |
| 15 | Streak counter | meta | **EXPLICIT REMOVAL** (no streak, mastery sparkline only) |
| 16 | Past-paper approval queue | curator | folds into §16 (curator SPA) |
| 17 | Bilingual language toggle behavior | all | **ships** (foundational) |
| 18 | Cross-subject daily notification | meta | **ships** (extends existing R5 email) |

**Multi-user-only surfaces (§16 cohort layer):**
- Cohort presence pill (anonymous, k=3 server-enforced)
- Anonymous Q&A per-KC
- Cohort confusion-map (timeline + per-subject + trigger)
- Study-group async sessions (48h window, attributed discussion)
- Co-curator suggest-edit workflow
- Peer code review (cheat-shielded, anti-spoiler frame)
- Cohort budget transparency indicator

### 6.3 Brutalist UI ruleset R1-R10

R1 Major Third 1.25 type scale, JetBrains Mono only · R2 8-base spacing · R3 1px/2px/3px borders, hard edges, `border-radius` ≤4px · R4 Ink `#0a0a0a` + Paper `#f5f5f0` + Accent `#facc15` only · R5 No gradients, no shadows · R6 Asymmetric 7fr/3fr grid · R7 One focal claim per surface, one yellow CTA · R8 ≤3 hierarchy levels per surface · R9 Tufte sparklines as primary data viz · R10 Bilingual gloss inline (`<span class="gloss-ro">lacom</span> <span class="gloss-en">greedy</span>`)

### 6.4 Visual primitive ruleset V1-V20 + 36-viz roster + library matrix

#### V-rules

V1 Discrete frames not interpolation · V2 Manual advance only (no autoplay) · V3 Prediction gate every 3-5 frames · V4 One focal element animates per frame · V5 ARIA live narration per frame · V6 Ink + paper + accent only, hatching density for magnitude · V7 SVG over canvas (Counterpoint CMU ceiling: ~5000 SVG nodes before jank) · V8 `motion@12.x` (mini variant 2.3KB) library — NOT `motion-one` (deprecated package name) · V9 MathJax 4 with `a11y/explorer` + `a11y/speech` for interactive math (lazy), KaTeX for inline math · V10 Mermaid for source-controlled, custom React SVG for interactive · V11 Traces persist (past outcomes leave hairline ink) · V12 Yellow = focus not category · V13 Monospace labels everywhere · V14 Pure 90°/45° angles · V15 1px stroke default, 2px for focus (never opacity for focus) · V16 `prefers-reduced-motion` honored · V17 Keyboard parity with mouse · V18 Real `<text>` inside SVG · V19 Long descriptions on every complex diagram · V20 Static fallback for every interactive figure

#### 12 base component vocabulary (V1-V20 compliant, all ship)

AlgoStepper · ProcessTree · FilesystemTree · PermissionsGrid · BayesGrid · MatrixTransform · DPTable · RecursionTree · UMLClass · PacketFlow · Sparkline · SmallMultiples

Implementation per primitive in §6.4 library matrix below. All 12 are spec'd but NOT YET BUILT in `tutor-web/src/components/viz/` — clean slate. Existing 5 components (`NumLineDirect.tsx`, `SumPlotTracker.tsx`, `SlopeCounter.tsx`, `SigmaStackedBar.tsx`, `CompareFrames.tsx`) are brutalist-noncompliant (use blue `#3b82f6` + amber `#f59e0b` + rounded pills) → require theme.ts retrofit before more primitives ship.

#### 36-viz roster (all ship — no v1/v2 split)

Full inventory from V1 theoretical catalog. Top-10 ranked by impact × novelty × brutalist-feasibility, but ALL 36 ship.

**PA (algorithms):**
1. ⭐ PA-1 Recursion stack + tree + code triple-pane scrubber (call frames as physical cards, recursion tree fanning right, wasted-work shadow on duplicate subtrees)
2. PA-2 Greedy exchange-argument duel (counterfactual fork — drag a greedy choice, watch alternative branch fall behind)
3. ⭐ PA-3 DP table + dependency-arrow overlay + naive-tree-with-color-coded-duplicates side-by-side (wasted-work overlay = **world-first at this fidelity**)
4. ⭐ PA-4 Graph traversal with DFS-tree morph + edge classification (tree-edge solid, back-edge dashed; mid-traversal morph to DFS tree)
5. PA-5 Dijkstra-fails-on-negative split-screen with Bellman-Ford (lock-in animation proves the proof)
6. PA-6 MST cut-property overlay (drag a cut, system reveals minimum crossing edge as star)
7. ⭐ PA-7 NP-completeness reduction (gadget) animator with bidirectional iff (3-SAT → CLIQUE — drag assignment, watch clique form; drag clique, watch assignment derive) = **world-first**
8. PA-8 Complexity wall-clock translator (Tangle-prose with draggable n, ops/sec → seconds/years/age-of-universe; recurrence tree with work-per-level curve revealing master-theorem case)

**PS (probability + statistics):**
9. PS-1 Sample-space grid (draw events by click-drag, P(A), P(B), P(A∩B), P(A∪B) live; independence test = "B looks same whether or not conditioned on A")
10. ⭐ PS-2 Bayes tree + area-overlay + draggable-prior (reverse-the-tree on "given positive"; iterated Bayesian updating)
11. PS-3 PDF↔CDF sweep (vertical line traverses PDF, area accumulates to CDF below; inverse quantile click)
12. PS-4 CLT histogram convergence with **Cauchy counterexample** (most demos hide this — Cauchy doesn't converge no matter how large n; proves the *conditions* of CLT)
13. ⭐ PS-5 Hypothesis testing dual-distribution (null + alt overlaid, critical-line slider, α/β trade-off as balance beam; 100-replications mode reveals replication crisis)
14. PS-6 MLE likelihood landscape (drop-lines from data points to candidate distribution; landscape traced as θ swept; log-L vs L toggle preserves peak)
15. PS-7 Markov chain mixing (state diagram + distribution bar + trajectory ribbon; mixing-time green-when-within-ε)

**POO (OOP C++/Java):**
16. ⭐ POO-1 C++ vtable dispatch arrow path (`b->foo()` → pointer → object → vtable pointer → vtable → slot → function code, animated; toggle virtual/non-virtual = dispatch arrow changes) = **world-first**
17. POO-2 Polymorphism trace chain (call sequence with dispatch waterfall per call; override mismatch as missing-arrow)
18. POO-3 Memory layout (vertical address map: text/BSS/heap/gap/stack; stack frame cards, heap blocks, pointer arrows)
19. ⭐ POO-4 Smart pointer ref-count + cycle-detection (use_count/weak_count counters, shared_ptr copy = arrow + count++, cycle = both objects glow red as memory leak) = **world-first**
20. POO-5 Templates instantiation expansion (each `Vec<int>`, `Vec<double>` spawns concrete class card; Java type-erasure contrast)
21. POO-6 Inheritance-vs-composition combinatorial explosion (add "JetDuck" requirement → inheritance side balloons, composition side stays flat)
22. POO-7 Exception stack unwinding (upward sweep through frames, destructors visibly run on each, mutex locks visibly release; throw-from-destructor = std::terminate animation)

**ALO (linear algebra + discrete math):**
23. ALO-1 Vector spaces (basis vectors + scalar sliders; trace-toggle paints the span; drag basis vectors collinear → span collapses)
24. ⭐ ALO-2 Linear transformation grid-warp + non-commutativity composition animation (3Blue1Brown-style but **interactive**)
25. ⭐ ALO-3 Eigenvalue stretch (rays colored by angle deflection; eigenvectors = gold-flash zero-deflection; complex-eigenvalue spiral as visual proof of "no real eigenvector")
26. ALO-4 Determinant signed-volume scaling (unit square morphs; orientation flip animation for det<0; 3D mirror for negative det in R³)
27. ALO-5 Induction dominoes + contradiction tree + pigeonhole literal animation (proof structure externalized; strong induction = all-previous-boxes-arrow)
28. ALO-6 Eulerian + planar non-planarity struggle (Königsberg trace; drag vertices of K₅/K₃,₃, edge crossings counted live, minimum stays ≥1)
29. ALO-7 Pascal's triangle + binomial expansion + path-count interpretation + 3D tetrahedron generalization
30. ALO-8 Number theory clock + Euclidean GCD as geometric subtraction + Bezout's identity back-substitution

**SO+RC (operating systems + networks):**
31. SO-1 Process FSM with zombie state explicitly visualized (PCB lingers until parent wait())
32. SO-2 Scheduler Gantt (FCFS / SJF / SRTF / RR / Priority / MLFQ) with side-by-side comparison; MLFQ promotion/demotion animations
33. SO-3 Threads vs processes memory-sharing diff (shared heap, private stacks); race condition animator + mutex serialize; TLS slots
34. ⭐ SO-4 Page table walk + TLB hit/miss + page fault trace + COW after fork (page-table-entry COW animation = rare)
35. SO-5 Memory layout (POO-3 callback + OS perspective with mmap/munmap)
36. SO-6 Filesystem inode + hard/soft links + journal + `rm` interactive autopsy (link count decrement, data blocks marked free, recovery forensics)
37. SO-7 Permissions ugo×rwx grid with sticky bit on /tmp visualized
38. SO-8 IPC (pipes / shared memory / signals) with distributed-events timeline; signal-handler reentrancy bug animation
39. RC-1 OSI layered model packet encapsulation with **MTU fragmentation + reassembly** (rare)
40. RC-2 TCP 3-way handshake with **SYN-flood attack mode** + SYN cookies; **ISN randomization** = anti-spoofing
41. ⭐ RC-3 TCP cwnd over time (Tahoe/Reno/CUBIC/BBR variants) + **bufferbloat** (AQM = CoDel + FQ-CoDel flat-latency contrast) = **world-first at this fidelity**
42. RC-4 BGP route propagation + **BGP hijack visualization** (2008 Pakistan-YouTube incident) + RPKI defense
43. RC-5 DNS recursive resolution + DNSSEC chain + **Kaminsky cache poisoning live** + source-port-randomization defense
44. ⭐ RC-6 TLS 1.3 handshake + key derivation + **0-RTT replay attack** = **world-first**

⭐ = world-first OR best-in-class at this fidelity. No other tool ships these.

#### Library matrix (per-primitive recommended stack)

| Primitive | Library stack | Frame source |
|---|---|---|
| AlgoStepper (sorting/scan) | hand-SVG + `motion` mini `useAnimate` | `function*` generator yielding `{ arr, hl: [i,j], action }` |
| ProcessTree (OS) | `@visx/hierarchy` tree layout + custom SVG | `{ id, ppid, cmd }[]` |
| FilesystemTree | `@visx/hierarchy` + custom SVG | `{ path, type, children? }` |
| PermissionsGrid | hand-SVG 3×3 table | `{ user: 'rwx', group: 'r-x', other: 'r--' }` |
| BayesGrid | `@visx/shape` + hand SVG | `{ prior, sensitivity, specificity }` |
| MatrixTransform | `mafs` (color-overridden) | `{ matrix: [[a,b],[c,d]], vectors: [[x,y]] }` |
| DPTable | hand-SVG table + arrows | `function*` knapsack/LCS generator |
| RecursionTree | `@visx/hierarchy` (d3-hierarchy) | recursion-decorator pattern (instrumented fn) |
| UMLClass | `mermaid` static / hand SVG interactive | YAML class spec |
| PacketFlow | hand-SVG swimlane + `motion` | `function*` `{ from, to, label }` |
| Sparkline | hand-SVG path (Tufte purist) | `data: number[]` |
| SmallMultiples | CSS grid of Sparkline | `data: number[][]` |
| Recursion (PA-1) | RecursionTree + Python-Tutor-style call-stack cards | recursion-decorator generator |
| DP wasted-work (PA-3) | DPTable + RecursionTree side-by-side | dual generator (naive + memoized) |
| NP gadget (PA-7) | hand-SVG composition | bidirectional iff generator |
| C++ vtable (POO-1/4) | hand-SVG memory map + `motion` arrows | C++ AST + runtime trace generator |
| Linear transform (ALO-2/3) | `mafs` color-overridden | matrix + vector inputs |
| TCP cwnd (RC-3) | `@visx/shape` line + hand-SVG pipe | cwnd-over-time generator |
| Page table walk (SO-4) | hand-SVG 3-column grid | virtual address + page-table state |
| 3D wireframe (anywhere R³ helps) | `three` + `@react-three/fiber` + `@react-three/drei` lazy | `EdgesGeometry` + `LineBasicMaterial({color: 0x0a0a0a})` |
| Math eq interactive explore | MathJax 4 a11y/explorer (lazy on focus) | `<math>` MathML |
| Inline math in narrative | KaTeX (already shipped) | `\(...\)` |
| Source-controlled diagram (UML/ER/sequence) | Mermaid + themeVariables brutalist override | `.mmd` file |
| State machine (mode FSM / feedback ladder) | `xstate@5` + Visx Network for layout + custom SVG | xstate machine def |

#### Performance ceilings (hard guardrails)

| Layer | Hard ceiling | When to escalate |
|---|---|---|
| SVG | ~5000 DOM nodes before jank | switch to Counterpoint CMU canvas-first |
| Three.js wireframe | ~800 lines before perf issues with default LineBasicMaterial | use `LineSegments2`/`MeshLine` for batched rendering |
| Mermaid | hundreds of nodes OK, thousands slow | switch to custom React Flow |
| MathJax 4 | slower first-paint than KaTeX | lazy-load when surface contains `<math interactive>` |
| Plotly (deprecated) | ~999KB minified | DEPRECATE → `@visx/shape` |

#### ARIA recipe (V19 + V20 standard, every interactive viz)

```html
<svg role="img" aria-labelledby="title-id desc-id" focusable="true" tabindex="0">
  <title id="title-id">Bubble sort step 4 of 14</title>
  <desc id="desc-id">Comparing index 3 (value 42) with index 7 (value 19). Will swap.</desc>
  <!-- visual marks -->
</svg>
<div aria-live="polite" role="status" class="sr-only" data-testid="viz-narrator">
  Compared 42 and 19 at positions 3 and 7. Swap pending. Press space to advance.
</div>
```

WebAIM 2026 finding: pages with ARIA averaged 59 detectable errors vs 42 without. **Less ARIA is more. Native HTML first.** Brutalist UI naturally pushes this way — every viz is `<svg>` + `<button>` + `<dialog>`, not custom widgets.

#### Three.js wireframe recipe (when 3D R³ helps)

```typescript
const geometry = new THREE.BoxGeometry(1, 1, 1);
const edges = new THREE.EdgesGeometry(geometry, 1);  // 1° threshold
const lineMaterial = new THREE.LineBasicMaterial({ color: 0x0a0a0a });  // ink
const wireframe = new THREE.LineSegments(edges, lineMaterial);
scene.add(wireframe);
// bg: 0xf5f5f0 (paper); NO lighting; NO MeshStandardMaterial; NO shadows
```

Pair with `<OrbitControls enableDamping={false} />` (R6 hard edges → no smooth-camera). Discrete keyframe rotations triggered by ArrowLeft/Right (V2 + V17). Lazy-load via dynamic `import()` per route.

#### `function*` universal API contract

Every interactive algo viz takes:

```typescript
interface AlgoVizProps<Frame> {
  frames: Generator<Frame>;  // OR Frame[] precomputed
  initialStep?: number;
  onStep?: (idx: number) => void;
  predictionGates?: Map<number, PredictionGate>;
  liveRegionId: string;
}
```

Reusable `AlgoStepperShell.tsx` handles: scrub + predict-gate + ARIA narration + reduced-motion + keyboard parity + URL hash share-link. 12 primitives subclass Shell. New viz = new generator + new render function. No duplicated plumbing.

#### Lazy-load discipline

First-paint viz cost: ~125KB gzipped (motion + d3 subset + visx subset + mafs + katex).

Lazy via dynamic `import()` per route: Three.js + R3F + drei (~170KB), Mermaid (~250KB), MathJax 4 + a11y (~100KB), xstate (~25KB if used per-route).

Total cost only paid when surface needs it.

#### Brutalist-theme constants (`tutor-web/src/components/viz/theme.ts`)

```typescript
export const INK = '#0a0a0a';
export const PAPER = '#f5f5f0';
export const ACCENT = '#facc15';
export const STROKE_DEFAULT = 1;
export const STROKE_FOCUS = 2;
export const HATCH_LIGHT = 'url(#hatch-light)';
export const HATCH_DENSE = 'url(#hatch-dense)';
```

Every viz primitive imports from here. No hardcoded colors anywhere.

### 6.5 Interaction grammar (6 universal verbs)

Every interactive viz inherits these 6 verbs from `AlgoStepperShell`. Reuse breeds fluency:

1. **Scrub** — horizontal timeline at bottom; drag to any point; ←/→/J/K step keys; Space advance; Home/End first/last frame; speed slider
2. **Predict-then-reveal** — before any state transition flagged as a prediction-gate, "guess?" overlay accepts the student's answer; reveal compares with actual; gate blocks advance until prediction submitted (testing effect d=0.61)
3. **Manipulate parameters** — every numeric input draggable in-place (Tangle-style); sliders for continuous, scrubbers for discrete
4. **Counterfactual fork** — every algorithmic choice point offers "what if I'd picked the other?" forking a ghost-branch in parallel
5. **Layer toggle** — "show me the proof" overlays inductive invariant / loop invariant / proof structure; off by default, on by request
6. **Save state / share link** — URL hash encodes current parameter values; tutor can deep-link from a chat to "exactly this state of the viz"

Brutalist mono treats all 6 identically: black-bordered controls, yellow accent on hover/active, JetBrains Mono labels.

### 6.6 Feedback ladder (load-bearing)

| L | Trigger | Reveals |
|---|---|---|
| L0 | 1st submit | nothing (direction only) |
| L1 | 2nd wrong OR [hint] click | strategy hint |
| L2 | 3rd wrong OR 2nd hint | worked next-step assertion (1 of N) |
| L3 | 4th wrong OR 3rd hint | 2/3 worked example, final inference blanked |
| L4 | [give up] OR 5th wrong OR ≥10 attempts (Beck-Gong wheel-spin detector) | full + misconception ribbon + FSRS re-queue at low mastery |

Wheel-spin = ≥10 attempts on same KC without 3-in-a-row correct. Force L4 + FSRS add. "I GIVE UP" friction load-bearing (Bjork productive struggle). `gave_up: true` NOT counted as 4th-wrong.

### 6.7 Mode FSM (Normal / Crunch / Day-Of)

- **Normal** (≥7d to exam): full feature set, FSRS scheduling default, hints scaffolded, mock-exam optional
- **Crunch** (7d to exam): queue reorders by exam-weight × predicted-retention-gap, mock-exam recommended daily, retrieval-gate strictness ↑
- **Day-Of** (T-0 exam day): single-page surface, countdown (largest text), sleep-data callback, Jamieson stress reappraisal copy, 6-card review from week's misses, FII-specific checklist (ID/matricol, 2 blue pens, water, phone silenced, 4-7-8 breath × 3). **NO new content. NO streak. NO mastery %. NO cohort percentile. Subject Map / new lessons / new drills HIDDEN.**

Mode transition driven by `user_preferences.exam_dates` table. Exam dates locked from edit at T-14d (panic-reset protection).

### 6.8 Positioning claims (load-bearing for §1 Problem framing)

- **5 world-firsts on the visualization layer.** No existing tool ships these at the proposed fidelity: PA-7 NP-completeness reduction-gadget animator (bidirectional iff), POO-1 + POO-4 C++ vtable dispatch arrow-path + smart-pointer ref-count cycle visualization, PA-3 DP wasted-work overlay (naive-tree-with-color-coded-duplicates side-by-side), RC-3 TCP cwnd + bufferbloat (CUBIC/BBR + AQM), RC-6 TLS 1.3 + 0-RTT replay attack visualization.
- **First credible Romanian-language interactive CS pedagogy tool.** Research scan found no Romanian academic open-source CS-viz publishing tradition. Romanian academic CS leans textual/blackboard. No incumbent to imitate, no incumbent to beat.
- **Brutalist mono is a feature, not a tax.** Tufte recommends high-contrast monochrome with one sparingly-used accent in *Visual Display of Quantitative Information* (1983). For boxes-and-arrows pedagogy, ink + paper + yellow is SUPERIOR to color-coded; it forces every focal claim to earn its yellow.

## 7. ML stack (Python sidecar)

### 7.1 DSPy signatures (verbatim from research wiki §D)

```python
class RomanianTutor(dspy.Signature):
    """You are jarvis, a Socratic CS tutor for FII Iași CS students.
    Reply in Romanian with correct ț ș ă â î diacritics.
    Preserve EN technical terms (fork, thread, mutex, polymorphism, heap).
    Ask exactly ONE Socratic question. NEVER state the final answer."""
    student_question: str = dspy.InputField()
    kc_id: str = dspy.InputField()
    textbook_chunks: list[str] = dspy.InputField()
    student_history_summary: str = dspy.InputField()
    socratic_question: str = dspy.OutputField(desc="Romanian, ≤2 sentences, ONE question")
    mastery_estimate: float = dspy.OutputField()
    misconception_observed: str = dspy.OutputField()
    next_kc_suggestion: str = dspy.OutputField()
```

Plus: `MisconceptionDetector`, `WorkedExampleGenerator`, `GradingCritic`, `IdeaAgent`, `DeepSolve Planner+Solver+Writer`, `KCExtractFromPDF`, `MisconceptionExtract`, `PastPaperQuestion`, `PlacementMCQ`, `GlossRoToEn`, `RomanianCheck`. All return `confidence` field.

### 7.2 GEPA optimizer protocol (per-user personalization)

Each friend gets their own GEPA-tuned tutor program. Cold-start = shared optimized program. Steady-state = per-user fork of the program, retrained on that user's specific thumbs-up/down + wrong-intuition history. Storage: `dspy/compiled/{user_id}/program_v{n}.pkl`. Atomic symlink flip on new version.

Per-user pattern matters because: friends have different math/CS backgrounds (some did olympiad, some didn't); different misconception sets; different language register preference; different drill pace. Single shared program flattens that.

Cold-start: Alex hand-writes 20 `(student, gold_tutor_reply, rating, misconception_target, kc_id)` per subject × 5 = 100 examples. Then ActiveLLM-style bootstrap: DeepSeek V3 free zero-shot on 200 candidates → cluster by misconception (also free model) → 30 most diverse → Alex labels. Total 150 labeled. **This shared corpus seeds every user's initial program.** Subsequent per-user GEPA cycles retrain on that user's own thumbs feedback.

Steady-state cycle: thumbs-up/down + free-text "why bad?" appended to `data/labels/{subject}/{date}.jsonl` → aggregate → GEPA run → smoke eval gate before symlink flip.

**GEPA judge model ($0 spend, fully automated, Opus quality, routine-triggered):** Generator = DeepSeek V3 free (via OpenRouter). Judge = **Claude Opus 4.7 via `claude -p` headless invocation**, authenticated with Alex's existing ClaudeMax session. Cross-family bias avoidance preserved (Anthropic Opus vs DeepSeek). No paid API key required.

**Trigger model: routine-driven, not cron-driven.** Judge runs fire on workflow events, not calendar:

| Trigger | What fires | Why routine beats cron |
|---|---|---|
| GitHub Actions `push` to main | nightly smoke eval (~50 items) | runs only when code actually changes, not on every empty night |
| New row in `data/labels/{subject}/{date}.jsonl` crosses threshold (e.g. ≥10 new labels accumulated) | GEPA training cycle | runs when there's actual fresh feedback, not on a fixed Sunday whether or not anything's new |
| PR opened touching `eval/rubrics/*.yaml` or `prompts/*.dspy` | full 250-item eval suite | catches rubric/prompt regressions at review time |
| Deploy job success | post-deploy smoke against live URL | confirms prod still healthy |
| Curator promotes new misconception via swipe queue | mini-eval on the 3 KCs nearest the new misconception | catches localized regression cheaply |

Mechanism:
- `claude login` runs once interactively on the runner (GitHub Actions secrets-injected Claude session)
- Session token refreshes automatically while sub is active
- Triggered job invokes `claude -p "$(cat eval/rubrics/judge-prompt.txt)" < eval/batch.jsonl > eval/results.jsonl`
- Output JSON parsed back into pedagogy eval pipeline
- Same Opus 4.7 model used everywhere for judge-consistency

If `claude -p` rate-limits under Max plan limits, fallback chain: Opus 4.7 → DeepSeek-R1 free (same family as generator = degraded cross-family bias, but acceptable as backstop). Alert on fallback so Alex knows.

Pass threshold ≥0.85 weighted preserved (Opus quality justifies the original threshold).

Metric callable returns `dspy.Prediction(score, feedback)` — feedback textual for GEPA reflection LLM.

### 7.3 5-layer hallucination defense

1. **Citation grounding** (PaperQA2) — claims with no chunk support refused
2. **Regex leak check** — RO + EN patterns: `(răspunsul|rezultatul|soluția)\s+(este|e)`, Big-O leak, naked numeric `=`
3. **Generator-Critic cross-check** — **CROSS-FAMILY mandatory** (Generator DeepSeek V3 free, Critic Gemini 2.5 Flash free). 1.3× cost catches ~40% of agreement-failure cases.
4. **SymPy verifier** for math derivations (off-by-one, integer overflow caught; conceptual errors not caught)
5. **Confidence threshold + refusal** — "nu sunt sigur" suppress

### 7.4 Bilingual prompt strategy

System EN cached (Anthropic 4-cache-breakpoint pattern, 1.25× write / 0.10× read) + Glossary lock cached + Textbook chunks cached + User turn fresh. Exemplars Romanian with diacritics intact. **Static-prefix rule load-bearing:** anything dynamic before cache_control wipes everything after. Expected cache hit 70-85% at scale.

5 RO patterns: bilingual anchor + diacritic-repair post-hoc regex (`Ş→Ș, Ţ→Ț`) + term-glossary lock (`fork, thread, mutex — DO NOT translate`) + Socratic register markers (`"Hai să vedem"` warm vs `"Conform definiției"` cold) + code-switch acceptance (mirror `"thread-ul ăsta"` don't translate).

### 7.5 Voice (push-to-talk locked)

Push-to-talk only (privacy + battery + intent clarity). 3 modes: Feynman (explain out loud, gap-check) / Drill (voice math answers) / Podcast (5-min overview of today's queue).

STT: `gigant/whisper-medium-romanian` Q5 quantization (~10-14% WER vs Whisper large-v2 17-20%). Fallback: small-romanian Q5 (RAM-constrained) → OpenAI Whisper API (last resort).

TTS: Piper `ro_RO-mihai-medium` ONNX (60MB MIT) via Ktor + ffmpeg pipe. Audio cache key `sha256(voice_id|text|speed|model_version)`, 30-day TTL. Pre-generate canned phrases ("Bună întrebare", "Hai să gândim") at deploy.

LLM fallback chain: DeepSeek V3 free → Qwen 3 Coder 480B free → Gemini 2.5 Flash free → DeepSeek V4 paid (rate-limit cliff).

Mobile = text primary + voice opt-in per-turn.

### 7.6 RAG (PaperQA2 + MinerU 2.5)

Real materials: Lucanu/Craus PA Course 4 + PA Seminar 3 (Hiring / biased coin / majority) + Vidrașcu SO Lab 1 (man pages, hard links) + SO Seminar 1+2 + Frasinaru POO + past papers 2015-2024.

MinerU 2.5 (1.2B VLM, beats Gemini 2.5 Pro on OmniDocBench) extracts text/math/code/tables from PDFs → chunks → nomic-embed-text v1.5 → pgvector (768-dim, IVFFlat index).

PaperQA2 citation-grounded retrieval. Every claim carries `{repo, path, sha, page, paragraph}`. Citation accuracy target ≥92%.

### 7.7 SymPy + Matplotlib viz sidecar endpoint

`POST /api/v1/sidecar/math/plot { expression: str, x_range: [f, f], y_range?: [f, f], style: 'brutalist' }` → SVG response.

Use case: pre-rendered static plots for narrative content that doesn't need interactivity (decorative figures embedded in lesson prose, e.g., "here's f(x) = x²−2x+1 with its minimum highlighted"). Cheaper than rendering live in browser; cached aggressively (sha256 of input).

SymPy handles symbolic math (derivatives, integrals, expansions). Matplotlib with brutalist matplotlib-style applied (ink lines, paper background, no grid, no legend chrome). Pure server-side, no browser cost.

Distinct from `mafs`-based interactive viz which lives in browser for student manipulation.

## 8. Compliance (15 ship-ready docs)

Full content in research wiki §E. Index here:

| # | Doc | Location | Status |
|---|---|---|---|
| 1 | `PRIVACY.md.ro` (~2100 words) | `docs/compliance/PRIVACY.ro.md` | template ready |
| 2 | `PRIVACY.md.en` (~2000 words) | `docs/compliance/PRIVACY.en.md` | template ready |
| 3 | Cookie-banner decision | `docs/compliance/COOKIES.md` | **NO banner — only strictly necessary** |
| 4 | AI Literacy first-login (RO + EN) | `/welcome/ai-literacy/{ro,en}` | template + Kotlin gate code ready |
| 5 | `ANNEX_IV.md` (AI Act Article 11) | `docs/compliance/ANNEX_IV.md` | template ready |
| 6 | `RISK_REGISTER.md` (R-001 to R-015) | `docs/compliance/RISK_REGISTER.md` | template ready |
| 7 | `MODEL_CARD.md` (10 components) | `docs/compliance/MODEL_CARD.md` | template ready |
| 8 | DPA Register + Template | `docs/compliance/DPA_REGISTER.md` | template ready |
| 9 | Age verification flow | `signup.kt` + `consent_log` table | code ready |
| 10 | DSR UI + API (Art 15/17/18/22) | `/me` + `/api/v1/me/*` | spec ready |
| 11 | Breach notification template | `docs/compliance/BREACH_NOTIFICATION_TEMPLATE.md` | template ready |
| 12 | TIA-OpenRouter (Schrems II) | `docs/compliance/TIA-OpenRouter.md` | template ready |
| 13 | `EXCLUSIONS.md` (9 categories) | `docs/compliance/EXCLUSIONS.md` | template ready |
| 14 | Law 190/2018 addenda | `docs/compliance/LAW-190-2018-ADDENDA.md` | template ready |
| 15 | 5 code-level enforcement patterns | `migrations/` + `services/` | spec ready |

**Critical pre-launch:**
1. Switch to `https://eu.openrouter.ai` + enforce Zero Data Retention globally (email request to OpenRouter required)
2. Sign Hetzner DPA + Resend DPA + store at `docs/compliance/dpas/`
3. Wire AI literacy first-login gate before exposing any LLM endpoint
4. Enable RLS on every user-scoped table + cross-tenant CI test green
5. Calendar reminder 2026-08-17 for quarterly compliance review

**Hard bans (in `PROHIBITED_FEATURES.md`):** Emotion recognition (Article 5(1)(f) HARD BAN in education) · Biometric ID · CNP processing · Profiling for ads · Cohort percentile · Streak counter · Always-listening voice.

## 9. Multi-user (cohort layer)

### 9.1 Friend-group scope

Cohort = invited friend group (~10 people, hard-cap 12). NOT grupa-wide (~25). NOT anul-wide (~150-200). This is a personal tool Alex shares with friends from his year, not a class-wide rollout. Magic-link invite 7-day single-use email-bound token. 4-slide onboarding: AI literacy → ToS+privacy → profile → notification opt-ins.

Sizing implications throughout the spec:
- Hetzner CX22 (€8.32/mo) covers full population — no CX32 upgrade needed
- k=3 floor still applies — but at N=10, that means 3 of 10 = 30% engagement floor before aggregates render
- Cohort presence pill will frequently show "• —" (k unmet) — accept that; presence is a sometimes-on signal, not always-on
- Confusion-map trigger threshold rescaled (see §9.6)
- LiteLLM budget enforcement still per-user (no aggregate scaling concerns)

### 9.2 Permission matrix

| Action | Student | Curator | Admin (Alex) |
|---|---|---|---|
| Run drills / ask anon / answer | ✓ | ✓ | ✓ |
| Mark Q→misconception | — | ✓ | ✓ |
| Accept/reject KC edit | — | ✓ | ✓ |
| Schedule study session | — | ✓ | ✓ |
| See Q author identity | — | — | ✓ (audit-logged) |
| Promote student→curator | — | — | ✓ |
| Invite / remove cohort member | — | — | ✓ |
| Budget settings | — | — | ✓ |

### 9.3 k=3 server-enforced anonymity

Cohort presence pill, confusion-map, and study-session aggregates show data only when `count >= 3` for that segment. Server filters BEFORE shipping aggregates — client never sees raw counts of 1 or 2.

### 9.4 Q&A is an escape valve, not the pedagogy

**Design principle:** the pedagogy is supposed to be good enough that a stuck student rarely needs to ask another human. The tutor already has feedback ladder (§6.5), retrieval gate, misconception ribbon, cohort confusion-map auto-trigger (§9.6), and auto-detected misconception promotion (§9.7) to surface "you're stuck on X, here's the refutation for the wrong-intuition the system saw you take" before the student types a question.

Anonymous Q&A exists for the residual case — the system didn't detect the confusion, OR the student wants peer voice, OR the misconception is novel and not yet in the catalog. Q&A signal is captured back into the misconception-DB so over time the pedagogy absorbs each Q&A pattern and the escape-valve fires less. Net direction: Q&A volume per student declines as the misconception catalog grows. That's the success metric, not "more questions asked."

### 9.5 Anonymity contracts (different per surface)

- **Q&A asking**: anonymous-by-default (removes social cost of being seen as the slow one). Only Alex can lookup author on explicit click → audit-log row. NEVER surfaced to peers.
- **Study-session discussion**: AUTHOR-ATTRIBUTED (Liu et al. AOD research: social presence rises with attributed comments in small groups, falls with anonymous).
- **Confusion-map**: anonymous aggregate only, k=3 floor per window.

Different goals, different contracts. Asking-anonymous removes social cost; contributing-attributed enables relationship-building.

### 9.6 Cohort confusion-map trigger

`first_attempt_wrong_pct > 50% AND n_attempts >= 3` flags KC `cohort-blocked` in Alex's curator dashboard. At N≈10 cohort, the n_attempts floor drops from 5 → 3 (3 of 10 = 30% engagement is realistic; 5 would rarely trigger). 50% wrong-rate threshold stays. **Small-cohort statistics force these adjustments — the published 10% Caleon threshold is meaningless at N=10 because 10% = 1 person = below k=3 anonymity floor.**

### 9.7 Auto-detected misconception promotion

3+ classmates fail same KC same way (AST-normalized log) → auto-files draft misconception entry into Alex's queue. **Closed loop: spaced-rep feeds curation feeds spaced-rep, and shrinks the escape-valve Q&A volume over time.**

### 9.8 Peer code review (cheat-shielded, anti-spoiler frame)

Submit → automated grader (FINAL grade) → unlock "vezi soluții ale colegilor" → up to 3 AST-normalized peer solutions. Cheat-shield: peer view unlocks ONLY after own grade. Levenshtein anti-paste at submit (FII-GitHub + cohort window + textbook seeds; threshold 0.85; flag-NOT-punish — followup variant problem on same KC).

**Frame: anti-spoiler NOT anti-plagiarism** (collaboration normalized in FII; cognition-protection is right frame).

### 9.9 Cost model (sponsor + BYOK + chip-in)

Default: Alex covers $10 OpenRouter unlock with shared cohort budget. Power-user escape: Settings → "Adaugă cheia mea OpenRouter" routes own traffic through own free-tier. Optional: "contribuie la budget-ul cohortei" Stripe button (voluntary, NO leaderboard). LiteLLM per-user virtual key + budget enforcement. Transparency indicator: "buget cohortă: $7.20 / $10 folosit luna asta" (total only, NOT per-user).

### 9.10 Romanian culture locks

- Address register: **"tu" informal default** (student-to-student + student-to-Alex + tool-to-student). "Dumneavoastră" only if tool ever onboards an actual asistent/profesor.
- Role label: Alex = "asistent" in UI (cultural fit) / `curator` underlying RBAC name.
- **Restanță mode** (Sep retake): opt-in cohort temporarily shrinks to just retakers, fresh confusion-map generated.
- IRL Pizza-Hut-on-Copou study sessions irreplaceable — async sessions COMPLEMENT not COMPETE.

## 10. Notifications

Per-channel Resend Topics:

| Channel | Default | Granularity |
|---|---|---|
| Daily digest 08:00 RO | OPT-IN | all-or-nothing |
| Cohort confusion alert | OPT-OUT | per-subject |
| Q&A: answer-to-my-Q | OPT-IN | single switch |
| Q&A: new question in subject-I'm-strong-on | OPT-OUT | per-subject |
| Study-session reminder 2h-before-open + 4h-before-close-not-started | OPT-IN | per-session |
| Misconception flag (curators only) | OPT-OUT | all-or-nothing |

**Anti-nag locks:** No "haven't studied 3 days" reminder · No "falling behind cohort" frame · No re-send if not opened · No sleep-window pushes · No exam-day pushes except Day-Of surface auto-open · Hard cap 3×/day even if enabled · Info not exhortation copy · List-Unsubscribe header + 1-click footer.

## 11. Testing strategy ($0 annual)

| Layer | Tool | Count | Coverage gate | Runs |
|---|---|---|---|---|
| Unit Kotlin | JUnit5 + MockK + Kotest | 715 existing → 900 | 80% line | every push |
| Unit TS | Vitest + Testing-Library | 150 | 75% line / 100% `useState`-bearing | every push |
| Unit Python | pytest + pytest-cov | 80 | 80% line / 100% grading determinism | every push |
| Integration | Ktor TestApplication + Testcontainers + httpx-mock sidecar | 40-50 | all 9 surfaces × happy + 1 error | PR + nightly |
| E2E | Playwright `chromium-desktop` + `chromium-mobile` (Firefox/WebKit weekly) | 20-30 | all spec'd `data-testid` paint | PR + nightly |
| Visual regression | Lost Pixel + Playwright `toHaveScreenshot` | ~80 baselines | 0 unexplained pixel diff | PR (gates merge) |
| Pedagogy eval | OpenRouter `:free` DeepSeek-R1 LLM-as-judge | 250 items (5 agents × 50) | ≥85% rubric-passing | nightly |
| Load | k6 OSS (sustained/burst/break-point/soak) | 4 scenarios | p95 LLM <3s, p95 non-LLM <300ms, err <1% | weekly + pre-deploy |

**Playwright `e2e/fixtures/no-bad-network.ts`** auto-applied to every spec — fails soft on any 4xx/5xx (excluding auth-boundary 401s on `/whoami`). **Closes Slice 1.5 PDF-404 gap.**

**Config pin:** `locale: 'ro-RO'` + `timezoneId: 'Europe/Bucharest'` (catches diacritic bugs early).

**Brutalist rule sniff (Playwright assertion per surface):** R3 no blue (HSL hue 200-260° check) · R5 rectangles only (`border-radius > 4px` check) · R7 no shadow (`box-shadow` blur > 0 check) · R9 typography (font-family allowlist).

**N=1 A/B Thompson sampling — `N1Experiment.kt` ~80 LOC.** Marsaglia & Tsang Gamma → Beta → arg-max. **3-drill burn-in pins to most-data variant** for first 3 attempts on new KC (carry-over confound mitigation).

**17 concrete deliverables** (~3000 LOC = 250 Kotlin / 150 Python / 200 TS / rest YAML+SQL+scripts):

1. `docs/superpowers/templates/spec-template.md`
2. `docs/superpowers/templates/task-template.md`
3. `tutor-web/playwright.config.ts`
4. `tutor-web/e2e/fixtures/no-bad-network.ts`
5. `tutor-web/e2e/utils/snapshot.ts`
6. `eval/judge.py` + `eval/rubrics/*.yaml`
7. `.github/workflows/pedagogy-eval.yml`
8. `.github/workflows/deploy.yml`
9. `src/main/kotlin/jarvis/experiments/N1Experiment.kt`
10. `migrations/V42__variant_posterior.sql`, `V43__audit_log_retention.sql`
11. `tools/check-romanian.sh`
12. `src/main/kotlin/jarvis/i18n/RomanianNormalizer.kt`
13. `load/scenarios/{sustained,burst,break-point,soak}.js`
14. `/srv/jarvis/scripts/{blue-green-switch,rollback,backup}.sh`
15. `/srv/observability/docker-compose.yml` + dashboards
16. `.sops.yaml` + age key onboarding doc
17. `docs/runbooks/INCIDENTS.md` (one page per class)

## 12. Ops + deploy

### 12.1 Hosting (existing VPS — no new spend)

VPS already provisioned: `root@46.247.109.91`. Live at `https://corgflix.duckdns.org` (DuckDNS free dynamic-DNS, already in use). Jarvis Slice 1.5 currently runs here. Existing SQLite DB at `/root/.jarvis/tutor.db` migrates to Postgres on same box.

**No new hosting spend.** If the box turns out too small for peak ML load (Whisper + Piper + DSPy + MinerU simultaneously from multiple friends), upgrade to next Hetzner tier is the only cost lever — trigger = first observed OOM in Loki, not preemptive.

Observability stack budget: Prometheus ~150MB + Loki ~200MB + Grafana ~200MB + Promtail ~50MB + OTel collector ~100MB = ~700MB. App + sidecar split the rest of available RAM.

### 12.2 Deploy pipeline (GitHub Actions)

Build → Push GHCR → Migrate (SSH appleboy/ssh-action) → Blue-green switch via Caddy admin API on :2019 → Smoke `@critical` Playwright headless.

`/srv/jarvis/scripts/blue-green-switch.sh` — new container on alt port, /healthz wait 60s, Caddy load-config switch, 5s drain, stop old.

Rollback: `git describe --tags --abbrev=0 HEAD^` → blue-green-switch back.

**Hard rule: all migrations backwards-compatible for ≥1 version** (additive only; subtractive ships 1 release later).

### 12.3 Backup + secrets

**Backups to Alex's local device via Tailscale + rsync** (no paid Storage Box). Setup: Tailscale tailnet joins VPS + Alex's PC. Cron on VPS runs `pg_dump | gzip > /tmp/jarvis-{ts}.sql.gz`. Cron on Alex's PC pulls via `rsync -avz vps:/tmp/jarvis-*.sql.gz ~/jarvis-backups/`. Retention enforced on PC side (keep last 7 daily, last 4 weekly). Restic encryption optional at rsync layer.

Restore drill: pull latest dump → spin up local Postgres in Docker → load dump → schema-diff against HEAD migration. Confirm it boots.

**Secrets: SOPS + age** (no managed dependency). `secrets.enc.yaml` in repo, age key on VPS chmod 600 + Alex's laptop + printed paper backup. CI fetches `SOPS_AGE_KEY` GitHub secret at deploy.

### 12.4 Incident runbook (one page per class)

`docs/runbooks/INCIDENTS.md` — sidecar OOM / LLM 429 / DB connection exhaustion / RO Whisper failure / etc. Each = symptom + diagnosis + mitigation + root-cause.

### 12.5 Performance SLOs

Page TTFB→LCP p95 <1.5s · First-interactive <2s · LLM p95 <3s · Non-LLM p95 <300ms · Error rate <1% · Availability 99.0%/mo · Sidecar OOM 0/week.

### 12.6 Total NEW infra cost: €0

| Component | €/mo | Status |
|---|---|---|
| VPS (Hetzner, `46.247.109.91`) | already paid | **existing, no new spend** |
| Domain (`corgflix.duckdns.org`) | 0 | **DuckDNS free, existing** |
| TLS (Caddy + Let's Encrypt) | 0 | free |
| Backups (Tailscale + rsync to Alex's PC) | 0 | **uses your device** |
| Resend transactional email (3000/mo free) | 0 | free tier covers ~10 users |
| OpenRouter `:free` runtime models | 0 | banked "no paid LLM" rule |
| GEPA judge + nightly smoke (`claude -p` headless, Opus 4.7) | 0 | uses your ClaudeMax sub |
| **TOTAL NEW SPEND** | **€0** | nothing to subscribe to |

Only conditional spend = VPS upgrade to next Hetzner tier IF peak ML load OOMs. Reactive trigger only.

### 11.1 Extended deliverables (27 total: original 17 + 10 viz-stack additions)

In addition to the 17 testing/ops deliverables already listed, ship these from the viz research:

18. `tutor-web/src/components/viz/theme.ts` — INK/PAPER/ACCENT constants, hatching pattern factories, stroke widths
19. Brutalist-compliance retrofit pass on existing 5 viz components (`NumLineDirect`, `SumPlotTracker`, `SlopeCounter`, `SigmaStackedBar`, `CompareFrames`) — replace blue/amber/rounded-pill styling with theme.ts
20. `tutor-web/src/components/viz/AlgoStepperShell.tsx` — shared `function* frames` + `stepIdx` + `onStep` + a11y narration plumbing; 12 base primitives subclass
21. Add `motion@12.x` to package.json (mini variant 2.3KB) — explicitly the `motion` package, NOT deprecated `motion-one`
22. Add `d3` 5 submodules (`d3-scale`, `d3-shape`, `d3-hierarchy`, `d3-array`, `d3-format`)
23. Add `@visx/{hierarchy,network,shape,scale,group}@3.12.x`
24. Add `mafs@0.21.x` + brutalist-theme wrapper component
25. Lazy-load discipline: `three` + `@react-three/fiber` + `@react-three/drei` + `mermaid` + `@mathjax/src` via dynamic `import()` per route
26. Deprecate `plotly.js-dist-min@3.5.1` — reimplement `CompareFrames.tsx` over `@visx/shape`; ~1MB bundle save
27. Brutalist rule-sniff Playwright assertion per surface: R3 no blue (HSL hue 200°-260° check), R5 rectangles only (`border-radius > 4px` check), R7 no shadow (`box-shadow` blur > 0 check), R9 typography (font-family allowlist) — fails PR if drift

## 13. Content authoring (curator SPA)

Per research wiki §G — git-tracked YAML/JSON sources + headless Ktor admin API + brutalist React curator SPA at `/curator`.

**8-stage `/curate-tutor` pipeline:** MinerU 2.5 PDF extract → DSPy KC discovery → DSPy prereq edges → DSPy misconception mining → DSPy template generation → deterministic attribution → PaperQA2 groundedness verify → curator swipe approval.

**Output paths (git-tracked):** `content/{subject}/{kcs,misconceptions,templates,past_papers,placement,glossary,transcripts,labs,mock_exams}/`. One YAML per artifact. Mermaid `edges.mmd` auto-generated mirror of `edges.yaml` for git diffs.

**Curator UI surfaces:**
- KC editor (form + LLM suggestion sidecar + validation rules)
- Concept-graph editor — **React Flow** (HTML DOM nodes, `isValidConnection` + `getOutgoers` cycle prevention, Dagre TB layout)
- Misconception review queue — `react-tinder-card` swipe (right = git commit, left = `rejected_misconceptions.jsonl`)
- Past-paper review queue — same swipe pattern as misconceptions
- LLM bulk-import diff view (added green / removed red / modified amber)

**Validation rules:** cycle detection · orphan detection (≤8 hops to Tier-1) · exam-weight sum 1.0 ±0.02 · bilingual completeness · source attribution non-empty.

**Versioning:** optimistic locking on `version: N`, 409 on mismatch, atomic `git add + commit` per right-swipe.

**Architecture purpose = make Alex's role be approval, not creation.** LLM extracts drafts from real FII materials; Alex swipes accept/reject in a Tinder-shaped queue. Manual KC writing is too slow to ever reach the ~300 KC target across 5 subjects.

### 13.1 LLM-generated SVG illustrations (DSPy signature)

```python
class SvgDiagramGenerator(dspy.Signature):
    """Generate a brutalist-compliant SVG illustration for a specific
    lecture concept. Output MUST use only ink #0a0a0a, paper #f5f5f0,
    accent #facc15 — no gradients, no shadows, no rounded corners
    beyond 4px. SVG MUST include <title> and <desc> for ARIA. Pure
    90°/45° angles only (Vignelli grid). 1px stroke default, 2px focus.
    """
    description: str = dspy.InputField(desc="Romanian description of what to illustrate")
    kc_id: str = dspy.InputField()
    target_axiom: str = dspy.InputField(desc="dual_coding | contiguity | concrete_first")

    svg_code: str = dspy.OutputField(desc="self-contained SVG, no external CSS")
    title_ro: str = dspy.OutputField()
    desc_ro: str = dspy.OutputField()
    confidence: float = dspy.OutputField()
```

Use case: one-off illustrations for unique lecture concepts where no banked 12-primitive component fits. Sourced from 2026 SOTA: LLM4SVG, StarVector, Chat2SVG, arxiv:2503.07429. Curator UI swipe queue includes brutalist-compliance auto-check (regex no `#3b82f6`/`#f59e0b`, no `gradient`, no `box-shadow`, max border-radius 4px) before Alex reviews.

### 13.2 GeoGebra as curator-side authoring tool (NOT runtime embed)

Alex draws a transformation / construction in GeoGebra (free for students under EUPL v1.2), exports to SVG, ships static SVG into the lesson YAML. Do NOT embed GeoGebra iframe — chrome stays GeoGebra-blue regardless of theme variable scope, breaks brutalist compliance. Same reasoning rejects Desmos iframe.

## 14. Execution order (11 ship-gates)

11 ship-gates, each closing one or more pedagogy surfaces or infrastructure layers. Order is dependency-driven, NOT calendar-driven. Detailed task breakdown happens in plan doc (writing-plans skill next step).

| # | Gate | Surfaces / deliverables shipped |
|---|---|---|
| 1 | **Foundations** | §13 empty / §14 error / §17 bilingual toggle / brutalist R1-R10 + V1-V20 ruleset committed / `tutor-web/src/components/viz/theme.ts` shipped / existing 5 viz components retrofit to brutalist compliance / `AlgoStepperShell.tsx` shared utility / motion + d3 subset + visx subset + mafs added to package.json / Plotly deprecated (CompareFrames.tsx reimplemented over @visx/shape) / brutalist rule-sniff Playwright assertion green / RLS + cross-tenant CI test |
| 2 | **Compliance + auth** | Postgres + Auth.js v5 + Drizzle + Resend magic-link + AI literacy first-login gate + Settings/Me (§12) + 15 compliance docs in `docs/compliance/` |
| 3 | **Content authoring foundation** | `subjects.yaml` schema + DAG validator + Ktor curator routes + `/curate-tutor` skill stages 1-7 against PA chapter 1 |
| 4 | **Curator SPA** | KC editor + concept-graph editor + Mermaid mirror + misconception swipe queue + past-paper swipe queue |
| 5 | **Pedagogy core** | §1 feedback ladder + §2 hints + §6 calibration plot + sidecar bring-up (DSPy + PaperQA2 + MinerU) |
| 6 | **Drill engine** | FSRS-6 + PFA mastery + 5-phase Learning Rhythm + §10 placement primer + §5 wrap pane |
| 7 | **Multi-user cohort** | invite flow + RBAC + k=3 server-enforced + cohort presence + cohort confusion-map + study-session async + escape-valve Q&A |
| 8 | **Crunch + Day-Of modes** | mode FSM + §3 Day-Of mode + §9 mock-exam grading + Crunch-mode queue reorder + §18 batched notifications |
| 9 | **ML quality + RO voice** | GEPA cold-start (150 labeled) + 5-layer hallucination defense + pedagogy LLM-as-judge eval green ≥0.85 + Whisper-RO Q5 + Piper streaming + §11 voice mode (Feynman + drill + podcast overview) |
| 10 | **Lab + scratchpad + extras** | §7 Lab sandbox (V86 + xterm.js + step-grading) + §8 Scratchpad PHOTO + AI co-scratch + YouTube transcript ingestion + Archive UI + cross-cohort governance |
| 11 | **Acceptance + ops** | full SDD whole-branch Playwright headless gate green · k6 load gate green · pedagogy eval green · all 17 F-deliverables shipped · `/welcome/ai-literacy` gate enforced · Hetzner DPA signed · Resend DPA signed · OpenRouter EU endpoint + ZDR confirmed · multi-classmate invite-tested |

Everything ships. No deferred-to-v2 list. All 28 surfaces (§6.2), full ML stack (§7), full compliance scaffold (§8), full multi-user layer (§9), full ops stack (§12), full curator SPA (§13) land before acceptance gate.

## 15. Deprecation matrix (what survives jarvis-kotlin → redesign)

| Component | Status | Reason |
|---|---|---|
| Ktor backend (~200 .kt files) | **survives** | Path B keeps Kotlin owning HTTP + domain |
| ~715 backend tests | **survives** | Don't kill green tests for rewrite |
| Existing Auth.js v5 + Resend magic-link | **survives** | Already-shipped, GDPR-compliant |
| SQLite + Exposed | **MIGRATES to Postgres + Exposed** | Multi-user + RLS + pgvector requires Postgres |
| Existing `/api/v1/tutor` routes | **survives + extended** | Add /curator/* /me/* /cohort/* /admin/* |
| Existing memory/audit infra | **survives** | Banked Phase 2.5 working |
| Existing sensor/telemetry route (commit 6762e44) | **survives + extended** | Add Ledger drawer Art 14 UI |
| Slice 1.5 frontend (current) | **REWORKED** | Brutalist R1-R10 + V1-V20 compliance + remove PDF chrome + add 12 surfaces |
| `TutorWorkspace.tsx` (current) | **REWORKED** | `_pdfUrl` underscore-dead-prop removed; remount sub-components per Slice 1.5 lesson |
| Existing `KnowledgeGraph.kt` + `SubjectCorpus.kt` | **survives** | Already-shipped, curator UI builds on top |
| ResourceRail PDF drawer | **REWORKED** | PDF buttons removed per user feedback; content delivered as narrative |
| No FSRS scheduler | **NEW** | FSRS-6 in Python sidecar |
| No PFA mastery model | **NEW** | PFA in Python sidecar |
| No misconception catalog | **NEW** | Curator-built, git-tracked YAML |
| No multi-user / RLS | **NEW** | Postgres RLS + magic-link cohort invites |
| No AI literacy gate | **NEW** | Hard requirement Article 4 |
| No GDPR Art 15/17/18/22 surfaces | **NEW** | `/me/*` routes |
| No DSPy / GEPA / RAG / RO voice | **NEW** | Python sidecar primary purpose |
| No SDD spec/task templates | **NEW** | Closes Slice 1 + 1.5 ghost-component gaps |
| `plotly.js-dist-min@3.5.1` (~1MB, blue chrome) | **DEPRECATED** | Brutalist-incompatible default palette; replace with `@visx/shape` (~10KB); reimplement `CompareFrames.tsx` |
| Existing 5 viz components (`NumLineDirect`, `SumPlotTracker`, `SlopeCounter`, `SigmaStackedBar`, `CompareFrames`) | **REWORKED** | Currently use blue `#3b82f6` + amber `#f59e0b` + rounded pills — predate V1-V20; theme.ts retrofit required |
| Spec wording: "Motion One" | **CORRECTED** | npm-canonical name = `motion` (rebrand 2024); old `motion-one` deprecated — banked `gws` lesson class |
| 12 banked SVG primitives (AlgoStepper / ProcessTree / FilesystemTree / etc.) | **NEW (BUILD)** | Spec'd in §6.4 but not yet in `tutor-web/src/components/viz/` — clean slate |
| 36-viz roster (§6.4) including 5 world-firsts | **NEW (BUILD)** | PA-7 NP gadget, POO-1/4 vtable+smart-ptr, PA-3 DP wasted-work, RC-3 bufferbloat, RC-6 0-RTT replay — no incumbent ships these |
| First Romanian-language CS-viz tool | **NEW (positioning)** | Research found zero RO open-source CS-viz tradition; FII academic register is textual/blackboard |

## 15.1 Forward-compat note (jarvis as multi-subsystem personal OS)

Tutor is the first subsystem in a planned jarvis personal-OS (future subsystems: dietician, finance advisor, others TBD). Architecture choices in this spec aim to NOT box out the platform direction:

- **Auth.js v5 + Drizzle** owns users + sessions globally — every subsystem reuses same identity layer
- **Postgres + RLS** policies use `app.current_user_id` setting that all subsystems can share
- **Per-subsystem schema separation** anticipated — tutor lives in `tutor.*` schema (or DB-tagged tables), dietician will live in `dietician.*`, finance in `finance.*` — RLS enforces both per-user AND per-subsystem-consent
- **Compliance scaffold** (audit log, consent records, DSR routes) designed cross-subsystem: `audit_log` carries `subsystem` column, `consent_log` carries `subsystem` + `consent_type` together, `/me/export.json` aggregates across subsystems user has consented to
- **AI literacy gate** versioned per subsystem (`tutor-v1.0`, `dietician-v1.0`) — confirmation in one subsystem doesn't auto-grant another

What this means for tutor v1: tables prefixed/scoped from day 1 (`tutor_kcs`, `tutor_attempts`, etc.) rather than bare names. Costs ~nothing now, saves a migration later.

What's explicitly OUT of this spec: dietician / finance / other subsystems' designs. Those are separate brainstorms.

## 16. Open questions + risks

1. **OpenRouter EU enterprise endpoint request** — requires email to OpenRouter. If not granted before Gate 2, fallback = continue with default US routing + SCCs + DPF until response. Risk: Schrems III invalidation would force migration to Mistral La Plateforme EU.

2. **KC authoring velocity** — target ~300 KCs across 5 subjects assumes LLM extraction quality high on Romanian textbooks. If extraction quality is bad on a given subject, manual cleanup may bottleneck. Mitigation: surface curator extraction-quality metric per subject; iterate prompt on the worst-performing subject before mass extraction.

3. **GEPA cold-start labeling cost** — 100 hand-labeled tutor-reply examples is non-trivial work for solo dev mid-finals. Mitigation: accept lower judge quality (pass threshold ≥0.75 not 0.85) until enough labels banked; tighten threshold once steady-state thumbs-up/down feedback accumulates.

4. **Multi-user testing before classmates trust the tool** — invite test in Gate 10 might surface bugs Alex hasn't seen as single user. Mitigation: invite 1-2 trusted classmates during Gate 7 for early signal.

5. **CX22 RAM under peak ML load** — sidecar (Whisper + Piper + DSPy + MinerU) + Postgres + observability stack might OOM if multiple friends hit voice + RAG + drill simultaneously. Mitigation: CX32 upgrade trigger at first observed OOM event in Loki.

6. **Romanian legal review** — short paid review with someone who's done ANSPDCP work would catch local-law gotchas. Mitigation: schedule before multi-user gate ships.

7. **Slice 1.5 PDF-404 lesson regression risk** — every new component must pass `no-bad-network.ts` + interaction-smoke gate. If skipped, ghost-component / network-error class returns.

8. **Brutalist rule drift under time pressure** — under deadline, easy to slip blue accent / shadow / gradient. Mitigation: brutalist rule sniff Playwright assertion runs on every PR.

9. **Misconception-DB consent at promotion time** — Art 17(3)(d) research carve-out only clean if consent collected at onboarding. Mitigation: explicit consent slide in onboarding (Gate 7).

10. **Day-Of mode triggering accidentally** — `exam_dates` table edit lock at T-14d protects panic-reset, but bug in mode FSM could trigger Day-Of on wrong date. Mitigation: unit test mode FSM with 30+ date permutations.

## 17. Spec-grep-gate audit (per load-bearing rule)

Per the Slice 1 ghost-component lesson + spec-first clarification rule, this spec was synthesized from 9 rounds of research with 8 parallel subagents in Round 9. Clarifying questions to user during Rounds 1-9 totaled ~15, of which the following hindsight-grep would have answered:

- "Should I use Postgres or stay on SQLite?" → answerable from `TutorDb.kt` grep (SQLite + Exposed, not Postgres as initially assumed). Wasted 1 question round.
- "Should we go full Python rewrite or hybrid?" → answerable from council (eventually consulted). Wasted 1 question round before council dispatched.

Future spec/plan transitions: grep existing artifacts BEFORE asking. The 2026-05-17 finding is banked in BRIDGE.md handoff.

## 18. Visual acceptance criterion summary (per Slice 1 lesson)

Every slice in §14 ships with explicit `data-testid` paint checklist + interaction-smoke checklist. SDD final-review Playwright gate against live URL asserts:

1. All spec'd `data-testid` selectors visible on first paint
2. ZERO 4xx/5xx network responses during first paint (per `e2e/fixtures/no-bad-network.ts`)
3. Click every spec-listed interactive element
4. After each click: no on-screen text matches `/404|HTTP \d{3}|not found|error/i` AND no new 4xx/5xx fired

If any of (1)-(4) fail, the slice is NOT shipped. Same severity as failing (1). **Closes Slice 1.5 PDF-404 lesson gap.**

## 19. References

- Research wiki: `docs/superpowers/research/2026-05-17-novice-pedagogy-tutor-redesign-research.md` (3500+ lines, 9 rounds, 8-subagent Round 9 final)
- Council verdict: `.claude/council-cache/council-1779022799-backend-path-decision.md` (Path B hybrid)
- Visual mockups (banked, brainstorming session 1071296-1779023552):
  - `content/v4-brutalist-novice-arc.html` — first brutalist novice entry + term-landing mockup
  - `content/v5-full-system-board.html` — 9-surface system board
  - `content/v6-real-content-drilldown.html` — 10-surface real-content mockup (Vidrașcu SO Lab #1 + Lucanu/Craus PA Course 4)
- Existing system docs:
  - `wiki/architecture/Pedagogy Playbook.md` (Alex's own 9-technique pedagogy with d-values)
  - `Desktop/SO/os-study-guide/wiki/architecture/UX Playbook.md` (28 heuristics)
  - `Desktop/SO/os-study-guide/wiki/architecture/Design Principles.md`
- BRIDGE.md: `~/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md`
- Pedagogy + ML + compliance bibliography: ~120 external sources cited inline in research wiki across Rounds 1-9
