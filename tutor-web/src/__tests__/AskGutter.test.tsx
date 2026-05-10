import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, vi, describe } from "vitest";
import { AskGutter } from "../components/AskGutter";
import type { SidekickEnvelope } from "../lib/inlineAsk";

const baseEnv: Omit<SidekickEnvelope, "user_question"> = {
  task_id: "task-01",
  problem_id: "A3",
  card_id: "card-1",
  card_title: "① DEFINITION",
  anchor_id: "def-para-0",
};

describe("AskGutter", () => {
  test("renders children (paragraph content)", () => {
    render(
      <AskGutter paragraphText="Laplace distribution has heavy tails." context={baseEnv} onAsk={vi.fn()}>
        <p>Laplace distribution has heavy tails.</p>
      </AskGutter>
    );
    expect(screen.getByText("Laplace distribution has heavy tails.")).toBeInTheDocument();
  });

  test("? button is present in the DOM (visible on hover via CSS)", () => {
    render(
      <AskGutter paragraphText="Laplace distribution has heavy tails." context={baseEnv} onAsk={vi.fn()}>
        <p>Laplace distribution has heavy tails.</p>
      </AskGutter>
    );
    expect(screen.getByRole("button", { name: /ask sidekick about this paragraph/i })).toBeInTheDocument();
  });

  test("clicking ? calls onAsk with the paragraph as anchorText + empty userQuestion", async () => {
    const onAsk = vi.fn();
    render(
      <AskGutter paragraphText="Heavy tails make the median optimal." context={baseEnv} onAsk={onAsk}>
        <p>Heavy tails make the median optimal.</p>
      </AskGutter>
    );
    await userEvent.click(screen.getByRole("button", { name: /ask sidekick about this paragraph/i }));
    expect(onAsk).toHaveBeenCalledOnce();
    const env: SidekickEnvelope = onAsk.mock.calls[0][0];
    expect(env.anchor_text).toBe("Heavy tails make the median optimal.");
    expect(env.task_id).toBe("task-01");
    expect(env.user_question).toBe("");
  });

  test("? button responds to keyboard activation (focus-visible path)", async () => {
    const onAsk = vi.fn();
    render(
      <AskGutter paragraphText="Laplace MLE." context={baseEnv} onAsk={onAsk}>
        <p>Laplace MLE.</p>
      </AskGutter>
    );
    const btn = screen.getByRole("button", { name: /ask sidekick about this paragraph/i });
    btn.focus();
    await userEvent.keyboard("{Enter}");
    expect(onAsk).toHaveBeenCalledOnce();
  });
});
