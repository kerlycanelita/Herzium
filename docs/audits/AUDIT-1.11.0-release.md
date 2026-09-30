# Herzium 1.11.0 — release audit

Date: 2026-09-30. Scope: the seven release JARs for 1.21.10, 1.21.11, 26.1,
26.1.1, 26.1.2, 26.2 and 26.3, as a fresh install leaves them. The options
themselves, how they were designed and what the laboratory measured on the lab
builds are in the [1.11 laboratory audit](LAB-1.11-burst-options.md).

## From the lab build to the release

- **Same tick is gone.** The laboratory order is removed with everything that
  served only it: the enum constant, the replay in `HotbarOrderController`, the
  segments in `HotbarOrderPolicy`, the `MinecraftActionInvoker` mixin, its two
  translation keys and its test suites. The order button cycles Vanilla →
  Herzium → Vanilla reversed again, which is what KoHs Anchor's
  (`HerziumBridge.ORDERS`) and Crystal Tweaks step through when they change the
  order. A config that still holds `"SAME_TICK"` reads as no answer and gets the
  Herzium order, like a fresh install.
- **Defaults.** A fresh install starts on the Herzium order with split bursts,
  strict attacks and offhand sync all on. A `herzium.json` from 1.10.x keeps its
  order; it has none of the three keys, so it gets the same three defaults.
- **26.3 drops.** 26.3 drops through `MultiPlayerGameMode.dropItem`, which calls
  `ensureHasSentCarriedItem()` before it sends the drop (checked in bytecode), so
  the `LocalPlayer.drop` hook has no target there. The hook is fenced with
  `herzium:26.3-drop-start` / `-end` and the 26.x build leaves it out for 26.3
  only. The swap hook stays in every version: no version from 1.21.10 to 26.3
  sends the slot before a swap.
- **Texts.** The Mod Menu description and the note under the alternative orders
  no longer say that click timing never changes: with the burst options a click
  can wait one tick, never go sooner. The first-launch advisory is unchanged; at
  GUI scale 4 on a 1080p screen its Spanish text already fills the screen.

## Build

- The seven targets were built with the arguments of `version/build-all.ps1`
  (`-ExportRuntime`), from Git Bash: PowerShell 5.1 turned javac's deprecation
  note on stderr into a terminating error when the whole output was redirected.
- `hotbarOrderTest` passes 6,581,486 policy/layout assertions: 43,923 exhaustive
  and 15,000 random bursts, 431,289 option bursts. It no longer carries the
  same-tick suites, which is the whole difference from the lab build's count.
- In every JAR: `HotbarOrder` has exactly `VANILLA`, `HERZIUM` and
  `VANILLA_REVERSED`; `HerziumConfig`'s constructor sets `HERZIUM` and `true` for
  the three options; `HotbarOrderMixin` has the drop hook in six JARs and not in
  26.3's; the swap hook is in all seven.

## What KoHs Anchor's and Crystal Tweaks call

Both reach Herzium by reflection, so a renamed method would switch their part
off silently rather than fail. `javap` on each of the seven JARs finds every one
with the same signature as in 1.10.7:

| Caller | Method |
| --- | --- |
| both | `HerziumConfig.get()`, `hotbarOrder()`, `cycleHotbarOrder()` |
| KoHs Anchor's (better communication) | `HotbarOrderController.hotbarClickConsumed(int, int)` |
| KoHs Anchor's (better communication) | `ImmediateHotbarInput.clearPreview()`, `visualSelectedSlot(Inventory, int)` |

## Packaged-JAR validation

`tools/validation/smoke-release.py` launched each release JAR in a new isolated
profile: the advisory accepted through its real continuation, every required
target transformed, the fresh config on `HERZIUM`, the order cycle of three, the
settings screen rendered and closed, and `config/herzium.json` read back with
`splitBursts`, `strictActionOrder` and `offhandSync` all `true`. All seven pass.

`smoke-release.py 26.3 --gameplay` passes 43 live world, input and hand
assertions. Two things turned up on the way:

- **The 26.3 test world now asks for confirmation.** Minecraft shows its
  experimental-settings warning before creating the harness's test world, and
  the harness waited on it until its phase timed out. The released 1.10.7 JAR
  stopped at the same screen, so it is not a Herzium change. `Gameplay263` now
  accepts that warning for its disposable world.
- **The 26.3 client sometimes dies while loading fonts** (exit `0xC0000005`, no
  JVM crash report, last line "Found unifont_jp_patch-17.0.01.hex, loading").
  In one streak, alternating the 1.10.7 and 1.11.0 JARs, it died on five of six
  launches (twice with 1.10.7, three times with 1.11.0), and on three of six
  launches of the same client **without Herzium at all**. It is this machine's
  26.3 client, not Herzium; every launch that got past loading passed.

## Through the lab, as a fresh install leaves it

The 26.2 release JAR went through the
[Herzium lab](https://github.com/kerlycanelita/KoHs-Debug-Tools-for-KoHs-Mods/tree/main/herzium-lab)
with a `herzium.json` that holds only the acknowledged advisory, so the order and the three options
are the defaults; every bench logged `herzium=HERZIUM split=on strict=on sync=on`. Four sessions on
2026-09-30, 47 benches and 1,065 cycles, the calibration probe seen by Grim in each:

| Session | Grim | Setup | Result |
| --- | --- | --- | --- |
| `release-1` | as it ships | lab, 0 and +100 ms | every burst 100 %: one-tick and 20 ms obsidian→crystal, anchor place→charge→detonate (one tick and 15 ms), rail→TNT cart, totem swap (one tick and 10 ms), wheel kept, random bursts (3 and 12 ms) with every click on its own item |
| `release-player-1` | as it ships | the player's setup, 0 and +50 ms | anchors, crystals and the totem swap at 100 % next to KoHs Anchor's 0.4.0 and Crystal Tweaks 2.3.1 (the released JARs) and Marlow's Crystal Optimizer |
| `release-player-strict-1` | experimental checks on | the player's setup, 0 and +50 ms | the same, 100 % |
| `release-strict-1` | experimental checks on | lab, 0 ms | every burst 100 % |

**No Grim alert in any session, and no tick with a slot change after a click.** The benches were
the lab's own (`release`, `release-player`, `release-strict` in `testlab/make_scripts.py`).

One number is lower than in the lab build's run, and it is not Herzium's: hitting the crystal a
fixed 120 ms after the burst broke it in 16 of 20 cycles with the experimental checks on (19 of 20
as Grim ships, 20 of 20 in the player's setup). In the four misses the server spawned the crystal
about 252 ms after the burst, against 140 to 205 ms in the others, so when the hit came the client
had no crystal to aim at and sent no attack at all. Every placement used the right item in every
cycle; the spread is the server's tick timing in that session. At +100 ms the same hit fails for
every order, as in the lab: the crystal is not on the client yet.
