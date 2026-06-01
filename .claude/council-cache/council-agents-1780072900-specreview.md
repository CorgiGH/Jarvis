# Council — E3 spec review (rev 1 → rev 2)

_2026-05-29 · adversarial review of `docs/superpowers/specs/2026-05-29-e3-generate-route-design.md` rev 1. 5 reviewers (Gemini external + scope-creep / code-correctness / ghost-component / plan-readiness), each grounded by reading the spec + verifying claims against the repo._

## Verdicts
- 🟦 Gemini — **FAILED** (free tier `limit:0`, reconfirmed). Fallback grounded analysis, confidence low; converged with the others.
- scope-creep — approve-with-changes (high)
- code-correctness — approve-with-changes (high)
- ghost-component — **revise** (high)
- plan-readiness — **revise** (high)
- **Net: REVISE.**

## Consensus critical findings (all verified against code)
1. **Ghost-trap in the persistence rule.** `DrillStack` renders only from `drillsJson` (TutorWorkspace.tsx:114-116, 205); rev 1 kept `drillsJson="{}"`. Generated drills → `problemsJson` only → grade server-side but **never paint**. → rev 2 §2/§1.5: generation writes BOTH stores; `DrillContent.vizId` mount in `DrillStack`.
2. **`prep-authored` OVERWRITES `problemsJson`** (TutorRoutes.kt:~1395-1400) — naive reuse wipes E2 authored drills. → rev 2 §2: read-merge-write; committed `/reprep` kcId-preserving merge.
3. **Kotlin validator can't see the TS registry.** → rev 2 §5: shared `content/viz-ids.yaml` both sides read + parity test.
4. **Critic cross-family wiring not "existing" + env-var collision.** `FallbackLlm` relay→OpenRouter isn't pre-wired (Llm.kt:60-70 = relay→copilot); `OpenRouterChatLlm()` reads shared `JARVIS_OPENROUTER_MODEL` (default Llama = generator family). → rev 2 §4: explicit `FallbackLlm(RelayLlm(), OpenRouterChatLlm(defaultModel=<non-Llama :free>))` via constructor arg + runtime family≠family assert + verify-and-pin.
5. **PA corpus can't exercise the features:** no visuals (→ DEC-2 promoted to a REQUIRED fixture KC, rev 2 §7), no spans (→ drop "grounded spans", ground on `quote`, §0/§3), no numeric answers (→ computational fixture KC for the self-solve leg; "proven on PA" overclaim removed).
6. **Playwright not installed** (no config/spec/dep). → rev 2 §5/§9: stand-up is its own task + prerequisite gate P2.
7. **`parseLlmJson` drops kcIds/shape/answer** (PdfProblemExtractor.kt:33-51) — reuse = silently ungradable. → rev 2 §3/§8: new parser; injectable generator/critic LLM seams for deterministic E2E.

## Missing contracts the review demanded (added in rev 2 §8)
generate req/resp DTO · critic return shape + threshold · shape-keyed prompt skeletons · KC→shape assignment · frontend plumbing of shape+viz_id · two-track acceptance + build order (§9).

## Validator constraints on the DEC-2 fixture (ghost reviewer, rev 2 §7)
exam_weight 0.0 (sum stays 1.0±0.02) · prereq edge to a tier-1 root (orphan gate) · real `quote` source (empty source = ERROR) · bilingual names. RecursionTree is zero-prop static fib(5) — a concept animation, not a per-problem viz (stated honestly).
