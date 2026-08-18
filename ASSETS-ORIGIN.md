# ASSETS-ORIGIN.md

Where every asset in this mod came from, and how that was checked rather than assumed.

The standing rule this file exists to satisfy was written during the aerospace campaign's
provenance pass, after a local "quarry" turned out to be 100% laundered third-party material with
every licence field edited:

> **A licence claim attached to a file is an assertion by whoever handed you the file, and it is
> exactly the field an unreliable source will edit. Verify against upstream.**

So nothing here is trusted because a filename or a web page said so.

---

## Audio — sourced, CC0, hash-pinned

**Upstream: Kenney (kenney.nl).** One publisher, blanket CC0, licence text bundled inside every
archive. Audited **2026-08-18**.

| Pack | URL | SHA-256 |
|---|---|---|
| Sci-Fi Sounds | `kenney.nl/media/pages/assets/sci-fi-sounds/6b296f9ecf-1677589334/kenney_sci-fi-sounds.zip` | `119340f351a5098ad814f78719438c0da355a9ce8a4c8a3af6a8d48aa3d49e04` |
| Interface Sounds | `kenney.nl/media/pages/assets/interface-sounds/fa43c1dd4d-1677589452/kenney_interface-sounds.zip` | `f2193d072726d6758a5f7871b2dcc54dcce0d5c35c6f0a62f92549b327c81232` |

**How the licence was verified.** Not from the download page. `tools/prepare-sounds.py` reads
`License.txt` *out of each archive* at build time and refuses to proceed unless it contains both
"Creative Commons Zero" and a `publicdomain/zero` URL. Both archives declare:

```
License: (Creative Commons Zero, CC0)
http://creativecommons.org/publicdomain/zero/1.0/
This content is free to use in personal, educational and commercial projects.
Support us by crediting Kenney or www.kenney.nl (this is not mandatory)
```

Attribution is not required. **It is given anyway**, here and in the release notes, because
crediting the people whose work you shipped is right whether or not a licence compels it.

**How the archives are pinned.** Each is fetched by exact URL and checked against the SHA-256
above. A changed archive fails loudly rather than quietly becoming this mod's audio. Re-running
`python3 tools/prepare-sounds.py` reproduces the shipped files byte-for-byte from upstream.

### The mapping

| event | source clip | slice | why |
|---|---|---|---|
| `launch` | `thrusterFire_002.ogg` | 0.00–2.60 s | a punch with a tail; heard across a server |
| `flyby` | `spaceEngineLow_002.ogg` | 1.20–3.20 s | mid-clip, so it starts already running |
| `impact` | `lowFrequency_explosion_000.ogg` | 0.00–1.90 s | weight under the vanilla blast's crack |
| `console` | `bong_001.ogg` | full | opening the console |
| `target_set` | `confirmation_001.ogg` | full | a target is designated |
| `armed` | `confirmation_003.ogg` | full | a strike is authorised and counting |
| `denied` | `error_002.ogg` | full | every refusal, so "no" always sounds the same |

**Processing, and why each step exists:**

- **Downmix to mono.** Minecraft plays a stereo file flat and non-directional. For a missile that
  is exactly wrong — hearing which way it is coming from is most of the value of it making a noise.
  Only `lowFrequency_explosion_000` needed this; the rest were already mono.
- **Slice to length.** Each event is cut to the duration of the thing it marks. A UI confirm has to
  be over before the next click; a flyby has to outlast the missile crossing a hearing range.
- **Fade and normalise.** Short fades prevent the click a hard cut makes at a non-zero sample;
  peak normalisation then a per-event gain keeps the set balanced against each other.

---

## Textures — computed, original, not sourced

`tools/gen-textures.py` computes every texture as a small program over a pixel grid. Nothing is
traced, sampled, or adapted, and re-running it is byte-identical.

**This was a deliberate decision, not a shortcut, and it was made after looking.** CC0 art on
offer for this niche is 2D platformer tilesets and 2K PBR panel textures — neither is 16×16
Minecraft block art, both would need reworking past the point where anything of the original
survived, and OpenGameArt's licence fields are user-supplied per upload, which is precisely the
"assertion by whoever handed you the file" the rule above warns about. Every sibling mod in this
group (cosmos especially) computes its art for the same reasons, so a sourced pack would also read
as visually foreign beside them.

If a specific look is wanted later, the generator is the place to change it, and swapping in
sourced art means replacing files rather than unpicking anything.

| texture | size | notes |
|---|---|---|
| `entity/cruise_missile.png` | 64×64 | UV regions solved per box from `2*(sz+sx) × (sz+sy)` |
| `item/cruise_missile_body.png` | 16×16 | side silhouette |
| `item/conventional_warhead.png` | 16×16 | blunt cone, banded collar |
| `block/fire_control_console_{front,side,top}.png` | 16×16 | three faces; the block is directional |
| `block/launch_tube{,_top,_bottom}.png` | 16×16 | four capped tubes, hazard rail |

Still placeholder in the honest sense: no custom particles (vanilla smoke), and the console screen
is drawn in code rather than textured.

---

## Code

No third-party code is vendored. `kinetics`, `cosmos` and `warfront` are consumed as dependencies —
the latter two by reflection only, so not one of their types appears in this mod's signatures.
Design references (ICBM's frequency linking, ICBM-Classic's cruise flight profile) were read for
their pattern and reimplemented; no file, asset, or line was copied from either.
