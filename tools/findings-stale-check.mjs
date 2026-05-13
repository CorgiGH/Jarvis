import { readFileSync } from "node:fs";
import { execSync } from "node:child_process";

export async function checkStaleness(filepath, opts = {}) {
  const text = readFileSync(filepath, "utf8");
  const match = text.match(/^---\n([\s\S]+?)\n---/);
  if (!match) throw new Error(`No frontmatter in ${filepath}`);
  const fm = match[1];
  const parseField = (name) => fm.match(new RegExp(`^\\s+${name}:\\s+(.+)$`, "m"))?.[1]?.trim().replace(/^["']|["']$/g, "") ?? null;
  const stamp = {
    git_head: parseField("git_head"),
    bundle_hash: parseField("bundle_hash"),
    live_dom_fingerprint: parseField("live_dom_fingerprint"),
    ts_utc: parseField("ts_utc"),
    surface_version: parseField("surface_version"),
    judge_model_resolved: parseField("judge_model_resolved"),
    judge_prompt_sha256: parseField("judge_prompt_sha256"),
  };

  const currentGit = (() => {
    try { return execSync("git rev-parse --short HEAD", { stdio: ["pipe", "pipe", "ignore"] }).toString().trim(); }
    catch { return null; }
  })();
  const currentBundle = opts.currentBundleHash ?? await fetchBundleHash();

  const fields = {
    git_head: {
      stamped: stamp.git_head,
      current: currentGit,
      status: stamp.git_head === currentGit ? "OK" : "STALE",
    },
    bundle_hash: {
      stamped: stamp.bundle_hash,
      current: currentBundle,
      status: stamp.bundle_hash === currentBundle ? "OK" : "STALE",
    },
    ts_utc: {
      stamped: stamp.ts_utc,
      ageMs: stamp.ts_utc ? Date.now() - Date.parse(stamp.ts_utc) : null,
      status: stamp.ts_utc ? (Date.now() - Date.parse(stamp.ts_utc) < 86400_000 ? "FRESH" : "AGED") : "MISSING",
    },
    surface_version: {
      stamped: stamp.surface_version,
      current: process.env.SURFACE_VERSION ?? "unknown",
      status: stamp.surface_version === (process.env.SURFACE_VERSION ?? "unknown") ? "OK" : "DRIFT",
    },
    judge_model_resolved: {
      stamped: stamp.judge_model_resolved,
      status: stamp.judge_model_resolved ? "PRESENT" : "MISSING",
    },
    judge_prompt_sha256: {
      stamped: stamp.judge_prompt_sha256,
      status: stamp.judge_prompt_sha256 ? "PRESENT" : "MISSING",
    },
  };

  const anyStale = Object.values(fields).some((f) => f.status === "STALE" || f.status === "DRIFT");
  const overall = anyStale ? "STALE" : "OK";
  return { fields, overall };
}

async function fetchBundleHash() {
  try {
    const txt = await fetch("https://corgflix.duckdns.org/tutor/").then(r => r.text());
    return txt.match(/index-([A-Za-z0-9_-]+)\.js/)?.[1] ?? "unknown";
  } catch { return "unknown"; }
}

// CLI entrypoint
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith("findings-stale-check.mjs")) {
  const fp = process.argv[2];
  if (!fp) { console.error("Usage: findings-stale-check.mjs <path>"); process.exit(2); }
  const r = await checkStaleness(fp);
  for (const [k, v] of Object.entries(r.fields)) {
    console.log(`${k}: ${v.stamped ?? "(missing)"} → ${v.current ?? ""} [${v.status}]`);
  }
  console.log(`Overall: [${r.overall}]`);
  process.exit(r.overall === "STALE" ? 1 : 0);
}
