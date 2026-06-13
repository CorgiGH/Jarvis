/**
 * Plan-4b Task 7 / §0.9G — chrome EN-literal grep (INV-8.3).
 *
 * Scans the component files listed in tools/chrome-scope.json for
 * learner-visible EN literals (JSX text nodes + learner-visible string
 * attributes: title, placeholder, aria-label, alt).  A literal is flagged
 * if it:
 *   (a) contains a token from the pinned EN_WORDS vocabulary, OR
 *   (b) is ≥3 words and has EN-stopword ratio ≥ threshold,
 * UNLESS the literal:
 *   - is imported from chromeStrings / practiceStrings / lessonStrings, OR
 *   - matches an allowPattern (import lines, data-testid, className, etc.).
 *
 * Exit 0 = clean; exit 1 = hits found (file:line listed on stdout).
 *
 * Anti-vacuity: exits 1 if the `files` scope resolves to zero files overall.
 * Individual entries that match zero files are tolerated (practice/*.tsx may
 * not exist yet until Lane B merges).
 *
 * §0.9G R-4b-Q7: grep scope = tools/chrome-scope.json allowlist.
 */

import { readFileSync, existsSync, readdirSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, "..");
const SCOPE_FILE = resolve(HERE, "chrome-scope.json");

// ---------------------------------------------------------------------------
// Pinned vocabulary (§0.9E / §0.9G — same constants as LanguageGate.kt)
// ---------------------------------------------------------------------------

/** DEFINITELY/MAYBE/GUESS/IDK class + common chrome vocabulary */
export const EN_WORDS = new Set([
  // catalogue class (§0.9G)
  "definitely", "maybe", "guess", "idk", "save", "reset", "loading", "share",
  // shell chrome
  "play", "frame", "voice",
  // common UI chrome
  "send", "grant", "revoke", "close", "open", "cancel", "back", "next",
  "search", "filter", "refresh", "submit", "apply", "create", "delete",
  "error", "warning", "success", "info", "ok",
  // chat/message surface (finding 4 fix: "Chat messages", "Message Jarvis" class)
  "chat", "message",
  // EN vocabulary from the tutor surface
  "skeleton", "heap", "array", "loop", "stack", "string", "pointer",
  "queue", "tree", "graph", "node", "edge", "sort", "merge", "insert",
  "traversal", "algorithm", "data", "structure",
]);

/** EN stopwords (§0.9E) */
const EN_STOPWORDS = new Set([
  "the", "and", "of", "to", "is", "are", "with", "for", "this", "that",
  "it", "its", "in", "on", "at", "by", "an", "a", "or", "not", "be",
  "was", "were", "has", "have", "had", "will", "would", "can", "could",
  "should", "may", "might", "do", "does", "did", "from", "as", "into",
  "up", "down", "out", "so", "if",
]);

const EN_RATIO_THRESHOLD = 0.18;
const SHORT_STRING_WORDS = 3;

/** Strings file import patterns — files importing these have their literals checked. */
const STRINGS_FILE_PATTERNS = ["chromeStrings", "practiceStrings", "lessonStrings"];

/**
 * Allow patterns applied to full LINE context.
 * If any pattern matches the line, the line is skipped wholesale.
 *
 * NOTE: `className` is intentionally NOT here — a line with className="sr-only"
 * can still contain a learner-visible JSX text node on the same line (e.g.
 * `<label className="sr-only">Message Jarvis</label>` — finding-3 fix).
 * CSS utility class values (kebab-case tokens) do not trigger EN_WORDS or
 * stopword-ratio legs, so no false positives arise from removing className.
 */
