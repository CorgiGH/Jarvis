import { render } from "@testing-library/react";
import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { axe } from "vitest-axe";
import { MemoryRouter } from "react-router-dom";
import { DrillStack } from "../components/DrillStack";

beforeEach(() => {
  vi.stubGlobal("matchMedia", vi.fn((query: string) => ({
    matches: query === "(prefers-reduced-motion: reduce)",
    media: query, onchange: null,
    addListener: vi.fn(), removeListener: vi.fn(),
    addEventListener: vi.fn(), removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 200 })));
});
afterEach(() => { vi.unstubAllGlobals(); });

const STUB_CONTENT = {
  drill: "Sample: x = (3,7,8,9,14). Find the MLE of μ for Laplace(μ,b).",
  worked: "Σ|x_i − μ| is minimised at the sample median. MLE = 8.",
  definition: "Laplace MLE: μ̂ = sample median (L1 estimator).",
  check: "Transfer: for sample (2,5,10,11,12), what is the Laplace MLE?",
  expectedAnswerHint: "median equals 8",
};

test("DrillStack initial state has no axe violations", async () => {
  const { container } = render(
    <MemoryRouter>
      <DrillStack
        taskId="T1"
        problemId="A1"
        content={STUB_CONTENT}
        onProblemComplete={vi.fn()}
      />
    </MemoryRouter>
  );
  await new Promise((r) => setTimeout(r, 0));
  const results = await axe(container, { rules: { "color-contrast": { enabled: false } } });
  expect(results).toHaveNoViolations();
});
