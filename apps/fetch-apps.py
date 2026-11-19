#!/usr/bin/env python3
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
"""The open-source apps Mira ships, from each project's own official releases.

Downloads each app (the newest release with --update, or the version in apps.lock), checks it
is signed by the same developer key as before (apps.lock keeps the SHA-256 of each signing
certificate, and of each file), and writes the build rules:
  prebuilt/Android.bp   the apps, as presigned prebuilts in /product
  prebuilt/jni.mk       native libraries an APK stores compressed: Android cannot load those
                        from a system app, so the build installs them next to the APK
apps.mk (in git) chooses which apps each variant ships.

Usage: fetch-apps.py [--update]     (run from anywhere; set GITHUB_TOKEN to avoid rate limits)"""
import hashlib, json, os, re, shutil, subprocess, sys, tempfile, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import apkcert  # noqa: E402

PRE = os.path.join(HERE, "prebuilt")
LOCK = os.path.join(HERE, "apps.lock")
CACHE = os.environ.get("MIRACLO_CACHE", os.path.expanduser("~/gapps-cache")) + "/apps"

# module, where it comes from, which release file, AOSP apps it replaces
APPS = [
    ("MiraFossifyPhone",       "github:FossifyOrg/Phone",        r"-foss-release\.apk$",            ["Dialer"]),
    ("MiraFossifyMessages",    "github:FossifyOrg/Messages",     r"-foss-release\.apk$",            ["messaging"]),
    ("MiraFossifyContacts",    "github:FossifyOrg/Contacts",     r"-foss-release\.apk$",            ["Contacts"]),
    ("MiraFossifyCalendar",    "github:FossifyOrg/Calendar",     r"-foss-release\.apk$",            ["Calendar"]),
    ("MiraFossifyClock",       "github:FossifyOrg/Clock",        r"-foss-release\.apk$",            ["DeskClock"]),
    ("MiraFossifyCalculator",  "github:FossifyOrg/Calculator",   r"-foss-release\.apk$",            []),
    ("MiraFossifyFileManager", "github:FossifyOrg/File-Manager", r"-foss-release\.apk$",            []),
    ("MiraAves",               "github:deckerst/aves",           r"^app-libre-arm64-v8a-release\.apk$", ["Gallery2"]),
    ("MiraGramophone",         "github:FoedusProgramme/Gramophone", r"^Gramophone-[^-]+-release\.apk$", ["Music"]),
    ("MiraHeliBoard",          "github:HeliBorg/HeliBoard",      r"^HeliBoard_[^-]+-release\.apk$", ["LatinIME"]),
    ("MiraCromite",            "github:uazo/cromite",            r"^arm64_ChromePublic\.apk$",      ["Browser2", "QuickSearchBox"]),
    ("MiraOpenCamera",         "sourceforge:opencamera",         r"OpenCamera\.apk$",               ["Camera2", "jetpack-camera-app"]),
]

# Signing certificates checked against each project's official release (SHA-256).
FOSSIFY = "affdb124d3f4720c2f98dbca9eacba0514fba4306e20a2786c861c3c0d6ff292"
KNOWN_CERTS = {
    "MiraFossifyPhone": FOSSIFY, "MiraFossifyMessages": FOSSIFY, "MiraFossifyContacts": FOSSIFY,
    "MiraFossifyCalendar": FOSSIFY, "MiraFossifyClock": FOSSIFY, "MiraFossifyCalculator": FOSSIFY,
    "MiraFossifyFileManager": FOSSIFY,
    "MiraAves": "f4794727e45416d87c487138292b709f11d1cd7dab5584d8c6857d92e066604b",
    "MiraGramophone": "f451197ad7b80bd1bc981ba38a2c49d471856fb38bcc333676d6e8f8f3ce5d6e",
    "MiraHeliBoard": "5ec0a5313aa43558ee75b20b58ccd8194cdbf066df94a43ba288d933d60b86ce",
    "MiraCromite": "633fa41d8211d6d0916a819b89668c6de92e64232da67f9d16fd81c3b7e923ff",
}


def get(url, accept=None):
    req = urllib.request.Request(url, headers={"User-Agent": "miraclo-build"})
    if accept:
        req.add_header("Accept", accept)
    tok = os.environ.get("GITHUB_TOKEN")
    if tok and "api.github.com" in url:
        req.add_header("Authorization", "Bearer " + tok)
    return urllib.request.urlopen(req, timeout=120)


def latest(source, pattern):
    """(version, download URL) of the newest official release."""
    kind, name = source.split(":", 1)
    if kind == "github":
        rel = json.load(get("https://api.github.com/repos/%s/releases/latest" % name, "application/vnd.github+json"))
        assets = [a for a in rel["assets"] if re.search(pattern, a["name"])]
        if len(assets) != 1:
            raise SystemExit("%s %s: expected one release file matching %s, found %s"
                             % (name, rel["tag_name"], pattern, [a["name"] for a in rel["assets"]]))
        return rel["tag_name"], assets[0]["browser_download_url"]
    if kind == "sourceforge":
        final = subprocess.run(["curl", "-fsSIL", "-o", "/dev/null", "-w", "%{url_effective}",
                                "https://sourceforge.net/projects/%s/files/latest/download" % name],
                               capture_output=True, text=True, check=True).stdout
        m = re.search(r"/v_([0-9_]+)/", final)
        if not m or not re.search(pattern, final.split("?")[0]):
            raise SystemExit("%s: unexpected latest download %s" % (name, final))
        ver = m.group(1).replace("_", ".")
        return ver, "https://sourceforge.net/projects/%s/files/v_%s/OpenCamera.apk/download" % (name, m.group(1))
    raise SystemExit("unknown source " + source)


