/**
 * Render server-supplied enum/key strings as human-readable text in UI.
 *
 * Closes the snake_case + SCREAMING_SNAKE leak class flagged by the
 * 2026-05-17 Slice-1.5 audit. Use everywhere a server-supplied enum or
 * rubric key lands in user-visible text.
 *
 * Rules:
 *   - underscores → spaces
 *   - result lowercased
 *   - optional `preserve: string[]` re-cases tokens after the lowercase
 *     pass (case-insensitive match, replaced with the token-as-supplied)
 *
 * Defensive: null/undefined/"" → "" (no throw).
 */
export function formatEnum(
  s: string | null | undefined,
  opts?: { preserve?: string[] },
): string {
  if (s == null || s === "") return "";
  let out = s.replace(/_/g, " ").toLowerCase();
  if (opts?.preserve) {
    for (const token of opts.preserve) {
      const re = new RegExp(`\\b${token.toLowerCase()}\\b`, "g");
      out = out.replace(re, token);
    }
  }
  return out;
}
