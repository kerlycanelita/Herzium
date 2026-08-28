# Changelog

## 1.9.3

- Stops previewing the hotbar slot when the item in it keeps Vanilla's equip
  transition. The HUD highlight used to move instantly for every item while the
  hand could only follow for ordinary ones, so selecting a sword left the
  highlight on the new slot and the hand still holding the old item, mid-dip.
  Players read that as the hotbar failing to respond, and it happened on roughly
  every other press in combat. Now both surfaces obey the same rule: ordinary
  items are instant everywhere, combat items are Vanilla everywhere, and which
  one applies is predictable from the item.

- Removes every Herzium write to Raw Input, Smooth Camera, and the running mouse
  window mode. Cursor placement is now entirely Vanilla-owned or controlled by
  the player's installed mouse mod, with no per-mod exception path.
- Removes the confirmed GLFW Raw Input rewrite that could replace Cursor Landing
  with a centred pointer when an inventory opened.
- Keeps Raw Input Buffer, Ixeris, KoHsium, and KoHs Inventory Tweaks detection
  informational only; Herzium no longer activates, disables, or mediates any of
  their mouse pipelines.
- Establishes Priority Hotbar as Herzium's primary input feature. FPS/VSync and
  the remaining loading/equip improvements stay in the rendering core.
- Removes the configuration screen and the Mod Menu entry. Every feature is core
  and always on, so `config/herzium.json` now holds nothing but the start-up
  advisory acknowledgement.
- Removes the runtime diagnostics ledger along with the screen that displayed
  it, ending its per-frame bookkeeping.
- Restores Priority Hotbar, which now verifies itself: it compares its predicted
  slot against Vanilla's committed slot every tick and suspends itself for the
  rest of the world after three disagreements in a row, so any mod that owns
  hotbar selection wins without being known to Herzium by name.
- Fixes combat items losing Vanilla's equip animation on servers. Item tags
  arrive after the level does, and a sword classified in that window was cached
  as an ordinary item for the whole session; an unanswerable classification now
  reports "combat" and nothing is cached until the tag set is live.
- Connects the first-person hand to the previewed hotbar slot again, so a swap
  reaches the HUD and the hand in the same frame.
- Keeps zero-duration equip transitions for ordinary items while preserving
  Vanilla's visible transition for swords, axes, pickaxes, spears, maces, bows,
  crossbows, tridents, shields and tagged combat items.
- Removes container-focus world suppression so inventories use Vanilla's live
  background, or the background supplied by another visual mod, instead of
  exposing a cleared black framebuffer.
- Returns inactive, minimized, menu and ten-minute AFK pacing to Minecraft's
  own policy, but keeps rendering uncapped through Vanilla's sixty-second AFK
  throttle while a level is loaded. Sixty seconds without input is not away from
  the game, and dropping to 30 fps there made the first mouse movement afterwards
  arrive as one lump instead of a sweep, which players read as the camera
  dragging.
- Stops writing VSync, the frame-rate limit, Smooth Camera and Raw Input into
  `options.txt`; the policy is applied at the window and pacing level instead.

## 1.9.2

- Prevents render-only hotbar ghosting when distinct slot inputs arrive inside the same Vanilla client tick.
- Keeps a single visual candidate instead of a queue; repeated input for the same slot cannot create duplicate previews.
- Resolves distinct same-tick slots inside one aggregate preview exactly like Vanilla's ascending hotbar loop, preventing a provisional block from differing from the block Vanilla can use.
- Restores the renderer-field synchronization used by 1.8.7 for both hands while keeping the new hotbar path render-only; offhand always comes from the current local player stack.
- Preserves every Vanilla `KeyMapping` click, selected-slot write, carried-item packet, offhand packet, action order, cooldown, and server acknowledgement.
- Adds a safe batch-resolution counter and updated English, Spanish (Spain), and Spanish (Mexico) explanations to the Core diagnostics.

## 1.9.1

