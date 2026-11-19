#!/usr/bin/env python3
#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
"""Checks a finished build (out/target/product/<device>) for the mistakes that stop a phone
from booting or break apps, before anything is packaged or flashed:

  1. privileged permissions an app asks for that no allowlist grants
     (system_server refuses to start: the boot loop of 1.0's first build);
  2. apps whose native code lacks the phone's 32-bit ABI while declaring multiArch
     (Android refuses to install them: the missing WebView of 1.0's first build);
  3. apps that need a static shared library (Trichrome) that is missing or another version;
  4. files that must be in the build (passed with --expect).

The checker tests itself first (it must read the framework's permissions and the apps'
manifests); if it cannot, it fails instead of passing silently.

Usage: preflight.py <product out dir> [--aapt2 PATH] [--expect relative/path ...]
Exit code 0 only if everything passed.
"""
import argparse
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

DEVICE_ABIS_64 = "arm64-v8a"
DEVICE_ABIS_32 = ("armeabi-v7a", "armeabi")
PRIVILEGED = 0x10
APP_PARTITIONS = ("system", "system_ext", "product", "vendor", "odm")


def parse_xmltree(text):
    """aapt2 'dump xmltree' text -> list of (depth, tag, {attr: value})."""
    out = []
    for line in text.splitlines():
        m = re.match(r"^(\s*)E: (\S+)", line)
        if m:
            out.append([len(m.group(1)), m.group(2), {}])
            continue
        m = re.match(r"^\s*A: (.+?)=(.*)$", line)
        if m and out:
            key = re.sub(r"\(0x[0-9a-fA-F]+\)$", "", m.group(1)).split(":")[-1]
            val = m.group(2).strip()
            if val.startswith('"'):
                val = val[1:].split('"', 1)[0]
            else:
                val = re.sub(r"^\(type 0x[0-9a-fA-F]+\)", "", val).split(" ", 1)[0]
            out[-1][2][key] = val
    return out


def as_int(v):
    try:
        return int(v, 0)
    except (TypeError, ValueError):
        return None


def as_bool(v):
    if v is None:
        return False
    if v.lower() == "true":
        return True
    i = as_int(v)
    return bool(i)


