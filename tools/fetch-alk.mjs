#!/usr/bin/env node
/**
 * tools/fetch-alk.mjs — Alk interpreter pinned-release fetch (R-6-Q2)
 *
 * PM RULING 2026-06-12: license = none published (null), no LICENSE file — fetch-only path,
 * no redistribution (jar/zip NEVER committed). tools/alk/README.md records the status.
 * CLI = verified classpath/wrapper form per §0.9-C (PM-amended).
 *
 * PM RULING 2026-06-13 (amended): wrapper scripts are STATIC TRACKED files (alki.cmd / alki.sh)
 * — portable, relative-path form. This script does NOT emit them.
 *
 * What this script does:
 *   1. Downloads alki-v4.3.zip from the pinned GitHub release URL.
 *   2. Verifies sha256 against the pinned value.
 *   3. Extracts v4.3/bin/ to tools/alk/v4.3/bin/ (git-ignored).
 *   4. Runs a 3-line Alk probe program to confirm the interpreter works.
 *
 * Run from the repo root:
 *   node tools/fetch-alk.mjs
 *
 * After running, LanguageRunners.kt calls tools/alk/alki.cmd or tools/alk/alki.sh.
 * The wrapper encapsulates the full classpath invocation — callers never need the raw form.
 */

import { createHash } from "node:crypto";
import { createWriteStream, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { exec } from "node:child_process";
import { promisify } from "node:util";

const execAsync = promisify(exec);
const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "..");

// ── pinned release (verified 2026-06-12) ─────────────────────────────────────

const RELEASE = {
  version: "v4.3",
  url: "https://github.com/alk-language/java-semantics/releases/download/v4.3/alki-v4.3.zip",
  // sha256 of alki-v4.3.zip — computed on first download 2026-06-12
  sha256: "1c6fe0778111faf4747a445342872eb69a1f17f098c1eefc94d7db39b66820da",
  zipName: "alki-v4.3.zip",
};

// tools/alk/v4.3/bin/ is the bin directory after extraction (zip extracts a top-level v4.3/ dir)
const ALK_DIR   = join(REPO_ROOT, "tools", "alk");
const ZIP_PATH  = join(ALK_DIR, RELEASE.zipName);
// Zip contains v4.3/bin/alk.jar — extract the zip directly to tools/alk/ → tools/alk/v4.3/bin/
const BIN_DIR   = join(ALK_DIR, "v4.3", "bin");

// ── helpers ──────────────────────────────────────────────────────────────────

async function fetchWithProgress(url, destPath) {
  console.log(`Downloading ${url} ...`);
  const res = await fetch(url, { redirect: "follow" });
  if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
  const total = parseInt(res.headers.get("content-length") || "0");
  let received = 0;
  const chunks = [];
  const reader = res.body.getReader();
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    received += value.length;
    if (total > 0 && received % (1024 * 1024) < 8192) {
      const pct = Math.round((received / total) * 100);
      console.log(`  ${Math.round(received / 1024 / 1024)} MB / ${Math.round(total / 1024 / 1024)} MB (${pct}%)`);
    }
  }
  const ws = createWriteStream(destPath);
  for (const chunk of chunks) ws.write(chunk);
  await new Promise((ok, fail) => { ws.on("finish", ok); ws.on("error", fail); ws.end(); });
  console.log(`  Saved: ${destPath} (${received.toLocaleString()} bytes)`);
}

function sha256File(filePath) {
  const hash = createHash("sha256");
  hash.update(readFileSync(filePath));
  return hash.digest("hex");
}

async function extractZip(zipPath, destDir) {
  console.log(`Extracting ${zipPath} → ${destDir} ...`);
  const isWindows = process.platform === "win32";
  if (isWindows) {
    await execAsync(
      `powershell -Command "Expand-Archive -Path '${zipPath.replace(/'/g, "''")}' -DestinationPath '${destDir.replace(/'/g, "''")}' -Force"`,
      { timeout: 120000 }
    );
  } else {
    await execAsync(`unzip -o "${zipPath}" -d "${destDir}"`, { timeout: 120000 });
  }
  console.log("  Extraction complete.");
}


