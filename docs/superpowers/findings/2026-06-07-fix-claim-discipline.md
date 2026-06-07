# Fix-claim discipline — "fixed" = a covering test/invariant exists

**Ratified:** council `1780827389` (the rule) + council `1780762845` (the surrounding process). 2026-06-07.

## The problem (Alex named it)
The dev loop — build → AI bug-find → fix findings → claim "fixed/closed" → a LATER separate bug-find
re-surfaces a bug in the same area "supposedly fixed." Verified root cause: **scope mismatch**, not
regression. Each fix closes the EXACT instance found + tests exactly that instance, but the handoff/commit
claim is **class-broad** ("false-faithful holes closed") while the fix was **instance-narrow**; the next
review finds the adjacent **sibling**.

Concrete instance: `B5r-1 (c2)` closed the **agreed**-REFUTED DEFINITION case (both families REFUTED →
never faithful) + tested exactly it; the handoff said "false-faithful holes closed". The **disagreeing**-
REFUTED sibling (one family REFUTED, one SUPPORTED/UNCLEAR) stayed open and a later review found it.

## Why "enumerate all siblings" is the WRONG fix (council, unanimous)
A SAMPLING review can never prove a POPULATION closed by counting instances. "Class enumerated + closed"
becomes an unfalsifiable over-claim that a different memoryless AI treats as a sealed guarantee — a
green test then *defends* the missed sibling. That is **worse** than honest scoping.

## The ratified rule
1. **"Fixed/closed" = a CI-enforced test or invariant covers the claimed scope.** No covering test ⇒ it is
   "patched", not "fixed". Free-text "fixed" in a commit/handoff is not a closure.
2. **Prefer a CLASS-KILLING property/invariant**, asserted structurally and independently of the routing
   table, so it closes all siblings present + future without enumerating them. The invariant catches a
   wrong cell even if a cell was ratified wrong. (Built this session: the `anyRefuted ⇒ never faithful`
   structural guard in `VerificationRunner.decideOutcome`, locked by test `B5r-1 (c3)`.)
3. **Else a RED-GREEN regression test** (write the failing test FIRST, watch it fail), whose **test name ==
   the exact claimed scope**, so the claim and the proof can't drift apart.
4. **Default claim wording** (commit body / BRIDGE handoff) = **"closed instance X; siblings unverified."**
   Widening to "class closed" requires DELETING that disclaimer AND citing the covering invariant/test by
   name. Honest scope is the path of least resistance.
5. **Never certify a class closed by enumeration alone.**

## Enforcement status (eating our own dog food — honest scope)
- BUILT: the rule recorded here; the first class-killing invariant + property test (`anyRefuted`); AND
  the broader independent SAFETY-FLOOR backstop test (`VerificationRunnerTest` "no claim kind reaches
  faithful under any poison condition" — covers `threw / collapsed / no-gold-span / anyRefuted` across the
  DEFINITION + INVARIANT routing branches; held green first run, so it locks the floor against future drift).
- NOT BUILT (follow-up): mechanical enforcement of the default wording (a commit-msg template / hook).
  Until then, enforcement is convention + `/wrap` discipline (see the BRIDGE-HEAD flag).
