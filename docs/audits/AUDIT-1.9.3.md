# Auditoría Herzium 1.9.3 — objetivo Minecraft 26.1.2

**Fecha:** 2026-08-24 · **Auditor:** Claude (Opus 4.6) · **Estado del documento:** vigente

> **Para agentes que lean esto:** esta auditoría ya se hizo. **No la repitas.**
> Antes de auditar cualquier archivo listado en «Cobertura», lee este documento.
> Si corriges un hallazgo, cambia su `Estado` a `corregido` **en este archivo**
> y anota el commit. Si el código cambia de forma que invalide un hallazgo,
> márcalo `obsoleto` con una línea de explicación. No borres entradas.

---

## Cobertura

### Revisado línea a línea

Todo el código fuente de `src/`, objetivo 26.1.2:

```
src/main/java/dev/zymekoh/herzium/Herzium.java
src/client/java/dev/zymekoh/herzium/HerziumClient.java
src/client/java/dev/zymekoh/herzium/compat/ExternalInputCompatibility.java
src/client/java/dev/zymekoh/herzium/compat/KoHsiumIntegration.java
src/client/java/dev/zymekoh/herzium/compat/ModMenuIntegration.java
src/client/java/dev/zymekoh/herzium/config/HerziumConfig.java
src/client/java/dev/zymekoh/herzium/diagnostics/CoreDiagnostics.java
src/client/java/dev/zymekoh/herzium/gui/AnimatedPurpleButton.java
src/client/java/dev/zymekoh/herzium/gui/HerziumConfigScreen.java
src/client/java/dev/zymekoh/herzium/gui/HerziumWarningScreen.java
src/client/java/dev/zymekoh/herzium/gui/StartupPixelFont.java
src/client/java/dev/zymekoh/herzium/input/ImmediateHotbarInput.java
src/client/java/dev/zymekoh/herzium/render/CombatItemClassifier.java
src/client/java/dev/zymekoh/herzium/mixin/ContainerFocusBackgroundMixin.java
src/client/java/dev/zymekoh/herzium/mixin/ContainerFocusRendererMixin.java
src/client/java/dev/zymekoh/herzium/mixin/FramerateLimiterMixin.java
src/client/java/dev/zymekoh/herzium/mixin/FramerateLimitTrackerMixin.java
src/client/java/dev/zymekoh/herzium/mixin/GlDeviceMixin.java
src/client/java/dev/zymekoh/herzium/mixin/GuiMixin.java
src/client/java/dev/zymekoh/herzium/mixin/HerziumMixinPlugin.java
src/client/java/dev/zymekoh/herzium/mixin/HotbarVisualMixin.java
src/client/java/dev/zymekoh/herzium/mixin/ItemInHandRendererMixin.java
src/client/java/dev/zymekoh/herzium/mixin/KeyMappingAccessor.java
src/client/java/dev/zymekoh/herzium/mixin/KeyMappingMixin.java
src/client/java/dev/zymekoh/herzium/mixin/LoadingOverlayMixin.java
src/client/java/dev/zymekoh/herzium/mixin/MinecraftMixin.java
src/client/java/dev/zymekoh/herzium/mixin/MouseHandlerMixin.java
src/client/java/dev/zymekoh/herzium/mixin/TitleScreenMixin.java
src/client/java/dev/zymekoh/herzium/mixin/WindowMixin.java
```

Configuración y build:

```
build.gradle · settings.gradle · gradle.properties
version/build.gradle · version/settings.gradle · version/gradle.properties
version/build-all.ps1
version/official26/build.gradle · settings.gradle · gradle.properties
src/main/resources/fabric.mod.json
src/client/resources/herzium.client.mixins.json
src/main/resources/assets/herzium/lang/{en_us,es_es,es_mx}.json
README.md · CHANGELOG.md · MODRINTH.md · checksums.txt
evidence/1.9.3-26.1.2/README.md · .gitignore · .gitattributes
```

### NO revisado — pendiente si alguien quiere completarlo

