import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { LessonErrorBoundary } from "./LessonErrorBoundary";

/** A child that always throws on render. */
function AlwaysThrows(): never {
  throw new Error("deliberate render error");
}

/** A stable child that renders alongside the boundary. */
function Sibling({ label }: { label: string }) {
  return <div data-testid={`sibling-${label}`}>{label}</div>;
}

describe("LessonErrorBoundary", () => {
  it("catches a child render error and shows the RO fallback (lesson-beat-error)", () => {
    render(
      <div>
        <LessonErrorBoundary>
          <AlwaysThrows />
        </LessonErrorBoundary>
      </div>,
    );
    expect(screen.getByTestId("lesson-beat-error")).toBeInTheDocument();
  });

  it("fallback contains the beatError RO string", () => {
    render(
      <LessonErrorBoundary>
        <AlwaysThrows />
      </LessonErrorBoundary>,
    );
    const el = screen.getByTestId("lesson-beat-error");
    expect(el.textContent).toContain("încearcă");
  });

  it("sibling chrome OUTSIDE the boundary stays mounted when the child throws", () => {
    render(
      <div>
        <Sibling label="pips" />
        <LessonErrorBoundary>
          <AlwaysThrows />
        </LessonErrorBoundary>
        <Sibling label="gate" />
      </div>,
    );
    expect(screen.getByTestId("sibling-pips")).toBeInTheDocument();
    expect(screen.getByTestId("sibling-gate")).toBeInTheDocument();
    expect(screen.getByTestId("lesson-beat-error")).toBeInTheDocument();
  });

  it("does not escalate an unhandled error to the test runner (boundary catches it)", () => {
    // If the boundary failed, render() would throw; reaching the assertion proves it didn't.
    render(
      <LessonErrorBoundary>
        <AlwaysThrows />
      </LessonErrorBoundary>,
    );
    expect(screen.getByTestId("lesson-beat-error")).toBeInTheDocument();
  });

  it("resets the error state when the key changes (advancing a beat)", () => {
    const { rerender } = render(
      <LessonErrorBoundary key="beat-0">
        <AlwaysThrows />
      </LessonErrorBoundary>,
    );
    expect(screen.getByTestId("lesson-beat-error")).toBeInTheDocument();

    // Rerender with a new key simulates advancing to a new beat (which must reset the error).
    rerender(
      <LessonErrorBoundary key="beat-1">
        <div data-testid="healthy-child">OK</div>
      </LessonErrorBoundary>,
    );
    expect(screen.queryByTestId("lesson-beat-error")).not.toBeInTheDocument();
    expect(screen.getByTestId("healthy-child")).toBeInTheDocument();
  });
});
