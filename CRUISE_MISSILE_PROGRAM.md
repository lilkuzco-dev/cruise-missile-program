# CRUISE_MISSILE_PROGRAM.md

Command and control for cruise missiles. A fire control console commands launchers a thousand
blocks away over a callsign network, targets them from orbital reconnaissance, and flies a low
terrain-hugging profile to the coordinate.

**The network is the mod.** The missile is what happens at the end of a decision made somewhere
else; if the roster, the linking and the inventory-at-a-distance are not right, nothing else
matters.

---

## 1. File map

```
src/main/java/dev/lilkuzco/cruisemissileprogram/
├── CruiseMissileProgram.java     entrypoint; registers everything, wires both tick subscribers
├── CruiseBlocks/BlockEntities/Items/Menus/Entities/Components.java   registries
├── command/
│   ├── CommandRank.java          NONE < OBSERVER < SOLDIER < OFFICER < COMMANDER
│   ├── CommandRoster.java        per-console owner + player ranks (codec)
│   ├── CommandNetwork.java       SavedData: callsign -> console + launchers. THE registry.
│   ├── LauncherRecord.java       one launcher's mirrored state, readable with its chunk unloaded
│   ├── StrikeTarget.java         a coordinate plus where it came from and how old it is
│   ├── FireControlBlock(Entity)  the command centre
│   ├── StrikeTracker.java        countdowns, on END_SERVER_TICK
│   └── CruiseCommands.java       /cruise selftest, /cruise status
├── launcher/
│   ├── LaunchTubeBlock(Entity)   4-round box launcher; mirrors inventory into the network
│   └── LaunchTubeMenu.java
├── missile/
│   ├── CruiseProfile.java        the kinetics Profile — wing, mass, thrust, autopilot limits
│   ├── CruiseFlight.java         the flight, on END_SERVER_TICK. Terrain corridor + autopilot.
│   ├── CruiseMissileEntity.java  a VIEW of a kinetics body. Owns no motion.
│   ├── CruiseLaunch.java         released round -> body in the air
│   └── Detonation.java           the only place damage is applied
├── warhead/
│   ├── WarheadRegistry.java      the #cruise_missile_program:warheads tag contract
│   └── WarheadSpec.java
├── bridge/
│   ├── CosmosTargetBridge.java   satellite coordinates, reflection-only
│   └── WarfrontC2Bridge.java     strike markers on warfront's display wall, reflection-only
└── net/
    ├── CruiseNet.java            console wire protocol
    └── ConsoleActions.java       every action, re-authorised server-side

src/client/java/.../client/
├── CruiseMissileModel.java       nose-toward-+Z; UV regions solved per box
├── CruiseMissileRenderer.java    registered — see §7
├── FireControlScreen.java        roster, target, satellite picker, FIRE
├── LaunchTubeScreen.java
└── CruiseRenderTest.java         the render battery

tools/gen-textures.py             every texture, computed; size read from the pixel array
```

---

## 2. Confirmed integration points

### kinetics 0.1.4 — **hard dependency, required**

The only hard dependency, and agreed as such. Kinetics integrates every metre: drag, lift, thrust,
gravity, collision. This mod supplies steering and nothing else.

| Used | How |
|---|---|
| `Integrator.step(body, env, control, t, dt, sink)` | driven directly, once per server tick |
| `KineticBody`, `FlightPhase.BOOST -> TERMINAL` | the missile's own body and phase |
| `GuidanceLaws.waypoint` / `altitudeHold` / `purePursuit` | steering |
| `Environment.groundYBelow` / `densityAt` / `gravity` | terrain and air |
| `KineticsService.environmentOf`, `constants`, `worldTimeSeconds` | per-dimension setup |

**Not** used: `FlightDirector`'s guided mission. Its guidance is a proportional-navigation seeker
that needs a lock and drops it behind terrain — correct for an interceptor, exactly wrong for a
missile whose purpose is to fly behind terrain. `GuidanceLaws` is documented as the cheaper laws
"for bodies that do not warrant a PN seeker", and its `waypoint` javadoc literally describes
keeping a cruise missile from cornering like an interceptor. This is the intended seam.

