# Herzium 1.11 lab — burst options

Date: 2026-09-29. Scope: the Herzium lab build `1.11.0-lab.1` for Minecraft 26.2, measured on a
local server with Grim Anticheat and Ravenclaw's Ping Equalizer, with the player's own key
bindings. The rig lives in
[KoHs Debug Tools, `herzium-lab/`](https://github.com/kerlycanelita/KoHs-Debug-Tools-for-KoHs-Mods/tree/main/herzium-lab).

## Why

Vanilla's `Minecraft.handleKeybinds` resolves every hotbar key of a client tick before any click of
that tick, and the server learns the selected slot only when a use or an attack is sent (or at the
next tick). Three consequences, all reproducible in the lab:

1. **Bursts lose items.** "Obsidian key, use, crystal key, use" inside one tick gives both clicks
   the same item. Vanilla picks the highest slot; Herzium 1.10.7 picks the key pressed before the
   first click and defers the later key, so the obsidian lands and the crystal click fails.
2. **Offhand swaps take the wrong item.** The swap packet is built inside `handleKeybinds` and sent
   before the slot packet of the same tick, so "totem key, swap" inside one tick swaps the item
   held *before* the key. Up to 26.2 the drop key has the same problem. Mojang fixed the drop half
   in 26.3: `MultiPlayerGameMode.dropItem` now calls `ensureHasSentCarriedItem()` before sending the
   drop. The swap is unchanged in every version from 1.21.10 to 26.3 (checked in bytecode).
3. **An attack and a use share the item of the last key.** "Sword key, attack, crystal key, use"
   hits with the crystal in hand in Vanilla and in 1.10.7.

## The options

| Option | Default in the lab build | What it does |
| --- | --- | --- |
| Split bursts | on | Herzium order. A click whose item (the last key pressed before it) differs from the slot this tick holds waits for the next tick, and so does every click pressed after it. Every tick still changes the slot at most once, before its clicks. |
| Offhand sync | on | Not in the Vanilla order. The server is told the slot right before an offhand swap or a drop, as Vanilla does before a use or an attack; in the Herzium order a swap or a drop bounds the pass like a use. |
| Strict attacks | off | Herzium order. An attack bounds the pass even when a use follows, so it gets the key pressed before it; the key and the use go to the next tick. |
| Same tick | lab only | A fourth order that replays the whole burst in pressed order inside one tick: several slot changes per tick, including after a click. Built only to measure it. |

All three options off is exactly Herzium 1.10.7: `hotbarOrderTest` runs every burst through the
1.10.7 entry points and the new ones and checks that every pass defers and selects the same slots.

## Correctness before the lab

`hotbarOrderTest` (`./gradlew hotbarOrderTest`) now also models the four action types. Over every
burst of four events over all thirteen event codes, every burst of six events over three keys,
use, attack and swap, and 20,000 random bursts of up to 40 events, for the Herzium and same-tick
orders with each option set, it checks:

- each checked click runs with the slot of the last key pressed before it (uses always; attacks
  with strict attacks; swaps and drops with offhand sync);
- the Herzium order changes the slot at most once per pass;
- every click runs exactly once, and the last key stays selected;
- options off decide exactly as 1.10.7.

It passes 8,155,246 assertions (761,736 option bursts). Three deliberate mutants of the policy
(no waiting, no strict boundary, wrong tie-break) each fail it on the first named case.

## Method

- Server: Minecraft 26.2, Fabric API, Grim Anticheat 2.3.74 (`b1d49ff`), verbose alerts on, every
  alert copied into the trace with a microsecond clock. Two Grim profiles: as it ships, and with
  its experimental checks on.
- Client: the development client with Herzium (the lab build, or the released 1.10.7 jar),
  Ravenclaw's Ping Equalizer 1.5, and for some sessions Marlow's Crystal Optimizer 1.1.0, KoHs
  Anchor's 0.4.0 and Crystal Tweaks 2.3.0 with the player's own configurations. Keys: hotbar R, C, F,
  4, M, G, 2, 8, 3; use on the period key; attack on the left button; swap on B.
- A calibration probe (one duplicate slot packet) must show up as Grim's BadPacketsA in every
  session, or its "no alerts" means nothing. It did in every session.
- Profiles: **V** Vanilla order; **H107** Herzium order, options off (1.10.7); **R107** the released
  1.10.7 jar; **HS** split bursts and offhand sync; **HX** HS plus strict attacks; **ST** same tick.
- Each cycle starts on a rebuilt platform with a full kit and the sword in hand. Phase 0 starts a
  burst 3 ms after a client tick begins, so a short burst falls inside one tick; phase 1 starts it
  at a random moment.

## Results

Grim Anticheat as it ships, 20 to 30 cycles per cell, every cycle on a rebuilt platform. A cell is
the share of cycles in which the burst did what was pressed; for random bursts, the share of clicks
that ran with the item of the last key pressed before them.

#### No added latency

| Burst | Vanilla | 1.10.7 | split + sync | + strict | same tick |
| --- | --- | --- | --- | --- | --- |
| Obsidian, use, crystal, use (one tick): crystal placed | 0 % | 0 % | 100 % | 100 % | 0 % |
| The same, 20 ms from click to key, random phase: crystal placed | 20 % | 50 % | 100 % | 100 % | 47 % |
| The one-tick burst, then hit the crystal 120 ms later: crystal broken | 0 % | 0 % | 100 % | 96 % | 0 % |
| One tick, crystals in a lower slot than obsidian: crystal placed | 0 % | 0 % | 100 % | · | · |
| Anchor, use, glowstone, use, sword, use (one tick): exploded | 0 % | 0 % | 100 % | 100 % | 0 % |
| The same, 15 ms from click to key, random phase: exploded | 0 % | 0 % | 100 % | 100 % | 50 % |
| Rail, use, TNT cart, use (one tick): cart placed | 0 % | 0 % | 100 % | 100 % | 0 % |
| Totem key, swap, sword key (one tick): totem in offhand and sword held | 0 % | 0 % | 100 % | 100 % | 100 % |
| The same, 10 ms apart, random phase | 12 % | 0 % | 100 % | 100 % | 100 % |
| Random bursts of nine blocks, 3 ms mean gap: clicks with the right item | 39 % | 77 % | 100 % | 100 % (preview suspended) | 100 % |
| Random bursts, 12 ms mean gap: clicks with the right item | 49 % | 87 % | 100 % (preview suspended) | 100 % (preview suspended) | 100 % |

#### +100 ms (Ping Equalizer)

| Burst | Vanilla | 1.10.7 | split + sync | + strict | same tick |
| --- | --- | --- | --- | --- | --- |
| Obsidian, use, crystal, use (one tick): crystal placed | 0 % | 0 % | 100 % | 100 % | 0 % |
| The same, 20 ms from click to key, random phase: crystal placed | 27 % | 43 % | 100 % | 100 % | 37 % |
| The one-tick burst, then hit the crystal 120 ms later: crystal broken | 0 % | 0 % | 0 % | 0 % | 0 % |
| Anchor, use, glowstone, use, sword, use (one tick): exploded | 0 % | 0 % | 100 % | 100 % | 0 % |
| The same, 15 ms from click to key, random phase: exploded | 0 % | 0 % | 100 % | 100 % | 30 % |
| Rail, use, TNT cart, use (one tick): cart placed | 0 % | 0 % | 100 % | 100 % | 0 % |
| Totem key, swap, sword key (one tick): totem in offhand and sword held | 0 % | 0 % | 100 % | 100 % | 100 % |
| The same, 10 ms apart, random phase | 8 % | 4 % | 100 % | 100 % | 100 % |

Grim raised **no alert** in any of these benches. The calibration probe raised BadPacketsA in every
session.

What the numbers say:

- **Vanilla** gives every click of a one-tick burst the item of the highest pending slot, so the
  obsidian, the anchor or the rail is never placed, or the second click runs with the first item.
  With the crystals in a lower slot than the obsidian, the obsidian lands and the crystal click
  fails the same way.
- **1.10.7** places the first block every time but never the second: the second click keeps the
  first key's item. The offhand swap takes the item held before the burst, as in Vanilla.
- **Split bursts** place the obsidian on one tick and the crystal on the next, on top of it: 100 %
  at 0, 50, 100 and 150 ms of added latency. The anchor is placed, charged and detonated on three
  consecutive ticks. Every random-burst click runs with its own item.
- **Offhand sync** puts the totem in the offhand every time, with the sword selected afterwards.
- **Same tick** runs every click with its own item but still fails the second placement: Minecraft
  reads the crosshair once per tick, so the crystal, glowstone or cart click aims at the floor the
  first click aimed at. It also changes the slot after a click in the same tick in every burst.
- **Hitting the crystal 120 ms after placing it** fails for every order at +100 ms: the crystal does
  not exist on the client yet. That is network latency, not the hotbar.

The released 1.10.7 jar gave the same numbers as the lab build with every option off (one-tick
crystal, crystal hit, anchor and offhand benches).

### Latency of a single key

Eighty taps at random moments per profile, no other input.

| | With Herzium | Without Herzium |
| --- | --- | --- |
| Key to the HUD showing the slot (median, p90) | 2.0 ms, 3.0 ms | 30.2 ms, 48.3 ms |
| Key to the slot packet leaving the client (median, p90) | 76.8 ms, 96.6 ms | 78.0 ms, 94.9 ms |

The preview draws the slot on the next frame; the packet the server sees leaves at the start of
the next tick either way. Herzium does not move anything the server sees earlier.

### With Marlow's Crystal Optimizer and the player's setup

With Marlow's Crystal Optimizer 1.1.0 the numbers are the same, and the crystal hit succeeds where
the crystal already exists on the client. With the player's full setup (KoHs Anchor's 0.4.0 and
Crystal Tweaks 2.3.0 with their own configurations, Marlow, Ping Equalizer), crystal bursts behave
as above; anchor bursts succeed in every order because KoHs Anchor's applies them itself, in
pressed order, inside the tick.

RESULTS2
