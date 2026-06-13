/**
 * Plan-6 Task 13 — §6.2 interaction-smoke contract helper (INV-6.5/INV-6.6).
 *
 * Implements the interaction-smoke contract defined in the CLAUDE.md "Interaction-smoke gate":
 *   1. All N spec'd [data-testid] selectors visible on first paint
 *   2. ZERO 4xx/5xx network responses during first paint (captured via page.on('response'))
 *   3. Click every spec-listed interactive element
 *   4. After each click: assert no on-screen text matches /404|HTTP \d{3}|not found|eroare|error/i
 *      AND no new 4xx/5xx network responses fired
 *
 * All spec selectors are the exact REQ-4/6/10/17/21 strings from §0.9 of the plan.
 */

import { expect, type Page } from "@playwright/test";

/** Collected bad responses during a smoke run. */
export interface SmokeCollector {
  bad: string[];
  attachResponseListener: (page: Page) => void;
  assertNoBadResponses: (context: string) => void;
}

/** Create a fresh collector — call once per test, attach to the page, then assert at each gate. */
export function createSmokeCollector(): SmokeCollector {
  const bad: string[] = [];
  return {
    bad,
    attachResponseListener: (page: Page) => {
      page.on("response", (r) => {
        if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`);
      });
    },
    assertNoBadResponses: (context: string) => {
      expect(bad, `4xx/5xx during ${context}:\n${bad.join("\n")}`).toEqual([]);
    },
  };
}

/** Assert no error text matches the smoke contract regex on the current page. */
export async function assertNoErrorText(page: Page, context: string): Promise<void> {
  await expect(
    page.getByText(/404|HTTP \d{3}|not found|eroare|error/i),
    `error text visible after ${context}`,
  ).toHaveCount(0);
}

/**
 * Full smoke check: assert no error text AND no 4xx/5xx responses.
 * Call after every interactive action per the smoke contract.
 */
export async function smokeCheck(
  page: Page,
  collector: SmokeCollector,
  context: string,
): Promise<void> {
  await assertNoErrorText(page, context);
  collector.assertNoBadResponses(context);
}
