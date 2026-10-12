# Herzium 1.12.0: why 1.11 felt worse than Vanilla, and what changed

Players on 26.2 and 1.21.11 reported that Herzium 1.11 was worse than playing
without it: blocks that appeared and vanished ("ghost blocks"), and hotbar keys
that sometimes did not seem to react. This audit is the search for the cause,
what 1.12 does about it, and how far a server's anticheat can see any of it.

## What was found

### 1. The held button (the ghost block)

The 1.11 laboratory pressed and released every click at once. A hand does not:
a click lasts 60 to 120 ms, longer than a tick. Vanilla's `handleKeybinds` has
two ways to use an item. The first consumes clicks. The second runs after it,
in the same tick:

```
keyUse.isDown() && rightClickDelay == 0 && !player.isUsingItem()  ->  startUseItem()
```

With Split bursts, a click whose key was pressed after another click waits one
tick. In that tick the click is still in the queue, nothing has used an item,
`rightClickDelay` is 0 and the button is still down, so the second path fires
by itself with the item of the *previous* key. One extra use, with the wrong
item, that the player never pressed.

Bytecode of `Minecraft.handleKeybinds`, identical from 1.21.10 to 26.3:
`rightClickDelay` is read once and `continueAttack(Z)` is called once, so the
hook is the same in the seven builds.

Measured on 26.2 against Grim 2.3.74 with experimental checks, "swap, key,
use" pressed inside one tick (`hb1`) and 15 ms apart at a random moment
(`hb15`), 25 bursts each:

| Build | Hold | Order | Extra uses `hb1` / `hb15` | Right item `hb1` / `hb15` |
| --- | --- | --- | --- | --- |
| Vanilla order | 80 ms | Vanilla | 0 / 0 | 100 % / 100 % |
| 1.11.0 | 0 ms | Herzium, split | 0 / 0 | 100 % / 100 % |
| 1.11.0 | 80 ms | Herzium, split | **25 / 7** | **48 % / 80 %** |
| 1.11.0 | 80 ms | Herzium, split + strict | **25 / 10** | **48 % / 72 %** |
| 1.12.0 | 80 ms | Herzium, split | 0 / 0 | 100 % / 100 % |
| 1.12.0 | 80 ms | Herzium, split + strict | 0 / 0 | 100 % / 100 % |

Grim raised no alert in any row, 1.11.0 included: the extra use is a tick
Vanilla can send (two placements in one tick). It was a bug in the hand, not
something an anticheat sees. The laboratory's own judge (below) does count it:
25 double placements in 25 bursts.

**Hold guard** closes it. In a tick where a use click waits, the held-use path
reads `rightClickDelay` as 1 and does nothing; the same for a held attack
through `continueAttack`. The guard acts only in that tick. Holding the button
to keep placing or mining is Vanilla's code, untouched.

### 2. The preview that never came back

When the preview and the game disagreed once about the selected slot, the
preview switched itself off, correctly: the screen must not show a slot the
game did not choose. But it stayed off until the player changed world. From
then on the hotbar followed the 20 Hz tick again, and the mod "did nothing"
for the rest of the match. **Preview recovery** brings it back after three
seconds.

### 3. The held hotbar key

The keyboard repeats a key that stays down, and `KeyboardHandler.keyPress`
registers every repeat as a new click of that binding. A second key pressed on
the same keyboard stops the repeat, so two keyboard keys never showed it. A
slot bound to a **mouse button** does not stop it: hold the sword key, tap the
mouse button of the crystal slot, and the sword key's next repeat was the
newest input again. The slot went back, which reads as "the key did not
react". **Ignore key repeat** counts only real presses for the order.

### 4. Two clicks in one tick: the guard that stays off

A double click inside 50 ms runs both uses in one tick in Vanilla. **One click
per tick** sends the second on the next tick; a click that has already waited
runs then whatever came after it, so nothing is ever more than one tick late.

It was meant to be on. The laboratory says it should not be. Random bursts of
wool, 50 per row, Grim 2.3.74 with experimental checks, the same JAR:

| Order | One click | Added ping | Two placements in a tick | Clicks on a block placed the tick before, same item | `AirLiquidPlace` |
| --- | --- | --- | --- | --- | --- |
| Herzium | off | 0 ms | 27 | - | 0 |
| Herzium | on | 0 ms | 15 | 16 | 0 |
| Vanilla | - | 100 ms | 30 | 4 | 2 |
| Herzium | off | 100 ms | 34 | 3 | 1 |
| Herzium | on | 100 ms | 17 | 22 | **13** |

The second use of a same-tick pair hits the face the first one hit, finds it
taken and does nothing. Moved to the next tick, it hits the block the first
one just placed. The server has not confirmed that block yet when the ping is
over a tick, and Grim, for a player who stands still and did not change slot
in between, has not put it in its own copy of the world either. It reads the
click as a click on air. Vanilla draws the same alert when a double click
happens to fall on two ticks (2 in 50 bursts); the guard turned an accident
into the rule (13 in 50).

Every one of those ticks is a tick Vanilla can send, and the alert is Grim
being late, not the player cheating. It is still an alert, on a regular check,
and a private anticheat may read it the same way. So the switch ships **off**,
with this written in its description. Hold guard, the fix that matters, does
not move any click to a new block and drew no alert at 0 or 100 ms.

## What a server can see

A server sees packets, in order. The order survives any ping: TCP keeps it.
Since 1.21.2 the client also tells the server where each of its ticks ends, so
an anticheat that reads that mark sees the client's ticks exactly, at 20 ms or
at 200 ms. Ping only blurs *when* a packet arrived, never what came before
what. That splits what can be checked into three kinds:

