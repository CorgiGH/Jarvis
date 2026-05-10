# Tutor Overhaul — Backlog

Created: 2026-05-10 (Phase 1).

Source-of-truth doc for MED/LOW findings produced by each phase's UX-Playbook gate. HIGH severity findings block phase advancement and are fixed in-place — they should never appear here. Phase 8's final audit rolls everything up; entries can be deleted as they ship.

Format per entry:
- `[phase] [category] [principle] [severity med|low] [finding] — [recommended action]`

## Phase 1 — Critical bugs

- `[1] [backend] [idempotency] [med] POST /api/v1/tasks read-then-write race under truly-concurrent clients — Phase 1 Playwright gate (2026-05-10) used synthesized JS dispatchEvent x3 to fire 3 simultaneous identical POSTs and got 3 distinct task rows. The pre-INSERT lookup runs in a separate transaction from the INSERT, so concurrent requests can all miss the lookup and all INSERT. Real-user UX is protected by TaskQuickStart's disabled={busy != null} gating (one click sets busy → other preset buttons disabled before second click registers); the natural browser_click x3 in the same gate run fired only once (page navigated after first click, breaking locator). Race window for real users requires a non-disabled-button entry path (e.g. raw curl, future custom task UI bypassing busy). — Phase 6 closes this with TaskDetector + unique index on (user_id, subject, title) WHERE status='ACTIVE'. Comment at TutorRoutes.kt:589-591 already documents the race; no Phase-1 fix expected.`
- `[1] [frontend-test] [scroll] [low] TutorWorkspace.scroll.test.tsx asserts class contracts only (jsdom can't measure layout). Real scroll-engaged behavior is verified by Playwright gate, not vitest. — When Phase 2 lands typography tokens that change line-height + min-h propagation, re-run Playwright gate scenario 1 to make sure scroll math still holds. No backlog action needed unless Phase 2 regresses.`
- `[1] [layout] [mobile-first] [med] Mobile stack order is PDF-on-top / chat-below. Chat is the primary action; on phones user must scroll past full PDF height before reaching the input. — Cap PDF height on mobile (e.g. max-h-[40vh] sm:max-h-none) instead of flex-col-reverse, so reading order stays top→down without DOM-reverse semantic confusion. Phase 2 touch audit territory; revisit there.`
- `[1] [layout] [gestalt-grouping] [med] On mobile the column stack reads PDF + Scratchpad (no internal divider) → thick black bar → Chat. PDF↔Scratchpad visually merge. — Add border-t-4 border-black sm:border-t-0 above Scratchpad so three regions read as three on mobile and two columns on desktop. Phase 2 sweep.`
- `[1] [layout] [responsive-breakpoint] [med] Two 50/50 columns at 640-820px (small tablets / landscape phones) produce ~310px each — ChatPane SEND button + PdfPane both cramped. — Bump column-split breakpoint to md (768px): change sm:flex-row sm:w-1/2 sm:border-b-0 sm:border-r-4 → md: variants. Phase 2 reflow pass.`
- `[1] [interaction] [feedback-response-time] [med] Submit/preset buttons today disable while busy (TaskQuickStart line 140) but other surfaces creating tasks (custom task page, future curl/API) lack the in-flight signal. Dedup-200 path also returns silently fast enough that users may not notice the inline busy state. — Phase 2.5 already commits to spinner/disabled-state polish; ensure all task-create entry points get covered there.`
- `[1] [layout] [scroll-containment] [low] PdfPane sibling has overflow-hidden on its wrapper. On mobile when PDF is stacked, the iframe can clip to 0 height if chat content dominates first paint. — Add min-h-[50vh] sm:min-h-0 on the PDF wrapper for mobile breakpoint. Phase 2 mobile sweep.`

## Phase 2 — UX foundation

(empty)

## Phase 3 — Hygiene

(empty)

## Phase 4 — Layer B §4 close

(empty)

## Phase 5 — Corpus expansion

(empty)

## Phase 6 — Task autonomy

(empty)

## Phase 7 — Layer C

(empty)

## Phase 8 — Layer D + ops + final audit

(empty)
