# Morro — Visual System

One stage: pure black. One signal: orange bleeding into pink. Everything else is a step in tone,
a line of type, or air.

This document is the design contract for the base UI. The code is the source of truth
(`ui/theme/Theme.kt`); this records why the values are what they are.

---

## 1. Colour

| Token | Value | Role |
| --- | --- | --- |
| `Canvas` | `#000000` | Every screen's ground. AMOLED black, no tint. |
| `Surface` | `#07070B` | Sheets and large panels. |
| `SurfaceCard` | `#09090D` | Rows, quiet containers. |
| `SurfaceElevated` | `#0D0D12` | Bento cards, answer cards, the mission dock. |
| `SurfaceLow` | `#12121A` | Icon wells, legibility pills, drag handles. |
| `TextPrimary` | `#FFFFFF` | Headlines, times, names. |
| `TextSecondary` | `#A0A0AB` | Body copy, explanations, status. |
| `TextTertiary` | `#6B6B76` | Decorative eyebrows and disabled states only. |
| `AccentTactical` | `#FF5500` | Primary action, active state, second hand. |
| `AccentBubbly` | `#FF66A3` | Selection checks, live states, glow. |
| `AccentBubblySoft` | `#FF85B3` | Gradient midpoint. |
| `AccentBubblyDeep` | `#FF5E9E` | Inline validation (borrowed, not a second error hue). |
| `WarmYellow` | `#FFC94A` | Armed status only. |
| `BorderSubtle` | `#1FFFFFFF` | Structural hairlines. |
| `Success` | `#2E7D32` | Reserved for positive confirmation. |
| `Error` | `#FF4D5E` | Destructive only. |

**Gradient.** `MorroGradients.horizontal()` is the only gradient in the product: `#FF5500 →
#FF66A3`, used on progress, selected borders, the primary action and the dial rim. It is a
signature, so it is never used as background decoration behind text.

### Measured contrast (WCAG 2.1 relative luminance)

| Pair | Ratio | Result |
| --- | --- | --- |
| White on `#000000` | 21.00 | AA / AAA |
| White on `#0D0D12` | 19.38 | AA / AAA |
| `#A0A0AB` on `#000000` | 8.11 | AA / AAA |
| `#A0A0AB` on `#0D0D12` | 7.49 | AA / AAA |
| `#FF5500` on `#000000` | 6.55 | AA |
| `#FF5500` on `#0D0D12` | 6.05 | AA |
| `#FF66A3` on `#000000` | 7.68 | AA / AAA |
| `#FF66A3` on `#0D0D12` | 7.09 | AA |
| Black on `#FF5500` | 6.55 | AA (button label) |
| Black on `#FF66A3` | 7.68 | AA (check on selection) |
| `#FFC94A` on `#0D0D12` | 12.65 | AA / AAA |
| `#FF4D5E` on `#09090D` | 6.13 | AA |
| `#6B6B76` on `#000000` | 3.99 | Large / UI only |

`#6B6B76` is the brief's tertiary. It clears the 3:1 threshold for large text and UI edges but not
4.5:1 for small body copy, so it is used only for decorative eyebrows (`WAKE-UP MISSION`,
`SYSTEM CHECK`) and disabled labels. Every string that carries information uses `#A0A0AB` or
above.

---

## 2. Geometry

- Cards: **20dp** squarcle.
- Buttons: **16dp** squarcle; pills are fully round.
- Sheet: **28dp** top corners only.
- Page gutter: **24dp** on every screen, with a **24dp** vertical rhythm.
- Touch targets: **56dp** minimum button height, **44dp** minimum icon well.

No sharp interactive corners, and no card nested inside a card except the sheet's expanded mission
panel, where the inner panel is a different tone rather than a border.

---

## 3. Type

Two faces: system sans for voice, system mono for status.

| Role | Size / weight | Notes |
| --- | --- | --- |
| Splash wordmark | 36sp bold, −1sp tracking | White. |
| Screen title | 28sp bold, −0.5sp | White, left aligned. |
| Question | 22sp medium, 30sp leading | White, left aligned. |
| Body | 14–16sp regular | `#A0A0AB`, not grey-on-grey. |
| Wake-up time | 64sp bold | The one number that may be loud. |
| Status / eyebrows | 10–11sp mono, 1.2–2.2sp tracking | Decorative, sentence-case where it carries meaning. |

---

## 4. Motion

Springs, not durations, for anything a finger touches.

| Intent | Damping | Response | Stiffness used |
| --- | --- | --- | --- |
| Default (settle, no overshoot) | 1.00 | 0.35s | 322 |
| Momentum (entrance, flick) | 0.82 | 0.32s | 386 |
| Press (touch-down) | 1.00 | 0.12s | 2741 |

- Press = scale **0.97**, acknowledged on touch-down.
- Screen transitions: shared-axis fade + slide, **300ms**.
- Ambient loops (ribbon breathe, pin glow, ghost bob) are the only tweens: 1.6–11s.
- Reduced motion (`ANIMATOR_DURATION_SCALE == 0`): springs become a **150ms** cross-fade, idle
  loops hold still, the splash's hold collapses to a beat.
- One `Animatable` per animated component; every visual is derived from its value.

---

## 5. Composition

**Splash.** Layered Canvas, back to front: pink sun (upper right), orange→pink ribbon (bottom-left
to middle-right), grass blades sampling the ribbon's own bezier so they always stand on its edge,
then the white ghost with pink eyes and a smile. The ghost rises 36dp and the wordmark lands after
it. Tap or 2.4s advances.

**Questions.** Editorial, not a wizard: mono position line, one gradient progress strip, a 22sp
question, one column of answer cards. Selection = gradient border + 6% warm wash + pink check.
The free-text answer owns the keyboard: the header compacts on IME, the card shakes ±10px on empty
submit, and the counter warns 12 characters before the cap.

**How it works.** Three existing slides, unchanged content. The only gradient is the progress and
the final CTA; pagination has 28dp of clearance above the button so they can never collide.

**Permissions.** One screen. Two live rows (notifications, microphone) with inline system requests
and status pills that flip to a pink check. Exact timing and battery live in a monospace terminal
card with optional actions; Continue never depends on them.

**Dashboard.** Black, with a low dusk-blue→orange→pink horizon arc behind the dial. The clock is the
hero: near-black face, dual-tone rim, white hands, orange second hand, pink pin. One mono readout
under it. The bento mission dock owns the bottom: status, time, mission, armed switch, and one
full-width action. No floating button.

**Alarm sheet.** Bottom anchored at 92% height, opaque `#07070B`, 88% black scrim, navigation-bar
and IME insets, content scrolls. Time card → mission rows → save. Nothing else.

---

## 6. Rules

1. Colour never carries meaning alone: every state also changes a border, a check, a label or a
   dot.
2. Black is the ground, not a tint. Never place a dark panel on a dark panel without a tone step.
3. The ribbon gradient appears on progress, selection and the primary action — three jobs, no more.
4. No shadows at rest. Elevation is a response to touch, not decoration.
5. Copy is accountable, never shaming. No "no excuses", no guilt, no streaks.
6. Motion explains a change or answers a touch; it never performs for its own sake.
