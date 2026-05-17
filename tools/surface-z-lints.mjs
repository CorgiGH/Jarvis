// tools/surface-z-lints.mjs

// File-extension suffix on the surrounding non-whitespace token marks a
// filename (e.g. "Linux-intro_print-ro.pdf"); the embedded snake_case is
// noise, not a user-visible enum leak. Keep narrow — only common doc/data
// extensions, NOT e.g. ".js" / ".ts" which appear in URLs.
const FILENAME_EXT_RE = /\.(pdf|md|html?|txt|json|docx?|csv|zip|png|jpe?g|svg)$/i;

export function detectSnakeCase(text, { skipFilenameTokens = false } = {}) {
  const matches = [];
  for (const m of text.matchAll(/\b[a-z]+_[a-z_]+\b/g)) {
    if (skipFilenameTokens) {
      // Expand to bounding non-whitespace token, reject if it ends with
      // a known doc/data file extension.
      let start = m.index;
      while (start > 0 && !/\s/.test(text[start - 1])) start--;
      let end = m.index + m[0].length;
      while (end < text.length && !/\s/.test(text[end])) end++;
      const token = text.slice(start, end);
      if (FILENAME_EXT_RE.test(token)) continue;
    }
    matches.push(m[0]);
  }
  return matches;
}

/**
 * Detect SCREAMING_SNAKE_CASE tokens in plain text — server-enum leaks
 * to UI (USER_MARKED_DONE, EXPLICIT_ASK, etc.). Requires at least one
 * underscore so single ALLCAPS words (PDF, OK) don't trip.
 */
export function detectScreamingSnake(text) {
  const matches = [];
  const re = /\b[A-Z]{2,}(?:_[A-Z0-9]+)+\b/g;
  let m;
  while ((m = re.exec(text)) !== null) matches.push(m[0]);
  return { matches };
}

/**
 * Detect OpenRouter / Anthropic model-name leaks in user text — strings
 * of the form `vendor/model-name(-suffix)*(:tier)?`. Excludes file paths
 * (must NOT have a file extension on the right side) and URLs (must NOT
 * be preceded by `://` or follow `/`).
 */
export function detectDottedModelName(text) {
  const matches = [];
  // Vendor: 2-20 lowercase letters/digits/hyphens
  // Model: 2-40 lowercase + digits + hyphens + dots, optional trailing :tier
  const re = /[a-z][a-z0-9-]{1,19}\/[a-z][a-z0-9.-]{1,39}(?::free|:beta|:nitro)?\b/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    const idx = m.index;
    const match = m[0];
    const endIdx = idx + match.length;

    // Reject if preceded by :// (URL scheme like https://)
    if (idx >= 2 && text[idx - 1] === '/' && text[idx - 2] === ':') {
      continue;
    }

    // Reject if preceded by . and there's a :// earlier (domain part of URL)
    if (idx > 0 && text[idx - 1] === '.' && text.substring(0, idx).includes('://')) {
      continue;
    }

    // Reject if preceded by / (path separator in URLs or file paths)
    if (idx > 0 && text[idx - 1] === '/') {
      continue;
    }

    // Reject if match ends with a file extension like .js or .ts
    if (match.match(/\.[a-z]{2,5}$/)) {
      continue;
    }

    // Reject if followed by / (continuing file path) or . (file extension)
    if (endIdx < text.length) {
      const nextChar = text[endIdx];
      if (nextChar === '/') {
        continue; // Likely a path like path/to/file
      }
      if (nextChar === '.') {
        const extMatch = text.slice(endIdx + 1).match(/^[a-z]{2,5}(\s|$)/);
        if (extMatch) {
          continue;
        }
      }
    }

    matches.push(match);
  }
  return { matches };
}

/**
 * Detect raw HTTP error envelopes leaking through to user text —
 * "HTTP 4xx" or "HTTP 5xx" patterns. The Slice-1.5 error-recovery
 * surfaces ALSO render these (Scratchpad save status, ConceptDrawer
 * load-error UI), so callers may need to allowlist specific
 * data-testid containers; this detector returns raw matches without
 * context, the orchestrator handles allowlisting.
 */
export function detectRawHttpError(text) {
  const matches = [];
  const re = /\bHTTP [45]\d{2}\b/g;
  let m;
  while ((m = re.exec(text)) !== null) matches.push(m[0]);
  return { matches };
}

/**
 * Detect placeholder text leaking to user UI — engineering markers
 * (TODO/TBD/FIXME/XXX) and copy-stub patterns (lorem ipsum).
 * Case-insensitive but preserves the match string's casing.
 */
export function detectPlaceholder(text) {
  const matches = [];
  const re = /\b(TODO|TBD|FIXME|XXX|lorem ipsum)\b/gi;
  let m;
  while ((m = re.exec(text)) !== null) matches.push(m[0]);
  return { matches };
}

// These run inside page.evaluate() — exported as strings to be serialized
export const LINT_EVAL_SCRIPT = `
(() => {
  const findings = { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };

  // snake_case scan — visible text not inside <code> / <pre>
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const parentTag = node.parentElement?.tagName?.toLowerCase();
    if (parentTag === 'code' || parentTag === 'pre') continue;
    const matches = [...node.nodeValue.matchAll(/\\b[a-z]+_[a-z_]+\\b/g)].map(m => m[0]);
    for (const m of matches) {
      findings.snake_case.push({ text: m, selector: cssPath(node.parentElement) });
    }
  }

  // contrast scan — sample N body text nodes
  function contrastRatio(rgb1, rgb2) {
    function lum([r, g, b]) {
      const s = [r, g, b].map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); });
      return 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2];
    }
    const a = lum(rgb1), b = lum(rgb2);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  }
  function parseRgb(str) {
    const m = str.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*([\\d.]+))?\\s*\\)/);
    if (!m) return null;
    const alpha = m[4] !== undefined ? parseFloat(m[4]) : 1;
    if (alpha === 0) return null;
    return [+m[1], +m[2], +m[3]];
  }
  const textEls = [...document.querySelectorAll('p, span, li, h1, h2, h3, h4, button, a, label')].slice(0, 50);
  for (const el of textEls) {
    if (!el.textContent.trim()) continue;
    const cs = getComputedStyle(el);
    const fg = parseRgb(cs.color);
    const bg = parseRgb(cs.backgroundColor);
    if (!fg || !bg) continue;
    const ratio = contrastRatio(fg, bg);
    if (ratio < 4.5) findings.low_contrast.push({ selector: cssPath(el), ratio: ratio.toFixed(2) });
  }

  // small-font scan
  for (const el of textEls) {
    if (!el.textContent.trim()) continue;
    const px = parseFloat(getComputedStyle(el).fontSize);
    if (px < 14) findings.small_font.push({ selector: cssPath(el), px });
  }

  // h-overflow
  findings.h_overflow = document.documentElement.scrollWidth > window.innerWidth;

  function cssPath(el) {
    if (!el) return '';
    if (el.id) return '#' + el.id;
    const path = [];
    while (el && el.nodeType === Node.ELEMENT_NODE && path.length < 4) {
      let s = el.tagName.toLowerCase();
      if (el.className && typeof el.className === 'string') s += '.' + el.className.trim().split(/\\s+/).slice(0, 2).join('.');
      path.unshift(s);
      el = el.parentElement;
    }
    return path.join(' > ');
  }

  return findings;
})()
`;
