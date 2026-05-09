import { test, expect } from "vitest";
import { parseSuggestedEdits } from "../lib/suggestedEdit";

test("parses zero envelopes — body unchanged", () => {
  const r = parseSuggestedEdits("just text, no edit blocks");
  expect(r.body).toBe("just text, no edit blocks");
  expect(r.edits).toHaveLength(0);
});

test("extracts a clipboard envelope and strips it from body", () => {
  const reply = `here is a fix:\n<edit>{"id":"e1","type":"clipboard","payload":"x = 1","status":"pending"}</edit>\nlet me know.`;
  const { body, edits } = parseSuggestedEdits(reply);
  expect(edits).toHaveLength(1);
  expect(edits[0].id).toBe("e1");
  expect(edits[0].type).toBe("clipboard");
  expect(edits[0].payload).toBe("x = 1");
  expect(edits[0].status).toBe("pending");
  expect(body).not.toContain("<edit>");
  expect(body).toContain("here is a fix");
  expect(body).toContain("let me know");
});

test("parses multiple envelopes in document order", () => {
  const reply =
    `<edit>{"id":"a","type":"clipboard","payload":"foo","status":"pending"}</edit>` +
    `mid` +
    `<edit>{"id":"b","type":"clipboard","payload":"bar","status":"pending"}</edit>`;
  const { body, edits } = parseSuggestedEdits(reply);
  expect(edits.map(e => e.id)).toEqual(["a", "b"]);
  expect(body).toBe("mid");
});

test("skips malformed JSON envelope", () => {
  const reply = `before <edit>not valid json</edit> after`;
  const { body, edits } = parseSuggestedEdits(reply);
  expect(edits).toHaveLength(0);
  // Body still has the envelope stripped (consistent rendering).
  expect(body).not.toContain("<edit>");
  expect(body).toContain("before");
  expect(body).toContain("after");
});

test("rejects envelope missing required fields", () => {
  const reply = `<edit>{"id":"","type":"clipboard","payload":"x","status":"pending"}</edit>`;
  expect(parseSuggestedEdits(reply).edits).toHaveLength(0);
});

test("rejects unknown type", () => {
  const reply = `<edit>{"id":"x","type":"weird","payload":"y","status":"pending"}</edit>`;
  expect(parseSuggestedEdits(reply).edits).toHaveLength(0);
});

test("defaults missing status to pending", () => {
  const reply = `<edit>{"id":"x","type":"clipboard","payload":"y"}</edit>`;
  const { edits } = parseSuggestedEdits(reply);
  expect(edits[0].status).toBe("pending");
});

test("preserves optional label + lang", () => {
  const reply = `<edit>{"id":"x","type":"clipboard","payload":"println(1)","status":"pending","label":"insert at line 5","lang":"kotlin"}</edit>`;
  const { edits } = parseSuggestedEdits(reply);
  expect(edits[0].label).toBe("insert at line 5");
  expect(edits[0].lang).toBe("kotlin");
});

test("collapses extra blank lines after stripping envelopes", () => {
  const reply = `start\n<edit>{"id":"a","type":"clipboard","payload":"y","status":"pending"}</edit>\n\n\nend`;
  const { body } = parseSuggestedEdits(reply);
  expect(body).toBe("start\n\nend");
});
