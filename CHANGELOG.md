# Changelog

## 1.8.4

- Added builds for Minecraft 1.21 through 1.21.11 and 26.1 through 26.2.
- Fixed Minecraft 26.2 startup compatibility after Mojang renamed the first-person hand renderer and moved HUD extraction from `Gui` to `Hud`.
- Replaced early startup text with a resource-independent pixel font, preventing missing glyph, shader, and follow-on model-loading errors.
- Removed active-window VSync and internal FPS limits across supported versions.
- Added an independent 10 FPS safeguard for unfocused or minimized sessions.
- Added optional immediate hotbar 1-9 selection, enabled by default.
- Fixed the one-tick Vanilla first-person equip dip after immediate hotbar selection on Minecraft 1.21-1.21.10 without changing combat cooldowns or packets.
- Added responsive English, Spanish (Spain), and Spanish (Mexico) configuration and warning screens.
- Added Exordium HUD-cache compatibility and KoHsium ownership coordination.
- Changed the project license to MIT.
