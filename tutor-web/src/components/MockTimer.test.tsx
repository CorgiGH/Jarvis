import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import { MockTimer } from "./MockTimer";

describe("MockTimer", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders the timer container", () => {
    render(<MockTimer totalSeconds={300} onExpire={() => {}} />);
    expect(screen.getByTestId("mock-timer")).toBeInTheDocument();
  });

  it("displays initial time correctly (5:00)", () => {
    render(<MockTimer totalSeconds={300} onExpire={() => {}} />);
    expect(screen.getByTestId("mock-timer")).toHaveTextContent("5:00");
  });

  it("counts down by 1 second each tick", () => {
    render(<MockTimer totalSeconds={300} onExpire={() => {}} />);
    act(() => { vi.advanceTimersByTime(1000); });
    expect(screen.getByTestId("mock-timer")).toHaveTextContent("4:59");
  });

  it("calls onExpire when time reaches 0", () => {
    const onExpire = vi.fn();
    render(<MockTimer totalSeconds={3} onExpire={onExpire} />);
    act(() => { vi.advanceTimersByTime(3000); });
    expect(onExpire).toHaveBeenCalledOnce();
  });

  it("does not go below 0:00", () => {
    const onExpire = vi.fn();
    render(<MockTimer totalSeconds={1} onExpire={onExpire} />);
    act(() => { vi.advanceTimersByTime(5000); });
    expect(screen.getByTestId("mock-timer")).toHaveTextContent("0:00");
    expect(onExpire).toHaveBeenCalledOnce();
  });

  it("shows warning styling when under 60 seconds", () => {
    render(<MockTimer totalSeconds={59} onExpire={() => {}} />);
    expect(screen.getByTestId("mock-timer")).toHaveAttribute("data-timer-warning", "true");
  });

  it("does not show warning when over 60 seconds", () => {
    render(<MockTimer totalSeconds={120} onExpire={() => {}} />);
    expect(screen.getByTestId("mock-timer")).toHaveAttribute("data-timer-warning", "false");
  });
});
