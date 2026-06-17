import { useCallback, useMemo, type ReactNode } from "react";
import { AlgoStepperShell, type Frame, type ShellLayout } from "../AlgoStepperShell";
import { FONT_FAMILY, INK, PAPER, ACCENT, STROKE_DEFAULT, STROKE_FOCUS } from "../theme";
import type { FamilyRendererProps } from "./familyRegistry";

/**
 * Plan-V family 7 — `class-diagram` (static-structure / UML). AMENDMENT 2026-06-17
 * (`build-review/2026-06-17-family7-oop-class-diagram-amendment.md`).
 *
 * This family is STRUCTURALLY DIFFERENT from the other six: it is NOT a trace. There is no step-delta
 * stream, no per-frame state advance, no end-state, no oracle — a class diagram is ONE canonical static
 * frame (spec §5.2 line 321; amendment §"Family-contract divergence"). It therefore does NOT plug into
 * the §5.4 trace-match harness (nothing executes). Its hard floor is the deterministic STRUCTURE-
 * ISOMORPHISM gate (classDiagramStructure.ts): parse the typed class model → assert the rendered SVG is
 * isomorphic to the model (every class/field/method/edge present with correct edge type+direction, zero
 * dropped, zero extra), shipped with a committed seeded-wrong RED→GREEN self-test.
 *
 * NO-CLIP BY CONSTRUCTION (§5.3): box widths are MEASURED from the longest member/name label and sized
 * to fit — never a post-hoc screenshot check. SVG-only (§5.6).
 *
 * The figure mounts inside AlgoStepperShell with a SINGLE frame (the scrubber is degenerate/optional
 * here, per the amendment), so it reuses the shell's a11y / chrome / dark-skin plumbing unchanged.
 *
 * data-* STAMPS — driven by the RENDER/LAYOUT output (the laid-out boxes/edges the renderer actually
 * draws), NOT echoed straight from the input prop, so a render bug (a dropped method, a reversed arrow)
 * diverges the DOM from the model and the gate catches it:
 *   <g data-class-id="<id>"> … one per class box the layout placed
 *   <g data-field="<classId>:<name>"> … one per field row the renderer drew
 *   <g data-method="<classId>:<name>"> … one per method row the renderer drew
 *   <g data-edge="<from>-><to>:<kind>"> … one per edge the renderer drew (direction = from→to)
 */

// ── Typed instance schema ────────────────────────────────────────────────────────────────────────
export type Visibility = "+" | "-" | "#";
export type Stereotype = "abstract" | "interface";
export type EdgeKind =
  | "inheritance"
  | "composition"
  | "aggregation"
  | "association"
  | "dependency";

export type ClassField = { name: string; type: string; vis: Visibility };
export type ClassMethod = { name: string; ret: string; vis: Visibility };

export type ClassBox = {
  id: string;
  name: string;
  stereotype?: Stereotype;
  fields: ClassField[];
  methods: ClassMethod[];
};

export type ClassEdge = {
  from: string;
  to: string;
  kind: EdgeKind;
  label?: string;
  fromMult?: string;
  toMult?: string;
};

export type ClassModel = {
  classes: ClassBox[];
  edges: ClassEdge[];
};

const VIS = new Set<Visibility>(["+", "-", "#"]);
const STEREOTYPES = new Set<Stereotype>(["abstract", "interface"]);
const EDGE_KINDS = new Set<EdgeKind>([
  "inheritance",
  "composition",
  "aggregation",
  "association",
  "dependency",
]);

/**
 * Hand-rolled validation guard (zod-free, mirrors parseMatrixGridData). Throws naming `instanceId` +
 * the offending field/index on EVERY fault (INV-5.5: a broken instance fails admission loud, never
 * renders garbled).
 */
