import { test } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { readEvents, filterEvents } from "./event-log-reader.mjs";

const tmp = mkdtempSync(join(tmpdir(), "evtreader-"));

function writeJsonl(filename, events) {
  writeFileSync(join(tmp, filename), events.map(e => JSON.stringify(e)).join("\n") + "\n");
}

test("readEvents reads jsonl into objects", async () => {
  writeJsonl("tutor_events.2026-05-13.jsonl", [
    { event_type: "drill_grade", event_id: "e1", task_id: "t1", is_synthetic: false, ts_utc: "2026-05-13T10:00:00Z" },
    { event_type: "sidekick_ask", event_id: "e2", task_id: "t1", is_synthetic: true, ts_utc: "2026-05-13T10:01:00Z" },
  ]);
  const events = await readEvents({ dir: tmp });
  assert.equal(events.length, 2);
  assert.equal(events[0].event_id, "e1");
});

test("filterEvents filters synthetic by default", () => {
  const events = [
    { event_id: "e1", is_synthetic: false },
    { event_id: "e2", is_synthetic: true },
  ];
  assert.deepEqual(filterEvents(events, {}).map(e => e.event_id), ["e1"]);
  assert.deepEqual(filterEvents(events, { include_synthetic: true }).map(e => e.event_id), ["e1", "e2"]);
});

test("filterEvents filters by task_id and time window", () => {
  const events = [
    { event_id: "e1", task_id: "t1", ts_utc: "2026-05-13T10:00:00Z", is_synthetic: false },
    { event_id: "e2", task_id: "t2", ts_utc: "2026-05-13T11:00:00Z", is_synthetic: false },
    { event_id: "e3", task_id: "t1", ts_utc: "2026-05-13T12:00:00Z", is_synthetic: false },
  ];
  const r = filterEvents(events, { task_id: "t1", from_ts: "2026-05-13T09:00:00Z", to_ts: "2026-05-13T11:30:00Z" });
  assert.deepEqual(r.map(e => e.event_id), ["e1"]);
});
