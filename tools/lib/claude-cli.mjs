import { createHash } from "node:crypto";
import { spawn as nodeSpawn } from "node:child_process";
import { execFileSync } from "node:child_process";

function defaultVersionImpl(bin) {
  // execFileSync throws if binary missing — propagated as "claude CLI not found"
  const out = execFileSync(bin, ["--version"], { encoding: "utf8" });
  const m = out.match(/(\d+\.\d+\.\d+)/);
  return m ? m[1] : out.trim();
}

export async function callLlm({
  apiKey = null,                 // ignored — CLI uses subscription pool
  model = null,                  // optional --model passthrough
  systemPrompt,
  userPrompt,
  temperature = null,            // ignored by CLI; preserved in API shape
  seed = null,                   // ignored
  maxTokens = null,              // ignored
  bin = process.env.CLAUDE_CLI_BIN || "claude",
  timeoutMs = Number(process.env.CLAUDE_CLI_TIMEOUT_MS) || 120000,
  spawnImpl = nodeSpawn,
  versionImpl = defaultVersionImpl,
}) {
  let cli_version;
  try {
    cli_version = versionImpl(bin);
  } catch (e) {
    throw new Error(`callLlm: claude CLI not found (${bin}): ${e.message}`);
  }

  const t0 = Date.now();
  const prompt_sha256 = createHash("sha256")
    .update(systemPrompt + "\n---\n" + userPrompt)
    .digest("hex");

  const args = ["--print"];
  if (model) args.push("--model", model);
  // System+user join — Claude CLI --print takes prompt from stdin or arg;
  // we use stdin to avoid arg-size limits and shell escaping.
  const fullPrompt = `${systemPrompt}\n\n${userPrompt}`;

  const child = spawnImpl(bin, args, { stdio: ["pipe", "pipe", "pipe"] });
  let stdoutBuf = "", stderrBuf = "";
  child.stdout.on("data", (d) => { stdoutBuf += d.toString(); });
  child.stderr.on("data", (d) => { stderrBuf += d.toString(); });
  child.stdin.write(fullPrompt);
  child.stdin.end();

  const timer = setTimeout(() => { try { child.kill("SIGTERM"); } catch {} }, timeoutMs);
  const [exitCode, signal] = await new Promise((resolve) => {
    child.on("close", (code, sig) => resolve([code, sig]));
    child.on("error", (err) => resolve([null, err.message]));
  });
  clearTimeout(timer);

  if (signal === "SIGTERM" || exitCode === null) {
    throw new Error(`callLlm: claude CLI timeout (${timeoutMs}ms) or signal ${signal}`);
  }
  if (exitCode !== 0) {
    throw new Error(`callLlm: claude CLI exit ${exitCode} — ${stderrBuf.slice(0, 200) || "(no stderr)"}`);
  }

  return {
    text: stdoutBuf,
    model_resolved: model ?? `claude-cli@${cli_version}`,
    prompt_sha256,
    tokens_in: null,
    tokens_out: null,
    latency_ms: Date.now() - t0,
    cli_version,
  };
}
