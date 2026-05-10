import { render, fireEvent } from "@testing-library/react";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { NumLineDirect } from "../../components/viz/NumLineDirect";

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
});
