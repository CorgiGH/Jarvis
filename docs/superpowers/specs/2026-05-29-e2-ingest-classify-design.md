# E2 — Ingest + Classify — Design

**Date:** 2026-05-29
**Status:** Approved design, revision 2 (brainstorming complete; council-reviewed 3 rounds — rounds 1-2 on the design, round 3 on this written spec; `.claude/council-cache/council-1780049092.md`). Revision 2 folds in the 6 round-3 spec gaps.
**Roadmap position:** E1 grader (done) → **E2 ingest+classify** → E3 generate+route → E4 study loop. Direction source: `memory/project_reorientation_teaching_engine.md`.

## 1. Goal

Turn a subject's raw lecture PDFs + past exams into a **trustworthy, git-tracked KC corpus**: grounded knowledge components + prerequisite edges + past-exam problems linked to KCs (`kcIds`) with embedded rubrics. The signal this produces must be trustworthy enough to drive E1's grader and mastery store — *"no un-cited claim reaches a student"* ("I don't know" > confident-wrong).

**General pipeline, per-subject output.** One machine, runs on all 5 subjects (PA, PS, POO, SO, ALO). PA runs **first** because its lecture material + past exams are ready — proving ground, not scope limit. No deferral of breadth.

## 2. Non-goals (explicit)

- Video prerequisite-aware nodes → **E3** (explanation routing).
- `viz_id` / `vizId→component` registry → **E3**.
- Replacing EWMA mastery with BKT/PFA — EWMA stays (transparent stand-in; documented limitation: no guess/slip term).
- The paid Kotlin `OpenRouterVisionLlm` class — **untouched and unused** by E2. Vision grounding is done by Claude in-session.
- A rigid "shape taxonomy as KC identity" — see §6, shape is a descriptive drill attribute, not the grader's source of truth.

## 3. Architecture — Hybrid

| Layer | Does | Why here |
|---|---|---|
| **Kotlin (mechanical, deterministic)** | PDF→text via `pdftotext`/PDFBox; commit the source-of-record extraction with page + char-offset anchoring + provenance; extract exam problems; the re-pointed grounding validator; schema; persistence. | Reproducible, testable, no model trust required. |
| **Claude in-session (judgment)** | KC discovery; prereq edges; assign `kcIds` to exam problems; extract rubrics from answer-keys; **vision-confirm** extraction vs the rendered PDF page on dirty/math pages. Runs through the hardened `curate-tutor` skill. | Reasoning the machine can't verify. Reads rendered pages on the Claude Max 20x subscription — **free, frontier-model, real-PDF, no raster code, no paid API**. |

**Why in-session vision (council R2 resolution):** the vision read is *not* the Kotlin `VisionLlm` class (which defaults to paid OpenRouter `claude-3.5-sonnet`). It is Claude's own Read tool rendering the PDF page as an image. That makes the second read (a) free on the flat 20x sub, (b) a read of the *actual rendered page* — the real-PDF comparison that breaks circular grounding at authoring time, (c) **not** two correlated OCR pipelines (read-1 is deterministic text-layer; read-2 is a frontier model judging the rendered page). Trade-off accepted: it is **per-page manual**, so it is an authoring-time / ad-hoc-fallback step, not a hands-off batch stage.

## 4. The grounding fix (the load-bearing change)

**Today's hole (verified live):** `ContentValidator.checkVerbatimSources` checks each KC `source.quote` as a substring of `_sources/{doc}.md` — *machine-extracted text*, not the PDF. Garbled extraction ships green; absent source degrades to a warning. Live example: `content/PA/kcs/pa-kc-001.yaml:13` ships the garbled quote *"It does not exists a standard definition…"* green today because that exact garble sits in `_sources/pa-lecture-01.md`.

**Why "two machine reads + diff" does NOT fix it (council, unanimous):** diffing two machine reads against each other never compares against the PDF; correlated errors agree and ship green — strictly worse (manufactures false confidence). Rejected.

**The fix — four parts:**

