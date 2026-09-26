# Herzium 1.10.3 — seven-version release audit

Run: 2026-09-22. Written up on 2026-09-25 from the logs that run left under
`tmp/release-audit/`, which is not tracked.

Scope: the seven release JARs published on Modrinth on 2026-09-22 for 1.21.10,
1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. The behaviour and trust boundary
are in the [root audit](AUDIT-1.10.3-26.1.2.md); how the matrix got here is in
the [multiversion audit](AUDIT-1.10.3-multiversion.md).

## Build

- `version\build-all.ps1 -MinecraftVersions ... -ExportRuntime` built all seven
  targets.
- The builds are reproducible: the six targets built on 2026-09-21 and rebuilt
  on 2026-09-22 have identical SHA-256 sums.
- The sums are in [`../releases/1.10.3-checksums.sha256`](../releases/1.10.3-checksums.sha256).
  On 2026-09-25 the seven files on Modrinth were compared against the local
  JARs by SHA-1 and SHA-512: all seven match.
- `hotbarOrderTest` passed 516,380 policy/layout assertions.

## The 26.3 adapter

26.3 moved the live first-person hand state out of `ItemInHandRenderer` and into
`FirstPersonHandsAndItems`, owned by the local player. The shared
`ItemInHandRendererMixin` therefore has no target there. For 26.3 the build
excludes it and compiles `version/official26/src/26.3/.../ItemInHandRendererMixin.java`,
which:

- synchronizes ordinary items at the head of `extractRenderState`, before
  Vanilla resolves the stack and its model, so the two cannot disagree;
- replaces ordinary items instantly through the three-argument
  `shouldInstantlyReplaceVisibleItem`;
- declares `require = 1` on both, so a future signature change fails at launch
  instead of silently dropping the feature.

26.3 also shares 26.2's split between `Gui` and `Hud`, so it takes the same
`Gui`/`Hud` rewrite and `GuiScreenMixin`.

## Packaged-JAR validation

`tools/validation/smoke-release.py` launches each release JAR, not the dev
classes, in a new profile under `tmp/release-audit/`. It uses the production
namespace: intermediary for 1.21.x, official for 26.x. A separate validation mod,
never shipped, drives the checks from `Minecraft.runTick`. For every JAR it
checks:

- **Metadata:** the mod id, client environment, the exact `minecraft`
  dependency, `java`, `fabricloader >=0.19.3`, the mixin config, the eight
  locales with identical keys, the class-file versions, and that no validation
  class leaked into the JAR.
- **Runtime:** the advisory is accepted through its real continuation; every
  mixin target class carries its `herzium$` handlers; Mixin's own audit passes;
  a fresh config starts on `HERZIUM`; the order cycle goes Vanilla reversed,
  Vanilla, Herzium; the settings screen renders and closes through its real
  handler.
- **Optional hooks:** the exported, transformed `Gui`/`Hud` and hand-renderer
  bytecode really calls the `require = 0` handlers
  (`renderVanillaResolvableHotbarInput`, `replaceVisibleItemImmediately`).

| Minecraft | Metadata | Runtime | Optional hooks | Gameplay |
| --- | --- | --- | --- | --- |
| 1.21.10 | PASS | PASS | PASS | not run |
| 1.21.11 | PASS | PASS | PASS | not run |
| 26.1 | PASS | PASS | PASS | not run |
| 26.1.1 | PASS | PASS | PASS | not run |
| 26.1.2 | PASS | PASS | PASS | not run |
| 26.2 | PASS | PASS | PASS | not run |
| 26.3 | PASS | PASS | PASS | PASS, 43 assertions |

The first six rows are run `smoke-20260922-133248`. The 26.3 row is run
`smoke-20260922-133115` with `--gameplay`.

The 26.3 gameplay run creates a creative world and drives the real
`handleKeybinds` pass once per order. It checks that 9 then 1 ends on slot 9
under Vanilla and on slot 1 under Herzium and Vanilla reversed, and that each
click is consumed exactly once. It also checks that stone, a sword and a shield
are classified correctly from live tags, that the ordinary-item dip is removed
at extraction, that combat heights are preserved, and that busy-hand lowering is
preserved.

## Known limits of this run

- The smoke clients run without Fabric API, so the language files are never
  loaded. The screens were exercised with raw translation keys, not with the
  length of the real English or Spanish text.
- Gameplay was exercised on 26.3 only.
- A 26.3 run with Fabric API and Mod Menu added through `--mods` crashed natively
  on both attempts: exit `0xC0000005`, during font loading, before the advisory
  (runs `smoke-20260922-133449` and `smoke-20260922-133742`). No pass was
  recorded for that configuration.
- No multiplayer server or anticheat was involved.
