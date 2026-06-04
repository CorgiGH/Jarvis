# Council — 1780583533 — architecture: run the trust-net audit PC-side (VPS serves verdicts only)?

**Date:** 2026-06-04. **Run:** workflow `factcheck-pc-side-audit`. 4 reviewers (systems-data · refuter · pragmatist-solo · risk-ops) + judge; reviewers read the real code.

**Question:** Is running the whole offline audit (relay + local NLI model + SymPy) on Alex's home PC, then syncing verdicts to the VPS (serve-only), a good architecture? Focus: the two-DB sync / source-of-truth risk.

**Verdict: GOOD_WITH_CONDITIONS (3 GOOD_WITH_CONDITIONS, 1 BAD_IDEA refuter).**

**Topology is RIGHT:** audit is already an offline owner/manual batch; leg-A (relay) already PC-side; PC is ON at audit time; VPS has ~1.9 GB free next to a 4 GB-pinned JVM → loading the model there risks OOM-killing the live box; audit-on-VPS still calls the PC relay anyway → buys nothing. The MODEL belongs on the PC.

**Two real code breakages found (gate it from GOOD_IDEA):**
1. **Verdict NOT content-keyed.** `kc_verification_status` PK = `kc_id` alone (`Phase1Tables.kt:108`), no content hash; the hash lives only in `verification_audit.claim_id`. Gate ALLOWs iff `status==faithful` (`VerificationGate`). → content drift after audit ⇒ badge LIES "matches your lecture" vs unverified text.
2. **VPS is NOT read-only for verdicts.** `POST /fsrs/{id}/report-wrong` (`TrustRoutes.kt:291-360`) writes `kc_verification_status` (faithful→pending) + pauses the card; runner does read-modify-write. → a naive whole-table PC→VPS push clobbers a live report-wrong + un-pauses a flagged card (lost update; badge lies).

**Conditions (MUST):**
- **Content-hash gate** enforced on BOTH serve (hash(current)==row.content_hash else UNVERIFIED) and sync (push only on hash match). Fail CLOSED.
- Sync = content-hash-keyed per-KC UPSERT of audit-owned columns ONLY; NEVER whole-`tutor.db` (VPS holds irreplaceable FSRS/attempt/session/report_wrong); skip OPEN-report_wrong KCs; one-way; Tailscale + bearer auth.
- VPS read-only for content+verdict; content edits originate on PC → git deploy. `verifyContent` stays FAIL-LOUD on the VPS without the model.

**Adopted:** D7 (PC-side audit), D8 (content-hash gate), D9 (surgical sync) in `2026-06-02-correctness-engine.md`.