def download(url, dst):
    """curl, as prepare-gapps.sh uses: it follows GitHub's and SourceForge's download redirects."""
    tmp = dst + ".part"
    subprocess.run(["curl", "-fL", "--retry", "3", "-sS", "-o", tmp, url], check=True)
    os.replace(tmp, dst)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def apksigner_ok(path):
    """Full signature check with the build's apksigner when it is there."""
    top = os.path.abspath(os.path.join(HERE, "../../.."))
    for c in (top + "/out/host/linux-x86/bin/apksigner", shutil.which("apksigner")):
        if c and os.path.exists(c):
            r = subprocess.run([c, "verify", path], capture_output=True, text=True)
            if r.returncode != 0:
                raise SystemExit("%s: signature check failed\n%s" % (path, r.stderr or r.stdout))
            return True
    return False


def main():
    update = "--update" in sys.argv[1:]
    lock = json.load(open(LOCK)) if os.path.exists(LOCK) else {}
    os.makedirs(CACHE, exist_ok=True)
    os.makedirs(PRE, exist_ok=True)
    verified = False
    added = 0
    for mod, src, pat, _ in APPS:
        ent = lock.get(mod)
        dst_dir = os.path.join(PRE, mod)
        dst = os.path.join(dst_dir, mod + ".apk")
        if ent and not update and os.path.exists(dst) and sha256(dst) == ent["sha256"]:
            print("  %-24s %-14s ok" % (mod, ent["version"][:14]))
            continue
        if ent and not update:
            ver, url = ent["version"], ent["url"]
        else:
            ver, url = latest(src, pat)
        cached = os.path.join(CACHE, "%s-%s.apk" % (mod, re.sub(r"[^A-Za-z0-9._-]", "_", ver)))
        if not os.path.exists(cached):
            print("  %-24s %-14s downloading" % (mod, ver[:14]))
            download(url, cached)
        digest = sha256(cached)
        if ent and not update and digest != ent["sha256"]:
            os.remove(cached)
            raise SystemExit("%s %s: the download is not the file in apps.lock" % (mod, ver))
        cert = hashlib.sha256(apkcert.cert(cached)).hexdigest()
        want = KNOWN_CERTS.get(mod) or (ent or {}).get("cert")
        if want and cert != want:
            os.remove(cached)
            raise SystemExit("%s %s: signed by a different key (%s), stopped" % (mod, ver, cert))
        verified = apksigner_ok(cached) or verified
        os.makedirs(dst_dir, exist_ok=True)
        shutil.copy(cached, dst)
        lock[mod] = {"version": ver, "url": url, "sha256": digest, "cert": cert,
                     "jni": apkcert.jni(cached)}
        added += 1
        print("  %-24s %-14s added (key %s…)" % (mod, ver[:14], cert[:16]))
    json.dump(lock, open(LOCK, "w"), indent=1, sort_keys=True)
    open(LOCK, "a").write("\n")
    if added and not verified:
        print("  (signatures: key pinned; full check runs once out/host has apksigner)")
    write_rules(lock)


def write_rules(lock):
    """Every app is a presigned, preprocessed prebuilt (installed exactly as released, so its
    signature stays valid). Native libraries an APK stores compressed are extracted next to it
    (/product/app/<module>/lib/arm64), where Android looks for a system app's libraries."""
    import zipfile
    bp = ["// Generated by fetch-apps.py. Do not edit.", ""]
    mk = ["# Generated by fetch-apps.py. Do not edit.",
          "# Native libraries that the APKs store compressed, beside each app.", ""]
    copies = []
    for mod, src, _, over in APPS:
        bp += ["android_app_import {",
               '    name: "%s",' % mod,
               '    apk: "%s/%s.apk",' % (mod, mod),
               "    presigned: true,",
               "    preprocessed: true,",
               "    product_specific: true,",
               "    dex_preopt: { enabled: false },",
               "    enforce_uses_libs: false,"]
        if over:
            bp.append("    overrides: [%s]," % ", ".join('"%s"' % o for o in over))
        if lock[mod].get("jni"):
            # Libraries stay compressed in the APK (the developer's signature); Android loads
            # the copies installed beside it.
            bp.append("    skip_preprocessed_apk_checks: true,")
        bp += ["}", ""]
        libdir = os.path.join(PRE, mod, "lib", "arm64")
        shutil.rmtree(os.path.join(PRE, mod, "lib"), ignore_errors=True)
        jni = lock[mod].get("jni", [])
        if jni:
            os.makedirs(libdir)
            with zipfile.ZipFile(os.path.join(PRE, mod, mod + ".apk")) as z:
                for j in jni:
                    name = os.path.basename(j)
                    with z.open(j) as fi, open(os.path.join(libdir, name), "wb") as fo:
                        shutil.copyfileobj(fi, fo)
                    copies.append("    vendor/miraclo/apps/prebuilt/%s/lib/arm64/%s:"
                                  "$(TARGET_COPY_OUT_PRODUCT)/app/%s/lib/arm64/%s" % (mod, name, mod, name))
    if copies:
        mk += ["PRODUCT_COPY_FILES += \\"] + [c + " \\" for c in copies[:-1]] + [copies[-1], ""]
    old_mk = os.path.join(PRE, "Android.mk")
    if os.path.exists(old_mk):
        os.remove(old_mk)
    open(os.path.join(PRE, "Android.bp"), "w").write("\n".join(bp))
    open(os.path.join(PRE, "jni.mk"), "w").write("\n".join(mk))
    print("Apps ready: %d apps, %d native libraries installed beside their app" % (len(APPS), len(copies)))


if __name__ == "__main__":
    main()
