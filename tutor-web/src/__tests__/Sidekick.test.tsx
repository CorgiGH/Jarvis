import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, vi, describe, beforeEach } from "vitest";
import { Sidekick } from "../components/Sidekick";
import type { SidekickEnvelope } from "../lib/inlineAsk";

vi.mock("../lib/sidekickContext", () => ({
  askSidekick: vi.fn(),
}));

import { askSidekick } from "../lib/sidekickContext";
const mockAskSidekick = vi.mocked(askSidekick);

const baseEnv: SidekickEnvelope = {
  task_id: "task-01",
  problem_id: "A3",
  card_id: "card-1",
  card_title: "① DEFINITION",
  selection: "MLE",
  user_question: "what does MLE mean?",
};

describe("Sidekick", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  test("renders collapsed by default when no envelope is provided", () => {
    render(<Sidekick />);
    const panel = screen.getByTestId("sidekick-panel");
    expect(panel).toBeInTheDocument();
    expect(panel).toHaveAttribute("data-expanded", "false");
  });

  test("expands and calls askSidekick when envelope prop is set", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "MLE stands for Maximum Likelihood Estimation.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: "MLE",
    });

    render(<Sidekick envelope={baseEnv} />);

    await waitFor(() => {
      expect(mockAskSidekick).toHaveBeenCalledOnce();
      expect(mockAskSidekick).toHaveBeenCalledWith(baseEnv);
    });

    expect(await screen.findByText(/MLE stands for Maximum Likelihood Estimation/)).toBeInTheDocument();
    const panel = screen.getByTestId("sidekick-panel");
    expect(panel).toHaveAttribute("data-expanded", "true");
  });

  test("renders > quoted: strip above the AI reply (animation #16)", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "The median minimises the sum of absolute deviations.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: "MLE",
    });

    render(<Sidekick envelope={baseEnv} />);

    const quote = await screen.findByTestId("sidekick-quote");
    expect(quote.textContent).toMatch(/quoted:.*MLE/i);
    expect(quote.classList.contains("sidekick-quote-pop-in")).toBe(true);
  });

  test("renders LLM unavailable message on askSidekick rejection", async () => {
    mockAskSidekick.mockRejectedValue(new Error("503 upstream"));

    render(<Sidekick envelope={baseEnv} />);

    expect(await screen.findByText(/LLM unavailable/i)).toBeInTheDocument();
  });

  test("toggle button collapses an expanded panel", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "Answer text.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: null,
    });

    render(<Sidekick envelope={baseEnv} />);
    await screen.findByText("Answer text.");

    const toggle = screen.getByRole("button", { name: /collapse sidekick/i });
    await userEvent.click(toggle);

    const panel = screen.getByTestId("sidekick-panel");
    expect(panel).toHaveAttribute("data-expanded", "false");
  });
});
