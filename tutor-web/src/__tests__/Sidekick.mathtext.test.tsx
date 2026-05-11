import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Sidekick } from "../components/Sidekick";

vi.mock("../lib/sidekickContext", async () => ({
  askSidekick: vi.fn(async () => ({
    text: "Laplace MLE: $$\\hat{\\mu} = \\text{median}(x)$$ — the L1 estimator.",
    model: "m", quotedContext: null, citations: [],
  })),
}));

describe("Sidekick math rendering", () => {
  it("renders reply text via MathText (data-testid=math-text present)", async () => {
    render(<Sidekick envelope={{ task_id: "t", user_question: "q" } as any} />);
    await waitFor(() => expect(screen.getByTestId("math-text")).toBeTruthy());
  });
});
