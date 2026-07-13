# TOKEN-MAPPING.md — openaev

**Not generated.** Written by the agent during Phase 4 of
`implement-tokens-product.prompt.md`. All arbitration below was decided by
Sandy in chat on 2026-07-13 (mapping-table checkpoint) before any line of
code was touched — this file is the settled record of that arbitration, not
a proposal.

Scope of this pass: `src/components/ThemeDark.ts` and `ThemeLight.ts` only —
static TS wiring of hardcoded hex values to `FDS.colors.<mode>['--color-...']`
from `fds-tokens.generated.ts`. No runtime CSS-variable sync, no
`@filigran/design-system` package dependency added (confirmed not installed;
see "Deferred" below) — same architecture as the OpenCTI pilot.

Visual delta legend (same as OpenCTI's convention): **none** = identical or
case-only diff · **minor** = perceptible only side-by-side · **notable** = a
real, at-a-glance color shift — these are the ones to scrutinize in the
Phase 5 screenshots.

---

## 1. Named constants (`THEME_<MODE>_DEFAULT_*`, `EE_COLOR`)

Two independent arbitration tracks produced these values:

- **§B (typo-class fix)** — a single drifted literal (`#00f1bd`, one/two hex
  digits off FDS's `--color-filigran-tonic-primary`) reused across several
  fields in the **dark** file (and silently copy-pasted, unmodified, into two
  **light**-file fields that should have been mode-adapted). Near-invisible
  by construction — same pixel-level drift class validated on the OpenCTI
  pilot.
- **§C (ISO OpenCTI alignment)** — properties where OpenAEV had **no FDS
  token match of its own**, but the exact same semantic slot in OpenCTI
  already has an FDS-validated value (OpenCTI's pilot is the reference:
  its values were visually signed off first). Aligning OpenAEV onto that
  value converges both products onto the same real, shared token — some of
  these deltas **are** visually notable, by design (see the Phase 5 report
  for before/after evidence).

| Constant | FDS token | Track | Old (dark) | New (dark) | Δ | Old (light) | New (light) | Δ |
|---|---|---|---|---|---|---|---|---|
| `..._PRIMARY` | `--color-filigran-brand-primary` (dark) / `--color-darkblue-600` (light) | dark: already exact · light: §B (typo `a`→`b` only) | `#0fbcff` | `#0fbcff` | none | `#001bda` | `#001bdb` | none (imperceptible) |
| `..._SECONDARY` / `EE_COLOR` | `--color-filigran-tonic-primary` | dark: §B · light: §C | `#00f1bd` | `#00f0bc` | none (imperceptible, ≤1/255 per channel) | `#0c7e69` | `#00f0bc` | **notable** |
| `..._ACCENT` | `--color-elevation-background-layer-3` | §C (both modes) | `#0f1e38` | `#1f3965` | **notable** | `#dfdfdf` | `#e4e5e7` | minor |
| `..._PAPER` | `--color-elevation-background-layer-1` | dark: §C · light: already exact | `#09101e` | `#0d172b` | minor | `#ffffff` | `#ffffff` | none |
| `..._BACKGROUND` | `--color-elevation-background-layer-0` | §C (both modes) | `#070d19` | `#070d18` | none (imperceptible) | `#f8f8f8` | `#f2f2f3` | minor |
| `..._NAV` | `--color-elevation-surface-heading-layer-0` | dark only — **rides along with `..._BACKGROUND`** (same constant in the dark file, so this comes for free, no separate visible change) | `#070d19` | `#070d18` | none | `#ffffff` | `#ffffff` **(unchanged — see "7th item" below)** | — |

`palette.gradient.main` and `xtmhub.main` (both modes) also carried the same
drifted `#00f1bd` literal — recalibrated to `#00f0bc` alongside §B for source
consistency, but these two are **not** equivalent in consumer status:

- **`gradient.main`** — confirmed **zero consumers** (grepped, no hits
  outside `Theme.ts`/the theme files) — genuinely dead, zero visual impact.
- **`xtmhub.main` — this is exactly the "end-gradient" trap from the OpenCTI
  passation, found live here.** `GradientButton.tsx:26` reads it as the
  `endColor` of a `linear-gradient(99.95deg, primary.main 0%, xtmhub.main
  100%)` used for the button's border/shadow/background/text-clip gradient
  effect. `GradientButton` itself is rendered in 3 real screens: XTM Hub
  settings tab (`XtmHubTab.tsx`), the unregistered-hub CTA
  (`XtmHubUnregisteredSection.tsx`), and the import-from-hub action
  (`ImportFromHubButton.tsx`). Before this lot, `endColor` was the drifted
  `#00f1bd` literal — same class of bug as OpenCTI's uncabled end-gradient,
  now fixed the same way (wired to `--color-filigran-tonic-primary`). Visual
  delta: **none/imperceptible** (≤1/255 per channel, same as the other §B
  fixes) — but unlike `gradient.main`, this one **is** exercised on screen,
  so it's included in the XTM Hub tab if you want to eyeball it at the
  checkpoint even though no visible change is expected.

### ⚠️ 7th item found, NOT applied this lot — `THEME_LIGHT_DEFAULT_NAV`

Not one of the 6 properties named in your arbitration, so I did **not**
touch it — flagging because I found it via the exact same methodology while
cross-referencing OpenCTI's table. OpenCTI's own light-mode `NAV` constant
was old `#ffffff` (**identical to OpenAEV's current value**) → their
validated new value is `#f2f2f3` (`--color-elevation-surface-heading-layer-0`,
light), rated **notable** on their own report ("was pure white"). Same
DURABLE-and-divergent-from-OpenCTI situation as the 6 you named, but it's a
real, visible white→pale-gray shift on the left nav/top bar that you
haven't explicitly signed off on. Left as the raw `#ffffff` literal for now
— tell me if you want it folded into this lot or held for a later one.

---

## 2. §D — `background.secondary` (newly wired)

`Theme.ts`'s `TypeBackground` augmentation has required `secondary` since
before this pilot, but neither `ThemeDark.ts` nor `ThemeLight.ts` ever
assigned it — resolved to `undefined` at runtime. One real consumer exists:
`DragAndDropImportDialog.tsx:32,40` (hover/active drag-and-drop background),
silently rendering no color. Wired both modes:

| Mode | FDS token | New value |
|---|---|---|
| dark | `--color-elevation-surface-highlight` | `#101b33` |
| light | `--color-elevation-surface-highlight` | `#e4e5e7` |

Zero-cost fix (was broken/invisible, now shows the intended highlight) — no
before/after delta to review since there was no "before" color to compare
against, just confirm the hover/active state now has a visible background
on the import-drawer's drag zone.

---

## 2b. ISO OpenCTI — body/html gradient (found during your Phase 5 review)

**You caught a real gap, not a false alarm.** OpenAEV's `MuiCssBaseline`
never gave `html`/`body` a `background` at all — it only set
`scrollbarColor`/`scrollbarWidth`. The actual rendered background came
purely from MUI `CssBaseline`'s own default (a **flat fill** off
`background.default`). Zero gradient, on either mode, before this fix.

OpenCTI's `ThemeDark.ts`/`ThemeLight.ts` build a real two-stop
`background: linear-gradient(100deg, <background> 0%, <end-stop> 100%)` on
both `html` and `body` (`backgroundAttachment: 'fixed'` alongside it), where
`<end-stop>` comes from a small `getAppBodyGradientEndColor(background)`
helper:
- if the DB-configured `background` param is null (using the FDS default):
  return the FDS `--color-elevation-background-layer-0-gradient` token
  as-is.
- if an admin **has** overridden `background` in a custom theme: return
  `lighten(background, 0.05)` instead — so a custom background still gets a
  live, coherent gradient end-stop even though no form field lets anyone
  author that end-stop directly.

Ported verbatim (same helper name, same 100deg angle, same
`backgroundAttachment: 'fixed'`, same html+body duplication) into
`ThemeDark.ts`/`ThemeLight.ts` — new `THEME_<MODE>_DEFAULT_BODY_END_GRADIENT`
constants, wired to the same token OpenCTI uses:

| Mode | Start (`background.default`, already wired §1) | End (new) | FDS token (end) |
|---|---|---|---|
| dark | `#070d18` | `#0c1527` | `--color-elevation-background-layer-0-gradient` |
| light | `#f2f2f3` | `#ffffff` | `--color-elevation-background-layer-0-gradient` |

**Visual delta: minor** (unlike OpenCTI, which was replacing an already-visible-but-wrong
gradient, OpenAEV had no gradient at all — the two stops are close enough
in value that the change reads as a subtle diagonal depth cue, not a color
shift; still worth a dedicated look at the checkpoint since it touches
every single screen's canvas, per your call that this is the most
consequential ISO delta of the lot).

Left untouched, matching OpenCTI's own scope decision on this exact point:
`palette.gradient.main` (OpenAEV) / `palette.gradient.background` (OpenCTI)
— confirmed dead code on both products, neither's `MuiCssBaseline` ever
reads it; the real body background is always hand-built inline as shown
above. No DB schema change considered or needed (same reasoning as OpenCTI:
`lighten()` already covers the custom-theme case).

---

## 3. §C detail — usages, classification, treatment (per your requested methodology)

All 6 properties you named came back **DURABLE** (none JETABLE) — consistent
with all 6 being fundamental theme slots (background/paper/accent/secondary),
not one-off overrides on a component slated for replacement.

### 3.1 `paper` (dark) — `#09101e`

- **Usages**: `background.paper` — 17 explicit `theme.palette.background.paper`
  call sites (`Charts.ts`, `NodePhantom.tsx`, `AskArianePanel.tsx`,
  `CatalogFilters.tsx`, `SearchFilter.tsx`, `LogicFlow.tsx`,
  `LogicalOperatorSelect.tsx`, `DeletableEdge.tsx`, both `Lessons.tsx`,
  `AnswersByQuestionDialog.tsx`, `CommandsInfoCard.tsx`,
  `OutputParserInfoCard.tsx`, `GradientButton.tsx`, `AttackPatternBox.tsx`,
  `IconBar.tsx`, `TraceStatusChip.tsx`) **plus** the implicit universal
  default every unstyled MUI `Paper`/`Card`/`Dialog`/`Menu`/`Drawer` reads
  automatically. Also backs `background.paperInCard` and the
  `.leaflet-container` CSS rule.
- **Classification**: DURABLE — the platform's fundamental "surface" color.
- **OpenCTI comparison**: OpenAEV's `#09101e` = OpenCTI's **old** dark paper,
  exactly. OpenCTI's validated new value is `#0d172b`
  (`--color-elevation-background-layer-1`, "minor" delta on their own report).
- **Treatment**: DURABLE + divergent from OpenCTI's current value → aligned.
  → **TOKEN PARTAGÉ** (`--color-elevation-background-layer-1`, already exists
  in FDS, now genuinely shared between both products).

### 3.2 `accent` (dark) — `#0f1e38`

- **Usages**: feeds both `background.accent` (`ItemCopy.tsx`,
  `AttackPatternBox.tsx` ×2 — matrix view + dashboard widget viz) **and**
  `background.code` (`DateTimeFieldController.tsx`, `TextFieldController.tsx`
  — shared low-level form-field components used across many forms) since the
  dark file uses one constant for both slots. Also CSS-only: scrollbar-color,
  `pre`/`code` block backgrounds, `.react-grid-placeholder`.
- **Classification**: DURABLE — shared form-field styling + the product's
  signature MITRE ATT&CK matrix feature + code/markdown rendering, all
  lasting production surfaces.
- **OpenCTI comparison**: OpenAEV's `#0f1e38` = OpenCTI's **old** dark accent,
  exactly. OpenCTI's validated new value is `#1f3965`
  (`--color-elevation-background-layer-3`, "notable" on their report).
- **Treatment**: DURABLE + divergent → aligned. → **TOKEN PARTAGÉ**.

### 3.3 `secondary` (light) + `EE_COLOR` (light) — `#0c7e69`

- **Usages**: `secondary.main` — core MUI slot (`color="secondary"` prop
  used broadly, plus explicit `theme.palette.secondary.main` in
  `Lessons.tsx`, `DetectionRemediationInfo.tsx`,
  `WidgetSeriesSelection.tsx`, `Communication.jsx` ×2) and `border.secondary`
  (hexToRGB-derived). `ee.main`/`ee.background`/`ee.lightBackground` —
  Enterprise Edition badge (`ItemBoolean.jsx`, `EEChip.tsx` ×3).
- **Classification**: DURABLE — core MUI slot + permanent EE branding.
- **OpenCTI comparison**: OpenAEV's `#0c7e69` ≠ OpenCTI's old light secondary
  (`#00bd94`) — different starting points in each product. But OpenCTI's
  validated new value (**both modes**, mode-invariant token) is `#00f0bc`
  (`--color-filigran-tonic-primary`) — "notable" on their own light-mode row
  too.
- **Treatment**: DURABLE + divergent → aligned to `#00f0bc`. Same target as
  the dark-mode §B fix → secondary/EE/gradient.main/xtmhub.main now unified
  at one value across both modes. → **TOKEN PARTAGÉ** (same token as §1).
- **Expected delta: notable** (muted teal-green → bright cyan-green) —
  flagged for the visual checkpoint, unlike §B's near-invisible pairs.

### 3.4 `accent` (light, i.e. `background.code` only) — `#dfdfdf`

- **Usages**: `background.code` — `DateTimeFieldController.tsx`,
  `TextFieldController.tsx` (shared field components) + CSS (`pre`/`code`
  blocks, scrollbar, `.react-grid-placeholder`). Note: this is a **different**
  fallback from `background.accent` (light splits the two — see 3.5).
- **Classification**: DURABLE — same shared low-level field components as
  3.2's dark accent.
- **OpenCTI comparison**: OpenAEV's `#dfdfdf` = OpenCTI's **old** light
  accent, exactly. OpenCTI's validated new value is `#e4e5e7`
  (`--color-elevation-background-layer-3`, "minor" on their report).
- **Treatment**: DURABLE + divergent → aligned. → **TOKEN PARTAGÉ** (same
  token family as 3.2, now consistent across both modes and both products).

### 3.5 `background.accent` (light, standalone) — `#d3eaff`

- **Usages**: `ItemCopy.tsx` (generic copy-value chip), `AttackPatternBox.tsx`
  ×2 (matrix cell buttons + dashboard widget). Pre-existing quirk, unrelated
  to this mission: this fallback is **not** the same constant as
  `background.code`'s (`THEME_LIGHT_DEFAULT_ACCENT`) — light mode has always
  had two different "accent" colors depending on which field you read, dark
  mode does not have this split. Not touched (out of scope, not a token
  question).
- **Classification**: DURABLE (attack-matrix is a core, lasting feature).
- **OpenCTI comparison**: **No equivalent found.** OpenCTI's closest analog
  (`designSystem.background.bg1`–`bg4`) is itself explicitly left untouched
  in their report — "no confident 1:1 FDS token found," listed in their own
  "Tokens à créer dans Figma." Neither product has a validated value here.
- **Treatment**: Neither of your two rules applies — this is a genuine
  **cross-product gap**, not a divergence to resolve unilaterally. **Not
  wired.** → joins the consolidated Figma list (§5) as a new, OpenAEV-only
  entry with no existing OpenCTI counterpart to borrow.

### 3.6 `background.default` (both modes) — dark `#070d19` / light `#f8f8f8`

- **Usages**: 9 files / 11 call sites (`private/Index.tsx`, `public/Index.tsx`,
  `InjectTestDetail.tsx`, `SecurityCoverage.tsx`, `LogicFlow.tsx`,
  `EventNode.tsx`, `ActionNode.tsx`, `InjectCardComponent.tsx` — 1 each —
  `Drawer.tsx` — 3) plus the implicit universal default (`MuiCssBaseline`'s
  `body` background — the base canvas color for the entire app).
- **Classification**: DURABLE — the single most foundational slot in the
  theme.
- **OpenCTI comparison**: dark `#070d19` = OpenCTI's old dark background,
  exactly (validated new: `#070d18`, "none" — imperceptible). Light `#f8f8f8`
  ≠ OpenCTI's old light background (`#ececf2`) — different starting values,
  but OpenCTI's validated new value is `#f2f2f3` ("minor" on their report).
- **Treatment**: DURABLE + divergent (both modes) → aligned, both via
  `--color-elevation-background-layer-0`. → **TOKEN PARTAGÉ**.
- Dark `background.default`/`nav` share one constant already in the source,
  so this fix also resolves dark `nav` for free (no separate decision
  needed — see §1 table).

---

## 4. Left deliberately unwired (design decisions, not token gaps)

- **`palette.severity.*`** (7 levels: critical/high/medium/low/info/none/
  default) — per your arbitration, this is a **design decision for you and
  Thibault**, not something to resolve unilaterally. Documenting the
  composition path without applying it: OpenCTI's own pilot (§4 of their
  TOKEN-MAPPING.md) already validated a 5-level mapping directly portable to
  OpenAEV's own `fds-tokens.generated.ts` (same token set, confirmed
  present): `critical→feedback-error-primary`, `high→feedback-warning-primary`,
  `medium→feedback-alert-primary`, `low→feedback-success-primary`,
  `info→feedback-info-primary`. `none`/`default` have no feedback-family
  equivalent in either product (neutral/unset state) — OpenCTI left these
  untouched too. `Tag.tsx:32` currently has a defensive
  `theme.palette.severity?.default ?? '#004C66'` fallback already masking
  the gap — functional today, just not FDS-driven.