export function parseClassModel(dataJson: string, instanceId: string): ClassModel {
  let raw: unknown;
  try {
    raw = JSON.parse(dataJson);
  } catch (e) {
    throw new Error(`class-diagram instance '${instanceId}': data_json is not valid JSON (${String(e)})`);
  }
  const obj = raw as Record<string, unknown>;

  // ── classes ──
  if (!Array.isArray(obj.classes) || obj.classes.length < 1)
    throw new Error(`class-diagram instance '${instanceId}': field 'classes' must be a non-empty array`);

  const ids = new Set<string>();
  const classes: ClassBox[] = (obj.classes as unknown[]).map((c, ci) => {
    const co = c as Record<string, unknown>;
    if (typeof co.id !== "string" || co.id.length === 0)
      throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].id must be a non-empty string`);
    if (ids.has(co.id))
      throw new Error(`class-diagram instance '${instanceId}': duplicate class id '${co.id}'`);
    ids.add(co.id);
    if (typeof co.name !== "string" || co.name.length === 0)
      throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].name must be a non-empty string`);
    let stereotype: Stereotype | undefined;
    if (co.stereotype !== undefined) {
      if (typeof co.stereotype !== "string" || !STEREOTYPES.has(co.stereotype as Stereotype))
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].stereotype must be one of abstract|interface (got ${String(co.stereotype)})`);
      stereotype = co.stereotype as Stereotype;
    }

    if (!Array.isArray(co.fields))
      throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].fields must be an array`);
    const fseen = new Set<string>();
    const fields: ClassField[] = (co.fields as unknown[]).map((f, fi) => {
      const fo = f as Record<string, unknown>;
      if (typeof fo.name !== "string" || fo.name.length === 0)
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].fields[${fi}].name must be a non-empty string`);
      if (fseen.has(fo.name))
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}] has duplicate field '${fo.name}'`);
      fseen.add(fo.name);
      if (typeof fo.type !== "string" || fo.type.length === 0)
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].fields[${fi}].type must be a non-empty string`);
      if (typeof fo.vis !== "string" || !VIS.has(fo.vis as Visibility))
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].fields[${fi}].vis must be one of +|-|# (got ${String(fo.vis)})`);
      return { name: fo.name, type: fo.type, vis: fo.vis as Visibility };
    });

    if (!Array.isArray(co.methods))
      throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].methods must be an array`);
    const mseen = new Set<string>();
    const methods: ClassMethod[] = (co.methods as unknown[]).map((m, mi) => {
      const mo = m as Record<string, unknown>;
      if (typeof mo.name !== "string" || mo.name.length === 0)
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].methods[${mi}].name must be a non-empty string`);
      if (mseen.has(mo.name))
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}] has duplicate method '${mo.name}'`);
      mseen.add(mo.name);
      if (typeof mo.ret !== "string" || mo.ret.length === 0)
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].methods[${mi}].ret must be a non-empty string`);
      if (typeof mo.vis !== "string" || !VIS.has(mo.vis as Visibility))
        throw new Error(`class-diagram instance '${instanceId}': classes[${ci}].methods[${mi}].vis must be one of +|-|# (got ${String(mo.vis)})`);
      return { name: mo.name, ret: mo.ret, vis: mo.vis as Visibility };
    });

    return { id: co.id, name: co.name, stereotype, fields, methods };
  });

  // ── edges ──
  if (!Array.isArray(obj.edges))
    throw new Error(`class-diagram instance '${instanceId}': field 'edges' must be an array`);
  const edges: ClassEdge[] = (obj.edges as unknown[]).map((e, ei) => {
    const eo = e as Record<string, unknown>;
    if (typeof eo.from !== "string" || !ids.has(eo.from))
      throw new Error(`class-diagram instance '${instanceId}': edges[${ei}].from '${String(eo.from)}' is not a declared class id`);
    if (typeof eo.to !== "string" || !ids.has(eo.to))
      throw new Error(`class-diagram instance '${instanceId}': edges[${ei}].to '${String(eo.to)}' is not a declared class id`);
    if (eo.from === eo.to)
      throw new Error(`class-diagram instance '${instanceId}': edges[${ei}] is a self-edge on '${String(eo.from)}' (unsupported)`);
    if (typeof eo.kind !== "string" || !EDGE_KINDS.has(eo.kind as EdgeKind))
      throw new Error(`class-diagram instance '${instanceId}': edges[${ei}].kind must be one of ${[...EDGE_KINDS].join("|")} (got ${String(eo.kind)})`);
    let label: string | undefined;
    if (eo.label !== undefined) {
      if (typeof eo.label !== "string")
        throw new Error(`class-diagram instance '${instanceId}': edges[${ei}].label must be a string when present`);
      label = eo.label;
    }
    let fromMult: string | undefined;
    if (eo.fromMult !== undefined) {
      if (typeof eo.fromMult !== "string")
        throw new Error(`class-diagram instance '${instanceId}': edges[${ei}].fromMult must be a string when present`);
      fromMult = eo.fromMult;
    }
    let toMult: string | undefined;
    if (eo.toMult !== undefined) {
      if (typeof eo.toMult !== "string")
        throw new Error(`class-diagram instance '${instanceId}': edges[${ei}].toMult must be a string when present`);
      toMult = eo.toMult;
    }
    return { from: eo.from, to: eo.to, kind: eo.kind as EdgeKind, label, fromMult, toMult };
  });

  return { classes, edges };
}

