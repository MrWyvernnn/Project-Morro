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
| Surface high (the lightest step: inset wells, switch track, skeletons) | `#231F19` |
| Text primary ("warm honey white") | `#EFE7D6` |
| Text secondary | `#A79E8D` |
| Text tertiary | `#877F70` |
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
- Monospace only for figures with a fixed rhythm: the wake-up time display, the
  character counter, the system status values.
- Numbers use one format across a screen.

## Space and shape

- 4px rhythm: 4, 8, 12, 16, 20, 24, 28, 32, 40, 48.
- Three radii only: cards 16dp, buttons, inputs and chips 12dp, sheets 24dp top. Not everything is
  a pill. `MorroShapes` holds them; a component that hardcodes a radius is a bug.
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

- Buttons dip to 0.97 on a press and settle back on a critically damped spring: no overshoot. That
  is the whole playfulness; no glow, no shimmer, no hover scale on cards.
- The mascot hovers rather than hops: a slow rise and fall with a second, quicker bob on top, a
  side sway with a matching tilt, a breathing stretch, a two-frame blink whose eyes follow the
  drift, and a hem that ripples. No crouch and no landing squash.
- Clouds are solid, not smoky: a flat base and four round puffs in warm white, one outline, one
  fill, no alpha. They sit along the top edge of the intro screens, cropped by it, and drift left
  on one clock, 180s per pass, with a few pixels of sway. Keeping them in that band is what lets
  them be opaque without ever crossing the copy. Intro screens only; the alarm setup and the
  dashboard stay still.
- The mascot outline is curves only: a dome that flares past the shoulders, a hard tuck at the
  waist, a late flare over the hip, and three round hem scallops. Nothing on it is straight.
- Everything respects `ANIMATOR_DURATION_SCALE = 0`. Reduced motion freezes the mascot hop, the
  clouds and the pulse, and keeps the entrances as fades.

## Screens

1. **Splash.** Clouds, mascot at 126×176dp (taller than wide, so the waist and hip read), wordmark,
   signature rule, one line of copy. It holds for 1.8s and any tap continues immediately.
2. **Questions.** "Question 1 of 5", a 2dp progress rail, one question at 26sp, flat answer rows.
   Selected = 35% border + check. Last answer is free text with a shake on empty submit. Continue
   sits below the options and is explicit: choosing no longer skips ahead, and the button stays a
   hairline until something is chosen, so a mis-tap can be corrected.
3. **Permissions.** Two rows, each with an icon, a plain sentence of why and its live status.
   A two-line reliability card below: a title, one sentence, then two rows that show either "On"
   or a single Allow action. Continue never depends on it.
4. **Dashboard.** Wordmark and one privacy link; the dial with the date under it, nothing else
   ticking; one alarm card with the time at 46sp, the mission as a line of text, the arm switch and
   one filled action. Clock and card arrive on separate springs, so the screen assembles.
5. **Alarm sheet.** Title and one line, "Time" then the time at 62sp, "Mission" then three rows.
   Selected row = border + honey check. "Save and arm" is the only filled surface. An expanded
   mission states its setting in place ("Object pool / 4 of 30 objects chosen") with one quiet
   Choose action, and the pool itself is a plain two-column list of named tiles with a check.

## Anti-patterns (do not reintroduce)

- Orange-to-pink or any hue gradient; gradient borders; sweeping rim glows.
- Uppercase letter-spaced eyebrows ("WAKE-UP MISSION", "MORRO • 01 / 05").
- Pill everything: pills are for pips and the sheet handle only.
- Looping pulses on things that are not live.
- Emoji as icons; more than three type roles; shadows on resting cards.
