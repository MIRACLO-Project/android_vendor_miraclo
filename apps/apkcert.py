#!/usr/bin/env python3
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
"""Prints facts about an APK that fetch-apps.sh needs, with no outside tools:
  cert   SHA-256 of the signing certificate (from the APK Signature Scheme v3 or v2 block)
  jni    the arm64 native libraries that are stored compressed (Android cannot load those
         from a system app, so the build installs them next to the APK)
Usage: apkcert.py cert|jni <file.apk>"""
import hashlib, struct, sys, zipfile

V2, V3, V31 = 0x7109871A, 0xF05368C0, 0x1B93AD61


def blocks(data):
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise SystemExit("not a zip file")
    cd = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd - 16:cd] != b"APK Sig Block 42":
        raise SystemExit("no APK signing block (v1-only signature)")
    size = struct.unpack_from("<Q", data, cd - 24)[0]
    pos, end = cd - size - 8 + 8, cd - 24
    out = {}
    while pos < end:
        n, ident = struct.unpack_from("<QI", data, pos)
        out[ident] = data[pos + 12:pos + 8 + n]
        pos += 8 + n
    return out


def seq(b):
    """Length-prefixed items of a length-prefixed sequence."""
    n = struct.unpack_from("<I", b, 0)[0]
    pos, end, items = 4, 4 + n, []
    while pos < end:
        m = struct.unpack_from("<I", b, pos)[0]
        items.append(b[pos + 4:pos + 4 + m])
        pos += 4 + m
    return items


def cert(path):
    data = open(path, "rb").read()
    bl = blocks(data)
    raw = bl.get(V3) or bl.get(V31) or bl.get(V2)
    if raw is None:
        raise SystemExit("no v2/v3 signature")
    signer = seq(raw)[0]
    sd_len = struct.unpack_from("<I", signer, 0)[0]
    sd = signer[4:4 + sd_len]
    d_len = struct.unpack_from("<I", sd, 0)[0]
    certs = seq(sd[4 + d_len:])
    return certs[0]


def jni(path):
    z = zipfile.ZipFile(path)
    return [i.filename for i in z.infolist()
            if i.filename.startswith("lib/arm64-v8a/") and i.filename.endswith(".so") and i.compress_type != 0]


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("cert", "jni"):
        raise SystemExit(__doc__)
    if sys.argv[1] == "cert":
        print(hashlib.sha256(cert(sys.argv[2])).hexdigest())
    else:
        for f in jni(sys.argv[2]):
            print(f)