| Ruta | Motivo |
| --- | --- |
| `src/client/java/.../mixin/compat/ExordiumBufferInstanceMixin.java` | El puente de archivos no pudo leerlo (anidamiento > 7 niveles). **Único archivo Java del proyecto sin revisar.** |
| `version/src/client/{classic,common121,modern-button,public-selected,tracker-basic,tracker-modern121}/**` | Overlays de la matriz. Revisados a nivel de configuración de build (qué se incluye y cuándo), no línea a línea. |
| `version/official26/src/client/26.2/.../WindowMixin.java` | Ídem. |

### Comprobaciones automáticas ya ejecutadas

| Comprobación | Resultado |
| --- | --- |
| Paridad de claves entre `en_us`, `es_es`, `es_mx` | **86 claves en las tres.** 0 faltantes, 0 sobrantes en ambas direcciones. |
| Claves `translatable()` usadas en Java que no existen en `en_us` | **Ninguna.** |
| Claves en `en_us` no referenciadas desde ningún `.java` | **22** (ver `M-05`; `modmenu.*` no cuenta, las consume Mod Menu). |
| Inventario de rutas del repositorio | 1453 entradas; 11 rutas basura identificadas (ver `ORG-01`). |

---

## Hallazgos

Estado posible: `abierto` · `corregido` · `aceptado` (decisión consciente de no arreglar) · `obsoleto`.

### Severidad alta

| ID | Estado | Archivo | Resumen |
| --- | --- | --- | --- |
| H-01 | corregido `d6c81f8` | `input/ImmediateHotbarInput.java:29,171` | Fuga de memoria: retiene `LocalPlayer` → `ClientLevel` |
| H-02 | corregido `135e964` | `mixin/MinecraftMixin.java:86-107` | Reescribe `options.txt` del usuario en cada frame |
| H-03 | corregido `6897614`, `da695d0` | `mixin/MinecraftMixin.java:54-67` | `@Redirect` sin `ordinal` sobre `Runnable.run()` |
| H-04 | corregido `9f1e966` | `mixin/ContainerFocus{Background,Renderer}Mixin.java` | Los dos mixins se asumen mutuamente sin comprobarlo |

**H-01 — Fuga de memoria en la preview de hotbar.**
`PREVIEW` es un `AtomicReference<PreviewState>` estático y `PreviewState` guarda
referencia fuerte a `LocalPlayer`. Lo único que lo limpia es `onVanillaHotbarTick`,
inyectado en el TAIL de `Minecraft.handleKeybinds`, que vanilla no llama sin nivel
cargado. El fail-safe de 2 s (`FAIL_SAFE_PREVIEW_NANOS`) hace que `previewIsValid`
devuelva `false` pero **no libera la referencia**. Pulsar una tecla de hotbar y
desconectar en esa ventana retiene `LocalPlayer → ClientLevel →` chunks y entidades
hasta la siguiente pulsación en otra partida.
*Fix sugerido:* llamar a `clearPreview()` desde `herzium$keepCoreOptionsOptimized`
(ya corre en cada `runTick`) cuando `minecraft.player == null`; o `WeakReference`.

**H-02 — Escritura a la configuración persistente del usuario.**
`herzium$enforceCoreOptions` corre en el HEAD de cada `runTick` y hace `set()` sobre
`enableVsync`, `framerateLimit` y `smoothCamera` del `Options` real. Consecuencias:
(a) los valores quedan sobrescritos en `options.txt` y **sobreviven a la desinstalación
del mod**; (b) la pantalla de Ajustes de vídeo se vuelve inoperante — el valor rebota
al frame siguiente; (c) `smoothCamera = false` incondicional impide activar la cámara
cinemática para siempre. La escritura es **redundante**: `WindowMixin`, `GlDeviceMixin`,
`FramerateLimiterMixin` y `FramerateLimitTrackerMixin` ya interceptan el comportamiento
real. Solo se tocan las opciones para que la UI «se vea coherente».
*Fix sugerido:* dejar de escribir en `Options`; interceptar el getter del widget si se
quiere coherencia visual. Como mínimo respetar `smoothCamera` y hacer copia de los
valores originales al arrancar.

