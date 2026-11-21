# Morro design routing — read before any UI work

**Authority order:**
1. `app/.../ui/theme/Theme.kt` and `ui/motion/MorroMotion.kt` — the actual tokens
2. `design/MORRO_VISUAL_SYSTEM.md` — the design spec
3. This file — which skill to use when
4. Any skill in `.freebuff/skills/` — generic advice, always subordinate to 1 and 2

**A skill never overrides a Morro token.** If a skill tells you to add dynamic colour,
a tonal palette, an expressive shape, a glow, or a gradient, and Morro's tokens don't
have it — the skill loses. Every time.

---

## The system in one paragraph

Warm monochrome on pure black. Colour is not the brand — **light** is. One warm neutral
ramp, one honey highlight used once or twice per screen, one dusty rose that belongs to
the mascot's eyes and nothing else. Depth comes from surface steps plus a 1px hairline,
never from shadows or glows. Sentence case everywhere. System fonts. Exactly one moment
per screen carries the character.

## Tokens — verified against code

| Role | Value |
|---|---|
| Canvas | `#000000` |
| Surface (sheets) | `#0B0A08` |
| Surface card | `#121110` |
| Surface elevated (inputs, selection) | `#1A1714` |
| Surface high (inset wells, switch track) | `#231F19` |
| Text primary (honey white, never `#FFFFFF`) | `#EFE7D6` |
| Text secondary | `#A79E8D` |
| Text tertiary (5.3:1 on black, AA at 12sp) | `#877F70` |
| Text disabled | `#6B6458` |
| Honey — the one warm mark | `#E8C58F` |
| Cloud white | `#F6EFE1` |
| Mascot eyes only | `#E8B4AE` |
| Hairline / strong / active | `#EFE7D6` at 8% / 18% / 35% |
| Success (tonal sage) | `#B9C4A6` |
| Error (tonal sand) | `#D8C1A4` |

**Radii:** card 16dp · button/input/chip 12dp · sheet 24dp top. Three steps. A component
that hardcodes a radius is a bug — use `MorroShapes`.

**Type:** system sans, 400/500 (600 rare). Hierarchy from size and colour, never weight
or tracking. Sentence case. Monospace only for fixed-rhythm figures.

**Motion:** `MorroMotion` holds durations and easings; `MorroSpring` holds springs.
Enter 220ms ease-out, exit at 80% of enter, stagger 30ms capped at 6 steps.
Press dips to 0.97. Everything honours `ANIMATOR_DURATION_SCALE = 0`.

---

## Skill routing — all 25

### Use constantly

| Skill | When |
|---|---|
| `compose-ui` | Any Compose layout work. Default first call. |
| `compose-component-design` | Designing a new composable's API — params, slots, modifier order, hoisting. |
| `compose-state-and-effects` | `remember` keys, `LaunchedEffect` correctness, state hoisting, side-effect placement. |
| `compose-animations` | Implementing motion. **Feed it Morro's springs and easings — never its defaults.** |
| `jetpack-compose-performance` | Recomposition audits, stability, `derivedStateOf`, lazy lists. |
| `edge-to-edge` | Insets, system bars, `windowInsetsPadding`. Morro draws behind both bars. |
| `adaptive` | Window size classes. Dashboard already caps at 520dp — extend that pattern, don't replace it. |

### Use as a constraint, not a style

| Skill | Caution |
|---|---|
| `android-design-guidelines` | Platform correctness (touch targets, navigation, accessibility). Take the rules, ignore the visual suggestions. |
| `mobile-android-design` | Same. 48dp targets, thumb reach, one-hand use. Not a palette source. |
| `material-3-expressive` | ⚠️ **Highest-risk skill in the pack.** M3 Expressive pushes dynamic colour, tonal palettes, expressive shapes and motion schemes. Morro deliberately rejects all of it. Use only for component *behaviour* reference. **Never** let it introduce `dynamicColorScheme`, a tonal surface tint, or an M3 shape scale. `surfaceTint` is `Color.Transparent` and stays that way. |

### Use for judgment and critique