const ALLOW_LINE_REGEX = [
  /^\s*import\s+/,                               // import statements
  /^\s*\/\//,                                    // comment lines
  /^\s*\*/,                                      // JSDoc lines
  /data-testid\s*=\s*["'][^"']*["']/,            // data-testid="..."
  /console\.(log|error|warn|info|debug)/,         // console calls
  /aria-label\s*=\s*\{/,                         // aria-label={expr} (dynamic)
  /aria-labelledby\s*=/,                         // aria-labelledby
  /\btype\s*=\s*["'][^"']*["']/,                 // type="..." (form types)
];

/** Allow patterns applied to extracted literal strings. */
const ALLOW_LITERAL_REGEX = [
  /^[a-zA-Z_$][a-zA-Z0-9_$/.:-]*$/,             // pure identifiers
  /^\d+(\.\d+)?$/,                               // numbers
  /^[a-z][a-z0-9-]*$/,                          // kebab-case (data attr values)
  /^https?:\/\//,                                // URLs
  /^\{.*\}$/,                                    // JSX expression blocks
  /^[A-Z_][A-Z0-9_]+$/,                         // ALL_CAPS constants
];

// ---------------------------------------------------------------------------
// Exported helpers (used by tests)
// ---------------------------------------------------------------------------

/**
 * Returns true if the file source imports from a strings file AND the literal
 * appears as a bare quoted string (which would be defined there, not hardcoded inline).
 *
 * NOTE: This function is kept for use in tests but is NO LONGER called from
 * flagEnLiterals. The original logic was inverted: it returned true (clean/skip)
 * when the literal was NOT a quoted string in source, which silently suppressed
 * JSX text nodes (e.g. `>Save changes<`) in any file that imported chromeStrings.
 * The fix: flagEnLiterals does not call isFromStringsImport. JSX text nodes that
 * are hardcoded in the component are bugs regardless of whether the file has a
 * strings import. S.key expressions are already filtered by trimmed.startsWith("{").
 * (finding-2 fix, §0.9G bounded-fix-round 2026-06-13)
 */
export function isFromStringsImport(src, literal) {
  const hasStringsImport = STRINGS_FILE_PATTERNS.some(p => src.includes(p));
  if (!hasStringsImport) return false;
  const escapedLiteral = literal.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const barePattern = new RegExp(`["'\`]${escapedLiteral}["'\`]`);
  return !barePattern.test(src);
}

/**
 * Returns true if a LINE matches an allow pattern.
 * The function receives a full line of source code.
 */
export function matchesAllowPattern(line) {
  return ALLOW_LINE_REGEX.some(re => re.test(line));
}

/**
 * Checks a single extracted string literal for EN content.
 * Returns true if it should be flagged.
 */
function isEnLiteral(str) {
  const trimmed = str.trim();
  if (!trimmed || trimmed.length < 2) return false;

  // Skip pure allowed literal patterns
  if (ALLOW_LITERAL_REGEX.some(re => re.test(trimmed))) return false;

  // Tokenize (handle separators like ·, ×, /, ·)
  const tokens = trimmed.toLowerCase().split(/[\s/·×]+/).filter(Boolean);

  // (a) EN_WORDS vocabulary leg
  const RO_SUFFIXES = /(ul|ului|urile|uri|ii|ilor|ele)$/;
  for (const rawTok of tokens) {
    const tok = rawTok.replace(/[^a-z]/g, "");
    if (!tok) continue;
    const stem = tok.replace(RO_SUFFIXES, "");
    if (EN_WORDS.has(tok) || EN_WORDS.has(stem)) return true;
  }

  // (b) EN-stopword ratio leg (only for ≥SHORT_STRING_WORDS words)
  const wordTokens = tokens.filter(t => /^[a-z]{2,}$/.test(t));
  if (wordTokens.length >= SHORT_STRING_WORDS) {
    const stopCount = wordTokens.filter(t => EN_STOPWORDS.has(t)).length;
    const ratio = stopCount / wordTokens.length;
    if (ratio >= EN_RATIO_THRESHOLD) return true;
  }

  return false;
}

/**
 * Scan TSX/TS source for learner-visible EN literals.
 * Returns an array of { file, line, literal } hits.
 *
 * Design note: isFromStringsImport is NOT called here. A hardcoded JSX text node
 * or attribute literal is a bug regardless of whether the file imports chromeStrings —
 * that is precisely the post-sweep scenario this tool guards against. S.key accesses
 * (`{S.sendButton}`) are filtered by the `trimmed.startsWith("{")` check below.
 * (finding-2 fix, §0.9G bounded-fix-round 2026-06-13)
 */
export function flagEnLiterals(src, filepath) {
  const hits = [];
  const lines = src.split("\n");

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const lineNum = i + 1;

    // Skip lines that match allow patterns wholesale
    if (matchesAllowPattern(line)) continue;

    const candidates = [];

    // 1. Quoted string attributes: title="...", placeholder="...", aria-label="...", alt="..."
    const attrRe = /(?:title|placeholder|aria-label|alt)\s*=\s*["']([^"']{2,})["']/g;
    let m;
    while ((m = attrRe.exec(line)) !== null) {
      candidates.push(m[1]);
    }

    // 2. JSX text content between > and < (not inside { })
    // Matches text between a closing > and an opening < on the same line
    const jsxTextRe = />\s*([^<>{}\n\r]{3,}?)\s*(?:<|$)/g;
    while ((m = jsxTextRe.exec(line)) !== null) {
      const text = m[1].trim();
      if (text && text.length >= 2 && !text.startsWith("{")) candidates.push(text);
    }

    for (const literal of candidates) {
      const trimmed = literal.trim();
      if (!trimmed || trimmed.length < 2) continue;
      if (trimmed.startsWith("{") || trimmed.startsWith("$")) continue;

      if (isEnLiteral(trimmed)) {
        hits.push({ file: filepath, line: lineNum, literal: trimmed });
      }
    }
  }

  return hits;
}

