# Morro visual system

The interface is warm monochrome on pure black. No hue gradients, no glowing chrome, no second
accent. Depth comes from surface steps, hairlines and light, and each screen has exactly one moment
that carries the character.

## Direction

- **Product.** An alarm that makes you say a promise before it stops.
- **Character.** Calm, warm, a little stubborn.
- **World.** A windowsill before sunrise: black glass, warm paper-white light, soft edges.
- **Moments.** The mascot on the opening screen; the dial on the dashboard; the wake-up time at
  display size in the setup sheet.
- **Signature.** A short hand-drawn rule under the wordmark, and an elapsed-hour arc on the dial.

## Colour

One warm neutral ramp. Nothing tints the ground.

| Role | Value |
| --- | --- |
| Canvas | `#000000` |
| Surface (sheets) | `#0B0A08` |
| Surface card | `#121110` |
| Surface elevated (inputs, selection) | `#1A1714` |
| Text primary ("warm honey white") | `#EFE7D6` |
| Text secondary | `#A79E8D` |
| Text tertiary | `#7C7466` |
| Honey highlight (arc, armed dot, second hand) | `#E8C58F` |
| Mascot eyes only (dusty rose) | `#E8B4AE` |
| Hairline | text primary at 8% (`#14EFE7D6`) |
| Hairline, strong / active | 18% / 35% |

Rules:

- The only warm mark on a screen is the honey highlight, and it appears once or twice.
- Dusty rose is reserved for the mascot's eyes and nowhere else: no chrome, no status, no text.
  It is deliberately not part of `MorroColorPalette`, so a screen cannot reach it by accident.
- Primary actions are a warm off-white fill with near-black text. That is the hierarchy on black.
- Status is tonal. "Armed" is a honey dot plus the word; nothing turns green or red at rest.
- No gradient runs from one hue to another anywhere. A single-neutral tone shift is the only
  gradient allowed, and only inside the mascot body and the dial face.

## Type

System sans, weights 400/500 (600 rare). Hierarchy from size and colour, never weight or tracking.

| Use | Size / line height |
| --- | --- |
| Sheet title | 26 / 32, medium |
| Screen title | 30 / 37, medium |
| Question | 26 / 33, medium |
| Body | 15 / 23 |
| Secondary, helpers | 13–14 / 19–21 |
| Labels, metadata | 12 / 16 |

- Sentence case everywhere. No uppercase tracked labels, no negative tracking below 16sp.
- Monospace only for figures with a fixed rhythm: the clock readout, the time display, the
  character counter, the system status values.
- Numbers use one format across a screen.

## Space and shape

- 4px rhythm: 4, 8, 12, 16, 20, 24, 28, 32, 40, 48.
- Cards 16dp radius, buttons and inputs 12dp, chips 10dp, sheet 20dp. Not everything is a pill.
- Card padding 18–20dp, screen edges 24dp. Group gaps smaller than the gaps between groups.
- Selected rows are a border step (35%) plus a check, never a coloured wash.

## Motion

Springs, one language:

| Use | Damping / stiffness |
| --- | --- |
| Default (layout, focus) | 1.0 / 322 |
| Settle after a gesture | 0.82 / 386 |
| Press | 1.0 / 2741 |
| Playful pop (buttons, mascot) | 0.55 / 438 |

- Buttons dip to 0.96 on a press and return with a ~1% overshoot. That is the whole playfulness;
  no glow, no shimmer, no hover scale on cards.
- The mascot runs a hop cycle: crouch, stretch, float, land squash, recover, with a slow breath
  and a blink.
- Clouds drift left on one clock, 150s per full pass, with a few pixels of sway. Intro screens
  only; the alarm setup and the dashboard stay still.
- Everything respects `ANIMATOR_DURATION_SCALE = 0`. Reduced motion freezes the mascot hop, the
  clouds and the pulse, and keeps the entrances as fades.

## Screens

1. **Splash.** Clouds, mascot at 124dp, wordmark, signature rule, one line of copy. Tap to continue.
2. **Questions.** "Question 1 of 5", a 2dp progress rail, one question at 26sp, flat answer rows.
   Selected = 35% border + check. Last answer is free text with a shake on empty submit.
3. **Permissions.** Two rows, each with an icon, a plain sentence of why and its live status.
   A quiet reliability card below; Continue never depends on it.
4. **Dashboard.** Wordmark and one privacy link; the dial; the digital readout; one alarm card with
   the time at 44sp, the mission as a line of text, the arm switch and one filled action.
5. **Alarm sheet.** Title and one line, "Time" then the time at 62sp, "Mission" then three rows.
   Selected row = border + honey check. "Save and arm" is the only filled surface.

## Anti-patterns (do not reintroduce)

- Orange-to-pink or any hue gradient; gradient borders; sweeping rim glows.
- Uppercase letter-spaced eyebrows ("WAKE-UP MISSION", "MORRO • 01 / 05").
- Pill everything: pills are for pips and the sheet handle only.
- Looping pulses on things that are not live.
- Emoji as icons; more than three type roles; shadows on resting cards.
