# Self-Verification Checklist

Run this **completely** before declaring any prototype done. No exceptions.

## Technical checks (non-negotiable)

- [ ] HTML loads without console errors (no undefined variables, no missing imports)
- [ ] All colors come from CSS variables in `colors_and_type.css` — zero hardcoded hex/rgb values
- [ ] Both themes work: add/remove `.dark` class on `<html>` and verify nothing breaks
- [ ] No React warnings in console (key props, deprecated APIs)
- [ ] React 18 + Babel CDN links match the exact versions in `templates/prototype.html`
- [ ] `colors_and_type.css` is linked correctly (relative path `./colors_and_type.css`)
- [ ] All fonts load (Geologica, IBM Plex Sans, IBM Plex Mono) — check network tab

## Visual checks (best effort, most are critical)

- [ ] **Platform shell** present — TopBar + Sidebar + content area (via `<AppShell>`)
- [ ] **activeNav** set to correct sidebar item
- [ ] **Breadcrumbs** reflect the page hierarchy
- [ ] **Eyebrows** on every card and section — 11px, uppercase, mono, tertiary color
- [ ] **All numbers** in IBM Plex Mono with `font-variant-numeric: tabular-nums`
- [ ] **IDs, timestamps, codes** in IBM Plex Mono (never IBM Plex Sans)
- [ ] **Headings** in Geologica (never IBM Plex Sans for h1-h6)
- [ ] **Severity colors** match tokens and are always paired with text label
- [ ] **Entity colors** match their assigned types (threats=gold, arsenal=yellow, etc.)
- [ ] **Density** matches target: 12-16px card padding, 36-40px row height
- [ ] **Hover states** on all clickable elements
- [ ] **Border-radius** ≤ 4px for cards/buttons (no large rounded corners)
- [ ] **Borders** use token values (--border-light, --border-default)
- [ ] **No drop shadows** in dark mode

## Content checks (non-negotiable)

- [ ] **No lorem ipsum** — all text is realistic (framework codes, entity names, dates)
- [ ] **No emoji** — zero, anywhere
- [ ] **No fake aggregate scores** — all metrics show denominators
- [ ] **No marketing language** — no "Welcome to", "Get started", "Gain insights"
- [ ] **Numbers tell a story** — not all round, not all the same, varying statuses
- [ ] **Copy is precise** — "2 controls drifting since Nov 14" not "Issues detected"

## Direction-quality checks (for multi-direction work)

- [ ] **Directions genuinely contrast** on a stated axis (not "same with different color")
- [ ] **Each direction is fully realized** — not a sketch vs. a polished version
- [ ] **Each has a 4-word name** and a one-sentence explanation of what it explores
- [ ] **Same mock data** across directions (user compares design, not data)

## 30-second final pass

Look at the prototype for 30 seconds and ask:

1. What's the first thing my eye lands on? → Is that the hero metric?
2. Can I tell what this page IS in 2 seconds? → If not, add/fix eyebrow + title
3. Does it look like it belongs in the same app as existing OpenAEV pages? → Is the sidebar there? Topbar? Dark navy background?
4. Is there any element that screams "AI-generated"? → Fix or remove it
5. Would I be embarrassed showing this to a design lead? → If yes, what's wrong?

## When to ship anyway

- **Technical + Content checks:** Must all pass. No exceptions.
- **Visual checks:** Best effort. If you've spent 3+ minutes on a single visual issue and it's minor, note it and ship.
- **Direction-quality:** Must pass for multi-direction work. Optional for single-direction iterations.

If a check fails, fix it before showing the prototype. The user should never see a prototype with console errors, lorem ipsum, or broken themes.
