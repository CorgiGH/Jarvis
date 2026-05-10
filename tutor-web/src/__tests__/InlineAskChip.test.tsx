import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, vi, describe, beforeEach } from "vitest";
import { InlineAskChip } from "../components/InlineAskChip";
import type { SidekickEnvelope } from "../lib/inlineAsk";

const baseRect: DOMRect = {
  top: 200,
  left: 120,
  bottom: 216,
  right: 200,
  width: 80,
  height: 16,
  x: 120,
  y: 200,
  toJSON: () => ({}),
};

const baseEnv: SidekickEnvelope = {
  task_id: "task-01",
  problem_id: "A3",
  card_id: "card-1",
  card_title: "③ DRILL · YOUR TURN",
  selection: "MLE",
  user_question: "what does MLE mean?",
};

describe("InlineAskChip", () => {
  test("renders ✨ ASK label", () => {
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    expect(screen.getByRole("button", { name: /✨ ASK/i })).toBeInTheDocument();
  });

  test("chip is positioned 8px above the selection rect top", () => {
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    const chip = screen.getByRole("button", { name: /✨ ASK/i });
    // Position is applied via inline style; top = rect.top - 8 + scrollY
    // In jsdom scrollY = 0, so top = 192
    expect(chip.style.top).toBe("192px");
  });

  test("chip left is anchored to selectionRect.left", () => {
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    const chip = screen.getByRole("button", { name: /✨ ASK/i });
    expect(chip.style.left).toBe("120px");
  });

  test("calls onAsk with the envelope when clicked", async () => {
    const onAsk = vi.fn();
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={onAsk} />
    );
    await userEvent.click(screen.getByRole("button", { name: /✨ ASK/i }));
    expect(onAsk).toHaveBeenCalledOnce();
    expect(onAsk).toHaveBeenCalledWith(baseEnv);
  });

  test("chip carries the ask-chip-fade-in CSS class (animation #15)", () => {
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    const chip = screen.getByRole("button", { name: /✨ ASK/i });
    expect(chip.classList.contains("ask-chip-fade-in")).toBe(true);
  });

  test("reduced-motion: chip still renders without animation class suppressed (CSS media query handles it)", () => {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: (query: string) => ({
        matches: query === "(prefers-reduced-motion: reduce)",
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }),
    });
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    expect(screen.getByRole("button", { name: /✨ ASK/i })).toBeInTheDocument();
  });
});
