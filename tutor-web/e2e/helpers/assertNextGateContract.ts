import { expect, type Page } from "@playwright/test";

/**
 * Plan-4b Task 8 / gate 4c — the next-gate interaction contract.
 *
 * Same throw-with-violation-list shape as assertNoClip.ts.
 *
 * The gate contract (§0.9I, §4.1):
 *  - `beat-next-gate` has the `disabled` attribute whenever the active beat's
 *    gate condition is uncleared (checked at the defined probe points).
 *  - Clicking while logically gated NEVER advances `lesson-beat-pips`'s active pip index.
 *
 * This helper asserts BOTH halves at a probe point where the gate SHOULD be locked:
 *  1. `beat-next-gate` is disabled.
 *  2. Clicking it does NOT advance the pips.
 *
 * For the "should be open" verification, the caller simply uses Playwright's
 * `expect(page.getByTestId("beat-next-gate")).toBeEnabled()` — that is not this helper's job.
 *
 * Usage: call `assertNextGateContract(page)` at any point where you believe the gate
 * should be LOCKED (before completing the current beat).
 *
 * Throws with a violation list if either invariant is broken.
 */
export async function assertNextGateContract(page: Page): Promise<void> {
  // 1. Assert the button is disabled
  const btn = page.getByTestId("beat-next-gate");
  const isDisabled = await btn.evaluate((el: HTMLButtonElement) => el.disabled);

  // Record the active pip index before any click
  const activePipsBefore = await page.getByTestId("lesson-beat-pips").evaluate((pipsEl) => {
    const pips = Array.from(pipsEl.querySelectorAll("[data-pip]"));
    // The active pip is the one styled differently — find by presence of bg-accent class
    // (the BeatOrchestrator marks the active pip with border-accent + bg-accent).
    // Fallback: count pips before any pip with bg-accent.
    return pips.findIndex((p) => p.classList.contains("bg-accent"));
  });

  if (!isDisabled) {
    throw new Error(
      `assertNextGateContract: beat-next-gate is NOT disabled at this probe point ` +
      `(active pip index ${activePipsBefore}). ` +
      `The gate must be locked until the active beat's gate condition clears (§4.1).`,
    );
  }

  // 2. Click the disabled button (should be a no-op) and verify pips don't advance
  // We use `dispatchEvent` so Playwright doesn't auto-wait for enabled state
  await btn.evaluate((el) => el.click());
  // Small settling wait — if clicking somehow triggered a state update it would render
  await page.waitForTimeout(150);

  const activePipsAfter = await page.getByTestId("lesson-beat-pips").evaluate((pipsEl) => {
    const pips = Array.from(pipsEl.querySelectorAll("[data-pip]"));
    return pips.findIndex((p) => p.classList.contains("bg-accent"));
  });

  expect(
    activePipsAfter,
    `assertNextGateContract: clicking the gated button advanced the active pip from ` +
    `${activePipsBefore} to ${activePipsAfter} — the gate must prevent progression (§4.1)`,
  ).toBe(activePipsBefore);
}
