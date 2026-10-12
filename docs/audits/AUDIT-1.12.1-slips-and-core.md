# Herzium 1.12.1: the click that lands before its key, and what is in the core

A player recorded a session with Herzium 1.12.0, KoHs Anchor's 0.6.0 and Crystal Tweaks 2.6.1
(Minecraft 26.2, a practice server) and reported that the anchor loop felt uncomfortable and slow,
with or without the burst options, and that glowstone was placed again while the anchor was
selected. This audit is the search for the cause in that recording, the same thing reproduced in
the laboratory, and what 1.12.1 changes.

## What the recording showed

The recording was read frame by frame (60 fps): the selected hotbar slot and the count under each
item. A count that drops by one is a use of that item, so every key and every use of the session
has its frame.

- 110 cycles of anchor, glowstone, totem. **67 (61 %) were clean.**
- 139 glowstones spent for 96 anchors. 27 glowstone steps used two, 16 used none; 24 anchor steps
  placed nothing.
- The mistakes come in chains. One of them, frame by frame: a second glowstone goes on the charged
  anchor; the totem step that follows does not detonate; the click meant to place the next anchor
  detonates the old one instead; the next glowstone, with no anchor to charge, lands on the floor
  as a block.
- In 20 of the 27 double glowstones, the totem's slot is selected exactly three frames, one tick,
  after the second glowstone.

That last line is the signature of the cause: the click for the totem was pressed a moment
**before** the totem's key.

## The cause: a slip

Two hands press a key and its click together. Now and then the click lands a few milliseconds
first. What happens next depends on who orders the presses:

- **Vanilla** applies every hotbar key of a tick before that tick's clicks. If the click and its
  key land in the same tick, the click uses the key's item: the slip is forgiven without anyone
  noticing. Only when a tick falls between the two does the click run with the old item.
- **The Herzium order** (since 1.10.6) keeps the order of the presses: a key pressed after a click
  waits for the next tick, and the click uses the item held when it was pressed. That is what makes
  "obsidian, use, crystal, use" work, and it is also what took Vanilla's forgiveness away: a click
  that slipped ran with the item of the step before, every time. KoHs Anchor's has its own pressed
  order for anchor bursts, with the same effect.

In the loop the cost is not one click. The slipped click does something with the old item (a second
charge, an anchor on the anchor just placed, a click with the totem on nothing), its key is left
without a click, and the loop is one click late until the player notices and catches up.

## What 1.12.1 does

**A click goes with its key.** When a hotbar key is pressed, the click pressed just before it goes
behind that key if it can only be the key's:

- it is the one click waiting, and nothing else waits (no other key, attack, swap or drop);
- the key came within 30 ms of it;
- the key is not the one of the slot already held;
- the item in hand has already had its click (a use ran since the key or wheel turn that selected
  it), or the hands hold nothing that can be used (an empty hand, a sword, a mace, a totem).

The first click with an item that can be used is never moved: "click, then pick the next item"
stays in pressed order, which is where the Herzium order is better than Vanilla. Two clicks waiting
are a double click, not a slip, and are left alone.

The result is the tick Vanilla sends for the same presses: one slot change, then the click. Nothing
is sent sooner, and no click is created. When a tick falls between the click and its key, Herzium
does what Vanilla does: the click has already run. KoHs Anchor's 0.6.1 goes further for anchor
loops only; see its own audit.

**The core has no switches.** Split bursts, Offhand sync, Hold guard and Ignore key repeat were
options in 1.11 and 1.12.0. With any of them off, a burst sent the wrong item or an extra click,
and none of them made anything faster when off: the recording above was made with the burst options
off. They are the Herzium order now, always on and no longer saved. Three options are gone and what
they did is off:

| Option | Why it left |
| --- | --- |
| Strict attacks | "Attack, key, use" took two ticks where Vanilla takes one. |
| Mouse-like keys | Dropped the repeats a held use key places with. |
| One click per tick | Drew Grim's `AirLiquidPlace` with 100 ms of ping (1.12.0 audit). |

The settings screen has four tabs: Order, Feel, Monitor and KoHs. A `herzium.json` from 1.12.0
keeps its order and its Feel switches; its burst and guard keys are ignored.

**The monitor names the mod.** The tick shape monitor counted "a hotbar change after a click in the
same tick" in the recorded session and could not say who sent it. The first time it happens in a
session, the log now gets the call stack of the click and of the hotbar change.

## Measured

