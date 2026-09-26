# Herzium 1.10.5 — order hooks per version, and the crystal cycle

Date: 2026-09-26. Scope: the 1.10.5 JARs for 1.21.10, 1.21.11, 26.1, 26.1.1,
26.1.2, 26.2 and 26.3.

This round was checked on code and bytecode only. No game was launched, as
requested. The selection-order code is unchanged since 1.10.4, whose behaviour
was measured in game in the
[in-game order audit](AUDIT-1.10.4-orders-ingame.md). The only functional change
in 1.10.5 is in the first-person hand renderer.

## The order hooks, version by version

Each target's `Minecraft.handleKeybinds` and `Minecraft.tick` were read with
`javap` from the Minecraft JARs in the Loom cache.

| Minecraft | `setSelectedSlot` calls | Pass sealed at | Sealed before Attack/Use | `tick`: game mode < keybinds < renderer |
| --- | --- | --- | --- | --- |
| 1.21.10 | 1 | `keySocialInteractions` | yes | yes |
| 1.21.11 | 1 | `keySocialInteractions` | yes | yes |
| 26.1 | 1 | `keySocialInteractions` | yes | yes |
| 26.1.1 | 1 | `keySocialInteractions` | yes | yes |
| 26.1.2 | 1 | `keySocialInteractions` | yes | yes |
| 26.2 | 1 | `keyInventory` | yes | yes |
| 26.3 | 1 | `keyInventory` | yes | yes |

So in every version:

- The selection preference wraps exactly one call.
- The slot pass is sealed after the hotbar loop and before Vanilla processes
  Attack and Use in the same tick, so a click in the same tick always uses the
  slot the order chose.
- `KeyMapping.click(Key)`, `KeyMapping.clickCount`, `KeyMapping.key`,
  `MouseHandler.onScroll(long, double, double)` and
  `Inventory.get/setSelectedSlot` all exist.

The order code each target compiles is identical to the shared source on 1.21.10
through 26.1.2. On 26.2 and 26.3 it differs only by the documented rewrites:
screen and overlay read through `Gui`, and the seal anchored at `keyInventory`.
The release JARs carry the matching targets. On 1.21.x they are statically
remapped to intermediary: `class_1661.method_61496` is `setSelectedSlot` and
`class_315.field_26845` is `keySocialInteractions`. `hotbarOrderTest` passes
527,948 assertions.

## The crystal cycle on 26.2

A user reported that the Herzium order felt slower when placing obsidian and an
end crystal right after switching slots.
`py tools/validation/smoke-release.py 26.2 --crystal` measured it on 1.10.4.
Each cycle is key 2 (obsidian), use, key 3 (crystal), use, 10 cycles per case:

| | Vanilla order | Herzium order |
| --- | --- | --- |
| Obsidian visible after the click, median | 32 ms | 27 ms |
| Crystal visible after the click, median | 71 ms | 63 ms |
| Complete cycles, steps 60 ms apart | 10 / 10 | 10 / 10 |
| Complete cycles, steps 25 ms apart | 5 / 10 | 6 / 10 |
| Hotbar and hand after the key | ~1 to 2 ms | ~1 ms |

The orders place equally fast; the differences follow the tick phase in both
directions. Three things explained the feeling instead:

1. **The preview shows the switch sooner.** The hotbar and hand move about 1 ms
   after the key, while the block can only appear on the next tick. Without
   Herzium the switch and the placement arrive together, so there is no visible
   gap between them.
2. **Herzium removed Vanilla's placement dip.** `ItemInHandRenderer.itemUsed`
   drops the hand to height 0 after a placement. The measurement read 0 after
   the tick, but Herzium rendered 1 because it pinned ordinary items to full
   height every frame. **Fixed in 1.10.5:** after `itemUsed`, the hand is left
   to Vanilla until it is fully up again. Switching items stays instant.
3. **Vanilla resolves keys before clicks within a tick.** If key, click, key land
   in one 50 ms tick, both keys are applied first and both clicks use the second
   slot. Half the cycles at 25 ms failed this way, under either order. Changing
   it would mean several slot changes and placements in one tick, which is the
   pattern server anticheats look for, so it is left to Vanilla.

## Limits

- 1.10.5 was not launched for this audit. The placement dip was checked by
  reading the code path, not in game.
- The crystal numbers come from singleplayer on one machine.
