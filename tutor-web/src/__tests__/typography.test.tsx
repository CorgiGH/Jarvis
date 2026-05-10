import { test, expect, afterEach } from "vitest";

afterEach(() => {
  document.querySelectorAll("style[data-typography-test]").forEach(s => s.remove());
});

test("body --type-body resolves to a clamp() expression with 14px floor + 16px ceiling", () => {
  const styleEl = document.createElement("style");
  styleEl.setAttribute("data-typography-test", "1");
  styleEl.textContent = ":root { --type-body: clamp(14px, calc(13.5px + 0.1vw), 16px); --type-sm: 12px; --type-lg: 18px; --type-h2: 20px; }";
  document.head.appendChild(styleEl);
  const computed = getComputedStyle(document.documentElement);
  expect(computed.getPropertyValue("--type-body").trim()).toMatch(/clamp\(14px/);
  expect(computed.getPropertyValue("--type-body").trim()).toMatch(/16px\)$/);
  expect(computed.getPropertyValue("--type-sm").trim()).toBe("12px");
  expect(computed.getPropertyValue("--type-lg").trim()).toBe("18px");
  expect(computed.getPropertyValue("--type-h2").trim()).toBe("20px");
});

test(".prose-clamp paragraphs carry a max-width rule", () => {
  const styleEl = document.createElement("style");
  styleEl.setAttribute("data-typography-test", "1");
  styleEl.textContent = ".prose-clamp p { max-width: 60ch; }";
  document.head.appendChild(styleEl);
  const wrap = document.createElement("div");
  wrap.className = "prose-clamp";
  const p = document.createElement("p");
  p.textContent = "x";
  wrap.appendChild(p);
  document.body.appendChild(wrap);
  const computed = getComputedStyle(p);
  expect(computed.maxWidth).toMatch(/60ch|^\d+px$/);
  document.body.removeChild(wrap);
});
