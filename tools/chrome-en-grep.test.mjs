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

test("matchesAllowPattern: className-only line is NOT wholesale-allowed (finding-3 fix)", () => {
  // className is intentionally NOT in ALLOW_LINE_REGEX — a line with className="sr-only"
  // can also contain learner-visible JSX text on the same line. CSS utility tokens do not
  // trigger EN_WORDS or stopword-ratio legs, so no false positives arise.
  assert.ok(!matchesAllowPattern('  className="flex items-center gap-2"'),
    "className-only line must NOT be wholesale-skipped; per-candidate filtering handles it");
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

// ---------------------------------------------------------------------------
// Regression tests for finding-2: files WITH strings imports must still be
// flagged when they contain hardcoded JSX text or static attributes.
// These cover the real-world post-sweep scenario (the bug was: isFromStringsImport
// silently suppressed every JSX text node in any file that imported chromeStrings).
// ---------------------------------------------------------------------------

test("flagEnLiterals: JSX text node flagged even when file imports chromeStrings", () => {
  // Simulates: reviewer seeds <button>Save changes</button> into a swept component.
  const src = `
    import { scratchpad as S } from "../lib/chromeStrings";
    export function Scratchpad() {
      return (
        <div>
          <button>Save changes</button>
          <span>{S.heading}</span>
        </div>
      );
    }
  `;
  const hits = flagEnLiterals(src, "Scratchpad.tsx");
  assert.ok(hits.length > 0, "expected JSX text node 'Save changes' to be flagged even with strings import");
  assert.ok(hits.some(h => h.literal.toLowerCase().includes("save")), "expected 'save' to be in a hit");
});

test("flagEnLiterals: static aria-label flagged even when file imports chromeStrings", () => {
  // Simulates: ChatPane.tsx:204 aria-label="Chat messages" in a file that imports chatPane as S.
  const src = `
    import { chatPane as S } from "../lib/chromeStrings";
    export function ChatPane() {
      return (
        <div
          role="log"
          aria-label="Chat messages">
          <span>{S.sendButton}</span>
        </div>
      );
    }
  `;
  const hits = flagEnLiterals(src, "ChatPane.tsx");
  assert.ok(hits.length > 0, "expected static aria-label 'Chat messages' to be flagged even with strings import");
  assert.ok(hits.some(h => h.literal.toLowerCase().includes("chat") || h.literal.toLowerCase().includes("message")), "expected 'chat' or 'message' in a hit");
});

test("flagEnLiterals: JSX text on same line as className still flagged (finding-3 fix)", () => {
  // Simulates: <label className="sr-only">Message Jarvis</label>
  // Previously the className allow pattern suppressed the whole line.
  const src = `
    import { chatPane as S } from "../lib/chromeStrings";
    export function ChatInput() {
      return <label htmlFor="chat-input" className="sr-only">Message Jarvis</label>;
    }
  `;
  const hits = flagEnLiterals(src, "ChatInput.tsx");
  assert.ok(hits.length > 0, "expected 'Message Jarvis' to be flagged even on a line with className");
  assert.ok(hits.some(h => h.literal.toLowerCase().includes("message") || h.literal.toLowerCase().includes("jarvis")), "expected 'message' or 'jarvis' in a hit");
});

test("flagEnLiterals: properly swept component (all via S.key) reports no hits", () => {
  // Properly swept component — all strings via S.key. Zero false positives expected.
  const src = `
    import { chatPane as S } from "../lib/chromeStrings";
    export function ChatPaneClean() {
      return (
        <div role="log" aria-label={S.chatMessages}>
          <label htmlFor="chat-input" className="sr-only">{S.chatInputLabel}</label>
          <button>{S.sendButton}</button>
          <span>{S.readOnlyBadge}</span>
        </div>
      );
    }
  `;
  const hits = flagEnLiterals(src, "ChatPaneClean.tsx");
  assert.equal(hits.length, 0, "properly swept component with all S.key accesses must report zero hits");
});
