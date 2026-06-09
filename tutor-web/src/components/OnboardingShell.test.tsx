import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { OnboardingShell } from "./OnboardingShell";

describe("OnboardingShell", () => {
  beforeEach(() => {
    // AiLiteracyGate makes a POST to confirm — mock fetch globally for these tests
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ok: true }) }),
    );
  });

  it("renders the onboarding-shell container", () => {
    render(<OnboardingShell onComplete={vi.fn()} />);
    expect(screen.getByTestId("onboarding-shell")).toBeInTheDocument();
  });

  it("renders step 1 on first paint", () => {
    render(<OnboardingShell onComplete={vi.fn()} />);
    expect(screen.getByTestId("onboarding-step-1")).toBeInTheDocument();
  });

  it("does not render step 2 before step 1 is complete", () => {
    render(<OnboardingShell onComplete={vi.fn()} />);
    expect(screen.queryByTestId("onboarding-step-2")).not.toBeInTheDocument();
  });

  it("renders lang-toggle", () => {
    render(<OnboardingShell onComplete={vi.fn()} />);
    expect(screen.getByTestId("lang-toggle")).toBeInTheDocument();
  });

  it("renders vox-btn-disabled", () => {
    render(<OnboardingShell onComplete={vi.fn()} />);
    expect(screen.getByTestId("vox-btn-disabled")).toBeInTheDocument();
  });

  it("advances to step 2 when step 1 is confirmed", async () => {
    render(<OnboardingShell onComplete={vi.fn()} />);
    // Step 1 is the AI literacy gate — click its confirm button
    fireEvent.click(screen.getByRole("button", { name: /am înțeles|confirm/i }));
    await waitFor(() =>
      expect(screen.getByTestId("onboarding-step-2")).toBeInTheDocument(),
    );
    expect(screen.queryByTestId("onboarding-step-1")).not.toBeInTheDocument();
  });

  it("step 2 heading has explicit text-panel-dark-fg color class (contrast on dark bg)", async () => {
    render(<OnboardingShell onComplete={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /am înțeles|confirm/i }));
    await waitFor(() =>
      expect(screen.getByTestId("onboarding-step-2")).toBeInTheDocument(),
    );
    const heading = screen.getByTestId("onboarding-step-2").querySelector("h2");
    expect(heading?.className).toMatch(/text-panel-dark-fg/);
  });

  it("advances through all 5 steps and calls onComplete after step 5", async () => {
    const onComplete = vi.fn();
    render(<OnboardingShell onComplete={onComplete} />);

    // Step 1 — AI literacy (async: fetch POST → onConfirmed)
    fireEvent.click(screen.getByRole("button", { name: /am înțeles|confirm/i }));
    await waitFor(() =>
      expect(screen.getByTestId("onboarding-step-2")).toBeInTheDocument(),
    );

    // Step 2 — ToS/privacy
    fireEvent.click(screen.getByRole("button", { name: /accept|continuă/i }));
    await waitFor(() =>
      expect(screen.getByTestId("onboarding-step-3")).toBeInTheDocument(),
    );

    // Step 3 — placement intent (click the "Yes" button)
    fireEvent.click(screen.getByRole("button", { name: /da, continuă/i }));
    await waitFor(() =>
      expect(screen.getByTestId("onboarding-step-4")).toBeInTheDocument(),
    );

    // Step 4 — profile + lang toggle
    fireEvent.click(screen.getByRole("button", { name: /continuă|gata/i }));
    await waitFor(() =>
      expect(screen.getByTestId("onboarding-step-5")).toBeInTheDocument(),
    );

    // Step 5 — notifications
    fireEvent.click(screen.getByRole("button", { name: /continuă|gata|finalizează/i }));
    await waitFor(() => expect(onComplete).toHaveBeenCalled());
  });
});
