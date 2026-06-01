import { test, expect } from '@playwright/test';
// Gating net: hit a REAL route with NO API stubs; assert zero 4xx/5xx on first paint
// (CLAUDE.md interaction-smoke). Replaces the all-stubbed smoke as the slice gate.
test('a real route paints with zero 4xx/5xx', async ({ page }) => {
  const bad: string[] = [];
  page.on('response', r => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });
  await page.goto('/');
  await expect(page.locator('body')).toBeVisible();
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx during first paint:\n${bad.join('\n')}`).toEqual([]);
});
