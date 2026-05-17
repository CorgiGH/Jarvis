import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { DrillStack } from "../components/DrillStack";
import type { GradeResult } from "../lib/drillGrader";

const DRILL_CONTENT = {
  drill: "What is the MLE of μ for Laplace(μ, b) given sample (3,7,8,9,14)?",
  worked: "The MLE is the sample median = 8. Proof: argmin Σ|x_i − μ| at median.",
  definition: "The Laplace distribution has pdf: (1/2b)exp(−|x−μ|/b). L2 estimator is mean; L1 is median.",
  check: "Transfer: for sample (2,5,10,11,12), what is the Laplace MLE?",
  expectedAnswerHint: "median equals 8 for original; for transfer: 10",
};

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

function makeGradeOkResponse(result: GradeResult): Response {
  return new Response(JSON.stringify(result), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}

beforeEach(() => {
  mockReducedMotion(false);
  Object.defineProperty(document, "cookie", {
    value: "csrf=test-csrf",
    configurable: true,
    writable: true,
  });
  vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 200 })));
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("DrillStack initial render", () => {
  test("renders DRILL card open and WORKED/DEFINITION/CHECK locked", () => {
    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );

    const cards = screen.getAllByTestId("drill-card");
    expect(cards).toHaveLength(4);

    // First card (DRILL) should be open — no lock message
    expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(3);

    // Drill input textarea and submit button present
    expect(screen.getByTestId("drill-attempt-input")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /check answer/i })).toBeInTheDocument();
  });

  test("DRILL content text appears in first open card", () => {
    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );
    expect(screen.getByText(/MLE of μ for Laplace/)).toBeInTheDocument();
  });

  test("renders give-up button", () => {
    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );
    expect(
      screen.getByRole("button", { name: /give up/i })
    ).toBeInTheDocument();
  });
});

describe("DrillStack correct-answer path", () => {
  test("on correct grade → WORKED, DEFINITION, CHECK unlock (lock messages disappear)", async () => {
    const correctResult: GradeResult = {
      correct: true,
      score: 1.0,
      rubric: { numeric: true, mechanism: true, justification: true },
      misconception: null,
      elaboratedFeedback: "✓ correct. Median = 8.",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
          return makeGradeOkResponse(correctResult);
        }
        return new Response("{}", { status: 200 });
      })
    );

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByTestId("drill-attempt-input"), {
      target: { value: "μ̂ = 8, the sample median" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check answer/i }));

    await waitFor(() =>
      expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(0)
    );

    // Elaborated feedback appears
    expect(screen.getByTestId("grade-feedback")).toBeInTheDocument();
    expect(screen.getByTestId("grade-feedback").textContent).toContain("correct");
  });

  test("on correct grade → onProblemComplete is not called yet (check card still pending)", async () => {
    const correctResult: GradeResult = {
      correct: true,
      score: 1.0,
      rubric: { numeric: true, mechanism: true, justification: true },
      misconception: null,
      elaboratedFeedback: "✓ correct.",
    };
    const onComplete = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
          return makeGradeOkResponse(correctResult);
        }
        return new Response("{}", { status: 200 });
      })
    );

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={onComplete}
        />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByTestId("drill-attempt-input"), {
      target: { value: "median = 8" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check answer/i }));
    await waitFor(() =>
      expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(0)
    );
    // onProblemComplete fires only when CHECK is also marked done
    expect(onComplete).not.toHaveBeenCalled();
  });
});

describe("DrillStack misconception path", () => {
  test("on incorrect grade → misconception banner appears, cards stay locked", async () => {
    const wrongResult: GradeResult = {
      correct: false,
      score: 0.0,
      rubric: { numeric: false, mechanism: false, justification: false },
      misconception: "L2_ESTIMATOR_CONFUSION",
      elaboratedFeedback:
        "You computed the mean (8.2). For Laplace, the L1 MLE is the median.",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
          return makeGradeOkResponse(wrongResult);
        }
        return new Response("{}", { status: 200 });
      })
    );

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByTestId("drill-attempt-input"), {
      target: { value: "μ̂ = 8.2 (sum / n)" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check answer/i }));

    await waitFor(() =>
      expect(screen.getByTestId("grade-feedback")).toBeInTheDocument()
    );

    expect(screen.getByTestId("grade-feedback").textContent).toContain(
      "mean"
    );
    expect(screen.getByTestId("misconception-banner")).toBeInTheDocument();
    // Cards remain locked
    expect(screen.getAllByTestId("card-lock-message")).toHaveLength(3);
  });
});

describe("DrillStack giveUp path", () => {
  test("give up → POSTs giveUp=true with empty userAttempt + unlocks all cards", async () => {
    const giveUpResult: GradeResult = {
      correct: false,
      score: 0.0,
      rubric: { numeric: false, mechanism: false, justification: false },
      misconception: null,
      elaboratedFeedback: "the student gave up before answering.",
    };
    const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
      if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
        return makeGradeOkResponse(giveUpResult);
      }
      return new Response("{}", { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /give up/i }));

    await waitFor(() =>
      expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(0)
    );

    // Verify the grade call signals giveUp + does NOT leak the legacy
    // ATTEMPTED_NOT_SOLVED sentinel (which pre-2026-05-17 was echoed into
    // elaboratedFeedback by the LLM grader as SCREAMING_SNAKE in the UI).
    const gradeCalls = fetchMock.mock.calls.filter(
      (c) =>
        typeof c[0] === "string" && c[0].includes("/api/v1/drill/grade")
    );
    expect(gradeCalls).toHaveLength(1);
    const body = JSON.parse(gradeCalls[0][1].body as string);
    expect(body.giveUp).toBe(true);
    expect(body.userAttempt).toBe("");
    expect(body.userAttempt).not.toContain("ATTEMPTED_NOT_SOLVED");
  });
});

describe("DrillStack CHECK completion", () => {
  test("clicking Mark CHECK Done fires onProblemComplete", async () => {
    const correctResult: GradeResult = {
      correct: true,
      score: 1.0,
      rubric: { numeric: true, mechanism: true, justification: true },
      misconception: null,
      elaboratedFeedback: "✓ correct.",
    };
    const onComplete = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
          return makeGradeOkResponse(correctResult);
        }
        return new Response("{}", { status: 200 });
      })
    );

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={onComplete}
        />
      </MemoryRouter>
    );

    // Grade drill first
    fireEvent.change(screen.getByTestId("drill-attempt-input"), {
      target: { value: "median = 8" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check answer/i }));
    await waitFor(() =>
      expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(0)
    );

    // Now complete the CHECK card
    fireEvent.click(screen.getByRole("button", { name: /mark check done/i }));
    expect(onComplete).toHaveBeenCalledWith("A3");
  });
});
