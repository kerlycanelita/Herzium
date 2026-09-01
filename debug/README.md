# Herzium Debug

Herzium Debug 0.2.0 is a temporary, client-only diagnostic companion for
Herzium on Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2. It observes the
path from a normal key or mouse event through
Vanilla's key queue, Herzium's render-only preview, Vanilla's final hotbar
selection and the relevant outgoing packet metadata.

It does not press keys, consume clicks, change a selected slot, open a screen,
alter a cooldown, cancel or create a packet, or modify the world. Chat contents
and typed characters are never recorded.

## Viewer

Open **Mods → Herzium Debug → Configure**. The responsive viewer provides:

- a live status strip for FPS, frame time, ticks, mouse events, relevant packets,
  Herzium HUD hooks and detected issues;
- a color-coded, scrollable event log;
- **Add marker** to delimit a manual test;
- **Freeze view**, which pauses only the visible snapshot while collection
  continues;
- **Copy full report**, which copies environment details, loaded mods, the
  privacy boundary, current status and retained events.

The in-memory ring retains the newest 6,000 events. A persistent UTF-8 log is
written asynchronously to `logs/herzium-debug/` inside the active instance.

## Diagnostic coverage

- logical hotbar bindings and duplicate bindings;
- hotbar bursts that cross Vanilla's processing boundary, tracked as separate
  active and confirmation generations;
- Herzium HUD and ordinary-item hand previews;
- Vanilla confirmation latency and carried-item packets;
- remapped Use/Place, attack, scroll and offhand input;
- relevant use, attack, block-breaking and container calls;
- hand-rendered versus authoritative item states;
- inventory/screen transitions, focus, cursor position and Vanilla mouse
  grab/release behavior;
- frame/tick timing and bounded duplicate-packet warnings.

Packet logging is metadata-only and limited to hotbar, hand, interaction and
container diagnosis. The server remains authoritative.