**Gap found, worked around, not patched:** `KineticsMod.onEndTick` calls `service.tick(server,
null, null)` — targets and countermeasures are hardcoded null and there is no registration point
for a consumer to supply them, despite the comment saying consumers supply them. Any mod wanting
`Mission.GUIDED` today gets a body with no target every tick. Driving the integrator directly
side-steps this without touching the shared kinetics repo. Worth raising there separately.

### cosmos 0.2.2+ — **optional, reflection-only**

`CosmosTargetBridge`. Consumes cosmos's published contract exactly as warfront does:

- `SatelliteConstellation.of(level).ownedBy(uuid)` — the player's own satellites
- `SatellitePayload.RECON` only, with `sensorHalfAngleDeg()`
- `ReconImager.image(...)` → `Report.strongestSignals()` — **already a target list**
- `CommsCoverage.hasCoverage(level, pos)` — reported alongside the fix

One-way feed, as specified: the satellite hands down a coordinate and knows nothing further. The
missile's own terminal phase owns the final approach.

**Coverage is a window, not a state.** Cosmos is emphatic: a recon footprint crosses a point in
about 3.2 s and the next pass is a Minecraft day away. The bridge therefore asks every time and
never caches. When nothing is overhead the console shows **NO DATA** and refuses to set a target —
falling back to the last fix would silently aim a strike at wherever the satellite was looking an
orbit ago.

### warfront 0.3.0+ — **optional, reflection-only**

`WarfrontC2Bridge` registers a `TacticalOverlayRegistry.Provider` through a dynamic proxy, so no
warfront type appears in this mod's signatures and warfront's LGPL stays out of an MIT link graph.

Warfront left this door open deliberately: the registry is documented as "the radar/intel contract
for displays — Phase 2's target registry and future radar hardware register providers here", and
`Marker.Kind.TARGET` already existed **with no producer in warfront**. This mod is that producer.
An existing display wall starts showing launch tubes with no change to warfront at all.

**Known limit:** warfront caps a snapshot at 64 markers and stops calling providers once it has
that many; its two built-ins register first, so on a busy display this mod's markers are the ones
dropped.

### missile_program (the ballistic mod) — **optional, no code coupling at all**

Nothing is imported. Warheads arrive through the item tag `#cruise_missile_program:warheads` plus
data-driven stats in `data/*/cruise_warheads/*.json`, which is the same idiom cosmos uses to let
crude_empire fill its propellant tags with neither mod naming the other.

This mod ships tag entries for `missile_program`'s ladder marked `"required": false`, so the
datapack loads identically whether that mod is installed or not:

| their tier | item | our tier | accepted by a cruise body |
|---|---|---|---|
| Conventional I | `hornet_warhead` | 1 | yes |
| Conventional II | `breaker_warhead` | 2 | yes |
| Heavy I — Mini | `mini_warhead` | 3 | yes |
| Heavy II — Tactical | `tactical_warhead` | — | **no** |
| Heavy III — Mass Destruction | `mass_destruction_warhead` | — | **no** |

The cap of 3 was chosen before their ladder was read and turned out to land exactly on their own
`heavy` / silo-only split. Cruise is the precision option; ballistic is the raw-power one, and a
choice nobody would make is not a choice.

---

## 3. The network layer

**Linking is by callsign** — a normalised string, `ALPHA-1` — set on both ends. There is **no
distance limit anywhere in this mod**. Distance shows up only as signal delay on the countdown:
`1.6 * log10(blocks / 128)` seconds, capped at 6 s and zero under 128 blocks. Logarithmic so the
first kilometre reads as a real change and the tenth costs almost nothing.

One console per callsign; a second console claiming it is **refused**, not allowed to steal it.
A launcher belongs to one callsign and may be re-linked freely.

**Inventory at a distance is the load-bearing trick.** A launcher a kilometre away is in an
unloaded chunk essentially always, so the console never reads a block entity. Launchers *mirror*
their contents into `CommandNetwork` (SavedData) whenever those contents change, and the console
reads the mirror. `LauncherRecord.updatedAt` is carried so the UI can be honest about the picture's
age rather than implying it is live.

**Registration hangs off placement and removal, never `setRemoved`** — which also fires on chunk
unload and would quietly empty the roster of every launcher nobody was standing near. This repo
has paid for that lesson three times; see CLAUDE.md rule 7.

