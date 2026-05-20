import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { LoginPage } from "./LoginPage";

describe("LoginPage", () => {
  it("posts the email to /auth/request-link and shows the check-email state", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ok: true }) });
    vi.stubGlobal("fetch", fetchMock);
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "a@b.io" } });
    fireEvent.click(screen.getByRole("button", { name: /send|trimite/i }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/auth/request-link"), expect.objectContaining({ method: "POST" }),
    ));
    expect(screen.getByText(/check your email|verifică/i)).toBeInTheDocument();
  });
});