**H-03 — `@Redirect` sin `ordinal`.**
El redirect que muestra la advertencia apunta a `Ljava/lang/Runnable;run()V` en
`onGameLoadFinished` sin `ordinal`. Mixin lo aplica a **todas** las invocaciones
coincidentes; hoy hay una sola. Si Mojang añade otra o la mueve a un lambda, la
advertencia intercepta el runnable equivocado y el arranque se cuelga sin error visible.
Además `@Redirect` es exclusivo: rompe cualquier otro mod que toque la pantalla inicial
(onboarding, Quick Play, launchers de perfiles).
*Fix sugerido:* `ordinal = 0` como mínimo; migrar a `@WrapOperation` de MixinExtras
(incluido en Fabric Loader 0.19.3) para que el punto sea compartible.

**H-04 — Acoplamiento no verificado entre los dos mixins de container focus.**
`ContainerFocusRendererMixin` fuerza `renderLevel = false` con un `AbstractContainerScreen`
abierto; `ContainerFocusBackgroundMixin` pinta un degradado opaco (`0xFA…`) para tapar
el hueco. Ninguno comprueba el estado del otro. Si el renderer no aplica —cambio de firma
en `GameRenderer.extract`/`render`, otro mod con prioridad, o la variante `common121` de
la matriz— el fondo **sí** se aplica: panel morado opaco encima de un mundo que se sigue
renderizando a coste completo. Lo peor de las dos opciones, sin aviso.
*Efectos secundarios a documentar aunque todo funcione:* F2 con inventario abierto ya no
captura el mundo; el jugador pierde visión periférica usando un cofre — cambio con
impacto jugable presentado como optimización.
*Fix sugerido:* flag compartido por frame + hacerlo desactivable (ver `M-01`).

### Severidad media

| ID | Estado | Archivo | Resumen |
| --- | --- | --- | --- |
| M-01 | corregido `2736bc4`, `5c01c8a` | `config/HerziumConfig.java` · `gui/HerziumConfigScreen.java` | Cero opciones configurables; el panel es solo lectura |
| M-02 | obsoleto `fa14f76` | `build.gradle:32-34` · `version/official26/build.gradle:88-90` | `implementation` en vez de `modImplementation` |
| M-03 | abierto (fuera de alcance) | `version/build.gradle:63-92` · `version/official26/build.gradle:38-61` | Backports por sustitución de texto plano |
| M-04 | abierto (fuera de alcance) | `version/build-all.ps1:298` | Ruta del JDK escrita a mano |
| M-05 | corregido `1d505db` | `assets/herzium/lang/*.json` · `mixin/LoadingOverlayMixin.java:41-53` | 22 claves muertas; pantalla de carga en inglés fijo |
| M-06 | corregido `d03f129` | `mixin/LoadingOverlayMixin.java:105-144` | Reimplementa el ciclo de cierre de vanilla |
| M-07 | corregido `04ccdcc` | `mixin/GuiMixin.java:18-43` | `@Redirect` no componible + clamp del indicador de ataque |
| M-08 | corregido `7ab9544` | `fabric.mod.json:29` | Dependencia de Minecraft de coincidencia exacta |
| M-09 | corregido `2e12ea9` | `README.md` · `checksums.txt` · `evidence/…/README.md` | Documentación de release contradictoria |

**M-01.** `HerziumConfig` tiene un solo campo: `startupWarningAcknowledged`. La pantalla
de Mod Menu, pese a sus ~800 líneas, es un panel de diagnóstico de solo lectura. Todo
—container focus, equip dip, límites de FPS, raw input— es «core siempre activo». Eso
convierte cualquier incompatibilidad en «desinstala el mod».
*Fix sugerido:* tres booleanos (`containerFocus`, `instantEquip`, `hotbarPreview`) leídos
en el punto de inyección, no en la aplicación del mixin, para que funcionen en caliente.

**M-02.** El proyecto raíz y `official26` declaran `fabric-loader` y `modmenu` con
`implementation`. Con Loom, un jar de mod debe entrar por las configuraciones `mod*` para
remapearse de intermediary a nombres. `version/build.gradle` **sí lo hace bien**
(`modImplementation` + `modCompileOnly(…) { transitive = false }`) — es una inconsistencia
entre tres builds que producen el mismo mod.
*Fix sugerido:* copiar el bloque `dependencies` de `version/build.gradle` a los otros dos.

