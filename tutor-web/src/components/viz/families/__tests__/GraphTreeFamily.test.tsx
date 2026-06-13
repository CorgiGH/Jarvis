import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { parse as parseYaml } from "yaml";
import { GraphTreeFamily, parseGraphTreeData, framesFromGraphTree } from "../GraphTreeFamily";
import { familyRegistry } from "../familyRegistry";
import { mergesortReference } from "./mergesortTrace";
// Raw-import the SHIPPED instance YAML so the test reads the ACTUAL bytes on disk — NOT a
// regenerated copy. A YAML edit that drifts from the family contract now goes red here (the
// previous self-regenerated `buildMergesortData()` copy never touched the YAML, so 8-vs-9 drift
// was invisible — consistency#3). The `?raw` query is Vite's raw-string loader.
import mergesortYamlRaw from "../../../../../../content/PA/viz/viz-pa-mergesort-001.yaml?raw";

// Pull the single-quoted data_json string straight out of the shipped instance YAML.
const MERGESORT_INPUT = [5, 2, 8, 1, 9, 3];
const MERGESORT_DATA_JSON: string = (() => {
  const doc = parseYaml(mergesortYamlRaw) as { instance: { data_json: string } };
  const dj = doc?.instance?.data_json;
  if (typeof dj !== "string" || dj.length === 0)
    throw new Error("viz-pa-mergesort-001.yaml: instance.data_json missing or not a string");
  return dj;
})();

describe("familyRegistry", () => {
  it("registers graph-tree (Plan 3), chart-dist + seq-array (Plan-V families 4 + 2)", () => {
    // order-insensitive: the registry is a Record, key order is not a contract.
    expect(Object.keys(familyRegistry).sort()).toEqual(["chart-dist", "graph-tree", "seq-array"]);
  });
});

describe("GraphTreeFamily — parse guard (INV-5.5 load-error shape)", () => {
  it("parses the SHIPPED well-formed payload (8 steps — locked count, §0.6 / Step-6 reconciliation)", () => {
    const parsed = parseGraphTreeData(MERGESORT_DATA_JSON, "viz-pa-mergesort-001");
    expect(parsed.nodes.length).toBeGreaterThan(0);
    // 8 steps = 4 divide (d=0..3) + 1 pause + 2 merge (m=2,m=1) + 1 final (m=0 root merge).
    // This pins the SHIPPED YAML's step count; if the YAML drifts, this goes red.
    expect(parsed.steps.length).toBe(8);
  });

  it("a missing nodes field throws naming the instance id and the bad field", () => {
    expect(() => parseGraphTreeData(JSON.stringify({ steps: [] }), "viz-bad-001"))
      .toThrow(/viz-bad-001.*nodes/s);
  });

  it("a step missing highlight throws naming the instance id and the bad field", () => {
    const bad = JSON.stringify({ nodes: [{ id: "a", label: "1" }], steps: [{ deltas: [], callout: "x" }] });
    expect(() => parseGraphTreeData(bad, "viz-bad-002")).toThrow(/viz-bad-002.*highlight/s);
  });
});

describe("GraphTreeFamily — trace-match (INV-5.1) over the MergeSort instance", () => {
  it("every step's highlight + node-label set equals the independent mergesort reference", () => {
    const parsed = parseGraphTreeData(MERGESORT_DATA_JSON, "viz-pa-mergesort-001");
    const frames = framesFromGraphTree(parsed);
    const ref = mergesortReference(MERGESORT_INPUT);
    expect(frames.length).toBe(ref.length);
    for (let i = 0; i < ref.length; i++) {
      const renderedLabels = frames[i].state.activeNodes.map((n) => n.label).sort();
      const renderedHi = [...frames[i].state.highlight].sort();
      expect(renderedLabels, `step ${i} labels`).toEqual([...ref[i].labels].sort());
      expect(renderedHi, `step ${i} highlight (by label)`).toEqual([...ref[i].highlight].sort());
    }
  });
});

describe("GraphTreeFamily — semantic invariants (INV-5.2)", () => {
  it("leaf count is preserved at every step (== input length)", () => {
    const parsed = parseGraphTreeData(MERGESORT_DATA_JSON, "viz-pa-mergesort-001");
    const frames = framesFromGraphTree(parsed);
    for (let i = 0; i < frames.length; i++) {
      const totalVals = frames[i].state.activeNodes
        .reduce((acc, n) => acc + n.label.split(/\s+/).filter(Boolean).length, 0);
      expect(totalVals, `step ${i} total values`).toBe(MERGESORT_INPUT.length);
    }
  });

  it("every merged node label equals sorted(its concatenated values)", () => {
    const parsed = parseGraphTreeData(MERGESORT_DATA_JSON, "viz-pa-mergesort-001");
    const frames = framesFromGraphTree(parsed);
    // the final step's single node must equal sorted(input)
    const finalNodes = frames[frames.length - 1].state.activeNodes;
    expect(finalNodes.length).toBe(1);
    expect(finalNodes[0].label).toBe([...MERGESORT_INPUT].sort((a, b) => a - b).join(" "));
  });

  it("the final step label is the fully sorted array", () => {
    const ref = mergesortReference(MERGESORT_INPUT);
    expect(ref[ref.length - 1].labels).toEqual([[...MERGESORT_INPUT].sort((a, b) => a - b).join(" ")]);
  });
});

describe("GraphTreeFamily — renders inside the shell without throwing", () => {
  it("mounts via the registry renderer with no runtime error", () => {
    const Renderer = familyRegistry["graph-tree"];
    const { getByTestId } = render(
      <Renderer instanceId="viz-pa-mergesort-001" dataJson={MERGESORT_DATA_JSON} language="ro" />,
    );
    // AlgoStepperShell root paints with the family's testId prefix
    expect(getByTestId("graph-tree-root")).toBeTruthy();
  });
});
