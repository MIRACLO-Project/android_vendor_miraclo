#!/usr/bin/env python3
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
"""Keeps, from the Pixel set in gapps.mk, only Google's own apps MIRACLO ships beside
MindTheGapps: Phone, Messages, Clock, Contacts, Calculator, Calendar, Chrome (with its
Trichrome library), Files by Google, Gboard, Photos and YouTube. MindTheGapps provides the core
(Play services, Play Store, Google app, setup). Modules are matched by package name, from the
pkgmap.txt extract-gapps.sh writes. trim-pixel-bp.py then removes every other Pixel module."""
import os, re, sys
p = sys.argv[1]
KEEP_PKGS = [
    "com.google.android.dialer",
    "com.google.android.apps.messaging",
    "com.google.android.deskclock",
    "com.google.android.contacts",
    "com.google.android.calculator",
    "com.google.android.calendar",
    "com.android.chrome",
    "com.google.android.trichromelibrary",
    "com.google.android.apps.nbu.files",
    "com.google.android.inputmethod.latin",
    "com.google.android.apps.photos",
    "com.google.android.youtube",
]
REQUIRED = {"com.google.android.dialer", "com.google.android.apps.messaging"}
pm = os.path.join(os.path.dirname(os.path.abspath(p)), "pkgmap.txt")
by_pkg = {}
for line in open(pm):
    parts = line.split()
    if len(parts) == 2:
        by_pkg[parts[0]] = parts[1]
missing = [k for k in KEEP_PKGS if k not in by_pkg]
if REQUIRED & set(missing):
    sys.exit("trim-pixel: Google Phone or Messages is missing from the Pixel image")
for k in missing:
    print("trim-pixel: NOTE: %s is not in this Pixel image" % k)
keep_names = {by_pkg[k] for k in KEEP_PKGS if k in by_pkg} | {"MiraGmsFrameworksOverlay"}

s = open(p).read()
lines = s.splitlines()
i = next(k for k, l in enumerate(lines) if l.startswith("PRODUCT_PACKAGES +="))
j = i + 1
while j < len(lines) and lines[j].startswith("    "):
    j += 1
mods = [l.strip().rstrip("\\").strip() for l in lines[i + 1:j]]
kept = [m for m in mods if m in keep_names]
block = ["PRODUCT_PACKAGES += \\"] + ["    %s \\" % m for m in kept[:-1]] + ["    %s" % kept[-1]]
open(p, "w").write("\n".join(lines[:i] + block + lines[j:]) + "\n")
print("Pixel apps kept: " + ", ".join(kept))
