import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { SettingsMe } from "./SettingsMe";

describe("SettingsMe", () => {
  it("loads and shows the account email + AI-literacy status", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        user: { id: "u1", name: "alex", email: "alex@x.io", scope: "FRIEND", lang: "ro" },
        consentEvents: [],
        preferences: { hintMode: "static", loggingPausedUntil: null },
        aiLiteracyConfirmed: true,
        exportedAt: "2026-05-20T00:00:00Z",
      }),
    }));
    render(<SettingsMe />);
    await waitFor(() => expect(screen.getByText(/alex@x.io/)).toBeInTheDocument());
    expect(screen.getByText(/literacy/i)).toBeInTheDocument();
  });
});
