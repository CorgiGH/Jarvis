import { expect, type Page } from "@playwright/test";

/**
 * Plan-4b Task 8 / §0.9D / INV-5.3 (gate 4b) — legibility + contrast gate.
 *
 * Same throw-with-violation-list shape as assertNoClip.ts.
 *
 * Checks every visible text node inside `[data-testid="${scopeTestId}"]`:
 *
 *  DOM text:  computed `color` vs EFFECTIVE background (walk ancestors to first
 *             fully-opaque background, compositing translucent layers).
 *             WCAG 2.1 contrast ratio ≥ 4.5:1.
 *
 *  SVG text:  computed `fill` vs nearest opaque ancestor background (the figure PAPER
 *             or the svg background).
 *
 *  Truncation (VIZ-05 class): any scoped element with text-overflow:ellipsis and
 *             scrollWidth > clientWidth, OR an SVG <text> whose rendered box exceeds its
 *             clip → RED.
 *
 * EXCLUDE: aria-hidden="true" decorative elements; zero-area / visibility:hidden nodes.
 *
 * Determinism: run at settled state (fonts ready via 4a self-hosted fonts) + reduced motion
 * (the caller is expected to have emulated reducedMotion before navigating).
 *
 * §0.9D contrast method — defined at plan-level (R-4b-Q4), implemented verbatim here.
 */
