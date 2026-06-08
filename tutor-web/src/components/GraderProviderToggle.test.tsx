/**
 * GraderProviderToggle — unit tests.
 *
 * Coverage:
 *  - Initial load shows current provider selected.
 *  - Selecting a different option enables the save button.
 *  - Saving calls PUT and updates current selection.
 *  - Fetch error surfaces in status message.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { GraderProviderToggle } from "./GraderProviderToggle";

function mockFetch(responses: Array<{ ok: boolean; body: unknown }>) {
  let call = 0;
  return vi.fn((_url: string | URL | Request, _init?: RequestInit) => {
    const resp = responses[call++] ?? responses[responses.length - 1];
    return Promise.resolve({
      ok: resp.ok,
      status: resp.ok ? 200 : 500,
      json: () => Promise.resolve(resp.body),
    } as Response);
  });
}

beforeEach(() => {
  // jarvisFetch reads document.cookie for CSRF; stub cookie
  Object.defineProperty(document, "cookie", {
    value: "csrf=test-token",
    configurable: true,
    writable: true,
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("GraderProviderToggle", () => {
  it("renders all three provider options", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(
      mockFetch([{ ok: true, body: { provider: "free" } }]),
    );

    render(<GraderProviderToggle />);

    await waitFor(() => {
      expect(
        screen.getByTestId("grader-provider-option-free"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("grader-provider-option-claude"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("grader-provider-option-freellmapi"),
      ).toBeInTheDocument();
    });
  });

  it("loads and selects current provider", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(
      mockFetch([{ ok: true, body: { provider: "claude" } }]),
    );

    render(<GraderProviderToggle />);

    await waitFor(() => {
      const claudeRadio = screen.getByTestId(
        "grader-provider-option-claude",
      ) as HTMLInputElement;
      expect(claudeRadio.checked).toBe(true);
    });
  });

  it("save button is disabled when selection matches current", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(
      mockFetch([{ ok: true, body: { provider: "free" } }]),
    );

    render(<GraderProviderToggle />);

    await waitFor(() => {
      expect(screen.getByTestId("grader-provider-save-btn")).toBeDisabled();
    });
  });

  it("save button is enabled when a different option is selected", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(
      mockFetch([{ ok: true, body: { provider: "free" } }]),
    );

    render(<GraderProviderToggle />);

    await waitFor(() =>
      expect(
        screen.getByTestId("grader-provider-option-claude"),
      ).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByTestId("grader-provider-option-claude"));

    expect(screen.getByTestId("grader-provider-save-btn")).not.toBeDisabled();
  });

  it("saving calls PUT and shows Salvat status", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(
      mockFetch([
        { ok: true, body: { provider: "free" } },
        { ok: true, body: { provider: "claude" } },
      ]),
    );

    render(<GraderProviderToggle />);

    await waitFor(() =>
      expect(
        screen.getByTestId("grader-provider-option-claude"),
      ).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByTestId("grader-provider-option-claude"));
    fireEvent.click(screen.getByTestId("grader-provider-save-btn"));

    await waitFor(() => {
      expect(screen.getByTestId("grader-provider-status")).toHaveTextContent(
        "Salvat.",
      );
    });

    // After save, current matches selected → save disabled
    await waitFor(() => {
      expect(screen.getByTestId("grader-provider-save-btn")).toBeDisabled();
    });
  });

  it("shows error status on fetch failure", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(
      mockFetch([{ ok: false, body: {} }]),
    );

    render(<GraderProviderToggle />);

    await waitFor(() => {
      expect(
        screen.getByTestId("grader-provider-toggle"),
      ).toBeInTheDocument();
    });

    // After GET failure, status message with "Eroare" should appear
    await waitFor(() => {
      expect(
        screen.getByTestId("grader-provider-toggle").textContent,
      ).toMatch(/Eroare/i);
    });
  });
});
