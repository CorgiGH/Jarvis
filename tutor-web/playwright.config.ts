import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  // PM fix 2026-06-12 (Lane-A Task-0 escalation): visual baselines run ONLY via
  // playwright.visual.config.ts (env-locked, custom snapshotPathTemplate). Without this
  // ignore, the default config collects e2e/visual/, finds no baseline at the default
  // *-snapshots/ path, fails 3 specs and writes stray baseline dirs.
  testIgnore: "**/e2e/visual/**",
  use: {
    baseURL: "http://localhost:5173",
    locale: "ro-RO",
    timezoneId: "Europe/Bucharest",
    reducedMotion: "reduce",
  },
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: true,
  },
});
