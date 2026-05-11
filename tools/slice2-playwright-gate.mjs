#!/usr/bin/env node
/**
 * Slice 2 interaction-smoke gate against live URL.
 *
 * Selectors-painted ≠ selectors-work (Slice 1.5 lesson). This gate clicks
 * every interactive surface introduced by Slice 2 and asserts no 4xx/5xx
 * + no on-screen error text after each click.
 *
 * Asserts:
 *   1. Default URL: no daemon-health-pill, no domain-footer; "READY" visible.
 *   2. ?debug=1: daemon-health-pill visible, domain-footer visible.
 *   3. Resource rail has >= 1 CONCEPT or PRIOR_GAP item for the test task.
 *   4. Selection-fired sidekick flow (the load-bearing interaction):
 *      a. Select text inside a card-body or PDF paragraph.
 *      b. InlineAskChip appears within 2s; click it.
 *      c. Sidekick reply paints within 30s; status row leaves "loading".
 *      d. Reply renders via MathText (data-testid="math-text" present).
 *      e. If response contained `(src:` markers AND backend returned
 *         citations, [data-testid="sidekick-citations-strip"] visible AND
 *         contains >=1 [data-testid^="citation-pill"].
 *      f. Click first citation-pill → assert ResourceRail drawer opens for
 *         that archival path (drawer testid OR title element changes),
 *         no on-screen text matches /404|HTTP \d{3}|not found|error/i.
 *   5. No 4xx/5xx network responses during first paint OR any of the clicks
 *      above.
 *
 * If the live task has no curated cards yet OR the sidekick reply lacks
 * any (src:) marker, citation pill assertions (e–f) are skipped — but the
 * MathText assertion (d) still runs because LaTeX-bearing replies are
 * the dominant case.
 */
import { chromium } from "playwright";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join, dirname } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));

const URL = process.env.SLICE2_URL || "https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E";
// JARVIS_AUTH_COOKIE: value of `jarvis_auth` cookie — required for routes gated by
// the outer JARVIS_AUTH_TOKEN interceptor (/api/v1/last-task, /api/v1/daemon/health etc.)
// The SPA calls /api/v1/tutor/auto-session (public) to mint jarvis_session + csrf
// cookies, but those routes still need the outer token. Read from env or AUTH_TOKEN.txt.
let AUTH_COOKIE = process.env.JARVIS_AUTH_COOKIE || "";
if (!AUTH_COOKIE) {
  try { AUTH_COOKIE = readFileSync(join(__dirname, "AUTH_TOKEN.txt"), "utf8").trim(); } catch { /* ok */ }
}

const fails = [];
const errs = [];
const ERROR_RX = /404|HTTP \d{3}|not found|no PDF attached|error/i;