// ── Label measurement (reserve space ⇒ no-clip by construction, §5.3) ──────────────────────────────
// Off-DOM 2D context ONLY for text extents (NOT a canvas figure; the figure is pure SVG, §5.6).
let measureCanvas: { ctx: CanvasRenderingContext2D | null } | null = null;
function measureLabelWidth(text: string, fontPx: number): number {
  if (typeof document !== "undefined") {
    if (!measureCanvas) {
      const c = document.createElement("canvas");
      measureCanvas = { ctx: c.getContext("2d") };
    }
    const ctx = measureCanvas.ctx;
    if (ctx) {
      ctx.font = `${fontPx}px ${FONT_FAMILY}`;
      return Math.ceil(ctx.measureText(text).width);
    }
  }
  // SSR / no-canvas fallback: monospace ≈ 0.6em per glyph.
  return Math.ceil(text.length * fontPx * 0.6);
}

// ── Constants ────────────────────────────────────────────────────────────────────────────────────
const SVG_W = 480;
const VIEWBOX_H = 360;
const NAME_FONT = 13;
const MEMBER_FONT = 11;
const STEREO_FONT = 9;
const ROW_H = 16; // a field/method row
const NAME_BAND_H = 22; // name compartment (single line)
const STEREO_BAND_H = 12; // <<abstract>> / <<interface>> line above the name
const BOX_PAD_X = 8;
const COMPARTMENT_PAD_Y = 4;
const BOX_MIN_W = 70;
const COL_GAP = 36; // horizontal gap between columns
const ROW_GAP = 30; // vertical gap between layout rows
const MARGIN = 10;

// ── Compute box geometry — MEASURE every label, size the box to FIT (no-clip by construction) ──────
type BoxGeom = {
  cls: ClassBox;
  x: number;
  y: number;
  w: number;
  h: number;
  hasStereo: boolean;
  fieldsBandTop: number;
  methodsBandTop: number;
};

/** Render text of a field/method row, used BOTH for measuring and drawing (one source of truth). */
function fieldRowText(f: ClassField): string {
  return `${f.vis} ${f.name}: ${f.type}`;
}
function methodRowText(m: ClassMethod): string {
  return `${m.vis} ${m.name}(): ${m.ret}`;
}

function boxWidth(cls: ClassBox): number {
  let widest = measureLabelWidth(cls.name, NAME_FONT);
  if (cls.stereotype) widest = Math.max(widest, measureLabelWidth(`«${cls.stereotype}»`, STEREO_FONT));
  for (const f of cls.fields) widest = Math.max(widest, measureLabelWidth(fieldRowText(f), MEMBER_FONT));
  for (const m of cls.methods) widest = Math.max(widest, measureLabelWidth(methodRowText(m), MEMBER_FONT));
  return Math.max(BOX_MIN_W, widest + 2 * BOX_PAD_X);
}

function boxHeight(cls: ClassBox): { h: number; hasStereo: boolean; fieldsBandTop: number; methodsBandTop: number } {
  const hasStereo = !!cls.stereotype;
  const stereoH = hasStereo ? STEREO_BAND_H : 0;
  const nameH = NAME_BAND_H;
  // fields compartment: at least 1 row tall (empty compartment still draws a thin band).
  const fieldsRows = Math.max(cls.fields.length, cls.fields.length === 0 ? 1 : cls.fields.length);
  const methodsRows = Math.max(cls.methods.length, cls.methods.length === 0 ? 1 : cls.methods.length);
  const fieldsH = fieldsRows * ROW_H + 2 * COMPARTMENT_PAD_Y;
  const methodsH = methodsRows * ROW_H + 2 * COMPARTMENT_PAD_Y;
  const fieldsBandTop = stereoH + nameH;
  const methodsBandTop = fieldsBandTop + fieldsH;
  const h = stereoH + nameH + fieldsH + methodsH;
  return { h, hasStereo, fieldsBandTop, methodsBandTop };
}