> **`obsoleto` (2026-08-23, `fa14f76`).** El fix propuesto no es aplicable: el proyecto
> raíz aplica el plugin **`net.fabricmc.fabric-loom`**, que no crea ninguna configuración
> `mod*`. Comprobado enumerando las configuraciones del proyecto con Gradle sobre Java 21
> y sobre Java 25: solo existen `modCompileClasspath`, `modCompileClasspathMapped` y
> `productionRuntimeMods`. La matriz usa el plugin **`fabric-loom`**, que sí las crea.
> Cambiar el id no es un cambio local: con `fabric-loom` el build raíz falla primero con
> *«Minecraft 26.1.2 requires Java 25 but Gradle is using 21»* y después con
> *«Configuration 'mappings' has no dependencies»*, una dependencia que este build nunca
> ha declarado. Se aplicó lo que sí captura la intención —Mod Menu pasa a `compileOnly`
> y deja de ser transitivo— y se dejó `fabric-loader` en `implementation`: sus clases no
> son intermediary y no necesitan remapeo. El hallazgo se cierra como incorrecto en su
> premisa, no como arreglado.
>
> Efecto colateral: Mod Menu deja de estar en el classpath de ejecución de `runClient`.
> Para poder abrir la pantalla de configuración en el entorno de desarrollo hay que dejar
> el jar en `run/mods/` (está ahí desde la verificación de 2026-08-23; `run/` está
> ignorado por git).

**M-03.** La tarea `Sync` adapta la API entre versiones con `filter { line.replace(…) }`
línea a línea (`GuiGraphicsExtractor→GuiGraphics`, `extractRenderState→render`,
`renderHandsWithItems→submitHandsWithItems`…). Sustituye igual dentro de comentarios,
javadoc, literales de string y descriptores de anotaciones de Mixin. **Ya hay reglas
muertas**: `.replace('require = 4', 'require = 0')` y `'require = 2'` no coinciden con nada
en las fuentes actuales.
*Fix sugerido:* mover cada divergencia real a un overlay por versión (el mecanismo ya
existe en `version/src/client/<variant>`) y añadir un `doLast` que falle si una regla de
`replace` no encontró coincidencias.

> **Fuera de alcance (2026-08-23).** `version/` no se toca en esta ronda. Ver
> «Deuda con la matriz multiversión» al final del documento.

**M-04.** `-Dorg.gradle.java.home=C:\Program Files\Java\jdk-25.0.2` ata la compilación de
los 4 targets 26.x a una instalación concreta de una máquina concreta.
*Fix sugerido:* leer `$env:JAVA_HOME_25` con la ruta actual como fallback, o dejar que
resuelva el toolchain de Gradle (ya declarado, `JavaLanguageVersion.of(25)`).

> **Fuera de alcance (2026-08-23).** `version/` no se toca en esta ronda. Nota de apoyo:
> el requisito es real y no arbitrario. El plugin `fabric-loom` exige que el **daemon de
> Gradle** corra en Java 25 para 26.x, no solo el toolchain de compilación; se reprodujo
> al intentar aplicar M-02 en el build raíz. Un `JAVA_HOME_25` con fallback resuelve el
> caso de esta máquina; lo portable en Gradle 9 es `gradle/gradle-daemon-jvm.properties`
> con `toolchainVersion=25`.

**M-05.** Las tres lenguas tienen paridad perfecta pero 22 claves no las usa nadie.
Catorce son `herzium.loading.title`, `herzium.loading.subtitle` y `herzium.loading.tip.0…11`:
`LoadingOverlayMixin` tiene los doce consejos escritos a mano en una `List.of(…)` en inglés.
La razón técnica es válida —durante la primera recarga el sistema de idiomas no existe—
pero contradice la promesa trilingüe del README y deja 22 entradas duplicadas en tres
archivos que mantener.
Lista completa de claves muertas: `herzium.loading.{title,subtitle}`,
`herzium.loading.tip.0`…`.11`, `herzium.state.enabled`, `herzium.state.disabled`,
`herzium.config.issue.preview_mismatch`, `herzium.config.state.inventory_uncapped`,
`herzium.config.subtitle.kohsium`, `herzium.option.immediate_hotbar_selection.description`.
*Fix sugerido:* borrarlas, o leer el `.json` del idioma directamente del classpath al arrancar.

