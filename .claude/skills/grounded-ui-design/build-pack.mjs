#!/usr/bin/env node
/**
 * Grounded-UI deterministic GROUNDING PACK builder (Stage ① — ZERO LLM).
 * Reads the real repo and emits a frozen, git-SHA-stamped pack.json so every
 * downstream design agent cites repo reality, not its own recall.
 *
 * Usage:  node .claude/skills/grounded-ui-design/build-pack.mjs <surface> [--out <dir>]
 * Run from the jarvis-kotlin repo root.
 *
 * The agent ANNOTATES this manifest; it never authors it from memory.
 * (Council wf_e06c3501-271: agent-assembled grounding is already wrong —
 *  24 viz .tsx on disk vs 1 registered.)
 */
import { execSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync } from "node:fs";
import { join, basename, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const surface = process.argv[2] || "unspecified-surface";
const outIdx = process.argv.indexOf("--out");
const sh = (c) => execSync(c, { encoding: "utf8" }).trim();
const tryRead = (p) => (existsSync(p) ? readFileSync(p, "utf8") : "");

const gitSha = (() => { try { return sh("git rev-parse --short HEAD"); } catch { return "nogit"; } })();

// --- Primitives: components + their export/interface signatures ---
let componentFiles = [];
try {
  componentFiles = sh("git ls-files tutor-web/src/components")
    .split("\n").filter(f => /\.tsx?$/.test(f) && !/\.test\./.test(f));
} catch {}
const primitives = componentFiles.map(f => {
  const src = tryRead(f);
  const exports = [...src.matchAll(/export function (\w+)/g)].map(m => m[1]);
  const ifaces = [...src.matchAll(/(?:export )?interface (\w+Props)\s*\{([^}]*)\}/g)]
    .map(m => ({ name: m[1], props: m[2].split("\n").map(s => s.trim()).filter(s => s && !s.startsWith("/")) }));
  return { file: f, name: basename(f).replace(/\.tsx?$/, ""), exports, propInterfaces: ifaces };
});

// --- Design tokens: parse index.css :root ---
const css = tryRead("tutor-web/src/index.css");
const tokenDecls = [...css.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map(m => ({ name: m[1].trim(), value: m[2].trim() }));
const tokenNames = [...new Set(tokenDecls.map(t => t.name))];

// --- Viz inventory reconciled across 3 sources (divergence is a FACT) ---
const vizDir = "tutor-web/src/components/viz";
const vizOnDisk = existsSync(vizDir)
  ? readdirSync(vizDir).filter(f => /\.tsx$/.test(f) && !/\.test\./.test(f)).map(f => f.replace(/\.tsx$/, ""))
  : [];
const registrySrc = tryRead("tutor-web/src/components/viz/vizRegistry.ts");
const registryKeys = [...registrySrc.matchAll(/["'`]([\w-]+)["'`]\s*:/g)].map(m => m[1]);
const yamlSrc = tryRead("content/viz-ids.yaml");
const yamlIds = [...yamlSrc.matchAll(/^\s*-\s*([\w-]+)/gm)].map(m => m[1]);
const usableViz = registryKeys.filter(k => yamlIds.includes(k));
const builtButUnreachable = vizOnDisk.filter(v => !registryKeys.includes(v));
const vizSelfCheck = {
  registryKeyCount: registryKeys.length,
  yamlIdCount: yamlIds.length,
  onDiskCount: vizOnDisk.length,
  parityOk: registryKeys.length === yamlIds.length,
  note: registryKeys.length !== yamlIds.length || vizOnDisk.length !== registryKeys.length
    ? "DIVERGENCE — most viz are BUILT-BUT-UNREACHABLE; proposers may request wiring, must not assume render"
    : "parity holds",
};

// --- Prohibited denylist (checked-in, reflects CURRENT direction) ---
const prohibitedRaw = tryRead(join(HERE, "prohibited-ui.txt"));
const prohibited = prohibitedRaw.split("\n").map(s => s.trim()).filter(s => s && !s.startsWith("#"));

// --- Learner profile (baked in) ---
const profilePath = join(process.env.USERPROFILE || process.env.HOME || "", ".claude/projects/C--Users-User-jarvis-kotlin/memory/alex-learner-profile.md");
const learnerProfile = tryRead(profilePath).slice(0, 1600) ||
  "ADHD (one primary action visible, minimize cognitive load); visual learner (viz foregrounded); predict-then-reveal; low programming confidence.";

const pack = {
  surface, gitSha, builtAt: "STAMP_AT_RUNTIME",
  primitives, tokenNames, tokenDecls,
  viz: { usable: usableViz, builtButUnreachable, onDisk: vizOnDisk, registryKeys, yamlIds, selfCheck: vizSelfCheck },
  prohibited,
  learnerProfile,
  rules: [
    "Cite this pack by gitSha. Do NOT regenerate from memory.",
    "Every color/space value MUST resolve to a token in tokenNames — raw hex = FAIL.",
    "Every reused primitive MUST name the data/URL it is fed (correct-component/wrong-prop is a real failure class).",
    "A USABLE primitive must appear USED or REJECTED-with-reason; unaccounted = auto-FAIL.",
    "viz NOT in viz.usable must not be assumed to render (request wiring explicitly).",
    "No token/string matching the prohibited denylist may appear in a proposal.",
  ],
  omittedOnPurpose: "FILL IN: list every primitive/token intentionally not used this run, so omission is a logged decision (ghost-component lesson).",
};

const outDir = outIdx > 0 ? process.argv[outIdx + 1]
  : join(".superpowers", "ui-runs", `${surface.replace(/\W+/g, "-")}-${gitSha}`);
mkdirSync(outDir, { recursive: true });
const outFile = join(outDir, "pack.json");
writeFileSync(outFile, JSON.stringify(pack, null, 2));

console.log(JSON.stringify({
  type: "pack-built", outFile, gitSha,
  primitives: primitives.length, tokens: tokenNames.length,
  vizUsable: usableViz.length, vizBuiltButUnreachable: builtButUnreachable.length,
  vizParityOk: vizSelfCheck.parityOk, prohibitedTerms: prohibited.length,
}, null, 2));
if (!vizSelfCheck.parityOk) console.error("WARN viz parity mismatch:", JSON.stringify(vizSelfCheck));
