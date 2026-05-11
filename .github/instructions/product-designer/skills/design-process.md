# Design Process — The 6-Step Workflow

Every non-trivial design task follows these steps in order. No skipping.

## 1. Understand

Read the user's request and classify it:

| Situation | Action |
|-----------|--------|
| Clear, specific, small tweak | Just do it. Skip to step 4. |
| New feature, vague brief | Ask 8-12 questions (see `asking-questions.md`) |
| Clear feature but multiple valid approaches | Ask 3-5 scoping questions |
| Iteration on existing mockup | Confirm the change, then build |

**Default:** When in doubt, ask. A 30-second question saves 10 minutes of wrong work.

## 2. Soak in context

Before opening a single HTML file, re-read:

1. **`colors_and_type.css`** — refresh the token vocabulary (severity, entity, spacing)
2. **`skills/visual-language.md`** — the 8 fingerprints (incl. platform shell)
3. **`skills/anti-slop.md`** — the traps to avoid
4. **Adjacent existing UI** — search `openaev-front/src/private/` for related pages. How do sibling features solve layout, navigation, data display?
5. **The user's context** — what did they say? What persona? What data will this screen show at day 0 vs day 100?

This isn't optional. Even if you "remember" the design system, re-read it. Human designers re-check their Figma libraries constantly.

## 3. Plan

Write a mental TODO covering:

### Directions
- **At least 2 contrasting directions** for non-trivial work
- Each must explore a **different meaningful axis**: visual ambition (sober ↔ signature), metaphor (matrix ↔ graph ↔ timeline ↔ map), persona (analyst ↔ executive ↔ auditor), density (compact ↔ spacious), etc.
- Name each direction in ≤4 words

### Hero element
What's the single most important piece of information on this screen? Design around it.

### Data hierarchy
1. What the user needs immediately (hero)
2. What they need on hover/drill-down (progressive disclosure)
3. What they'd filter/search for (secondary)

### Interactions
- What's clickable?
- What opens a drawer/modal?
- What filters exist?
- What empty states exist?

### Mock data
Plan realistic data before building:
- Real framework codes (NIST CSF PR.AC-1, ISO 27001 A.5.1)
- Real-looking STIX IDs (threat--a932fcc6-...)
- Plausible timestamps (not all Jan 1 2024)
- Plausible counts that tell a story (not all round numbers)

## 4. Build

### File setup
1. Copy `templates/prototype.html` to `docs/design/mockups/<Feature Name>.html`
2. Ensure `colors_and_type.css` is in `docs/design/mockups/`
3. If >300 lines of JSX, split into `<feature>-components.jsx`

### Start from the template
The template has React 18, ReactDOM, Babel pinned to exact versions. **Do not change the CDN links or versions.** Do NOT add `crossorigin` or `integrity` attributes — mockups are opened locally via `file://` where SRI checks fail.

### Platform shell (AppShell) — mandatory
The template includes a full platform chrome (`TopBar` + `Sidebar` + `AppShell` wrapper). **Every prototype must be wrapped in `<AppShell>`** to look like a real OpenAEV page. Configure:
- `activeNav` — set to the relevant sidebar item ID (e.g., `"simulations"`, `"scenarios"`, `"settings"`)
- `breadcrumbs` — array matching the page location (e.g., `['Simulations', 'Campaign Alpha', 'Attack path']`)

**Exception:** When using the Design Canvas for multi-direction work, the canvas replaces AppShell (the canvas IS the viewport).

### Build in this order
1. Data structures first (mock data arrays/objects)
2. Small named components (Eyebrow, SeverityBadge, etc. from `component-library.md`)
3. Page content inside `<AppShell>`
4. Interactions last

### Real mock data
```jsx
// GOOD — tells a story
const controls = [
  { id: 'PR.AC-1', title: 'Identity management', status: 'covered', evidence: 3, lastAttestation: '2025-11-14' },
  { id: 'PR.AC-2', title: 'Physical access', status: 'drifting', evidence: 1, lastAttestation: '2025-08-03' },
  { id: 'DE.CM-1', title: 'Network monitoring', status: 'stale', evidence: 0, lastAttestation: null },
];

// BAD — lorem ipsum in disguise
const controls = [
  { id: 'CTL-001', title: 'Control 1', status: 'active', evidence: 5 },
  { id: 'CTL-002', title: 'Control 2', status: 'active', evidence: 3 },
  { id: 'CTL-003', title: 'Control 3', status: 'active', evidence: 2 },
];
```

### When using the design canvas (2+ directions)
- Embed both directions in a single HTML file using `<DesignCanvas>` (see `design-canvas.md`)
- Never produce separate HTML files for different directions

### When using tweaks (parameter variations)
- Add the tweaks panel (see `tweaks.md`) for theme/density/persona toggles
- Every prototype should include at minimum: Theme toggle (light/dark) and Density toggle

## 5. Self-verify

Run the full checklist in `skills/verification.md`. Every item.

Quick summary:
- ✅ No console errors
- ✅ Only CSS variables from `colors_and_type.css`
- ✅ Both themes work (toggle `.dark` class)
- ✅ All numbers in IBM Plex Mono
- ✅ Eyebrows on every card/section
- ✅ No emoji, no lorem ipsum, no fake scores
- ✅ Directions genuinely contrast on a meaningful axis
- ✅ Hover states on all clickable elements

## 6. Hand off

### Format
One paragraph per direction:
- **Direction name** (≤4 words) — what axis it explores, what it looks like, 1-2 key design choices.

### End with next iterations
Always end with 2-3 suggestions:
- "Add drill-down modal for individual controls"
- "Try a timeline view instead of the table"
- "Add an attestation workflow"

### Don't
- Don't apologize ("Here's my attempt...")
- Don't pad ("I hope you find this useful...")
- Don't explain React/Babel/HTML — the user knows
- Don't describe what they can see — show it