**M-06.** El mixin cancela `tick()` por completo y ejecuta su propia versión:
`checkExceptions()`, `onFinish.accept(…)`, re-`init()` de la pantalla y `setOverlay(null)`.
Las comprobaciones defensivas están bien pensadas (no borra un overlay ajeno, relanza el
layout con las fuentes ya cargadas). El problema es estructural: cualquier paso que Mojang
añada a `LoadingOverlay.tick` se pierde en silencio, sin error de compilación ni de mixin.
*Fix sugerido:* si es viable, sustituir el cancel total por `@ModifyConstant` /
`@ModifyExpressionValue` sobre la duración del fade.

**M-07.** Dos `@Redirect` sobre `LocalPlayer.getAttackStrengthScale(F)F` en
`extractCrosshair` y `extractItemHotbar`. Al ser exclusivo, rompe cualquier otro mod de HUD
en esos call sites — y el archivo vecino `HotbarVisualMixin` sí usa `@ModifyExpressionValue`,
la forma componible. Aparte, `herzium$safePartialTick()` satura el partial tick a `0.5`: el
indicador se congela en la segunda mitad de cada tick y salta al inicio del siguiente. Es
más conservador que vanilla, **no equivalente**.
*Fix sugerido:* migrar los dos a `@ModifyExpressionValue`; documentar o interpolar el clamp.

**M-08.** `"minecraft": "${minecraft_version}"` se expande a `26.1.2` literal → igualdad
estricta. El jar se niega a cargar en 26.1.3 aunque nada haya cambiado.
*Fix sugerido:* expandir a rango (`~26.1.2` o `>=26.1.2 <26.2`) y decidirlo por target.

**M-09.** Tres desajustes concretos:
- README dice que el build raíz genera `herzium-1.9.2.jar`; `gradle.properties` tiene `mod_version=1.9.3`.
- `checksums.txt` lista los 16 jars de la **1.8.7**, dos versiones atrás.
- El hash que `checksums.txt` da para 26.1.2 (`cf12fa27…`) no es el que `evidence/README.md` declara validado (`E1F01D75…`).

*Fix sugerido:* generar `checksums.txt` desde una tarea de Gradle al final de `build-all`;
sacar la versión del README de `gradle.properties`.

### Severidad baja

| ID | Estado | Archivo | Resumen |
| --- | --- | --- | --- |
| L-01 | corregido `69b1c71` | `mixin/ContainerFocusRendererMixin.java:32` | `herzium$containerOpen()` es el único auxiliar sin `@Unique` |
| L-02 | corregido `022dd47` | `config/HerziumConfig.java:111` · `gui/HerziumWarningScreen.java:147` | `save()` bloquea el hilo de render |
| L-03 | corregido `1c8c15c` | `fabric.mod.json` | Sin bloque `contact` (homepage/sources/issues) |
| L-04 | corregido `e31f269` | `mixin/ItemInHandRendererMixin.java:43-71` | Anula por completo la lógica de `ItemInHandRenderer.tick()` |
| L-05 | corregido `9ded140` | `diagnostics/CoreDiagnostics.java:52-58` | Contadores estáticos sin reinicio al cambiar de mundo |
| L-06 | corregido `bd513e0` | `input/ImmediateHotbarInput.java:93-107` | Preview colgada hasta 2 s con una pantalla abierta |

### Organización del repositorio

| ID | Estado | Resumen |
| --- | --- | --- |
| ORG-01 | corregido `5888ffe` | 11 rutas basura + material de release mezclado en la raíz |

Rutas basura confirmadas (verificadas vacías o temporales):

