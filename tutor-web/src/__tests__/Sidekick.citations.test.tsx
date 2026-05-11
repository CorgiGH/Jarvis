import { render, screen } from "@testing-library/react";
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

describe("Sidekick citations strip", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  test("renders sidekick-citations-strip with one CitationPill when citations present", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "MLE stands for Maximum Likelihood Estimation.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: null,
      citations: [
        { path: "lectures/week03/slides.pdf", snippet: "MLE definition here.", score: 0.91 },
      ],
    });

    render(<Sidekick envelope={baseEnv} />);

    const strip = await screen.findByTestId("sidekick-citations-strip");
    expect(strip).toBeInTheDocument();

    const pills = screen.getAllByTestId("citation-pill");
    expect(pills).toHaveLength(1);
    expect(pills[0].textContent).toMatch(/slides\.pdf/);
  });

  test("does NOT render sidekick-citations-strip when citations is empty", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "No citations here.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: null,
      citations: [],
    });

    render(<Sidekick envelope={baseEnv} />);

    await screen.findByTestId("sidekick-reply");
    expect(screen.queryByTestId("sidekick-citations-strip")).not.toBeInTheDocument();
  });

  test("sidekick-reply testid wraps the reply text", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "Reply text here.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: null,
      citations: [],
    });

    render(<Sidekick envelope={baseEnv} />);

    const replyEl = await screen.findByTestId("sidekick-reply");
    expect(replyEl.textContent).toMatch(/Reply text here/);
  });
});
