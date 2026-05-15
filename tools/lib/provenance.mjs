import { execSync } from "node:child_process";
import { createHash } from "node:crypto";

export function normalizeDomForFingerprint(html) {
  return html
    .replace(/\b[0-9A-HJKMNP-TV-Z]{26}\b/g, "<ULID>")
    .replace(/\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi, "<UUID>")
    .replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z?/g, "<TS>")
    .replace(/\b\d{13,}\b/g, "<EPOCH>")
    .replace(/data-(event-id|session-id|render-key|nonce)="[^"]*"/g, '<VOL>')
    .replace(/jarvis_auth=[^;"\s]+/g, "jarvis_auth=<COOKIE>")
    .replace(/csrfToken=\\?"[^"\\]*\\?"/g, 'csrfToken="<TOKEN>"');
}

export async function getStamp(page, opts = {}) {
  let git_head = "unknown";
  try {
    git_head = execSync("git rev-parse --short HEAD", { stdio: ["pipe", "pipe", "ignore"] }).toString().trim();
  } catch {}

  let bundle_hash = "unknown";
  try {
    const txt = await fetch("https://corgflix.duckdns.org/tutor/").then(r => r.text());
    bundle_hash = txt.match(/index-([A-Za-z0-9_-]+)\.js/)?.[1] ?? "unknown";
  } catch {}

  const live_dom_fingerprint = page
    ? createHash("sha256")
        .update(normalizeDomForFingerprint(await page.content()))
        .digest("hex")
        .slice(0, 16)
    : null;

  return {
    git_head,
    bundle_hash,
    live_dom_fingerprint,
    ts_utc: new Date().toISOString(),
    surface_version: process.env.SURFACE_VERSION ?? "unknown",
    judge_model_resolved: opts.judge_model_resolved ?? null,
    judge_prompt_sha256: opts.judge_prompt_sha256 ?? null,
    provider_name: opts.provider_name ?? null,
  };
}
