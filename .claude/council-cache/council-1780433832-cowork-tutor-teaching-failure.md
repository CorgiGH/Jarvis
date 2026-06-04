# Council review — 1780433832

**Problem:** A claude.ai "cowork" Claude tutoring Alex (ADHD, visual, zero-prior-knowledge, CANNOT verify correctness) keeps failing despite repeated mid-session corrections + handoff-doc hardening. Diagnose the ROOT pattern from the real transcript and prescribe the fix.

**Proposed diagnosis + fix (under review):** DIAGNOSIS = "build-forward" teaching: references its own prior words + the course's named results as settled ground instead of deriving each idea from zero; assumes continuity; cites sources as authority instead of explaining; patches instances not the pattern; improvises from training-memory. FIX = "teach from zero, never reference" contract + one atomic idea/message + forced-retrieval gate + on-correction re-derive from Alex's last demonstrated point; PLUS structural: author a grounded lesson-plan from the materials first, then deliver one step at a time.

**Project context:** Cowork tutor for Alex; subject ALO (linear algebra / numerical methods); his real course materials uploaded; exam imminent. Transcript: vectors → Ax=b → Gauss went WELL (worked-example, predict-then-reveal, visual, one-step, Alex engaged + correct); BREAKDOWN at LU — taught a non-course "multipliers=L" shortcut, skipped the forward-substitution prereq, flip-flopped Doolittle/Crout, wrote paragraph walls, jumped to Step 2 after a hard reset, cited formulas ("U's first row = A's first row") without explaining what U is. Corrections held for ONE message then regressed. Alex's wound: "the only reason I know you didn't fuck up is because I stopped you."

**Timestamp:** 2026-06-02T20:57:12Z

---

## 🔴 Devil's Advocate
AGENT: Devil's Advocate
STANCE: CONDITIONAL
REASONING: The diagnosis is mostly right and the fix directionally sound, but it contains one self-defeating ban that makes the tutor MORE dangerous for a student who cannot verify. "BAN citing any source/prior-result as authority" + "teach from zero, never reference" attacks the wrong target: the LU breakdown happened because the tutor taught material NOT in Alex's course, not because it cited a source. Citation isn't the disease — ungrounded improvisation is. Strip the obligation to anchor every step to the exact course procedure and you remove the only tripwire that caught the failure: Alex's "is this from the courses?" only worked because there was a course to check against. A "derive from zero, never reference the source" tutor would have confidently derived the Crout shortcut from scratch, Alex would have had nothing to challenge it with, and the silent failure he NAMED gets worse.
KEY CONCERN: The fix conflates "don't cite as authority-substitute-for-explanation" with "don't anchor to the course's specific method" — but the root failure was teaching the wrong off-syllabus method, so the contract must FORCE binding every step to Alex's exact course procedure (faithful extraction of HIS materials, method-name + convention locked, hard stop if ambiguous), not free it to re-derive from training-memory.

## 📚 Domain Expert
AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The diagnosis correctly identifies **expert blind spot** (curse of expertise) — the tutor compresses derivations because the named result feels like settled ground, so it references rather than re-derives. The fix's structural half is the right, well-established wheel: **ITS lesson-graph / knowledge-component pre-authoring** (Anderson ACT-R tutors, ALEKS knowledge-space) + **mastery learning** (Bloom 2-sigma, Keller PSI) gated by **forced retrieval** (testing effect) — authoring a prereq-sequenced path before teaching is exactly how ITS avoid improvising from a generative prior. "One atomic idea + predict-then-reveal" = worked-example effect + expertise-reversal-aware scaffolding. BUT the absolutist "teach from zero, NEVER reference" overshoots the science: scaffolding-and-fading + expertise-reversal say support should DECREASE as competence grows (Alex succeeded through Gauss); a literal "never reference prior results" forbids the schema-chunking that makes LU comprehensible once forward-substitution is mastered, and contradicts the grounding contract's "cite the materials."
KEY CONCERN: "Never reference / teach from zero" is the wrong control variable — the failure isn't THAT it referenced, it's that it referenced things Alex hadn't yet mastered (and un-introduced formulas / un-grounded shortcuts). Re-scope to **mastery-gated referencing**: build on a prior result only AFTER the forced-retrieval gate confirms Alex can reproduce it; anything not yet gated must be derived from zero. Preserves fading, kills build-forward, stays consistent with materials-grounding.

