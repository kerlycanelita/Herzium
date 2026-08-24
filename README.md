# Herzium

[![GitHub](https://img.shields.io/badge/GitHub-Herzium-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Modrinth](https://img.shields.io/badge/Modrinth-Herzium-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220">
</p>

Herzium is a client-side Fabric mod for Minecraft 1.21 through 26.2 that removes
internal FPS limits while its window is active, forces VSync to remain disabled,
reduces mouse and hotbar visual latency, and removes safe decorative waits from startup
and world entry. Background, minimized, menu, and AFK pacing remain owned by Minecraft.

## KoHsium cooperation

Herzium 1.9.3 and KoHsium 0.10.0 use an explicit ownership split when both are
installed. Herzium remains the sole writer of VSync, the FPS limit and immediate
hotbar visual preview. KoHsium owns its editable Raw Input, Smooth Camera and late-input
controls; Herzium stops rewriting those two vanilla input values while KoHsium is present.

Herzium remains authoritative over active-window frame pacing when both mods are
installed. When the window is inactive, minimized, in a menu, or AFK, Minecraft's
own frame-rate policy is allowed to run without a duplicate Herzium limiter.

KoHsium continues to provide its independent late-event sample, conservative
section scheduling, render-work reduction, PvP visual safety and diagnostics.
Its adaptive percentile and background telemetry cadence are based on actual
render work instead of raw frame count, so Herzium's uncapped rendering does
not cause those maintenance tasks to run hundreds of times per second.

The Herzium warning remains first because it owns the initial-screen runnable.
KoHsium waits until the real title screen is free before showing its own notice,
so neither startup screen overwrites the other.

## What it removes

- The OpenGL `swap interval` (VSync) is forced to `0`.
- Minecraft's general FPS limiter becomes a no-op while the window is active.
- The configured frame-rate limit remains on vanilla's `Unlimited` value.
- Special menu, loading-screen, and AFK limits are bypassed only while the window
  is active. Minecraft's own inactive, minimized, menu, and AFK policy is left intact.
- When Exordium is installed, Herzium disables its HUD cache so the hotbar, TAB
  list, and the rest of the HUD are extracted and rendered every frame again.

The core optimizations, Priority Hotbar, and fast loading transitions remain
permanently active. The configuration screen reports their live diagnostic state.

## Low-latency input

- Without KoHsium or an external raw-input mod, Vanilla Raw Input is enabled at
  start-up whenever GLFW and the operating system support it. It is applied to
  the window directly and is not written to `options.txt`, so the setting stays
  yours: turn it off in Controls and it stays off.
- Herzium no longer forces Smooth Camera off. It used to overwrite that option
  on every frame, which made cinematic camera impossible to enable and left the
  value behind after uninstalling the mod. Nothing Herzium does depends on it.
- The same applies to VSync and the frame rate limit: both are bypassed at the
  window and pacing level, not by editing your saved settings. Video Settings
  therefore shows what you chose, not what Herzium enforces.
- When Raw Input Buffer or Ixeris is detected, Herzium disables only Vanilla's
  Raw Input path so two implementations do not own the same Vanilla setting.
  The external mod remains active; Herzium does not disable foreign mixins or threads.
- Raw Input Buffer and Ixeris must not be installed together because both own
  overlapping low-level mouse pipelines. Herzium detects and reports the overlap,
  but the safe fix is to remove one of them.
- With KoHsium installed, Herzium yields those two input settings so KoHsium's
  Precision Camera, Direct Mouse and Force Raw controls remain authoritative.
  Mouse sensitivity and the amount of rotation produced by each raw delta remain unchanged.
- Mouse events are accumulated without discarding samples and applied before
  the next rendered frame. Actual end-to-end latency still depends on mouse
  polling rate, frame rate, display refresh rate, GPU queue, and compositor;
  Herzium cannot guarantee 0.1 ms response time.
- Cursor Landing is supplied by KoHs Inventory Tweaks rather than Herzium. Its
  verified Raw Input Buffer adapter suppresses only the late menu recentring and
  disarms after the one requested landing; without that mod, Cursor Landing is unavailable.
- `Priority Hotbar` is an always-active core feature. One unambiguous keyboard or remapped
  mouse-button binding is previewed in the HUD and first-person hand before the
  next client tick. The observer never consumes a click or writes the real
  inventory slot; Vanilla alone confirms selection and emits carried-item packets.
  If distinct hotbar slots are pending in the same tick, one aggregate visual
  state resolves them exactly like Vanilla's ascending hotbar loop. This keeps
  the previewed block equal to the block Vanilla can actually use without
  changing or consuming the underlying click queue.
  Mouse-wheel selection remains entirely Vanilla and supersedes a pending preview.
- The preview observes Vanilla's central logical `KeyMapping.click` after the
  click is registered, instead of redirecting keyboard or mouse callbacks. This
  lets ordinary input-remapping mods cooperate without an exclusive redirect.
  A mod that cancels Vanilla input before that point or replaces the HUD path
  cannot be overridden safely; Herzium falls back to Vanilla and reports the
  unobserved hook in the Core page.
- Creative hotbar save/load shortcuts and spectator controls retain their
  vanilla path.
- `Attack` and `Use/Place` remain entirely on Vanilla's 20 TPS input path,
  including mouse buttons, side buttons, keyboard remaps, and scancodes.
  Herzium does not dispatch combat or interaction packets from render-only
  frames.

## Faster loading

- The resource reload itself, validation, model baking, shader compilation, and
  error recovery remain untouched.
- Once those required tasks finish, Herzium removes vanilla's two-second loading
  overlay fade and the title screen's two-second visual fade.
- The 500 ms post-readiness hold used only after creating a new world is
  removed; chunk readiness and the compiled-and-visible player section are
  still required.
- Herzium starts its tiny config read asynchronously, so Fabric's client
  entrypoint does not wait on disk I/O before Minecraft creates the window.
- A lightweight dark-purple loading screen displays a pixel spiral, real reload
  progress, and randomized English tips while the required work is running.
- After the initial resource load, a responsive English or Spanish advisory
  explains the possible instability, FPS drops, frame-time spikes, hardware
  usage, heat, and power cost of uncapped operation. It must be acknowledged
  once before the normal title, onboarding, or Quick Play flow continues, and
  the acknowledgement is then persisted in `config/herzium.json`.
- The advisory includes fast right-moving purple particles. Particles near the
  mouse become more opaque, and compact logical resolutions receive a
  scrollable text area plus vertically stacked buttons.

## Configuration menu

Installing the compatible Mod Menu release for the selected Minecraft version
adds a configuration button for Herzium.
Its interface uses a restrained translucent dark-purple panel, compact animated
switches, clear option descriptions, and a scrollable system overview. At normal
GUI scales it uses two balanced columns; at high GUI scales it stacks them while
keeping every control inside the panel.

`Priority Hotbar` is displayed as an always-active core feature rather than a
toggle. Keyboard, remapped-button, and mouse-wheel selection stay on Vanilla's
unchanged input and packet paths. The diagnostics panel reports which mixins were
applied, which runtime hooks have actually executed, config health, active features,
operating details, compatibility warnings, and hardware risks.
The one-time startup-warning acknowledgement is stored in `config/herzium.json`.

## High-refresh improvements

- The HUD is not capped at 240 FPS: it renders at the game's full frame rate.
  When the game delivers 240 FPS, the HUD, hotbar, TAB list, and inventories
  update at 240 Hz; if the game runs faster, they can exceed that rate too.
- Inventories and containers remain on the active uncapped rendering cadence.
  Their world background is left completely to Vanilla or another installed
  visual mod; Herzium does not replace it with a black or custom backdrop.
- The crosshair and hotbar attack-strength indicators interpolate only toward
  vanilla combat's `0.5` partial-tick sample. They never display a stronger
  value than the one used by the attack calculation.
- Ordinary items adopt the current first-person model on the next frame with no
  equip dip. Combat-capable items such as swords, axes, pickaxes, spears, maces,
  bows, crossbows, tridents, and shields retain Vanilla's visible equip transition.
  Swing and held-item use animations remain Vanilla.
- Eating, bows, crossbows, held item use, gameplay cooldowns, and server tick
  rates are not accelerated or falsified.
- Players, hitboxes, raycasts, packet contents, block textures, and entity
  animation timers are never modified. Attack and use/place timing remains
  entirely Vanilla.

## What it does not change

Herzium does not increase the game's 20 ticks per second, invent intermediate
server packets, or override VSync imposed by the graphics driver, operating
system compositor, or an external layer. An element whose logic only changes
once per tick will still change at 20 Hz even though it is rendered every frame.

Herzium takes priority over Vanilla's limiter and the known limiter mixin paths
only while the window is active. It cannot override an external driver, compositor, or arbitrary mod
that blocks the render thread outside those paths. `Dynamic FPS` is no longer
declared incompatible because Herzium no longer owns background throttling.
Exordium buffer compatibility is specifically verified against Exordium
2.1.0/2.1.1 for Minecraft 26.1.x.

## Requirements

- Minecraft Java Edition 1.21-1.21.11 or 26.1-26.2
- Fabric Loader 0.19.3 or a newer compatible version
- Java 21 for Minecraft 1.21.x; Java 25 for Minecraft 26.x
- Fabric API is not required at runtime
- Mod Menu is optional; use the release compatible with the selected Minecraft version

## Building

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The root build targets Minecraft 26.1.2 and generates
`build/libs/herzium-<mod_version>.jar`, where `mod_version` is the value in
`gradle.properties` -- currently `1.9.3`, so `build/libs/herzium-1.9.3.jar`. Do
not use the file ending in `-sources.jar`.

### All supported Minecraft versions

The version matrix shares Herzium's sources and assets while applying the API
adaptations required by each Minecraft release. On Windows, build every target
with:

```powershell
.\version\build-all.ps1
```

This produces one release JAR per target at
`version/<minecraft-version>/build/libs/herzium-<minecraft-version>-1.9.3.jar`
for 1.21, every 1.21.x release through 1.21.11, and 26.1 through 26.2.

## Warning

While the Minecraft window is active, uncapped rendering may keep the GPU at
100% usage, consume more power, and generate more heat. Inactive, minimized,
menu, and AFK frame pacing is provided by Minecraft itself.

## License

Herzium is available under the [MIT License](LICENSE).

## Credits

Created by **zymekoh**.

The white `H` speed icon on a purple background is an original design supplied
for Herzium.

[Exordium](https://github.com/tr7zw/Exordium) was studied as a reference to
identify the buffer that limits HUD updates. Herzium does not include Exordium
code.
