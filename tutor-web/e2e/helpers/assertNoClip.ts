import { expect, type Page } from "@playwright/test";

/**
 * Plan-3 §0.9H / INV-5.3 — the standing no-clip gate, ported VERBATIM-logic from
 * audit.viz.mjs:76-125 (the three checks + exact thresholds):
 *   1. viewport-width overflow  : element.right > vw+1 OR element.left < -1
 *   2. text clipping            : a clipping element whose scroll{W,H} > client{W,H}+2 and has text
 *   3. interactive overlap      : two non-nested interactives whose intersection area > 16 px²
 *
 * Scoped to a testid subtree. Call at >=2 viewport heights per the cardinal-sin gate
 * (machine-checked: every viz/lesson workflow asserts no clip/overlap, 2+ viewport heights).
 */
export async function assertNoClip(page: Page, scopeTestId: string): Promise<void> {
  const findings = await page.evaluate((tid) => {
    const sec = document.querySelector(`[data-testid="${tid}"]`);
    if (!sec) return { missing: true, viewportOverflow: [], textClips: [], overlaps: [] };
    const out = { missing: false, viewportOverflow: [] as unknown[], textClips: [] as unknown[], overlaps: [] as unknown[] };
    const vw = document.documentElement.clientWidth;
    const visible = (el: Element) => {
      const r = el.getBoundingClientRect();
      if (r.width < 1 || r.height < 1) return false;
      const cs = getComputedStyle(el);
      return cs.display !== "none" && cs.visibility !== "hidden" && parseFloat(cs.opacity || "1") > 0.05;
    };
    const all = Array.from(sec.querySelectorAll("*"));
    // 1. viewport-width overflow (audit.viz.mjs:76-88)
    for (const el of all) {
      if (!visible(el)) continue;
      const r = el.getBoundingClientRect();
      if (r.right > vw + 1 || r.left < -1) {
        out.viewportOverflow.push({
          tag: el.tagName, cls: String(el.getAttribute("class") || "").slice(0, 60),
          left: Math.round(r.left), right: Math.round(r.right), vw,
          text: (el.textContent || "").trim().slice(0, 60),
        });
        if (out.viewportOverflow.length > 8) break;
      }
    }
    // 2. text clipping (audit.viz.mjs:89-106)
    for (const el of all) {
      if (el.namespaceURI && el.namespaceURI.includes("svg")) continue;
      if (!visible(el)) continue;
      const cs = getComputedStyle(el);
      const clipsX = ["hidden", "clip", "scroll", "auto"].includes(cs.overflowX);
      const clipsY = ["hidden", "clip", "scroll", "auto"].includes(cs.overflowY);
      const hasText = Array.from(el.childNodes).some((n) => n.nodeType === 3 && (n.textContent || "").trim());
      if (!hasText) continue;
      if ((clipsX && el.scrollWidth > el.clientWidth + 2) || (clipsY && el.scrollHeight > el.clientHeight + 2)) {
        out.textClips.push({
          tag: el.tagName, text: (el.textContent || "").trim().slice(0, 80),
          sw: el.scrollWidth, cw: el.clientWidth, sh: el.scrollHeight, ch: el.clientHeight,
        });
        if (out.textClips.length > 8) break;
      }
    }
    // 3. interactive-element overlaps (audit.viz.mjs:107-125)
    const inter = Array.from(sec.querySelectorAll('button, a, input, select, [role="button"], [role="slider"]')).filter(visible);
    for (let i = 0; i < inter.length; i++) {
      for (let j = i + 1; j < inter.length; j++) {
        const a = inter[i], c = inter[j];
        if (a.contains(c) || c.contains(a)) continue;
        const ra = a.getBoundingClientRect(), rc = c.getBoundingClientRect();
        const w = Math.min(ra.right, rc.right) - Math.max(ra.left, rc.left);
        const h = Math.min(ra.bottom, rc.bottom) - Math.max(ra.top, rc.top);
        if (w > 2 && h > 2 && w * h > 16) {
          out.overlaps.push({
            a: `${a.tagName}:${(a.getAttribute("data-testid") || a.textContent || "").trim().slice(0, 40)}`,
            b: `${c.tagName}:${(c.getAttribute("data-testid") || c.textContent || "").trim().slice(0, 40)}`,
            area: Math.round(w * h),
          });
          if (out.overlaps.length > 6) break;
        }
      }
    }
    return out;
  }, scopeTestId);

  expect(findings.missing, `no-clip scope '${scopeTestId}' not found in DOM`).toBe(false);
  expect(findings.viewportOverflow, `viewport overflow in '${scopeTestId}':\n${JSON.stringify(findings.viewportOverflow, null, 2)}`).toEqual([]);
  expect(findings.textClips, `text clipping in '${scopeTestId}':\n${JSON.stringify(findings.textClips, null, 2)}`).toEqual([]);
  expect(findings.overlaps, `interactive overlap in '${scopeTestId}':\n${JSON.stringify(findings.overlaps, null, 2)}`).toEqual([]);
}
