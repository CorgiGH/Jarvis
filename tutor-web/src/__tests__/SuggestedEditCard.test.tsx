import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { SuggestedEditCard } from "../components/SuggestedEditCard";
import type { SuggestedEdit } from "../lib/suggestedEdit";

beforeEach(() => {
  Object.defineProperty(navigator, "clipboard", {
    value: { writeText: vi.fn(async () => undefined) },
    configurable: true,
    writable: true,
  });
});
afterEach(() => { vi.restoreAllMocks(); });

const baseEdit: SuggestedEdit = {
  id: "e1", type: "clipboard", payload: "x = 1", status: "pending",
  label: "insert at top",
};

test("renders type, label, payload preview, COPY + REJECT buttons", () => {
  render(<SuggestedEditCard edit={baseEdit} />);
  expect(screen.getByText(/SUGGESTED · CLIPBOARD/)).toBeInTheDocument();
  expect(screen.getByText(/insert at top/)).toBeInTheDocument();
  expect(screen.getByText("x = 1")).toBeInTheDocument();
  expect(screen.getByTestId("suggested-edit-apply")).toHaveTextContent("COPY");
  expect(screen.getByTestId("suggested-edit-reject")).toHaveTextContent("REJECT");
});

test("APPLY writes payload to clipboard and flips status to applied", async () => {
  const onChange = vi.fn();
  render(<SuggestedEditCard edit={baseEdit} onStatusChange={onChange} />);
  fireEvent.click(screen.getByTestId("suggested-edit-apply"));
  await waitFor(() => {
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("x = 1");
    expect(screen.getByTestId("suggested-edit-status")).toHaveTextContent("applied");
    expect(onChange).toHaveBeenCalledWith("e1", "applied");
  });
});

test("REJECT marks status rejected without touching clipboard", () => {
  const onChange = vi.fn();
  render(<SuggestedEditCard edit={baseEdit} onStatusChange={onChange} />);
  fireEvent.click(screen.getByTestId("suggested-edit-reject"));
  expect(navigator.clipboard.writeText).not.toHaveBeenCalled();
  expect(screen.getByTestId("suggested-edit-status")).toHaveTextContent("rejected");
  expect(onChange).toHaveBeenCalledWith("e1", "rejected");
});

test("apply_edit type without daemon shows failed status + error", async () => {
  const onChange = vi.fn();
  const e: SuggestedEdit = { ...baseEdit, type: "apply_edit" };
  render(<SuggestedEditCard edit={e} onStatusChange={onChange} />);
  fireEvent.click(screen.getByTestId("suggested-edit-apply"));
  await waitFor(() => {
    expect(screen.getByTestId("suggested-edit-status")).toHaveTextContent("failed");
    expect(screen.getByTestId("suggested-edit-error")).toBeInTheDocument();
    expect(onChange).toHaveBeenCalledWith("e1", "failed");
  });
});

test("hides action buttons after APPLY + REJECT", async () => {
  render(<SuggestedEditCard edit={baseEdit} />);
  fireEvent.click(screen.getByTestId("suggested-edit-reject"));
  expect(screen.queryByTestId("suggested-edit-apply")).toBeNull();
  expect(screen.queryByTestId("suggested-edit-reject")).toBeNull();
});

test("clipboard write failure flips status to failed + surfaces error", async () => {
  Object.defineProperty(navigator, "clipboard", {
    value: { writeText: vi.fn(async () => { throw new Error("denied"); }) },
    configurable: true,
    writable: true,
  });
  render(<SuggestedEditCard edit={baseEdit} />);
  fireEvent.click(screen.getByTestId("suggested-edit-apply"));
  await waitFor(() => {
    expect(screen.getByTestId("suggested-edit-status")).toHaveTextContent("failed");
    expect(screen.getByTestId("suggested-edit-error").textContent).toMatch(/denied/);
  });
});

test("readOnly disables APPLY with reason in error + tooltip; REJECT still works", async () => {
  const onChange = vi.fn();
  render(<SuggestedEditCard
    edit={baseEdit}
    onStatusChange={onChange}
    readOnly
    readOnlyReason="browser tab: stackoverflow.com"
  />);
  const apply = screen.getByTestId("suggested-edit-apply");
  expect(apply).toBeDisabled();
  expect(apply.getAttribute("title")).toMatch(/stackoverflow/);
  // REJECT remains operable.
  fireEvent.click(screen.getByTestId("suggested-edit-reject"));
  expect(onChange).toHaveBeenCalledWith("e1", "rejected");
});

test("truncates long payloads in preview", () => {
  const long = "x".repeat(500);
  render(<SuggestedEditCard edit={{ ...baseEdit, payload: long }} />);
  // Preview cuts to 240 + ellipsis; original payload still on the
  // edit object for clipboard write.
  const preview = screen.getByText(/x{200,}/);
  expect(preview.textContent!.length).toBeLessThanOrEqual(241);
  expect(preview.textContent!.endsWith("…")).toBe(true);
});
