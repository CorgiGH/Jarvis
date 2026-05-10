import { test } from "node:test";
import assert from "node:assert/strict";
import { classifySubject, classifyKind, sha256Hex } from "./extract-fiimaterials.mjs";

test("classifySubject buckets URLs into known faculty subjects", () => {
  assert.equal(classifySubject("https://fiimaterials.web.app/PA/curs1.pdf"), "PA");
  assert.equal(classifySubject("https://fiimaterials.web.app/programare-avansata/x.pdf"), "PA");
  assert.equal(classifySubject("https://fiimaterials.web.app/PS/sem.pdf"), "PS");
  assert.equal(classifySubject("https://fiimaterials.web.app/probabilitati/x.pdf"), "PS");
  assert.equal(classifySubject("https://fiimaterials.web.app/POO/lab1.pdf"), "POO");
  assert.equal(classifySubject("https://fiimaterials.web.app/ALO/exam.pdf"), "ALO");
  assert.equal(classifySubject("https://fiimaterials.web.app/SO/linux.pdf"), "SO");
  assert.equal(classifySubject("https://fiimaterials.web.app/RC/networking.pdf"), "RC");
  assert.equal(classifySubject("https://fiimaterials.web.app/random/x.pdf"), "OTHER");
});

test("classifyKind buckets by URL keyword", () => {
  assert.equal(classifyKind("https://x/curs1.pdf"), "course");
  assert.equal(classifyKind("https://x/lecture-3.pdf"), "course");
  assert.equal(classifyKind("https://x/seminar2.pdf"), "seminar");
  assert.equal(classifyKind("https://x/laborator-5.pdf"), "lab");
  assert.equal(classifyKind("https://x/tema-1.pdf"), "hw");
  assert.equal(classifyKind("https://x/partial-2021.pdf"), "exam");
  assert.equal(classifyKind("https://x/random.pdf"), "other");
});

test("sha256Hex matches known vector", () => {
  assert.equal(sha256Hex(Buffer.from("hello", "utf8")),
    "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
});