---

## 4. Authorisation

Player ranks, local to each console, and **a player rank is never overridden by an NPC one**.

| rank | view | designate / load | fire | administer |
|---|---|---|---|---|
| `NONE` | | | | |
| `OBSERVER` | ✓ | | | |
| `SOLDIER` | ✓ | ✓ | | |
| `OFFICER` | ✓ | ✓ | ✓ | |
| `COMMANDER` | ✓ | ✓ | ✓ | ✓ |

`SOLDIER` is the staffed-network rank: keep tubes loaded and a target designated so a strike is
set up the moment an officer arrives, and do none of it irreversibly.

The first player to open an unclaimed console becomes its `COMMANDER` (same shape as warfront's
display wall binding its owner on first use). Unlisted players default to `OBSERVER`; the
commander can lower that to `NONE`.

**Why not warfront's system:** warfront has no player ranks. Its `General` is an AI planner that
scores tactical templates, and its only player-facing quantity is *standing* — reputation with an
NPC faction. Nothing there can express "these four players may fire and that one may not". Every
console action is re-authorised server-side in `ConsoleActions`; the UI disabling a button is a
courtesy, not the gate.

---

## 5. The flight profile

Everything below was **measured on a live server over generated terrain**, not reasoned about.
`/cruise selftest <range>` flies one and prints altitude, ground clearance, speed, throttle and the
scanned corridor once a second.

### Final numbers

| | |
|---|---|
| Wet mass | 400 kg (240 payload + 60 stage dry + 100 fuel) |
| Wing area | **8.5 m²** — wing loading 47 kg/m², light-aircraft territory |
| Frontal area / Cd0 | 0.22 m² / 0.30 (a *body* coefficient, not a clean-wing one) |
| Sustainer | 1,500 N at Isp 1200 — about 6× cruise drag, all of it climb authority |
| Governed cruise speed | 44 m/s, raised automatically with commanded altitude |
| Cruise clearance | 26 blocks over the highest ground in the corridor |
| Look-ahead | **14 s** (~600 blocks) |
| Corridor samples | 24 columns, max not mean |
| Terminal handover | adaptive: `max(70, 1.4 × height-to-lose + 40)` |
| Proximity fuse | armed inside 48 blocks, fires at closest approach |

### Five things the trace taught that reasoning had not

1. **Wing loading picks cruise speed.** The first draft had 1.3 m² on 680 kg — a real Tomahawk's
   loading, which a real Tomahawk carries at 240 m/s. Asked to cruise at 40 it made a ninth of the
   lift it needed, arced, porpoised 55 blocks and dived in at t=7 s. No gain tuning could have
   fixed an airframe that could not hold itself up.
2. **Over-damped altitude hold flies you into the ground.** kp 0.55 / kd 1.6 gave
   `0.55×17 − 1.6×14 = −13`: the damping term outvoted the error term and commanded *down* while
   still below cruise height. Now near-critically damped, `kd ≈ 2√kp`.
3. **Two-point terrain following is not enough for Minecraft.** Kinetics' `terrainFollow` samples
   underneath and one point ahead — right for gentle real terrain. The first forest flight hit a
   tree 11 blocks out, in the gap between the two samples, with both reporting clear. Hence the
   24-column corridor scan.
4. **A fast missile steps over a small arming radius.** At 60 m/s it covers 3 m per tick. One
   flight passed within **2 blocks** of its target, did not fire, and flew on 50 blocks into a
   hillside — a perfect intercept scored as a miss. Now a closest-approach fuse.
5. **A winged vehicle here has a service ceiling, and mountains are above it.** Kinetics compresses
   the atmospheric scale height to 55 m, so density falls 154× faster with altitude than it really
   does: at 70 m above sea level the air is already a quarter of sea level. A missile governed to a
   fixed 44 m/s physically cannot hold level flight at y=135. The autopilot now solves `L = W` at
   the density of the altitude it is commanding and speeds up to get there — climbing makes it
   accelerate, which is correct and good to watch.

### Measured accuracy (same world, four ranges)

