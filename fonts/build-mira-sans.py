#!/usr/bin/env python3
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# Builds Mira Sans from the Inter variable fonts (SIL Open Font License 1.1).
#
# Usage:
#   python3 build-mira-sans.py InterVariable.ttf InterVariable-Italic.ttf <out-dir>
#
# Writes MiraSans-Regular.ttf and MiraSans-Italic.ttf. Outlines, spacing, kerning
# and both variation axes (wght 100-900, opsz 14-32) are kept as they are; only
# the naming is changed so the system reports the font as Mira Sans.

import sys
from fontTools.ttLib import TTFont

STYLES = (
    # (source index, style name, full name, PostScript name, variations prefix)
    (0, "Regular", "Mira Sans", "MiraSans-Regular", "MiraSans"),
    (1, "Italic", "Mira Sans Italic", "MiraSans-Italic", "MiraSansItalic"),
)


def rename(font, style, full, ps, prefix, version):
    name = font["name"]
    for rec in list(name.names):
        if rec.nameID in (1, 2, 3, 4, 6, 16, 17, 21, 22, 25):
            name.removeNames(nameID=rec.nameID)
    def put(nid, text):
        name.setName(text, nid, 3, 1, 0x409)
        name.setName(text, nid, 1, 0, 0)
    put(1, "Mira Sans")
    put(2, style)
    put(3, f"{version};MIRACLO;{ps}")
    put(4, full)
    put(6, ps)
    put(25, prefix)
    # Named instances get PostScript names that follow the new family name.
    for inst in font["fvar"].instances:
        sub = name.getDebugName(inst.subfamilyNameID) or ""
        if inst.postscriptNameID not in (None, 0xFFFF):
            name.setName(f"{prefix}-{sub.replace(' ', '')}", inst.postscriptNameID, 3, 1, 0x409)


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__ or "usage: build-mira-sans.py roman.ttf italic.ttf out-dir")
    sources = sys.argv[1:3]
    out = sys.argv[3]
    for idx, style, full, ps, prefix in STYLES:
        font = TTFont(sources[idx])
        version = font["name"].getDebugName(5) or "Version 4.1"
        rename(font, style, full, ps, prefix, version.split(";")[0])
        font.save(f"{out}/{ps}.ttf")
        print(f"{out}/{ps}.ttf")


if __name__ == "__main__":
    main()
