/**
 * Plan-V family 7 (AMENDMENT 2026-06-17) — the STRUCTURE-ISOMORPHISM gate for the class-diagram family.
 *
 * This is the §5.4 ANALOG for a static structure. Trace-match cannot apply (a class diagram does not
 * execute — no steps, no oracle, no end-state), so the hard floor is: parse the typed class model →
 * render it → read the structure BACK from the RENDERED SVG's data-* stamps → assert it is ISOMORPHIC
 * to the input model (every class/field/method/edge present with correct edge kind + direction; ZERO
 * dropped, ZERO extra).
 *
 * The whole point is that the two sides of the comparison come from DIFFERENT places:
 *   • LEFT side  = `modelStructure(model)` — derived from the parsed instance data (the input).
 *   • RIGHT side = `domStructure(container)` — read ONLY from the rendered DOM's data-* stamps, which
 *     the renderer drives from its LAYOUT output (the boxes/edges it actually drew), NOT echoed from
 *     the prop. So a render bug (a dropped method, a reversed inheritance arrow, an edge to the wrong
 *     class) makes the DOM diverge from the model and the gate trips.
 * This is NOT the read-model-on-both-sides tautology: one side never touches the DOM, the other never
 * touches the model.
 */

import type { ClassModel } from "./ClassDiagramFamily";

/** A canonical, order-independent fingerprint of a class diagram's STRUCTURE. */
export type StructureFingerprint = {
  classes: string[]; // sorted class ids
  fields: string[]; // sorted "classId:fieldName"
  methods: string[]; // sorted "classId:methodName"
  edges: string[]; // sorted "from->to:kind" (direction-bearing)
};

/** Build the fingerprint from the MODEL (the input side). */
export function modelStructure(model: ClassModel): StructureFingerprint {
  const classes: string[] = [];
  const fields: string[] = [];
  const methods: string[] = [];
  for (const c of model.classes) {
    classes.push(c.id);
    for (const f of c.fields) fields.push(`${c.id}:${f.name}`);
    for (const m of c.methods) methods.push(`${c.id}:${m.name}`);
  }
  const edges = model.edges.map((e) => `${e.from}->${e.to}:${e.kind}`);
  return {
    classes: classes.sort(),
    fields: fields.sort(),
    methods: methods.sort(),
    edges: edges.sort(),
  };
}

/** Build the fingerprint from the RENDERED DOM (the read-back side). Reads ONLY the data-* stamps. */
export function domStructure(container: Element): StructureFingerprint {
  const classes = Array.from(container.querySelectorAll("g[data-class-id]"))
    .map((g) => g.getAttribute("data-class-id") ?? "")
    .filter(Boolean);
  const fields = Array.from(container.querySelectorAll("g[data-field]"))
    .map((g) => g.getAttribute("data-field") ?? "")
    .filter(Boolean);
  const methods = Array.from(container.querySelectorAll("g[data-method]"))
    .map((g) => g.getAttribute("data-method") ?? "")
    .filter(Boolean);
  const edges = Array.from(container.querySelectorAll("g[data-edge]"))
    .map((g) => g.getAttribute("data-edge") ?? "")
    .filter(Boolean);
  return {
    classes: classes.sort(),
    fields: fields.sort(),
    methods: methods.sort(),
    edges: edges.sort(),
  };
}

/** A precise diff of two sorted string lists (what's missing from the DOM, what's extra in the DOM). */
function diffLists(modelList: string[], domList: string[]): { dropped: string[]; extra: string[] } {
  const modelSet = new Set(modelList);
  const domSet = new Set(domList);
  const dropped = modelList.filter((x) => !domSet.has(x)); // in model, not rendered
  const extra = domList.filter((x) => !modelSet.has(x)); // rendered, not in model
  return { dropped, extra };
}

export type IsoResult = { ok: true } | { ok: false; message: string };

/**
 * Assert the rendered DOM is structurally isomorphic to the model. Returns a precise, named failure on
 * ANY divergence (dropped or extra class / field / method / edge — edge mismatches include a reversed
 * direction or wrong kind, since the edge key carries `from->to:kind`).
 */
export function assertStructureIsomorphic(model: ClassModel, container: Element): IsoResult {
  const want = modelStructure(model);
  const got = domStructure(container);
  for (const key of ["classes", "fields", "methods", "edges"] as const) {
    const { dropped, extra } = diffLists(want[key], got[key]);
    if (dropped.length > 0 || extra.length > 0) {
      const parts: string[] = [];
      if (dropped.length > 0) parts.push(`DROPPED (in model, not rendered): [${dropped.join(", ")}]`);
      if (extra.length > 0) parts.push(`EXTRA (rendered, not in model): [${extra.join(", ")}]`);
      return {
        ok: false,
        message: `structure-isomorphism FAIL on ${key} — ${parts.join("; ")}`,
      };
    }
  }
  return { ok: true };
}
