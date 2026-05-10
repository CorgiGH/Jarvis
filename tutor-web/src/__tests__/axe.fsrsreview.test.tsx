import { render } from "@testing-library/react";
import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { axe } from "vitest-axe";
import { FsrsReview } from "../components/FsrsReview";

vi.mock("../lib/fsrsClient", () => ({
  getDue: vi.fn(),
  getForecast: vi.fn(),
  gradeCard: vi.fn(),
}));

import * as fsrsClient from "../lib/fsrsClient";

const STUB_CARDS = [{
  id: "c1",
  front: "What is the MLE of μ for Laplace(μ,b)?",
  back: "The sample median.",
  sourceTaskId: "T1",
  difficulty: 2.0, stability: 1.0, retrievability: 0.9,
  dueAt: new Date().toISOString(), lapses: 0,
}];
const STUB_FORECAST = { tomorrow: 4, thisWeek: 18, thisMonth: 41 };

beforeEach(() => {
  vi.stubGlobal("matchMedia", vi.fn((query: string) => ({
    matches: query === "(prefers-reduced-motion: reduce)",
    media: query, onchange: null,
    addListener: vi.fn(), removeListener: vi.fn(),
    addEventListener: vi.fn(), removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })));
  vi.mocked(fsrsClient.getDue).mockResolvedValue(STUB_CARDS);
  vi.mocked(fsrsClient.getForecast).mockResolvedValue(STUB_FORECAST);
  vi.mocked(fsrsClient.gradeCard).mockResolvedValue({
    cardId: "c1", nextDueAt: new Date().toISOString(), newDifficulty: 2.1, newStability: 1.5,
  });
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

test("FsrsReview front-of-card state has no axe violations", async () => {
  const { container } = render(<FsrsReview streak={3} />);
  await new Promise((r) => setTimeout(r, 0));
  const results = await axe(container, { rules: { "color-contrast": { enabled: false } } });
  expect(results).toHaveNoViolations();
});

test("FsrsReview empty queue has no axe violations", async () => {
  vi.mocked(fsrsClient.getDue).mockResolvedValue([]);
  vi.mocked(fsrsClient.getForecast).mockResolvedValue({ tomorrow: 0, thisWeek: 0, thisMonth: 0 });
  const { container } = render(<FsrsReview streak={0} />);
  await new Promise((r) => setTimeout(r, 0));
  const results = await axe(container, { rules: { "color-contrast": { enabled: false } } });
  expect(results).toHaveNoViolations();
});
