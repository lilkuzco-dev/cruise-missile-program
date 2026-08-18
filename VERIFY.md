# Cruise Missile Program 0.2.1 verification

Verified from release commit `08d12c4` on 2026-08-18.

- `./gradlew clean test build runGametest`: PASS on Java 25 using Kinetics 0.1.5,
  Warfront 0.4.1, and Cosmos 0.2.3.
- Terrain impacts use Minecraft's vanilla TNT-style three-dimensional explosion and move the
  blast centre into the struck material along the missile's incoming velocity. This prevents
  near-horizontal strikes from wasting most of the blast in open air.
- The automated depth test fired the real detonation path into a solid-stone volume and measured
  two continuous cleared blocks vertically below the embedded blast centre. The test fails if
  the detonation is absent or clears fewer than two vertical blocks.
- Visual inspection confirmed the missile blocks, launch and cruise flight, and the exposed
  vertical blast-depth cutaway rendered correctly.
- Dedicated Fabric server: booted with the staged dependencies, executed the Cruise command path,
  and stopped cleanly.
- Release: <https://github.com/lilkuzco-dev/cruise-missile-program/releases/tag/v0.2.1>
- Release asset SHA-512:
  `3c4bd348cae1338de7753139f47bb3aae9348c4b7334ef2dfb1b225bc5befb8b335749f24aa0f2f656f53f589dc68bfc1e4089489cd2746df42b63a4ec77c4b1`
- The downloaded GitHub asset was byte-for-byte identical to the clean local build.
- Root manifest commit: `f63749e`.
- `tools/postship-check.sh`: PASS; exact hashes, dependency compatibility, and zero-change
  convergence after installing 0.2.1.
- Client mods backup:
  `/Users/jessehagy/Library/Application Support/minecraft/mods-backup-20260818-195757`

No live dedicated server was deployed.
