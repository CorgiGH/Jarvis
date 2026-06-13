/**
 * Plan 4b Task 2 (+ Plan-V family-4 generalization) — Trace-match harness totality (INV-5.1/5.2),
 * §0.9C.
 *
 * Enumerates EVERY viz instance in content/‌*‌/viz/*.yaml (via import.meta.glob raw import),
 * asserts ≥1 instance found (totality self-check — if the glob yields 0 the test REDS),
 * and for each instance:
 *   - Dispatches a per-FAMILY assertion from a harness-local registry keyed by family_id.
 *   - Unregistered family_id (or missing renderer) = explicit RED (never silent pass).
 *   - Trace-match (INV-5.1): every rendered frame's state equals the family's independent reference.
 *   - Semantic invariants (INV-5.2): each invariant holds for every rendered step (rendered DOM).
 *
 * Each family owns its OWN trace model (graph-tree compares node-label sets; chart-dist compares
 * the per-step reveal level + an independent area-under-curve probability check). The harness no
 * longer hardcodes the graph-tree parser — it dispatches `runAssertion` per family.
 *
 * See §0.9C for the authoritative contract.
 */

import { render, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { parse as parseYaml } from "yaml";
import {
  parseGraphTreeData,
  framesFromGraphTree,
} from "../GraphTreeFamily";
import {
  parseChartData,
  framesFromChartData,
} from "../ChartDistributionFamily";
import {
  parseSeqArrayData,
  framesFromSeqArrayData,
} from "../SequenceArrayFamily";
import { familyRegistry } from "../familyRegistry";
import { mergesortReference } from "./mergesortTrace";
import { chartDistributionReference } from "./chartTrace";
import { selectionSortReference } from "./selectionSortTrace";
import { GRAPH_TREE_INVARIANTS } from "../graphTreeInvariants";
import { CHART_DIST_INVARIANTS } from "../chartDistributionInvariants";
import { SEQ_ARRAY_INVARIANTS } from "../sequenceArrayInvariants";

// ─── HARNESS REGISTRY ────────────────────────────────────────────────────────
// Keyed by family_id. Each entry owns the FULL per-instance assertion (parse → frames → trace →
// rendered-DOM invariants), throwing a descriptive message on any failure. An unregistered
// family_id → the harness REDs at dispatch time below.
type HarnessEntry = {
  /** Runs trace-match + invariants for ONE instance. Throws on any failure. */
  runAssertion: (dataJson: string, instanceId: string) => void;
};

const HARNESS_REGISTRY: Record<string, HarnessEntry> = {
  "graph-tree": {
    runAssertion: (dataJson, instanceId) => {
      // ── TRACE-MATCH (INV-5.1) — node-label sets vs the independent mergesort oracle ──
      const parsed = parseGraphTreeData(dataJson, instanceId);
      const frames = framesFromGraphTree(parsed);
      const root = parsed.nodes.find((n) => !n.parent);
      if (!root) throw new Error(`${instanceId}: graph-tree has no root node`);
      const input = root.label.split(/\s+/).map((s) => {
        const n = parseInt(s, 10);
        if (isNaN(n)) throw new Error(`${instanceId}: graph-tree root label contains non-integer token "${s}"`);
        return n;
      });
      const ref = mergesortReference(input);

      if (frames.length !== ref.length) {
        throw new Error(
          `${instanceId}: step count mismatch — rendered ${frames.length} steps, reference has ${ref.length}`,
        );
      }
      for (let i = 0; i < ref.length; i++) {
        const renderedLabels = frames[i].state.activeNodes.map((n) => n.label).sort();
        const renderedHi = [...frames[i].state.highlight].sort();
        const refLabels = [...ref[i].labels].sort();
        const refHi = [...ref[i].highlight].sort();
        if (JSON.stringify(renderedLabels) !== JSON.stringify(refLabels)) {
          throw new Error(
            `${instanceId}: trace mismatch at step ${i} — ` +
              `rendered labels [${renderedLabels.join(", ")}] ≠ reference [${refLabels.join(", ")}]`,
          );
        }
        if (JSON.stringify(renderedHi) !== JSON.stringify(refHi)) {
          throw new Error(
            `${instanceId}: trace mismatch at step ${i} (highlight) — ` +
              `rendered [${renderedHi.join(", ")}] ≠ reference [${refHi.join(", ")}]`,
          );
        }
      }

      // ── SEMANTIC INVARIANTS (INV-5.2) — rendered DOM ──
      const Renderer = familyRegistry["graph-tree"];
      const { container } = render(
        <Renderer instanceId={instanceId} dataJson={dataJson} language="ro" />,
      );
      const stepFwdBtn = container.querySelector('[data-testid="graph-tree-step-fwd"]');
      for (let i = 0; i < frames.length; i++) {
        for (const invariant of GRAPH_TREE_INVARIANTS) {
          const result = invariant(container, frames[i], i, frames);
          if (!result.ok) throw new Error(`${instanceId}: ${result.message}`);
        }
        if (i < frames.length - 1 && stepFwdBtn) fireEvent.click(stepFwdBtn);
      }
    },
  },

  "chart-dist": {
    runAssertion: (dataJson, instanceId) => {
      // ── TRACE-MATCH (INV-5.1) — per-step reveal vs the independent chart oracle, which ALSO
      //    re-derives P(a≤X≤b) by integrating the supplied curve and throws naming the step on
      //    any drift. ──
      const parsed = parseChartData(dataJson, instanceId);
      const frames = framesFromChartData(parsed);
      const ref = chartDistributionReference(dataJson); // may throw (area/reveal mismatch) — names the step

      if (frames.length !== ref.length) {
        throw new Error(
          `${instanceId}: step count mismatch — rendered ${frames.length} steps, reference has ${ref.length}`,
        );
      }
      for (let i = 0; i < ref.length; i++) {
        const rendered = `reveal-${frames[i].state.reveal}`;
        if (rendered !== ref[i].label) {
          throw new Error(
            `${instanceId}: trace mismatch at step ${i} — rendered ${rendered} ≠ reference ${ref[i].label}`,
          );
        }
      }

      // ── SEMANTIC INVARIANTS (INV-5.2) — rendered DOM ──
      const Renderer = familyRegistry["chart-dist"];
      const { container } = render(
        <Renderer instanceId={instanceId} dataJson={dataJson} language="ro" />,
      );
      const stepFwdBtn = container.querySelector('[data-testid="chart-dist-step-fwd"]');
      for (let i = 0; i < frames.length; i++) {
        for (const invariant of CHART_DIST_INVARIANTS) {
          const result = invariant(container, frames[i], i, frames);
          if (!result.ok) throw new Error(`${instanceId}: ${result.message}`);
        }
        if (i < frames.length - 1 && stepFwdBtn) fireEvent.click(stepFwdBtn);
      }
    },
  },

  "seq-array": {
    runAssertion: (dataJson, instanceId) => {
      // ── TRACE-MATCH (INV-5.1) — per-step state (array contents + pointers + sortedCount + phase)
      //    vs the independent selection-sort oracle, which re-runs REAL selection sort from the
      //    instance's SEED (`values`), so the family cannot animate a step the algorithm never does
      //    nor end on an unsorted array. ──
      const parsed = parseSeqArrayData(dataJson, instanceId);
      const frames = framesFromSeqArrayData(parsed);
      const ref = selectionSortReference(parsed.values);

      if (frames.length !== ref.length) {
        throw new Error(
          `${instanceId}: step count mismatch — rendered ${frames.length} steps, reference has ${ref.length}`,
        );
      }
      for (let i = 0; i < ref.length; i++) {
        const r = ref[i];
        const s = frames[i].state;
        if (JSON.stringify(s.array) !== JSON.stringify(r.array)) {
          throw new Error(
            `${instanceId}: trace mismatch at step ${i} (array) — rendered [${s.array.join(",")}] ≠ reference [${r.array.join(",")}]`,
          );
        }
        if (s.sortedCount !== r.sortedCount || s.i !== r.i || s.j !== r.j || s.min !== r.min || s.phase !== r.phase) {
          throw new Error(
            `${instanceId}: trace mismatch at step ${i} (pointers/phase) — rendered ` +
              `{i:${s.i},j:${s.j},min:${s.min},sortedCount:${s.sortedCount},phase:${s.phase}} ≠ reference ` +
              `{i:${r.i},j:${r.j},min:${r.min},sortedCount:${r.sortedCount},phase:${r.phase}}`,
          );
        }
      }

      // ── SEMANTIC INVARIANTS (INV-5.2) — rendered DOM ──
      const Renderer = familyRegistry["seq-array"];
      const { container } = render(
        <Renderer instanceId={instanceId} dataJson={dataJson} language="ro" />,
      );
      const stepFwdBtn = container.querySelector('[data-testid="seq-array-step-fwd"]');
      for (let i = 0; i < frames.length; i++) {
        for (const invariant of SEQ_ARRAY_INVARIANTS) {
          const result = invariant(container, frames[i], i, frames);
          if (!result.ok) throw new Error(`${instanceId}: ${result.message}`);
        }
        if (i < frames.length - 1 && stepFwdBtn) fireEvent.click(stepFwdBtn);
      }
    },
  },
};

// ─── GLOB ALL VIZ INSTANCES ──────────────────────────────────────────────────
// ?raw + eager: gives us a Record<string, string> of path → YAML text.
// The relative depth from __tests__/ to content/ is 6 levels up.
// STOP if vite refuses the path — the harness REDs on 0 instances (totality self-check).
const rawInstances = import.meta.glob(
  "../../../../../../content/*/viz/*.yaml",
  { query: "?raw", import: "default", eager: true },
) as Record<string, string>;

// ─── ASSERTION HELPER (exported for the seeded-wrong-trace tests) ───────────
/**
 * Runs the full harness assertion (trace-match + invariants) for a single parsed YAML doc by
 * dispatching to the family's HARNESS_REGISTRY entry. Throws with a descriptive message on any
 * failure. Used by this test (expects no throw) and the seeded-wrong-trace tests (expect throw).
 */
export function assertHarnessForInstance(yamlRaw: string, instancePath = "<unknown>"): void {
  const doc = parseYaml(yamlRaw) as {
    id: string;
    family_id: string;
    instance: { data_json: string };
  };
  const instanceId = doc?.id ?? instancePath;
  const familyId = doc?.family_id;
  const dataJson = doc?.instance?.data_json;

  if (!familyId) throw new Error(`${instanceId}: missing family_id in YAML`);
  if (!dataJson) throw new Error(`${instanceId}: missing instance.data_json in YAML`);

  // (a) Family must be in the harness registry
  const entry = HARNESS_REGISTRY[familyId];
  if (!entry) {
    throw new Error(
      `${instanceId}: family_id "${familyId}" has no harness entry — ` +
        `add a runAssertion to HARNESS_REGISTRY`,
    );
  }
  // (b) Family must also be in the familyRegistry (renderer exists)
  if (!familyRegistry[familyId]) {
    throw new Error(
      `${instanceId}: family_id "${familyId}" is in the harness registry but has no renderer in familyRegistry`,
    );
  }
  // (c) Dispatch
  entry.runAssertion(dataJson, instanceId);
}

// ─── TESTS ───────────────────────────────────────────────────────────────────

describe("traceMatchHarness — totality (INV-5.1/5.2, §0.9C)", () => {
  it("glob finds ≥1 viz instance (totality self-check)", () => {
    const paths = Object.keys(rawInstances);
    expect(
      paths.length,
      `Harness glob yielded 0 instances — check the relative path or content/ layout. ` +
        `Expected ≥1 file matching content/*/viz/*.yaml`,
    ).toBeGreaterThanOrEqual(1);
  });

  it("every instance in the glob has a registered family + harness entry", () => {
    const paths = Object.keys(rawInstances);
    for (const path of paths) {
      const doc = parseYaml(rawInstances[path]) as { id?: string; family_id?: string };
      const familyId = doc?.family_id;
      expect(familyId, `Instance at ${path}: missing family_id`).toBeTruthy();
      expect(
        HARNESS_REGISTRY[familyId!],
        `Instance at ${path}: family_id "${familyId}" not in HARNESS_REGISTRY — add a runAssertion`,
      ).toBeTruthy();
      expect(
        familyRegistry[familyId!],
        `Instance at ${path}: family_id "${familyId}" not in familyRegistry (no renderer)`,
      ).toBeTruthy();
    }
  });

  it("every instance passes trace-match + semantic invariants (INV-5.1/5.2)", () => {
    const paths = Object.keys(rawInstances);
    for (const path of paths) {
      // assertHarnessForInstance throws on any failure — vitest surfaces the message.
      expect(() => assertHarnessForInstance(rawInstances[path], path)).not.toThrow();
    }
  });
});