/**
 * Deterministic grid layout. Classes are placed in a packed grid (left→right, top→bottom). The number
 * of columns is chosen so the widest row fits within SVG_W. Inheritance children are ordered so a child
 * tends to sit below its parent (we keep it deterministic + simple: place parents before children when
 * an inheritance edge orders them). No d3 needed — a UML diagram is a small static frame.
 *
 * NO-CLIP: the column count is derived from MEASURED widths; if even one box is wider than the canvas
 * the box width is what it is and the layout shrinks the inter-column gap, but a single class can never
 * exceed SVG_W because boxWidth caps nothing — so we additionally scale the whole frame's x-extent into
 * [MARGIN, SVG_W-MARGIN] at render time via a viewBox that always covers the full laid-out bounds.
 */
function computeLayout(model: ClassModel): { boxes: BoxGeom[]; viewW: number; viewH: number } {
  // order: a class that is the `to` of an inheritance edge (a parent) is placed before its children.
  const order = orderClasses(model);

  // measure each box first.
  const measured = order.map((cls) => {
    const w = boxWidth(cls);
    const { h, hasStereo, fieldsBandTop, methodsBandTop } = boxHeight(cls);
    return { cls, w, h, hasStereo, fieldsBandTop, methodsBandTop };
  });

  // choose column count: pack so the row width (sum of box widths + gaps) stays ≤ SVG_W where possible.
  const widest = Math.max(...measured.map((m) => m.w), BOX_MIN_W);
  const usable = SVG_W - 2 * MARGIN;
  // how many of the WIDEST boxes fit per row (lower bound on columns sizing)
  let cols = Math.max(1, Math.floor((usable + COL_GAP) / (widest + COL_GAP)));
  cols = Math.min(cols, measured.length);

  // lay out row by row; each row's boxes are top-aligned, x cursor advances by per-box width + gap.
  const boxes: BoxGeom[] = [];
  let cursorY = MARGIN;
  let maxRight = 0;
  for (let i = 0; i < measured.length; i += cols) {
    const rowItems = measured.slice(i, i + cols);
    const rowH = Math.max(...rowItems.map((m) => m.h));
    let cursorX = MARGIN;
    for (const m of rowItems) {
      boxes.push({
        cls: m.cls,
        x: cursorX,
        y: cursorY,
        w: m.w,
        h: m.h,
        hasStereo: m.hasStereo,
        fieldsBandTop: m.fieldsBandTop,
        methodsBandTop: m.methodsBandTop,
      });
      cursorX += m.w + COL_GAP;
      maxRight = Math.max(maxRight, cursorX - COL_GAP);
    }
    cursorY += rowH + ROW_GAP;
  }

  // NO-CLIP BY CONSTRUCTION (§5.3): the shell's SVG viewBox WIDTH is fixed at 480, so if the laid-out
  // content is wider than 480 we uniformly SCALE every box's x + width (and the column gaps fold in)
  // so the whole frame fits inside [MARGIN, SVG_W-MARGIN]. Height grows the viewBox via viewH, so the
  // vertical never clips. Degrade (shrink), never clip.
  const laidWidth = maxRight + MARGIN;
  if (laidWidth > SVG_W) {
    const scale = (SVG_W - MARGIN) / maxRight;
    for (const b of boxes) {
      b.x = MARGIN + (b.x - MARGIN) * scale;
      b.w = b.w * scale;
    }
  }

  const viewH = Math.max(VIEWBOX_H, cursorY - ROW_GAP + MARGIN);
  return { boxes, viewW: SVG_W, viewH };
}

/** Order classes so inheritance parents come before children (parents on top), else input order. */
function orderClasses(model: ClassModel): ClassBox[] {
  // parent depth via inheritance edges (child --inheritance--> parent in our schema, see arrowhead note).
  const parentOf = new Map<string, string[]>();
  for (const e of model.edges) {
    if (e.kind === "inheritance") {
      // child = from, parent = to: the hollow-triangle points AT the parent (to).
      const arr = parentOf.get(e.from) ?? [];
      arr.push(e.to);
      parentOf.set(e.from, arr);
    }
  }
  const depth = new Map<string, number>();
  const computeDepth = (id: string, seen: Set<string>): number => {
    if (depth.has(id)) return depth.get(id)!;
    if (seen.has(id)) return 0; // cycle guard
    seen.add(id);
    const parents = parentOf.get(id) ?? [];
    const d = parents.length === 0 ? 0 : 1 + Math.max(...parents.map((p) => computeDepth(p, seen)));
    depth.set(id, d);
    return d;
  };
  for (const c of model.classes) computeDepth(c.id, new Set());
  // stable sort by (depth asc, original index) so a parent (depth 0) precedes its child (depth ≥1).
  return [...model.classes].sort((a, b) => {
    const da = depth.get(a.id) ?? 0;
    const db = depth.get(b.id) ?? 0;
    if (da !== db) return da - db;
    return model.classes.indexOf(a) - model.classes.indexOf(b);
  });
}

