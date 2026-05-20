import { render, fireEvent } from "@testing-library/react";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { NumLineDirect } from "../../components/viz/NumLineDirect";
import { act } from "react";

beforeEach(() => {
  vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => { cb(0); return 0; });
  vi.stubGlobal("cancelAnimationFrame", () => {});
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }),
  });
});
afterEach(() => { vi.unstubAllGlobals(); });

describe("NumLineDirect", () => {
  const data = [3, 7, 8, 9, 14];

  test("renders an SVG element", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    expect(container.querySelector("svg")).not.toBeNull();
  });

  test("renders a tick mark for each sample point", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    const ticks = container.querySelectorAll("[data-testid='sample-tick']");
    expect(ticks.length).toBe(data.length);
  });

  test("renders the μ marker circle", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    expect(container.querySelector("[data-testid='mu-marker']")).not.toBeNull();
  });

  test("μ marker cx reflects mu prop mapped to SVG space", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} min={0} max={20} />);
    const marker = container.querySelector("[data-testid='mu-marker']")!;
    const cx = parseFloat(marker.getAttribute("cx") ?? "0");
    expect(cx).toBeGreaterThan(24);
    expect(cx).toBeLessThan(456);
  });

  test("pointer drag calls onMu with new value", () => {
    const onMu = vi.fn();
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={onMu} min={0} max={20} />);
    const marker = container.querySelector("[data-testid='mu-marker']")!;
    (marker as any).setPointerCapture = vi.fn();
    (marker as any).releasePointerCapture = vi.fn();

    fireEvent.pointerDown(marker, { pointerId: 1, clientX: 196 });
    fireEvent.pointerMove(marker, { pointerId: 1, clientX: 216, target: marker });
    fireEvent.pointerUp(marker, { pointerId: 1 });

    expect(onMu).toHaveBeenCalled();
    const called = onMu.mock.calls[onMu.mock.calls.length - 1][0];
    expect(called).toBeGreaterThan(8);
  });

  test("does not call onMu if pointer moves less than 1px threshold", () => {
    const onMu = vi.fn();
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={onMu} min={0} max={20} />);
    const marker = container.querySelector("[data-testid='mu-marker']")!;
    (marker as any).setPointerCapture = vi.fn();
    (marker as any).releasePointerCapture = vi.fn();

    fireEvent.pointerDown(marker, { pointerId: 1, clientX: 196 });
    fireEvent.pointerMove(marker, { pointerId: 1, clientX: 196.3 });
    fireEvent.pointerUp(marker, { pointerId: 1 });

    expect(onMu).not.toHaveBeenCalled();
  });

  // ── V19: ARIA — role/title/desc ────────────────────────────────────────────
  test("svg has role='img' (V19)", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    const svg = container.querySelector("svg");
    expect(svg?.getAttribute("role")).toBe("img");
  });

  test("svg has <title> and <desc> elements (V19)", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    const svg = container.querySelector("svg");
    expect(svg?.querySelector("title")).not.toBeNull();
    expect(svg?.querySelector("desc")).not.toBeNull();
  });

  test("svg aria-labelledby references title and desc ids (V19)", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    const svg = container.querySelector("svg");
    const labelledBy = svg?.getAttribute("aria-labelledby") ?? "";
    const titleId = svg?.querySelector("title")?.id ?? "";
    const descId = svg?.querySelector("desc")?.id ?? "";
    expect(titleId.length).toBeGreaterThan(0);
    expect(descId.length).toBeGreaterThan(0);
    expect(labelledBy).toContain(titleId);
    expect(labelledBy).toContain(descId);
  });

  // ── V5: ARIA — aria-live narrates μ changes ────────────────────────────────
  test("aria-live='polite' region exists (V5)", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    const live = container.querySelector("[aria-live='polite'][role='status']");
    expect(live).not.toBeNull();
  });

  test("aria-live region contains current μ value on initial render (V5)", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    const live = container.querySelector("[aria-live='polite'][role='status']");
    expect(live?.textContent).toMatch(/8/);
  });

  test("aria-live region updates when μ prop changes (V5)", () => {
    const { container, rerender } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    act(() => {
      rerender(<NumLineDirect data={data} mu={11.5} onMu={() => {}} />);
    });
    const live = container.querySelector("[aria-live='polite'][role='status']");
    expect(live?.textContent).toMatch(/11\.5/);
  });
});
