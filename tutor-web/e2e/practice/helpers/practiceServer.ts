/**
 * Plan-6 Task 13 — self-contained practice e2e server helper.
 *
 * Boots the real Kotlin backend against a scratch SQLite DB pre-seeded with
 * real-corpus problems (Task 2 seeds) and a known e2e test user+session, via the
 * JARVIS_SEED_PRACTICE=1 flag added to WebMain.kt (Task 13).
 *
 * Usage (from a spec's globalSetup or worker fixture):
 *
 *   const srv = await startPracticeServer();
 *   // srv.baseUrl  — e.g. "http://127.0.0.1:29301"
 *   // srv.sessionToken — raw jarvis_session cookie value
 *   // srv.csrfToken    — csrf cookie value (same value goes into X-CSRF-Token header)
 *   await srv.stop();
 *
 * ENV-GATE: every practice spec opens with
 *   test.skip(process.env.JARVIS_PRACTICE_E2E !== "1", "practice e2e boots the Kotlin backend")
 * so the existing node-only CI frontend job silently skips these (they never boot a JVM there).
 * The env is set ONLY in the CI practice-e2e job (test-yml-practice.patch) and during local
 * Task-13/14 runs (`$env:JARVIS_PRACTICE_E2E='1'`).
 */

import { ChildProcess, spawn } from "child_process";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import * as http from "http";

export interface PracticeServerHandle {
  baseUrl: string;
  sessionToken: string;
  csrfToken: string;
  stop: () => Promise<void>;
}

/** Pick a free-ish port in the 29000-29999 range. */
function pickPort(): number {
  return 29000 + Math.floor(Math.random() * 1000);
}

/** Poll the health endpoint until it returns 200, or time out. */
async function waitForHealth(url: string, timeoutMs = 90_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      await new Promise<void>((resolve, reject) => {
        const req = http.get(url, (res) => {
          if (res.statusCode === 200) resolve();
          else reject(new Error(`status ${res.statusCode}`));
          res.resume();
        });
        req.on("error", reject);
        req.setTimeout(3000, () => { req.destroy(); reject(new Error("timeout")); });
      });
      return; // health check passed
    } catch {
      await new Promise((r) => setTimeout(r, 1000));
    }
  }
  throw new Error(`Backend did not become healthy within ${timeoutMs}ms at ${url}`);
}

/**
 * Start the practice e2e server.
 *
 * Spawns `gradle --no-daemon :run --args=web` in the WORKTREE root (two levels up from this
 * file) with:
 *   JARVIS_TUTOR_DB   = a fresh scratch SQLite file in a temp dir
 *   JARVIS_SEED_PRACTICE = 1  (seeds problems + creates e2e user+session on first boot)
 *   JARVIS_AUTH_TOKEN  = "practice-e2e-token"  (outer bearer — not needed for session routes,
 *                          but required so the server starts without error)
 *   JARVIS_PORT        = <free port>
 *
 * Reads the line "PRACTICE_E2E_SESSION=<token>" from stdout to obtain the session token.
 * Sets csrf to a known fixed value "practice-e2e-csrf" (both cookie and header).
 */
export async function startPracticeServer(): Promise<PracticeServerHandle> {
  const port = pickPort();
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "jarvis-practice-e2e-"));
  const dbPath = path.join(tmpDir, "practice.db");

  // Worktree root is ../../../ relative to this file (tutor-web/e2e/practice/helpers/ → root)
  const worktreeRoot = path.resolve(__dirname, "..", "..", "..", "..");

  const env: NodeJS.ProcessEnv = {
    ...process.env,
    JARVIS_TUTOR_DB: dbPath,
    JARVIS_SEED_PRACTICE: "1",
    JARVIS_AUTH_TOKEN: "practice-e2e-token",
    JARVIS_PORT: String(port),
    // Suppress non-essential LLM/block-reminder/cron noise
    REMINDER_LOOP_ENABLED: "0",
    JARVIS_CRON_ENABLED: "0",
    // Gradle/JVM: avoid daemon startup noise in CI
    GRADLE_OPTS: "-Dorg.gradle.daemon=false -Dfile.encoding=UTF-8",
  };

  let proc: ChildProcess;
  // On Windows use `gradlew.bat`, on POSIX use `./gradlew`
  const isWin = process.platform === "win32";
  const gradlew = isWin ? "gradlew.bat" : "./gradlew";
  const gradleArgs = ["--no-daemon", ":run", "--args=web"];

  proc = spawn(gradlew, gradleArgs, {
    cwd: worktreeRoot,
    env,
    stdio: ["ignore", "pipe", "pipe"],
    windowsVerbatimArguments: isWin,
    shell: isWin,
  });

  let sessionToken = "";

  // Capture stdout to find PRACTICE_E2E_SESSION= line.
  proc.stdout?.setEncoding("utf8");
  proc.stdout?.on("data", (chunk: string) => {
    const lines = chunk.split("\n");
    for (const line of lines) {
      const match = line.match(/^PRACTICE_E2E_SESSION=(.+)$/);
      if (match) sessionToken = match[1].trim();
      // Forward to debug output (only in verbose mode)
      if (process.env.JARVIS_PRACTICE_E2E_VERBOSE === "1") {
        process.stdout.write("[backend] " + line + "\n");
      }
    }
  });

  proc.stderr?.setEncoding("utf8");
  proc.stderr?.on("data", (chunk: string) => {
    if (process.env.JARVIS_PRACTICE_E2E_VERBOSE === "1") {
      process.stderr.write("[backend-err] " + chunk);
    }
  });

  const baseUrl = `http://127.0.0.1:${port}`;

  // Wait for the health endpoint to be up.
  try {
    await waitForHealth(`${baseUrl}/api/v1/health`);
  } catch (err) {
    proc.kill("SIGTERM");
    try { fs.rmSync(tmpDir, { recursive: true, force: true }); } catch {}
    throw new Error(`Practice backend failed to start on port ${port}: ${err}`);
  }

  // The server is up. sessionToken should be populated by now (printed before health is ready).
  if (!sessionToken) {
    // Fallback: wait a short moment for the stdout buffer to flush
    await new Promise((r) => setTimeout(r, 500));
  }
  if (!sessionToken) {
    proc.kill("SIGTERM");
    try { fs.rmSync(tmpDir, { recursive: true, force: true }); } catch {}
    throw new Error("Practice backend did not print PRACTICE_E2E_SESSION= — seeding may have failed");
  }

  // Known fixed CSRF token for e2e tests (set as both cookie and header).
  const csrfToken = "practice-e2e-csrf-000000000000000";

  // We cannot inject the csrf cookie from here (that's done per-page in the spec via
  // browserContext.addCookies). The helper returns both tokens; each spec applies them.

  const stop = (): Promise<void> => new Promise((resolve) => {
    if (proc.exitCode !== null) {
      resolve();
      return;
    }
    proc.on("exit", () => {
      try { fs.rmSync(tmpDir, { recursive: true, force: true }); } catch {}
      resolve();
    });
    proc.kill("SIGTERM");
    // Force-kill after 10 s
    setTimeout(() => {
      if (proc.exitCode === null) proc.kill("SIGKILL");
    }, 10_000);
  });

  return { baseUrl, sessionToken, csrfToken, stop };
}
