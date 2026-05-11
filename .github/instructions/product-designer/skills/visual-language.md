# The OpenAEV Visual Language (filigran-ui)

This document defines the visual fingerprints that make a prototype "feel OpenAEV." Every prototype must pass the litmus test: *could this screenshot sit next to the real app and look like a sibling?*

## The 8 fingerprints

### 1. Platform chrome — sidebar + topbar always visible

Every OpenAEV page lives inside a persistent shell:
- **Top bar** (64px): logo left (35px height, from `logo_text_dark.png` — NEVER use the SVG which shows wrong branding), search center (~40% width), AI + XTM + account icons right. Background = `--nav-background`. Border-bottom only (outlined AppBar).
- **Left sidebar** (180px expanded / 55px collapsed): menu items grouped by dividers. Selected item has subtle `rgba(255,255,255,0.08)` background. "by Filigran" logo + collapse toggle at the bottom.
- **Content area**: margin-left = sidebar width, padding-top = topbar height + 16px, padding horizontal = 20px.
- **minWidth**: 1400px (same as the real app).

Prototypes MUST include this shell (via the `AppShell` component in the template). A mockup without the chrome looks like a random web page, not OpenAEV.

**When to omit:** Only when using the Design Canvas (multi-direction viewport) — the canvas itself IS the frame.

### 2. Geologica for structure, IBM Plex Sans for content, IBM Plex Mono for data

| Use case | Font | Notes |
|----------|------|-------|
| Page titles, section headings | Geologica 400–500 | Never bold titles — weight conveys hierarchy, not emphasis |
| Category labels, tabs | Geologica 600, uppercase | `.txt-category`, `.txt-tab` classes |
| Body text, descriptions, labels | IBM Plex Sans 400 | 0.9rem base size |
| Numbers, IDs, timestamps, code | IBM Plex Mono 400–500 | Always with `font-variant-numeric: tabular-nums` |
| Eyebrows | IBM Plex Mono 500, 11px, uppercase | `letter-spacing: 0.08em`, `color: var(--text-tertiary)` |

### 3. Severity is semantic, never decorative

| Level | Token | Usage |
|-------|-------|-------|
| Critical | `--severity-critical` (red) | Active breach, failed critical control |
| High | `--severity-high` (orange) | Urgent attention needed |
| Medium | `--severity-medium` (yellow) | Degraded but not dangerous |
| Low | `--severity-low` (green) | Minor, scheduled |
| Info | `--severity-info` (blue) | Informational, no action |
| None | `--severity-none` (gray) | Unscored, N/A |

Rules:
- Never use severity colors for decoration or brand expression
- Never use green alone to mean "good" — pair with a text label like "compliant" or "covered"
- A number with a severity must show its denominator (not just "14 critical" — "14 of 287 critical")

### 4. Entity colors are fixed assignments

Each OpenAEV entity type has a permanently assigned color:

| Entity | Token | Approximate hue |
|--------|-------|-----------------|
| Analyse | `--entity-analyse` | Yellow-green |
| Cases | `--entity-cases` | Purple-pink |
| Events | `--entity-events` | Pink-red |
| Observations | `--entity-observations` | Orange |
| Threats | `--entity-threats` | Gold |
| Arsenal | `--entity-arsenal` | Yellow |
| Techniques | `--entity-techniques` | Lime |
| Victimology | `--entity-victimology` | Violet |
| Locations | `--entity-locations` | Teal |
| Observables | `--entity-observables` | Cyan |

Never reassign these colors. They're part of the user's mental model.

### 5. Layered surfaces

The filigran-ui surface hierarchy:

| Layer | Token | Usage |
|-------|-------|-------|
| Page | `--page-background` | Full-page backdrop |
| Box | `--box-background` | Sidebar, panels |
| Card | `--card-bg` | Elevated content areas |
| Hover | `--hover-bg` | Interactive highlight |
| DS surfaces | `--ds-bg-1` through `--ds-bg-4` | Nested depth levels |

In dark mode, depth is expressed through subtle lightness shifts, not shadows.

### 6. Subtle borders by default

- `--border-light` = default boundary between cards, sections
- `--border-medium-light` = emphasis (active tab, selected item)
- `--border-focus` = focus rings (darkblue in light, blue in dark)
- Never use thick borders (>1px) for decoration
- Never use colored left-borders as "accent" indicators

### 7. Minimal radius

- `--radius` = 4px for cards, buttons, inputs
- `--radius-md` = 2px for chips, badges
- `--radius-sm` = 0px for nested elements
- No pill shapes (border-radius: 9999px) except for actual pills/tags
- No large rounded corners (8px+)

### 8. Spacing is tight and consistent

| Token | Value | Usage |
|-------|-------|-------|
| `--spacing-xxs` | 2px | Icon-to-text gap |
| `--spacing-xs` | 4px | Tight padding (chips) |
| `--spacing-s` | 8px | Default internal gap |
| `--spacing-m` | 12px | Card padding, row gap |
| `--spacing-l` | 16px | Section padding |
| `--spacing-xl` | 24px | Major section gaps |
| `--spacing-xxl` | 32px | Page-level spacing |

Density target: 12–16px card padding, 36–40px row height, 8–12px card gap.

## Typography scale

| Class | Size | Weight | Font | Use |
|-------|------|--------|------|-----|
| `.txt-jumbo` | 2.5rem | 400 | Geologica | Hero numbers (rare) |
| `.txt-title` | 1.375rem | 400 | Geologica | Page title |
| `.txt-subtitle` | 1rem | 500 | Geologica | Section heading |
| `.txt-category` | 0.875rem | 600 | Geologica | Category, nav labels |
| `.txt-tab` | 0.875rem | 400 | Geologica | Tabs (uppercase) |
| `.txt-h5` | 1rem | 700 | Geologica | Bold callout |
| `.txt-default` | 0.9rem | 400 | IBM Plex Sans | Body text |
| `.txt-body2` | 0.8rem | 400 | IBM Plex Sans | Secondary body |
| `.txt-table` | 0.8125rem | 400 | IBM Plex Sans | Table cells |
| `.txt-sub-content` | 0.75rem | 400 | IBM Plex Sans | Captions, metadata |
| `.txt-mini` | 0.5625rem | 400 | IBM Plex Sans | Timestamps, very small |
| `.eyebrow` | 0.6875rem | 500 | IBM Plex Mono | Section/card labels (uppercase) |

## Iconography

- Use Lucide icons (stroke-only, 1.5px stroke)
- Size: 14–16px inline, 20px standalone
- Color: `--text-secondary` by default, `--text-primary` on hover
- Never filled icons, never custom SVG drawings

## Explicit DON'Ts

- ❌ Gradients on cards or buttons (only allowed for AI and gradient tokens)
- ❌ Glassmorphism / frosted glass
- ❌ Border-radius > 8px on any element
- ❌ Emoji as UI elements
- ❌ Center-aligned body text
- ❌ Drop shadows in dark mode (use subtle border instead)
- ❌ Decorative illustrations or mascots
- ❌ Color-only severity indicators (always pair with text)
- ❌ Multiple font families for the same role
- ❌ More than 3 levels of text hierarchy on a single card

## Litmus test

Open your prototype next to an existing OpenAEV page. It should look like:
- Same font rhythm
- Same density
- Same color restraint
- Same border treatment
- A sibling, not a cousin
