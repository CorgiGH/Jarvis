#!/usr/bin/env node
/**
 * tools/fetch-alk.mjs — Alk interpreter pinned-release fetch script (R-6-Q2)
 *
 * STATUS: BLOCKED — see tools/alk/README.md for PM decisions required.
 *
 * Two blockers prevent completing this script:
 * 1. License UNCLEAR (no LICENSE file in alk-language/java-semantics repo) — HALT per plan Step 6.
 * 2. CLI mismatch: plan §0.9-C assumes `java -jar alk.jar main.alk` but the real CLI requires
 *    a classpath launch with Z3 native libraries.
 *
 * Once PM resolves both blockers, this script should:
 * - Download https://github.com/alk-language/java-semantics/releases/download/v4.3/alki-v4.3.zip
 * - Verify sha256 against the pinned value below
 * - Extract v4.3/bin/ to tools/alk/ (gitignored on the preferred path)
 * - Probe `java -Djava.library.path=tools/alk/lib -cp tools/alk/alk.jar:tools/alk/lib/com.microsoft.z3.jar main.ExecutionDriver -a <probe.alk>`
 * - Confirm output matches expected value
 *
 * Pinned release information (verified 2026-06-12):
 */

const RELEASE = {
  version: "v4.3",
  url: "https://github.com/alk-language/java-semantics/releases/download/v4.3/alki-v4.3.zip",
  zipSize: 76211126,
  // sha256 NOT yet recorded — requires PM license clearance before download+commit
  sha256: "PENDING_PM_LICENSE_CLEARANCE",
  mainJar: "v4.3/bin/alk.jar",
  z3Jar: "v4.3/bin/lib/com.microsoft.z3.jar",
  cli: {
    windows: "java -Djava.library.path=tools/alk/lib -cp tools/alk/alk.jar;tools/alk/lib/com.microsoft.z3.jar main.ExecutionDriver -a <file>.alk",
    linux:   "java -Djava.library.path=tools/alk/lib -cp tools/alk/alk.jar:tools/alk/lib/com.microsoft.z3.jar main.ExecutionDriver -a <file>.alk",
  },
};

console.error("BLOCKED: tools/alk/README.md lists two PM decisions required before this script can run.");
console.error("1. License clearance for alk-language/java-semantics (currently: no LICENSE file)");
console.error("2. Plan §0.9-C CLI amendment: real CLI is classpath-based, not java -jar");
process.exit(1);