```
net/minecraft/client/gui/components/     ← árbol de 8 carpetas, CERO archivos
net/minecraft/client/gui/screens/
net/minecraft/client/multiplayer/
net/minecraft/client/renderer/
tmp/build-all-1.8.5.out.log              ← log de una versión de hace 2 releases
tmp/imagegen/                            ← vacío
version/1.21.11/src/client/compat/       ← overlay vacío
version/src/client/classic/dev/zymekoh/herzium/input/   ← overlay vacío
```

A mover a `docs/`: `MODRINTH.md`, `checksums.txt`, `evidence/`, `media/`.
`.gitignore` no cubría `tmp/` ni `*.out.log` (ya corregido 2026-08-24).

**Herramienta:** `tools/organize-repo.ps1` hace las tres cosas (borrar basura, mover a
`docs/`, limpiar salidas de compilación). Usa `git mv`/`git rm` cuando el archivo está
versionado. Soporta `-WhatIf`, `-KeepBuildOutput`, `-CleanRunDir`.

---

## Verificado correcto — no volver a marcar como problema

Estas decisiones se revisaron y son sólidas. Si alguien las «arregla», rompe algo.

1. **La frontera de confianza se respeta.** Se revisaron los 14 mixins buscando escrituras
   al slot real, consumo de clics, envío de paquetes o cambios de tick/cooldown/alcance.
   **No hay ninguno.** Lo que declara `evidence/README.md` se sostiene contra el código.
2. **`HerziumConfig.save()`** escribe con temporal + `ATOMIC_MOVE`, con fallback a
   `REPLACE_EXISTING` si el FS no lo soporta, y borra el temporal en el `catch`. Correcto.
3. **`CoreDiagnostics` depende solo del JDK** — a propósito, y comentado en el sitio
   correcto: así el plugin de mixins puede registrar aplicaciones antes de que existan las
   clases de cliente. **No añadir imports de Minecraft a esa clase.**
4. **`HerziumMixinPlugin`** evita resolver el target de Exordium cuando el mod no está,
   en lugar de confiar en que el mixin falle en silencio.
5. **`StartupPixelFont`** dibuja rectángulos en vez de texto porque durante la recarga
   inicial no hay fuentes ni shaders. **No sustituir por el `Font` de vanilla** — es
   exactamente el bug que la 1.8.4 arregló.
6. **Paridad de idiomas perfecta** (86/86/86).
7. **La resolución de hotbar imita a vanilla correctamente.** `Math.max(previous.slot(), slot)`
   reproduce el bucle ascendente de `consumeClick`, y el guard
   `vanillaSlot == selectedSlotAtInput` evita que una preview obsoleta contradiga una
   selección real.
8. **Las pantallas se defienden del layout degenerado:** `Math.max(1, …)` y `Mth.clamp` por
   todas partes, scissors comprobados antes de activarse, divisiones por `maxScroll` siempre
   bajo `if (maxScroll > 0)`. A 320×240 no se rompe nada.

---

## Objetivo del mod (resumen de referencia)

Mod **de cliente** para Fabric cuyo único propósito es **quitar los frenos artificiales
del renderizado** —VSync, límites internos de FPS, esperas decorativas— para que lo visual
se actualice a la velocidad real del equipo, **sin tocar nada que el servidor valide**.

- **Quita frenos:** swap interval de OpenGL a 0; limitador de FPS anulado con la ventana
  activa; HUD/hotbar/TAB/inventarios sin el tope de 240 Hz; fades de 2 s y espera de 500 ms
  al crear mundo eliminados.
- **Reduce latencia visual:** *Priority Hotbar* previsualiza la ranura y el ítem en mano
  antes del siguiente tick sin escribir la selección real; equip dip eliminado para ítems
  ordinarios (armas y escudos conservan la transición vanilla); Raw Input on / Smooth Camera off.
- **Optimiza contenedores:** con un inventario abierto omite el mundo 3D y pinta un
  degradado; la GUI se sigue dibujando cada frame.
- **Convive:** desactiva la caché de HUD de Exordium; cede a KoHsium los ajustes de input
  editables; detecta Raw Input Buffer / Ixeris y avisa del solape.
