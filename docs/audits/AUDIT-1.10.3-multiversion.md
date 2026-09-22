# Herzium 1.10.3 — multiversion matrix

Date: 2026-09-21. Scope: the five Modrinth targets 1.21.10, 1.21.11, 26.1,
26.1.2 and 26.2, built from `version/` at mod version 1.10.3. The root 26.1.2
build is the reference; see [the root audit](AUDIT-1.10.3-26.1.2.md) for the
behaviour and trust boundary, which this pass does not revisit.

The matrix had been left behind by two releases. `version/gradle.properties`
still said 1.10.2 and `version/official26/gradle.properties` still said 1.10.1,
so every jar in `version/*/build/libs` carried code older than the published
root build. Everything below was found while bringing those five targets up to
1.10.3.

## What was broken

**M-01 — 1.21.x shipped a two-release-old lifecycle hook.**
`version/src/client/common121/.../MinecraftMixin.java` was an overlay copy that
still called `ImmediateActionFeedback`, a class deleted before 1.10.2. It
predated the fail-closed action boundaries
(`discardDivergentPreviewBeforeAction` on `startUseItem`, `startAttack` and
`continueAttack`), the preview clear on screen open, `CombatItemClassifier`
invalidation on level change, and the settled-session world check added in
1.10.3. Because it was an overlay rather than the shared file, none of that
showed up as a compile error; it just quietly produced weaker 1.21.x jars.

Every member the shared `MinecraftMixin` needs exists on 1.21.10 and 1.21.11,
including `LevelLoadTracker.LEVEL_LOAD_CLOSE_DELAY_MS` and the `runAllTasks`
call inside `runTick`. The overlay was deleted and the shared file is compiled
directly, so the next 26.x-only API in it fails this build loudly.

**M-02 — the 1.21.x classifier had no cache and no invalidation.**
The remaining overlay, `CombatItemClassifier`, exists because 1.21.x has no
`ItemStack.typeHolder()`. Its copy also lacked the per-item cache, the
"tags are not live yet" answer that preserves Vanilla, and `invalidate()` —
which the shared `MinecraftMixin` now calls. It was rewritten to mirror the
shared class on the 1.21 API. `ItemTags.SPEARS` only exists from 1.21.11 on, so
the build strips that one line for older targets, where spears are
`WEAPON_ENCHANTABLE` anyway.

**M-03 — 1.21.10 had never compiled at all.**
Mojang renamed `ResourceLocation` to `Identifier` in 1.21.11. The shared sources
use the new name, and nothing translated it back, so `:compileClientJava` failed
on every 1.21.x target below 1.21.11. `version/1.21.10/build/libs` was empty and
had been since the folder was created. The generator now renames it back for
those targets.

**M-04 — 26.2 would have failed to launch (seal anchor).**
`HotbarOrderMixin` seals the finished slot pass at the `keySocialInteractions`
field read in `Minecraft.handleKeybinds`, with `require = 1`. In 26.2 that key
moved into `Gui.handleKeybinds`, which 26.2 calls *before* the hotbar loop
rather than after. The injection would find no target and, with
`defaultRequire = 1`, take the whole mixin config down. For 26.2 the anchor is
`keyInventory`: the first screen-opening key handled after
`Inventory.setSelectedSlot`, appearing exactly once in the method.

This did not affect the published `herzium-26.2-1.10.1.jar`, which has no
`HotbarOrderMixin` at all — the mixin is newer than that release.

**M-05 — 26.2 would have failed to launch (setScreen).**
26.2 removed `Minecraft.setScreen` and gave screen ownership to `Gui`, so
`MinecraftMixin.herzium$clearPreviewBeforeScreen` had no target. A `Minecraft`
mixin cannot reach `Gui.setScreen`, so the hook is fenced with
`herzium:26.2-drop-start/-end` markers, dropped for 26.2, and replaced by
`version/official26/src/26.2/.../GuiScreenMixin.java`. That file is deliberately
outside the source filter that rewrites `Gui` to `Hud`: in 26.2 those are two
different classes, and this one wants the real `Gui`.

