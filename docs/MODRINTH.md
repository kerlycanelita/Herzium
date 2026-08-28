[![GitHub](https://img.shields.io/badge/GitHub-Source-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

# Herzium

Herzium removes visual latency that Minecraft adds on purpose, and nothing else.
It is not an FPS mod: it does not raise your frame rate and does not claim to.

## What it does

- **Priority Hotbar.** Pressing 1-9 updates the HUD and your hand on the next
  rendered frame instead of waiting for the next client tick, which is up to
  50 ms sooner. Herzium never writes the selected slot, never consumes the
  click and never sends a packet; Vanilla still resolves the selection exactly
  as before. If the preview ever disagrees with what Vanilla commits, three
  times in a row, it suspends itself for the rest of that world.
- **Instant equip.** Ordinary items appear in hand with no equip dip. Swords,
  axes, pickaxes, spears, maces, bows, crossbows, tridents and shields keep
  Vanilla's animation.
- **Faster start-up transitions.** Removes the two-second loading fade, the
  two-second title fade and the 500 ms hold after creating a world. The
  underlying work is untouched.

## When it helps, and when it does not

Helps on high refresh-rate displays and with fast item switching, mainly PvP,
where one client tick of delay is visible. Does nothing if you are GPU or CPU
bound: it will not add frames.

## What it does not touch

VSync, `Max Framerate`, `Reduce FPS when inactive`, Raw Input, Smooth Camera
and cursor placement are all yours or another mod's. Herzium does not write to
`options.txt`. Ticks, reach, cooldowns, hitboxes, attack and use timing and
every packet stay Vanilla.

There are no options: everything above is always on. The only interface is a
one-time start-up notice.

## Requirements

- Minecraft 26.1.2, Fabric Loader 0.19.3 or newer, Java 25
- Client-side only. No Fabric API needed.
