/**
 * Plan 4b Task 2 — Trace-match harness totality (INV-5.1/5.2), §0.9C.
 *
 * Enumerates EVERY viz instance in content/‌*‌/viz/*.yaml (via import.meta.glob raw import),
 * asserts ≥1 instance found (totality self-check — if the glob yields 0 the test REDS),
 * and for each instance:
 *   - Dispatches {referenceExecutor, invariants} from a harness-local registry keyed by family_id.
 *   - Unregistered family_id or missing executor/invariants = explicit RED (never silent pass).
 *   - Trace-match (INV-5.1): every rendered frame's labels+highlight equal the reference.
 *   - Semantic invariants (INV-5.2): each invariant holds for every rendered step.
 *
 * The invariants operate on the mounted family DOM via data-node-id + SVG transform attributes
 * (the family computes real geometry; jsdom reads it back).
 *
 * See §0.9C for the authoritative contract.
 */

import { render, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { parse as parseYaml } from "yaml";
import {
  parseGraphTreeData,
  framesFromGraphTree,
  type GraphTreeState,
} from "../GraphTreeFamily";
import { familyRegistry } from "../familyRegistry";
import type { Frame } from "../../AlgoStepperShell";
import { mergesortReference, type RefStep } from "./mergesortTrace";
import { GRAPH_TREE_INVARIANTS, type GraphTreeInvariant } from "../graphTreeInvariants";

// ─── HARNESS REGISTRY ────────────────────────────────────────────────────────
// Keyed by family_id.  An unregistered family_id → the harness REDs.

type ReferenceExecutor = (dataJson: string) => RefStep[];
type HarnessEntry = {
  referenceExecutor: ReferenceExecutor;
  invariants: GraphTreeInvariant[];
};
const HARNESS_REGISTRY: Record<string, HarnessEntry> = {
  "graph-tree": {
    /**
     * Derive reference from the INSTANCE's root node label (the initial unsorted list).
     * This generalises the existing pin: any graph-tree instance whose root is a
     * space-separated list of integers is exercised by the real mergesort oracle.
     */
    referenceExecutor: (dataJson: string): RefStep[] => {
      const parsed = parseGraphTreeData(dataJson, "harness-instance");
      const root = parsed.nodes.find((n) => !n.parent);
      if (!root) throw new Error("graph-tree: no root node found in data_json");
      const input = root.label.split(/\s+/).map((s) => {
        const n = parseInt(s, 10);
        if (isNaN(n)) throw new Error(`graph-tree: root label contains non-integer token "${s}"`);
        return n;
      });
      return mergesortReference(input);
    },
    invariants: GRAPH_TREE_INVARIANTS,
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

// ─── ASSERTION HELPER (exported for seededWrongTrace.test.tsx) ───────────────

/**
 * Runs the full harness assertion (trace-match + invariants) for a single parsed YAML doc.
 * Throws with a descriptive message on any failure.
 * Used by both this test (expects no throw) and seededWrongTrace.test.tsx (expects throw).
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
        `add a referenceExecutor + invariants list to HARNESS_REGISTRY`,
    );
  }

  // (b) Family must also be in the familyRegistry (renderer exists)
  if (!familyRegistry[familyId]) {
    throw new Error(
      `${instanceId}: family_id "${familyId}" is in the harness registry but has no renderer in familyRegistry`,
    );
  }

  // (c) Reference executor and invariants must be truthy arrays/functions
  if (typeof entry.referenceExecutor !== "function") {
    throw new Error(`${instanceId}: harness entry for "${familyId}" missing referenceExecutor`);
  }
  if (!Array.isArray(entry.invariants) || entry.invariants.length === 0) {
    throw new Error(`${instanceId}: harness entry for "${familyId}" has empty/missing invariants list`);
  }

  // ── TRACE-MATCH (INV-5.1) ──────────────────────────────────────────────────
  const parsed = parseGraphTreeData(dataJson, instanceId);
  const frames = framesFromGraphTree(parsed);
  const ref = entry.referenceExecutor(dataJson);

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

  // ── SEMANTIC INVARIANTS (INV-5.2) — rendered DOM ──────────────────────────
  // Render the family component (mounts step 0). Then advance through all frames,
  // checking each invariant at each step via data-node-id DOM attributes.
  const Renderer = familyRegistry[familyId];
  const { container, getByTestId } = render(
    <Renderer instanceId={instanceId} dataJson={dataJson} language="ro" />,
  );

  // The shell root is `{prefix}-root`; we use `container` (the jsdom wrapper) for queries.
  // Step through every frame (starting at 0 which is already rendered).
  const stepFwdTestId = `${familyId === "graph-tree" ? "graph-tree" : familyId}-step-fwd`;
  const stepFwdBtn = container.querySelector(`[data-testid="${stepFwdTestId}"]`);

  for (let i = 0; i < frames.length; i++) {
    // At this point idx=i is rendered. Run all invariants.
    for (const invariant of entry.invariants) {
      const result = invariant(container, frames[i], i, frames);
      if (!result.ok) {
        throw new Error(`${instanceId}: ${result.message}`);
      }
    }
    // Advance to next frame (unless at last)
    if (i < frames.length - 1 && stepFwdBtn) {
      fireEvent.click(stepFwdBtn);
    }
  }
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

  it("every instance in the glob has a registered family + executor + invariants", () => {
    const paths = Object.keys(rawInstances);
    for (const path of paths) {
      const doc = parseYaml(rawInstances[path]) as { id?: string; family_id?: string };
      const familyId = doc?.family_id;
      expect(
        familyId,
        `Instance at ${path}: missing family_id`,
      ).toBeTruthy();
      expect(
        HARNESS_REGISTRY[familyId!],
        `Instance at ${path}: family_id "${familyId}" not in HARNESS_REGISTRY — ` +
          `add a referenceExecutor + invariants`,
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
