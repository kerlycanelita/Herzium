
<!-- Modrinth summary: Your hotbar switches on the very next frame, and you choose which slot wins. --> <p align="center"> <img src="https://raw.githubusercontent.com/kerlycanelita/Herzium/main/src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220"> </p> <p align="center"> <a href="https://github.com/kerlycanelita/Herzium"><img src="https://img.shields.io/badge/GitHub-Source-6f2cff?style=for-the-badge&logo=github" alt="GitHub"></a> <a href="https://github.com/kerlycanelita/Herzium/issues"><img src="https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues" alt="Issues"></a> <a href="https://discord.gg/9t2VxEF7UU"><img src="https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a> </p>
Herzium
Your hotbar switches on the very next frame — and you choose which slot wins.

Minecraft waits for its next game update to show a hotbar switch. Herzium draws it on the next frame: up to about 50 ms sooner on a high-refresh monitor. Every item appears in hand right away; weapons keep their normal animation unless one switch puts them in hand at once too.

Pick how your hotbar chooses
Press two hotbar keys at once and Minecraft has to pick one. The Order tab of the settings has the three options, no restart.

🟪 Herzium — last input  ·  this is the default
![boton herzium Last input](https://cdn.modrinth.com/data/cached_images/58b165c1a1985fe8ca852a076d1aaf36e1f216b3.png)

The key you pressed last wins. Press 9 then 1, you get slot 1 — the one you actually meant.

🟪 Vanilla
![Boton Vanilla herzium](https://cdn.modrinth.com/data/cached_images/dfd0b849c7c08ad91b2bc9485399a1d7444a0bda.png)

The highest slot wins, exactly like Minecraft without any mods. Press 1 and 9 together, you get slot 9.

🟪 Vanilla reversed
![Boton Vanilla reverso herzium](https://cdn.modrinth.com/data/cached_images/d32fbf7e176c0f4797b0bea692d22f25a52170dc.png)

The lowest slot wins. Press 1 and 9 together, you get slot 1.

Fast bursts, the right item every time
Built into the Herzium order, always on.

🟪 Bursts in order — obsidian, use, crystal, use inside one tick places the obsidian, then the crystal right on top of it on the next tick. Vanilla lands only one of them. Same for anchor → glowstone → detonate and rail → TNT minecart.

🟪 Two hands, one rhythm — new in 1.12.1. When your click lands a few milliseconds before its key, it goes with that key, as Vanilla does inside a tick. 1.12.0 ran it with the item of the step before: a second glowstone instead of the detonation, and your loop one click late.

🟪 Offhand sync — totem key and swap in the same tick puts the totem in your offhand, not the item you held before.

🟪 Hold guard — a button you keep held never places once more with the old item, the ghost block of 1.11.

🟪 Real presses only — a hotbar key you keep held no longer takes the slot back from a slot on a mouse button.

Every tick still switches slot at most once, before its clicks, just like Vanilla. A click can only ever wait one tick, or go with the key pressed right with it: nothing is sent earlier, nothing is repeated. Tested against Grim Anticheat, with and without its experimental checks, and with Ping Equalizer: no alerts.

Built for a real hand
In the settings screen, with what each one does written next to it.

🟪 Offhand swap preview — the swap is drawn at once instead of one ping later.

🟪 Preview recovery — if the preview ever stops, it is back in 3 seconds instead of staying off until the next world.

🟪 Monitor — how many milliseconds your last hotbar key took to reach the screen, and a counter of what a server could tell from Vanilla in your own packets.


⚠️ Read this once
Herzium (last input) is on by default. It changes the slot you really hold, not just what you see — so the item you use can change, and the server sees it. Switch to Vanilla in Mod Menu if you don't want that, and check your server's rules on client mods. Herzium also tells you this on first launch.

Also included
No equip dip — items appear in hand at once, and placing a block still plays Minecraft's quick hand dip. Swords, axes, bows and shields keep their full animation unless you switch "Combat items too" on.
Smoother attack indicator — the cooldown bar glides instead of stepping. Your real attack timing is untouched.
Snappier start-up — skips the title fade and the pause after world creation. Nothing real is skipped, only the waiting.
Not a performance mod. No FPS gain, and your settings — VSync, max framerate, Raw Input, sensitivity, cursor — are never touched.

Download
Pick the file matching your Minecraft version. Client-side only.

Minecraft	Java
1.21.10 · 1.21.11	21
26.1 · 26.1.1 · 26.1.2 · 26.2 · 26.3	25
Needs Fabric Loader 0.19.3+. Get Fabric API too, or Minecraft won't load Herzium's text and the screens show raw codes. Mod Menu is where the settings button lives — without it you're stuck on the defaults.

⚠️ Exordium: Herzium skips its HUD cache to redraw the hotbar each frame, so that cache stops working. Don't run both if you'd rather keep it.

English + all seven Spanish variants Minecraft ships.

Made by zymekoh · MIT License

