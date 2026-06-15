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

/**
 * ADDITIVE — `assertNoSvgOverlap`: the SVG ELEMENT-overlap pass `assertNoClip` is BLIND to.
 *
 * WHY a SEPARATE export (not folded into assertNoClip): assertNoClip's leg-3 only compares INTERACTIVE
 * elements and its leg-2 text-clip pass SKIPS the SVG namespace entirely — by design, so it never trips
 * on a figure. ~12 callers depend on that. Folding a figure-element-overlap pass into it unconditionally
 * would break callers whose scope holds a figure with INTENDED adjacencies this allow-list doesn't yet
 * model for every family (chart/graph/matrix). So this is opt-in and additive; assertNoClip is unchanged.
 *
 * It mirrors tools/svg-overlap-gate.mjs: it walks the figure SVG root under `scopeTestId`, does pairwise
 * bbox-intersection over every painted element, GROUPS tokens by the nearest [data-cell-index] ancestor,
 * and allow-lists the same intended adjacencies (same-token-group, thin structural rules, the callout
 * chip backing its own caption, glow-halo slivers, a chevron over its own column). A real cross-group
 * occlusion beyond the thresholds throws.
 *
 * TODO (load-bearing, before wiring this into the chart/graph/matrix family specs): the allow-list here is
 * CALIBRATED ONLY against the sort-merge family (boxes + bars). Other families have their own intended
 * adjacencies (e.g. a chart's axis tick labels touching the axis line, a graph edge passing under a node
 * label) that are NOT yet modelled — running this against them WILL false-positive. The tools gate
 * (tools/svg-overlap-gate.mjs) is the PRIMARY shipped artifact for sort-merge; extend the allow-list per
 * family before adding `assertNoSvgOverlap` to that family's spec. Until then, only call it on sort-merge
 * scopes.
 */