- **`text_color`** — out of scope, architecturally unwireable via the DB
  theme system today: it's the 9th positional parameter of
  `ThemeDark`/`ThemeLight`, and `AppThemeProvider.tsx` never passes it
  (stops at the 8th, `accent`). Wiring the *default* wouldn't change
  anything customizable; the closest FDS token if this is ever extended
  would be `--color-text-default-primary` (`#f2f2f3` dark / `#18191b` light).
- **`THEME_LIGHT_DEFAULT_NAV`** — see the "7th item" flag in §1. Found via
  the same methodology, not in your named list, a real visible delta —
  held back pending your call.
- **`borderRadius: 4`** — already numerically identical to FDS's
  `--radius-sm` (`4px`). Not wired symbolically this lot (out of scope —
  "chantier couleurs", not scalars/radii; zero visual risk either way since
  the value already matches).
- **Light `background.paperInCard: '#f7f7f7'`** — pre-existing, unrelated
  quirk (FYI only, not a decision point): unlike dark mode, light's
  `paperInCard` ignores the `paper` param entirely and is hardcoded
  unconditionally. Not a token question, not touched.

---

## 5. Dead code — confirmed zero consumers, left as-is, documented

- **`palette.designSystem`** (and its full `DesignSystemPalette` type in
  `Theme.ts:221-277`) — **option 2, per your call**: left dead, not
  resurrected. Prior-art history: introduced whole by PR #5204 (21 Mar,
  Samuel Hassine) — a full "design system v7" attempt bundled with an
  unrelated chatbot v2 and JWT change — then its UI/design-system portion
  was rolled back by PR #5523 (21 Apr, Romuald Lemesle, -1638 lines across
  249 files), keeping only the chatbot/JWT parts. Known context: full
  vibe-coding attempt without an actual design system behind it, rolled
  back for haste — nothing from that attempt is reused here. The type
  structurally mirrors current FDS namespaces closely (gradient/background/
  scale-level shapes) — a legitimate future resurrection candidate **if**
  Sandy/Thibault ever want a deeper structural migration, but explicitly
  out of scope for this lot.
