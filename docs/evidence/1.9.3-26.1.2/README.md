# Herzium 1.9.3 / Minecraft 26.1.2 QA evidence

## Controlled inventory A/B

The same Modrinth profile, world (`Herzium 1.9.3 QA`), player position,
resolution, resource pack, inventory page, and visible item grid were used.
Only the Herzium JAR changed between the two runs.

| Build | FPS samples | Median FPS | p98 samples | Median p98 | p99.5 samples | Median p99.5 |
| --- | --- | ---: | --- | ---: | --- | ---: |
| 1.8.7, live world behind inventory | 401, 398, 436 | 401 | 258, 255, 242 | 255 | 202, 185, 193 | 193 |
| 1.9.3, container focus | 525, 561, 565 | 561 | 387, 367, 301 | 367 | 312, 295, 208 | 295 |

Observed median differences in this short local run: about +39.9% FPS,
+43.9% p98, and +52.8% p99.5. These are scene- and hardware-dependent F3
snapshots, not a universal performance guarantee or a laboratory benchmark.

## Functional checks

- Priority Hotbar is always active and remains render-only.
- 6,561 four-input hotbar batches were exhaustively simulated with 0 differences
  from Vanilla's ascending hotbar resolution.
- Ordinary stone appeared in the first captured frame after its mapped key.
- A diamond sword selected immediately in the HUD while its first-person model
  kept the visible Vanilla equip transition.
- A shield in the offhand was classified as combat equipment and retained the
  Vanilla transition path.
- Container-focus, ordinary-item, and combat-item runtime counters all advanced.
- The one-time startup warning did not reappear after its saved acknowledgement.

## Trust-boundary audit

The hotbar, hand-render, and container-focus paths do not consume clicks, write
the selected inventory slot, send packets, change packet payloads/order, alter
ticks, cooldowns, reach, attack/use timing, or bypass server validation. They are
client rendering changes (L0). A launcher or server that attests the installed
client mod list can still identify Herzium.

Validated 26.1.2 release SHA-256:
`E1F01D75D869AA20CA61C43FFC11F46A4E7C510844E136CA54F801822066AD05`.
