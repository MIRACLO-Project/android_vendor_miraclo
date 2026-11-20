#!/bin/bash
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# Moves MIRACLO to a newer Qualcomm CLO release (and with it Android's monthly security patches).
# Run from the source root:
#
#   bash vendor/miraclo/tools/update-base.sh LA.QSSI.17.0.r1-<number>-qssi.0
#
# 1. Reads the release's manifest from CodeLinaro.
# 2. Points clo/system.xml of the MIRACLO manifest at it (committed and pushed).
# 3. Merges the release into every MIRACLO fork (public and private) and pushes the clean ones.
#    A fork with conflicts is left untouched and listed at the end, to be merged by hand.
# Then run: repo sync -c -j8 --force-sync --no-clone-bundle --no-tags
set -e
TAG="$1"
[ -n "$TAG" ] || { echo "Usage: $0 <CLO release tag, e.g. LA.QSSI.17.0.r1-13500-qssi.0>"; exit 2; }
ROOT=$(pwd)
[ -d .repo ] || { echo "Run this from the source root (where .repo is)."; exit 2; }
CLO=https://git.codelinaro.org/clo/la
export GIT_AUTHOR_NAME=PSP GIT_COMMITTER_NAME=PSP
export GIT_AUTHOR_EMAIL=78963354+PSP-HUB@users.noreply.github.com GIT_COMMITTER_EMAIL=78963354+PSP-HUB@users.noreply.github.com
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT

echo "== CLO release manifest $TAG"
git clone -q --depth 1 --branch "$TAG" "$CLO/la/system/manifest" "$W/m" 2>/dev/null \
  || git clone -q --depth 1 --branch release "$CLO/la/system/manifest" "$W/m"
NEW="$W/m/$TAG.xml"
[ -f "$NEW" ] || NEW="$W/m/default.xml"
[ -f "$NEW" ] || { echo "STOPPED: no manifest for $TAG in the CLO manifest repository."; ls "$W/m"; exit 1; }
echo "   $(basename "$NEW")"

echo "== MIRACLO manifest: clo/system.xml -> $TAG"
M=.repo/manifests
python3 - "$NEW" "$M/clo/system.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
src, out = sys.argv[1], sys.argv[2]
root = ET.parse(src).getroot()
res = ET.Element("manifest")
for p in root.findall("project"):
    p.set("remote", "clo-la")
    res.append(p)
ET.indent(res, space="    ")
ET.ElementTree(res).write(out, encoding="UTF-8", xml_declaration=True)
print("   %d projects" % len(res))
PY
( cd "$M" && git add clo/system.xml && git commit -q -m "clo: $TAG" && git push -q origin HEAD:aurora && echo "   pushed" ) || echo "   (nothing to change)"

echo "== Merging $TAG into the MIRACLO forks"
python3 - "$NEW" "$M" > "$W/forks.txt" <<'PY'
import sys, glob, xml.etree.ElementTree as ET
new = {p.get("path", p.get("name")): (p.get("name"), p.get("revision")) for p in ET.parse(sys.argv[1]).getroot().findall("project")}
files = glob.glob(sys.argv[2] + "/miraclo.xml") + glob.glob(sys.argv[2] + "/../local_manifests/*.xml")
for f in files:
    for p in ET.parse(f).getroot().iter("project"):
        path = p.get("path", p.get("name"))
        if path in new and new[path][1]:
            print(path, new[path][0], new[path][1])
PY
OK=(); FAILED=()
while read -r path name rev; do
  [ -d "$ROOT/$path/.git" ] || [ -f "$ROOT/$path/.git" ] || continue
  cd "$ROOT/$path"
  remote=$(git remote | grep -E '^(miraclo|mira|private|origin)$' | head -1)
  [ -n "$remote" ] || remote=$(git remote | head -1)
  echo "-- $path ($remote)"
  git fetch -q "$remote" aurora --unshallow 2>/dev/null || git fetch -q "$remote" aurora
  git checkout -q -B aurora "$remote/aurora"
  if ! git fetch -q "$CLO/$name" "$rev" 2>/dev/null; then
    git fetch -q --unshallow "$CLO/$name" "$rev" 2>/dev/null || { echo "   could not fetch $name"; FAILED+=("$path (fetch)"); continue; }
  fi
  if git merge-base --is-ancestor FETCH_HEAD HEAD 2>/dev/null; then echo "   already has it"; OK+=("$path"); continue; fi
  if git merge -q --no-edit -m "Merge CLO $TAG" FETCH_HEAD 2>/dev/null; then
    git push -q "$remote" HEAD:aurora && echo "   merged and pushed" && OK+=("$path")
  else
    git merge --abort 2>/dev/null || true
    echo "   CONFLICTS - left as it was"
    FAILED+=("$path")
  fi
done < "$W/forks.txt"
cd "$ROOT"

echo
echo "Merged: ${#OK[@]}"
if [ ${#FAILED[@]} -gt 0 ]; then
  echo "Need merging by hand (cd into each, git fetch $CLO/<name> <revision>, git merge FETCH_HEAD):"
  printf '   %s\n' "${FAILED[@]}"
fi
echo "Next: repo sync -c -j8 --force-sync --no-clone-bundle --no-tags"