| range | flight time | miss distance | notes |
|---|---|---|---|
| 400 | 8 s | ~3 blocks | |
| 800 | 15 s | ~2 blocks | fuse fired at closest approach |
| 1600 | 26 s | ~21 blocks | crosses a mountain; arrives high |
| 2400 | 33 s | ~95 blocks | **flies into a mountain short of target** |

**Stated honestly: extreme relief still defeats it.** A 60-block mountain needs ~12 s of climb and
the look-ahead is 14 s, which is marginal, and the let-down that improved medium-range accuracy
costs a little at 400. This is a tuning frontier, not a finished number — and it is arguably good
design that terrain masks a target and the approach bearing matters.

---

## 6. What is NOT in this mod, and why

- **Nothing from `ORBITAL-ARMS.md`.** That document is sealed by ruling. Ruled here that a
  ground-launched cruise missile reading a published recon coordinate is outside it: the seal
  covers orbital platforms and deorbited impactors, and warfront 0.3.0 already ships a
  satellite → C2 recon feed that the seal did not cover.
- **No damage inside kinetics.** Rule I10 is structural. `Detonation` is the only place a warhead
  does anything, and it lives here.
- **No faction messaging system.** Warfront has none to hook — its events are NPC dialogue and
  tactical orders, with no player-facing broadcast anywhere. A strike announces itself to players
  on the server instead of inventing a parallel faction-messaging system. If warfront grows a
  broadcast contract, `WarfrontC2Bridge.announceStrike` is the one method that changes.
- **No multiblock.** The console and the tube are single blocks. The network is the complexity.

---

## 7. Client rendering

`CruiseRenderTest` (`./gradlew runGametest`) is part of the ship ritual, per CLAUDE.md rule 9.
Screenshots land in `build/run-gametest/screenshots/` **and must be looked at**.

An unregistered entity renderer is not a missing feature here — the dispatcher returns null and the
render thread dereferences it, hard-crashing the client on the first launch while the server logs a
flawless flight. Cosmos shipped that, then shipped a renderer that drew nothing, which was a whole
release of invisible rocket. Both are invisible to every server-side check this mod has.

The battery includes a **model board**: the missile stood up stationary next to the camera and shot
from three sides. Verifying a model from a real flight takes many runs and rarely produces a
legible frame; the board answers "is it drawn, and is it the right shape" in seconds.

Read at the v0.1.0 gate: model draws correctly from front and plan view (slim fuselage, long wings
with warning chevrons, cruciform tail), both block textures render, and the missile is visible and
correctly oriented in flight with its smoke trail. One camera in the first battery run faced the
wrong way and photographed empty stone — a reminder that a camera angle is part of the evidence.

---

## 8. Phase 2

- **Multiple command centres per faction** — a callsign hierarchy, so a theatre console can see
  every battery's roster without being able to fire them.
- **Intercept and counter-battery** — kinetics' `point_defence_intercept` is a committed golden
  trajectory that has never been pointed at anything. A cruise missile flying at 26 blocks is a
  hard target for it, which is the point.
- **Satellite jamming** as a defensive mechanic — deny the recon fix rather than the missile.
- **Squad-linked salvo fire** — one order, several tubes, staggered releases so they arrive
  together from different bearings. The network layer already knows every tube's distance.
- **Mountain routing** — waypointed approach legs instead of a straight line, which would fix the
  2400-block case properly rather than by raising the climb rate.
- **Moving-target hand-off** — cosmos exposes entity tracking; the brief fenced this as one-way and
  it should stay one-way, but a *refreshed* fix before release is a smaller step.

---

## 9. Placeholder assets needing real art

Everything in `tools/gen-textures.py` is computed, original, and deliberately plain. All of it
would benefit from a real artist:

| asset | size | state |
|---|---|---|
| `entity/cruise_missile.png` | 64×64 | computed; UV regions solved per box, verified in the battery |
| `item/cruise_missile_body.png` | 16×16 | computed side silhouette |
| `item/conventional_warhead.png` | 16×16 | computed |
| `block/fire_control_console.png` | 16×16 | computed; one face used on all six |
| `block/launch_tube.png` / `_top.png` | 16×16 | computed; no distinct bottom |

Also placeholder: no sounds of any kind (launch uses vanilla firework), no custom particles
(vanilla smoke), and the console screen is drawn in code rather than textured.
