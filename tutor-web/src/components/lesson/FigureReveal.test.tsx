import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { parse as parseYaml } from "yaml";
import { FigureReveal } from "./FigureReveal";
import type { ApiFigureBinding } from "../../lib/lesson";

/** Raw-import the real viz YAML so the mock reply is the ACTUAL instance shape. */
import mergesortYamlRaw from "../../../../content/PA/viz/viz-pa-mergesort-001.yaml?raw";

// Build the §0.9B reply shape from the real YAML
const YAML_DOC = parseYaml(mergesortYamlRaw) as {
  id: string;
  subject: string;
  family_id: string;
  language: string;
  instance: { data_json: string };
};
const VIZ_REPLY = {
  id: YAML_DOC.id,
  subject: YAML_DOC.subject,
  family_id: YAML_DOC.family_id,
  language: YAML_DOC.language,
  data_json: YAML_DOC.instance.data_json,
};

const FIGURE_BINDING: ApiFigureBinding = {
  family_id: "graph-tree",
  instance_id: "viz-pa-mergesort-001",
};

const REVEAL_STEPS = [
  { text: "Pas 1 explicație", callout: "callout 1" },
  { text: "Pas 2 explicație", callout: "callout 2" },
];

function stubFetchOk() {
  const fetchMock = vi.fn(async () =>
    new Response(JSON.stringify(VIZ_REPLY), {
      status: 200,
      headers: { "content-type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function stubFetchReject() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => { throw new Error("network failure"); }),
  );
}

function stubFetchNotFound() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response("", { status: 404 })),
  );
}

function stubFetchUnknownFamily() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      new Response(JSON.stringify({ ...VIZ_REPLY, family_id: "unknown-family-xyz" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    ),
  );
}