// ── Edge endpoint geometry — connect two boxes border-to-border along the line of centers ──────────
function boxCenter(b: BoxGeom): { cx: number; cy: number } {
  return { cx: b.x + b.w / 2, cy: b.y + b.h / 2 };
}

/** Point on box b's border in the direction of (tx,ty) from its center. */
function borderPoint(b: BoxGeom, tx: number, ty: number): { x: number; y: number } {
  const { cx, cy } = boxCenter(b);
  const dx = tx - cx;
  const dy = ty - cy;
  if (dx === 0 && dy === 0) return { x: cx, y: cy };
  const hw = b.w / 2;
  const hh = b.h / 2;
  // scale to the box border (axis-aligned rectangle).
  const scale = 1 / Math.max(Math.abs(dx) / hw, Math.abs(dy) / hh);
  return { x: cx + dx * scale, y: cy + dy * scale };
}

// ── Render-skin palette ──────────────────────────────────────────────────────────────────────────
export type CdVariant = "light" | "dark";
const DARK = {
  boxFill: "#161616",
  nameFill: "#2a2510",
  ink: "#f4f4f4",
  rule: "#3a3a3a",
  accent: "#fde047",
  edge: "#cfcfcf",
} as const;

// ── State (single canonical frame; the model itself, plus dims so the renderer/extractor align) ────
export type ClassDiagramState = { model: ClassModel };

export function framesFromClassModel(model: ClassModel): Frame<ClassDiagramState>[] {
  return [{ state: { model }, aria: classDiagramAria(model) }];
}

function classDiagramAria(model: ClassModel): string {
  const names = model.classes.map((c) => c.name).join(", ");
  return `Diagramă de clase: ${model.classes.length} clase (${names}), ${model.edges.length} relații`;
}

// ── renderFrame — geometry + measurement + data-* stamps (light/dark skins) ────────────────────────
function renderFrame(
  model: ClassModel,
  layout: { boxes: BoxGeom[]; viewW: number; viewH: number },
  variant: CdVariant,
) {
  const dark = variant === "dark";
  const ink = dark ? DARK.ink : INK;
  const boxFill = dark ? DARK.boxFill : PAPER;
  const nameFill = dark ? DARK.nameFill : ACCENT;
  const rule = dark ? DARK.rule : INK;
  const edgeInk = dark ? DARK.edge : INK;
  const byId = new Map(layout.boxes.map((b) => [b.cls.id, b]));

  return (): ReactNode => {
    return (
      <>
        {/* ── EDGES first (under the boxes) ── */}
        {model.edges.map((e, ei) => {
          const a = byId.get(e.from);
          const b = byId.get(e.to);
          if (!a || !b) return null; // defensive — parse already validated ids
          return renderEdge(e, ei, a, b, edgeInk);
        })}

        {/* ── CLASS BOXES ── */}
        {layout.boxes.map((b) => (
          <g key={b.cls.id} data-class-id={b.cls.id} transform={`translate(${b.x},${b.y})`}>
            {/* outer box */}
            <rect x={0} y={0} width={b.w} height={b.h} fill={boxFill} stroke={rule} strokeWidth={STROKE_FOCUS} />
            {/* stereotype band */}
            {b.hasStereo && (
              <text
                x={b.w / 2}
                y={STEREO_BAND_H - 3}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={STEREO_FONT}
                fontStyle="italic"
                fill={ink}
              >
                {`«${b.cls.stereotype}»`}
              </text>
            )}
            {/* name compartment fill + text */}
            <rect
              x={0}
              y={b.hasStereo ? STEREO_BAND_H : 0}
              width={b.w}
              height={NAME_BAND_H}
              fill={nameFill}
              stroke={rule}
              strokeWidth={STROKE_DEFAULT}
            />
            <text
              x={b.w / 2}
              y={(b.hasStereo ? STEREO_BAND_H : 0) + NAME_BAND_H / 2 + NAME_FONT / 2 - 2}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={NAME_FONT}
              fontWeight={700}
              fontStyle={b.cls.stereotype === "abstract" ? "italic" : "normal"}
              fill={dark ? DARK.accent : INK}
            >
              {b.cls.name}
            </text>
            {/* fields compartment divider */}
            <line x1={0} y1={b.fieldsBandTop} x2={b.w} y2={b.fieldsBandTop} stroke={rule} strokeWidth={STROKE_DEFAULT} />
            {/* FIELDS — one <g data-field> per field row */}
            {b.cls.fields.map((f, fi) => (
              <g key={`f-${f.name}`} data-field={`${b.cls.id}:${f.name}`}>
                <text
                  x={BOX_PAD_X}
                  y={b.fieldsBandTop + COMPARTMENT_PAD_Y + fi * ROW_H + MEMBER_FONT}
                  textAnchor="start"
                  fontFamily={FONT_FAMILY}
                  fontSize={MEMBER_FONT}
                  fill={ink}
                >
                  {fieldRowText(f)}
                </text>
              </g>
            ))}
            {/* methods compartment divider */}
            <line x1={0} y1={b.methodsBandTop} x2={b.w} y2={b.methodsBandTop} stroke={rule} strokeWidth={STROKE_DEFAULT} />
            {/* METHODS — one <g data-method> per method row */}
            {b.cls.methods.map((m, mi) => (
              <g key={`m-${m.name}`} data-method={`${b.cls.id}:${m.name}`}>
                <text
                  x={BOX_PAD_X}
                  y={b.methodsBandTop + COMPARTMENT_PAD_Y + mi * ROW_H + MEMBER_FONT}
                  textAnchor="start"
                  fontFamily={FONT_FAMILY}
                  fontSize={MEMBER_FONT}
                  fill={ink}
                >
                  {methodRowText(m)}
                </text>
              </g>
            ))}
          </g>
        ))}
      </>
    );
  };
}

