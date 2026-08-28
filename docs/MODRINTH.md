[![GitHub](https://img.shields.io/badge/GitHub-Source-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

# Herzium

Herzium is a client-side Fabric optimization mod focused on Priority Hotbar, uncapped rendering, lower visual latency, and faster safe transitions. It keeps gameplay and server authority Vanilla while allowing HUDs, inventories, containers, TAB, and the hotbar to render at the full frame rate of the game.

## Highlights

- Removes active-window VSync and internal Minecraft FPS limits.
- Leaves inactive, minimized, menu, and AFK frame pacing to Minecraft's existing policy.
- Previews the hotbar slot you pressed in the HUD and hand before Vanilla's tick commits it, without consuming clicks, writing the selected slot, or sending packets. The preview compares itself against Vanilla every tick and suspends itself after three disagreements, so a mod that owns hotbar selection wins automatically.
- Keeps inventories and containers live at the active-window frame rate while omitting the expensive 3D world rendered behind them.
- Removes the delayed first-person equip dip for ordinary items while combat items retain Vanilla's visible equip transition.
- Removes safe decorative loading waits while preserving required resource reload, validation, model baking, shader compilation, chunk readiness, and error handling.
- Has no options and no Mod Menu entry: every feature is core and always on. The only interface is the one-time startup advisory.
- Supports English, Spanish (Spain), and Spanish (Mexico).
- Disables Exordium's HUD cache when both mods are installed so HUD elements render every frame.
- Leaves Raw Input, Smooth Camera, mouse grabbing, and cursor placement completely to Vanilla or the player's installed input mod. Herzium never calls a cursor-position API or changes the running Raw Input mode.

## Important

Uncapped rendering can increase GPU usage, power consumption, temperature, and frame-time instability. Herzium does not change Minecraft's 20 TPS simulation, invent packets, or bypass server-side rules.

## Español

Herzium es un mod de optimización para Fabric del lado del cliente cuya función principal de entrada es Priority Hotbar. Elimina VSync y los límites internos de FPS mientras la ventana está activa y acelera transiciones seguras sin modificar los ticks, paquetes, alcance, cooldowns ni la autoridad del servidor.

La preview de hotbar muestra la ranura pulsada antes de que el tick de Vanilla la confirme, sin consumir clics, sin escribir la selección real y sin enviar paquetes; si se equivoca tres veces seguidas se suspende sola. Raw Input, Smooth Camera y la posición del cursor quedan completamente en manos de Vanilla u otros mods. Los inventarios y contenedores se dibujan a la frecuencia de frames activa y su fondo lo pinta Vanilla, no Herzium. Al perder el foco, minimizar, estar en menús o AFK, Minecraft conserva su propia política de FPS. El mod no tiene opciones: todo es core. El aviso inicial está en inglés, español de España y español de México.

## Compatibility

- Minecraft 1.21-1.21.11 and 26.1-26.2
- Fabric Loader 0.19.3+
- Java 21 for Minecraft 1.21.x
- Java 25 for Minecraft 26.x
- Fabric API is not required at runtime
- Mod Menu is not used

Herzium is licensed under MIT and created by **zymekoh**.
