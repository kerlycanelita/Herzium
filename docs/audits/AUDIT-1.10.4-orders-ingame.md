# Herzium 1.10.4 — selection orders, measured in game

Date: 2026-09-25. Scope: the release JARs of 1.10.3 and 1.10.4 for Minecraft
1.21.11 and 26.1.2, driven in a fresh world. The question was whether the three
orders really apply, whether the Herzium order is fast, and whether its input
overlaps.

## How it was measured

`py tools/validation/smoke-release.py 1.21.11 26.1.2 --orders` launches each
release JAR in an isolated profile, in the production namespace, and creates a
creative test world. `GameplayOrders` then taps hotbar keys through
`KeyboardHandler.keyPress`, the method GLFW's key callback calls, from the end
of a frame, which is where real events arrive. Vanilla's own client tick resolves
every press; nothing calls `handleKeybinds` directly.

For every scenario it records:

- the slot the HUD hook returned on each frame;
- the selected slot after each client tick;
- every `ServerboundSetCarriedItemPacket` the client sent;
- whether Herzium's preview suspended itself.

Frames are uncapped: about 700 to 1050 fps on an RTX 3050 laptop GPU with render
distance 3.

The run has three parts:

- **Scenarios:** fixed key sequences, started at the beginning of a client tick,
  once per order.
- **Latency:** 25 single taps per order at random phases of the tick.
- **Stress:** the same 80 random bursts for every order, each 2 to 4 taps 0 to
  45 ms apart.

## Results

### The orders apply as documented

On both versions, before and after the fix:

- 9 then 1 in one tick ends on 9 under Vanilla and on 1 under Herzium and
  Vanilla reversed.
- 2, 7, 4 ends on 7, 4 and 2 respectively.
- Each sends exactly one carried-slot packet, the same count as Vanilla.

### Speed

The preview does not change the tick at which the slot is really selected, nor
when the server hears about it.

| | 1.21.11 | 26.1.2 |
| --- | --- | --- |
| Key to HUD, median / p95 | 0.7 / 1.2 ms, always the next frame | 0.7 / 1.3 ms, always the next frame |
| Key to real selection, median / p95 | 28.8 / 45.6 ms | 29.4 / 47.0 ms |
| Key to carried-slot packet, median | 77.9 ms | 78.2 ms |

The HUD therefore answers about 29 ms sooner at the median and up to 48 ms
sooner, but only on a display fast enough to show the extra frames. The packet
leaves one tick after the selection, as in Vanilla, because
`MultiPlayerGameMode.tick` sends it before `handleKeybinds` runs.

### The overlap that 1.10.3 had

Vanilla consumes at most one click per hotbar key per tick. A key tapped twice
inside one tick, or held until the keyboard auto-repeats, still has clicks
queued after a newer key has won. In 1.10.3 the Herzium order let those older
clicks select their slot again on the next tick:

| Herzium order | 1.10.3 | 1.10.4 |
| --- | --- | --- |
| 1, 1, 9 inside one tick | ends on 1, packets 9 then 1 | ends on 9, one packet |
| hold 1 until it repeats, then 9 | ends on 1, packets 1, 9, 1 | ends on 9, packets 1, 9 |
| random bursts keeping the last key | 77 of 80 on both versions | 80 of 80 on both versions |
| selection changed again after its first commit | 3 bursts | 0 bursts |

Holding a key really does queue clicks: both versions call `KeyMapping.click`
for auto-repeat events.

In 1.10.4 the policy remembers the press that produced the current Herzium
selection. A queued click whose press is not newer, on any other slot, is still
consumed by Vanilla but no longer selects anything. The guard switches itself off
as soon as the selection moves without Herzium (the wheel, the server or another
mod), and Vanilla and Vanilla reversed never use it.

For comparison, in the same 80 bursts Vanilla order keeps the last key in 47 to
49 and Vanilla reversed in 55 or 56. Those orders are unchanged by design.

### What the server sees

Across every scenario, burst and version:

- no duplicate carried-slot packet (a repeat of the slot the server already
  has);
- no carried-slot packet sent outside the client tick;
- no frame whose HUD showed a slot that the next tick did not select;
- no preview suspension.

The Herzium order sent 110 and 111 carried-slot packets over the 80 bursts in
1.10.4, against 112 and 114 for Vanilla order. It never sends more. It sends
fewer than before because it no longer flips back to an older slot.

`hotbarOrderTest` now also requires, over all 19,683 exhaustive and 15,000 random
bursts, that queued clicks never undo the last input under the Herzium order. It
passes 527,948 assertions with the new policy, and it fails on the 1.10.3 policy.

## Limits

- Keys are injected at `KeyboardHandler.keyPress`, not at the operating system.
- The world is singleplayer. Packets are observed as the client sends them; no
  dedicated server, network latency or anticheat was involved.
- Behaviour was measured on one machine. The preview's benefit scales with the
  display refresh rate; the selection-order results do not depend on frame rate.