// ── Edge rendering — correct arrowhead per kind, drawn AT the `to` endpoint (the arrow points at `to`) ─
function renderEdge(e: ClassEdge, ei: number, a: BoxGeom, b: BoxGeom, edgeInk: string): ReactNode {
  const ca = boxCenter(a);
  const cb = boxCenter(b);
  // endpoint on `from` box border (toward `to`) and on `to` box border (toward `from`).
  const p1 = borderPoint(a, cb.cx, cb.cy); // at `from`
  const p2 = borderPoint(b, ca.cx, ca.cy); // at `to` (arrowhead lives here)
  const dashed = e.kind === "dependency";
  const markerSize = 11;
  // unit vector pointing INTO `to` (from p1 toward p2).
  const vx = p2.x - p1.x;
  const vy = p2.y - p1.y;
  const len = Math.hypot(vx, vy) || 1;
  const ux = vx / len;
  const uy = vy / len;
  // For shape markers (triangle / diamond) the LINE stops at the marker's back so it doesn't poke through.
  const headLen =
    e.kind === "inheritance" ? markerSize : e.kind === "composition" || e.kind === "aggregation" ? markerSize : 0;
  const lineEndX = p2.x - ux * headLen;
  const lineEndY = p2.y - uy * headLen;

  return (
    <g key={`edge-${ei}`} data-edge={`${e.from}->${e.to}:${e.kind}`}>
      <line
        x1={p1.x}
        y1={p1.y}
        x2={lineEndX}
        y2={lineEndY}
        stroke={edgeInk}
        strokeWidth={STROKE_DEFAULT}
        strokeDasharray={dashed ? "4 3" : undefined}
      />
      {renderArrowhead(e.kind, p2.x, p2.y, ux, uy, markerSize, edgeInk)}
      {/* multiplicity labels: near `from` (fromMult) and near `to` (toMult) */}
      {e.fromMult && (
        <text
          x={p1.x + ux * 12 + 6}
          y={p1.y + uy * 12 - 4}
          fontFamily={FONT_FAMILY}
          fontSize={STEREO_FONT}
          fill={edgeInk}
        >
          {e.fromMult}
        </text>
      )}
      {e.toMult && (
        <text
          x={p2.x - ux * 16 + 6}
          y={p2.y - uy * 16 - 4}
          fontFamily={FONT_FAMILY}
          fontSize={STEREO_FONT}
          fill={edgeInk}
        >
          {e.toMult}
        </text>
      )}
      {e.label && (
        <text
          x={(p1.x + p2.x) / 2 + 4}
          y={(p1.y + p2.y) / 2 - 4}
          fontFamily={FONT_FAMILY}
          fontSize={STEREO_FONT}
          fill={edgeInk}
        >
          {e.label}
        </text>
      )}
    </g>
  );
}