export async function assertNoSvgOverlap(
  page: Page,
  scopeTestId: string,
  opts: { areaFloor?: number; minOverlapPx?: number; thinRulePx?: number; glowSliverPx?: number } = {},
): Promise<void> {
  const cfg = {
    AREA_FLOOR: opts.areaFloor ?? 12,
    MIN_OVERLAP_PX: opts.minOverlapPx ?? 2.0,
    THIN_RULE_PX: opts.thinRulePx ?? 3.5,
    GLOW_SLIVER_PX: opts.glowSliverPx ?? 4.0,
  };
  const findings = await page.evaluate(
    ({ tid, cfg }) => {
      const { AREA_FLOOR, MIN_OVERLAP_PX, THIN_RULE_PX, GLOW_SLIVER_PX } = cfg;
      const scope = document.querySelector(`[data-testid="${tid}"]`);
      if (!scope) return { missing: true, real: [] as unknown[] };
      const svgEl = scope.tagName.toLowerCase() === "svg" ? scope : scope.querySelector("svg");
      if (!svgEl) return { missing: false, noSvg: true, real: [] as unknown[] };

      const groupKeyOf = (el: Element): string | null => {
        let n: Node | null = el;
        while (n && n !== svgEl) {
          if (n.nodeType === 1 && (n as Element).getAttribute("data-cell-index") !== null)
            return "cell#" + (n as Element).getAttribute("data-cell-index");
          n = n.parentNode;
        }
        return null;
      };
      const pointerOf = (el: Element): Element | null => {
        let n: Node | null = el;
        while (n && n !== svgEl) {
          if (n.nodeType === 1 && (n as Element).getAttribute("data-pointer") !== null) return n as Element;
          n = n.parentNode;
        }
        return null;
      };
      const textOf = (el: Element) => (el.textContent || "").replace(/\s+/g, " ").trim();
      const classify = (el: Element) => {
        const tag = el.tagName.toLowerCase();
        const cellKey = groupKeyOf(el);
        const ptrEl = pointerOf(el);
        const txt = textOf(el);
        const fill = (el.getAttribute("fill") || "").toLowerCase();
        let role = "unknown";
        if (cellKey) role = tag === "text" ? "tokenValue" : tag === "rect" ? (fill === "none" ? "tokenGlow" : "tokenBody") : "tokenOther";
        else if (ptrEl) role = "chevron";
        else if (tag === "rect" && fill === "#161616") role = "calloutChip";
        else if (tag === "text" && /CONTOPESC|LISTE SORTATE/i.test(txt)) role = "bandLabel";
        else if (tag === "text" && /INTERCLASAT|SORTAT/i.test(txt)) role = "ruleCaption";
        else if (tag === "text" && /POZI[ȚT]II/i.test(txt)) role = "posCaption";
        else if (tag === "text") role = fill === "#fde047" ? "calloutText" : "chromeText";
        else if (tag === "line") role = "baselineRule";
        else if (tag === "path") role = "bracket";
        else if (tag === "polygon") role = "polygon";
        else if (tag === "rect") role = "chromeRect";
        return { tag, role, cellKey, ptrEl, txt, fill };
      };
      const visible = (el: Element) => {
        const cs = getComputedStyle(el);
        if (cs.display === "none" || cs.visibility === "hidden") return false;
        if (parseFloat(cs.opacity || "1") <= 0.05) return false;
        const r = el.getBoundingClientRect();
        return r.width >= 0.5 && r.height >= 0.5;
      };
      const PAINT = new Set(["rect", "text", "path", "line", "polygon", "circle", "ellipse"]);
      const items = Array.from(svgEl.querySelectorAll("*"))
        .filter((el) => PAINT.has(el.tagName.toLowerCase()) && visible(el))
        .map((el) => {
          const r = el.getBoundingClientRect();
          return { el, r, meta: classify(el), thin: Math.min(r.width, r.height) };
        });
      const contains = (R: DOMRect, r: DOMRect, slack = 1.5) =>
        r.left >= R.left - slack && r.right <= R.right + slack && r.top >= R.top - slack && r.bottom <= R.bottom + slack;
      type It = (typeof items)[number];
      const allowReason = (A: It, B: It, ow: number, oh: number): string | null => {
        if (A.meta.cellKey && A.meta.cellKey === B.meta.cellKey) return "same token group";
        const chipBacks = (C: It, T: It) =>
          C.meta.role === "calloutChip" &&
          ["calloutText", "ruleCaption", "chromeText", "posCaption"].includes(T.meta.role) &&
          contains(C.r, T.r);
        if (chipBacks(A, B) || chipBacks(B, A)) return "chip backs its own caption";
        const thinStruct = (X: It, Y: It) =>
          (X.meta.role === "baselineRule" || (X.meta.tag === "rect" && X.meta.role !== "calloutChip" && X.meta.role !== "tokenBody")) &&
          X.thin <= THIN_RULE_PX &&
          ["tokenBody", "tokenValue", "tokenGlow", "chevron"].includes(Y.meta.role);
        if (thinStruct(A, B) || thinStruct(B, A)) return "thin structural rule on a bar";
        const chevOwner = (CH: It, TOK: It) => {
          if (CH.meta.role !== "chevron" || TOK.meta.role !== "tokenBody" || !CH.meta.ptrEl) return false;
          return TOK.meta.cellKey === "cell#" + CH.meta.ptrEl.getAttribute("data-pointer-index");
        };
        if (chevOwner(A, B) || chevOwner(B, A)) return "chevron over its own column";
        const glowSliver = (G: It, O: It) =>
          G.meta.role === "tokenGlow" && ["tokenBody", "tokenValue", "tokenGlow"].includes(O.meta.role) && Math.min(ow, oh) <= GLOW_SLIVER_PX;
        if (glowSliver(A, B) || glowSliver(B, A)) return "glow-halo edge sliver";
        return null;
      };
      const real: unknown[] = [];
      for (let i = 0; i < items.length; i++) {
        for (let j = i + 1; j < items.length; j++) {
          const A = items[i], B = items[j];
          if (A.el.contains(B.el) || B.el.contains(A.el)) continue;
          if (A.meta.cellKey && A.meta.cellKey === B.meta.cellKey) continue;
          const ow = Math.min(A.r.right, B.r.right) - Math.max(A.r.left, B.r.left);
          const oh = Math.min(A.r.bottom, B.r.bottom) - Math.max(A.r.top, B.r.top);
          if (ow <= 0 || oh <= 0) continue;
          const area = ow * oh;
          if (area < AREA_FLOOR || Math.min(ow, oh) < MIN_OVERLAP_PX) continue;
          if (allowReason(A, B, ow, oh)) continue;
          const lbl = (X: It) => X.meta.role + (X.meta.txt ? ` "${X.meta.txt.slice(0, 40)}"` : X.meta.cellKey ? ` ${X.meta.cellKey}` : "");
          real.push({ a: lbl(A), b: lbl(B), area: Math.round(area), ow: Math.round(ow * 10) / 10, oh: Math.round(oh * 10) / 10 });
          if (real.length > 12) break;
        }
        if (real.length > 12) break;
      }
      return { missing: false, real };
    },
    { tid: scopeTestId, cfg },
  );
  expect(findings.missing, `svg-overlap scope '${scopeTestId}' not found in DOM`).toBe(false);
  expect(findings.real, `SVG element overlap in '${scopeTestId}':\n${JSON.stringify(findings.real, null, 2)}`).toEqual([]);
}