- **`palette.background.gradient.{start,end}`** — zero consumers (grepped).
  Stays dead, documented.
- **`palette.leftBar.*`** — zero consumers (grepped). Stays dead, documented.

---

## 6. Tokens à créer dans Figma — consolidated cross-product backlog

Per your request: durable-but-not-yet-tokenized properties across **both**
OpenCTI and OpenAEV, for the shared Figma backlog with Thibault.

| Concept | OpenAEV usage | OpenCTI usage | Common value today | Suggested token name |
|---|---|---|---|---|
| Tinted "accent" background (distinct from the base accent/code color) | `background.accent` (light only), `#d3eaff` — `ItemCopy.tsx`, `AttackPatternBox.tsx` ×2 | No direct equivalent — closest is `designSystem.background.bg1`-`bg4`, also untokenized on their side | Different per product — genuinely no shared value yet | `--color-elevation-background-tint` (working name) |
| Multi-tier elevation background (beyond default/paper/accent) | `background.bg1`-`bg4`/`disabled` — **fully dead**, 0 consumers (part of the never-resurrected `designSystem` scaffolding) | `designSystem.background.{bg1,bg2,bg3,bg4,disabled}` — **live**, 0 confident FDS match either (their own report, §6/"Tokens à créer") | n/a (both hardcoded, no shared value) | `--color-elevation-background-layer-{1..4}` extension, or a dedicated `bg-tint-*` scale |
| Severity `none`/`default` (neutral/unset state) | `palette.severity.default`, `#004C66` fallback in `Tag.tsx` | `severity.none`/`.default` — explicitly left untouched, "no feedback-family equivalent" | Different per product | `--color-feedback-neutral-*` (new family — neither product has a "neutral" feedback tier today) |
| Raw blue hue scale gap | n/a (OpenAEV has no `tertiary.*` scale) | `designSystem.tertiary.blue` (`#0099CC`/`#003242`) — no FDS match at all, closest is `blue-500` which is a completely different, much brighter color | OpenCTI-only | `--color-blue-{step}` scale extension |
| Generic "border" concept | n/a | `designSystem.border.{main,border1,border2}` — no FDS "border" concept exists today | OpenCTI-only | `--color-border-*` (new family) |
| Light-mode tonic sub-shades | n/a (OpenAEV has no `secondary.light`/`.dark`) | `designSystem.secondary.{light,dark}` (light mode only) — old values don't match `tonic-secondary`/`tonic-tertiary` the way dark mode's did | OpenCTI-only | Possibly a light-mode-specific tonic sub-shade pair |

