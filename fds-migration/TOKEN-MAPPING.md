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

> **Token-name note (post lib#32).** The design-system lib renamed 264
> tokens after this arbitration was recorded (filigran-design-system#32,
> merged 2026-07-16); the bridge was regenerated and the 7 tokens OpenAEV
> consumes were renamed in `ThemeDark.ts`/`ThemeLight.ts` — pure rename, no
> value change (see the `chore(fds): realign token names` commit for the
> full old→new mapping). Token names below have been updated to the new
> nomenclature so this doc always matches the code; the dated evidence
> report (`reports/tokens-visual-validation.md`) keeps the pre-rename names
> it was captured with.

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
| `..._PRIMARY` | `--color-filigran-brand-primary` (dark) / `--darkblue-600` (light, `FDS.scalars` — mode-invariant raw ramp token post lib#32) | dark: already exact · light: §B (typo `a`→`b` only) | `#0fbcff` | `#0fbcff` | none | `#001bda` | `#001bdb` | none (imperceptible) |
| `..._SECONDARY` / `EE_COLOR` | `--color-filigran-tonic-primary` | dark: §B · light: §C | `#00f1bd` | `#00f0bc` | none (imperceptible, ≤1/255 per channel) | `#0c7e69` | `#00f0bc` | **notable** |
| `..._ACCENT` | `--bg-elevation-default-layer-3` | §C (both modes) | `#0f1e38` | `#1f3965` | **notable** | `#dfdfdf` | `#e4e5e7` | minor |
| `..._PAPER` | `--bg-elevation-default-layer-1` | dark: §C · light: already exact | `#09101e` | `#0d172b` | minor | `#ffffff` | `#ffffff` | none |
| `..._BACKGROUND` | `--bg-elevation-default-layer-0` | §C (both modes) | `#070d19` | `#070d18` | none (imperceptible) | `#f8f8f8` | `#f2f2f3` | minor |
| `..._NAV` | `--bg-elevation-heading-layer-0` | dark only — **rides along with `..._BACKGROUND`** (same constant in the dark file, so this comes for free, no separate visible change) | `#070d19` | `#070d18` | none | `#ffffff` | `#ffffff` **(unchanged — see "7th item" below)** | — |

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
validated new value is `#f2f2f3` (`--bg-elevation-heading-layer-0`,
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
| dark | `--bg-elevation-highlight-layer-0` | `#101b33` |
| light | `--bg-elevation-highlight-layer-0` | `#e4e5e7` |

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
  return the FDS `--bg-elevation-default-layer-0-gradient` token
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
| dark | `#070d18` | `#0c1527` | `--bg-elevation-default-layer-0-gradient` |
| light | `#f2f2f3` | `#ffffff` | `--bg-elevation-default-layer-0-gradient` |

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

## 2c. Live `getComputedStyle` verification — §B + §C + §2b + gradient-token proof

Closes two open items: (1) your standing request for proof that these hex values
reach real rendered pixels, not just the theme source files; (2) the gradient
token-name check — at verification time (pre lib#32) the canonical name in
`theme.css` was `--color-elevation-background-layer-0-gradient`, and both
OpenCTI's and OpenAEV's generated files referenced that exact literal string
(no naming drift between the products). The lib#32 rename has since made
`--bg-elevation-default-layer-0-gradient` the canonical name — the bridge and
`ThemeDark.ts`/`ThemeLight.ts` now use it (see the token-name note at the top
of this doc); same token, same value, verification still holds.

Method: a throwaway Playwright script (`_fds_computed_style_probe.mjs`, not
committed) logged in, toggled `platform_theme`/`user_theme` via the same
proven-safe API+restore lifecycle as the capture script, and on 5 real pages
(dashboard, scenarios list, entity detail, settings/parameters,
settings/experience) either read `getComputedStyle()` directly off known
elements or scanned every injected stylesheet rule for the target `rgb()`
value — proof the color reached actual CSS, not just the JS theme object.

**§B — near-invisible recalibrations, live-confirmed:**

| Value | Expected | Rendered proof | Verdict |
|---|---|---|---|
| `PRIMARY` (light, typo `a`→`b`) | `#001bdb` | `body a { color: rgb(0, 27, 219) }` — 19–37 rule hits/page, light mode only (dark unchanged, as expected) | ✅ exact |
| `SECONDARY`/`EE_COLOR` (dark) | `#00f0bc` | EE chip: `color`/`border-color: rgb(0, 240, 188)` (dashboard + scenarios list, dark) | ✅ exact |
| `xtmhub.main` (both modes, `GradientButton` end-color) | `#00f0bc` | `GradientButton` `background-image` border-box layer: `...rgb(0, 240, 188) 100%)` — **both modes**, confirming the mode-invariant token | ✅ exact |

**§C — ISO-OpenCTI alignments, live-confirmed:**

| Value | Expected | Rendered proof | Verdict |
|---|---|---|---|
| `SECONDARY`/`EE_COLOR` (light, **notable**) | `#00f0bc` | Same EE chip, light mode: `rgb(0, 240, 188)` — identical to dark, confirms unification | ✅ exact |
| `ACCENT` (dark, **notable**) | `#1f3965` | `scrollbar-color` thumb stop: `rgb(31, 57, 101)` (5/5 pages) | ✅ exact |
| `ACCENT` (light) | `#e4e5e7` | `scrollbar-color` thumb stop: `rgb(228, 229, 231)` (5/5 pages) | ✅ exact |
| `PAPER` (dark) | `#0d172b` | `.leaflet-container`/search-root backgrounds: `rgb(13, 23, 43)` (5/5 pages) + `GradientButton` padding-box layer (double-sourced) | ✅ exact |
| `BACKGROUND` (dark) | `#070d18` | `body`/`html` text-color rule scope + `body::backdrop`: `rgb(7, 13, 24)` (5/5 pages) | ✅ exact |
| `BACKGROUND` (light) | `#f2f2f3` | `body::backdrop`: `rgb(242, 242, 243)` (5/5 pages) | ✅ exact |

**§2b — body/html gradient, both stops, both modes** (`document.body`/`document.documentElement` `getComputedStyle().backgroundImage`, direct read — the single most consequential ISO delta of the lot):

| Mode | Rendered | Expected | Verdict |
|---|---|---|---|
| dark | `linear-gradient(100deg, rgb(7, 13, 24) 0%, rgb(12, 21, 39) 100%)` | `#070d18` → `#0c1527` | ✅ exact, both stops |
| light | `linear-gradient(100deg, rgb(242, 242, 243) 0%, rgb(255, 255, 255) 100%)` | `#f2f2f3` → `#ffffff` | ✅ exact, both stops |

**§D — `background.secondary`, targeted follow-up probe** (the gap above, now closed):
opened the real `DragAndDropImportDialog` (Threat Arsenal page → "Import
payloads" button), used a real Playwright `.hover()` (actual mouse move, real
`:hover` CSS matching, not a synthetic class) on the drop-area, waited past
its 200ms `background-color` transition, then read `getComputedStyle`:

| Mode | State | Rendered | Expected | Verdict |
|---|---|---|---|---|
| dark | rest (no hover) | `rgba(0, 0, 0, 0)` | `transparent` (base style) | ✅ exact |
| dark | hover | `rgb(16, 27, 51)` | `#101b33` | ✅ exact |
| light | rest (no hover) | `rgba(0, 0, 0, 0)` | `transparent` (base style) | ✅ exact |
| light | hover | `rgb(228, 229, 231)` | `#e4e5e7` | ✅ exact — same numeric value as `ACCENT` (light), confirmed coincidental not a bug (different FDS token, `--bg-elevation-highlight-layer-0` vs `--bg-elevation-default-layer-3`, that happen to resolve to the same hex) |

No more open gaps in §B/§C live verification — all 8 recalibrated/aligned
values now confirmed reaching real rendered CSS, both modes.

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
  (`--bg-elevation-default-layer-1`, "minor" delta on their own report).
- **Treatment**: DURABLE + divergent from OpenCTI's current value → aligned.
  → **TOKEN PARTAGÉ** (`--bg-elevation-default-layer-1`, already exists
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
  (`--bg-elevation-default-layer-3`, "notable" on their report).
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
  (`--bg-elevation-default-layer-3`, "minor" on their report).
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
  `--bg-elevation-default-layer-0`. → **TOKEN PARTAGÉ**.
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
  would be `--text-default-primary` (`#f2f2f3` dark / `#18191b` light).
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
  - **Update, §8 (main merge)**: no longer *entirely* dead. #6813 added the
    namespace's first live consumer —
    `theme.palette.designSystem.background.bg2`, a menu separator border
    color in `LeftMenu.tsx` (grepped, confirmed via `git blame` as
    introduced by `dd030cb1`). Re-grepped the rest of the namespace
    (`severity.*`, `leftBar.*`, and every other `designSystem.*` sub-path)
    this pass — all still 0 consumers. Doesn't change the "leave dead"
    call, since 1 real consumer out of dozens of sub-properties doesn't
    justify resurrecting/retyping the whole namespace, but the blanket
    "fully dead" framing no longer holds for `designSystem.background`
    specifically (also reflected in §6's row for this family).
- **`palette.background.gradient.{start,end}`** — zero consumers (grepped,
  re-confirmed §8). Stays dead, documented.
- **`palette.leftBar.*`** — zero consumers (grepped, re-confirmed §8).
  Stays dead, documented — still the best candidate for "likely obsolete
  once the lib's `Navbar` component lands" per the mission-3 table.

---

## 6. Tokens à créer dans Figma — consolidated cross-product backlog

Per your request: durable-but-not-yet-tokenized properties across **both**
OpenCTI and OpenAEV, for the shared Figma backlog with Thibault.

| Concept | OpenAEV usage | OpenCTI usage | Common value today | Suggested token name |
|---|---|---|---|---|
| Tinted "accent" background (distinct from the base accent/code color) | `background.accent` (light only), `#d3eaff` — `ItemCopy.tsx`, `AttackPatternBox.tsx` ×2 | No direct equivalent — closest is `designSystem.background.bg1`-`bg4`, also untokenized on their side | Different per product — genuinely no shared value yet | `--color-elevation-background-tint` (working name) |
| Multi-tier elevation background (beyond default/paper/accent) | `designSystem.background.{bg1,bg2,bg3,bg4,disabled}` — **RESOLVED on OpenAEV as of § 9** (reused existing `--bg-elevation-default-layer-{0-3}`/`--bg-elevation-disabled`, not a new token). `.bg2`'s live consumer (`LeftMenu.tsx`) confirmed zero/imperceptible delta | `designSystem.background.{bg1,bg2,bg3,bg4,disabled}` — **live**, 0 confident FDS match either (their own report, §6/"Tokens à créer") — unchanged, still open on their side | n/a (both hardcoded, no shared value) | `--color-elevation-background-layer-{1..4}` extension, or a dedicated `bg-tint-*` scale — **may no longer be needed for OpenAEV** if OpenCTI adopts the same existing-token reuse; still useful if a purpose-built tint scale is wanted later |
| Severity `none`/`default` (neutral/unset state) | `palette.severity.default`, `#004C66` fallback in `Tag.tsx` — **RESOLVED on OpenAEV as of § 9** (reused existing `--color-feedback-neutral-primary`, both keys collapse to the same value) | `severity.none`/`.default` — explicitly left untouched, "no feedback-family equivalent" — unchanged, still open on their side | Different per product | `--color-feedback-neutral-*` (new family) — **may no longer be needed** now that OpenAEV reuses an existing token; kept for OpenCTI's side |
| Raw blue hue scale gap | `designSystem.tertiary.blue.{500,900}` (`#0099CC`/`#003242`) — **RESOLVED on OpenAEV as of § 9**, but via reuse of `--color-feedback-info-secondary-transparency` (a semi-transparent alpha token) — **not** a new opaque blue-scale token; both products' dormant `.500`/`.900` are 0-consumer, see § 9.3's semantic-change caveat before ever wiring a real consumer to this | `designSystem.tertiary.blue` (`#0099CC`/`#003242`) — no FDS match at all — unchanged, still open on their side | **Was shared** — now diverges (OpenAEV reused an unrelated alpha token, OpenCTI still hardcoded) | `--color-blue-{step}` scale extension — still relevant if a real opaque-blue consumer ever appears on either product |
| Generic "border" concept | `designSystem.border.{main,border1,border2}` — **RESOLVED on OpenAEV as of § 9** (reused existing `--border-elevation-default`/`--border-elevation-subtle`; `border1`/`border2` collapse to the same subtle value) | `designSystem.border.{main,border1,border2}` — no FDS "border" concept exists today — unchanged, still open on their side | **Was shared** — now diverges (OpenAEV resolved via reuse, OpenCTI still hardcoded) | `--color-border-*` (new family) — may no longer be needed for OpenAEV; kept for OpenCTI's side |
| Light-mode tonic sub-shades | `designSystem.secondary.{light,dark}` (light mode only) — now **live** as of #6813/§8.7; only `.main` was retokenized this pass, `.light`/`.dark` share OpenCTI's exact residual gap | `designSystem.secondary.{light,dark}` (light mode only) — old values don't match `tonic-secondary`/`tonic-tertiary` the way dark mode's did | **Now shared** — same residual gap on both products (was OpenCTI-only) | Possibly a light-mode-specific tonic sub-shade pair |
| Dialog/modal background (distinct from generic paper elevation) | `MuiDialog.styleOverrides.paper` hardcoded: `#0F1D34` (dark) / `#FFFFFF` (light) — now **live** as of #6813/§8.10, still unwired | `MuiDialog.styleOverrides.paper` hardcoded: `#0F1D34` (dark, distinct from paper `#0d172b`) / `#FFFFFF` (light, = paper `#ffffff`) — already self-flagged "no confident FDS match" in OpenCTI's own `TOKEN-MAPPING.md` (`THEME_DARK_DIALOG_BACKGROUND`/`THEME_LIGHT_DIALOG_BACKGROUND`) | **Now shared** — identical hardcoded pattern and values on both products (was OpenCTI-only) | `--color-elevation-background-dialog` (working name) — added following the 2026-07-13 cross-product comparison, see `reports/tokens-visual-validation.md` annex |

Everything else durable in both products **already has** a matching FDS
token as of this lot (see §1/§3) — this table is the genuine remaining gap,
not a restatement of what's already solved.

---

## 7. Horizon — measuring convergence to zero hardcoded color

Target state (your words): zero hardcoded color, zero MUI default, in both
products, 100% resolved from `theme.css`. Every color-bearing property in
`ThemeDark.ts`/`ThemeLight.ts` now falls into exactly one bucket — a future
`mui-inventory` scanner can check convergence by counting bucket (a) vs
(b)+(c).

> **Superseded by §8, extended by §9.** This section originally *predicted*
> wave 2 (written before #6813 landed). #6813's merge forced wave 2 to happen
> now — §8 is the executed record. Wave 3 (§9) then closed §8.6's 5
> "confirmed true gaps" once the lib's mapping guide caught up (lib#52). The
> buckets below are updated to reflect what actually shipped vs. what's
> still open; where §7's original prediction and §8's actual execution
> differ (rare — mostly root-level `warn`/`warning`/`success`/`dangerZone`,
> see the note at the end of bucket (b)), that's called out explicitly
> rather than silently reconciled.

- **(a) Wired to an existing FDS token — waves 1+2+3, both modes unless noted**:
  `primary`, `secondary`, `EE_COLOR`, `gradient.main`, `xtmhub.main`,
  `accent`, `paper`, `background.default`, `background.secondary` (dark
  `nav` for free) — **wave 1, 9 fields** — plus, newly wired this pass
  (**wave 2, executed via §8**): `background.drawer` (rule 3, Option B),
  `border.main`/`.secondary` (root), `ai.main/light/dark` (palette),
  `text.disabled`, `error.main/dark` (now genuinely mode-split),
  `ee.contrastText` (light, bug fix),
  `severity.critical/high/medium/low/info`,
  `designSystem.tertiary.*` (17 of 19 values — all but `blue.500/900`),
  `designSystem.alert.*`, `primary.light` (**light mode only**),
  `common.grey`/`.lightGrey`, `designSystem.primary.*`,
  `designSystem.secondary.main`, `designSystem.destructive.*`,
  `designSystem.ia.*`, `designSystem.background.main`,
  `designSystem.gradient.ia`/`.focus`, plus the `THEME_LIGHT_DEFAULT_PRIMARY`
  bug fix (rule 5). **~20 additional field families, both modes** (some
  single-value, some 3-tier `.main/.light/.dark`) — full detail in §8.1–8.5.
  Plus, newly wired in **wave 3 (§9, lib#52 gap-fix)**: `severity.none/
  .default`, `designSystem.tertiary.blue.500/900`, `designSystem.background.
  {bg1-4,disabled}`, `designSystem.border.{main,border1,border2}`,
  `primary.light` (**dark mode, closing the last mode-gap** — light was
  already wired in wave 2). **5 field families, both modes.**
- **(b) Hardcoded, DURABLE, awaiting a Figma token — remaining after wave 3**:
  - **Residual light-mode-only gap (§8.7)**: `designSystem.secondary.{light,
    dark}` — only `.main` retokenized, matches OpenCTI's own identical gap.
  - **Bridge-shape gap, not a value gap (§8.7)**: `designSystem.gradient.
    background` — no `--gradient-background` key exists in OpenAEV's bridge
    (OpenCTI's does); adopting the nearest key would be a real visual
    change, so left hardcoded pending a bridge-generation question.
  - **Pre-existing, deliberate, unchanged (§8.7)**: `background.accent`
    (light) — distinct from `THEME_LIGHT_DEFAULT_ACCENT`, decided before
    this merge (§3.5), #6813 didn't touch it.
  - `THEME_LIGHT_DEFAULT_NAV` (technically has a token already —
    `--bg-elevation-heading-layer-0` — pending your sign-off, unchanged this
    pass, still the "7th item" from §1).
  - **`text.secondary` — still the HIGH PRIORITY item, still untouched.**
    Deliberately kept as MUI's translucent default (explicit code comment,
    predates this merge) — not named in your go-ahead rules, not touched.
  - **Scope note — NOT actioned this pass, flagging for awareness**:
    root-level (non-`designSystem`) `warn.main` (`#E6700F`), `warning.main`
    (`#ffa726`), `success.main`/`.dark` (`#17AB1F`/`#094E0B`), and
    `dangerZone.*` (`#F44336`/`#F8958C`/`#881106`) remain hardcoded exactly
    as #6813 introduced them. Your rule 4 explicitly named
    `designSystem.alert.*` (which **was** rewired, §8.4) but not these
    root-level siblings — OpenCTI already wires the equivalent root-level
    slots to the same `feedback-*-primary` tokens, so this is a real,
    available quick win, intentionally left alone as out-of-scope for a
    strict reading of your rules rather than assumed-in.
  - `MuiDialog` background — component-level override, not a `palette.*`
    key. Now hardcoded on **both** products with matching values (§8.10,
    §6's updated row) — parity reached, still no FDS token exists.
  - **`ThemeLight.ts` root `border.main`/`.secondary`** — found in §9.4:
    still hardcoded (`#D2D2D2`/`#C2C2C2`) while `ThemeDark.ts`'s root
    `border.main`/`.secondary` are already wired — an asymmetry on the same
    guide-mapped property, flagged not fixed (not in Sandy's named 5).
- **(c) Hardcoded, JETABLE (dies with component migration) — wave 3**: none
  identified in this lot's 6 named properties, and none of the wave-2 items
  above either (all classified DURABLE — cross-cutting platform colors, not
  tied to a component slated for replacement). (`background.gradient`,
  `leftBar`, `designSystem` are dead code today, not JETABLE in the
  "consumed-by-a-doomed-component" sense — they have zero consumers at all,
  so they carry no convergence debt either way.)

---

## 8. `main` merge (PR #6813 reconciliation) — wave 2 executed, Figma-arbitrated

**Context.** `main` shipped PR #6813 (Samuel Hassine, 2026-07-21, "align UX
with OpenCTI") in parallel with this pilot's own work on
`design-system/current`. It re-hardcoded ~13 color families (some
overlapping §1-§7's already-tokenized fields, some genuinely new:
`severity.*`, `designSystem.*`, `ai.*`, tertiary ramps, `common.grey`/
`lightGrey`, `border.*`, `text.disabled`) to visually match OpenCTI, without
knowledge of the token wiring already landed here. Merging `main` into
`design-system/current` therefore produced real conflicts in both theme
files. Per Sandy's explicit go-ahead (chat, Figma-arbitrated), **Figma
tokens win on every named conflict** — this section is the settled record
of that merge, executing most of §7's predicted "wave 2" ahead of schedule
because #6813 forced the question. Verified against `fds-tokens.generated.ts`
(`themeCssHash` unchanged since §1, see footer) and cross-checked against
OpenCTI's own currently-wired `ThemeDark.ts`/`ThemeLight.ts` (read-only
reference, its wiring is more mature on these specific families) — every
mapping below was confirmed either an exact value match or an explicit,
evidenced design decision, never a blind copy (two of OpenCTI's own patterns
were caught as unsafe to copy verbatim — see "Corrections vs. OpenCTI" below).

### 8.1 Rule 1 — named conflicts, Figma wins

| Property | Mode | #6813 value | Resolved to | FDS token |
|---|---|---|---|---|
| `EE_COLOR` / `gradient.main` / `xtmhub.main` / `designSystem.secondary.main` | dark | `#00f18d` | `#00f0bc` | `--color-filigran-tonic-primary` (already `EE_COLOR` pre-merge — no drift) |
| same 4 fields | light | `#00BD94` | `#00f0bc` | `--color-filigran-tonic-primary` (mode-invariant) |
| `border.main` / `border.secondary` (root) | dark | `#252A35` / `#424751` | `#3665b4` (both) | `--border-elevation-default` — no OpenCTI wiring to mirror, Sandy's own forward decision |
| `border.main` / `border.secondary` (root) | light | `#D2D2D2` / `#C2C2C2` | `#7a7c85` (both) | `--border-elevation-default` |
| `ai.main` / `.light` / `.dark` | dark | `#B286FF` / `#D6C2FA` / `#5E1AD5` | `#a47af0` / `#e3d6fa` / `#651fe5` | `ia-primary`/`-secondary`/`-tertiary` (corrected from OpenCTI's stale `-main` key, see below) |
| `ai.main` / `.light` / `.dark` | light | `#5E1AD5` / `#D6C2FA` / `#3C108C` | `#651fe5` / `#e3d6fa` / `#3c108c` | `ia-primary`/`-tertiary`/`-secondary` (flip vs. dark — confirmed via OpenCTI) |
| `text.disabled` | dark | `#75829A` | `#a0b4e3` | `--text-default-disabled` (OpenCTI itself doesn't wire this either — original fix) |
| `text.disabled` | light | `#6E7788` | `#2b4f8d` | `--text-default-disabled` |
| `error.main` / `.dark` | dark | `#F14337` / `#881106` | unchanged (already exact) | `feedback-error-primary`/`-secondary` |
| `error.main` / `.dark` | light | `#F14337` / `#881106` (bug: same as dark) | `#e51e10` / `#f8958c` | `feedback-error-primary`/`-tertiary` — now genuinely mode-split |
| `ee.contrastText` | light | `#F2F2F3` (bug: dark-mode text reused) | `THEME_LIGHT_DEFAULT_TEXT` (`#18191b`) | n/a — copy-paste fix, mirrors dark mode's own pattern |

### 8.2 Rule 2 — `xtmhub` reunification

Confirms §1's original framing ("`xtmhub.main`... not equivalent in consumer
status" to `gradient.main`): #6813 had drifted `xtmhub.main` to `#00f18d`/
`#00BD94` while leaving `gradient.main`/`designSystem.secondary.main` on
different, also-drifted values — the 4 properties this pilot originally
unified (§1) had silently re-diverged. All 4 now point back at the single
`EE_COLOR` constant (table above) — `GradientButton.tsx`'s live consumer
(XTM Hub tab, unregistered-hub CTA, import-from-hub button) confirmed
unaffected in shape, only in end-color value.

### 8.3 Rule 3 — `background.secondary`/`.drawer` (Option B)

Custom-theme ternary (`paper === DEFAULT ? x : paper`) preserved exactly —
the per-install override still works unchanged. Only the ternary's DEFAULT
branch is retokenized:

| Property | Mode | #6813 default | Resolved to | FDS token |
|---|---|---|---|---|
| `background.secondary` | dark | `#0C1524` (hardcoded) | `#101b33` | `--bg-elevation-highlight-layer-0` (= this pilot's own §2/§D value, unchanged) |
| `background.secondary` | light | `#FFFFFF` (hardcoded) | `#e4e5e7` | `--bg-elevation-highlight-layer-0` |
| `background.drawer` | dark | `#0f1d34` (hardcoded) | `THEME_DARK_DEFAULT_PAPER` (`#0d172b`) | reuses PAPER's own token — close match to old hardcode, avoids a fresh token and avoids the parked `NAV` decision (§1 "7th item") |
| `background.drawer` | light | `#FFFFFF` (hardcoded) | `THEME_LIGHT_DEFAULT_PAPER` (`#ffffff`) | reuses PAPER's own token — **exact** match |

Option A (a dedicated GraphQL field so an admin can author the drawer/
secondary surface independently of `paper`) is **not** implemented this
pass — backlogged per Sandy's instruction, see §8.6.

### 8.4 Rule 4 — rewired onto existing tokens

| Family | Dark: #6813 → resolved | Light: #6813 → resolved | FDS token(s) |
|---|---|---|---|
| `severity.critical/high/medium/low/info` | unchanged (already exact) | unchanged (already exact) | `feedback-error/warning/alert/success/info-primary` — executes §4's already-documented, OpenCTI-validated 5-level mapping |
| `designSystem.tertiary.*` (grey/darkBlue/turquoise/green/red/orange/yellow — 19 values) | unchanged (already exact) | unchanged (already exact) | `FDS.scalars['--<hue>-<step>']` — mode-invariant raw ramp, confirmed OpenAEV's bridge has no mode-specific `--color-<hue>-N` equivalent (unlike OpenCTI's bridge — a real product-to-product bridge-shape difference, not an error) |
| `designSystem.alert.*` (info/success/alert/warning/error) | unchanged (already exact) | unchanged (already exact) | `feedback-*-primary`/`-secondary`(+`-tertiary` for success) |
| `primary.light` (root) | **left hardcoded** `#B2ECFF` | `#7587FF` → `#7587ff` | light: `FDS.scalars['--darkblue-300']` (exact match); dark: no scalar match, confirmed gap, stays hardcoded |
| `common.grey` / `.lightGrey` | `#95969D`/`#E4E5E7` → unchanged (exact) | `#494A50`/`#AFB0B6` → unchanged (exact) | dark: `FDS.scalars['--gray-400']`/`['--gray-150']`; **light uses different scalars than dark** — `['--gray-700']`/`['--gray-300']` — confirmed by exact-value match, not a naive mode mirror |
| `designSystem.primary.*` | unchanged (already exact) | unchanged (already exact) | `brand-primary`/`-secondary`/`-tertiary` |
| `designSystem.secondary.main` | unchanged (already exact, = `EE_COLOR`) | `#00BD94` → `#00f0bc` | `tonic-primary` (see 8.2) |
| `designSystem.destructive.*` | unchanged (already exact) | unchanged (already exact) | `feedback-error-primary`/`-tertiary`/`-secondary` (dark); `-primary`/`-secondary`/`-tertiary` (light — order flips vs. dark, confirmed via OpenCTI) |
| `designSystem.ia.*` | unchanged (already exact) | unchanged (already exact) | `ia-primary`/`-secondary`/`-tertiary` (dark); `-primary`/`-tertiary`/`-secondary` (light) — corrected from OpenCTI's stale `-main` key |
| `designSystem.background.main` | `#070D19` → constant ref | `#ECECF2` → constant ref | `THEME_<MODE>_DEFAULT_BACKGROUND` — zero value change, single-source-of-truth cleanup only |
| `designSystem.gradient.ia` / `.focus` | unchanged (already exact) | unchanged (already exact) | `FDS.gradients.<mode>['--gradient-ia'/'--gradient-focus']` — full pre-built gradient strings replacing hand-rolled hex+stops |

All "unchanged (already exact)" rows above mean #6813's hardcode already
matched the token bit-for-bit — the rewiring replaces the *literal* with a
*reference* (convergence/traceability improvement, per §7's methodology),
not a visual change.

### 8.5 Rule 5 — wiring bug fixed

`THEME_LIGHT_DEFAULT_PRIMARY` was wired to the raw scalar
`FDS.scalars['--darkblue-600']` (`#001bdb`) — this resolves to a *different*
value than the actual semantic `--color-filigran-brand-primary` (light)
token (`#0015a8`), which is what dark mode's equivalent constant already
correctly used. Fixed to `FDS.colors.light['--color-filigran-brand-primary']`,
mirroring dark mode's own pattern. Net effect: `THEME_LIGHT_DEFAULT_PRIMARY`
now resolves to `#0015a8` — coincidentally the exact value #6813 had
hardcoded for this same slot, so this bug fix and #6813's independent
hardcode converge on the same corrected value.

### 8.6 Backlog — 5 genuine gaps + Option A (no FDS token exists, left hardcoded)

- `severity.none` / `.default` — no neutral/unset feedback token in FDS (both products).
- `designSystem.tertiary.blue.500` / `.900` — no scalar ramp matches either value.
- `designSystem.background.bg1`–`bg4` / `.disabled` — no confident 1:1 FDS token (dead code on OpenAEV, live-but-equally-unmapped on OpenCTI).
- `designSystem.border.main` / `.border1` / `.border2` — no FDS "border" concept exists yet (OpenCTI leaves the identical values unmapped too, self-flagged in its own TOKEN-MAPPING.md).
- `primary.light` (root, **dark mode only** — light mode is now wired, see 8.4) — `#B2ECFF`, no scalar match.
- **Option A** (dedicated GraphQL field for `background.secondary`/`.drawer`, letting an admin author these independently of `paper`) — parked per Sandy's instruction; Option B (8.3) ships this pass instead.

These 5 families + Option A join §6's consolidated cross-product Figma
backlog table conceptually (not re-tabulated here to avoid duplication);
update that table the next time it's revisited.

### 8.7 Newly-found residual gaps (discretionary, flagged not fixed)

- **`designSystem.secondary.light`/`.dark` (light mode only)** — only
  `.main` matches `tonic-primary` exactly; unlike dark mode (where all
  three tiers match `tonic-primary`/`-secondary`/`-tertiary` exactly),
  light mode's `.light`/`.dark` (`#74E9CA`/`#0A8268`) don't match
  `tonic-secondary`/`-tertiary` (`#009474`/`#bdffed`). Confirmed via
  OpenCTI's own code and comment ("No confident FDS match for light/dark
  shades") — same gap exists there. Left hardcoded, not named in Sandy's
  rules, flagging rather than assuming.
- **`designSystem.gradient.background`** — OpenCTI's bridge has a direct
  `--gradient-background` key; OpenAEV's bridge does not (only
  `--gradient-default`, a different angle — 135deg vs. the current 100.35deg
  hardcode — and different stops on both modes). Adopting it would be a
  real, unauthorized visual change. Left hardcoded both modes; worth a
  design-system bridge-generation question (why does OpenCTI's bridge have
  this key and OpenAEV's not, for the same `theme.css` source?).
- **`background.accent` (light, standalone)** — unchanged, still the
  pre-existing `#d3eaff` literal documented at §3.5; #6813 didn't touch this
  field and neither did this merge.

### 8.8 Corrections vs. OpenCTI (copying its pattern verbatim would have broken)

- **`ia-main` → `ia-primary`.** OpenCTI's own currently-wired code still
  references `FDS.colors.<mode>['--color-filigran-ia-main']` — a stale key,
  renamed to `-primary` in a bridge regeneration OpenCTI's theme files never
  picked up. OpenAEV's bridge only has `-primary` (confirmed via grep, zero
  `-main` matches) — using OpenCTI's exact key would have resolved to
  `undefined` at runtime. Flagged for a future OpenCTI-side session (not
  fixed there — out of scope, read-only per this session's rules).
- **Mode-specific vs. scalar tertiary-ramp keys.** OpenCTI's bridge exposes
  `FDS.colors.<mode>['--color-gray-400']`-style mode-specific ramp keys;
  OpenAEV's bridge has no such keys (confirmed via grep) — only the flat,
  mode-invariant `FDS.scalars['--gray-400']` namespace. Used the latter;
  blind copy of OpenCTI's key paths would have broken at runtime.

### 8.9 Merge-mechanics bugs fixed (not color decisions, required for a working build)

- **`ThemeLight.ts`**: `getAppBodyGradientEndColor` was declared twice
  (both branches added it independently at non-overlapping lines, so git
  raised no conflict marker) — a real duplicate-`const` JS error. Kept the
  copy with the explanatory comment, removed the other.
- **`ThemeDark.ts`**: `background.secondary` was declared twice inside the
  same object literal (main's Option-B ternary + this pilot's pre-existing
  flat-token line) — same root cause (clean auto-merge, no conflict marker,
  invalid duplicate object key). Consolidated into the single Option-B
  entry shown in 8.3.
- **`ThemeLight.ts`**: the whole `background: {...}` key was similarly
  duplicated (git split it into two non-adjacent conflict hunks around the
  `widgets` block, rather than recognizing one logical change) —
  consolidated into the single object shown in 8.3/8.4.
- **Both files**: `MuiCssBaseline.styleOverrides.body` had `background` and
  `backgroundAttachment` declared twice each (same root cause — the
  `scrollbarColor`/`scrollbarWidth` lines from one side landed between two
  copies of the other side's `background`/`backgroundAttachment` pair, so
  git's line-based diff didn't recognize it as one hunk). Caught by
  `yarn check-ts` (`TS1117: An object literal cannot have multiple
  properties with the same name`) *after* the initial resolution pass —
  a reminder that structural duplicate-key bugs from auto-merge can hide
  past a manual conflict-marker/brace-balance review and only surface at
  typecheck. Both instances were byte-identical duplicates (no logic
  divergence), so no risk of dropped intent — fixed by deleting the
  redundant copy in each file.

### 8.10 New constants from #6813 (non-color, kept as literals)

`THEME_<MODE>_DEFAULT_TEXT` (`#F2F2F3` dark / `#18191b` light) and
`THEME_<MODE>_DEFAULT_DIALOG_BACKGROUND` (`#0F1D34` dark / `#FFFFFF` light,
new `MuiDialog.styleOverrides.paper` override) are new from #6813, with no
prior OpenAEV equivalent. §6's "Dialog/modal background" backlog row
predicted exactly this: OpenCTI already hardcodes the identical pattern
(`THEME_DARK_DIALOG_BACKGROUND`/`THEME_LIGHT_DIALOG_BACKGROUND`,
self-flagged "no confident FDS match" in its own TOKEN-MAPPING.md) — OpenAEV
now matches that same, equally-unwired state. Not a token gap to newly
backlog, §6's row already covers it; update that row's wording (OpenAEV
"No dedicated value" → "now has the identical hardcoded constants, still
unwired") next time §6 is revisited.

### 8.11 Out-of-scope finding — bridge staleness (unrelated to this merge)

`node fds-migration/scripts/check-fds-conformity.mjs` flags
`bridge-freshness: STALE` — the sibling `filigran-design-system` checkout's
`theme.css` (last changed 2026-07-17, #33 "add IBM Plex Mono...") no longer
matches the hash recorded in `fds-tokens.generated.meta.json`
(`sha256:d8710e3...`, this doc's footer). Confirmed pre-existing and
unrelated to this merge — this pilot never touches `fds-tokens.generated.ts`
or `theme.css` directly, and the mismatch is purely a function of
`filigran-design-system`'s own history advancing past this bridge's last
regeneration. Regenerating the bridge (`pnpm generate:mui-bridge --product
openaev --write-to-product`, run from `filigran-design-system`) is a
separate cross-repo action, not authorized by this pass — flagged, not
actioned. All other 11 conformity checks pass (bridge-integrity, wiring ×2,
forbidden-pattern ×8).

---

## 9. Wave 3 — the 5 confirmed gaps resolved (lib #52 mapping-guide update)

Trigger: `filigran-design-system` PR #52 (merged) completed
`TOKEN-MIGRATION-GUIDE.md` with confident mappings for the 5 gaps §8.6 left
hardcoded ("Option A — no FDS token exists"). Re-read the updated guide,
re-confirmed each mapping against the current, already-committed
`fds-tokens.generated.ts` (no bridge regeneration needed or performed — see
§9.4), and wired all 5 in both `ThemeDark.ts`/`ThemeLight.ts`. This is a
**value change**, not a pure rename (unlike the lib#32 pass) — deltas are
small for 3 of 5 and exactly zero for 1 sub-case, detailed below.

### 9.1 Before/after — dark mode

| Property | Old (hardcoded) | New token | Resolves to | Delta |
|---|---|---|---|---|
| `severity.none` | `#424242` | `--color-feedback-neutral-primary` | `#7a9cd6` | notable (was neutral grey, now a blue-tinted neutral — collapses with `.default`, see 9.3) |
| `severity.default` | `#1C2F49` | `--color-feedback-neutral-primary` | `#7a9cd6` | notable |
| `designSystem.background.bg1` | `#0C1524` | `--bg-elevation-default-layer-0` | `#070d18` | minor |
| `designSystem.background.bg2` | `#0D182A` | `--bg-elevation-default-layer-1` | `#0d172b` | minor — **live consumer**, see 9.2 |
| `designSystem.background.bg3` | `#253348` | `--bg-elevation-default-layer-2` | `#13213e` | minor |
| `designSystem.background.bg4` | `#1C2F49` | `--bg-elevation-default-layer-3` | `#1f3965` | minor |
| `designSystem.background.disabled` | `#363B46` | `--bg-elevation-disabled` | `#18191b` | minor |
| `designSystem.border.main` | `#2B3447` | `--border-elevation-default` | `#3665b4` | notable (0 consumers, inert) |
| `designSystem.border.border1` | `#424751` | `--border-elevation-subtle` | `#1f3965` | notable — collapses with `border2` (0 consumers, inert) |
| `designSystem.border.border2` | `#1C253A` | `--border-elevation-subtle` | `#1f3965` | minor (0 consumers, inert) |
| `designSystem.tertiary.blue.500` | `#0099CC` (opaque) | `--color-feedback-info-secondary-transparency` | `#0079a84d` (≈30% alpha) | notable, **semantic** — see 9.3 (0 consumers, inert) |
| `designSystem.tertiary.blue.900` | `#003242` (opaque) | `--color-feedback-info-secondary-transparency` | `#0079a84d` (≈30% alpha) | notable, **semantic** — see 9.3 (0 consumers, inert) |
| `primary.light` (root, dark only) | `#B2ECFF` | `--color-filigran-brand-secondary` | `#a8e7ff` | minor, **≈approximate per the guide** (0 consumers, inert) |

### 9.2 Before/after — light mode

| Property | Old (hardcoded) | New token | Resolves to | Delta |
|---|---|---|---|---|
| `severity.none` | `#424242` | `--color-feedback-neutral-primary` | `#afb0b6` | notable (collapses with `.default`) |
| `severity.default` | `#DDE1FE` | `--color-feedback-neutral-primary` | `#afb0b6` | notable |
| `designSystem.background.bg1` | `#F7F7F7` | `--bg-elevation-default-layer-0` | `#f2f2f3` | minor |
| `designSystem.background.bg2` | `#FFFFFF` | `--bg-elevation-default-layer-1` | `#ffffff` | **none — byte-identical**, live consumer (below) |
| `designSystem.background.bg3` | `#E4E4E4` | `--bg-elevation-default-layer-2` | `#f4f4f6` | minor |
| `designSystem.background.bg4` | `#DDE1FE` | `--bg-elevation-default-layer-3` | `#e4e5e7` | minor |
| `designSystem.background.disabled` | `#DFDFDF` | `--bg-elevation-disabled` | `#c8d6ee` | notable (0 consumers, inert) |
| `designSystem.border.main` | `#D2D2D2` | `--border-elevation-default` | `#7a7c85` | notable (0 consumers, inert) |
| `designSystem.border.border1` | `#C2C2C2` | `--border-elevation-subtle` | `#cacbce` | minor — collapses with `border2` (0 consumers, inert) |
| `designSystem.border.border2` | `#999797` | `--border-elevation-subtle` | `#cacbce` | minor (0 consumers, inert) |
| `designSystem.tertiary.blue.500` | `#0099CC` (opaque) | `--color-feedback-info-secondary-transparency` | `#42caff4d` (≈30% alpha) | notable, **semantic** — see 9.3 (0 consumers, inert) |
| `designSystem.tertiary.blue.900` | `#003242` (opaque) | `--color-feedback-info-secondary-transparency` | `#42caff4d` (≈30% alpha) | notable, **semantic** — see 9.3 (0 consumers, inert) |
| `primary.light` (root, light) | *(unchanged — already wired, `FDS.scalars['--darkblue-300']` = `--color-filigran-brand-secondary` exactly)* | — | `#7587ff` | none — comment updated only (was stale re: the dark gap) |

**Consumer re-check (grepped fresh this pass, matches §8.6's baseline):** every
property above has **zero consumers** in `openaev-front/src`, **except**
`designSystem.background.bg2` — still only `LeftMenu.tsx:42`
(`theme.palette.designSystem.background.bg2` as a separator `borderColor`).
Dark: `#0D182A`→`#0d172b`, a ~1-unit-per-channel shift, imperceptible.
Light: `#FFFFFF`→`#ffffff`, exactly identical. No other new consumers
appeared anywhere in the namespace since §8.6.

### 9.3 Two caveats worth flagging even though currently inert

- **`tertiary.blue.500`/`.900` — opaque → semi-transparent is a semantic
  change, not just a hue shift.** The guide's best-fit maps both to
  `--color-feedback-info-secondary-transparency`, an alpha-blended overlay
  token (`color-mix(... 30%, transparent)` in `theme.css`), collapsing two
  visually distinct *opaque* colors (bright cyan `#0099CC` vs. dark navy
  `#003242`) into one *translucent* value. Zero consumers today (grepped,
  both before and after), so nothing renders differently — but if either key
  is ever consumed in the future, this is not a drop-in equivalent of the old
  opaque color. Flagging prominently per the guide's own dormancy warning on
  `.900`; this pass extends the same warning to `.500`.
- **`primary.light` (dark) is an approximate match, not exact.** The guide
  itself marks `--color-filigran-brand-secondary` as "≈" for this slot:
  `#B2ECFF` → `#A8E7FF` (R 178→168, G 236→231, B unchanged, ~4% darker on two
  channels). 0 consumers confirmed, so inert today; noting for the record in
  case this is ever consumed and someone diffs against the pre-wave-3 value.

### 9.4 Scope confirmations

- **No bridge regeneration performed or required.** All 6 target tokens
  (`--color-feedback-neutral-primary`, `--bg-elevation-default-layer-{0-3}`,
  `--bg-elevation-disabled`, `--border-elevation-default`,
  `--border-elevation-subtle`, `--color-feedback-info-secondary-transparency`,
  `--color-filigran-brand-secondary`) already existed with correct values in
  the currently-committed `fds-tokens.generated.ts` — confirmed by direct
  grep. §8.11's bridge-staleness flag is unrelated: the lib commits that
  postdate this bridge's generation (#37 Button rework, #40 Tooltip/
  IconButton, #41 SearchField, #43 Navbar, #49/#50 overlay+blur tokens) touch
  shadow/overlay/component tokens, not any of the 6 above — confirmed via
  `git log`/`grep` on the lib's `theme.css`, not just inferred from commit
  titles. Still flagged, still not actioned (unchanged from §8.11).
- **Adjacent, NOT touched**: `ThemeLight.ts`'s **root** `border.main`
  (`#D2D2D2`)/`.secondary` (`#C2C2C2`) are still hardcoded, while
  `ThemeDark.ts`'s root `border.main`/`.secondary` are already wired to
  `--border-elevation-default` (§1) — a real asymmetry on the exact same
  guide-mapped property, found while investigating the `designSystem.border`
  gap (a different property) but not in Sandy's named 5, so left alone and
  flagged here rather than silently fixed.
- **Conformity**: `node fds-migration/scripts/check-fds-conformity.mjs --warn`
  gives an **identical** result before and after this pass — 12 checks, 1
  issue (the pre-existing §8.11 bridge staleness, unchanged), all 4
  forbidden-pattern checks per file still `OK`, both wiring checks still
  `OK`. Zero regression.
- **Gates**: `tsc --noEmit` (check-ts) clean; targeted `eslint` on both
  changed files, 0 errors/warnings; `vite build` succeeds (pre-existing
  chunk-size/dynamic-import warnings only, unrelated files, unchanged from
  before this pass).

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
(themeCssHash `sha256:d8710e326d866441b1d72c79b110d727332d3bd5868bf26e7dbe74d975681f5f`,
the post-lib#32 regeneration — the arbitration itself was recorded against
the pre-rename bridge, `sha256:6e9d0f45a1c4f762b83bd1908f04ed4d43809527ee8b43998af52aed719c5e11`,
which at the time matched the OpenCTI pilot's bridge; the rename changed
token names only, no values). If that file is regenerated with a different
upstream `theme.css`, re-verify this table rather than assuming it still
holds.*
