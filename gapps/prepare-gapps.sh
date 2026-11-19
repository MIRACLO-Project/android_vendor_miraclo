#!/bin/bash
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# Google apps for the MIRACLO GApps build, run by build-release.sh in place of extract-gapps.sh.
#   Core: MindTheGapps 17.0 arm64 (Play services, Play Store, Google app, setup), the latest
#         official release, downloaded from MindTheGapps' own GitHub releases.
#   Plus: Google Phone, Messages and Clock from the Pixel factory image (extract-gapps.sh).
# Then check-privapp.py makes sure every privileged app has every privileged permission it
# asks for allowlisted, so the phone can never stop at first boot over permissions.
# Usage: prepare-gapps.sh <Pixel factory image URL or zip>
set -e
T=$(cd "$(dirname "$0")/../../.." && pwd)
G=$T/vendor/miraclo/gapps
CACHE=${MIRACLO_CACHE:-$HOME/gapps-cache}
mkdir -p "$CACHE"

echo "== Pixel apps (Phone, Messages, Clock)"
bash "$G/extract-gapps.sh" "$1"
python3 "$G/trim-pixel.py" "$G/gapps.mk"
python3 "$G/trim-pixel-bp.py" "$G"

echo "== MindTheGapps core"
API=https://api.github.com/repos/MindTheGapps/17.0.0-arm64/releases/latest
JSON=$(curl -fsSL "$API")
URL=$(printf '%s' "$JSON" | grep -o '"browser_download_url": *"[^"]*\.zip"' | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')
[ -n "$URL" ] || { echo "Could not find the MindTheGapps download"; exit 1; }
ZIP=$CACHE/$(basename "$URL")
[ -s "$ZIP" ] || curl -fL --retry 3 -o "$ZIP" "$URL"
MD5URL=$(printf '%s' "$JSON" | grep -o '"browser_download_url": *"[^"]*\.md5[^"]*"' | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')
if [ -n "$MD5URL" ]; then
  WANT=$(curl -fsSL "$MD5URL" | awk '{print $1}')
  HAVE=$(md5sum "$ZIP" | awk '{print $1}')
  [ "$WANT" = "$HAVE" ] || { echo "MindTheGapps download is damaged (md5)"; rm -f "$ZIP"; exit 1; }
  echo "md5 ok"
fi
echo "using $(basename "$ZIP")"
rm -rf "$G/mtg"; mkdir -p "$G/mtg"
W=$(mktemp -d)
unzip -q "$ZIP" 'system/*' -d "$W"
mv "$W/system" "$G/mtg/system"; rm -rf "$W"
python3 "$G/make-mtg.py" "$G/mtg"
grep -q "gapps/mtg/mtg.mk" "$G/gapps.mk" || printf '\n# MindTheGapps core (prepare-gapps.sh)\n$(call inherit-product, vendor/miraclo/gapps/mtg/mtg.mk)\n' >> "$G/gapps.mk"

echo "== Privileged permission check"
python3 "$G/check-privapp.py" "$T"
echo "GApps ready."
