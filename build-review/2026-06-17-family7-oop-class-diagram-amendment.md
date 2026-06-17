# DECISION-LOG / AMENDMENT — Family 7: static-structure (class-diagram)

**Date:** 2026-06-17 (SESSION-78) · **Status:** RATIFIED by Alex · **Type:** PM/Decision-Log amendment to spec §5.2 + master-plan Phase-A roster (the only act an executor may not self-make — spec §5.2 line 322: "a *new family* is a design-level event requiring its own verification harness before first use"; master-plan line 135 escape-hatch: a §5.2 carve is a PM/Decision-Log amendment, NOT an executor choice).

**Authority:** the corrected-facts sequencing council (run `wzw8tvund`, 5/5 advisors, confidence 9, OOP-beeline) — grounded in the live repo at HEAD `f5182ba`. Honors Alex's locks: OOP/POO deployed FIRST ([[project_scope_oop_first]]); oracle-first; **bar any oracle-less family from VERIFIED**; quality-primary.

---

## The amendment

Add a **7th viz family — `class-diagram` (static-structure)** to spec §5.2's six.

- **What it renders:** UML-style class boxes — each a name compartment + fields compartment + methods compartment — connected by typed edges: **inheritance (generalization), composition, aggregation, association, dependency**, each carrying direction and (where typed) multiplicity. It models **static type structure**, the backbone of OOP/POO teaching (classes, members, relationships).
- **What it is NOT:** an algorithm trace. There is **no step-delta stream, no per-frame state advance, no end-state.** A class diagram is one canonical static frame.

## Why a NEW family — not folded into an existing one

Spec line 687 ("each named missing one-off becomes an instance of an existing family, not a new component") and §5.2 line 322 demand we first try to fold. We did; it does not fit:

- All 6 existing families are **dynamic / trace-based**. Even `state-machine/code-trace` (the closest candidate) animates a **run** — a step sequence with state-deltas and an end-state (variables table advancing per line). A class diagram has **none of that**: no run, no deltas, no end-state.
- The §5.4 verification harness (trace-match = "reference execution; every rendered step's state equals the reference state") is **structurally inapplicable** to a thing that does not execute.
- Therefore a class diagram is **structurally homeless** — the exact "design-level event" §5.2 line 322 reserves a new family for. This is a genuine new family, not a one-off instance.

## Its verification — the §5.4 analog for static structure: a STRUCTURE-ISOMORPHISM gate

Because trace-match cannot apply, this family's hard floor is a deterministic **structure-isomorphism gate**:

> Parse the typed class model (the instance data) → assert the rendered SVG is **isomorphic** to the model: every declared class present with its declared fields + methods; every edge present with the correct **type + direction** (and multiplicity where typed); **zero dropped, zero extra** classes/members/edges; labels measured + no-clip by construction (§5.3).

- **Ships WITH a committed seeded-wrong fixture** (a dropped method / a mis-typed edge / a reversed inheritance arrow / an association to the wrong class) proving the gate goes **RED on the seed, GREEN on the fix** — demonstrated before the family enters VERIFIED. This is the non-negotiable that keeps it out of the oracle-less trap.

## The MOTION gate is explicitly N/A — and its absence must NOT read as "no gate"

The `frame-conjunction` motion gate (STATE-moved + figure-pixels-frozen = FAIL) is **structurally inapplicable**: a static diagram is one frame, no motion. Do **not** wire it; do **not** let its absence be read as "this family is ungated." The **structure-isomorphism gate IS this family's hard floor** — it is what the "bar oracle-less from VERIFIED" lock points to here. (The `svg-overlap` element gate + no-clip-by-construction still apply — they are layout gates, family-agnostic.)

## Family-contract divergence (named, per §5.3)

§5.3 assumes typed step-deltas + callout-per-step + `AlgoStepperShell` scrubber playback. The static-structure family has a **single canonical frame**; any progressive reveal (e.g. dim-then-light each class) is presentation-only, **NOT algorithm-derived**, and is out of scope for the isomorphism gate (which checks the final/canonical structure). The scrubber mount is degenerate/optional here.

## Downstream accommodation ledger (account-for-accommodations rule)

| Surface | Change required |
|---|---|
| `tutor-web/src/components/viz/families/familyRegistry.ts` | register `family_id: 'class-diagram'` (siblings today: graph-tree, seq-array, matrix-grid, chart-dist, sort-merge, react). |
| `docs/.../interface-signatures-lock.md` §NEW-V viz route | a new `family_id` is a typed-data contract value — register it there (canonical-on-conflict). |
| Gate-coverage REGISTRY (Phase 0, `tools/gate-registry-check.mjs`) | add the structure-isomorphism gate as a clause — PLANNED-in-Phase-A until built, then LIVE-GREEN; PROOF-DEMANDED tag (Layer-1). |
| Master-plan Phase-A exit gate (line 149) | extend: family-7 passes structure-isomorphism + svg-overlap + no-clip; barred from VERIFIED until its seeded-wrong self-test is RED→GREEN. |
| concept_type → family FITNESS gate (§5.4 / plan line 145) | OOP "class-structure" concept_type maps to `class-diagram`; no-fit still HALTs. |
| spec §5.2 (line 313 list + line 603 "The 6 viz families") | add family 7; update the count. |

## Phase placement

Built in **Phase A** as the OOP-first long pole (call it **A2b**), ahead of finishing the other families' *rendered* motion+overlap gates (those are Phase-A LATE-exit completion, owed before Phase F breadth, NOT before this one OOP vertical's deploy). Sequenced next: A2b (this family + its gate) → first POO KC → one OOP lesson → gate-coupled Phase-J deploy. The vision-judge benchmark stays deferred/advisory throughout.
