#!/usr/bin/env python3
"""
Generate every cruise_missile_program texture procedurally.

All art here is original and computed: nothing is traced, sampled or adapted. Each texture is a
short program describing plate, panel, stripe and stencil, evaluated over a grid. Re-run it and
you get byte-identical files, so the art is reproducible as well as original.

Standard library only (zlib + struct); no dependencies.
"""
import struct, zlib, os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "cruise_missile_program", "textures")


def png(path, px):
    """Write an RGBA PNG, taking its size FROM THE PIXEL ARRAY.

    Cosmos' generator once hardcoded 16 for both the header and the row loop, which silently
    truncated every larger image to its top-left 16x16 corner. Block and item textures are all
    16x16, so nothing complained for months - and then a 64x64 entity sheet went through it and
    came out as a corner. The size is read from the data here for exactly that reason.
    """
    height = len(px)
    width = len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(width))
                   for y in range(height))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)   # 8-bit RGBA
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(blob)
    return path, width, height


def grid(w, h, c=(0, 0, 0, 0)):
    return [[list(c) for _ in range(w)] for _ in range(h)]


def shade(c, f):
    return [max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), c[3]]


def hsh(x, y, salt=0):
    """Deterministic value hash, so 'noise' is reproducible rather than random."""
    n = (x * 374761393 + y * 668265263 + salt * 1442695040888963407) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def rect(px, x0, y0, w, h, c):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if 0 <= y < len(px) and 0 <= x < len(px[0]):
                px[y][x] = list(c)


# ---- the palette --------------------------------------------------------
# A cruise missile is a low-visibility object; the whole family is desaturated grey-green so it
# reads against terrain rather than against sky, which is the opposite of the rocket family.
HULL = (126, 133, 128, 255)
HULL_LIT = (158, 165, 158, 255)
HULL_DARK = (92, 98, 95, 255)
BAND = (74, 96, 82, 255)
STENCIL = (196, 204, 198, 255)
INTAKE = (58, 62, 62, 255)
WARN = (188, 142, 60, 255)

CONSOLE = (44, 62, 54, 255)
CONSOLE_LIT = (68, 94, 80, 255)
SCREEN = (46, 133, 90, 255)
STEEL = (104, 110, 112, 255)
STEEL_LIT = (146, 152, 154, 255)
STEEL_DARK = (72, 78, 80, 255)


# ---- the entity sheet ---------------------------------------------------

def cruise_missile_sheet():
    """The missile's own 64x64 sheet. Every region matches CruiseMissileModel's texOffs.

    Regions, solved from each box's size (sx, sy, sz) -> 2*(sz+sx) wide, sz+sy tall:
        body  (4,4,22) at ( 0, 0) -> 52 x 26
        nose  (3,3,3)  at (52, 0) -> 12 x  6
        tip   (2,2,2)  at (52, 6) ->  8 x  4
        intake(3,2,9)  at ( 0,26) -> 24 x 11
        wing  (11,1,7) at (24,26) -> 36 x  8
        tailh (12,1,4) at ( 0,38) -> 32 x  5
        tailv (1,5,4)  at (32,38) -> 10 x  9
    """
    W = H = 64
    px = grid(W, H)

    # Body: panel lines along the tube, a warning band at the warhead station, a lighter top.
    rect(px, 0, 0, 52, 26, HULL)
    for y in range(26):
        for x in range(52):
            n = hsh(x, y, 3)
            if n > 0.93:
                px[y][x] = shade(HULL, 1.10)
            elif n < 0.07:
                px[y][x] = shade(HULL, 0.92)
    # Longitudinal panel seams.
    for x in range(52):
        px[0][x] = list(HULL_LIT)
        px[25][x] = list(HULL_DARK)
    # The warhead band, two rings near the nose end of the body wrap.
    rect(px, 14, 0, 2, 26, BAND)
    rect(px, 40, 0, 2, 26, BAND)
    # A stencilled serial, four ticks. Legible as "writing" at range without being letters.
    for i in range(4):
        rect(px, 22 + i * 3, 11, 1, 3, STENCIL)

    # Nose: brighter, it catches the light first.
    rect(px, 52, 0, 12, 6, HULL_LIT)
    rect(px, 52, 0, 12, 1, STENCIL)
    # Tip: darkest, a seeker window.
    rect(px, 52, 6, 8, 4, shade(HULL_DARK, 0.8))

    # Intake: a dark duct with a lit lip.
    rect(px, 0, 26, 24, 11, INTAKE)
    rect(px, 0, 26, 24, 1, shade(INTAKE, 1.7))
    rect(px, 0, 36, 24, 1, shade(INTAKE, 0.7))

    # Wing: lighter upper surface, dark leading edge, one warning chevron.
    rect(px, 24, 26, 36, 8, HULL)
    rect(px, 24, 26, 36, 1, HULL_LIT)
    rect(px, 24, 33, 36, 1, HULL_DARK)
    rect(px, 30, 29, 4, 1, WARN)
    rect(px, 48, 29, 4, 1, WARN)

    # Tail surfaces.
    rect(px, 0, 38, 32, 5, HULL)
    rect(px, 0, 38, 32, 1, HULL_LIT)
    rect(px, 32, 38, 10, 9, HULL)
    rect(px, 32, 38, 10, 1, HULL_LIT)
    rect(px, 34, 41, 6, 2, BAND)

    return png(os.path.join(OUT, "entity", "cruise_missile.png"), px)


