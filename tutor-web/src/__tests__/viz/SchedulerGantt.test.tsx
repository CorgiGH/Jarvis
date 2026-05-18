import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  SchedulerGantt,
  FRAME_COUNT,
} from "../../components/viz/SchedulerGantt";

describe("SchedulerGantt (SO-2)", () => {
  test("renders Shell scrubber + job batch label", () => {
    render(<SchedulerGantt />);
    expect(screen.getByTestId("sched-gantt-scrubber")).toBeInTheDocument();
    expect(screen.getByText("JOB BATCH")).toBeInTheDocument();
  });

  test("initial frame shows jobs table content", () => {
    render(<SchedulerGantt />);
    // Job IDs are present in the static jobs table on every frame
    expect(screen.getByText("J1")).toBeInTheDocument();
    expect(screen.getByText("J2")).toBeInTheDocument();
    expect(screen.getByText("J3")).toBeInTheDocument();
    expect(screen.getByText("J4")).toBeInTheDocument();
  });

  test("frame counter shows 1 / FRAME_COUNT", () => {
    render(<SchedulerGantt />);
    expect(
      screen.getByText(new RegExp(`^1\\s*\\/\\s*${FRAME_COUNT}$`))
    ).toBeInTheDocument();
  });

  test("FRAME_COUNT is 28", () => {
    // 1 intro + 5 FCFS + 5 SJF + 5 SRTF + 6 RR + 5 MLFQ + 1 summary = 28
    expect(FRAME_COUNT).toBe(28);
  });

  test("renders role=group from Shell", () => {
    render(<SchedulerGantt />);
    expect(screen.getByRole("group")).toBeInTheDocument();
  });
});