- **Dónde se detiene:** no acelera los 20 TPS, no inventa paquetes, no cambia alcance,
  cooldowns, hitboxes ni el timing de atacar o colocar.

---

## Deuda con la matriz multiversión

Escrito el 2026-08-23, tras aplicar en `src/` los hallazgos de esta auditoría. **Nada de
esto está arreglado**: se documenta para que la próxima persona que toque `version/` sepa
qué quedó desalineado y por qué.

El motivo de fondo es M-03. `version/build.gradle` no tiene fuentes propias: copia
`../src/client/java` y adapta la API con sustituciones de texto plano. Cualquier cambio en
`src/` llega a las dieciséis variantes sin que nadie lo revise, y solo falla cuando falla.

### 1. Fallo funcional silencioso: `ContainerFocusState` (H-04)

`ContainerFocusBackgroundMixin` ahora exige que `ContainerFocusRendererMixin` haya
confirmado la omisión del nivel **en ese mismo frame**, a través de
`render/ContainerFocusState`.

- Las variantes **1.21.x** excluyen `ContainerFocusRendererMixin.java` y usan el overlay
  `version/src/client/common121/…/ContainerFocusRendererMixin.java`, que **no llama a**
  `ContainerFocusState.beginFrame(...)`.
- `ContainerFocusBackgroundMixin` **no** está excluido en esas variantes.
- Resultado: en 1.21.x el flag nunca sale de `false` y **el fondo de container focus deja
  de pintarse**. Compila, arranca, y simplemente no hace nada.

*Qué hay que hacer:* portar el handshake al overlay de `common121`, o excluir también
`ContainerFocusBackgroundMixin` en esas variantes.

### 2. Correcciones que no llegan a las variantes con overlay propio

`MinecraftMixin.java` está excluido para 1.21.x en favor de
`version/src/client/common121/…/MinecraftMixin.java`. Ese overlay conserva la versión
anterior, así que **H-01, H-02, H-03 y L-05 no están aplicados en las doce variantes
1.21.x**: sigue el `@Redirect` sin `ordinal`, sigue la reescritura de `options.txt` en cada
frame, sigue la fuga de `LocalPlayer` y siguen los contadores sin reinicio.

Los métodos nuevos existen y son alcanzables desde ahí
(`ImmediateHotbarInput.releaseStalePreview`, `CoreDiagnostics.observeSession`); nadie los
llama. Compila; el arreglo simplemente no está.

Lo mismo con `AnimatedPurpleButton.java`, excluido en `classic` y `modern-button`: esos
overlays conservan sus copias privadas de `fillRounded` / `drawOutline` / `argb` y por
tanto **no** usan `HerziumTheme`.

### 3. Reglas de `replace` que hay que revisar

| Archivo | Regla | Situación |
| --- | --- | --- |
| `version/build.gradle:89-90` | `require = 4` → `0`, `require = 2` → `0` | **Ya estaban muertas** antes de esta ronda (lo dice M-03) y lo siguen estando. Además ahora son insuficientes: los hallazgos de esta ronda introdujeron `require = 1` en `MinecraftMixin` (H-03), `LoadingOverlayMixin` (M-06) y `GuiMixin` (M-07), y `require = 1` **no** se reescribe a 0. En una variante donde el objetivo no exista, eso es un fallo duro de mixin en vez de una degradación. |
| `version/build.gradle:71` | `extractRenderState` → `render` | Sigue siendo necesaria y ahora también afecta a `HerziumTheme` y a las dos pantallas rediseñadas. Revisar que el nuevo `ParticleField.render(...)` no se vea afectado por sustituciones parciales de nombre. |
| `version/build.gradle:75-76` | `graphics.centeredText(` / `graphics.text(` | `HerziumTheme` no usa ninguna de las dos (solo `fill` y `fillGradient`), así que la clase nueva pasa limpia. Sin cambios. |
| `version/official26/build.gradle:53-60` | `minecraft.getOverlay()`, `minecraft.screen`, `minecraft.setScreen(` | Cubren el código nuevo de `ImmediateHotbarInput.releaseStalePreview` (L-06) y el `setScreen` de H-03. **Verificar**, no dar por hecho. |
| `version/official26/build.gradle` | — | **No hay regla para `LoadingOverlay`.** M-06 introduce `@Shadow private long fadeOutStart` y un `@At` con el descriptor literal `Lnet/minecraft/client/gui/screens/LoadingOverlay;isReadyToFadeOut()Z`. Si en 26.2 esa clase se movió o se renombró —el propio archivo ya renombra `Gui` a `Hud`—, la variante 26.2 **no arranca**. Es el riesgo más concreto de esta lista. |

