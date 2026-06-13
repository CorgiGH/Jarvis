/**
 * Plan-4b Task 8 / §0.9E — TS-side mirror of LanguageGate.kt (Kotlin admission gate).
 *
 * Implements the SAME normalization + three legs with the SAME pinned constants as
 * `src/main/kotlin/jarvis/content/LanguageGate.kt`. Cite both: §0.9E + that file.
 * A divergence between this file and LanguageGate.kt is a bug.
 * A cross-language schema-hash CI test is carried to Plan 5 (Task 12 Step 6).
 *
 * Used by gate 4d (INV-8.4): rendered learner-visible text of every beat is run
 * through `checkField` / `extractRenderedRoText` and flagged on any non-RO hit.
 *
 * THREE legs (§0.9E, checked in order — first match wins):
 *  (1) EN-vocabulary leg (any string length, checked FIRST — plan-fix F1)
 *  (2) Diacritic leg (≥ SHORT_STRING_WORDS): any ăâîșțĂÂÎȘȚ anywhere → PASS
 *  (3) EN-stopword ratio leg (≥ SHORT_STRING_WORDS): ratio ≥ STOPWORD_RATIO_THRESHOLD → FAIL
 *
 * Normalization before any leg:
 *  - Strip «…»/‹…› guillemet-quoted spans (§8.1)
 *  - Strip code-ish tokens (numbers, identifiers with digits, formulas)
 *  - Strip per-call glossaryTerms + exempt set (post-tokenize, stem-based)
 */

// ── Pinned constants (§0.9E) — must match LanguageGate.kt exactly ──────────────────────────────

/** Minimum word count for the diacritic leg to apply. */
export const SHORT_STRING_WORDS = 3;

/** EN-stopword ratio threshold. Calibrated Task 6 Step 3 over real corpus. */
export const STOPWORD_RATIO_THRESHOLD = 0.18;

/** RO inflection suffixes to strip before EN-vocab stem lookup (longer first). */
export const RO_SUFFIXES: string[] = [
  "urile", "ului", "ilor", "ele", "uri", "ii", "ul",
];

/** Pinned EN loanword / vocabulary list (§0.9G `enWords` DEFINITELY class + technical loanwords). */
export const EN_VOCAB: Set<string> = new Set([
  // Data structure / algorithm loanwords
  "skeleton", "heap", "array", "stack", "queue", "loop", "string", "tree", "graph",
  "node", "edge", "pointer", "buffer", "cache", "token", "hash", "frame", "slot",
  "batch", "thread", "chunk", "step", "index",
  // UI chrome words (§0.9G DEFINITELY/MAYBE class)
  "loading", "reset", "share", "save", "play", "back", "forward",
  // Common short EN words that appear as bare loanwords in RO drill text
  "input", "output", "sort", "merge", "split", "swap",
]);

/** Pinned EN stopword list (~40 words, §0.9E). */
export const EN_STOPWORDS: Set<string> = new Set([
  "the", "and", "of", "to", "is", "are", "in", "a", "an", "with", "for", "this",
  "that", "it", "on", "at", "by", "as", "or", "be", "was", "were", "has", "have",
  "not", "from", "but", "they", "we", "he", "she", "you", "i", "its", "their",
  "which", "can", "will", "if", "then", "than",
]);

// ── Normalization regexes ────────────────────────────────────────────────────────────────────────

const GUILLEMET_RE = /«[^»]*»|‹[^›]*›/g;
/** Code-ish tokens: numbers, identifiers with digits, simple formulas. */
const CODE_TOKEN_RE = /[0-9]+|[a-zA-Z][a-zA-Z0-9_]*[0-9][a-zA-Z0-9_]*|O\([^)]*\)|[a-zA-Z]+[_/=+<>*]+[a-zA-Z0-9]*/g;

// ── Helpers ──────────────────────────────────────────────────────────────────────────────────────

function normalize(text: string): string {
  let s = text.replace(GUILLEMET_RE, " ");
  s = s.replace(CODE_TOKEN_RE, " ");
  return s.replace(/\s+/g, " ").trim();
}

