import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { AiLiteracyGate } from "./AiLiteracyGate";

describe("AiLiteracyGate", () => {
  it("posts confirmation and calls onConfirmed", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ok: true }) });
    vi.stubGlobal("fetch", fetchMock);
    const onConfirmed = vi.fn();
    render(<AiLiteracyGate lang="ro" onConfirmed={onConfirmed} />);
    fireEvent.click(screen.getByRole("button", { name: /confirm|am înțeles/i }));
    await waitFor(() => expect(onConfirmed).toHaveBeenCalled());
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/me/ai-literacy/confirm"),
      expect.objectContaining({ method: "POST" }),
    );
  });
});