## ⚙️ Pragmatist
AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: The diagnosis is sharp and "teach from zero / one atomic idea / re-derive on correction" are cheap and directionally right — but piling more prose onto already-long instructions feeds the exact decay flagged: longer context dilutes adherence, so net behavior change shrinks per rule added. The "author a lesson-plan first" step is the only structurally promising piece because it front-loads grounding into an artifact that persists across the session instead of relying on re-reading rules each turn — but only if that plan lives in a visible, re-referenced place (a pinned artifact), not a one-time message that scrolls away. As-is the fix is disproportionate: five new behavioral rules plus a structural step, when the decay evidence says rule-COUNT is the problem, not rule-quality.
KEY CONCERN: Adding 5+ rules to fight an instruction-decay problem is self-defeating; the leverage is a single persistent, re-anchored lesson-plan artifact (the structural half), not more contract text — keep that, compress the rest into its per-step template.

## 🧱 First Principles
AGENT: First Principles
STANCE: CONDITIONAL
REASONING: Stripped, the goal is: Alex must be able to trust what's taught is HIS course's procedure, correct, without policing it — which he can't, that's the point. The failure clustered exactly where the answer stopped being universal intuition (model knows cold) and became course-specific convention the model does NOT possess (which LU variant, prereq chain, step order). That is a **knowledge-absence failure wearing a behavior-failure costume**: "build-forward" is real but downstream — the model improvises course procedure precisely because it has nothing to build forward FROM, so it back-fills from training-prior + its own prior sentences. No behavioral contract can manufacture knowledge the model doesn't hold; stop it citing and assuming continuity and it will STILL emit the wrong LU variant, just more humbly. The fix is correct only in its second, structural half — forced extraction of the exact procedure from Alex's file BEFORE teaching; the behavioral half is necessary hygiene but insufficient and must not be sold as the primary lever.
KEY CONCERN: The diagnosis front-loads "build-forward style" when the root is knowledge-absence; make grounded-extraction-before-teaching the HARD GATE with a verifiable retrieved-procedure artifact (variant + prereq chain + step order, each tied to a file span) that the lesson cannot proceed without — that is the ONLY part addressing WHY it is wrong, not how it sounds when wrong.

## ⚠️ Risk Analyst
AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: CRITICAL risk — confident-wrong content reaching a non-verifying student before an exam — is only PARTIALLY reduced and the fix adds a new single point of failure. The original failure (wrong non-course LU method) was a content-sourcing error, not a presentation error; "teach from zero, never reference" attacks walls/skips/flip-flops (cosmetic symptoms) but a tutor improvising "from zero" still improvises the MATH from memory, deriving a confidently-wrong result with cleaner pedagogy — arguably MORE dangerous because polish raises trust. The structural half (author a grounded lesson-plan first) is the only part targeting the CRITICAL risk, but it RELOCATES the failure: a single mis-authored plan from a misread PDF becomes a confidently-followed wrong spine, no in-session correction signal. HIGH risk — instruction decay mid-session — is historically proven ("held one message then regressed"); a prose contract regresses the same way. Forced-retrieval is the one structural lever and it's underspecified (zero-knowledge ADHD student may not produce the demonstration → deadlock or silent "help").
KEY CONCERN: The fix must include a verification gate on the CONTENT/MATH itself (lesson-plan checked against source at author-time, each step re-checked against cited material before reveal — quote-and-locate, not paraphrase-from-memory), not just presentation form. Condition for approval: author-time grounding check on the plan + per-step source-quote check at delivery.

---