function tokenize(s: string): string[] {
  return s
    .split(/[^\p{L}\p{N}]+/u)
    .map((t) => t.toLowerCase())
    .filter((t) => t.length > 0);
}

function stemOf(token: string): string {
  for (const suffix of RO_SUFFIXES) {
    if (token.length > suffix.length && token.endsWith(suffix)) {
      return token.slice(0, token.length - suffix.length);
    }
  }
  return token;
}

// ── Public API ───────────────────────────────────────────────────────────────────────────────────

export interface RoViolation {
  field: string;
  text: string;
  leg: "en-vocab" | "stopword-ratio";
  detail: string;
}

/**
 * Check a single field value for Romanian-language compliance.
 * Returns a list of violations (empty = pass).
 *
 * @param text           the raw field text to check.
 * @param fieldName      descriptor for error messages (e.g. "predict.prompt").
 * @param glossaryTerms  stems of glossary-approved terms to strip before the EN-vocab leg.
 * @param exempt         per-call exempt stems (same as glossaryTerms in effect).
 */
export function checkField(
  text: string,
  fieldName: string,
  glossaryTerms: Set<string> = new Set(),
  exempt: Set<string> = new Set(),
): RoViolation[] {
  const exemptStems = new Set([...glossaryTerms, ...exempt].map((s) => s.toLowerCase()));

  const normalized = normalize(text);
  if (!normalized.trim()) return [];

  const allTokens = tokenize(normalized);
  if (allTokens.length === 0) return [];

  // Filter out exempt/glossary stems
  const tokens = allTokens.filter((token) => {
    const stem = stemOf(token);
    return !exemptStems.has(token) && !exemptStems.has(stem);
  });
  if (tokens.length === 0) return [];

  // Leg (1): EN-vocabulary leg (any length, checked FIRST — plan-fix F1)
  for (const token of tokens) {
    const stem = stemOf(token);
    if (EN_VOCAB.has(stem)) {
      return [
        {
          field: fieldName,
          text: text.slice(0, 120),
          leg: "en-vocab",
          detail: `token "${token}" (stem "${stem}") is in EN_VOCAB — field must be Romanian (§0.9E EN-vocabulary leg, plan-fix F1)`,
        },
      ];
    }
  }

  // Legs (2) and (3) only for multi-word strings
  if (tokens.length < SHORT_STRING_WORDS) return [];

  // Leg (2): diacritic presence in the NORMALIZED text
  if (/[ăâîșțĂÂÎȘȚ]/.test(normalized)) return [];

  // Leg (3): EN-stopword ratio
  const stopwordCount = tokens.filter((t) => EN_STOPWORDS.has(t)).length;
  const ratio = stopwordCount / tokens.length;
  if (ratio >= STOPWORD_RATIO_THRESHOLD) {
    return [
      {
        field: fieldName,
        text: text.slice(0, 120),
        leg: "stopword-ratio",
        detail: `EN-stopword ratio ${ratio.toFixed(2)} >= threshold ${STOPWORD_RATIO_THRESHOLD} (${stopwordCount}/${tokens.length} tokens are EN stopwords)`,
      },
    ];
  }

  return [];
}

/**
 * Extract visible learner-facing text strings from the rendered DOM inside `[data-testid="${scopeTestId}"]`
 * and run each through `checkField`.
 *
 * Returns all violations found across all extracted strings.
 *
 * Used by gate 4d (INV-8.4) in seeded-violations.spec.ts and lesson-gates.spec.ts.
 *
 * @param renderedTexts  array of { field, text } objects extracted from the DOM (Playwright).
 * @param glossaryTerms  stems of glossary-approved terms.
 * @param exempt         per-call exempt stems.
 */
export function checkRenderedTexts(
  renderedTexts: Array<{ field: string; text: string }>,
  glossaryTerms: Set<string> = new Set(),
  exempt: Set<string> = new Set(),
): RoViolation[] {
  const violations: RoViolation[] = [];
  for (const { field, text } of renderedTexts) {
    violations.push(...checkField(text, field, glossaryTerms, exempt));
  }
  return violations;
}
