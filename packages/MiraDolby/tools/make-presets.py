#!/usr/bin/env python3
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
"""Makes assets/presets.json from the EasyEffects presets (github.com/JackHack96/EasyEffects-Presets).
The equalizer of each preset becomes Mira Dolby's ten bands; the other effects become the closest
Dolby effects, and those presets carry the "Close match" tag.
Usage: make-presets.py <EasyEffects-Presets checkout> <MiraDolby/assets/presets.json>"""
import json, math, glob, sys, os
SRC = sys.argv[1]; OUT = sys.argv[2]
FS = 48000.0
C10 = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]

def biquad(t, f0, g, q):
    A = 10 ** (g / 40); w = 2 * math.pi * f0 / FS; a = math.sin(w) / (2 * q); c = math.cos(w)
    if t == "Bell":
        return [1 + a * A, -2 * c, 1 - a * A], [1 + a / A, -2 * c, 1 - a / A]
    if t == "Hi-pass":
        return [(1 + c) / 2, -(1 + c), (1 + c) / 2], [1 + a, -2 * c, 1 - a]
    if t == "Lo-pass":
        return [(1 - c) / 2, 1 - c, (1 - c) / 2], [1 + a, -2 * c, 1 - a]
    raise SystemExit("filter " + t)

def mag_db(filters, f):
    w = 2 * math.pi * f / FS; tot = 0.0
    for b, a in filters:
        z = complex(math.cos(w), -math.sin(w)); z2 = z * z
        h = (b[0] + b[1] * z + b[2] * z2) / (a[0] + a[1] * z + a[2] * z2)
        tot += 20 * math.log10(abs(h))
    return tot

INFO = {
 "Perfect EQ": ("Warm lows and clear highs, with a slightly softer midrange", ["EQ"], {}),
 "Boosted": ("Punchy lows and bright highs for music and films", ["EQ"], {}),
 "Dolby Atmos": ("Wide and immersive, with depth and air", ["EQ", "Bass", "Width", "Space"],
                 {"bassEnh": True, "virt": True, "width": 80}),
 "Bass Enhancing + Perfect EQ": ("Perfect EQ with fuller bass and surround", ["EQ", "Bass", "Surround"],
                 {"bassEnh": True, "virt": True}),
 "Bass Enhancing + Perfect EQ - Low Latency": ("The low-latency version of Bass Enhancing + Perfect EQ",
                 ["EQ", "Bass", "Surround"], {"bassEnh": True, "virt": True}),
 "Bass Boosted": ("Perfect EQ with heavy bass, surround and extra loudness", ["EQ", "Bass", "Surround", "Loudness"],
                 {"bassEnh": True, "virt": True, "lev": True, "bass": 3.0}),
 "Loudness+Autogain": ("Steady volume with a warmer, fuller sound", ["EQ", "Loudness"],
                 {"lev": True, "width": 35}),
 "Advanced Auto Gain": ("Steady volume with a fuller sound and extra sparkle", ["EQ", "Loudness", "Treble"],
                 {"lev": True, "treble": 1.5}),
 "Speaker Sync": ("A light, natural tune with crossfeed for relaxed listening", ["EQ", "Width"], {"width": 35}),
}
ORDER = ["Perfect EQ", "Dolby Atmos", "Bass Enhancing + Perfect EQ", "Bass Enhancing + Perfect EQ - Low Latency",
         "Boosted", "Bass Boosted", "Loudness+Autogain", "Advanced Auto Gain", "Speaker Sync"]
# Parts of a preset that Mira Dolby runs exactly (the EQ) and the ones it can only come close to.
EXACT = {"equalizer"}
out = []
names = sorted(os.path.splitext(os.path.basename(f))[0] for f in glob.glob(SRC + "/*.json"))
assert sorted(ORDER) == names, (names, ORDER)
for name in ORDER:
    d = json.load(open(SRC + "/" + name + ".json"))["output"]
    plugins = [p.split("#")[0] for p in d["plugins_order"]]
    eqk = next(k for k in d if k.startswith("equalizer"))
    L = d[eqk]["left"]; n = d[eqk]["num-bands"]
    bands = [(L["band%d" % i]["type"], L["band%d" % i]["frequency"], L["band%d" % i]["gain"], L["band%d" % i]["q"]) for i in range(n)]
    # Ten bells at (about) the ten band centres: the gains are the values EasyEffects shows.
    if n == 10 and all(b[0] == "Bell" and 1 / 1.3 < b[1] / c < 1.3 for b, c in zip(bands, C10)):
        eq = [b[2] for b in bands]
    else:
        flt = [biquad(t, f, g, q) for t, f, g, q in bands]
        eq = [round(mag_db(flt, f) * 2) / 2 for f in C10]
    desc, tags, fx = INFO[name]
    close = any(p not in EXACT for p in plugins)
    tune = {"virt": False, "de": False, "lev": False, "bassEnh": False, "bass": 0.0, "treble": 0.0, "ieq": 0}
    tune.update(fx)
    out.append({"name": name, "desc": desc, "tags": tags, "close": close,
                "eq": [round(float(x), 1) for x in eq], "fx": tune, "source": name + ".json"})
    print(name, close, out[-1]["eq"])
json.dump({"source": "https://github.com/JackHack96/EasyEffects-Presets", "presets": out}, open(OUT, "w"), indent=1)
