/**
 * Plan-V family 7 (AMENDMENT 2026-06-17) — the committed SEEDED-WRONG render paths for the
 * class-diagram structure-isomorphism gate.
 *
 * The class-diagram family is NOT trace-based: there is no oracle that re-derives a "true" trace from a
 * seed, so the seq-array / matrix-grid seeded-wrong idiom (mutate the fixture's data_json, let the
 * independent oracle catch it) does NOT transfer. For a static-structure gate the defect class the gate
 * exists to catch is a BROKEN RENDER PATH — a renderer that, given the TRUE model, drops a member or
 * reverses/retargets an edge in the rendered DOM. The amendment authorizes exactly this:
 *   "a deliberately BROKEN render path / mutated fixture (e.g. a dropped method, a reversed inheritance
 *    arrow, an edge to the wrong class) that makes the gate go RED."
 *
 * Each renderer below takes the SAME TRUE model the good fixture renders, and emits SVG whose data-*
 * stamps DIVERGE from that model in exactly one way. The gate (assertStructureIsomorphic) reads these
 * buggy stamps and is asserted to trip RED naming the offending element. This proves the gate has teeth
 * — it is the non-negotiable RED→GREEN self-test that keeps this family out of the oracle-less trap.
 *
 * These renderers stamp ONLY the data-* attributes the gate reads (and a placeholder rect for a valid
 * SVG subtree); they are intentionally NOT the production renderer — their whole job is to be wrong in
 * one controlled way.
 */

import type { ReactNode } from "react";
import type { ClassModel, EdgeKind } from "../ClassDiagramFamily";

/** A faithful stamp-only renderer (the CONTROL) — every class/field/method/edge stamped correctly.
 *  Used to prove the gate is GREEN when the DOM matches the model, isolating the bug in the variants. */
export function renderStampsFaithful(model: ClassModel): ReactNode {
  return <StampTree model={model} />;
}

/** BUG #1 — a DROPPED METHOD: skip stamping Dog's `fetch()` method. The model has it; the DOM won't. */
export function renderStampsDroppedMethod(model: ClassModel, dropClassId: string, dropMethod: string): ReactNode {
  return <StampTree model={model} dropMethod={{ classId: dropClassId, name: dropMethod }} />;
}

/** BUG #2 — a REVERSED INHERITANCE ARROW: stamp the inheritance edge as to->from instead of from->to. */
export function renderStampsReversedEdge(model: ClassModel, edgeIdx: number): ReactNode {
  return <StampTree model={model} reverseEdgeIdx={edgeIdx} />;
}

/** BUG #3 — an EDGE TO THE WRONG CLASS: retarget one edge's `to` to a different (valid) class id. */
export function renderStampsRetargetedEdge(model: ClassModel, edgeIdx: number, wrongTo: string): ReactNode {
  return <StampTree model={model} retargetEdge={{ idx: edgeIdx, to: wrongTo }} />;
}

type StampTreeProps = {
  model: ClassModel;
  dropMethod?: { classId: string; name: string };
  reverseEdgeIdx?: number;
  retargetEdge?: { idx: number; to: string };
};

function StampTree({ model, dropMethod, reverseEdgeIdx, retargetEdge }: StampTreeProps): ReactNode {
  return (
    <svg viewBox="0 0 480 360" data-testid="cd-buggy-root">
      {model.classes.map((c) => (
        <g key={c.id} data-class-id={c.id}>
          <rect x={0} y={0} width={10} height={10} fill="none" />
          {c.fields.map((f) => (
            <g key={`f-${f.name}`} data-field={`${c.id}:${f.name}`} />
          ))}
          {c.methods
            .filter((m) => !(dropMethod && dropMethod.classId === c.id && dropMethod.name === m.name))
            .map((m) => (
              <g key={`m-${m.name}`} data-method={`${c.id}:${m.name}`} />
            ))}
        </g>
      ))}
      {model.edges.map((e, ei) => {
        let from = e.from;
        let to = e.to;
        const kind: EdgeKind = e.kind;
        if (reverseEdgeIdx === ei) {
          // reverse the direction: the arrow now points the wrong way.
          const t = from;
          from = to;
          to = t;
        }
        if (retargetEdge && retargetEdge.idx === ei) {
          to = retargetEdge.to;
        }
        return <g key={`edge-${ei}`} data-edge={`${from}->${to}:${kind}`} />;
      })}
    </svg>
  );
}
