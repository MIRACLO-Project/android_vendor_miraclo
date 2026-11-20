#!/bin/bash
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# Moves MIRACLO from one Qualcomm CLO release to a newer one (and with it Android's monthly
# security patches). Run from the source root:
#
#   bash vendor/miraclo/tools/update-base.sh <current CLO tag> <new CLO tag>
#   e.g. bash vendor/miraclo/tools/update-base.sh LA.QSSI.17.0.r1-12300-qssi.0 LA.QSSI.17.0.r1-12500-qssi.0
#
# 1. Points clo/system.xml of the MIRACLO manifest at the new release (committed and pushed).
# 2. For every MIRACLO fork (public and private): takes CLO's own changes between the two
#    releases for that project and applies them on top of the fork (3-way, so MIRACLO's changes
#    stay), commits and pushes. Only the two release snapshots are downloaded, never full
#    history. A fork whose changes clash is left untouched and listed at the end.
# Then: repo sync -c -j4 --force-sync --no-clone-bundle --no-tags
set -e
FROM="$1"; TO="$2"
[ -n "$FROM" ] && [ -n "$TO" ] || { echo "Usage: $0 <current CLO tag> <new CLO tag>"; exit 2; }
ROOT=$(pwd)
[ -d .repo ] || { echo "Run this from the source root (where .repo is)."; exit 2; }
CLO=https://git.codelinaro.org/clo/la
export GIT_AUTHOR_NAME=PSP GIT_COMMITTER_NAME=PSP
export GIT_AUTHOR_EMAIL=78963354+PSP-HUB@users.noreply.github.com GIT_COMMITTER_EMAIL=78963354+PSP-HUB@users.noreply.github.com
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT

manifest() { # tag dir
  git clone -q --depth 1 --branch "$1" "$CLO/la/system/manifest" "$2" 2>/dev/null || { echo "STOPPED: no CLO tag $1"; exit 1; }
  if [ -f "$2/$1.xml" ]; then echo "$2/$1.xml"; else echo "$2/default.xml"; fi
}
echo "== CLO release manifests"
OLD=$(manifest "$FROM" "$W/old"); NEW=$(manifest "$TO" "$W/new")
echo "   $FROM -> $TO"

echo "== MIRACLO manifest: clo/system.xml -> $TO"
M=.repo/manifests
( cd "$M" && git fetch -q origin aurora && git checkout -q -B aurora origin/aurora )
python3 - "$NEW" "$M/clo/system.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
res = ET.Element("manifest")
for p in root.findall("project"):
    p.set("remote", "clo-la")
    res.append(p)
ET.indent(res, space="    ")
ET.ElementTree(res).write(sys.argv[2], encoding="UTF-8", xml_declaration=True)
print("   %d projects" % len(res))
PY
( cd "$M"
  if git diff --quiet; then echo "   already on $TO"
  else git commit -qam "clo: $TO" && git push -q origin HEAD:aurora && echo "   pushed"; fi )

echo "== CLO's changes $FROM -> $TO into the MIRACLO forks"
python3 - "$OLD" "$NEW" "$M" > "$W/forks.txt" <<'PY'
import sys, glob, os, xml.etree.ElementTree as ET
def load(f): return {p.get("path", p.get("name")): (p.get("name"), p.get("revision")) for p in ET.parse(f).getroot().findall("project")}
old, new = load(sys.argv[1]), load(sys.argv[2])
seen = set()
files = [sys.argv[3] + "/miraclo.xml"] + sorted(glob.glob(os.path.join(sys.argv[3], "..", "local_manifests", "*.xml")))
for f in files:
    if not os.path.exists(f): continue
    for p in ET.parse(f).getroot().iter("project"):
        path = p.get("path", p.get("name"))
        if path in seen or path not in new or path not in old: continue
        seen.add(path)
        if old[path][1] != new[path][1]:
            print(path, new[path][0], old[path][1], new[path][1])
PY
echo "   $(wc -l < "$W/forks.txt") forks have changes in $TO"
OK=(); FAILED=()
while read -r path name oldrev newrev; do
  [ -e "$ROOT/$path/.git" ] || continue
  cd "$ROOT/$path"
  rm -f "$(git rev-parse --git-dir)/shallow.lock" "$(git rev-parse --git-dir)/index.lock"
  remote=$(git remote | grep -E '^(mira|miraclo)$' | tail -1)
  [ -n "$remote" ] || remote=$(git remote | head -1)
  echo "-- $path"
  git fetch -q --depth=1 "$remote" aurora && git checkout -q -B aurora FETCH_HEAD
  if ! git fetch -q --depth=1 "$CLO/$name" "$oldrev" "$newrev" 2>/dev/null; then
    echo "   could not download CLO $name"; FAILED+=("$path (download)"); continue
  fi
  git diff --binary "$oldrev" "$newrev" > "$W/p.diff"
  if [ ! -s "$W/p.diff" ]; then echo "   no changes"; continue; fi
  if git apply --3way --index "$W/p.diff" 2>"$W/err"; then
    git commit -q -m "Update to CLO $TO" -m "CLO's changes from $FROM to $TO."
    git push -q "$remote" HEAD:aurora && echo "   updated and pushed" && OK+=("$path")
  else
    git reset -q --hard HEAD
    echo "   clashes with MIRACLO's changes - left as it was"; head -5 "$W/err"
    FAILED+=("$path")
  fi
done < "$W/forks.txt"
cd "$ROOT"

echo
echo "Updated: ${#OK[@]}"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Not updated (send this list):"
  printf '   %s\n' "${FAILED[@]}"
fi
echo "Next: repo sync -c -j4 --force-sync --no-clone-bundle --no-tags"
