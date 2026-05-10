import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { CompileSubmitCard } from "../components/CompileSubmitCard";

const ANSWERS = [
  { problemId: "A1", attempt: "μ̂ = 8 (sample median)" },
  { problemId: "A2", attempt: "σ̂² = 6.4 (MLE variance)" },
  { problemId: "A3", attempt: "MLE for Laplace is median" },
];

function mockReducedMotion(reduced: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: reduced && query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

beforeEach(() => {
  mockReducedMotion(false);
  Object.defineProperty(document, "cookie", {
    value: "csrf=submit-csrf",
    configurable: true,
    writable: true,
  });
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response("{}", { status: 200 }))
  );
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("CompileSubmitCard rendering", () => {
  test("renders card with COMPILE & SUBMIT heading", () => {
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    expect(screen.getByTestId("compile-submit-card")).toBeInTheDocument();
    expect(screen.getByTestId("compile-submit-heading").textContent).toMatch(
      /COMPILE & SUBMIT/i
    );
  });

  test("renders each problem answer in the LaTeX export block", () => {
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    const exportBlock = screen.getByTestId("latex-export");
    expect(exportBlock.textContent).toContain("A1");
    expect(exportBlock.textContent).toContain("μ̂ = 8 (sample median)");
    expect(exportBlock.textContent).toContain("A2");
    expect(exportBlock.textContent).toContain("σ̂² = 6.4");
    expect(exportBlock.textContent).toContain("A3");
  });

  test("renders MARK SUBMITTED button", () => {
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    expect(
      screen.getByRole("button", { name: /mark submitted/i })
    ).toBeInTheDocument();
  });

  test("under normal motion, card has slide-up animation class", () => {
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    const card = screen.getByTestId("compile-submit-card");
    expect(card.className).toContain("animate-slide-up");
  });

  test("under prefers-reduced-motion, no animation class", () => {
    mockReducedMotion(true);
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    const card = screen.getByTestId("compile-submit-card");
    expect(card.className).not.toContain("animate-slide-up");
  });
});

describe("CompileSubmitCard submit flow", () => {
  test("clicking MARK SUBMITTED POSTs to /api/v1/tasks/T1/submit with CSRF header", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /mark submitted/i }));

    await waitFor(() => {
      const calls = fetchMock.mock.calls;
      const submitCall = calls.find(
        (c) => typeof c[0] === "string" && c[0].includes("/api/v1/tasks/T1/submit")
      );
      expect(submitCall).toBeDefined();
      expect(submitCall![1].method).toBe("POST");
      expect(submitCall![1].headers["X-CSRF-Token"]).toBe("submit-csrf");
    });
  });

  test("after submit, button shows SUBMITTED and is disabled", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("{}", { status: 200 }))
    );

    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /mark submitted/i }));

    await waitFor(() => {
      const btn = screen.getByTestId("submit-button");
      expect(btn.textContent).toMatch(/SUBMITTED/i);
      expect(btn).toBeDisabled();
    });
  });

  test("submit failure shows error message, button re-enabled", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("server error", { status: 500 }))
    );

    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /mark submitted/i }));

    await waitFor(() =>
      expect(screen.getByTestId("submit-error")).toBeInTheDocument()
    );
    const btn = screen.getByTestId("submit-button");
    expect(btn).not.toBeDisabled();
  });

  test("submit body includes stitched LaTeX text for all problems", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /mark submitted/i }));

    await waitFor(() => {
      const submitCall = fetchMock.mock.calls.find(
        (c) => typeof c[0] === "string" && c[0].includes("/api/v1/tasks/T1/submit")
      );
      expect(submitCall).toBeDefined();
      const body = JSON.parse(submitCall![1].body as string);
      expect(body.note).toContain("A1");
      expect(body.note).toContain("μ̂ = 8");
      expect(body.note).toContain("A3");
    });
  });
});