# ---- items --------------------------------------------------------------

def cruise_missile_body_item():
    """A 16x16 side-on silhouette: slim tube, swept wing, tail fin. Reads at inventory size."""
    px = grid(16, 16)
    # Fuselage, nose to the right.
    rect(px, 2, 7, 11, 3, HULL)
    rect(px, 2, 7, 11, 1, HULL_LIT)
    rect(px, 2, 9, 11, 1, HULL_DARK)
    # Nose taper.
    rect(px, 13, 8, 1, 2, HULL_LIT)
    rect(px, 14, 8, 1, 1, STENCIL)
    # Wing, swept back and down.
    rect(px, 6, 10, 5, 1, HULL_DARK)
    rect(px, 7, 11, 3, 1, HULL_DARK)
    # Tail fin.
    rect(px, 2, 4, 2, 3, HULL)
    rect(px, 2, 4, 2, 1, HULL_LIT)
    # Intake under the belly.
    rect(px, 8, 10, 3, 1, INTAKE)
    # Warhead band.
    rect(px, 11, 7, 1, 3, BAND)
    return png(os.path.join(OUT, "item", "cruise_missile_body.png"), px)


def conventional_warhead_item():
    """A blunt cone with a banded collar. Deliberately unlike the body so a loaded round reads."""
    px = grid(16, 16)
    rect(px, 5, 3, 6, 2, shade(HULL_DARK, 0.9))
    rect(px, 4, 5, 8, 6, HULL)
    rect(px, 4, 5, 8, 1, HULL_LIT)
    rect(px, 4, 10, 8, 1, HULL_DARK)
    # The collar that mates with a body.
    rect(px, 3, 11, 10, 2, STEEL)
    rect(px, 3, 11, 10, 1, STEEL_LIT)
    # Hazard banding.
    for i in range(4):
        rect(px, 4 + i * 2, 7, 1, 2, WARN)
    return png(os.path.join(OUT, "item", "conventional_warhead.png"), px)


# ---- blocks -------------------------------------------------------------

def plated(base, lit, dark, seam_every=8):
    px = grid(16, 16)
    for y in range(16):
        for x in range(16):
            c = base
            n = hsh(x, y, 11)
            if n > 0.94:
                c = lit
            elif n < 0.06:
                c = dark
            if x % seam_every == 0 or y % seam_every == 0:
                c = dark
            px[y][x] = list(c)
    # Rivets at the plate corners.
    for y in range(0, 16, seam_every):
        for x in range(0, 16, seam_every):
            px[(y + 1) % 16][(x + 1) % 16] = list(lit)
    return px