/** Draw the kind-specific arrowhead at the `to` endpoint (tip = (tx,ty), pointing along (ux,uy)). */
function renderArrowhead(kind: EdgeKind, tx: number, ty: number, ux: number, uy: number, s: number, ink: string): ReactNode {
  // perpendicular
  const px = -uy;
  const py = ux;
  const back = { x: tx - ux * s, y: ty - uy * s };
  const wing = s * 0.42;
  switch (kind) {
    case "inheritance": {
      // hollow triangle pointing AT the parent (to)
      const l = { x: back.x + px * wing, y: back.y + py * wing };
      const r = { x: back.x - px * wing, y: back.y - py * wing };
      return (
        <polygon
          points={`${tx},${ty} ${l.x},${l.y} ${r.x},${r.y}`}
          fill={PAPER}
          stroke={ink}
          strokeWidth={STROKE_DEFAULT}
        />
      );
    }
    case "composition":
    case "aggregation": {
      // diamond at the `to` (whole) end; filled = composition, hollow = aggregation
      const mid = { x: tx - ux * (s / 2), y: ty - uy * (s / 2) };
      const l = { x: mid.x + px * wing, y: mid.y + py * wing };
      const r = { x: mid.x - px * wing, y: mid.y - py * wing };
      return (
        <polygon
          points={`${tx},${ty} ${l.x},${l.y} ${back.x},${back.y} ${r.x},${r.y}`}
          fill={kind === "composition" ? ink : PAPER}
          stroke={ink}
          strokeWidth={STROKE_DEFAULT}
        />
      );
    }
    case "association":
    case "dependency": {
      // open arrow (two strokes, V-shape) — dependency's line is dashed (handled by the caller).
      const l = { x: back.x + px * wing, y: back.y + py * wing };
      const r = { x: back.x - px * wing, y: back.y - py * wing };
      return (
        <g>
          <line x1={tx} y1={ty} x2={l.x} y2={l.y} stroke={ink} strokeWidth={STROKE_DEFAULT} />
          <line x1={tx} y1={ty} x2={r.x} y2={r.y} stroke={ink} strokeWidth={STROKE_DEFAULT} />
        </g>
      );
    }
  }
}

/** ADDITIVE props — `variant` selects the render skin (default "light"); `layout` lets the lesson
 *  surface drop the white box + side controls. `testIdPrefix` is the pre-existing carry. */
export type ClassDiagramFamilyProps = FamilyRendererProps & {
  testIdPrefix?: string;
  variant?: CdVariant;
  layout?: ShellLayout;
};

export function ClassDiagramFamily({
  instanceId,
  dataJson,
  language,
  labels,
  onStep,
  testIdPrefix = "cd",
  variant = "light",
  layout: shellLayout,
}: ClassDiagramFamilyProps): ReactNode {
  const model = useMemo(() => parseClassModel(dataJson, instanceId), [dataJson, instanceId]);
  const frames = useMemo(() => framesFromClassModel(model), [model]);
  const layout = useMemo(() => computeLayout(model), [model]);
  const render = useMemo(() => renderFrame(model, layout, variant), [model, layout, variant]);
  const lastIdx = Math.max(0, frames.length - 1);
  const shellOnStep = useCallback(
    onStep ? (idx: number) => onStep(idx, lastIdx) : () => {},
    [onStep, lastIdx],
  );
  // The figure's intrinsic bounds may exceed 480×360; pass the laid-out viewBox height so the shell's
  // SVG covers the full structure (no-clip by construction — the box is sized to the content).
  const mergedLayout: ShellLayout = { ...shellLayout, viewBoxH: layout.viewH };
  return (
    <AlgoStepperShell<ClassDiagramState>
      title={`Class diagram · ${instanceId}`}
      desc={language === "ro" ? "Diagramă de clase (structură statică)" : "UML class diagram"}
      frames={frames}
      renderFrame={render}
      testIdPrefix={testIdPrefix}
      labels={labels}
      onStep={onStep ? shellOnStep : undefined}
      layout={mergedLayout}
    />
  );
}
