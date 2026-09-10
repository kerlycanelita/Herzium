# Herzium 1.10.3 — Minecraft 26.1.2

Date: 2026-09-08. Scope: root 26.1.2 build only.

The user requested retaining existing hotbar sampling and explicitly selected
implementation of three slot-order preferences after being informed that the
alternate orders change real selection. No experimental sampling refinements
from this iteration were retained.

## Behavior and trust boundary

| Setting | Winning pending slot | Classification |
| --- | --- | --- |
| Vanilla, default | Highest slot | L0 visual feature; selection call passes through |
| Herzium | Last pressed binding among pending slots; highest slot on a tie | L2, server-observable selection change |
| Vanilla reversed | Lowest slot | L2, server-observable selection change |

Example: press 9 then 1 before a client tick. Vanilla ends on 9; Herzium and
reversed end on 1. The selected item may then change the carried-slot packet,
placement/use payload or which action Vanilla actually performs. Consequently
packet counts/outcomes cannot be promised identical in the alternate modes,
even though Herzium adds no direct sends or repeated actions.

A server-only anticheat sees ordinary gameplay protocol messages including
the selected slot and resulting interactions. It cannot read this local setting
from those messages. That does not establish server permission. A cooperating
client/launcher or attestation component can inspect mods/settings (L3 trust
model). No specific server/anticheat acceptance is claimed.

## Exact version inspection

Inspected the local named Minecraft 26.1.2 JAR with `javap`:

- `KeyMapping.click(Key)` increments the matching mapping counters.
- `consumeClick()` consumes one count, if present.
- `Minecraft.handleKeybinds()` visits hotbar indices 0 through 8, consumes at
  most one click per mapping, and calls `Inventory.setSelectedSlot(int)`.
- The hotbar loop ends before social/inventory screens, offhand swapping,
  attack/use processing and held-use processing.
- `startAttack`, `startUseItem`, `continueAttack`, `rightClickDelay` and server
  prediction/acknowledgement handling retain their original paths.

The new wrapper only filters lower-priority selected-slot assignments within
that existing loop. Vanilla mode forwards every original assignment. Creative
toolbar modifiers, spectators and GUI contexts fall back to the original path.
No new polling loop, early action call, queue consumption, cooldown modification
or packet send was introduced.

Herzium's sampling remains event-driven at `KeyMapping.click` tail. It has no
separate periodic sampler whose interval could be lowered. Presentation still
depends on the next rendered frame and platform event delivery. GLFW itself
documents callback delivery during event processing and some window-system
calls: [GLFW input guide](https://www.glfw.org/docs/latest/input).

The preference metadata uses two fixed arrays of nine event serials. It is not
a replacement click queue. Repeated clicks keep Vanilla's carry-over behavior;
the modes choose only among the slots consumed in the normal pass. A pass
captures event order once so a later callback cannot change its already captured
preference. HUD/ordinary-item preview reads the same configured winner.

## Configuration and evidence

- Vanilla is the field-initialized default; missing/null/unrecognized serialized
  enum values fall back to it. Changing the mode writes through the existing
  asynchronous config writer.
- Mod Menu remains optional and Fabric API is not required.
- The explanation scrolls inside bounded logical-screen coordinates; layout
  tests cover widths 64–1920 and heights 64–1080, including small high-scale GUIs.
- `hotbarOrderTest` executes the production policy on 19,683 exhaustive
  four-press bursts and 15,000 seeded random bursts, including repeated presses,
  duplicate bindings, unknown serials, late events and default setter passthrough.
- These are isolated policy/layout checks, not a Minecraft play session or
  evidence of reduced input latency. No live multiplayer/anticheat test has been
  performed for this candidate. Other Minecraft builds have not been validated
  in this iteration.

Debug 0.2.2 probes the configured policy read-only and includes the order in
bursts. Earlier Herzium builds without that API use its existing Vanilla oracle.
