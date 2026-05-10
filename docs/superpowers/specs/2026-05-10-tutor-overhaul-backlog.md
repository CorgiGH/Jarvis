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

- `[2] [test-infra] [playwright-zoom] [low] Phase 2 Playwright gate strict outlineWidth==='2px' assertion is brittle to harness viewport zoom. The Playwright MCP session rendered at effective 80% zoom; all px-valued border/outline widths returned 0.8x in computed styles (probe controls confirmed: literal outline:3px → 2.4px, border:10px → 9.6px). Real-user browsers at 100% see the authored 2px ring. — Phase 8 final audit: either pin Playwright session to 100% zoom OR change strict-literal assertion to a numeric ratio check (e.g. width >= 1.5px). axe-core a11y scan was unaffected and is the load-bearing check; passed clean on both /tutor/ and /tutor/tasks with zero AA violations including scrollable-region-focusable.`
- `[2] [responsive] [touch-target] [med] Action buttons under 44×44px on mobile in 4 surfaces: KnowledgeGapCard INSERT/RESOLVE/FLAG (px-3 py-1 text-xs ≈ 24px), SuggestedEditCard COPY/REJECT (same), TasksScreen subject presets + CREATE (px-2 py-1 / px-3 py-1 text-xs ≈ 24px), TrustSettings GRANT/REVOKE (px-3 py-1 / px-2 py-0.5 text-xs ≈ 20-24px). ChipRow already mobile-bumps via py-2 sm:py-1 — model the rest the same way. — Bump py-1 → py-2 sm:py-1 on every text-xs action button in those four files. Verify with Playwright getBoundingClientRect at 375px viewport.`
- `[2] [a11y] [form-labeling] [med] ChatPane chat input (line 142) has placeholder-only labeling — no <label htmlFor> and no aria-label. Placeholder disappears on focus and has weaker contrast than label text per [[Accessibility Fundamentals]]; SR users may hear nothing. — Add <label htmlFor="chat-input" className="sr-only">Message Jarvis</label> + id="chat-input" on the input. Same pattern for Scratchpad (currently aria-label only is acceptable but visible label binding via htmlFor on the SCRATCHPAD header would be stronger).`
- `[2] [responsive] [header-overflow] [med] App.tsx header at <420px viewport: "JARVIS · TUTOR" + taskId + "× close" + workspace/tasks/trust nav fits via flex but truncates the taskId chip and crowds the nav. No flex-wrap, no burger. — Add flex-wrap on the outer header div + min-w-0 already present on inner; consider hiding the taskId chip below sm:. Or move nav to a second line below sm: via flex-col sm:flex-row.`
- `[2] [visual-polish] [typography-tokens] [med] index.css defines --type-sm (12px), --type-lg (18px), --type-h2 (20px) but no component references them — every component still uses tailwind text-xs/text-sm/text-lg literals. Token contract is half-applied: colors flow through tokens (post Phase-2 fixup), font sizes don't. — Either drop the unused --type-* vars OR migrate text-xs → text-[length:var(--type-sm)] (less ergonomic) OR define matching @theme --text-* aliases so Tailwind's default text-* utilities map onto our scale. Lowest-friction: drop the dead vars; revisit when a real reskin demands them.`
- `[2] [a11y] [aria-current-value] [low] Sidebar.tsx line 98: task buttons use aria-current={active ? "true" : undefined}. These are routing buttons inside a <nav>, so aria-current="page" is the better-specified value per ARIA 1.2; "true" is the unspecified-context fallback. — Change "true" → "page". Behaviorally identical for SR; better semantics.`
- `[2] [a11y] [section-headings] [low] Sidebar.tsx subject group headers (line 80) and Scratchpad.tsx panel header are <div>s, not <h-N> tags. Screen-reader users navigating by heading (H key) can't jump to subject sections or scratchpad. — Promote subject headers to <h3> inside the <nav>; promote SCRATCHPAD label to <h2 id> + bind the textarea via aria-labelledby. KnowledgeGap "GAP · TYPE · TOPIC" and SuggestedEdit "SUGGESTED · TYPE" labels are inline message-attached chips, not navigable sections — leave as <div>.`
- `[2] [a11y] [redundant-role] [low] Sidebar.tsx line 83: <ul role="list">. Implicit role on <ul> is already "list"; redundant unless the parent CSS sets list-style:none which can strip the role in Safari reader. Tailwind preflight does set list-style:none, so the role IS load-bearing here. — Keep as-is. Documenting so future cleanup doesn't strip it.`
- `[2] [visual-polish] [page-h1-missing] [low] Workspace page (/tutor/?taskId=...) has no <h1>. The chat header "JARVIS · TASK X" is a <div>, the PDF panel uses a <div>, etc. /tasks and /settings/trust each provide an <h1>. — Add visually-hidden <h1>Tutor workspace · {taskId}</h1> at TutorWorkspace top. Helps SR landmark navigation; one h1 per page is the wiki [[Visual Hierarchy]] expectation.`

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
