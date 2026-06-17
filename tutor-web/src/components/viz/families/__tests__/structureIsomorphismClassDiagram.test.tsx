/**
 * Plan-V family 7 (AMENDMENT 2026-06-17) — the STRUCTURE-ISOMORPHISM gate test for the class-diagram
 * family. This is the §5.4 ANALOG for a static structure (trace-match is inapplicable — nothing runs).
 *
 * GREEN side: render the REAL ClassDiagramFamily for the committed good fixture, read the structure
 *   BACK from the rendered SVG's data-* stamps, assert it is ISOMORPHIC to the parsed model. The two
 *   sides come from different places — modelStructure() never touches the DOM, domStructure() reads
 *   ONLY the rendered DOM — so this is not the read-model-on-both-sides tautology.
 *
 * RED side: feed the SAME true model through deliberately BROKEN render paths (a dropped method, a
 *   reversed inheritance arrow, an edge retargeted to the wrong class) and assert the gate trips,
 *   naming the offending element. This is the non-negotiable RED→GREEN self-test that bars this family
 *   from VERIFIED until demonstrated (amendment §"Its verification").
 */

import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { parse as parseYaml } from "yaml";
import goodYamlRaw from "./fixtures/class-diagram-animals.yaml?raw";
import { ClassDiagramFamily, parseClassModel } from "../ClassDiagramFamily";
import {
  assertStructureIsomorphic,
  modelStructure,
  domStructure,
} from "../classDiagramStructure";
import {
  renderStampsFaithful,
  renderStampsDroppedMethod,
  renderStampsReversedEdge,
  renderStampsRetargetedEdge,
} from "./seededWrongClassDiagram";

// ── Load the committed good fixture (a real, correct OOP inheritance model) ──────────────────────────
const goodDoc = parseYaml(goodYamlRaw) as {
  id: string;
  family_id: string;
  instance: { data_json: string };
};
const GOOD_ID = goodDoc.id;
const GOOD_DATA_JSON = goodDoc.instance.data_json;
const GOOD_MODEL = parseClassModel(GOOD_DATA_JSON, GOOD_ID);

describe("structureIsomorphismClassDiagram — the gate is GREEN on the correct model", () => {
  it("fixture is a class-diagram instance with the expected OOP inheritance shape", () => {
    expect(goodDoc.family_id).toBe("class-diagram");
    // 4 classes, 2 fields (animal.name, owner.name), 4 methods, 3 edges (2 inheritance + 1 association)
    const s = modelStructure(GOOD_MODEL);
    expect(s.classes).toEqual(["animal", "cat", "dog", "owner"]);
    expect(s.methods.sort()).toEqual(
      ["animal:makeSound", "cat:makeSound", "dog:fetch", "dog:makeSound"].sort(),
    );
    expect(s.edges.sort()).toEqual(
      ["cat->animal:inheritance", "dog->animal:inheritance", "owner->animal:association"].sort(),
    );
  });

  it("the RENDERED DOM is isomorphic to the model (read-back from data-* stamps)", () => {
    if (typeof window !== "undefined") window.location.hash = "";
    const { container } = render(
      <ClassDiagramFamily instanceId={GOOD_ID} dataJson={GOOD_DATA_JSON} language="ro" />,
    );
    // The gate reads the rendered DOM on one side, the model on the other.
    const result = assertStructureIsomorphic(GOOD_MODEL, container);
    expect(result.ok, result.ok ? "" : result.message).toBe(true);

    // And, concretely, the read-back fingerprint equals the model fingerprint element-for-element.
    expect(domStructure(container)).toEqual(modelStructure(GOOD_MODEL));
  });

  it("the faithful stamp-only CONTROL is also GREEN (isolates the bug in the broken variants)", () => {
    const { container } = render(renderStampsFaithful(GOOD_MODEL));
    expect(assertStructureIsomorphic(GOOD_MODEL, container).ok).toBe(true);
  });
});

describe("structureIsomorphismClassDiagram — the gate is RED on each seeded-wrong render path", () => {
  it("DROPPED METHOD: dog.fetch() not stamped → gate FAILS naming the dropped method", () => {
    const { container } = render(renderStampsDroppedMethod(GOOD_MODEL, "dog", "fetch"));
    const result = assertStructureIsomorphic(GOOD_MODEL, container);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.message).toMatch(/structure-isomorphism FAIL on methods/);
      expect(result.message).toMatch(/dog:fetch/);
      expect(result.message).toMatch(/DROPPED/);
    }
  });

  it("REVERSED INHERITANCE ARROW: dog->animal stamped as animal->dog → gate FAILS on edges", () => {
    // edge index 0 in the fixture is dog->animal:inheritance.
    const { container } = render(renderStampsReversedEdge(GOOD_MODEL, 0));
    const result = assertStructureIsomorphic(GOOD_MODEL, container);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.message).toMatch(/structure-isomorphism FAIL on edges/);
      // the correct directed edge is DROPPED and the reversed one is EXTRA.
      expect(result.message).toMatch(/dog->animal:inheritance/);
      expect(result.message).toMatch(/animal->dog:inheritance/);
    }
  });

  it("EDGE TO THE WRONG CLASS: owner->animal retargeted to owner->cat → gate FAILS on edges", () => {
    // edge index 2 in the fixture is owner->animal:association.
    const { container } = render(renderStampsRetargetedEdge(GOOD_MODEL, 2, "cat"));
    const result = assertStructureIsomorphic(GOOD_MODEL, container);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.message).toMatch(/structure-isomorphism FAIL on edges/);
      expect(result.message).toMatch(/owner->animal:association/); // dropped
      expect(result.message).toMatch(/owner->cat:association/); // extra
    }
  });
});