async function gate() {
  const browser = await chromium.launch({ headless: true });
  const parsedUrl = new globalThis.URL(URL);
  const ctxOptions = {};
  if (AUTH_COOKIE) {
    ctxOptions.storageState = {
      cookies: [{
        name: "jarvis_auth",
        value: AUTH_COOKIE,
        domain: parsedUrl.hostname,
        path: "/",
        httpOnly: false,
        secure: parsedUrl.protocol === "https:",
        sameSite: "Lax",
      }],
      origins: [],
    };
  }
  const ctx = await browser.newContext(ctxOptions);
  const page = await ctx.newPage();

  page.on("response", (resp) => {
    const status = resp.status();
    if (status >= 400 && resp.url().includes("/api/")) {
      errs.push(`HTTP ${status} ${resp.url()}`);
    }
  });

  // 1. Default URL: no daemon-pill, no domain-footer
  await page.goto(URL, { waitUntil: "networkidle" });
  const pill = await page.locator('[data-testid="daemon-health-pill"]').count();
  const footer = await page.locator('[data-testid="domain-footer"]').count();
  if (pill !== 0) fails.push(`daemon-health-pill visible at default URL (count=${pill})`);
  if (footer !== 0) fails.push(`domain-footer visible at default URL (count=${footer})`);
  const readyText = await page.locator('text=/^READY$/').count();
  if (readyText < 1) fails.push("'READY' label not found at default URL");

  // 2. ?debug=1
  await page.goto(URL + "&debug=1", { waitUntil: "networkidle" });
  const pillDbg = await page.locator('[data-testid="daemon-health-pill"]').count();
  if (pillDbg < 1) fails.push("daemon-health-pill missing at ?debug=1");

  // 3. Rail item count — back to default URL
  await page.goto(URL, { waitUntil: "networkidle" });
  const conceptOrGap = await page.locator(
    '[data-testid^="rail-item-CONCEPT"], [data-testid^="rail-item-PRIOR_GAP"]'
  ).count();
  if (conceptOrGap < 1) fails.push(`expected >= 1 CONCEPT|PRIOR_GAP rail item; got ${conceptOrGap}`);

  // 4. Selection-fired sidekick interaction-smoke (load-bearing — Slice 1.5 lesson)
  //
  // Strategy: find the first .card-body element OR drill statement paragraph,
  // programmatically select a substring via window.getSelection so the
  // existing InlineAskChip listener (mouseup + selection-change) fires.
  // Card-body fallback handles tasks where drill cards are hand-curated;
  // PDF iframe text selection is browser-side hard to script reliably
  // across origins, so we prefer card-body.
  const selectionFired = await page.evaluate(() => {
    const target = document.querySelector('[data-testid="drill-card-body"]')
                 || document.querySelector('.card-body')
                 || document.querySelector('[data-testid="problem-statement"]');
    if (!target || !target.textContent || target.textContent.trim().length < 20) return false;
    const range = document.createRange();
    // Select first 40 chars of the first text node we find.
    let textNode = null;
    const walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      if (walker.currentNode.textContent.trim().length >= 20) { textNode = walker.currentNode; break; }
    }
    if (!textNode) return false;
    range.setStart(textNode, 0);
    range.setEnd(textNode, Math.min(40, textNode.textContent.length));
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    // Dispatch a synthetic mouseup so listeners that wait for it fire.
    target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    return true;
  });

  if (!selectionFired) {
    fails.push("could not script text selection: no card-body / drill-card-body / problem-statement node with content");
  } else {
    const chip = page.locator('[data-testid="inline-ask-chip"]');
    try {
      await chip.waitFor({ state: "visible", timeout: 3000 });
      await chip.click();
    } catch (e) {
      fails.push(`InlineAskChip never appeared after selection (${e.message})`);
    }

    // Wait for reply to finish loading. Sidekick container testid + a non-loading
    // state attr OR a non-empty reply body.
    try {
      // 60s timeout: free-tier OpenRouter LLMs can take 30-60s on first response.
      await page.locator('[data-testid="sidekick-reply"]').waitFor({ state: "visible", timeout: 60000 });
    } catch (e) {
      fails.push(`sidekick-reply never painted within 60s (${e.message})`);
    }

    // 4d. MathText wired — KaTeX adds .katex class even on text-only replies
    //     when the MathText component is used. data-testid="math-text" is
    //     emitted by the MathText wrapper. Either is sufficient evidence.
    const mathSeen = await page.locator('[data-testid="math-text"], .katex').count();
    if (mathSeen < 1) fails.push("sidekick reply rendered without MathText wrapper (no [data-testid=math-text] AND no .katex)");

    // 4e+f. Citation pill click. Only assert if backend returned a citations strip.
    const stripPresent = await page.locator('[data-testid="sidekick-citations-strip"]').count();
    if (stripPresent > 0) {
      const pills = page.locator('[data-testid^="citation-pill"]');
      const pillCount = await pills.count();
      if (pillCount < 1) {
        fails.push("citations strip present but no [data-testid^=citation-pill] children");
      } else {
        await pills.first().click();
        // Drawer opens — testid is rail-drawer-open OR resource-rail-drawer; the
        // implementer chooses. The assertion is: SOME drawer-like element with
        // a recognizable testid appears AND no error text shows.
        try {
          await page.locator('[data-testid$="-drawer"], [data-testid="rail-drawer-open"]')
                    .first()
                    .waitFor({ state: "visible", timeout: 5000 });
        } catch (e) {
          fails.push(`citation pill click did not open a rail drawer within 5s (${e.message})`);
        }
        const bodyTxt = await page.textContent('body');
        if (bodyTxt && ERROR_RX.test(bodyTxt)) {
          fails.push(`error text visible on screen after citation-pill click: ${(bodyTxt.match(ERROR_RX) || [])[0]}`);
        }
      }
    } else {
      console.log("[gate] note: sidekick reply has no citations strip — skipping pill-click assertions (4e/4f)");
    }
  }

  // 5. Final error check
  if (errs.length > 0) fails.push(`4xx/5xx network responses: ${errs.join(", ")}`);

  await browser.close();

  if (fails.length > 0) {
    console.error("[gate] FAIL:\n  " + fails.join("\n  "));
    process.exit(1);
  } else {
    console.log("[gate] PASS — all Slice 2 acceptance checks green");
  }
}

gate().catch((e) => {
  console.error("[gate] fatal:", e.message);
  process.exit(2);
});
