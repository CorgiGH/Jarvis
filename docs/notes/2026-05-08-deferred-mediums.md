# Deferred MEDIUM follow-ups from post-impl council 1778164815

Captured at end of Phase 1 (commits `193f836` 1.1 → `ada922f` 1.3). HIGH-A (salient PII filter) was fixed inline. The items below are intentional follow-ups; none of them block Phase 2.

## 1. Activity.append: O(file_size) loadEntries(hours=1) per sample

**Where:** `src/main/kotlin/jarvis/Activity.kt:34-43` — `append()` calls `loadEntries(hours=1)` to compute scoring context.

**Today:** activity.jsonl is small (low-MB), 5-min cadence. Cost trivial.

**At scale:** beyond ~50 MB, every append re-scans the whole log → I/O climbs and `-Xmx512m` JVM starts feeling it.

**Fix shape (Phase 2 or earlier):** read only the file tail (last N bytes / last K lines) instead of the full file; or maintain an in-process ring buffer of the last hour; or rotate the JSONL once it crosses a threshold.

**Verify:** add a perf-flavored test asserting append-time stays sub-50ms when file > 100k rows.

## 2. Activity.append scoring read outside LOCK

**Where:** `Activity.kt:34-43` — `loadEntries(...)` runs BEFORE `synchronized(LOCK)` in `appendTo`.

**Symptom:** two concurrent writers (`/api/activity` + PC fallback) can each compute score off the same prior file state and miss each other's continuity / churn signal. Writes themselves stay atomic; only the importance value is briefly stale.

**Fix shape:** move the `loadEntries` call inside the `synchronized` block, OR accept eventual-consistency and document. The latter is fine for v1 — scoring drift over 5-min windows is invisible to the user.

## 3. Conversation `last_accessed_at` field missing

**Where:** `ConversationEntry` (Conversations.kt:14-26) lacks a `lastAccessedAt: String?` field that LangChain `TimeWeightedVectorStoreRetriever` and the Generative-Agents paper (Park et al. 2023, Algorithm 1, line 6) both rely on.

**Symptom:** `recentByImportance` decays a row's recency from CREATION ts only. A frequently-recalled important turn from yesterday will eventually lose to a noisy chat from 2h ago, even though it has been re-surfaced repeatedly.

**Fix shape (Phase 1.4 or Phase 2):** add nullable `lastAccessedAt`. Update inside `recentByImportanceFrom` AFTER selection — but mutating an append-only JSONL is expensive, so consider a sidecar `last_access.jsonl` with `{msgId, ts}` rows that the scorer joins lazily. Letta's `RecallMemory.update_last_accessed` is the named precedent.

## 4. ACTIVITY_LINE_CAP truncation hides scored entries

**Where:** `Config.ACTIVITY_LINE_CAP = 200` (Config.kt:32). At 5-min cadence, 200 lines = ~16.6 hours of activity, even though `ACTIVITY_LOOKBACK_HOURS = 24L` says we want a 24-h window.

**Symptom:** importance scores beyond ~17h ago get silently dropped from the formatted `[[activity: 24]]` output.

**Fix shape:** switch the cap from line-count to time-window (`takeLastWhile { it.ts >= cutoff }`). Or raise the cap to ~300 to cover 24h on a 5-min cadence. Defer until Phase 2 ProactiveLoop is reading activity for real.

## 5. ConversationScorer keyword substring matching

**Where:** `ConversationScorer.kt:50-57` — `KEYWORDS.count { lower.contains(it) }`.

**Symptom:** "fail" hits inside "failsafe", "failure-mode"; "bug" hits inside "debug". Documented as acceptable v1 noise, but worth tracking. Fix is `\b<word>\b` regex per keyword, OR token-set lookup, OR (Phase 5) replace heuristic with LLM scoring per Park et al. §A.2.

---

None of these is a Phase 2 blocker. Each is wired to a clear "do this when …" trigger.
