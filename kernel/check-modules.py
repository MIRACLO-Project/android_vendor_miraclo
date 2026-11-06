#!/usr/bin/env python3
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
"""Proves Xiaomi's stock drivers will load on a kernel before anything is flashed.

Each stock module (.ko) records, in its __versions section, the checksum (CRC) of every kernel
function and variable it uses. The kernel refuses a module when one of them differs from its own.
This compares every stock module with the kernel's vmlinux.symvers:
  ok        same checksum
  CHANGED   the kernel's version of that symbol differs: the module will not load
  MISSING   neither the kernel nor any stock module provides it: the module will not load
Usage: check-modules.py <vmlinux.symvers> <folder with stock .ko files>...  (exit 1 on any problem)"""
import os, struct, sys


def sections(data):
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return {}
    shoff, = struct.unpack_from("<Q", data, 0x28)
    shentsize, shnum, shstrndx = struct.unpack_from("<HHH", data, 0x3A)
    hdrs = [struct.unpack_from("<IIQQQQ", data, shoff + i * shentsize) for i in range(shnum)]
    stroff = hdrs[shstrndx][4]
    out = {}
    for name, _type, _flags, _addr, off, size in hdrs:
        end = data.index(b"\0", stroff + name)
        out[data[stroff + name:end].decode()] = data[off:off + size]
    return out


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    kernel = {}
    for line in open(sys.argv[1]):
        f = line.split("\t")
        if len(f) >= 3 and f[2] == "vmlinux":
            kernel[f[1]] = int(f[0], 16) & 0xFFFFFFFF
    mods = []
    for d in sys.argv[2:]:
        for root, _, files in os.walk(d):
            mods += [os.path.join(root, f) for f in files if f.endswith(".ko")]
    exported = set()
    needs = {}
    for path in mods:
        s = sections(open(path, "rb").read())
        exported.update(x.decode() for x in s.get("__ksymtab_strings", b"").split(b"\0") if x)
        v = s.get("__versions", b"")
        needs[path] = [(struct.unpack_from("<Q", v, i)[0] & 0xFFFFFFFF,
                        v[i + 8:i + 64].split(b"\0")[0].decode()) for i in range(0, len(v) - 63, 64)]
    bad = 0
    checked = 0
    names = {}
    for path, syms in sorted(needs.items()):
        name = os.path.basename(path)
        if name in names:
            continue  # the same module in vendor_boot and vendor_dlkm
        names[name] = 1
        for crc, sym in syms:
            checked += 1
            if sym in kernel:
                if kernel[sym] != crc:
                    bad += 1
                    print("CHANGED  %-28s %s" % (name, sym))
            elif sym not in exported and sym != "module_layout":
                bad += 1
                print("MISSING  %-28s %s" % (name, sym))
    print("%d stock modules, %d symbol uses checked, %d problems" % (len(names), checked, bad))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
