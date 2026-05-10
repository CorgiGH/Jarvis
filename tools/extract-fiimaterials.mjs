#!/usr/bin/env node
// fiimaterials.web.app crawler. Re-runnable via sha256 dedup.
//
// Usage:
//   node extract-fiimaterials.mjs              # download new PDFs
//   node extract-fiimaterials.mjs --dry-run    # list URLs only
//   node extract-fiimaterials.mjs --root <p>   # override dest dir
//
// Crawl strategy: depth-1 BFS from start URL; collect <a href*=".pdf">
// links; classify by URL keyword; download + sha256 + write under
// root/<SUBJECT>/extracted/<sha8>/<filename>.pdf with sidecar meta.json.
//
// Conservative: filters non-fiimaterials hosts; access-controlled pages
// (login walls, 401/403) are logged + skipped. Re-runs are idempotent
// (sha256 dedup against existing meta.json sidecars).
//
// Post-crawl on VPS: rebuild knowledge graph so new PDFs are indexed:
//   ssh root@46.247.109.91 "set -a; source /opt/jarvis/.env; set +a; \
//     /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin ingest-corpus"

import { createHash } from "node:crypto";
import { mkdir, writeFile, readdir, readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";

const START_URL = "https://fiimaterials.web.app/";
const HOST_FILTER = /(^|\.)fiimaterials\.web\.app$/i;
const MAX_PAGES = 200;
const FETCH_TIMEOUT_MS = 30_000;

const args = process.argv.slice(2);
const DRY_RUN = args.includes("--dry-run");
const rootIdx = args.indexOf("--root");
const ROOT = rootIdx >= 0 ? args[rootIdx + 1]
  : (process.env.JARVIS_ARCHIVAL_ROOT ?? "./archival/_extras");

export function classifySubject(url) {
  const u = url.toLowerCase();
  if (/\bpa\b|programare-?avansata|study-?guide/.test(u)) return "PA";
  if (/\bps\b|probabilitati|statistica/.test(u)) return "PS";
  if (/\bpoo\b|programare-?orientata/.test(u)) return "POO";
  if (/\balo\b|algoritmica|optimizare/.test(u)) return "ALO";
  if (/\bso\b|sisteme-?de-?operare|linux/.test(u)) return "SO";
  if (/\brc\b|retele|networking/.test(u)) return "RC";
  return "OTHER";
}

export function classifyKind(url) {
  const u = url.toLowerCase();
  if (/curs|lecture|c\d/.test(u)) return "course";
  if (/seminar|sem\d/.test(u)) return "seminar";
  if (/lab|laborator/.test(u)) return "lab";
  if (/tema|hw|homework/.test(u)) return "hw";
  if (/exam|partial|final|colocviu/.test(u)) return "exam";
  return "other";
}

export function sha256Hex(buf) {
  return createHash("sha256").update(buf).digest("hex");
}

export async function existingHashes(root) {
  const out = new Set();
  if (!existsSync(root)) return out;
  async function walk(dir) {
    let entries;
    try { entries = await readdir(dir, { withFileTypes: true }); }
    catch (_) { return; }
    for (const e of entries) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) await walk(p);
      else if (e.name === "meta.json") {
        try {
          const txt = await readFile(p, "utf8");
          const j = JSON.parse(txt);
          if (j.sha256) out.add(j.sha256);
        } catch (_) {}
      }
    }
  }
  await walk(root);
  return out;
}

