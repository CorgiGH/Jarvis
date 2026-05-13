import { readdirSync, readFileSync, existsSync } from "node:fs";
import { join } from "node:path";

export async function readEvents({ dir = "/opt/jarvis/data/private", sshTarget = null } = {}) {
  let files = [];
  if (sshTarget) {
    const { execSync } = await import("node:child_process");
    const list = execSync(`ssh ${sshTarget} "ls ${dir}/tutor_events.*.jsonl 2>/dev/null"`).toString().trim().split("\n").filter(Boolean);
    files = list.map((remotePath) => {
      const local = join("/tmp", remotePath.split("/").pop());
      execSync(`scp ${sshTarget}:${remotePath} ${local}`);
      return local;
    });
  } else {
    if (!existsSync(dir)) return [];
    files = readdirSync(dir).filter(f => f.startsWith("tutor_events.") && f.endsWith(".jsonl")).map(f => join(dir, f));
  }
  const all = [];
  for (const f of files) {
    const lines = readFileSync(f, "utf8").split("\n").filter(Boolean);
    for (const line of lines) {
      try { all.push(JSON.parse(line)); } catch {}
    }
  }
  all.sort((a, b) => (a.ts_utc ?? "").localeCompare(b.ts_utc ?? ""));
  return all;
}

export function filterEvents(events, { task_id, session_id, from_ts, to_ts, event_type, include_synthetic = false } = {}) {
  return events.filter(e => {
    if (!include_synthetic && e.is_synthetic) return false;
    if (task_id && e.task_id !== task_id) return false;
    if (session_id && e.session_id !== session_id) return false;
    if (event_type && e.event_type !== event_type) return false;
    if (from_ts && (e.ts_utc ?? "") < from_ts) return false;
    if (to_ts && (e.ts_utc ?? "") > to_ts) return false;
    return true;
  });
}
