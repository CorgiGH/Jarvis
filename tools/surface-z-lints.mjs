// tools/surface-z-lints.mjs
export function detectSnakeCase(text) {
  return [...text.matchAll(/\b[a-z]+_[a-z_]+\b/g)].map(m => m[0]);
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
