# Alk Interpreter — Fetch Script and Wrapper (R-6-Q2)

## Status: FETCH-ONLY — no redistribution

**PM RULING 2026-06-12:**
- License: `null` (no LICENSE file in `alk-language/java-semantics` repository root or release archive)
- The license is unclear. Committing the jar/zip would constitute redistribution under an unclear
  license — this is **forbidden**.
- **Fetch-only path:** run `node tools/fetch-alk.mjs` to download and verify the pinned release.
- The jar/zip/extracted `v4.3/` tree is git-ignored (see `.gitignore`).
- `LanguageRunners.kt` calls `tools/alk/alki.cmd` (Windows) or `tools/alk/alki.sh` (Linux/macOS).
  Run the fetch script once before building or running the execution grader.

## Release artifact (pinned)

| Field       | Value |
|-------------|-------|
| Repository  | https://github.com/alk-language/java-semantics |
| Release     | v4.3 (non-draft, non-prerelease) |
| Asset       | `alki-v4.3.zip` (76,211,126 bytes) |
| Download URL | https://github.com/alk-language/java-semantics/releases/download/v4.3/alki-v4.3.zip |
| SHA-256     | `1c6fe0778111faf4747a445342872eb69a1f17f098c1eefc94d7db39b66820da` |

## Verified run CLI (§0.9-C amended form, 2026-06-12)

The interpreter uses a **classpath launch** (not `java -jar`). It requires:
- `v4.3/bin/alk.jar` — the main interpreter
- `v4.3/bin/lib/com.microsoft.z3.jar` — the Z3 SMT solver
- `v4.3/bin/lib/libz3.dll` / `libz3java.dll` (Windows native Z3 libs)
- `v4.3/bin/lib/libz3.so` / `libz3java.so` (Linux native Z3 libs)

**Wrapper CLI (what `LanguageRunners.kt` should call):**

Windows:
```
tools\alk\alki.cmd <file.alk>
```

Linux/macOS:
```
sh tools/alk/alki.sh <file.alk>
```

**Raw invocation (for reference — callers use the wrapper):**

Windows (`;` classpath separator):
```
java -Djava.library.path="<bindir>\lib" -cp "<bindir>\alk.jar;<bindir>\lib\com.microsoft.z3.jar" main.ExecutionDriver -a <file.alk>
```

Linux/macOS (`:` classpath separator):
```
java -Djava.library.path="<bindir>/lib" -cp "<bindir>/alk.jar:<bindir>/lib/com.microsoft.z3.jar" main.ExecutionDriver -a <file.alk>
```

**Verified output (2026-06-12):** `gcd(12,8) = 4` — probe program:
```alk
a = 12; b = 8;
while (b != 0) { t = a % b; a = b; b = t; }
print(a);
```

## Alk syntax notes

- Assignment: `=` (not `:=`)
- Output: `print(expr);`
- The `-a <file>` flag is required to specify the algorithm file

## Toolchain probe results (Task 0 Step 3)

| Tool       | Status          | Version/Path |
|------------|-----------------|-------------|
| g++        | Present in PATH | MinGW.org GCC 6.3.0-1 |
| python     | Present in PATH | Python 3.13.13 |
| java       | Present in PATH | OpenJDK 21.0.10 (Temurin) |
| Rscript    | NOT in PATH     | Installed at `C:\Program Files\R\R-4.5.3\bin\Rscript.exe` |
| Alk        | Fetched         | v4.3 (run `node tools/fetch-alk.mjs` to download) |

**R note:** Rscript is installed (v4.5.3) but not on the system PATH. `ToolchainProbe` in
`ExecutionGrader.kt` should probe `C:\Program Files\R\R-*\bin\Rscript.exe` as a Windows
fallback (known default install location per §0.9-C).

## git-ignored files (NEVER commit)

```
tools/alk/v4.3/        ← extracted Alk interpreter (jars + native libs)
tools/alk/*.zip        ← downloaded release archive
tools/alk/_probe*.alk  ← temporary probe files
```

## Tracked files (committed)

```
tools/fetch-alk.mjs    ← download + verify sha256 + extract (does NOT emit wrappers)
tools/alk/README.md    ← this file
tools/alk/alki.cmd     ← Windows wrapper (STATIC TRACKED — portable, %~dp0 relative path)
tools/alk/alki.sh      ← Linux/macOS wrapper (STATIC TRACKED — portable, dirname-relative path)
```

**PM RULING 2026-06-13 (amendment):** the wrapper scripts are static tracked files with portable
relative paths — they do NOT contain absolute machine paths. `fetch-alk.mjs` does NOT emit them.
Everything under `tools/alk/v4.3/` (the extracted interpreter) and `tools/alk/*.zip` are git-ignored.