async function discoverPdfLinks(page, startUrl) {
  const seen = new Set([startUrl]);
  const queue = [startUrl];
  const pdfs = new Set();
  let visited = 0;
  while (queue.length > 0 && visited < MAX_PAGES) {
    const url = queue.shift();
    try {
      await page.goto(url, { timeout: FETCH_TIMEOUT_MS, waitUntil: "networkidle" });
    } catch (e) {
      console.error(`[crawl] skip (${url}): ${e.message}`);
      continue;
    }
    visited++;
    // SPA sites (Angular/React) render anchors after hydration; wait for at
    // least one <a> to exist or timeout briefly. networkidle above usually
    // covers this, but add a short fallback wait.
    try {
      await page.waitForSelector("a[href]", { timeout: 5000 });
    } catch (_) { /* page may legitimately have no links */ }
    const hrefs = await page.$$eval("a[href]", els => els.map(a => a.getAttribute("href")));
    for (const h of hrefs) {
      if (!h) continue;
      let abs;
      try { abs = new URL(h, url).toString(); } catch (_) { continue; }
      if (!abs.startsWith("http")) continue;
      const u = new URL(abs);
      // Collect .pdf links regardless of host (most fiimaterials content
      // links to off-domain hosts: drive.google.com, github.com, etc).
      if (abs.toLowerCase().endsWith(".pdf")) {
        pdfs.add(abs);
        continue;
      }
      // Only follow same-host pages for BFS — don't crawl Drive/GitHub
      // recursively (they need different auth + scraping logic).
      if (!HOST_FILTER.test(u.host)) continue;
      if (!seen.has(abs)) {
        seen.add(abs);
        queue.push(abs);
      }
    }
  }
  return [...pdfs];
}

async function run() {
  // playwright is dynamically imported so the test file (which only uses
  // pure-function exports) doesn't need playwright installed to load this
  // module. Tests run headlessly via `node --test` without browser deps.
  const { chromium } = await import("playwright");
  const browser = await chromium.launch();
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const before = await existingHashes(ROOT);
  console.log(`[crawl] existing hashes: ${before.size}`);
  console.log(`[crawl] dry-run: ${DRY_RUN}`);
  console.log(`[crawl] root: ${ROOT}`);

  let pdfUrls;
  try {
    pdfUrls = await discoverPdfLinks(page, START_URL);
  } catch (e) {
    console.error(`[crawl] discovery failed: ${e.message}`);
    await browser.close();
    process.exit(1);
  }
  console.log(`[crawl] discovered ${pdfUrls.length} PDF link(s)`);

  let fetched = 0, skipped = 0, errored = 0;
  for (const url of pdfUrls) {
    if (DRY_RUN) {
      const subject = classifySubject(url);
      const kind = classifyKind(url);
      console.log(`[dry] ${subject}/${kind}  ${url}`);
      continue;
    }
    try {
      const resp = await ctx.request.get(url, { timeout: FETCH_TIMEOUT_MS });
      if (!resp.ok()) throw new Error(`HTTP ${resp.status()}`);
      const body = await resp.body();
      const sha = sha256Hex(body);
      if (before.has(sha)) {
        skipped++;
        continue;
      }
      const subject = classifySubject(url);
      const kind = classifyKind(url);
      const filename = decodeURIComponent(url.split("/").pop() || "untitled.pdf").slice(-128);
      const sha8 = sha.slice(0, 8);
      const dir = path.join(ROOT, subject, "extracted", sha8);
      await mkdir(dir, { recursive: true });
      await writeFile(path.join(dir, filename), body);
      await writeFile(path.join(dir, "meta.json"), JSON.stringify({
        sourceUrl: url, fetchedAt: new Date().toISOString(),
        sha256: sha, subject, kind,
      }, null, 2));
      console.log(`[fetch] ${subject}/${kind}  ${filename}  (${(body.length / 1024).toFixed(1)} KB)`);
      fetched++;
    } catch (e) {
      console.error(`[error] ${url}: ${e.message}`);
      errored++;
    }
  }

  await browser.close();
  console.log(`[crawl] done. fetched=${fetched} skipped=${skipped} errored=${errored} dry=${DRY_RUN}`);
}

// Cross-platform isMain check. On Windows, import.meta.url is
// "file:///C:/path/to/file.mjs" (3 slashes); process.argv[1] is the OS
// path. Compare via fileURLToPath so we don't fight slash counts.
import { fileURLToPath } from "node:url";
const isMain = (() => {
  try {
    return path.resolve(fileURLToPath(import.meta.url)) === path.resolve(process.argv[1] ?? "");
  } catch (_) { return false; }
})();
if (isMain) {
  run().catch(e => {
    console.error(`[crawl] fatal: ${e.message}`);
    process.exit(1);
  });
}
