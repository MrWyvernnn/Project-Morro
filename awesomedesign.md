# Awesome Design Skills — Master Index

> **Source:** [bergside/awesome-design-skills](https://github.com/bergside/awesome-design-skills)
> **Registry:** 67 design system skills for AI coding agents (Claude Code, Cursor, Codex, etc.)
> **Downloaded:** All skills stored in `.claude/skills/awesome-design/skills/`

Each skill contains:
- `SKILL.md` → AI instructions (tokens, components, accessibility, do/don'ts, quality gates)
- `DESIGN.md` → Human-readable design intent & rationale

---

## Full Skill Registry (67 Skills)

### Aesthetic / Style Skills

| Slug | Name | Description |
|------|------|-------------|
| `agentic` | Agentic | Design language for AI agent interfaces |
| `ant` | Ant | Minimalist ant-design inspired aesthetic |
| `artistic` | Artistic | Creative, expressive, art-forward design |
| `basic` | Basic | Fundamental, no-frills design system |
| `bold` | Bold | High-impact, unapologetic visual statements |
| `brutalism` | Brutalism | Raw, exposed, intentionally harsh aesthetics |
| `cafe` | Cafe | Warm, cozy, cafe-inspired aesthetics |
| `claude` | Claude | Anthropic Claude brand design language |
| `claymorphism` | Claymorphism | Soft, clay-like 3D UI elements |
| `clean` | Clean | Uncluttered, distraction-free design |
| `codex` | Codex | Developer-focused, documentation-first |
| `colorful` | Colorful | Vibrant, saturated, expressive palettes |
| `contemporary` | Contemporary | Current design trends, modern restraint |
| `corporate` | Corporate | Professional, structured business design |
| `cosmic` | Cosmic | Space, stars, deep dark aesthetics |
| `creative` | Creative | Unconventional, inventive design thinking |
| `dithered` | Dithered | Retro pixel/dither effects |
| `doodle` | Doodle | Hand-drawn, sketchy, casual style |
| `dramatic` | Dramatic | High contrast, theatrical presentation |
| `editorial` | Editorial | Magazine/newspaper-inspired typography |
| `enterprise` | Enterprise | Large-scale, org-wide design systems |
| `expressive` | Expressive | Emotion-forward, personality-rich |
| `fantasy` | Fantasy | Magical, mythical, storybook aesthetics |
| `fiction` | Fiction | Literary, narrative visual language |
| `flat` | Flat | Flat design, no gradients or shadows |
| `friendly` | Friendly | Approachable, rounded, inviting UI |
| `futuristic` | Futuristic | Sci-fi, tech-forward aesthetics |
| `geometric` | Geometric | Shapes, grids, mathematical precision |
| `glassmorphism` | Glassmorphism | Blur, transparency, glass-like surfaces |
| `gradient` | Gradient | Rich gradient-driven design language |
| `immersive` | Immersive | Full-screen, distraction-free experiences |
| `impeccable` | Impeccable | Perfectionist, pixel-perfect standard |
| `levels` | Levels | Depth-layered UI hierarchy |
| `lingo` | Lingo | Language-focused, typographic emphasis |
| `material` | Material | Google Material Design language |
| `matrix` | Matrix | Digital rain, cyberpunk aesthetics |
| `minimal` | Minimal | Maximum restraint, essential-only |
| `modern` | Modern | Contemporary, polished aesthetic |
| `mono` | Mono | Monochromatic design language |
| `neobrutalism` | Neobrutalism | Bold colors, thick borders, raw shapes |
| `neon` | Neon | Glowing, vibrant, night-city aesthetics |
| `neumorphism` | Neumorphism | Soft shadows, inset/extruded UI |
| `pacman` | Pacman | Retro gaming aesthetic |
| `paper` | Paper | Tactile, textured, print-inspired |
| `perspective` | Perspective | 3D, isometric, dimensional design |
| `premium` | Premium | Luxury, high-end visual language |
| `professional` | Professional | Corporate polish, business-ready |
| `pulse` | Pulse | Animated, live, dynamic aesthetics |
| `refined` | Refined | Elegant, understated sophistication |
| `retro` | Retro | Vintage, nostalgic design language |
| `riso` | Riso | Risograph printing, CMYK offsets |
| `roku` | Roku | Streaming device UI aesthetic |
| `sega` | Sega | Retro gaming, 16-bit aesthetic |
| `shadcn` | Shadcn | shadcn/ui component library style |
| `skeumorphism` | Skeumorphism | Realistic, textured, physical objects |
| `sketch` | Sketch | Hand-sketched, wireframe aesthetic |
| `sleek` | Sleek | Smooth, refined, aerodynamic feel |
| `spacious` | Spacious | Generous whitespace, airy layouts |
| `square` | Square | Boxy, grid-aligned, structured |
| `stitch` | Stitch | Stitch/Google design language |
| `storytelling` | Storytelling | Narrative, content-driven design |
| `terracotta` | Terracotta | Warm earth tones, clay-inspired |
| `tetris` | Tetris | Block-based, puzzle aesthetics |
| `vibrant` | Vibrant | Bright, energetic, punchy colors |
| `vintage` | Vintage | Old-world, classic aesthetic |

### Bento & Grid Skills

| Slug | Name | Description |
|------|------|-------------|
| `bento` | Bento | Apple-style bento grid layouts |
| `power` | Power | Dense, data-heavy dashboard layouts |

---

## How to Use

### Browse Available Skills
```
Location: .claude/skills/awesome-design/skills/<slug>/
```

### Access a Skill
Read the `SKILL.md` file for any skill:
```
.claude/skills/awesome-design/skills/brutalism/SKILL.md
.claude/skills/awesome-design/skills/glassmorphism/SKILL.md
```

### Use with TypeUI CLI
```bash
# Pull a specific skill
npx typeui.sh pull glassmorphism

# List all available
npx typeui.sh list

# Preview before pulling
npx typeui.sh pull brutalism --dry-run
```

### Using Skills in Code
1. Read the `SKILL.md` for your chosen aesthetic
2. Apply tokens, colors, typography, and component styles
3. Reference `DESIGN.md` for rationale and design decisions

---

## Quick Reference — By Aesthetic

### Dark / Cyber
| Slug | Mood |
|------|------|
| `neon` | Glowing neon on black |
| `matrix` | Digital rain, cyberpunk |
| `cosmic` | Deep space |
| `glassmorphism` | Dark glass panels |

### Retro / Nostalgic
| Slug | Mood |
|------|------|
| `retro` | Classic vintage |
| `vintage` | Old-world warmth |
| `skeumorphism` | Physical, textured |
| `pacman` | 80s gaming |
| `sega` | 16-bit nostalgia |
| `riso` | Risograph printing |

### Minimal / Clean
| Slug | Mood |
|------|------|
| `minimal` | Essential only |
| `clean` | Uncluttered |
| `basic` | No frills |
| `modern` | Contemporary |
| `refined` | Sophisticated |

### Bold / Expressive
| Slug | Mood |
|------|------|
| `brutalism` | Raw, exposed |
| `neobrutalism` | Bold, colorful brutalism |
| `bold` | High-impact |
| `dramatic` | Theatrical |
| `vibrant` | Punchy colors |

### Premium / Editorial
| Slug | Mood |
|------|------|
| `premium` | Luxury feel |
| `editorial` | Magazine typography |
| `storytelling` | Narrative-driven |
| `artistic` | Art-forward |

### Tech / Developer
| Slug | Mood |
|------|------|
| `material` | Material Design |
| `shadcn` | shadcn/ui |
| `codex` | Docs-first |
| `stitch` | Google Stitch |
| `claude` | Claude brand |

---

## License
MIT — [Bergside](https://github.com/bergside/awesome-design-skills)