// ---------------------------------------------------------------------------
// Scope file loading and file resolution
// ---------------------------------------------------------------------------

function loadScope() {
  if (!existsSync(SCOPE_FILE)) {
    throw new Error(`chrome-scope.json not found at ${SCOPE_FILE}`);
  }
  return JSON.parse(readFileSync(SCOPE_FILE, "utf8"));
}

function resolveFiles(scope) {
  const { files = [], excluded = [] } = scope;
  const excludedPaths = new Set(
    excluded.map(e => (typeof e === "string" ? e : e.path)),
  );

  const resolved = [];
  for (const pattern of files) {
    if (pattern.includes("*")) {
      // Glob pattern — resolve the directory and list matching files
      const parts = pattern.split("/");
      const globIdx = parts.findIndex(p => p.includes("*"));
      const dirParts = parts.slice(0, globIdx);
      const globPart = parts[globIdx]; // e.g. "*.tsx"
      const ext = globPart.replace(/^\*/g, "");
      const dir = resolve(REPO_ROOT, ...dirParts);
      try {
        const entries = readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
          if (
            entry.isFile() &&
            entry.name.endsWith(ext) &&
            !entry.name.match(/\.(test|spec)\./)
          ) {
            const fullPath = join(dir, entry.name);
            const rel = [...dirParts, entry.name].join("/");
            if (!excludedPaths.has(rel) && !resolved.includes(fullPath)) {
              resolved.push(fullPath);
            }
          }
        }
      } catch {
        // Directory doesn't exist yet — tolerated (e.g. practice/*.tsx before Lane B)
      }
    } else {
      // Direct path
      const fullPath = resolve(REPO_ROOT, pattern);
      const rel = pattern.replace(/\\/g, "/");
      if (existsSync(fullPath) && !excludedPaths.has(rel) && !resolved.includes(fullPath)) {
        resolved.push(fullPath);
      }
    }
  }

  return resolved;
}

// ---------------------------------------------------------------------------
// Main entry point
// ---------------------------------------------------------------------------
function main() {
  const scope = loadScope();
  const files = resolveFiles(scope);

  if (files.length === 0) {
    process.stderr.write(
      "chrome-en-grep: ERROR — scope resolved to ZERO files. " +
      "Check tools/chrome-scope.json `files` entries. " +
      "Anti-vacuity gate: the scope must match at least one file.\n",
    );
    process.exit(1);
  }

  const allHits = [];
  for (const f of files) {
    if (!/\.(tsx?)$/.test(f)) continue;

    let src;
    try {
      src = readFileSync(f, "utf8");
    } catch {
      continue;
    }

    const relPath = f.replace(REPO_ROOT, "").replace(/^[/\\]/, "").replace(/\\/g, "/");
    const hits = flagEnLiterals(src, relPath);
    allHits.push(...hits);
  }

  if (allHits.length === 0) {
    process.stdout.write(`chrome-en-grep: 0 hits across ${files.length} scoped files — clean.\n`);
    process.exit(0);
  }

  process.stdout.write(
    `chrome-en-grep: ${allHits.length} EN literal(s) found:\n` +
    allHits.map(h => `  ${h.file}:${h.line}  ${JSON.stringify(h.literal)}`).join("\n") +
    "\n",
  );
  process.exit(1);
}

// Run main only when invoked directly (not when imported by tests)
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main();
}