## Sanity Check
SANITY [Devil's Advocate]: PASS — found the self-defeating "never reference" ban; stays in lane.
SANITY [Domain Expert]: PASS — names real patterns (expert blind spot, ITS lesson-graphs, mastery learning, forced retrieval, expertise-reversal, fading); concrete re-scope.
SANITY [Pragmatist]: PASS — focuses on decay + rule-count + single persistent artifact; specified condition.
SANITY [First Principles]: PASS — strips to knowledge-absence root; makes extraction the hard gate.
SANITY [Risk Analyst]: PASS — ranks CRITICAL/HIGH; relocated-risk + per-step quote-check; specified condition.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The diagnosis is mis-rooted and the headline fix is actively harmful. The root is NOT "build-forward style" (that's a downstream symptom) — it is KNOWLEDGE-ABSENCE: the tutor is excellent on universal intuition (vectors→Gauss) and fails exactly where the answer becomes Alex's COURSE-SPECIFIC procedure (which LU variant, the prereq chain, the step order) — content it does not hold and improvises from training-memory. The proposed "teach from zero, NEVER reference / ban source citation" makes this WORSE: it removes the only tripwire that caught the error (Alex's "is this from the courses?") and lets the tutor improvise wrong math with cleaner pedagogy and higher trust. Only the structural half — forced grounded EXTRACTION of the exact procedure from his files BEFORE teaching — addresses why the tutor is wrong rather than how it sounds when wrong.

AGENT CONSENSUS: 5 CONDITIONAL — 0 flagged. Unanimous convergence: root = knowledge-absence; "never reference" is the wrong lever; the extraction-artifact is the real fix; don't add more decaying prose rules.

KEY ISSUES:
- **Wrong root (First Principles, Devil's Advocate):** knowledge-absence, not presentation style. No behavioral rule manufactures course knowledge the model lacks — it'll emit the wrong LU variant "just more humbly."
- **"Never reference" is self-defeating (Devil's Advocate, Domain Expert, Risk Analyst):** the disease is ungrounded improvisation, not citation; banning reference removes the tripwire and polishes wrong content. Replace with **mastery-gated referencing** — build on a prior result only after Alex reproduces it; anything not yet gated, derive it.
- **Decay = rule-count, not rule-quality (Pragmatist):** more prose rules dilute adherence and regress mid-session. Collapse to ONE persistent, re-anchored artifact.
- **The artifact relocates risk unless content-checked (Risk Analyst):** a mis-authored plan from a misread file becomes a confidently-followed wrong spine. Needs author-time grounding check + per-step quote-and-locate at delivery.

RECOMMENDED PATH (the corrected fix):
1. **Reframe the root for Alex:** the tutor improvises course-specific procedure it doesn't actually know — that's the failure, not "style."
2. **HARD GATE (headline, not a PLUS): grounded extraction before teaching.** Before a topic, the tutor authors a short VERIFIABLE "procedure card" from his files: method name + variant (e.g. Doolittle, 1's on L), prereq chain, exact step order — EACH tied to a quoted span from his material. Author-time check: card matches source? File ambiguous → STOP, don't guess.
3. **Mastery-gated referencing** replaces "never reference": may build on a prior result only AFTER the forced-retrieval gate confirms Alex reproduced it; otherwise derive from zero. Preserves fading.
4. **Per-step delivery check:** each step quote-and-locate against the card/source, not paraphrase-from-memory. One atomic idea, predict-then-reveal — folded into the card's per-step TEMPLATE.
5. **One artifact, not more loose rules:** the procedure card is pinned + re-anchored each step; behavioral rules live as its per-step template, not standalone contract clauses (fights decay).

CONFIDENCE: 8
What would change it: whether the cowork tutor can reliably author a faithful procedure-card from his (partly handwritten/scanned) materials — if extraction itself is unreliable, even the corrected fix needs a human/second-model check on the card before teaching. (This is exactly the jarvis project's extraction-gate + forced-retrieval guardrails — the cowork failure is a live proof of why they exist.)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780433832-cowork-tutor-teaching-failure.md
