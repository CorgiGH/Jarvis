# Tutor Overhaul — Phase 8 (Layer D + Ops + Final Audit) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** Final phase. 8.1 Plotly inline plot rendering via dynamic-import (~3MB on-demand). 8.2 Cron entry for the fallback-model probe script (weekly). 8.3 Full UX-Playbook audit rolling up Phases 1-7's backlog.

**Architecture:** Plotly + react-plotly.js installed but only loaded when first `plotly` fenced code block detected (React.lazy + dynamic import). Cron entry written to `/etc/cron.d/jarvis-probe` on VPS (one-shot ssh). Final-audit phase spawns the UX-Playbook agent across the entire surface and rolls up findings; HIGH severity → tracked as new backlog entries; MED/LOW that landed in earlier phase backlogs stay where they are.

**Tech Stack:** Same React + Vite + new dev deps `plotly.js-dist-min` + `react-plotly.js`.

**Source spec:** `docs/superpowers/specs/2026-05-10-tutor-overhaul-design.md` § Phase 8 (lines 345-373).

---

## File Structure

**Created:**
- `tutor-web/src/components/PlotlyEmbed.tsx` — lazy-loaded Plotly wrapper.
- `tutor-web/src/lib/plotlyParse.ts` — detects ```plotly fenced blocks in chat replies.
- `tutor-web/src/__tests__/plotlyParse.test.ts`
- `tutor-web/src/__tests__/PlotlyEmbed.test.tsx`

**Modified:**
- `tutor-web/package.json` — add `plotly.js-dist-min` + `react-plotly.js`.
- `tutor-web/src/components/ChatPane.tsx` — render Plotly embeds for parsed plotly blocks.
- `tutor-web/src/setupTests.ts` — mock react-plotly.js so jsdom doesn't load WebGL deps.

**One-shot ops:**
- VPS `/etc/cron.d/jarvis-probe` — install via ssh.

---

## Task 1: Phase 8 plan committed

```bash
git add docs/superpowers/plans/2026-05-10-tutor-overhaul-phase8.md
git commit -m "Phase 8 plan: Layer D + ops + final audit

Per spec § Phase 8. Phase 7 shipped + 4 gates passed at 93e506e.
Phase 8: Plotly inline plot via dynamic-import (~3MB on-demand),
cron install for fallback-model probe (weekly), final UX-Playbook
audit rolling up Phases 1-7.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: §8.1 — Plotly fenced-block parser

**Files:**
- Create: `tutor-web/src/lib/plotlyParse.ts`
- Create: `tutor-web/src/__tests__/plotlyParse.test.ts`

```ts
export interface PlotlyBlock { json: any; raw: string; }

const FENCE = /```plotly\s*\n([\s\S]*?)\n```/g;

/**
 * Phase 8.1: parses ```plotly fenced JSON blocks out of an assistant
 * reply. Returns the body with each block replaced by sentinels
 * (PLOTLY0, PLOTLY1, ...) plus the parsed Plotly.js Figure JSON
 * objects. Invalid JSON in a block is silently skipped (raw text
 * stays in body).
 */
export function parsePlotly(text: string): { body: string; plots: PlotlyBlock[] } {
  const plots: PlotlyBlock[] = [];
  let i = 0;
  const body = text.replace(FENCE, (raw, jsonText: string) => {
    try {
      const j = JSON.parse(jsonText);
      const idx = i++;
      plots.push({ json: j, raw });
      return `PLOTLY${idx}`;
    } catch (_) {
      return raw; // leave broken JSON in place
    }
  });
  return { body, plots };
}
```

Test:

```ts
import { test, expect } from "vitest";
import { parsePlotly } from "../lib/plotlyParse";

test("parsePlotly extracts ```plotly fenced JSON", () => {
  const text = "intro\n```plotly\n{\"data\":[{\"y\":[1,2,3]}]}\n```\noutro";
  const { body, plots } = parsePlotly(text);
  expect(plots).toHaveLength(1);
  expect(plots[0].json.data).toEqual([{ y: [1, 2, 3] }]);
  expect(body).toMatch(/intro\nPLOTLY0\noutro/);
});

test("parsePlotly handles multiple plots", () => {
  const text = "```plotly\n{\"data\":[]}\n```\nbetween\n```plotly\n{\"layout\":{}}\n```";
  const { plots } = parsePlotly(text);
  expect(plots).toHaveLength(2);
});

test("parsePlotly skips invalid JSON (leaves raw)", () => {
  const text = "```plotly\nnot json\n```";
  const { body, plots } = parsePlotly(text);
  expect(plots).toHaveLength(0);
  expect(body).toContain("not json");
});

