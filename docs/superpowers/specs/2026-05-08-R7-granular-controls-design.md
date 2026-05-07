# R7 — Granular notification controls + signal history feed

## Context

Notification fatigue research 2026: "single Allow notifications? toggle is no longer acceptable UX." Need quiet-hours editable, importance threshold slider, per-kind mute, paginated history feed.

## Approaches

- **(a) New Compose screen "Settings" with section-per-control + simple history list under it.** ✓ Picked. Single screen, accessible from MainActivity top-bar IconButton. Reuses existing JarvisClient.fetchSignals.
- **(b) Multi-screen NavHost.** Adds nav library dep + complexity. Single user, single screen sufficient.
- **(c) WebView pulling /settings page from server.** Inverts the responsibility; server doesn't have settings storage today.

## Design

**New file:** `android/src/main/kotlin/io/victor/jarvis/SettingsScreen.kt`

Single Compose function `@Composable fun SettingsScreen(onClose: () -> Unit)`. Sections:
1. **Quiet hours** — two stepper-style controls (start hour 0-23, end hour 0-23). Persisted to Prefs (new keys `QuietStart`, `QuietEnd`). Server-side enforcement is already hardcoded; client-side enforcement = SignalWorker filters out signals whose ts is during the user's quiet window.
2. **Importance threshold** — slider 0..1, default 0.0 (show all). SignalWorker filters out signals below threshold.
3. **Per-kind mute** — checkbox per kind: ctx_model_summary, reflection, error. Persisted as comma-separated `MutedKinds`. SignalWorker filters out muted kinds.
4. **Signal history** — LazyColumn showing last 50 signals fetched. No paginate cursor — at single-user scale, 50 covers ~3 days.

**MainActivity:** add Settings IconButton in TopAppBar. Tap → set `showSettings=true` → Scaffold swaps content. Close button restores chat.

**SignalWorker filtering:** before `postSignal`, apply: muted-kinds skip, below-threshold skip, in-quiet-hours skip. Filtered signals still advance `lastSeenTs` so they don't pile up.

## Edge cases

- Both quiet bounds equal (e.g. start=22, end=22) → quiet hours disabled (zero-width window).
- Threshold = 0 → all signals show (default behavior preserved).
- All kinds muted → nothing shows (user gets what they asked for).
- History feed when offline → shows whatever was cached (none for v1; show empty + retry button).

## Acceptance criteria

- 4 Prefs keys + load/save helpers wired.
- SignalWorker filtering live + tested logically (4 cases: muted-kind, below-threshold, in-quiet-hours, all-pass).
- APK builds + Settings screen renders.

## LOC estimate

~150 client (Compose screen + Prefs + Worker filtering). No server changes.