class Manifests:
    def __init__(self, aapt2):
        self.aapt2 = aapt2

    def read(self, apk):
        r = subprocess.run([self.aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                           capture_output=True, text=True)
        if r.returncode != 0 or "E: manifest" not in r.stdout:
            return None
        return parse_xmltree(r.stdout)


def framework_privileged(man, out):
    fw = os.path.join(out, "system/framework/framework-res.apk")
    tree = man.read(fw)
    if tree is None:
        return None
    perms = set()
    for _, tag, a in tree:
        if tag == "permission" and "name" in a:
            lvl = as_int(a.get("protectionLevel"))
            if lvl is not None and lvl & PRIVILEGED:
                perms.add(a["name"])
    return perms


def allowlists(out):
    """{partition: {package: (allowed set, denied set)}} from <partition>/etc/permissions."""
    res = {}
    for part in APP_PARTITIONS:
        d = os.path.join(out, part, "etc", "permissions")
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if not f.endswith(".xml"):
                continue
            try:
                root = ET.parse(os.path.join(d, f)).getroot()
            except ET.ParseError as e:
                print(f"  BAD XML {part}/etc/permissions/{f}: {e}")
                continue
            for pp in root.iter("privapp-permissions"):
                pkg = pp.get("package")
                allow, deny = res.setdefault(part, {}).setdefault(pkg, (set(), set()))
                for p in pp.iter("permission"):
                    allow.add(p.get("name"))
                for p in pp.iter("deny-permission"):
                    deny.add(p.get("name"))
    # vendor and odm apps share one list, as in SystemConfig
    if "odm" in res:
        for pkg, (a, d) in res["odm"].items():
            va, vd = res.setdefault("vendor", {}).setdefault(pkg, (set(), set()))
            va |= a
            vd |= d
    return res


def apks(out):
    for part in APP_PARTITIONS:
        for kind in ("app", "priv-app"):
            base = os.path.join(out, part, kind)
            if not os.path.isdir(base):
                continue
            for d in sorted(os.listdir(base)):
                folder = os.path.join(base, d)
                if not os.path.isdir(folder):
                    continue
                for f in sorted(os.listdir(folder)):
                    if f.endswith(".apk"):
                        yield part, kind, os.path.join(folder, f)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--aapt2", default=None)
    ap.add_argument("--expect", nargs="*", default=[])
    args = ap.parse_args()
    out = os.path.abspath(args.out)
    aapt2 = args.aapt2
    if not aapt2:
        top = os.path.abspath(os.path.join(out, "../../../.."))
        for c in (os.path.join(top, "out/host/linux-x86/bin/aapt2"),
                  os.path.join(top, "prebuilts/sdk/tools/linux/bin/aapt2")):
            if os.access(c, os.X_OK):
                aapt2 = c
                break
    if not aapt2:
        print("FAIL: aapt2 not found (m aapt2)")
        return 1
    man = Manifests(aapt2)
    problems, notes = [], []

    # --- self-test ---------------------------------------------------------------------------
    priv = framework_privileged(man, out)
    if not priv or len(priv) < 50:
        print(f"FAIL: checker could not read the framework's privileged permissions "
              f"({0 if not priv else len(priv)} found); nothing was checked.")
        return 1
    lists = allowlists(out)
    n_lists = sum(len(v) for v in lists.values())
    if n_lists < 10:
        print(f"FAIL: checker found only {n_lists} privapp allowlist entries; nothing was checked.")
        return 1

    all_apks = list(apks(out))
    parsed, unreadable = {}, []
    for part, kind, apk in all_apks:
        t = man.read(apk)
        if t is None:
            unreadable.append(os.path.relpath(apk, out))
        else:
            parsed[apk] = (part, kind, t)
    if len(parsed) < 0.8 * max(1, len(all_apks)):
        print(f"FAIL: checker could read only {len(parsed)} of {len(all_apks)} apps.")
        return 1
    print(f"checker ok: {len(priv)} privileged framework permissions, {n_lists} allowlist "
          f"entries, {len(parsed)}/{len(all_apks)} apps read")

    # --- 1. privileged permission allowlists ---------------------------------------------------
    static_libs, uses_static = {}, []
    packages = {}
    for apk, (part, kind, tree) in parsed.items():
        pkg = next((a.get("package") for _, tag, a in tree if tag == "manifest"), None)
        packages.setdefault(pkg, []).append(os.path.relpath(apk, out))
        app = next((a for _, tag, a in tree if tag == "application"), {})
        for _, tag, a in tree:
            if tag == "static-library":
                static_libs.setdefault(a.get("name"), set()).add(a.get("version"))
            if tag == "uses-static-library":
                uses_static.append((os.path.relpath(apk, out), pkg, a.get("name"), a.get("version")))
        if kind == "priv-app" and pkg != "android":
            wanted = {a.get("name") for _, tag, a in tree
                      if tag in ("uses-permission", "uses-permission-sdk-23") and a.get("name")}
            need = sorted(p for p in wanted if p in priv)
            allow, deny = lists.get(part, {}).get(pkg, (set(), set()))
            missing = [p for p in need if p not in allow and p not in deny]
            if missing:
                problems.append(f"ALLOWLIST {pkg} ({part}/priv-app): " + ", ".join(missing))

        # --- 2. native code ABIs ---------------------------------------------------------------
        try:
            with zipfile.ZipFile(apk) as z:
                abis = {n.split("/")[1] for n in z.namelist()
                        if n.startswith("lib/") and n.count("/") >= 2 and n.endswith(".so")}
        except zipfile.BadZipFile:
            problems.append(f"BROKEN APK {os.path.relpath(apk, out)}")
            continue
        if abis and as_bool(app.get("multiArch")):
            has32 = any(a in abis for a in DEVICE_ABIS_32)
            if DEVICE_ABIS_64 not in abis or not has32:
                problems.append(f"ABI {pkg} ({os.path.relpath(apk, out)}): multiArch app with only "
                                f"{sorted(abis)}; this phone also runs 32-bit code, Android will refuse it")

    # --- 3. static shared libraries ------------------------------------------------------------
    for rel, pkg, name, ver in uses_static:
        have = static_libs.get(name)
        if not have:
            problems.append(f"STATIC LIB {pkg} ({rel}) needs {name} v{ver}: not in the build")
        elif ver not in have:
            problems.append(f"STATIC LIB {pkg} ({rel}) needs {name} v{ver}: build has v{sorted(have)}")

    for pkg, where in packages.items():
        if pkg and len(where) > 1:
            notes.append(f"same package twice: {pkg}: {where}")

    # --- 4. expected files ---------------------------------------------------------------------
    # each entry: "path", "path1|path2" (any of them), or "path::text" (the file contains text)
    for e in args.expect:
        paths, _, text = e.partition("::")
        found = [p for p in paths.split("|") if os.path.exists(os.path.join(out, p))]
        if not found:
            problems.append(f"MISSING FILE {paths}")
        elif text:
            data = "".join(open(os.path.join(out, p), errors="replace").read() for p in found)
            if text not in data:
                problems.append(f"MISSING SETTING '{text}' in {paths}")

    for n in notes:
        print("  note: " + n)
    for u in unreadable:
        print("  not checked (aapt2 cannot read it): " + u)
    if problems:
        print(f"\nPREFLIGHT FAILED: {len(problems)} problem(s)")
        for p in problems:
            print("  " + p)
        return 1
    print("\nPREFLIGHT PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