beforeEach(() => {
  // Reset the location hash between tests — AlgoStepperShell deep-links its frame index into
  // window.location.hash (#graph-tree-idx-N); without this reset the prior test's final frame
  // leaks into the next mount's initialIdx (same pattern as AlgoStepperShell.test.tsx).
  window.location.hash = "";
  Object.defineProperty(document, "cookie", {
    value: "csrf=test",
    configurable: true,
    writable: true,
  });
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("FigureReveal — happy path", () => {
  it("mounts the graph-tree family root after fetching the instance", async () => {
    stubFetchOk();
    render(
      <FigureReveal
        figure={FIGURE_BINDING}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() =>
      expect(screen.getByTestId("graph-tree-root")).toBeInTheDocument(),
    );
  });

  it("shows scrubber-step-counter as 'pas 1/8' on first paint (8 steps in the mergesort instance)", async () => {
    stubFetchOk();
    render(
      <FigureReveal
        figure={FIGURE_BINDING}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() =>
      expect(screen.getByTestId("scrubber-step-counter")).toHaveTextContent("pas 1/8"),
    );
  });

  it("stepping forward updates the counter", async () => {
    stubFetchOk();
    render(
      <FigureReveal
        figure={FIGURE_BINDING}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() => expect(screen.getByTestId("graph-tree-root")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("graph-tree-step-fwd"));
    await waitFor(() =>
      expect(screen.getByTestId("scrubber-step-counter")).toHaveTextContent("pas 2/8"),
    );
  });

  it("back control exists and functions: forward -> back -> counter decrements", async () => {
    stubFetchOk();
    render(
      <FigureReveal
        figure={FIGURE_BINDING}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() => expect(screen.getByTestId("graph-tree-root")).toBeInTheDocument());
    // step forward first
    fireEvent.click(screen.getByTestId("graph-tree-step-fwd"));
    await waitFor(() =>
      expect(screen.getByTestId("scrubber-step-counter")).toHaveTextContent("pas 2/8"),
    );
    // now back
    fireEvent.click(screen.getByTestId("graph-tree-step-back"));
    await waitFor(() =>
      expect(screen.getByTestId("scrubber-step-counter")).toHaveTextContent("pas 1/8"),
    );
  });

  it("reset returns to 'pas 1/8' AFTER reaching the final frame (replay possible)", async () => {
    stubFetchOk();
    render(
      <FigureReveal
        figure={FIGURE_BINDING}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() => expect(screen.getByTestId("graph-tree-root")).toBeInTheDocument());
    // step to the final frame (8 steps, need 7 clicks)
    for (let i = 0; i < 7; i++) {
      fireEvent.click(screen.getByTestId("graph-tree-step-fwd"));
    }
    await waitFor(() =>
      expect(screen.getByTestId("scrubber-step-counter")).toHaveTextContent("pas 8/8"),
    );
    // reset
    fireEvent.click(screen.getByTestId("graph-tree-reset"));
    await waitFor(() =>
      expect(screen.getByTestId("scrubber-step-counter")).toHaveTextContent("pas 1/8"),
    );
  });

  it("onGateClear fires exactly once when the final frame is first reached", async () => {
    stubFetchOk();
    const onGateClear = vi.fn();
    render(
      <FigureReveal
        figure={FIGURE_BINDING}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={onGateClear}
      />,
    );
    await waitFor(() => expect(screen.getByTestId("graph-tree-root")).toBeInTheDocument());
    expect(onGateClear).not.toHaveBeenCalled();
    // step to the final frame
    for (let i = 0; i < 7; i++) {
      fireEvent.click(screen.getByTestId("graph-tree-step-fwd"));
    }
    await waitFor(() => expect(onGateClear).toHaveBeenCalledTimes(1));
    // stepping again (e.g. reset + re-reach) does NOT fire again (once-only)
    fireEvent.click(screen.getByTestId("graph-tree-reset"));
    for (let i = 0; i < 7; i++) {
      fireEvent.click(screen.getByTestId("graph-tree-step-fwd"));
    }
    await waitFor(() => expect(onGateClear).toHaveBeenCalledTimes(1));
  });

  it("beat-figure-scrubber wrapper is present with exactly ONE scrubber-step-counter", async () => {
    stubFetchOk();
    render(
      <FigureReveal
        figure={FIGURE_BINDING}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() => expect(screen.getByTestId("graph-tree-root")).toBeInTheDocument());
    expect(screen.getByTestId("beat-figure-scrubber")).toBeInTheDocument();
    // exactly ONE scrubber-step-counter — no duplicate
    expect(screen.getAllByTestId("scrubber-step-counter")).toHaveLength(1);
  });
});

describe("FigureReveal — failure fallbacks", () => {
  it("fetch reject -> renders stepped-text fallback + figureFallback message, no thrown error", async () => {
    // suppress console.warn so the test output stays clean
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    stubFetchReject();
    render(
      <FigureReveal
        figure={FIGURE_BINDING}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() =>
      expect(screen.getByTestId("figure-fallback-message")).toBeInTheDocument(),
    );
    // stepped-text fallback uses beat-figure-scrubber
    expect(screen.getByTestId("beat-figure-scrubber")).toBeInTheDocument();
    warnSpy.mockRestore();
  });

  it("fetch 404 -> renders stepped-text fallback, no thrown error", async () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    stubFetchNotFound();
    render(
      <FigureReveal
        figure={{ family_id: "graph-tree", instance_id: "viz-unknown" }}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() =>
      expect(screen.getByTestId("figure-fallback-message")).toBeInTheDocument(),
    );
    warnSpy.mockRestore();
  });

  it("unknown family_id in fetched reply -> renders stepped-text fallback, no thrown error", async () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    stubFetchUnknownFamily();
    render(
      <FigureReveal
        figure={{ family_id: "unknown-family-xyz", instance_id: "viz-pa-mergesort-001" }}
        steps={REVEAL_STEPS}
        predictedOption={null}
        onGateClear={vi.fn()}
      />,
    );
    await waitFor(() =>
      expect(screen.getByTestId("figure-fallback-message")).toBeInTheDocument(),
    );
    warnSpy.mockRestore();
  });
});