### 4. Overlays a revisar uno por uno

```
version/src/client/common121/…/MinecraftMixin.java              ← puntos 1 y 2
version/src/client/common121/…/ContainerFocusRendererMixin.java ← punto 1
version/src/client/common121/…/FramerateLimiterMixin.java
version/src/client/classic/…/ItemInHandRendererMixin.java       ← L-04 no aplicado
version/src/client/classic/…/MouseHandlerMixin.java
version/src/client/modern-button/…                              ← AnimatedPurpleButton
version/src/client/public-selected/…/HotbarVisualMixin.java
version/official26/src/client/26.2/…/WindowMixin.java
```

### 5. Lo que sí es seguro

- `herzium.client.mixins.json` **no cambia**: esta ronda no añade ni quita ninguna clase
  de mixin, así que el `"required": true` no se rompe en ninguna variante.
- Los tres `lang/*.json` se comparten y solo ganan y pierden claves de forma simétrica.
  Ninguna variante los referencia de otra manera.
- M-08 construye el rango de Minecraft en el `build.gradle` **raíz**, no en
  `fabric.mod.json`, precisamente para no tocar la matriz. Las dieciséis variantes
  conservan su comportamiento actual.

---

## Verificación de esta ronda (2026-08-23)

Ejecutado con el cliente de desarrollo en 26.1.2:

| Comprobado | Resultado |
| --- | --- |
| `.\gradlew.bat build` | Correcto. `herziumChecksums` genera `docs/checksums.txt`. |
| Arranque completo del cliente | Sin errores de mixin. Con `"required": true` y `defaultRequire: 1`, arrancar **es** la prueba de que todos los inyectores encontraron su objetivo: H-03 (`ordinal = 0`, `require = 1`), M-06 (`isReadyToFadeOut`), M-07 (los dos `@ModifyExpressionValue`) y H-04. |
| Pantalla de carga (M-06) | La recarga inicial termina y el overlay se cierra; no se queda colgado. |
| Advertencia inicial (H-03) | Se muestra; al pulsar Continuar la cadena de Vanilla se reanuda y aparece la pantalla de título. **Sin** el mensaje de fallback, es decir, `original.call(...)` diferido funciona de verdad. |
| `herzium.json` (M-01, L-02) | Se escribe con los cuatro campos y los tres nuevos por defecto en `true`. |
| Mod Menu (L-03) | Muestra los botones Website e Issues y el enlace Source. |
| Pantalla de configuración (fase E) | Los tres conmutadores se dibujan con etiqueta, estado y descripción. El panel de diagnóstico no reporta **ningún** mixin como `not_applied`. |

**Pendiente de comprobación manual en partida:** abrir un cofre, cambiar de ranura con 1-9,
cambiar de ranura con la rueda, equipar una espada y equipar un bloque. Requiere entrar a un
mundo y no se automatizó.

---

## Registro de cambios de este documento

| Fecha | Cambio |
| --- | --- |
| 2026-08-24 | Auditoría inicial de 1.9.3 / 26.1.2. 19 hallazgos + 8 aciertos + ORG-01. |
| 2026-08-23 | Ronda de corrección sobre 26.1.2. Corregidos ORG-01, H-01..H-04, M-01, M-05..M-09 y L-01..L-06; M-02 marcado `obsoleto` con la reproducción del fallo; M-03 y M-04 fuera de alcance. Rediseño de la pantalla de configuración y extracción de `gui/HerziumTheme`. Añadidas las secciones «Deuda con la matriz multiversión» y «Verificación de esta ronda». |
