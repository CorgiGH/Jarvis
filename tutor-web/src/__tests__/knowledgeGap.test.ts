import { test, expect } from "vitest";
import { parseKnowledgeGaps } from "../lib/knowledgeGap";

test("zero gaps — body unchanged", () => {
  const r = parseKnowledgeGaps("plain text no envelopes");
  expect(r.body).toBe("plain text no envelopes");
  expect(r.gaps).toHaveLength(0);
});

test("extracts a CONCEPT gap and strips envelope", () => {
  const reply = `before <gap>{"id":"g1","topic":"closures","language":"kotlin","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"a closure captures..."}</gap> after`;
  const { body, gaps } = parseKnowledgeGaps(reply);
  expect(gaps).toHaveLength(1);
  expect(gaps[0].topic).toBe("closures");
  expect(gaps[0].type).toBe("CONCEPT");
  expect(gaps[0].trigger).toBe("EXPLICIT_ASK");
  expect(gaps[0].language).toBe("kotlin");
  expect(body).not.toContain("<gap>");
  expect(body).toContain("before");
  expect(body).toContain("after");
});

test("rejects unknown gap type", () => {
  const reply = `<gap>{"id":"g","topic":"t","type":"WAT","trigger":"EXPLICIT_ASK","content":"c"}</gap>`;
  expect(parseKnowledgeGaps(reply).gaps).toHaveLength(0);
});

test("rejects unknown trigger", () => {
  const reply = `<gap>{"id":"g","topic":"t","type":"CONCEPT","trigger":"WAT","content":"c"}</gap>`;
  expect(parseKnowledgeGaps(reply).gaps).toHaveLength(0);
});

test("requires non-empty topic + id + content", () => {
  expect(parseKnowledgeGaps(`<gap>{"id":"","topic":"t","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"c"}</gap>`).gaps).toHaveLength(0);
  expect(parseKnowledgeGaps(`<gap>{"id":"g","topic":"","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"c"}</gap>`).gaps).toHaveLength(0);
});

test("preserves optional exampleCode + sourceCitation", () => {
  const reply = `<gap>{"id":"g1","topic":"x","type":"SYNTAX","trigger":"MANUAL_FLAG","content":"def","exampleCode":"val x = 1","sourceCitation":"PA notes p.42"}</gap>`;
  const { gaps } = parseKnowledgeGaps(reply);
  expect(gaps[0].exampleCode).toBe("val x = 1");
  expect(gaps[0].sourceCitation).toBe("PA notes p.42");
});

test("multiple gaps in document order", () => {
  const reply =
    `<gap>{"id":"a","topic":"x","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"c"}</gap>` +
    `mid` +
    `<gap>{"id":"b","topic":"y","type":"SYNTAX","trigger":"MANUAL_FLAG","content":"c"}</gap>`;
  const { body, gaps } = parseKnowledgeGaps(reply);
  expect(gaps.map(g => g.id)).toEqual(["a", "b"]);
  expect(body).toBe("mid");
});

test("malformed JSON envelope skipped, rest preserved", () => {
  const reply = `before <gap>not json</gap> after`;
  const { body, gaps } = parseKnowledgeGaps(reply);
  expect(gaps).toHaveLength(0);
  expect(body).not.toContain("<gap>");
});
