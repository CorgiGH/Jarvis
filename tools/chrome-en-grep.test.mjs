/**
 * Plan-4b Task 7 / §0.9G — chrome-en-grep.mjs unit tests.
 * Uses node:test (same convention as generate-design-md.test.mjs, etc.).
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  isFromStringsImport,
  matchesAllowPattern,
  flagEnLiterals,
} from "./chrome-en-grep.mjs";

// ---------------------------------------------------------------------------
// isFromStringsImport
// ---------------------------------------------------------------------------
test("isFromStringsImport: literal bound to chromeStrings import is clean", () => {
  const src = `
    import { chatPane as S } from "../lib/chromeStrings";
    const x = S.sendButton;
  `;
  assert.ok(isFromStringsImport(src, "SEND"));
});

test("isFromStringsImport: literal bound to lessonStrings import is clean", () => {
  const src = `
    import { lessonStrings as LS } from "../lib/lessonStrings";
    const y = LS.next;
  `;
  assert.ok(isFromStringsImport(src, "Continuă"));
});

test("isFromStringsImport: literal NOT from a strings import is not clean", () => {
  const src = `const label = "Save";`;
  assert.ok(!isFromStringsImport(src, "Save"));
});

// ---------------------------------------------------------------------------
// matchesAllowPattern — tests LINE context strings (as the function receives them)
// ---------------------------------------------------------------------------
test("matchesAllowPattern: full import line is allowed", () => {
  assert.ok(matchesAllowPattern('import { chatPane as S } from "../lib/chromeStrings";'));
});

test("matchesAllowPattern: data-testid line is allowed", () => {
  assert.ok(matchesAllowPattern('  <div data-testid="beat-figure-scrubber">'));
});

test("matchesAllowPattern: className line is allowed", () => {
  assert.ok(matchesAllowPattern('  className="flex items-center gap-2"'));
});

test("matchesAllowPattern: identifier-only token is allowed", () => {
  assert.ok(matchesAllowPattern("console.error"));
});

test("matchesAllowPattern: plain learner-visible string is NOT allowed", () => {
  assert.ok(!matchesAllowPattern("Save changes to the workspace"));
});

// ---------------------------------------------------------------------------
// flagEnLiterals — the main detection function
// ---------------------------------------------------------------------------
test("flagEnLiterals: flags an EN enWords hit in a JSX attribute", () => {
  const src = `
    export function Foo() {
      return <input placeholder="Save your work here" />;
    }
  `;
  const hits = flagEnLiterals(src, "Foo.tsx");
  assert.ok(hits.length > 0, "expected at least one hit for 'Save' in placeholder");
  assert.ok(hits.some(h => h.literal.toLowerCase().includes("save")));
});

test("flagEnLiterals: flags a 3+ word EN-stopword-heavy run", () => {
  const src = `
    export function Bar() {
      return <div title="the heap is ready for search">x</div>;
    }
  `;
  const hits = flagEnLiterals(src, "Bar.tsx");
  assert.ok(hits.length > 0, "expected at least one hit for stopword-heavy EN run");
});

test("flagEnLiterals: string imported from chromeStrings does not flag", () => {
  const src = `
    import { chatPane as S } from "../lib/chromeStrings";
    export function Baz() {
      return <button placeholder={S.sendButton}>{S.sendButton}</button>;
    }
  `;
  const hits = flagEnLiterals(src, "Baz.tsx");
  assert.equal(hits.length, 0, "imported string should not flag");
});

test("flagEnLiterals: data-testid value does not flag", () => {
  const src = `
    export function Qux() {
      return <div data-testid="save-button">ok</div>;
    }
  `;
  const hits = flagEnLiterals(src, "Qux.tsx");
  assert.equal(hits.length, 0, "data-testid value should not flag");
});

test("flagEnLiterals: className string does not flag", () => {
  const src = `
    export function Qux2() {
      return <div className="flex items-center gap-2">ok</div>;
    }
  `;
  const hits = flagEnLiterals(src, "Qux2.tsx");
  assert.equal(hits.length, 0, "className should not flag");
});

test("flagEnLiterals: returns empty array for clean RO source", () => {
  const src = `
    import { scratchpad as S } from "../lib/chromeStrings";
    export function Clean() {
      return <div aria-label={S.ariaLabel}>{S.heading}</div>;
    }
  `;
  const hits = flagEnLiterals(src, "Clean.tsx");
  assert.equal(hits.length, 0);
});

test("flagEnLiterals: flags aria-label with EN string literal", () => {
  const src = `
    export function WithAriaLabel() {
      return <button aria-label="Close the drawer">x</button>;
    }
  `;
  const hits = flagEnLiterals(src, "WithAriaLabel.tsx");
  assert.ok(hits.length > 0, "expected aria-label EN string to be flagged");
});
