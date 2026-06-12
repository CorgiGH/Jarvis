import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { BeatOrchestrator } from "./BeatOrchestrator";
import type { ApiLessonReply } from "../../lib/lesson";
import type { ApiBeatGradeReply } from "../../lib/beatGrade";

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

/** A FULL-plan definition-taxonomy lesson fixture -- choice variant, no figure. */
const FULL_LESSON: ApiLessonReply = {
  kcId: "pa-kc-001",
  kc_name_ro: "Noţiunea de algoritm",
  kc_name_en: "The notion of an algorithm",
  concrete_question_ro: null,
  echo_source_ro: null,
  prediction_options: [],
  term_ro: "Noţiunea de algoritm",
  definition_ro: null,
  explanation_ro: null,
  worked_example_ro: null,
  provenance: { type: "authored", hasBeenFaithfulChecked: true },
  beats: {
    plan: ["predict", "attempt", "reveal", "name", "check"],
    concept_type: "definition-taxonomy",
    predict: {
      prompt: "Care dintre acestea este un algoritm?",
      options: [
        {
          text: "O reţetă cu pași neambigui care se oprește",
          callback: "Exact — pași clari, finit.",
          correct: true,
        },
        {
          text: "O instrucţiune vagă",
          callback: "Prea vag — nu e neambiguu.",
          correct: false,
        },
        {
          text: "Un calcul care nu se oprește niciodată",
          callback: "Nu se termină în timp finit.",
          correct: false,
        },
      ],
    },
    attempt: {
      statement: "Clasifică: repetă la nesfarșit.",
      choices: [
        { text: "Algoritm", correct: false, feedback: "Nu se oprește în timp finit." },
        { text: "Nu e algoritm", correct: true, feedback: "Corect — încalcă condiţia de terminare." },
      ],
      skeleton_rows: [],
      trace_steps: [],
      input_schema: null,
      feedback_correct: "Bun — ai prins condiţia de terminare.",
    },
    reveal: {
      steps: [
        {
          text: "Un algoritm este o colecţie bine ordonată de operaţii.",
          callout: "well-ordered collection of operations",
        },
        {
          text: "Operaţiile sunt neambigue și efectiv calculabile.",
          callout: "fiecare pas e clar și executabil",
        },
        {
          text: "Execuţia produce un rezultat și se oprește în timp finit.",
          callout: "terminarea e obligatorie",
        },
      ],
      figure: null,
    },
    name: {
      definition:
        "Algoritm = colecţie bine ordonată de operaţii neambigue, efectiv calculabile, care produc un rezultat și se opresc în timp finit.",
      invariant_statement: "Dacă execuţia nu se termină, nu e algoritm.",
      why_matters: "Fără terminare nu poţi garanta un rezultat.",
    },
    check: {
      item_stem: "Este adună a și b, afișează suma, oprește un algoritm?",
      choices: [
        { text: "Da", correct: true, feedback: "Corect — pași neambigui, finit." },
        { text: "Nu", correct: false, feedback: "Ba da — îndeplinește toate condiţiile." },
      ],
      numeric_answer: null,
      numeric_tolerance: null,
    },
  },
};

function gradeReply(over: Partial<ApiBeatGradeReply> = {}): ApiBeatGradeReply {
  return {
    correct: true,
    score: 1.0,
    feedback_ro: "Corect.",
    beat_type: "predict",
    lesson_complete: false,
    first_encounter: true,
    phase: null,
    verification_status: null,
    ...over,
  };
}