| Kind | Rule | Vanilla | Herzium 1.12 |
| --- | --- | --- | --- |
| Certain | Hotbar change after a use, attack or dig of the same tick | never | never |
| Certain | The same hotbar slot twice in a row | never | never |
| Certain | Three hotbar changes in one tick | never (two at most) | never |
| Rate only | Two placements in one tick | yes, on a fast double click | the same as Vanilla |
| Not usable | The first rule, cut by the *server's* ticks | fires on Vanilla | fires the same |

The last row is why a realistic anticheat cannot do more: two client ticks
that arrive inside one server tick look like "slot after click" although each
client tick was clean. The laboratory counts that naive rule too. It fired on
legitimate sessions at 0 ms of added ping (3 to 9 times per bench of the
1.11.0 release session), so a server that punished it would punish Vanilla.

An offhand swap or a drop before the slot of the same tick is Vanilla's own
order and is not counted as a fault.

MCPVP's anticheat is private. What can be claimed is narrower and checkable:
every tick Herzium 1.12 sent in the laboratory is a tick a Vanilla client can
send, and the three certain rules stayed at 0.

### The stricter judge

Grim with its experimental checks on (`PacketOrderE`, `MultiPlace`) was the
judge of 1.11. Two things are added to it:

- **In the laboratory** (`analyze.py`, `judge`): the table above, counted on
  every bench from the client's own ticks, next to Grim's alerts.
- **In the mod** (Monitor tab, *Tick shape monitor*): the same three counts on
  every packet the client really sends while you play, whichever mod causes
  them. It only observes. The laboratory's probe, which sends the same slot
  twice on purpose, was caught by both Grim (`BadPacketsA`) and the monitor in
  the same session.

Held-click session `hold-112`, 1.12.0, Grim strict, 30 benches: no Grim alert
apart from the probe, and 0 on the three certain rules in every bench.

## The inventory screens

The request was for the hotbar and the inventory screens to feel as if they
ran at a far higher frame rate. For the hotbar that is real and measured: the
slot is drawn on the next frame instead of the next tick, the offhand swap is
drawn at once instead of one ping later, and the response meter shows the
milliseconds.

For the inventory there is nothing honest left to take. Vanilla already
handles a container's clicks, drags and tooltips per input event and draws
them every frame; they are not tied to the tick. What is slow is opening it
(one tick) and the server's answer to each click (the ping). Opening it faster
is what KoHs Inventory Tweaks does, with its own packet-order fence. Answering
for the server would be guessing, and a wrong guess is a ghost item. Herzium
1.12 adds no switch there, because it would be a placebo.

## The ten additions

| Switch | Tab | Default | Packets |
| --- | --- | --- | --- |
| Hold guard | Guards | on | Leaves out the use Vanilla adds for a held button in a tick whose click waits |
| One click per tick | Guards | off | A second use or attack waits one tick |
| Ignore key repeat | Guards | on | None; changes which press counts as newest |
| Mouse-like keys | Guards | off | Leaves out keyboard repeats of use, attack, swap, drop |
| Preview recovery | Feel | on | None |
| Offhand swap preview | Feel | on | None |
| Combat items too | Feel | off | None |
| Slot pop | Feel | on | None |
| Response meter | Monitor | off | None |
| Tick shape monitor | Monitor | on | None |

Hotbar preview, No equip dip and Smooth attack indicator existed before and
are now switches too. Nothing here aims, chooses a slot or acts for the
player; every packet still comes from a key or button the player pressed, and
none is sent sooner than Vanilla would send it.

## Proof

All of it with the seven JARs of `docs/releases/1.12.0-checksums.sha256`.

- `hotbarOrderTest`: 9,464,240 assertions over the order policy, the new
  options (one click and its one-tick limit, held keys, mouse-like keys) and
  the settings layout at every window size from 120x48 to 2600x1440.
- All seven JARs launch in a clean profile, show and close the new settings
  screen, and write a config with the defaults above. 26.3 also passes its
  world test of the hand adapter.
- 1.21.11, 314 order scenarios through the real input path at 1,400 fps: all
  pass, with no preview suspension. It has no laboratory companion; its
  `handleKeybinds` has the same bytecode shape as 26.2 at every hook and the
  policy is the same class.
- 26.2 laboratory, Grim 2.3.74, the JAR as a fresh install leaves it:

| Session | Grim | Benches | Cycles | Right item (Herzium rows) | Extra uses | Certain rules | Grim alerts |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `release-112` | as shipped | 19 | 485 | 100 % | 0 | 0 | 0 |
| `release-player-112` (with KoHs Anchor's and Crystal Tweaks) | as shipped | 10 | 190 | 100 % | 0 | 0 | 0 |
| `release-player-strict-112` | experimental | 10 | 190 | 100 % | 0 | 0 | 0 |
| `release-strict-112` | experimental | 8 | 200 | 100 % | 0 | 0 | 0 |
| `hold-112` (clicks held 0 and 80 ms) | experimental | 30 | 720 | 100 % | 0 | 0 | 0 |

- `guards-112`: each guard off and on in the same JAR at 0 and 100 ms. Hold
  guard off: 25 extra uses in 25 bursts and the right item in 48 %; on: 0 and
  100 %, at both pings. One click per tick: the table of section 4.
- `holdq-1110`: the same held clicks on the published 1.11.0, the baseline of
  section 1.
- Settings screen: `gallery-112` and `kohs-tab-112`, screenshots of every tab,
  the entrance, a tab change and GUI scales 1 to 4 at 1920x1001.