async function runProbe(binDir, alkDir) {
  // 3-line Alk probe: gcd(12, 8) = 4
  // Alk syntax: = for assignment, print() for output — verified 2026-06-12.
  const probeFile = join(alkDir, "_probe.alk");
  writeFileSync(probeFile,
    "a = 12; b = 8;\n" +
    "while (b != 0) { t = a % b; a = b; b = t; }\n" +
    "print(a);\n",
    "utf8"
  );

  const isWindows = process.platform === "win32";
  const wrapper   = join(alkDir, isWindows ? "alki.cmd" : "alki.sh");
  const cmd       = isWindows
    ? `"${wrapper}" "${probeFile}"`
    : `sh "${wrapper}" "${probeFile}"`;

  try {
    const { stdout, stderr } = await execAsync(cmd, { timeout: 15000, shell: true });
    const output = (stdout + stderr).trim();
    console.log(`  Probe output: "${output}"`);
    if (!output.includes("4")) {
      throw new Error(`Expected "4" in probe output, got: ${output}`);
    }
    console.log("  Probe PASSED — gcd(12,8) = 4 confirmed.");
    return output;
  } catch (err) {
    // Fallback: try direct java invocation (useful if wrapper PATH resolution differs)
    const sep = isWindows ? ";" : ":";
    const sep2 = isWindows ? "\\" : "/";
    const directCmd = `java "-Djava.library.path=${binDir}${sep2}lib" -cp "${binDir}${sep2}alk.jar${sep}${binDir}${sep2}lib${sep2}com.microsoft.z3.jar" main.ExecutionDriver -a "${probeFile}"`;
    try {
      const { stdout, stderr } = await execAsync(directCmd, { timeout: 15000, shell: true });
      const output = (stdout + stderr).trim();
      console.log(`  Direct probe output: "${output}"`);
      if (!output.includes("4")) {
        throw new Error(`Expected "4" in direct probe output, got: ${output}`);
      }
      console.log("  Probe PASSED (via direct invocation) — gcd(12,8) = 4 confirmed.");
      return output;
    } catch (err2) {
      throw new Error(`Probe FAILED.\nWrapper error: ${err.message}\nDirect error: ${err2.message}`);
    }
  }
}

// ── main ─────────────────────────────────────────────────────────────────────

async function main() {
  console.log("=== fetch-alk.mjs (R-6-Q2, PM ruling 2026-06-12) ===");
  console.log(`Alk version : ${RELEASE.version}`);
  console.log(`Target dir  : ${ALK_DIR}`);
  console.log(`Expected bin: ${BIN_DIR}`);

  // 1. Java check
  try {
    const { stderr } = await execAsync("java -version", { timeout: 10000 });
    console.log(`Java        : ${stderr.split("\n")[0].trim()}`);
  } catch {
    throw new Error("java is not on PATH — required to run Alk.");
  }

  // 2. Create dirs
  mkdirSync(ALK_DIR, { recursive: true });

  // 3. Download if not already present
  if (!existsSync(ZIP_PATH)) {
    await fetchWithProgress(RELEASE.url, ZIP_PATH);
  } else {
    console.log(`ZIP cached  : ${ZIP_PATH}`);
  }

  // 4. Verify sha256
  const actualSha256 = sha256File(ZIP_PATH);
  console.log(`SHA-256     : ${actualSha256}`);
  if (actualSha256 !== RELEASE.sha256) {
    throw new Error(
      `SHA-256 MISMATCH!\n  Expected: ${RELEASE.sha256}\n  Actual:   ${actualSha256}\n` +
      `Delete ${ZIP_PATH} and re-run, or update RELEASE.sha256 if the release was re-published.`
    );
  }
  console.log("SHA-256     : OK");

  // 5. Extract if not already done
  const alkJar = join(BIN_DIR, "alk.jar");
  if (!existsSync(alkJar)) {
    // Extract zip directly into ALK_DIR → produces ALK_DIR/v4.3/bin/alk.jar
    await extractZip(ZIP_PATH, ALK_DIR);
    if (!existsSync(alkJar)) {
      throw new Error(`Expected ${alkJar} after extraction — zip structure may have changed.`);
    }
  } else {
    console.log(`Already extracted: ${alkJar}`);
  }

  // 6. Run probe
  await runProbe(BIN_DIR, ALK_DIR);

  console.log("\n=== fetch-alk.mjs COMPLETE ===");
  console.log(`Alk ${RELEASE.version} ready at ${BIN_DIR}`);
  console.log(`Wrappers are static tracked files: tools/alk/alki.cmd / tools/alk/alki.sh`);
  console.log(`SHA-256 verified: ${RELEASE.sha256}`);
}

main().catch(err => {
  console.error("\nFATAL:", err.message || err);
  process.exit(1);
});