Everything else durable in both products **already has** a matching FDS
token as of this lot (see §1/§3) — this table is the genuine remaining gap,
not a restatement of what's already solved.

---

## 7. Horizon — measuring convergence to zero hardcoded color

Target state (your words): zero hardcoded color, zero MUI default, in both
products, 100% resolved from `theme.css`. Every color-bearing property in
`ThemeDark.ts`/`ThemeLight.ts` now falls into exactly one bucket — a future
`mui-inventory` scanner can check convergence by counting bucket (a) vs
(b)+(c):

- **(a) Wired to an existing FDS token — this lot**: `primary`, `secondary`,
  `EE_COLOR`, `gradient.main`, `xtmhub.main`, `accent`, `paper`,
  `background.default`, `background.secondary` (dark `nav` for free). 9
  fields, both modes.
- **(b) Hardcoded, DURABLE, awaiting a Figma token — wave 2** (token
  creation in Figma + wiring iteration on both products): `background.accent`
  (light), `palette.severity.*` (partial — 5/7 levels have a portable
  mapping already validated by OpenCTI, `none`/`default` need a new
  `feedback-neutral` family), `THEME_LIGHT_DEFAULT_NAV` (technically has a
  token already — `surface-heading-layer-0` — just not applied pending your
  sign-off, so this one may resolve to (a) as soon as you decide).
