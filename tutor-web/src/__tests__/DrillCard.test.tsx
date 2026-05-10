import { render, screen, fireEvent } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { DrillCard, DrillCardState } from "../components/DrillCard";

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
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("DrillCard ped-tag rendering", () => {
  test("DRILL card shows [PRACTICE] ped-tag", () => {
    render(
      <DrillCard
        cardType="DRILL"
        title="③ DRILL · YOUR TURN"
        state="open"
        staggerIndex={0}
      >
        <p>Attempt the problem.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("ped-tag").textContent).toBe("[PRACTICE]");
  });

  test("WORKED card shows [CONCRETE] ped-tag", () => {
    render(
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state="open"
        staggerIndex={1}
      >
        <p>Worked solution.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("ped-tag").textContent).toBe("[CONCRETE]");
  });

  test("DEFINITION card shows [CONCRETE] ped-tag", () => {
    render(
      <DrillCard
        cardType="DEFINITION"
        title="① DEFINITION"
        state="open"
        staggerIndex={2}
      >
        <p>Definition body.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("ped-tag").textContent).toBe("[CONCRETE]");
  });

  test("CHECK card shows [CHECK] ped-tag", () => {
    render(
      <DrillCard
        cardType="CHECK"
        title="④ CHECK · TRANSFER"
        state="open"
        staggerIndex={3}
      >
        <p>Transfer question.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("ped-tag").textContent).toBe("[CHECK]");
  });
});

describe("DrillCard lock state", () => {
  test("locked card hides body and shows lock message", () => {
    render(
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state="locked"
        staggerIndex={1}
      >
        <p data-testid="secret-body">Secret worked solution.</p>
      </DrillCard>
    );
    expect(screen.queryByTestId("secret-body")).not.toBeInTheDocument();
    expect(screen.getByTestId("card-lock-message")).toBeInTheDocument();
    expect(screen.getByTestId("card-lock-message").textContent).toMatch(
      /attempt drill first/i
    );
  });

  test("open card renders body slot", () => {
    render(
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state="open"
        staggerIndex={1}
      >
        <p data-testid="visible-body">Worked solution text.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("visible-body")).toBeInTheDocument();
    expect(screen.queryByTestId("card-lock-message")).not.toBeInTheDocument();
  });

  test("complete card shows checked checkbox and green header tint", () => {
    render(
      <DrillCard
        cardType="DRILL"
        title="③ DRILL · YOUR TURN"
        state="complete"
        staggerIndex={0}
      >
        <p>Body.</p>
      </DrillCard>
    );
    const checkbox = screen.getByTestId("card-checkbox");
    expect(checkbox.getAttribute("aria-checked")).toBe("true");
    const header = screen.getByTestId("card-header");
    expect(header.className).toContain("complete");
  });
});

describe("DrillCard stagger + reduced-motion", () => {
  test("data-stagger-index attribute matches prop", () => {
    render(
      <DrillCard
        cardType="CHECK"
        title="④ CHECK · TRANSFER"
        state="open"
        staggerIndex={3}
      >
        <p>Body.</p>
      </DrillCard>
    );
    expect(
      screen.getByTestId("drill-card").getAttribute("data-stagger-index")
    ).toBe("3");
  });

  test("under prefers-reduced-motion, no animation class applied", () => {
    mockReducedMotion(true);
    render(
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state="open"
        staggerIndex={1}
      >
        <p>Body.</p>
      </DrillCard>
    );
    const card = screen.getByTestId("drill-card");
    expect(card.className).not.toContain("animate-slide-in");
  });
});

test("DrillCard exposes data-state attribute matching state prop", () => {
  const states: Array<"locked" | "open" | "complete"> = ["locked", "open", "complete"];
  states.forEach(state => {
    const { getByTestId, unmount } = render(
      <DrillCard cardType="DRILL" title="③ DRILL" state={state} staggerIndex={0}>
        <p>body</p>
      </DrillCard>
    );
    expect(getByTestId("drill-card").getAttribute("data-state")).toBe(state);
    unmount();
  });
});
