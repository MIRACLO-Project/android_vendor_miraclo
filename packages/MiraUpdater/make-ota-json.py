#!/usr/bin/env python3
"""Makes the update file Mira Updater reads, from a full A/B OTA zip.
Run on the SERVER after a build:
  python3 vendor/miraclo/packages/MiraUpdater/make-ota-json.py OTA.zip DOWNLOAD_URL "Change one" "Change two" > marble.json
Then put marble.json in the MiraCLO-OS/ota repo (main branch) and the zip at DOWNLOAD_URL
(e.g. a GitHub release asset). Phones on an older build see the update."""
import sys, zipfile, hashlib, json, os

if len(sys.argv) < 3:
    print(__doc__); sys.exit(2)
path, url, notes = sys.argv[1], sys.argv[2], sys.argv[3:]
z = zipfile.ZipFile(path)
info = z.getinfo("payload.bin")
with open(path, "rb") as f:
    f.seek(info.header_offset + 26)
    name_len = int.from_bytes(f.read(2), "little")
    extra_len = int.from_bytes(f.read(2), "little")
offset = info.header_offset + 30 + name_len + extra_len
props = z.read("payload_properties.txt").decode().strip().splitlines()
meta = {}
for line in z.read("META-INF/com/android/metadata").decode().splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        meta[k] = v
h = hashlib.sha256()
with open(path, "rb") as f:
    for chunk in iter(lambda: f.read(1 << 20), b""):
        h.update(chunk)
print(json.dumps({
    "version": os.environ.get("MIRACLO_VERSION", meta.get("post-build-incremental", "")),
    "codename": os.environ.get("MIRACLO_CODENAME", "Aurora"),
    "datetime": int(meta.get("post-timestamp", "0")),
    "size": os.path.getsize(path),
    "url": url,
    "sha256": h.hexdigest(),
    "payload_offset": offset,
    "payload_size": info.file_size,
    "payload_properties": props,
    "changelog": notes,
}, indent=1))
