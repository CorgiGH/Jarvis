import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { normalizeDomForFingerprint, getStamp } from "./provenance.mjs";

test("normalizeDomForFingerprint strips ULIDs", () => {
  const html = '<div id="01KR6K07T6PATPRR5KH1JXYF8E">x</div>';
  const out = normalizeDomForFingerprint(html);
  assert.match(out, /<ULID>/);
  assert.doesNotMatch(out, /01KR6K07T6/);
});

test("normalizeDomForFingerprint strips UUIDs + ISO timestamps + epoch ms", () => {
  const html = `<div data-id="a1b2c3d4-e5f6-7890-abcd-1234567890ab">
    <span>2026-05-13T17:55:55Z</span>
    <span>1778694955123</span>
  </div>`;
  const out = normalizeDomForFingerprint(html);
  assert.match(out, /<UUID>/);
  assert.match(out, /<TS>/);
  assert.match(out, /<EPOCH>/);
});

test("normalizeDomForFingerprint strips volatile data-* attrs", () => {
  const html = '<div data-event-id="evt-1" data-session-id="s-1" data-render-key="r-1" data-nonce="n-1">x</div>';
  const out = normalizeDomForFingerprint(html);
  // The plan template uses a permissive match; the regex replaces the full data-* attr with <VOL>.
  // Accept either the replaced literal or a sentinel; the actual implementation below uses <VOL>.
  assert.ok(out.includes("<VOL>") || /<VOL>/.test(out));
});

test("normalizeDomForFingerprint strips cookies + csrf tokens", () => {
  const html = '<meta name="csrf" content="csrfToken=\\"abc123\\"" >';
  const out = normalizeDomForFingerprint(html);
  assert.match(out, /csrfToken="<TOKEN>"/);
});

test("getStamp returns required fields without page", async () => {
  const stamp = await getStamp(null, {});
  assert.equal(typeof stamp.git_head, "string");
  assert.equal(typeof stamp.bundle_hash, "string");
  assert.equal(stamp.live_dom_fingerprint, null);
  assert.equal(typeof stamp.ts_utc, "string");
  assert.equal(typeof stamp.surface_version, "string");
  assert.equal(stamp.judge_model_resolved, null);
  assert.equal(stamp.judge_prompt_sha256, null);
});

test("getStamp threads judge fields from opts", async () => {
  const stamp = await getStamp(null, {
    judge_model_resolved: "qwen/qwen-2.5-7b:free",
    judge_prompt_sha256: "deadbeef".repeat(8),
  });
  assert.equal(stamp.judge_model_resolved, "qwen/qwen-2.5-7b:free");
  assert.equal(stamp.judge_prompt_sha256.length, 64);
});

test("normalized hash is stable across volatile-only changes (Council #4 RA-1)", () => {
  const a = '<div data-event-id="evt-1" id="01KR6K07T6PATPRR5KH1JXYF8E">2026-05-13T17:55:55Z</div>';
  const b = '<div data-event-id="evt-2" id="01KR6K07T6XXXXXXXXXXXXXXXX">2026-05-13T18:01:22Z</div>';
  const h = s => createHash("sha256").update(normalizeDomForFingerprint(s)).digest("hex").slice(0,16);
  assert.equal(h(a), h(b));
});

test("getStamp: provider_name defaults to null when not provided", async () => {
  const s = await getStamp(null, {});
  assert.equal(s.provider_name, null);
});

test("getStamp: provider_name flows through from overrides", async () => {
  const s = await getStamp(null, { provider_name: "claude-cli" });
  assert.equal(s.provider_name, "claude-cli");
});
