import { test } from "node:test";
import assert from "node:assert/strict";
import { INVARIANTS, scopeFor } from "./surface-x-invariants.mjs";

test("catalog has 10 invariants", () => {
  assert.equal(INVARIANTS.length, 10);
});

test("each invariant has id, statement, classification, scope", () => {
  for (const inv of INVARIANTS) {
    assert.ok(inv.id.match(/^INV-\d{2}$/));
    assert.equal(typeof inv.statement, "string");
    assert.ok(inv.statement.length > 20);
    assert.ok(["PASS_FAIL", "INFO"].includes(inv.classification));
    assert.equal(typeof inv.scope, "function");
  }
});

test("INV-09 and INV-10 are INFO-only", () => {
  const inv09 = INVARIANTS.find(i => i.id === "INV-09");
  const inv10 = INVARIANTS.find(i => i.id === "INV-10");
  assert.equal(inv09.classification, "INFO");
  assert.equal(inv10.classification, "INFO");
});

test("INV-01 scope brackets the drill-grade event preceded by predict input", () => {
  const events = [
    { event_id: "e1", event_type: "page_nav", ts_utc: "2026-05-13T10:00:00Z" },
    { event_id: "e2", event_type: "drill_grade", ts_utc: "2026-05-13T10:02:00Z" },
  ];
  const inv01 = INVARIANTS.find(i => i.id === "INV-01");
  const bracketed = inv01.scope(events);
  assert.ok(bracketed.length >= 1);
  assert.ok(bracketed.some(e => e.event_id === "e2"));
});

test("scopeFor wraps catalog lookup", () => {
  const events = [{ event_id: "e1", event_type: "sidekick_ask" }];
  const bracketed = scopeFor("INV-05", events);
  assert.equal(Array.isArray(bracketed), true);
});