1. **Source-of-record = committed deterministic extraction.** `_sources/{doc}.md` becomes the *committed `pdftotext` output* (NOT an LLM paraphrase), reproducible from the PDF, inspectable in git, carrying page boundaries and a provenance tag per span (`pdftotext` | `vision-confirmed`).
2. **One human/Claude-vs-PDF confirmation breaks circularity.** At authoring time, Claude reads the *rendered* PDF page (in-session, free) and confirms the committed extraction matches the page — especially formula/algorithm spans. Confirmed spans are tagged `vision-confirmed`. This is the single comparison against ground truth; everything downstream checks against the now-trusted extraction.
3. **Span-anchored, diacritic-safe matching.** Each KC `source` carries `page` + `span:{start,end}` (offsets into the committed extraction). The validator uses diacritic-**insensitive** matching only as a *candidate finder*, then requires a diacritic-**EXACT** match at the cited span. (Romanian diacritic-collapse — `și/si`, `fața/fata`, `pește/peste` — otherwise matches the wrong span and grounds a false claim.)
   - **Offset semantics (round-3 gap #4):** `span:{start,end}` index the **raw** committed extraction (NOT whitespace-normalized). The candidate-find step may use the existing `normalizeWs` for fuzzy location; the confirm step extracts `rawText[start..end]` and requires it to equal the stored quote diacritic-exactly. The validator must therefore stop relying solely on `normalizeWs(text).contains(...)` (`ContentValidator.kt:176`) and add raw-offset extraction + exact compare. A KC whose stored quote does not equal its raw `(page, span)` slice → **ERROR**.
4. **Severity by tier.** Formula/algorithm-tier KC with absent **or** non-`vision-confirmed` source → **ERROR** (excluded, not shipped). Other tiers, absent source → warning. No grounded span → no KC.

**Residual, named honestly (sharpened by round 3):** the source-of-record is still *some* extraction (we cannot diff against PDF bytes directly). Non-circularity comes from (a) it being deterministic `pdftotext`, not an LLM re-rendering; (b) the recorded one-time vision confirmation vs the rendered page; (c) span+page anchoring making every claim re-checkable.

**What the CI gate provably enforces:** the quote *exists verbatim at its cited span* in deterministic extraction (citation-fidelity).

**What it CANNOT enforce (accepted frontier — Alex cannot vet content; the only competent reviewer is Claude in-session):** (i) that the quote actually *entails* the KC claim (no NLI/attribution layer — Domain Expert); (ii) that a verbatim quote is attached to the *correct* KC vs cherry-picked out of context (Risk Analyst); (iii) that a `vision-confirmed` tag corresponds to a vision read that actually happened (it is an author-asserted string). These are bounded, not solved.

**Anti-laundering rule (round-3 gap #6):** the §5 migration MUST re-ground each existing quote by re-reading the rendered page — it MUST NOT compute offsets onto an already-shipped (possibly garbled, e.g. `pa-kc-001`) quote and stamp `vision-confirmed`. Stamping without a real read launders existing garbage into "grounded." The `pa-kc-001` garble must either survive a genuine rendered-page re-read or be corrected/excluded.

## 5. Schema changes (all additive)

**`ContentSchema.SourceRef`:** `{doc, quote}` → `{doc, quote, page: Int, span: {start: Int, end: Int}, provenance: "pdftotext" | "vision-confirmed"}`. New fields take Kotlin defaults so the type stays load-compatible — but see the migration warning below: defaults make code compile, they do NOT make the existing corpus pass the re-pointed validator.

**`_sources/{doc}.md` is REPLACED, not appended (round-3 gap #3 — destructive, not mechanical).** The current `content/PA/_sources/pa-lecture-01.md` is *hand-curated markdown* (attribution block, `## Outline`, cleaned headings), NOT `pdftotext` output. Swapping in raw `pdftotext` (a) changes every character offset, and (b) may mean an existing quote no longer appears verbatim (the curated text was cleaned). So migrating the 6 PA KCs + 2 misconceptions is a **manual re-grounding** (re-read the rendered page per the anti-laundering rule, relocate or correct each quote, record real `page`/`span`/`provenance`), NOT a "computed backfill." Scope is small (8 entries) but human, not mechanical.

**`PdfProblemExtractor.Problem`:** add `kcIds: List<String>` (plural — a problem touches multiple KCs) and `rubric` (point-split items extracted from the exam answer-key; reuses E1's rubric shape). `shape: String?` optional descriptive label (see §6).

**No change to `KnowledgeConcept` for shape** — shape is *not* a KC-defining attribute.

## 6. Classify — shape lives on the drill, not the KC

The grade signal already forks on **"does a canonical answer exist?"** — E1's `GradeScoring.answerMatches()` handles exact/numeric (computational); everything else goes to the rubric path. That fork, not a taxonomy, decides the grader path.

So `shape` (computational / proof-derivation / design-implement / analysis-trace / fact-conceptual) is a **descriptive attribute on the problem/drill**: it informs drill *format* and UI, and is a hint, never the grader's source of truth. A **subject shape profile** (which shapes dominate) is descriptive metadata only.

`kcIds` assignment + prereq edges remain Claude-in-session judgment; each must carry a grounded source span subject to the §4 gate.

## 7. Exam ingest + problem→KC linkage (wakes the dormant loop)

Today `Problem` has no `kcId`, `/reprep` writes `drillsJson="{}"`, and `task.conceptRefs` point at archival paths — so drill→grade→mastery is dormant (BRIDGE-HEAD open item).

E2: ingest past-exam PDFs + answer-keys → extract problems → Claude assigns `kcIds` by matching each statement against the subject's KCs, and extracts the point-split rubric from the answer-key → persist `kcIds` + `rubric` on the Problem.

### 7.1 Grade-route rewiring — the load-bearing wiring (round-3 gap #1, biggest)

The round-3 review found the spec asserted "server-side, no client trust" without specifying the wiring. Verified current state:
- `TutorRoutes.kt:1850-1852` records mastery from **client-supplied** `req.conceptIds` — the client names which KCs to credit.
- `TutorRoutes.kt:1842` reads **client-supplied** `req.canonicalAnswer` for the deterministic exact-match.
- `Problem`s are persisted as a JSON blob in `task_prep.problemsJson` (no queryable column), and the grade route does NOT load them.

**Required wiring (must be in the plan, or the loop cannot become client-untrusted):**
1. At grade time, the server resolves the Problem by `(taskId, problemId)` — load `task_prep.problemsJson`, find the matching `Problem`, read its `kcIds` + `rubric` + canonical answer **server-side**.
2. Record mastery on the loaded `Problem.kcIds` via `KcMasteryRepo` — **replace** the `req.conceptIds` path (`:1852`). The client no longer names the KCs.
3. The deterministic exact-match uses the Problem's stored canonical answer, **not** `req.canonicalAnswer` (`:1842`). Client-supplied canonical answer is ignored for the recorded signal.
4. Client `conceptIds`/`canonicalAnswer` may remain accepted for backward-compat of the response shape, but MUST NOT drive `recorded` mastery.

This is the change that actually lights up the loop E1 built; everything else in E2 feeds it.

## 8. Data flow

```
PDF ──[Kotlin] pdftotext──▶ committed source-of-record (page/offset, provenance)
                                   │
        [Claude in-session, free vision on dirty/math pages]
        confirm extraction vs RENDERED page · discover KCs + spans · prereq edges
        ingest exam problems → kcIds + rubric
                                   │
        ──[Kotlin] write YAML corpus + Problem records──▶
                                   │
        ──[Kotlin] ContentValidator (re-pointed: span-anchored, diacritic-exact
           confirm, tier severity, provenance) in `gradle check`──▶
                                   │
        drill ─▶ grade (E1) ─▶ mastery (E1 KcMasteryRepo) ── loop lit
```

## 9. Error handling / trust rules

- Formula/algorithm KC without a `vision-confirmed` grounded span → **excluded** (not shipped).
- KC quote not diacritic-exact at its cited `(page, span)` → **ERROR**.
- `Problem.kcIds` entry not resolving to a real KC → **ERROR** (prevents mastery credited to a *phantom* KC). NOTE (round-3 gap #6, Risk Analyst): CI can enforce *resolvability* only — a resolvable-but-**wrong** link still mis-credits mastery → wrong FSRS scheduling. This sits in the accepted un-vettable frontier (§4). Mitigation, not a guarantee: the in-session `kcIds` assignment is made by Claude reading both the problem statement and the candidate KCs, and is the same judgment that authored the KCs.
- Vision grounding never depends on the paid OpenRouter vision class or its rate limits — it is Claude in-session.

## 10. Testing / acceptance

- **Validator unit tests:** span-anchored exact match passes; diacritic-collapse wrong-span match FAILS (`și/si` case); absent formula-tier source → ERROR; `vision-confirmed` provenance honored; missing/garbled span → ERROR.
- **`Problem.kcIds` round-trip (integration):** grade a drill on a kcId-linked problem → mastery records on the correct KC in `KcMasteryRepo`. This is the proof the dormant loop wakes.
- **PA end-to-end:** run the pipeline on PA lectures + a real past exam → corpus validates green under `gradle check` → drill a real PA exam problem → graded by E1 → mastery moves on the right KC.
- **Feature-shipped gate (CLAUDE.md rule):** do not claim shipped on green tests alone — actually drill one PA problem and observe mastery move on the user-facing surface.
- **Green-baseline breaks intentionally during migration (round-3 gap #5).** "Additive schema defaults" keeps code compiling, but the moment the re-pointed validator + formula-tier-ERROR lands (§12 step 3), the existing PA corpus FAILS `gradle check` until the 6 KCs + 2 misconceptions are re-grounded (§5 migration) — defaulted `provenance` is not `vision-confirmed`. So the validator-tighten and the PA re-grounding must land **together** (one plan phase), or the tightening goes behind a flag flipped on at the end of that phase. The 827/0 baseline is restored once PA is re-grounded; it does NOT hold continuously through the migration. Do not claim "stays green throughout."

## 11. Reuse / touched files

Reuses: `PdfProblemExtractor`, `ContentValidator`/`ContentSchema`/`ContentRepo`/`ContentCli`, `KcMasteryRepo` + `GradeScoring` (E1), the `curate-tutor` skill, `TutorRoutes` grade route.

**Honest effort split (round-3 gap #2 — corrected):**
- *Near-zero new code (in-session Claude):* the vision read / KC discovery / `kcIds` assignment / prereq edges — done in-session, no Kotlin.
- *The single LARGEST net-new mechanical piece:* the **span/page/provenance source-of-record extractor.** `pdftotext` IS available (verified v4.00 at `C:\tools\poppler` + `mingw64/bin`), but both `pdftotext` and PDFBox `PDFTextStripper` emit **plain text with no char-offset/page-boundary API** — so producing `{page, span:{start,end}}` needs new per-page extraction + cumulative-offset accounting (e.g. `pdftotext -f N -l N` per page, or PDFBox per-page stripping). Do NOT scope this as trivial.
- *Moderate:* validator re-point (raw-offset extraction + exact compare, candidate-find, tier severity), the `SourceRef`/`Problem` schema extensions, the §7.1 grade-route rewiring (load Problem server-side, drop client `conceptIds`/`canonicalAnswer` from the recorded path), the PA re-grounding migration (manual, 8 entries).

## 12. Build sequencing (dependency order, no time estimates)

1. Schema extensions (`SourceRef` page/span/provenance; `Problem.kcIds`+`rubric`) — additive defaults, suite stays green.
2. Source-of-record extractor (Kotlin, per-page + offset accounting + provenance) — the largest mechanical piece (§11).
3. **Validator re-point + PA re-grounding land TOGETHER as one phase** (§10): re-point the validator (raw-offset extraction, diacritic-exact span confirm, tier severity) AND re-ground the 6 PA KCs + 2 misconceptions (manual, anti-laundering re-read) in the same phase — or gate the tightening behind a flag flipped on at phase end — so `gradle check` is not left red. Restores 827/0.
4. Grade-route rewiring (§7.1): server loads Problem by `(taskId, problemId)`, records mastery on `Problem.kcIds`, drops client `conceptIds`/`canonicalAnswer` from the recorded path → wakes the loop.
5. Hardened `curate-tutor` authoring flow (KC discovery + vision-confirm + exam ingest → `kcIds`/rubric).
6. Run PA end-to-end through the machine; validate; **drill→grade→mastery proof on the live surface** (feature-shipped gate, §10).
7. Run the same machine on PS / POO / SO / ALO.
