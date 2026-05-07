# R2 — `lastAccessedAt` decay (Park et al. Algorithm 1, LangChain TimeWeightedRetriever parity)

> Autonomous-mode brainstorm + spec. Skill `superpowers:brainstorming` invoked; HARD-GATE waived per user "do everything" instruction. Decisions taken with rationale; user can revert via git if any pick is wrong.

## Context

Phase 1.3 `Conversations.recentByImportance` decays recency from `entry.ts` (creation). Park et al. Algorithm 1 line 6 + LangChain `TimeWeightedVectorStoreRetriever` both decay from *last_accessed_at* — so frequently-recalled rows stay surfaced. Already flagged in `docs/notes/2026-05-08-deferred-mediums.md` as a MEDIUM follow-up from post-impl council 1778164815.

## Approaches considered

- **(a) Field on `ConversationEntry`, mutate in place.** Append-only JSONL → rewriting full file every access. Fatal on a 10K-row file. Rejected.
- **(b) Sidecar `last_access.jsonl` ({msgId, ts}) append-only, lazy join on read.** ✓ Picked. Restart-safe. O(file_size) read per access acceptable at single-user volume; capped at last N rows in practice.
- **(c) In-memory map + periodic snapshot.** Lost on restart. Rejected (council 1778164815 said "restart-safe is sacred").

## Design

**New file:** `src/main/kotlin/jarvis/ConversationAccess.kt`
```kotlin
@Serializable data class AccessEntry(val msgId: String, val ts: String)
object ConversationAccess {
    fun touch(msgIds: List<String>)            // append rows to last_access.jsonl
    fun touchTo(file: Path, msgIds: List<String>, now: Instant)
    fun lastAccessByMsgId(): Map<String, Instant>
    fun lastAccessByMsgIdFrom(file: Path): Map<String, Instant>
}
```

**Path:** `Config.lastAccessFile = stateDir.resolve("last_access.jsonl")`.

**Wiring:**
- `Conversations.recentByImportance` and `recentByImportanceFrom` join the sidecar map; recency anchor = `max(creation_ts, lastAccessedAt)`.
- After picking the top-N, call `ConversationAccess.touch(picked.msgIds)` so next call sees them as freshly accessed.
- `Conversations.recent()` (chronological tail) does NOT touch — pure log replay must stay deterministic; only the importance-weighted view counts as "access" per Park et al. semantics.

## Edge cases

- Empty sidecar → fallback to `entry.ts` (matches today's behavior). No regression.
- msgId in sidecar but absent from conversations.jsonl → ignored (orphan row tolerated).
- Multiple touches in one call → keep latest ts only on read (`groupBy(msgId).maxByOrNull(ts)`).
- Race: two `recentByImportanceFrom` calls touching the same msgIds → append-only, both rows survive, latest-ts-wins on read. No corruption.

## Acceptance criteria

- New unit tests: 4-6 cases — empty sidecar fallback, last-access overrides creation for decay, repeated access keeps latest ts, recent(n) chronological output unchanged (regression).
- Existing tests pass.
- One smoke after deploy: hit `/api/chat` twice, observe a sidecar entry land for the latest msg_ids.

## LOC estimate

~80 LOC main + ~80 LOC tests. Within budget.