def console_front():
    """The console's working face: a green phosphor readout with a roster on it.

    A control block wants one face you read and five you do not, which is why this is three
    textures and a facing property rather than one texture on a cube. The first version put an
    identical screen on all six sides and the block read as a decorative box rather than
    something with a front.
    """
    px = plated(CONSOLE, CONSOLE_LIT, shade(CONSOLE, 0.7), 8)
    # Recessed bezel, then the display.
    rect(px, 2, 2, 12, 9, shade(CONSOLE, 0.42))
    rect(px, 2, 2, 12, 1, shade(CONSOLE, 0.30))
    rect(px, 3, 3, 10, 7, shade(SCREEN, 0.22))
    # Four roster rows of varying length - it should read as a list, not a pattern.
    for row, width in enumerate((7, 5, 6, 3)):
        rect(px, 4, 4 + row * 2, width, 1, shade(SCREEN, 0.75 + 0.08 * row))
    # The selection cursor sits on one row.
    rect(px, 12, 6, 1, 1, shade(SCREEN, 1.6))
    # Status lamps along the bottom rail: armed, link, power.
    rect(px, 0, 12, 16, 4, shade(CONSOLE, 0.62))
    rect(px, 2, 13, 2, 2, shade(WARN, 1.0))
    rect(px, 7, 13, 2, 2, shade(SCREEN, 1.4))
    rect(px, 12, 13, 2, 2, shade(SCREEN, 0.55))
    return png(os.path.join(OUT, "block", "fire_control_console_front.png"), px)


def console_side():
    """Plain armoured flank. Cable conduit down one edge so the sides are not featureless."""
    px = plated(CONSOLE, CONSOLE_LIT, shade(CONSOLE, 0.7), 8)
    rect(px, 11, 0, 3, 16, shade(CONSOLE, 0.58))
    for y in range(1, 16, 3):
        rect(px, 11, y, 3, 1, shade(CONSOLE, 0.78))
    rect(px, 0, 12, 16, 4, shade(CONSOLE, 0.62))
    return png(os.path.join(OUT, "block", "fire_control_console_side.png"), px)


def console_top():
    """Cooling vents. What the top of a machine that runs hot looks like from above."""
    px = plated(CONSOLE, CONSOLE_LIT, shade(CONSOLE, 0.7), 8)
    for i in range(4):
        rect(px, 3, 2 + i * 3, 10, 2, shade(CONSOLE, 0.40))
        rect(px, 3, 2 + i * 3, 10, 1, shade(CONSOLE, 0.30))
    return png(os.path.join(OUT, "block", "fire_control_console_top.png"), px)


def launch_tube_side():
    """Four capped tubes in a frame, with a hazard rail along the bottom."""
    px = plated(STEEL, STEEL_LIT, STEEL_DARK, 8)
    for i in range(4):
        x = 1 + i * 4
        # Tube body, then a lit upper lip and a shadowed lower one so it reads as a cylinder.
        rect(px, x, 3, 3, 9, shade(STEEL_DARK, 0.55))
        rect(px, x, 3, 3, 1, STEEL_LIT)
        rect(px, x, 11, 3, 1, shade(STEEL_DARK, 0.35))
        rect(px, x + 1, 4, 1, 7, shade(STEEL_DARK, 0.75))
    # Hazard rail: alternating stripes, the universal "this end is dangerous" language.
    for x in range(16):
        rect(px, x, 13, 1, 3, WARN if (x // 2) % 2 == 0 else shade(STEEL_DARK, 0.8))
    return png(os.path.join(OUT, "block", "launch_tube.png"), px)


def launch_tube_top():
    """Looking down four open tubes. The face a player sees a missile leave."""
    px = plated(STEEL, STEEL_LIT, STEEL_DARK, 8)
    for i in range(2):
        for j in range(2):
            x, y = 2 + i * 7, 2 + j * 7
            rect(px, x, y, 5, 5, shade(STEEL_DARK, 0.62))
            rect(px, x, y, 5, 1, STEEL_LIT)
            rect(px, x + 1, y + 1, 3, 3, shade(STEEL_DARK, 0.28))
            # A round in the tube, seen end-on.
            rect(px, x + 2, y + 2, 1, 1, shade(BAND, 1.2))
    return png(os.path.join(OUT, "block", "launch_tube_top.png"), px)


def launch_tube_bottom():
    """The mounting plate. Dull on purpose; nobody looks at it, but a shared side texture there
    made the block look like it floated."""
    px = plated(shade(STEEL, 0.8), STEEL, STEEL_DARK, 4)
    return png(os.path.join(OUT, "block", "launch_tube_bottom.png"), px)


if __name__ == "__main__":
    for path, w, h in [cruise_missile_sheet(), cruise_missile_body_item(),
                       conventional_warhead_item(),
                       console_front(), console_side(), console_top(),
                       launch_tube_side(), launch_tube_top(), launch_tube_bottom()]:
        print(f"{w}x{h}  {os.path.relpath(path, os.path.dirname(__file__))}")
