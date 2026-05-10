# Full Site Audit — Findings (2026-05-10)

> Living doc. Findings get appended as audit progresses. HIGH / MED auto-fixed inline (commits cited per item). LOW gets logged here AND mirrored to `2026-05-10-tutor-overhaul-backlog.md`.

## Method

- Surfaces: `/`, `/tutor/`, `/tutor/?taskId=X`, `/tutor/tasks`, `/tutor/settings/trust`
- Viewports: desktop 1280×800, mobile 390×844 (iPhone 13 emulation)
- Tools: Playwright (Chromium) + axe-playwright + manual code reads
- Backend: every route in `TutorRoutes.kt` + `WebMain.kt` reviewed for auth/CSRF/error path

## Severity

- **HIGH** — broken / data loss / security / regression / blocking UX
- **MED** — visible gap, missing affordance, axe violation, mockup divergence
- **LOW** — cosmetic / niche / unlikely to surface

---

## Mockup-v4 gaps (known going in)

| Item | Severity | Status |
|---|---|---|
| Sidebar subject % rollup | MED | _pending_ |
| PlotlyEmbed plot caption header | MED | _pending_ |
| Bottom status bar (READY · CTRL+ENTER · BUC time) | MED | _pending_ |
| Chat-only vs split-pane layout | LOW (acknowledged) | spec §4 deviation, intentional |

---

## Audit findings (added live)

_Section populated as audit progresses. Each entry: severity / surface / dimension / finding / commit (or "deferred to backlog" for LOW)._

---

## Out of scope

- Telegram bot producer — needs token from @BotFather (user action)
- gws OAuth `gws auth login` — interactive on VPS (user action)
- Daemon PC-boot autostart — Windows Task Scheduler (user action)

## Final state

_Filled in after deploy. Bundle hash, total findings, HIGH/MED shipped, LOW deferred._
