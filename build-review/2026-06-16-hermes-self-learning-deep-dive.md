# Hermes "self-learning" — deep-dive verdict (2026-06-16, research-agent, web-cited)

Triggered by Alex: "can we / should we integrate some system like Hermes has for self learning?" Researched live (web), grounded with sources.

## What "Hermes self-learning" most likely is (ranked)
1. **(most likely) Nous Research "Hermes Agent"** — open-source self-improving agent (released 2026-02-25, v0.9.0). Architecture is uncannily close to OURS: SQLite + FTS5 memory, agent-curated `MEMORY.md`/`USER.md`, model-agnostic via OpenRouter, single-user-by-default. Repo: github.com/nousresearch/hermes-agent. **Our `/wrap` + `BRIDGE-HEAD` + `content/` corpus IS this pattern, already applied to the DEV (me).**
2. **Nous "Hermes" model family (2/3/4)** — open-weight Llama fine-tunes w/ a synthetic-data flywheel (DataForge DAG → Atropos 1000+ verifiers → rejection-sample → fine-tune). This is *weight* self-improvement = **GPU + paid + big-corpus = INFEASIBLE for us**.
3. General self-improving-LLM class (self-rewarding LMs, RLAIF, Voyager skill libs, reflection loops). Hermes Agent is the productized no-fine-tuning member.
   - Bridge repo: `hermes-agent-self-evolution` — DSPy + GEPA prompt/skill optimization, **no GPU, API-only**, human-gated.

## The key distinction
- **Self-learning the MODEL** (weights / fine-tune) → NOT feasible for us (no GPU, no paid APIs, one learner). Hard no.
- **Self-learning the SYSTEM** (memory + skills + FTS5 retrieval + gated prompt-evolution; "backpropagation for prompts, not weights") → feasible, and we already have half of it.

## FIT verdict for jarvis-kotlin
- Model flywheel: **NO.**
- Self-rewarding / self-play: **NO** — needs a verifiable reward or data volume we lack; the self-rewarding variant IS the exact failure mode to avoid.
- System-level loop: **partial fit, half-built already** — but weaker here than in Hermes' native domain, because a tutor's core signal ("did this teach correctly?") is NOT cheaply observable from one student.

## Safe integration options (system-level only; signal must be EXTERNALLY GROUNDED, never model-self-judged) — all POST-DEPLOY (need Alex actually using it)
1. **Learner-model memory + retrieval (do first).** Persist Alex's wrong answers / recurring misconceptions / which figure styles landed → lesson-gen retrieves it, adapts what to teach next + framing. Signal = his real PREDICT/ATTEMPT/CHECK outcomes + 828-card FSRS history + checkpoint approve/reject. Risk LOW (steers what/when, not truth). Free.
2. **Offline human-gated template/prompt evolution (GEPA/DSPy).** Treat lesson-gen prompts / figure templates / rubrics as "skills"; periodically generate variants offline, run vs a frozen eval set of past lectures, surface diffs FOR ALEX'S APPROVAL. Verifiers = our existing gates. Risk MEDIUM, contained by human + CI gates. Never auto-commit.
3. **FSRS-driven scheduling adaptation (maybe 80% there).** The one mathematically-safe self-adapting loop (FSRS is n=1 by design). Feed lesson-beat outcomes, not just card reviews, into it. Risk VERY LOW. Free.
- **DO NOT BUILD:** any loop where the model generates a lesson, grades its own lesson, and feeds that grade back unsupervised.

## The single biggest RISK
**Silent self-amplification of wrong teaching** ("data autophagy" → entropy decay + variance amplification → model collapse, applied to content). For a truth-first tutor whose one student CANNOT vet content ([[user_subject_knowledge_gap]]), a loop that mistakes a confident hallucination for a "successful skill" reinforces + re-teaches it, undetectably. Mitigation = the architecture we already favor: every self-learning signal externally grounded (checkpoint approve/reject, `lecture_grounded`/`source_span_hash`, or a deterministic gate). Safe ONLY for selection/sequencing/scheduling (opts 1+3) or human-gated prompt evolution (opt 2).

## Bottom line for the plan
Hermes self-learning does NOT change the near-term path. It is a POST-DEPLOY enhancement that needs the deployed tutor + real usage to have a signal. It aligns with the master-plan Phase-C residual (#1: no machine proves teaching) + the already-DEMOTED post-deploy empirical-feedback loop. The ONE thing to carry NOW: it confirms Alex's "visual-overseer" must be GROUNDED (against the source / deterministic), never the model grading itself.