Minecraft 26.2 against Grim Anticheat 2.3.74 with its experimental checks on, the player's keys
(use on the right mouse button), clicks held 70 ms, Ravenclaw's Ping Equalizer for latency. The
bench is the loop itself: anchor, glowstone, totem on one block, 40 cycles, never rebuilt, so a
mistake is carried into the next cycle as it is in a fight. Three rhythms, each with the same
presses for every build (the random seed is the rhythm):

- **own**: the player's rhythm, 130 ms per step, key 35 ms before its click;
- **slip**: the same, with 20 % of the clicks pressed 8 to 18 ms before their key;
- **fast**: 65 ms per step.

A loop that lost its step fails every cycle after it, so "cycles completed" amplifies one mistake.
The slip columns count each slip on its own, only while the loop was in step.

**Added latency: 0 ms**

| Build | own: cycles | own: click to use, median / p90 | slip: cycles | slips in the same tick | slips across ticks | fast: cycles | Grim alerts | Ticks Vanilla cannot send |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Vanilla | 40/40 | 25 / 44 ms | 91/120 | 31/31 | 1/19 | 89/120 (4.4/s) | none | 0 |
| Herzium 1.12.0 + KoHs Anchor's 0.6.0 (the recording) | 40/40 | 25 / 45 ms | 47/120 | 0/13 | 0/4 | 118/120 (5.0/s) | none | 0 |
| Herzium 1.12.1 + KoHs Anchor's 0.6.1 | 40/40 | 24 / 48 ms | 120/120 | 44/44 | 22/22 | 117/120 (5.0/s) | none | 0 |
| Herzium 1.12.1 alone | 40/40 | 24 / 45 ms | 85/120 | 37/37 | 0/15 | 120/120 (5.1/s) | none | 0 |

**Added latency: 100 ms**

| Build | own: cycles | own: click to use, median / p90 | slip: cycles | slips in the same tick | slips across ticks | fast: cycles | Grim alerts | Ticks Vanilla cannot send |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Vanilla | 38/40 | 29 / 45 ms | 98/120 | 32/32 | 0/18 | 29/120 (3.4/s) | AirLiquidPlace x14 | 0 |
| Herzium 1.12.0 + KoHs Anchor's 0.6.0 (the recording) | 40/40 | 28 / 47 ms | 81/120 | 0/15 | 3/6 | 103/120 (4.4/s) | AirLiquidPlace x4 | 0 |
| Herzium 1.12.1 + KoHs Anchor's 0.6.1 | 40/40 | 28 / 47 ms | 117/120 | 39/39 | 19/22 | 97/120 (4.1/s) | none | 0 |

- *cycles*: cycles whose anchor exploded, of the cycles played. "slip" and "fast" are three runs of
  40 cycles each, with the same presses; the runs differ in where the ticks fall.
- *click to use*: from the press of a click to the moment the game used the item, at the player's
  rhythm.
- *slips*: slips whose click used its key's item, counted only while the loop was in step.
- *Grim alerts* over the three rhythms; *ticks Vanilla cannot send*: the laboratory's own judge (a
  hotbar change after a click of the same tick, the same slot twice, three slots in one tick).

The release sessions of 1.12.0 were run again with the 1.12.1 JAR as a fresh install leaves it:
crystals, anchors, rails and carts, offhand swaps, the wheel and random bursts; clicks released at once
and held 80 ms; Grim as shipped and with its experimental checks on; alone and next to KoHs Anchor's and
Crystal Tweaks; 0, 50 and 100 ms. Every click used the item of its key, no use was added, and the judge
found no tick Vanilla could not send. The only alerts were two `AirLiquidPlace` in a random burst of
wool, three clicks of one tick at one block, which Vanilla sends the same way. Without Strict attacks,
the crystal hit 120 ms after the placement broke every crystal under strict Grim (1.12.0: 35 and 48 %).

## What this does not claim

- The 30 ms window is a choice, not a measurement of anyone's hands: the recording shows that the
  slips exist and how often, not how many milliseconds each one was.
- A slip with a tick between the click and its key is still Vanilla's result in Herzium alone.
- Herzium alone was measured without added latency only: in the laboratory's setup without KoHs
  Anchor's, at 100 ms the anticheat's corrections move the item in hand and the rows cannot be
  compared.
- Other anticheats exist. Every tick 1.12.1 sends is one Vanilla sends for the same presses; that
  is the criterion, and Grim is a second opinion on it.
