import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { FeedbackLadder } from "./FeedbackLadder";
import type { LadderRung } from "../lib/drillGrader";

const rungs: LadderRung[] = [
  { level: 0, text: "Uită-te din nou la enunț." },
  { level: 1, text: "Explică-ți cu voce tare ce face funcția." },
  { level: 2, text: "Greșeala tipică: confunzi cazul de bază." },
  { level: 3, text: "De fapt: cazul de bază oprește recursia la n<=1." },
  { level: 4, text: "Soluția completă: fib(5)=fib(4)+fib(3)=5." },
];

describe("FeedbackLadder (keystone)", () => {
  it("renders the container + 5-pip rail, reveals only L0 initially", () => {
    render(<FeedbackLadder rungs={rungs} />);
    expect(screen.getByTestId("drill-feedback-ladder")).toBeInTheDocument();
    expect(screen.getByTestId("feedback-rung-rail")).toBeInTheDocument();
    expect(screen.getByTestId("feedback-rung-0")).toBeInTheDocument();
    expect(screen.queryByTestId("feedback-rung-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("feedback-rung-live-pip")).toBeInTheDocument();
  });

  it("escalate reveals the next rung cumulatively", () => {
    render(<FeedbackLadder rungs={rungs} />);
    fireEvent.click(screen.getByTestId("feedback-rung-escalate-button"));
    expect(screen.getByTestId("feedback-rung-1")).toBeInTheDocument();
    expect(screen.getByTestId("feedback-rung-0")).toBeInTheDocument();
    expect(screen.queryByTestId("feedback-rung-2")).not.toBeInTheDocument();
  });

  it("at the last rung the escalate button shows the give-up label and disables", () => {
    render(<FeedbackLadder rungs={rungs} />);
    const btn = () => screen.getByTestId("feedback-rung-escalate-button");
    for (let i = 0; i < 4; i++) fireEvent.click(btn());
    expect(screen.getByTestId("feedback-rung-4")).toBeInTheDocument();
    expect(btn()).toBeDisabled();
  });

  it("renders the rung text verbatim (no re-derivation)", () => {
    render(<FeedbackLadder rungs={rungs} />);
    expect(screen.getByTestId("feedback-rung-0")).toHaveTextContent("Uită-te din nou la enunț.");
  });

  it("renders nothing when rungs is empty", () => {
    const { container } = render(<FeedbackLadder rungs={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it("calls onEscalate with the new level when escalating", () => {
    const onEscalate = vi.fn();
    render(<FeedbackLadder rungs={rungs} onEscalate={onEscalate} />);
    fireEvent.click(screen.getByTestId("feedback-rung-escalate-button"));
    expect(onEscalate).toHaveBeenCalledWith(1);
  });
});