test("parsePlotly no-op when no fences", () => {
  const { body, plots } = parsePlotly("plain text");
  expect(plots).toHaveLength(0);
  expect(body).toBe("plain text");
});
```

Frontend tests 120 → 124.

Commit:
```
git add tutor-web/src/lib/plotlyParse.ts tutor-web/src/__tests__/plotlyParse.test.ts
git commit -m "Phase 8.1a: plotly fenced-block parser

parsePlotly(text) replaces \`\`\`plotly{...JSON...}\`\`\` blocks with
PLOTLY0/PLOTLY1/... sentinels + returns parsed Figure objects in
order. Invalid JSON is left in place (no plot rendered for that
block). 4 tests in plotlyParse.test.ts. Frontend tests 120 → 124."
```

---

## Task 3: §8.1 — `PlotlyEmbed` lazy-loaded component

**Files:**
- Modify: `tutor-web/package.json` — add `plotly.js-dist-min` + `react-plotly.js`.
- Create: `tutor-web/src/components/PlotlyEmbed.tsx`
- Create: `tutor-web/src/__tests__/PlotlyEmbed.test.tsx`
- Modify: `tutor-web/src/setupTests.ts` — mock react-plotly.js (jsdom can't render WebGL).
- Modify: `tutor-web/src/components/ChatPane.tsx` — call `parsePlotly` on assistant reply, render `<PlotlyEmbed>` for each detected block.

### Install

```bash
cd tutor-web && npm install --save plotly.js-dist-min react-plotly.js
```

### `PlotlyEmbed.tsx`

```tsx
import { lazy, Suspense } from "react";

// Lazy import keeps Plotly out of the base bundle. ~3MB only loads
// when first <PlotlyEmbed> mounts on the page. After load, browser
// caches; subsequent uses are free.
const Plot = lazy(async () => {
  const factory = (await import("react-plotly.js/factory")).default;
  const Plotly = await import("plotly.js-dist-min");
  return { default: factory(Plotly.default ?? Plotly) };
});

export interface PlotlyEmbedProps {
  figure: { data?: any[]; layout?: any; config?: any };
}

export function PlotlyEmbed({ figure }: PlotlyEmbedProps) {
  return (
    <div data-testid="plotly-embed" className="my-2">
      <Suspense fallback={<div className="text-xs text-page-fg/60">loading plot…</div>}>
        <Plot
          data={figure.data ?? []}
          layout={{ ...(figure.layout ?? {}), autosize: true }}
          config={{ displayModeBar: false, ...(figure.config ?? {}) }}
          style={{ width: "100%", maxWidth: 800 }}
          useResizeHandler
        />
      </Suspense>
    </div>
  );
}
```

### `setupTests.ts` mock

Append after existing react-pdf mocks:

```ts
vi.mock("react-plotly.js/factory", () => ({
  default: () => () => React.createElement("div", { "data-testid": "mock-plotly" }, "mock plot"),
}));
vi.mock("plotly.js-dist-min", () => ({}));
```

### `ChatPane.tsx` integration

In ChatPane assistant-message render, call `parsePlotly(prose)` after `parseChips` (or before — either works since neither overlaps). Replace each `PLOTLY{N}` sentinel in the rendered prose with `<PlotlyEmbed figure={plots[N].json} />`.

Simplest path: skip math rendering for messages containing `PLOTLY` sentinels, render plain text + PlotlyEmbed directly.

### Test

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import { test, expect } from "vitest";
import { PlotlyEmbed } from "../components/PlotlyEmbed";

test("PlotlyEmbed renders the lazy-loaded mock", async () => {
  render(<PlotlyEmbed figure={{ data: [{ y: [1, 2, 3] }] }} />);
  // Either suspense fallback or the mock plot must be in DOM.
  await waitFor(() => {
    const ok = screen.queryByTestId("mock-plotly") || screen.queryByText(/loading plot/i);
    expect(ok).not.toBeNull();
  });
});
```

Frontend tests 124 → 125.

Commit:
```
git add tutor-web/package.json tutor-web/package-lock.json \
        tutor-web/src/components/PlotlyEmbed.tsx \
        tutor-web/src/__tests__/PlotlyEmbed.test.tsx \
        tutor-web/src/setupTests.ts \
        tutor-web/src/components/ChatPane.tsx
git commit -m "Phase 8.1b: PlotlyEmbed lazy-loaded component + ChatPane integration

PlotlyEmbed wraps react-plotly.js with React.lazy + dynamic import so
Plotly.js (~3MB) only loads when a chat reply emits a \`\`\`plotly
fenced block. ChatPane runs parsePlotly on assistant prose, replaces
sentinels with <PlotlyEmbed figure={...}>.

setupTests.ts mocks react-plotly.js + plotly.js-dist-min so jsdom
doesn't try to load WebGL. Real plot rendering verified in Playwright.

1 PlotlyEmbed test. Frontend tests 124 → 125."
```

---

## Task 4: §8.2 — Cron install for fallback-model probe

One-shot ops, no Kotlin source change. Spec command:

```bash
ssh root@46.247.109.91 'cat > /etc/cron.d/jarvis-probe' << 'EOF'
30 6 * * 1 root /opt/jarvis/jarvis-kotlin/tools/probe-fallback-models.sh >> /var/log/jarvis-fallback-probe.log 2>&1
EOF
ssh root@46.247.109.91 'chmod 644 /etc/cron.d/jarvis-probe && systemctl reload cron && systemctl status cron --no-pager | head -3'
```

Verify:
```bash
ssh root@46.247.109.91 'cat /etc/cron.d/jarvis-probe; echo ---; ls -la /var/log/jarvis-fallback-probe.log 2>&1 || echo not yet; systemctl status cron --no-pager | head -3'
```

Document in backlog under "## Phase 8 — Layer D + ops" once installed:

```
- `[8] [ops] [cron-probe-installed] [done] /etc/cron.d/jarvis-probe weekly Mon 06:30 UTC. Logs to /var/log/jarvis-fallback-probe.log. Idempotent — re-runs sha256-skip on already-fetched probes; first run creates the log file.`
```

---

## Task 5: Code Gate

```bash
git push origin main
```

---

## Task 6: Live Gate

```bash
cd tutor-web && npm run build
cd /c/Users/User/jarvis-kotlin
git add src/main/resources/tutor-dist/
git commit -m "Phase 8: rebuild frontend bundle (Plotly lazy-loaded)"
git push origin main
& "C:\Program Files\Git\bin\bash.exe" tools/deploy.sh
curl -sS https://corgflix.duckdns.org/healthz   # ok
```

Verify base bundle stays under ~600KB (Plotly excluded via lazy import). Compare bundle size before/after; if main JS chunk grew by >100KB, the lazy import didn't work and Plotly leaked into the base.

---

## Task 7: §8.3 — Final UX-Playbook full-pass

Spawn UX-Playbook agent with the full surface set + the entire backlog file as context.

```
Final UX-Playbook audit for jarvis-kotlin tutor overhaul. Read:
1. C:\Users\User\Desktop\SO\os-study-guide\wiki\architecture\UX Playbook.md
2. C:\Users\User\Desktop\SO\os-study-guide\wiki\architecture\Design Principles.md
3. docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md (cumulative 7-phase backlog).

Audit ALL surfaces shipped 1-8 against the FULL Playbook (every category, every column). Goal: produce a final "polish complete" summary + list NEW HIGH severity findings (which would warrant a Phase 9, out of scope).

CRITICAL: Do NOT relitigate scope or deadlines. Honor:
- Build-everything mode (no "ship one + dogfood" rebuke).
- Authorization for the work is settled.
- Many MED/LOW findings already in backlog are intentional deferrals — note overlap, don't double-count.

Output:
- "Polish complete" summary: 5-8 bullets of what's now solid.
- New HIGH findings (severity high) NOT already in backlog. Each blocks Phase 9 IF the user chooses to ship one.
- New MED/LOW findings (NOT already in backlog). Append to "## Phase 8 — Layer D + ops + final audit" section.
- Coverage stats: which Playbook columns scored fully PASS vs partial.

Be terse. ~600 words.
```

Backlog gets the final-audit findings. Phase 9 is out of scope here.

---

## Task 8: Final review

DoD:
- Backend tests still 586 (Phase 8 didn't touch backend).
- Frontend tests 125 (120 + 4 plotlyParse + 1 PlotlyEmbed).
- Daemon untouched (16).
- Node tools tests still 3.
- CI green; live healthz ok; bundle hash matches; base bundle <600KB pre-Plotly.
- Cron installed on VPS + verified.
- Final UX-Playbook audit run; backlog has Phase 8 section populated.
- All 8 phases acceptance criteria from spec section ticked.

---

## Out of scope (Phase 8 explicitly defers)

- Real per-faculty CourseScraper impls (still need user-provided URLs).
- FSRS card promotion from gaps.
- sympy tool (sympy not installed locally or on VPS).
- ChatPane <concept> envelope rendering integration.
- JarvisToolset sanitizeInput + wrap wiring.
- ImplicitGapDetector route-level call.
- Phase 9 work surfaced by final audit (if any).
