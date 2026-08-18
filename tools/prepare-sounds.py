#!/usr/bin/env python3
"""
Build this mod's sound set from Kenney's CC0 packs.

Unlike gen-textures.py, this does not compute the assets — it *sources* them, which means the
provenance has to be enforced rather than asserted. Every upstream archive is pinned by URL and
by SHA-256, and the script refuses to proceed if a download does not match. The empire's standing
rule from the aerospace provenance pass applies with full force here:

    "A license claim attached to a file is an assertion by whoever handed you the file, and it is
     exactly the field an unreliable source will edit. Verify against upstream."

So the licence is read out of each archive's own `License.txt` at build time and asserted to be
CC0. Kenney is the upstream, the licence ships inside the download, and the hash pin means a
substituted archive fails loudly instead of quietly becoming this mod's audio.

What is done to the files is deliberately small and reversible in description: pick one source
clip per event, downmix to mono, trim, fade, normalise, write Ogg Vorbis. Minecraft needs mono for
positional audio — a stereo file plays flat and non-directional, which for a missile is exactly
wrong, since hearing where it is coming from is the point.

Needs `soundfile` (pip install soundfile). Run from anywhere:

    python3 tools/prepare-sounds.py
"""
import hashlib
import io
import os
import sys
import urllib.request
import zipfile

try:
    import numpy as np
    import soundfile as sf
except ImportError:
    sys.exit("needs numpy and soundfile:  python3 -m pip install numpy soundfile")

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets",
                   "cruise_missile_program", "sounds")
CACHE = os.path.join(ROOT, "build", "kenney-cache")

# Upstream, pinned. Audited 2026-08-18; licence read from each archive's own License.txt.
PACKS = {
    "sci-fi": (
        "https://kenney.nl/media/pages/assets/sci-fi-sounds/"
        "6b296f9ecf-1677589334/kenney_sci-fi-sounds.zip",
        "119340f351a5098ad814f78719438c0da355a9ce8a4c8a3af6a8d48aa3d49e04",
    ),
    "interface": (
        "https://kenney.nl/media/pages/assets/interface-sounds/"
        "fa43c1dd4d-1677589452/kenney_interface-sounds.zip",
        "f2193d072726d6758a5f7871b2dcc54dcce0d5c35c6f0a62f92549b327c81232",
    ),
}

# event name -> (pack, source file, start s, length s, gain, fade-in s, fade-out s)
#
# The slices are chosen so each event is the right LENGTH for what it marks, not merely the right
# character. A launch is a punch; a flyby has to be long enough to survive the missile crossing a
# player's hearing range; a UI confirm has to be over before the next click.
EVENTS = {
    "launch":     ("sci-fi",    "thrusterFire_002.ogg",           0.00, 2.60, 1.00, 0.005, 0.45),
    "flyby":      ("sci-fi",    "spaceEngineLow_002.ogg",         1.20, 2.00, 0.75, 0.25,  0.35),
    "impact":     ("sci-fi",    "lowFrequency_explosion_000.ogg", 0.00, 1.90, 1.00, 0.002, 0.30),
    "console":    ("interface", "bong_001.ogg",                   0.00, 0.90, 0.70, 0.002, 0.10),
    "target_set": ("interface", "confirmation_001.ogg",           0.00, 0.90, 0.80, 0.002, 0.08),
    "armed":      ("interface", "confirmation_003.ogg",           0.00, 1.10, 0.85, 0.002, 0.10),
    "denied":     ("interface", "error_002.ogg",                  0.00, 0.90, 0.80, 0.002, 0.08),
}


def fetch(name, url, expected_sha256):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, os.path.basename(url))
    if not os.path.exists(path):
        print(f"  downloading {name} …")
        with urllib.request.urlopen(url) as response, open(path, "wb") as out:
            out.write(response.read())
    digest = hashlib.sha256(open(path, "rb").read()).hexdigest()
    if digest != expected_sha256:
        sys.exit(f"REFUSING: {name} sha256 {digest}\n"
                 f"          expected {expected_sha256}\n"
                 f"          the pinned archive changed; verify upstream before trusting it.")
    return path


def assert_cc0(name, path):
    with zipfile.ZipFile(path) as z:
        licence = z.read("License.txt").decode("utf-8", "replace")
    if "Creative Commons Zero" not in licence or "publicdomain/zero" not in licence:
        sys.exit(f"REFUSING: {name} does not declare CC0 in its own License.txt")
    print(f"  {name}: CC0 confirmed from the archive's own License.txt")
    return licence


def slice_clip(data, rate, start, length, gain, fade_in, fade_out):
    if data.ndim > 1:                      # Minecraft positional audio is mono only.
        data = data.mean(axis=1)
    a = int(start * rate)
    b = min(len(data), a + int(length * rate))
    clip = np.array(data[a:b], dtype=np.float64)
    if clip.size == 0:
        raise ValueError("empty slice")

    n_in = min(int(fade_in * rate), clip.size // 2)
    n_out = min(int(fade_out * rate), clip.size // 2)
    if n_in > 0:
        clip[:n_in] *= np.linspace(0.0, 1.0, n_in)
    if n_out > 0:
        clip[-n_out:] *= np.linspace(1.0, 0.0, n_out)

    peak = np.abs(clip).max()
    if peak > 0:
        clip *= (gain * 0.97) / peak       # normalise, then apply the event's own level
    return clip


def main():
    os.makedirs(OUT, exist_ok=True)
    archives = {}
    for name, (url, sha) in PACKS.items():
        path = fetch(name, url, sha)
        assert_cc0(name, path)
        archives[name] = zipfile.ZipFile(path)

    print()
    for event, (pack, source, start, length, gain, fi, fo) in EVENTS.items():
        raw = archives[pack].read(f"Audio/{source}")
        data, rate = sf.read(io.BytesIO(raw), always_2d=False)
        clip = slice_clip(data, rate, start, length, gain, fi, fo)
        dest = os.path.join(OUT, f"{event}.ogg")
        sf.write(dest, clip, rate, format="OGG", subtype="VORBIS")
        print(f"  {event:11s} <- {pack}/{source:32s} "
              f"{len(clip)/rate:4.2f}s mono {os.path.getsize(dest):6d} B")

    print(f"\nwrote {len(EVENTS)} sounds to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
