# Alk Interpreter — Task 0 Investigation Report (R-6-Q2)

## Status: HALT — PM decision required

Two blockers found during Task 0 investigation. Do not proceed past this README without PM resolution.

---

## Blocker 1: License Unclear

- GitHub API `repos/alk-language/java-semantics` returns `"license": null`
- No `LICENSE` file exists in the repository root (GitHub API returns 404)
- No license files found inside the `alki-v4.3.zip` release archive
- The bundled `v4.3/README.md` makes no license statement
- The `pom.xml` makes no license declaration

**Plan rule:** "HALT FOR PM if the license is unclear or forbids redistribution/local use — do not fetch-and-ship under an unknown license."

**PM decision needed:** Is Alk safe to fetch and use for this single-user educational app? Options:
1. Contact the authors (Lungu, Lucanu at UAIC — same institution as Alex) and request license clarification.
2. Treat as "educational use permitted" based on context (published in academic papers, publicly distributed as a teaching tool at UAIC) — PM accepts the risk explicitly.
3. Descope Alk from the execution grader (the Alk leg degrades honestly if absent; only PA problems use Alk, and PA problems can fall back to rubric + LLM grading).

---

## Blocker 2: CLI Mismatch vs Plan §0.9-C

**Plan §0.9-C assumes:** `java -jar <alk.jar> main.alk`

**Actual CLI (verified):**
```
java -Djava.library.path="<alkdir>/lib" -cp "<alkdir>/alk.jar;<alkdir>/lib/com.microsoft.z3.jar" main.ExecutionDriver -a <file>.alk
```

The interpreter uses a classpath launch (NOT `java -jar`) and requires:
- The Microsoft Z3 SMT solver jar (`lib/com.microsoft.z3.jar`)
- Native Z3 libraries (`lib/libz3.dll` on Windows, `lib/libz3.so` on Linux, etc.)

This means the execution grader cannot use a single standalone jar. The `tools/alk/` directory must contain both the main jar AND the lib/ subdirectory (or the fetch script must extract the full `bin/` tree from the zip).

**Verified working CLI** (tested on Windows with Java 21):
```
java -Djava.library.path="<alkdir>/lib" -cp "<alkdir>/alk.jar;<alkdir>/lib/com.microsoft.z3.jar" main.ExecutionDriver -a gcd-no-input.alk
```
Output: `4` (gcd(12, 8) — correct)

**Linux CLI** (from alki.sh):
```
java -Djava.library.path="<alkdir>/lib" -cp "<alkdir>/alk.jar:<alkdir>/lib/com.microsoft.z3.jar" main.ExecutionDriver -a <file>.alk
```
(Colon separator on Linux vs semicolon on Windows)

**PM options:**
1. Update §0.9-C to the real CLI (requires deviation amendment to the frozen contract).
2. Create a thin wrapper script `tools/alk/alki` (sh) / `alki.bat` (Windows) that encapsulates the real CLI — the `ExecutionGrader` calls the wrapper, plan text stays `java -jar`-shaped but the actual invocation goes through the wrapper.
3. Accept the real multi-jar classpath approach and update the plan contract accordingly.

---

## Release Artifact

- **Repository:** https://github.com/alk-language/java-semantics
- **Latest stable release:** v4.3 (non-draft, non-prerelease)
- **Release asset:** `alki-v4.3.zip` (76,211,126 bytes)
- **Download URL:** https://github.com/alk-language/java-semantics/releases/download/v4.3/alki-v4.3.zip
- **Contents relevant to execution:**
  - `v4.3/bin/alk.jar` (15,112,646 bytes) — main interpreter jar
  - `v4.3/bin/lib/com.microsoft.z3.jar` (177,135 bytes) — Z3 SMT solver
  - `v4.3/bin/lib/libz3.dll` / `libz3java.dll` — Windows native libs
  - `v4.3/bin/lib/libz3.so` / `libz3java.so` — Linux native libs
  - `v4.3/bin/alki.bat` — Windows launcher script
  - `v4.3/bin/alki.sh` — Linux/Mac launcher script

## Toolchain Probe Results (Task 0 Step 3)

| Tool | Status | Version/Path |
|------|--------|-------------|
| g++ | Present in PATH | MinGW.org GCC 6.3.0-1 |
| python / python3 | Present in PATH | Python 3.13.13 |
| java | Present in PATH | OpenJDK 21.0.10 (Temurin) |
| Rscript | NOT in PATH | Installed at `C:\Program Files\R\R-4.5.3\bin\Rscript.exe` |
| Alk | NOT present | Requires download + license clearance |

**R note:** `Rscript` is installed (v4.5.3) but not on the system PATH. The `ToolchainProbe` in `ExecutionGrader` should use the full path `C:\Program Files\R\R-4.5.3\bin\Rscript.exe` as fallback on Windows, or probe common install locations. INV-6.2's R case CAN be satisfied on PC with the full path. This is NOT a BLOCKED condition (R is present, just needs full-path detection).