| Skill | When |
|---|---|
| `design-review` | Auditing a screen against the anti-pattern list below. Good second pass. |
| `design-taste-frontend` | Taste calls when two options are both technically correct. |
| `ui-ux-pro-max` | Flow and interaction critique. |
| `awesome-design` | Visual quality bar. |
| `brand` | Voice and tone checks. Morro is calm, warm, a little stubborn — never guilt or shame. |
| `design` | General design reasoning. |

### Use for structure and docs

| Skill | Caution |
|---|---|
| `design-system` | Token architecture and naming. Morro's ramp is fixed — extend documentation, not the palette. |
| `create-design-md` | Generating design docs. Point it at `MORRO_VISUAL_SYSTEM.md` as the source. |
| `theme-factory` | ⚠️ Generates palettes. **Do not run it to produce colours.** Morro's ramp is decided. Only use it to check contrast or structure. |
| `bento` | ✅ Genuinely relevant — the dashboard dock and setup sheet are bento cards. Use for grouping and hierarchy. |

### Do not use in this repo

| Skill | Why |
|---|---|
| `frontend-design` | Web. This is Compose. |
| `canvas-design` | Web canvas / graphics. Not applicable. |
| `banner-design` | Marketing banners. Belongs in the website repo. |
| `slides` | Presentations. Not app UI. |
| `apple-design` | ⚠️ iOS patterns — wrong platform. Will suggest SF Symbols, iOS navigation, Apple HIG spacing. Ignore entirely here. |

---

## Anti-patterns — reintroducing any of these is a defect

- Orange, pink, or **any hue-to-hue gradient**. Gradient borders. Rim glows. Bloom.
- Shimmer. (`MorroMotion.ShimmerMillis` exists but shimmer is banned — don't use it.)
- Uppercase letter-spaced eyebrows: "WAKE-UP MISSION", "MORRO • 01 / 05".
- Pill everything. Pills are for status pips and the sheet drag handle only.
- Drop shadows on resting cards.
- Looping pulses on anything not live.
- Emoji as icons.
- More than three type roles on one screen.
- Pure `#FFFFFF` text.
- Dusty rose anywhere except the mascot's eyes.
- Honey on more than one or two elements per screen.
- Coloured wash for a selected row. Selection is a 35% border step plus a check.
- Hardcoded radii, colours, or durations instead of tokens.

## The one bright thing per screen

| Screen | Its moment |
|---|---|
| Splash | The mascot, drifting clouds |
| Dashboard | The dial and its elapsed-hour arc |
| Alarm sheet | The wake-up time at display size |
| Alarm ringing | The mission itself |

Everything else stays quiet. If two things are competing for attention, one of them is wrong.

---

## Review checklist

Before calling any UI work done:

- [ ] Every colour comes from `MorroColors` — no literals
- [ ] Every radius comes from `MorroShapes`
- [ ] Every duration/easing comes from `MorroMotion`
- [ ] Honey appears at most twice on the screen
- [ ] No `#FFFFFF`, no dusty rose outside the mascot
- [ ] Sentence case throughout, no tracked uppercase
- [ ] All touch targets ≥ 48dp
- [ ] `TextTertiary` or lighter for anything 12sp and below
- [ ] Reduced motion honoured — entrances degrade to fades, mascot and clouds freeze
- [ ] Works at 360dp width and on a tablet
- [ ] Screen-off, phone-locked behaviour verified on a real device (if it touches the alarm)

## Known doc↔code drift — report, don't silently change

The UI works. Do not refactor it to match a doc. If you spot these, flag them:

- `MORRO_VISUAL_SYSTEM.md` lists a momentum spring (0.82 / 386); `MorroSpring` no longer has it.
- The doc says buttons settle "critically damped, no overshoot"; `MorroPrimaryButton` releases on the playful spring, which does overshoot.
- The doc says clouds sit in a top band; `DriftingClouds` spreads eight layers down the full height.
- The doc says the setup time is 62sp; the code uses 60sp.
- The doc says clock and card arrive on separate springs; `MainActivity` animates them serially in one effect.
- `MorroMotion` and `MorroSpring` both define a press spring, at different stiffness (900 vs 2741).

Code is truth. Update the doc, not the UI, unless the founder asks.
