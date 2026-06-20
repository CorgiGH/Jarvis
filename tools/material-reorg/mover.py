#!/usr/bin/env python3
"""
SAFE archive mover — council-mandated order: TAR (list-tested) -> COPY -> sha256-VERIFY -> STOP.
NO DELETE. Originals (material/raw-corpus-*, ~/Downloads/_material_survey) are left untouched;
deletion is a separate later step after Alex confirms the new material/archive/ tree.

Reads tools/material-reorg/out/move-map.csv. Copies only action in {keep, derived}; 'drop'
rows are NOT copied (they survive only inside the tar). Idempotent/resumable: a target that
already exists with a matching sha256 is skipped.
"""
import csv, hashlib, os, shutil, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, os.pardir, os.pardir))
OUT  = os.path.join(HERE, "out")
SURVEY_ROOT = os.path.expanduser(os.path.join("~", "Downloads", "_material_survey"))
HOME = os.path.expanduser("~")
TS = os.environ.get("MOVER_TS") or str(int(time.time()))
# Forward slashes: git-bash GNU tar treats "C:\..." as a remote host:path. With
# --force-local + forward slashes the drive colon is read as a local path.
TAR = os.path.join(HOME, f"material-backup-{TS}.tar").replace("\\", "/")

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for c in iter(lambda: f.read(1 << 20), b""):
            h.update(c)
    return h.hexdigest()

def src_abs(relpath):
    if relpath.startswith("_material_survey/"):
        return os.path.join(SURVEY_ROOT, relpath[len("_material_survey/"):])
    return os.path.join(REPO, relpath)

def log(m): print(m, file=sys.stderr, flush=True)

# ---------------- Step 1: TAR (raw dumps + survey only; exclude current/ + archive/) ----------------
log(f"[1/4] tar -> {TAR}")
raw_dirs = [d for d in ("raw-corpus-md","raw-corpus-secondbrain","raw-corpus-sorc",
                        "raw-corpus-study-guides") if os.path.isdir(os.path.join(REPO,"material",d))]
tar_cmd = ["tar","--force-local","-cf",TAR,"-C",REPO.replace("\\","/")] + \
          [f"material/{d}" for d in raw_dirs]
if os.path.isdir(SURVEY_ROOT):
    tar_cmd += ["-C", os.path.dirname(SURVEY_ROOT).replace("\\","/"), "_material_survey"]
r = subprocess.run(tar_cmd, capture_output=True, text=True)
if r.returncode != 0:
    log("TAR FAILED:\n"+r.stderr); sys.exit(1)
tar_bytes = os.path.getsize(TAR)
# list-test ("a tar you cannot list is not a backup")
lt = subprocess.run(["tar","--force-local","-tf",TAR], capture_output=True, text=True)
if lt.returncode != 0:
    log("TAR LIST-TEST FAILED:\n"+lt.stderr); sys.exit(1)
tar_members = sum(1 for _ in lt.stdout.splitlines())
tar_sha = sha256(TAR)
log(f"    tar OK: {tar_bytes} bytes, {tar_members} members, sha256={tar_sha}")
with open(os.path.join(OUT,"backup-manifest.txt"),"w",encoding="utf-8") as f:
    f.write(f"tar={TAR}\nbytes={tar_bytes}\nmembers={tar_members}\nsha256={tar_sha}\nts={TS}\n")

# ---------------- Step 2+3: COPY + VERIFY ----------------
log("[2/4] reading move-map ...")
rows = list(csv.DictReader(open(os.path.join(OUT,"move-map.csv"),encoding="utf-8")))
work = [r for r in rows if r["action"] in ("keep","derived")]
log(f"    {len(work)} files to copy ({sum(1 for r in rows if r['action']=='drop')} drops NOT copied)")

copied=skipped=verified=0; problems=[]
log("[3/4] copy + verify ...")
for i,r in enumerate(work):
    s = src_abs(r["source"]); t = os.path.join(REPO, r["target"])
    if not os.path.isfile(s):
        problems.append(("MISSING_SRC", r["source"])); continue
    ssha = sha256(s)
    if os.path.isfile(t) and sha256(t) == ssha:
        skipped+=1; verified+=1; continue
    os.makedirs(os.path.dirname(t), exist_ok=True)
    try:
        shutil.copy2(s, t)
    except Exception as e:
        problems.append(("COPY_FAIL", f"{r['source']} :: {e}")); continue
    if sha256(t) == ssha:
        copied+=1; verified+=1
    else:
        problems.append(("VERIFY_MISMATCH", r["target"]))
    if (i+1) % 500 == 0:
        log(f"    {i+1}/{len(work)}  copied={copied} skipped={skipped} verified={verified} problems={len(problems)}")

# ---------------- Step 4: report (NO DELETE) ----------------
log("[4/4] DONE — NO DELETE performed (originals untouched).")
with open(os.path.join(OUT,"move-report.txt"),"w",encoding="utf-8") as f:
    f.write(f"tar={TAR} ({tar_bytes}B, {tar_members} members, sha256={tar_sha})\n")
    f.write(f"to_copy={len(work)} copied={copied} skipped_already_present={skipped} verified={verified}\n")
    f.write(f"problems={len(problems)}\n")
    for kind,detail in problems: f.write(f"  {kind}: {detail}\n")
log(f"RESULT  tar_members={tar_members}  to_copy={len(work)}  copied={copied}  "
    f"skipped={skipped}  verified={verified}  problems={len(problems)}")
if problems:
    log("PROBLEMS (first 20):")
    for kind,detail in problems[:20]: log(f"  {kind}: {detail}")
log(f"backup tar: {TAR}")
log("originals NOT deleted — confirm the tree, then run a separate delete step.")