function stubFetch(handler?: (url: string, body: any) => ApiBeatGradeReply) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/lesson/") && url.endsWith("/beat")) {
      const body = init?.body ? JSON.parse(init.body as string) : {};
      const reply = handler ? handler(url, body) : gradeReply({ beat_type: body.beat_type });
      return new Response(JSON.stringify(reply), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

beforeEach(() => {
  mockReducedMotion(false);
  Object.defineProperty(document, "cookie", { value: "csrf=test", configurable: true, writable: true });
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function renderOrch(onComplete = vi.fn()) {
  return render(
    <MemoryRouter>
      <BeatOrchestrator kcId="pa-kc-001" lesson={FULL_LESSON} onComplete={onComplete} />
    </MemoryRouter>,
  );
}

describe("BeatOrchestrator first paint", () => {
  it("renders pips matching the plan length, one active beat, and a disabled Next gate", () => {
    stubFetch();
    renderOrch();
    expect(screen.getByTestId("lesson-beat-pips").querySelectorAll("[data-pip]")).toHaveLength(5);
    expect(screen.getByTestId("lesson-beat-active")).toBeInTheDocument();
    expect(screen.getByTestId("beat-predict-options")).toBeInTheDocument();
    expect(screen.getByTestId("beat-next-gate")).toBeDisabled();
  });
});

describe("BeatOrchestrator predict gate", () => {
  it("Next stays disabled until the predict POST resolves, then enables", async () => {
    const fetchMock = stubFetch();
    renderOrch();
    expect(screen.getByTestId("beat-next-gate")).toBeDisabled();
    fireEvent.click(screen.getByTestId("beat-predict-options").querySelectorAll("button")[0]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    const calls = fetchMock.mock.calls.filter((c) => String(c[0]).endsWith("/beat"));
    expect(calls).toHaveLength(1);
    expect(JSON.parse(calls[0][1].body as string).beat_type).toBe("predict");
  });
});

describe("BeatOrchestrator reveal echo + dwell", () => {
  it("echoes the chosen predict option callback at reveal start", async () => {
    stubFetch();
    renderOrch();
    // commit predict (option 0)
    fireEvent.click(screen.getByTestId("beat-predict-options").querySelectorAll("button")[0]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate")); // -> attempt
    // submit attempt (correct choice index 1)
    fireEvent.click(screen.getAllByTestId("attempt-choice")[1]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate")); // -> reveal
    // echo banner shows the predict option 0 callback
    expect(screen.getByTestId("reveal-echo")).toHaveTextContent("Exact");
  });

  it("dwell blocks Next until the per-step timer elapses on the final step", async () => {
    vi.useFakeTimers();
    stubFetch();
    renderOrch();
    // advance to reveal
    fireEvent.click(screen.getByTestId("beat-predict-options").querySelectorAll("button")[0]);
    await act(async () => { await vi.runOnlyPendingTimersAsync(); });
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    fireEvent.click(screen.getAllByTestId("attempt-choice")[1]);
    await act(async () => { await vi.runOnlyPendingTimersAsync(); });
    fireEvent.click(screen.getByTestId("beat-next-gate")); // -> reveal, step 1 of 3
    // step to the final reveal step
    fireEvent.click(screen.getByTestId("beat-figure-scrubber").querySelector("[data-step-fwd]")!);
    fireEvent.click(screen.getByTestId("beat-figure-scrubber").querySelector("[data-step-fwd]")!);
    expect(screen.getByTestId("scrubber-step-counter")).toHaveTextContent("pas 3/3");
    // dwell not yet met -> still disabled
    expect(screen.getByTestId("beat-next-gate")).toBeDisabled();
    // elapse the dwell floor
    await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
    expect(screen.getByTestId("beat-next-gate")).toBeEnabled();
  });
});

describe("BeatOrchestrator full traversal + completion", () => {
  it("clicks through all 5 beats, fires the check POST, and renders the completion handoff", async () => {
    mockReducedMotion(true);
    const onComplete = vi.fn();
    const fetchMock = stubFetch((_url, body) =>
      gradeReply({
        beat_type: body.beat_type,
        lesson_complete: body.beat_type === "check",
        phase: body.beat_type === "check" ? "practice" : null,
      }),
    );
    renderOrch(onComplete);

    // beat 1: predict
    fireEvent.click(screen.getByTestId("beat-predict-options").querySelectorAll("button")[0]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    // beat 2: attempt
    fireEvent.click(screen.getAllByTestId("attempt-choice")[1]);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    // beat 3: reveal -- step to the end (3 steps; start at 1, advance twice)
    fireEvent.click(screen.getByTestId("beat-figure-scrubber").querySelector("[data-step-fwd]")!);
    fireEvent.click(screen.getByTestId("beat-figure-scrubber").querySelector("[data-step-fwd]")!);
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    // beat 4: name -- text-only beat; reduced motion -> dwell=0 -> Next enabled immediately
    await waitFor(() => expect(screen.getByTestId("beat-next-gate")).toBeEnabled());
    fireEvent.click(screen.getByTestId("beat-next-gate"));
    // beat 5: check
    fireEvent.click(screen.getAllByTestId("check-choice")[0]);
    await waitFor(() => expect(screen.getByTestId("lesson-complete-handoff")).toBeInTheDocument());

    const beatCalls = fetchMock.mock.calls.filter((c) => String(c[0]).endsWith("/beat"));
    expect(beatCalls.map((c) => JSON.parse(c[1].body as string).beat_type)).toEqual([
      "predict", "attempt", "check",
    ]); // reveal + name are not graded (no POST)
    fireEvent.click(screen.getByTestId("lesson-complete-handoff"));
    expect(onComplete).toHaveBeenCalledWith("pa-kc-001");
  });
});