**M-06 — `build-all.ps1` pointed at a JDK that no longer exists.**
It hard-coded `C:\Program Files\Java\jdk-25.0.2` for the 26.x targets. That
directory is gone. The script now resolves a Java 25 home from `JAVA_HOME`, then
`PATH`, then the usual install roots, and uses it for every target.

**M-07 — no Java 21 toolchain could be provisioned.**
The 1.21.x targets compile at release 21 while the only installed JDK is the
Java 25 one the 26.x targets need, and `version/settings.gradle` had no
toolchain resolver, so `:compileJava` failed before reaching Loom. The foojay
resolver convention was added there.

## Default selection order changed to Herzium

Requested after the build pass: a fresh install now starts on
`HotbarOrder.HERZIUM` instead of `HotbarOrder.VANILLA`. Both the field
initialiser and the null fallback in `HerziumConfig` were changed, so a config
with no value and a config with an unreadable value land on the same answer.

The consequence is deliberate and stated here because it is not a cosmetic
default: among slots with pending clicks the last pressed binding wins, which
changes the **real** selected slot from the first session, and that is
server-observable (L2 in the root audit). The first-launch advisory is the only
notice a player gets before it applies. Vanilla order remains one click away in
Mod Menu.

The other `HotbarOrder.VANILLA` references were left alone on purpose:
`HotbarOrderController.passOrder`, `HotbarOrderPolicy.passOrder` and the
`ordinarySelection` fallbacks are "no alternate order active for this pass"
sentinels, not the user's setting.

Every text that claimed otherwise was corrected: the advisory and Mod Menu
strings in all eight locales, `fabric.mod.json`, the start-up log line, the
README table, the Modrinth page and the root audit's configuration note.

Verified by deleting the dev run's config, launching 26.1.2, accepting the
advisory and reading back `run/config/herzium.json`, which holds
`"hotbarOrder": "HERZIUM"`. `HerziumConfig.class` in all five matrix jars and in
the root jar references `HotbarOrder.HERZIUM` and no longer references
`HotbarOrder.VANILLA`. `hotbarOrderTest` passes 516,380 assertions unchanged —
it drives the policy with an explicit order, so it is unaffected by which one is
the default.

## How the five targets were checked

Each target was launched with Loom's offline dev client (`runClient`), taken
past the Herzium advisory with a posted click, and read back from its log:

| Target | Mixins applied | Advisory → vanilla chain resumed |
| --- | --- | --- |
| 1.21.10 | yes | yes |
| 1.21.11 | yes | yes |
| 26.1 | yes | yes |
| 26.1.2 | yes | yes |
| 26.2 | yes (after M-04, M-05) | yes |

M-05 was found only by running the game; a static sweep of injection *points*
had passed it, because the missing piece was the target method itself. Both 26.2
faults produce a hard launch failure, not a degraded feature.

The jars were also read back directly: each declares its own exact
`minecraft` version, `fabricloader >=0.19.3`, `java >=21` (1.21.x, class file
major 65, `JAVA_21` compatibility level) or `java >=25` (26.x, major 69,
`JAVA_25`). The 1.21.x jars are remapped to intermediary with the mixin
annotations rewritten statically, so they carry no refmap and need none.

## Known limits

- **Translations need Fabric API.** Fabric Loader 0.19.3 ships no resource-pack
  support, so `assets/herzium/lang/*.json` is never read unless
  `fabric-resource-loader-v0` is present. Without Fabric API the advisory and
  config screens render raw keys (`herzium.warning.title`). The mod loads and
  works; only its text degrades. `docs/releases/MODRINTH.md` states that Fabric
  API is not required, which is true of function but not of the UI text.
- **Gameplay was not exercised.** Each client was verified to the title screen.
  Hotbar preview, selection order and the equip transition were not played
  through on any target.
- **26.1.1 was not rebuilt.** It has a matrix slot but is not part of this set;
  its published jar is still at 1.10.1.
