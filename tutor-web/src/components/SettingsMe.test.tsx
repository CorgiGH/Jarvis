import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { SettingsMe } from "./SettingsMe";

const mockMeData = {
  user: { id: "u1", name: "alex", email: "alex@x.io", scope: "FRIEND", lang: "ro" },
  consentEvents: [],
  preferences: { hintMode: "static", loggingPausedUntil: null },
  aiLiteracyConfirmed: true,
  exportedAt: "2026-05-20T00:00:00Z",
};

describe("SettingsMe", () => {
  it("loads and shows the account email + AI-literacy status", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockMeData,
    }));
    render(<SettingsMe />);
    await waitFor(() => expect(screen.getByText(/alex@x.io/)).toBeInTheDocument());
    expect(screen.getByText(/literacy/i)).toBeInTheDocument();
  });

  it("renders RightsSidebar as a sibling panel (2-col layout)", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockMeData,
    }));
    render(<SettingsMe />);
    await waitFor(() => expect(screen.getByTestId("settings-me")).toBeInTheDocument());
    // RightsSidebar must be present (rendered in right column)
    expect(screen.getByTestId("rights-sidebar")).toBeInTheDocument();
    // The sidebar must NOT be a descendant of the account/settings sections
    // (it's in its own aside, not inside any section)
    const sidebar = screen.getByTestId("rights-sidebar");
    expect(sidebar.closest("aside")).not.toBeNull();
  });
});