- Detects Raw Input Buffer and Ixeris and disables only Vanilla Raw Input while an external mouse-input pipeline is present.
- Reports single-pipeline ownership and the unsafe Raw Input Buffer + Ixeris overlap in the bilingual Core diagnostics and startup log.
- Reports when Raw Input Buffer is loaded without KoHs Inventory Tweaks, which makes Cursor Landing and its late-recentering adapter unavailable.
- Clarifies that inventories, containers, and screens share Herzium's uncapped active-window render loop; no inventory interaction or networking behavior is changed.

## 1.9.0

- Rebuilt Hotbar Priority as a render-only preview: it no longer consumes Vanilla clicks or writes the real inventory slot from keyboard or mouse callbacks.
- Removed Herzium's exclusive mouse-input redirect and replaced it with a cooperative post-Vanilla logical-key observer plus read-only wheel confirmation.
- Added an uncapped HUD highlight and first-person item preview for the latest logical hotbar binding while Vanilla keeps full ownership of selection, packet timing, actions, and server authority.
- Supplies the current stack and zero equip offset only as final hand-draw arguments, leaving renderer-owned interpolation state untouched; swing and held-item use poses remain Vanilla.
- Added applied-mixin, observed-hook, config-health, and hotbar-preview diagnostics for the new responsive Core configuration page.
- Added the translucent purple Core interface, scrollable status/help/risk sections, and a high-density mouse-reactive particle background in English, Spanish (Spain), and Spanish (Mexico).
- Applied and compiled the architecture for Minecraft 1.21 through 1.21.11 and 26.1 through 26.2.

## 1.8.7

- Restored the direct per-frame hand synchronization that made 1.8.4 feel faster.
- Removed hotbar and offhand item-swap equip transitions completely while preserving swing, use, attack, cooldown, inventory and networking behavior.
- Removed the 1.8.5/1.8.6 visual revision counter and 40-tick recovery guard that could redraw or delay the Vanilla equip dip.
- Kept the immediate hotbar input path from 1.8.4: remapped keyboard and mouse-button slots are applied in event order, while wheel selection remains Vanilla.
- Adapted the shared renderer to the `renderHandsWithItems`/`submitHandsWithItems` rename in Minecraft 26.2 without relying on version-specific cooldown methods.
- Applied the render-only behavior to every supported Minecraft version from 1.21 through 26.2.

## 1.8.6

- Fixed the immediate hotbar item being followed by a second Vanilla equip animation.
- Kept the first-person renderer synchronized until Vanilla's item-swap visual state recovers.
- Matched selected hotbar stacks by identity so equal-looking stacks cannot trigger a delayed Vanilla replacement.
- Applied the render-only fix to every supported Minecraft version from 1.21 through 26.2.
- No packets, inventory rules, attacks, item use, cooldowns, reach, or server-side behavior are changed.

## 1.8.5

- Made immediate hotbar hand replacement zero-duration and render-only.
- Added zero-duration first-person visual updates after Vanilla mouse-wheel slot changes.
- Preserved Vanilla packet, cooldown, action, spectator, screen, and creative-hotbar behavior.
- Made the visual revision counter atomic and avoided redundant updates when a slot does not change.
- Made the startup warning persistently appear only until it is acknowledged once.
- Scoped the 1.21.11/26.x item-swap mapping adaptation to the hand renderer, preventing HUD mixin crashes.

## 1.8.4

- Added builds for Minecraft 1.21 through 1.21.11 and 26.1 through 26.2.
- Fixed Minecraft 26.2 startup compatibility after Mojang renamed the first-person hand renderer and moved HUD extraction from `Gui` to `Hud`.
- Replaced early startup text with a resource-independent pixel font, preventing missing glyph, shader, and follow-on model-loading errors.
- Removed active-window VSync and internal FPS limits across supported versions.
- Added optional immediate hotbar 1-9 selection, enabled by default.
- Fixed the one-tick Vanilla first-person equip dip after immediate hotbar selection on Minecraft 1.21-1.21.10 without changing combat cooldowns or packets.
- Added responsive English, Spanish (Spain), and Spanish (Mexico) configuration and warning screens.
- Added Exordium HUD-cache compatibility and KoHsium ownership coordination.
- Changed the project license to MIT.
