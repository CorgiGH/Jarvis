import { test, expect } from "vitest";
import { parseConcepts } from "../lib/conceptEnvelope";

test("parseConcepts extracts envelopes + leaves body sentinels", () => {
  const { body, concepts } = parseConcepts("hello <concept>laplace</concept> world");
  expect(concepts).toHaveLength(1);
  expect(concepts[0].name).toBe("laplace");
  expect(body).toMatch(/hello CONCEPT0 world/);
});

test("parseConcepts handles multiple envelopes", () => {
  const { concepts } = parseConcepts("<concept>a</concept> and <concept>b</concept>");
  expect(concepts.map(c => c.name)).toEqual(["a", "b"]);
});

test("parseConcepts no-op when no envelopes", () => {
  const { body, concepts } = parseConcepts("plain text");
  expect(concepts).toHaveLength(0);
  expect(body).toBe("plain text");
});
