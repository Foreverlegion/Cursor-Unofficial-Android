#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
mapping="${1:-$root/app/build/outputs/mapping/release/mapping.txt}"

if [ ! -f "$mapping" ]; then
  echo "R8 mapping is missing: $mapping"
  exit 1
fi

python3 - "$mapping" <<'PY'
import re
import sys

path = sys.argv[1]
text = open(path, encoding="utf-8", errors="replace").read().splitlines()

blocks = {}
name = None
members = []
for line in text:
    if line.startswith((" ", "\t", "#")):
        if name and not line.startswith("#"):
            members.append(line.strip())
        continue
    if name:
        blocks[name] = members
    name = None
    members = []
    if " -> " in line and line.endswith(":"):
        left, right = line[:-1].split(" -> ", 1)
        if left == right:
            name = left
            members = []
if name:
    blocks[name] = members

noarg = re.compile(r"void <init>\(\)(?::[\d:]+)? -> <init>")
worker = re.compile(
    r"void <init>\(android\.content\.Context,androidx\.work\.WorkerParameters\)(?::[\d:]+)? -> <init>"
)

required = {
    "androidx.work.impl.WorkDatabase_Impl": noarg,
    "androidx.work.WorkManagerInitializer": noarg,
    "androidx.emoji2.text.EmojiCompatInitializer": noarg,
    "androidx.lifecycle.ProcessLifecycleInitializer": noarg,
    "androidx.profileinstaller.ProfileInstallerInitializer": noarg,
    "androidx.work.OverwritingInputMerger": noarg,
    "com.cursorandroid.app.data.notify.RunWatchWorker": worker,
    "com.cursorandroid.app.data.notify.InboxSweepWorker": worker,
    "com.cursorandroid.app.data.notify.FeedbackReplyWorker": worker,
}

missing = []
for cls, pattern in required.items():
    found = blocks.get(cls)
    if found is None:
        missing.append(f"{cls} was renamed or removed")
        continue
    if not any(pattern.search(member) for member in found):
        missing.append(f"{cls} has no matching <init> in the minified mapping")

if missing:
    print("R8 dropped a constructor that is invoked by reflection:")
    for item in missing:
        print(f"  {item}")
    sys.exit(1)
PY