export async function assertLegibility(page: Page, scopeTestId: string): Promise<void> {
  const violations = await page.evaluate((tid) => {
    // WCAG 2.1 relative luminance of an sRGB component
    function srgbToLinear(c: number): number {
      const n = c / 255;
      return n <= 0.04045 ? n / 12.92 : Math.pow((n + 0.055) / 1.055, 2.4);
    }
    function relativeLuminance(r: number, g: number, b: number): number {
      return 0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b);
    }
    function contrastRatio(l1: number, l2: number): number {
      const [lo, hi] = l1 < l2 ? [l1, l2] : [l2, l1];
      return (hi + 0.05) / (lo + 0.05);
    }
    // Parse "rgb(r,g,b)" or "rgba(r,g,b,a)" into [r,g,b,a]
    function parseColor(css: string): [number, number, number, number] | null {
      const m = css.match(
        /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)/,
      );
      if (!m) return null;
      return [+m[1], +m[2], +m[3], m[4] != null ? +m[4] : 1];
    }
    // Composite fg over bg given fg alpha (simple Porter-Duff over with a=1 bg)
    function composite(fgR: number, fgG: number, fgB: number, fgA: number,
                        bgR: number, bgG: number, bgB: number): [number, number, number] {
      return [
        Math.round(fgR * fgA + bgR * (1 - fgA)),
        Math.round(fgG * fgA + bgG * (1 - fgA)),
        Math.round(fgB * fgA + bgB * (1 - fgA)),
      ];
    }
    // Walk ancestors to find the effective background colour (first fully-opaque bg).
    // Returns [r, g, b] of the composited background seen by `el`.
    function effectiveBg(el: Element): [number, number, number] {
      // Default: white
      let bg: [number, number, number] = [255, 255, 255];
      const chain: Element[] = [];
      let cur: Element | null = el;
      while (cur && cur !== document.body.parentElement) {
        chain.push(cur);
        cur = cur.parentElement;
      }
      // Walk from outermost to innermost, compositing opaque layers
      for (let i = chain.length - 1; i >= 0; i--) {
        const cs = getComputedStyle(chain[i]);
        const bgColor = parseColor(cs.backgroundColor);
        if (!bgColor) continue;
        const [r, g, b, a] = bgColor;
        if (a === 0) continue;
        if (a >= 1) {
          bg = [r, g, b];
        } else {
          bg = composite(r, g, b, a, bg[0], bg[1], bg[2]);
        }
      }
      return bg;
    }
    function isAriaHidden(el: Element): boolean {
      let cur: Element | null = el;
      while (cur) {
        if (cur.getAttribute("aria-hidden") === "true") return true;
        cur = cur.parentElement;
      }
      return false;
    }
    function isVisible(el: Element): boolean {
      const r = el.getBoundingClientRect();
      if (r.width < 1 || r.height < 1) return false;
      const cs = getComputedStyle(el);
      return cs.display !== "none" && cs.visibility !== "hidden" && parseFloat(cs.opacity || "1") > 0.05;
    }
    const scope = document.querySelector(`[data-testid="${tid}"]`);
    if (!scope) return { missing: true, contrast: [], truncation: [] };

    const contrastViolations: unknown[] = [];
    const truncationViolations: unknown[] = [];
    const all = Array.from(scope.querySelectorAll("*"));
    const isSvgEl = (el: Element) => el.namespaceURI === "http://www.w3.org/2000/svg";

    for (const el of all) {
      if (isAriaHidden(el)) continue;
      if (!isVisible(el)) continue;

      // --- DOM text contrast ---
      if (!isSvgEl(el)) {
        // only leaf text nodes (has direct text content)
        const hasDirectText = Array.from(el.childNodes).some(
          (n) => n.nodeType === 3 && (n.textContent || "").trim().length > 0,
        );
        if (hasDirectText) {
          const cs = getComputedStyle(el);
          const fgParsed = parseColor(cs.color);
          if (fgParsed) {
            const [fgR, fgG, fgB, fgA] = fgParsed;
            const bg = effectiveBg(el);
            const [eR, eG, eB] = fgA < 1
              ? composite(fgR, fgG, fgB, fgA, bg[0], bg[1], bg[2])
              : [fgR, fgG, fgB];
            const fgL = relativeLuminance(eR, eG, eB);
            const bgL = relativeLuminance(bg[0], bg[1], bg[2]);
            const ratio = contrastRatio(fgL, bgL);
            if (ratio < 4.5) {
              contrastViolations.push({
                element: `${el.tagName}.${(el.getAttribute("class") || "").split(" ").join(".")}`,
                text: (el.textContent || "").trim().slice(0, 60),
                ratio: Math.round(ratio * 100) / 100,
                fg: cs.color,
                bg: `rgb(${bg[0]},${bg[1]},${bg[2]})`,
              });
              if (contrastViolations.length > 8) break;
            }
          }
        }
        // Truncation check: text-overflow:ellipsis with scrollWidth > clientWidth
        const cs2 = getComputedStyle(el);
        if (cs2.textOverflow === "ellipsis" && el.scrollWidth > el.clientWidth + 1) {
          truncationViolations.push({
            type: "ellipsis",
            tag: el.tagName,
            text: (el.textContent || "").trim().slice(0, 80),
            scrollWidth: el.scrollWidth,
            clientWidth: el.clientWidth,
          });
        }
      }

      // --- SVG text contrast ---
      if (isSvgEl(el) && el.tagName.toLowerCase() === "text") {
        const cs = getComputedStyle(el);
        // SVG fill color
        const fillStr = cs.fill || el.getAttribute("fill") || "black";
        const fgParsed = parseColor(fillStr);
        if (fgParsed) {
          const [fgR, fgG, fgB, fgA] = fgParsed;
          const bg = effectiveBg(el);
          const [eR, eG, eB] = fgA < 1
            ? composite(fgR, fgG, fgB, fgA, bg[0], bg[1], bg[2])
            : [fgR, fgG, fgB];
          const fgL = relativeLuminance(eR, eG, eB);
          const bgL = relativeLuminance(bg[0], bg[1], bg[2]);
          const ratio = contrastRatio(fgL, bgL);
          if (ratio < 4.5) {
            contrastViolations.push({
              element: `SVG:text`,
              text: (el.textContent || "").trim().slice(0, 60),
              ratio: Math.round(ratio * 100) / 100,
              fg: fillStr,
              bg: `rgb(${bg[0]},${bg[1]},${bg[2]})`,
            });
          }
        }
        // SVG text truncation: rendered box > clip
        const bbox = (el as SVGTextElement).getBBox?.();
        const parentClip = el.parentElement?.getBoundingClientRect();
        if (bbox && parentClip) {
          const textRect = el.getBoundingClientRect();
          if (textRect.width > parentClip.width + 1) {
            truncationViolations.push({
              type: "svg-clip",
              text: (el.textContent || "").trim().slice(0, 60),
              textWidth: Math.round(textRect.width),
              clipWidth: Math.round(parentClip.width),
            });
          }
        }
      }
    }

    return { missing: false, contrast: contrastViolations, truncation: truncationViolations };
  }, scopeTestId);

  expect(violations.missing, `assertLegibility scope '${scopeTestId}' not found in DOM`).toBe(false);
  expect(
    violations.contrast,
    `contrast violations in '${scopeTestId}' (WCAG 4.5:1):\n${JSON.stringify(violations.contrast, null, 2)}`,
  ).toEqual([]);
  expect(
    violations.truncation,
    `truncation violations in '${scopeTestId}':\n${JSON.stringify(violations.truncation, null, 2)}`,
  ).toEqual([]);
}
