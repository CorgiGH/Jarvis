import { defineConfig } from "@playwright/test";

/**
 * Plan-4a Task 4 (spec §9.2 g5/g6, §0.9D) — env-locked visual-baseline config, SEPARATE from
 * playwright.config.ts. testDir is e2e/visual ONLY, so the lesson specs under e2e/ can NEVER grow
 * a baseline (structural scope enforcement; INV-9.5 double-checks it). Env-lock = deterministic PNGs:
 *   workers:1            — no parallel render races
 *   deviceScaleFactor:1  — 1 physical px = 1 CSS px (HiDPI machines would otherwise 2x the PNG)
 *   headless + reduced motion — no animation frame in the capture
 *   snapshotPathTemplate — every baseline lands under e2e/visual/__screenshots__/<spec>/<name>
 *
 * The three permitted surfaces (and ONLY these three): /tutor/ (AppShell fixed shell) +
 * /theme-ref.html?surface=dark + /theme-ref.html?surface=light (the 2 DESIGN.md theme reference
 * pages — "Two surfaces, never a third"). All served by the worktree's own `npm run dev` (Vite)
 * with NO Kotlin backend — shell.visual stubs the first-paint /api contract; the theme-ref pages
 * are static harness HTML and need no stub.
 */
export default defineConfig({
  testDir: "./e2e/visual",
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  reporter: "list",
  snapshotPathTemplate: "{testDir}/__screenshots__/{testFilePath}/{arg}{ext}",
  use: {
    baseURL: "http://localhost:5173",
    locale: "ro-RO",
    timezoneId: "Europe/Bucharest",
    reducedMotion: "reduce",
    deviceScaleFactor: 1,
    headless: true,
    viewport: { width: 1280, height: 900 },
  },
  expect: {
    // Exact match — drift in the fixed shell / gallery is a real signal, not noise.
    toHaveScreenshot: { maxDiffPixelRatio: 0, animations: "disabled" },
  },
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
