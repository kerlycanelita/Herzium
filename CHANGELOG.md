# Changelog

## 1.9.8

### Fixed
- The HUD now previews every unambiguous hotbar slot, including combat-item
  slots; combat items still keep their complete Vanilla first-person hand and
  equip transition.
- Distinct slot keys received before the same client tick no longer produce a
  speculative highlight or hand item. Herzium waits for Vanilla's committed
  result, preventing a rapid burst from looking like a lost or ignored remap.
- Single-slot repeats and duplicate bindings still use one preview candidate;
  there is no Herzium input queue and no duplicated visual state.

### Vanilla input boundary
- Minecraft 26.1.2 resolves several pending hotbar slots in ascending slot
  order, then handles Use/Place. Herzium does not replace that with last-key
  order because doing so changes which item Vanilla can use and is visible to
  the server.
- Mouse and keyboard remaps for Use/Place remain on Vanilla's `KeyMapping`,
  `rightClickDelay`, action and packet paths. Herzium neither consumes nor
  retries them.

## 1.9.7

### Fixed
- Preserved Minecraft 26.1.2's exact ascending-slot resolution when several
  hotbar keys arrive inside one client tick, including repeated and remapped
  inputs, without adding an input queue of Herzium's own.
- Moved visual-preview confirmation to the end of the complete client tick so
  later Vanilla or compatible-mod work is observed before Herzium accepts the
  result.
- Made Priority Hotbar fail closed after the first disagreement with Vanilla
  and coordinated HUD/hand hook health, preventing a missing HUD hook from
  leaving a hand-only preview.
- Treats Vanilla wheel selection and late creative save/load modifiers as
  authoritative superseding input, so neither can falsely trip the mismatch
  guard during a rapid remapped-key burst.
- Clears an earlier same-batch preview as soon as a creative toolbar modifier
  appears, instead of retaining the prefix of a batch Vanilla resolves under
  different rules.
- Shortened the English and all shipped Spanish Mod Menu descriptions.

### Security boundary
- The preview remains render-only. Herzium does not consume clicks, write the
  selected inventory slot, send packets, or change action/cooldown timing.

## 1.9.6

### Fixed
- Aligned the description bundled for Mod Menu with Herzium's public Modrinth
  summary and current feature scope.
- Updated the English and every shipped Spanish Mod Menu translation together,
  so the in-game listing no longer appears to describe a different project.
- Kept Mod Menu's `Website` action linked to Herzium's official Modrinth page.

## 1.9.5

### Changed
- Rewrote player-facing descriptions and messages so they describe the current
  render-only behavior without presenting Herzium as an FPS optimizer.
- The resource-independent loading overlay now selects neutral Spanish for any
  `es_*` language code and English for every other language.
- Added complete runtime translations for every Spanish locale shipped by
  Minecraft 26.1.2: Argentina, Chile, Ecuador, Spain, Mexico, Uruguay and
  Venezuela.
- The start-up information screen now has one `Continue` button. Continuing
  records the acknowledgement, so the screen is shown only once.
- Documentation now explicitly says that neither Fabric API nor Mod Menu is
  required.

### Fixed
- Removed obsolete loading messages about uncapped FPS, high-frequency HUD
  rendering and lifted frame limits.
- Qualified the hotbar feature as an ordinary non-combat item preview: Vanilla
  still owns the real selection, input resolution, actions and packets.

## 1.9.4

Reworked after Modrinth moderation feedback. Herzium is no longer an FPS mod.

### Removed
- Forced VSync off. Minecraft already exposes that setting, and overriding it
  left the Video Settings toggle showing something that was not true.
- All frame-limit handling. `Max Framerate` and `Reduce FPS when inactive` are
  Minecraft's to apply; Herzium was duplicating them.
- The start-up advisory no longer claims uncapped operation, because there is
  none to warn about.

### Fixed
- Combat items lost Vanilla's equip animation on servers whose item tags had
  not synced when the first frame rendered.
- The hotbar highlight moved instantly while the hand was still mid-animation,
  which read as the hotbar not responding.

### Changed
- Priority Hotbar checks itself against Vanilla every tick and stops previewing
  for the rest of the world after three disagreements, so a mod that owns
  hotbar selection takes over without Herzium knowing its name.

## 1.9.3

- Honours a frame limit the player set themselves. Herzium still removes the
  throttles Minecraft applies without asking -- the 60 fps menu cap and the AFK
  caps -- but it no longer overrules an explicit number: overriding a setting
  the player typed is the same mistake as writing to `options.txt`, only less
  visible. Leaving the limit on Unlimited behaves exactly as before.
- Announces that with a toast the first time the player is back at the controls
  with a limit in place, so an uncapping mod that appears to be doing nothing
  has a reason on screen. Pixel-drawn warning glyph in the mod's palette, a
  settle animation, and a bar that drains over the three seconds it stays up.

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
