#!/usr/bin/env node
/**
 * Slice 2.5 chip-flow citation gate (sidekick pre-fetch).
 *
 * Verifies: chip click on a "distributia Laplace" selection produces a
 * sidekick reply with at least one [data-testid="citation-pill"] whose
 * path starts under "_extras/PS/". Then clicks the pill and asserts the
 * concept drawer paints with resource content, without any new 4xx/5xx
 * network response during the flow.
 *
 * Run from repo root:
 *   JARVIS_AUTH_COOKIE=$(cat tools/AUTH_TOKEN.txt) node tools/slice2-prefetch-gate.mjs
 *
 * Env:
 *   JARVIS_AUTH_COOKIE  — value of jarvis_auth cookie for the live VPS.
 *                         Falls back to tools/AUTH_TOKEN.txt.
 *   JARVIS_TUTOR_URL    — defaults to https://corgflix.duckdns.org
 *   SLICE25_TASK_ID     — defaults to 01KR6K07T6PATPRR5KH1JXYF8E (PS Tema A)
 */
import { chromium } from "playwright";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join, dirname } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.JARVIS_TUTOR_URL ?? "https://corgflix.duckdns.org";
const TASK_ID = process.env.SLICE25_TASK_ID ?? "01KR6K07T6PATPRR5KH1JXYF8E";

let AUTH = process.env.JARVIS_AUTH_COOKIE || "";
if (!AUTH) {
    const f = join(__dirname, "AUTH_TOKEN.txt");
    if (existsSync(f)) AUTH = readFileSync(f, "utf8").trim();
}
if (!AUTH) {
    console.error("[gate] FAIL: JARVIS_AUTH_COOKIE not set and tools/AUTH_TOKEN.txt missing");
    process.exit(1);
}

async function main() {
    const parsed = new globalThis.URL(BASE);
    const browser = await chromium.launch({ headless: true });
    const ctx = await browser.newContext({
        storageState: {
            cookies: [{
                name: "jarvis_auth",
                value: AUTH,
                domain: parsed.hostname,
                path: "/",
                httpOnly: false,
                secure: parsed.protocol === "https:",
                sameSite: "Lax",
            }],
            origins: [],
        },
    });
    const page = await ctx.newPage();

    const networkErrors = [];
    page.on("response", (resp) => {
        const s = resp.status();
        if (s >= 400 && s < 600 && resp.url().includes("/api/")) {
            networkErrors.push(`${s} ${resp.url()}`);
        }
    });

    await page.goto(`${BASE}/tutor/?taskId=${TASK_ID}`, { waitUntil: "domcontentloaded" });
    // Wait for workspace to render (card body or InlineAskChip ancestor present).
    await page.waitForSelector('article, [data-testid="card-body"], [data-testid="problem-stepper"]', { timeout: 20000 });

    // Trigger selection on a Laplace-bearing span via DOM evaluation.
    const selected = await page.evaluate(() => {
        const candidates = Array.from(document.querySelectorAll("span, p, li, div"));
        const target = candidates.find((el) => {
            if (el.children.length > 5) return false;
            const t = el.textContent || "";
            return /Laplace/i.test(t) && t.length >= 12 && t.length < 800;
        });
        if (!target) return null;
        const r = document.createRange();
        r.selectNodeContents(target);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(r);
        target.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
        return (target.textContent || "").slice(0, 120);
    });
    if (!selected) throw new Error("no Laplace-bearing selectable element found in workspace");
    console.log(`[gate] selected: ${selected}`);

    await page.waitForSelector('[data-testid="inline-ask-chip"]', { state: "visible", timeout: 8000 });
    await page.click('[data-testid="inline-ask-chip"]');

    // Wait for sidekick reply (LLM round-trip + render).
    await page.waitForSelector('[data-testid="sidekick-reply"], [data-testid="math-text"]', { timeout: 60000 });

    // Wait for citation pills to attach. If pre-fetch worked, at least 1 pill.
    await page.waitForSelector('[data-testid^="citation-pill"]', { timeout: 10000 });
    const pillTitles = await page.$$eval('[data-testid^="citation-pill"]', (els) =>
        els.map((e) => e.getAttribute("title") || e.getAttribute("data-path") || e.textContent || "")
    );
    if (pillTitles.length < 1) throw new Error(`expected ≥1 citation pill, got ${pillTitles.length}`);
    const fiiHit = pillTitles.find((t) => /_extras\/PS\//.test(t));
    if (!fiiHit) throw new Error(`no _extras/PS/ pill found among: ${JSON.stringify(pillTitles)}`);
    console.log(`[gate] pill ok: ${fiiHit}`);

    // Click the matching pill → assert drawer paints with non-error content.
    const pillIndex = pillTitles.findIndex((t) => /_extras\/PS\//.test(t));
    await page.$$eval('[data-testid^="citation-pill"]', (els, i) => els[i].click(), pillIndex);

    // Drawer paint window — try multiple known testids that ResourceRail uses.
    await page.waitForSelector('[data-testid="concept-drawer"], [data-testid="resource-drawer"], [data-testid="resource-rail"]', { timeout: 10000 });
    const drawerText = await page.evaluate(() => {
        const drawer = document.querySelector('[data-testid="concept-drawer"]') ||
            document.querySelector('[data-testid="resource-drawer"]') ||
            document.querySelector('[data-testid="resource-rail"]');
        return drawer?.textContent || "";
    });
    if (/404|HTTP \d{3}|not found|error/i.test(drawerText)) {
        throw new Error(`drawer shows error text: ${drawerText.slice(0, 200)}`);
    }
    console.log(`[gate] drawer ok (${drawerText.length} chars)`);

    if (networkErrors.length > 0) {
        throw new Error(`network 4xx/5xx during flow:\n  ${networkErrors.join("\n  ")}`);
    }

    console.log("[gate] PASS — chip-flow citations + drawer + zero network errors");
    await browser.close();
}

main().catch((e) => {
    console.error("[gate] FAIL:", e.message);
    process.exit(1);
});
