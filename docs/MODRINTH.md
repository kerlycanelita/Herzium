[![GitHub](https://img.shields.io/badge/GitHub-Source-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

# Herzium

Herzium is a client-side Fabric optimization mod focused on uncapped rendering, lower visual latency, and faster safe transitions. It keeps gameplay and server authority Vanilla while allowing HUDs, inventories, containers, TAB, and the hotbar to render at the full frame rate of the game.

## Highlights

- Removes active-window VSync and internal Minecraft FPS limits.
- Leaves inactive, minimized, menu, and AFK frame pacing to Minecraft's existing policy.
- Leaves hotbar keys 1-9, remaps, selected-slot writes, and carried-item packets entirely on Vanilla's normal path so other hotbar-position mods retain ownership.
- Keeps inventories and containers live at the active-window frame rate while omitting the expensive 3D world rendered behind them.
- Removes the delayed first-person equip dip for ordinary items while combat items retain Vanilla's visible equip transition.
- Removes safe decorative loading waits while preserving required resource reload, validation, model baking, shader compilation, chunk readiness, and error handling.
- Includes a responsive dark-purple configuration screen and startup advisory.
- Supports English, Spanish (Spain), and Spanish (Mexico).
- Disables Exordium's HUD cache when both mods are installed so HUD elements render every frame.
- Coordinates ownership with KoHsium to avoid competing input and frame-limit settings.

## Important

Uncapped rendering can increase GPU usage, power consumption, temperature, and frame-time instability. Herzium does not change Minecraft's 20 TPS simulation, invent packets, or bypass server-side rules.

## Español

Herzium es un mod de optimización para Fabric del lado del cliente. Elimina VSync y los límites internos de FPS mientras la ventana está activa, reduce la latencia del mouse y acelera transiciones seguras sin modificar los ticks, paquetes, alcance, cooldowns ni la autoridad del servidor.

Las teclas 1-9 de la hotbar, sus remapeos, la selección real y sus paquetes quedan completamente en manos de Vanilla; Herzium no se impone sobre mods que cambien sus posiciones. Los inventarios y contenedores mantienen su GUI a la frecuencia de frames activa mientras se omite el costoso mundo 3D del fondo; al perder el foco, minimizar, estar en menús o AFK, Minecraft conserva su propia política de FPS. El mod incluye interfaz y advertencias en inglés, español de España y español de México.

## Compatibility

- Minecraft 1.21-1.21.11 and 26.1-26.2
- Fabric Loader 0.19.3+
- Java 21 for Minecraft 1.21.x
- Java 25 for Minecraft 26.x
- Fabric API is not required at runtime
- Mod Menu is optional

Herzium is licensed under MIT and created by **zymekoh**.