- **(c) Hardcoded, JETABLE (dies with component migration) — wave 3**: none
  identified in this lot's 6 named properties. (`background.gradient`,
  `leftBar`, `designSystem` are dead code today, not JETABLE in the
  "consumed-by-a-doomed-component" sense — they have zero consumers at all,
  so they carry no convergence debt either way.)

---

## Deferred to a later phase (confirmed, same pattern as OpenCTI)

Dynamic themes: OpenAEV stores admin/tenant theme overrides in a generic
`Setting` entity (not a dedicated `Theme` table, no `built_in` flag, simple
`||` fallback everywhere — architecturally safer than OpenCTI's pre-fix
state, no strict-equality trap possible here). `@filigran/design-system` is
**not** a dependency of `openaev-front`, no `theme.css` import anywhere, no
`.dark`/`.light` class ever applied (the `data-theme` attribute set by
`AppThemeProvider.tsx` is unrelated pre-existing CKEditor scoping). Runtime
CSS-variable sync (a `useFiligranTokensSync`-style hook) is real follow-up
work, not an oversight — same conclusion as the OpenCTI pilot.

tss-react (`makeStyles` from `tss-react/mui`, ~280 usages) calls MUI's own
`useTheme()` directly — no separate state. Wiring `ThemeDark`/`ThemeLight`
is sufficient; every tss-react consumer inherits automatically, zero extra
work.

## Docs site — placeholder resolved

`fds-migration/AGENTS.md`'s "Source of truth" section originally said the
docs site wasn't deployed yet (mission override #8). It went live 2026-07
(private GitHub Pages, PR filigran-design-system#19/#23) at
`https://silver-doodle-mnyv84e.pages.github.io` — `AGENTS.md` was
regenerated (`pnpm generate:fds-migration --product openaev --out-dir
fds-migration`) to reference `https://silver-doodle-mnyv84e.pages.github.io/llms-full.txt`
instead. The generator template (`filigran-design-system/scripts/generate-fds-migration.ts`,
`DOCS_SITE_URL` constant) was updated too, so every future regeneration —
this product or any other — picks it up automatically.

---

*All FDS values above are taken from `fds-tokens.generated.ts`
(themeCssHash `sha256:6e9d0f45a1c4f762b83bd1908f04ed4d43809527ee8b43998af52aed719c5e11`,
same hash as the OpenCTI pilot's bridge — confirms both products are
cross-referencing the same upstream `theme.css` generation). If that file is
regenerated with a different upstream `theme.css`, re-verify this table
rather than assuming it still holds.*
