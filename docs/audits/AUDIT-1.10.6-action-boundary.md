# Herzium 1.10.6 — clicks keep the key pressed before them

Date: 2026-09-27. Scope: the Herzium order on the 1.10.6 JARs for all seven
targets; measured on 26.2.

## The problem

Vanilla's `handleKeybinds` applies every hotbar key pending in a tick before any
Use or Attack click of that tick. Under the Herzium order, the last key pressed
won the pass, including a key pressed after the click. If the sequence "slot,
use, slot" landed inside one tick, the use went to the item of the key pressed
after it. For example, "nexus, use, glowstone" placed glowstone, and
"glowstone, use, sword" hit the anchor with the sword. The Vanilla order only
avoided it when the later key was a lower slot.

The KoHs Anchor's session found this in its anchor-PvP laboratory
(`KoHs-Debug-Tools/anchors-debug`) with the user's real setup: Use on a keyboard
key, the mctiers bar layout, 26.2, and Grim on the server.

## The change

- `KeyMappingMixin` already observes every logical click. The policy now also
  records Use and Attack clicks, and keeps, per hotbar slot, the order of its
  pending clicks, kept in step with Vanilla's `clickCount`.
- At the start of a pass under the Herzium order, a slot whose oldest pending
  click was pressed after the first pending Use or Attack is **deferred**. A
  `WrapOperation` on `KeyMapping.consumeClick()` inside `handleKeybinds` returns
  false for that slot only. Its click stays in Vanilla's queue, unconsumed, and
  is applied on the next pass. Nothing is replayed, reordered or sent early.
- Among the remaining slots, the last one pressed before the click wins.
- The Vanilla and Vanilla reversed orders are unchanged.
- The preview is now suspended only when Vanilla's own hotbar call site selects
  a slot that contradicts it. A slot chosen by another mod, such as KoHs Anchor's
  resolving a nexus burst, or by the server only clears the preview.

`hotbarOrderTest` models the clicks in an independent oracle. Over 30,000
exhaustive bursts of four events (keys and clicks) and 15,000 random ones, it
checks two things: the first click of a burst uses the key pressed before it,
and once the queue drains the last key pressed is selected. It passes 1,194,651
assertions. All seven JARs carry the `consumeClick` target; on 1.21.x that is
`class_304.method_1436`.

## Laboratory results on 26.2

Run by the KoHs Anchor's session with `herzium-26.2-1.10.6.jar` and KoHs Anchor's
0.2.1. 1.10.5 is in brackets.

| Test | Nexus charged | Clicks with another item | Misplaced blocks | Preview |
| --- | --- | --- | --- | --- |
| One action per tick | 100 % (0 %) | 0 (60) | 0 (40) | active |
| Click every 35 ms, key 10 ms later | 53.3 % (0 %) | 14 (72) | 0 (46) | active |
| Vanilla order + KoHs Anchor's | 100 % | 0 | 0 | active (suspended in 1.10.5) |

Grim raised no alert in any run.

## Known limit

The 14 remaining clicks in the 35 ms test all come from ticks with two uses and a
key between them, such as "use, glowstone, use". The second use still gets the
earlier slot. Covering it would take either several slot changes in one tick,
which is the pattern server anticheats watch, or delaying the player's click. The
user chose to leave it. With KoHs Anchor's installed, nexus bursts are resolved
by KoHs.
