# tokens-visual-validation.md — openaev

**Phase 5 checkpoint** (`implement-tokens-product.prompt.md`). **Not a verdict.**
This report lays out the elements — screenshot pairs, delta lists, live
`getComputedStyle` cross-references, anomalies found — for Sandy's own
on-screen review. It does not conclude go/no-go; that call is hers.

## Method

- **Before** = baseline commit `344419dae` (tip of `origin/design-system/current`,
  pre-wiring), captured by Sandy checking out that commit on a clean tree in
  her own checkout (see the git-safe protocol used: commit+push the
  in-progress work first, then checkout, to avoid contaminating uncommitted
  changes).
- **After** = current branch HEAD (`fds/tokens-colors`, commit `7536cf5f0`
  at capture time).
- Local dev env used as-is: docker deps (postgres/elastic/minio/rabbitmq,
  OpenCTI-cohabitation ports) + backend `:8080` (Maven) + front `:3002`
  (Vite, HMR). No rebuild between before/after — Vite hot-reloads each
  checked-out state.
- Captured with a throwaway Playwright script
  (`openaev-front/_fds_visual_capture.mjs`, **not committed, deleted after
  this report**), admin session, viewport 1600×1000, headless Chromium.
- Both the tenant's **platform-default** theme and the admin's own
  **profile** theme were switched together for each mode, via the app's
  real API (same PUT calls the Settings UI makes), with original values
  read first and restored after every run.
- One temporary scenario was created/deleted per full run to exercise
  creation/detail/delete screens without touching the 3 real demo
  scenarios — verified 0 leftover after each run.
- 18 screenshots per state (9 screens × 2 modes) = 36 total, all under:

  ```
  .fds-validation-artifacts/openaev/2026-07-13_tokens-colors/
    before/{dark,light}/*.png
    after/{dark,light}/*.png
  ```

  (gitignored, local-only — this is the path to open for your review, not
  embedded in this report or committed anywhere).

## Screens captured (paths for your review)

| # | Screen | Filename |
|---|---|---|
| 1 | Login (logged-out) | `01-login.png` |
| 2 | Dashboard (widget grid) | `02-dashboard.png` |
| 3 | Scenarios list (dense DataTable + chips) | `03-datatable-chips.png` |
| 4 | Entity detail (Scenario overview) | `04-entity-detail.png` |
| 5 | Creation Drawer (New scenario form) | `05-creation-drawer.png` |
| 6 | Confirmation Dialog (delete) | `06-delete-dialog.png` |
| 7 | Settings → Parameters (incl. theme dropdowns) | `07-settings-themes.png` |
| 8 | Left nav expanded | `08-nav-expanded.png` |
| 9 | Left nav collapsed | `09-nav-collapsed.png` |

Each exists under both `before/` and `after/`, both `dark/` and `light/` —
4 files per row, 36 total. All present and openable.

**Methodology note, not a bug**: `09-nav-collapsed` and `02-dashboard` are
the same underlying screen state (collapsed nav is the default, captured
before the script expands it for `08`) — expect them near-identical;
that's by design, not a capture error.

## §B — near-invisible recalibrations (tied to screens)

Full detail/rationale in `TOKEN-MAPPING.md` §1; live-rendering proof in §2c.
These are expected to look the same or all-but-identical before vs after.

| Value | Old → New | Where it's live | Screens it touches | `getComputedStyle` proof |
|---|---|---|---|---|
| `PRIMARY` (light, typo `a`→`b`) | `#001bda`→`#001bdb` | Global — links, focus states, primary buttons | All 9 (light) | ✅ §2c: `rgb(0, 27, 219)` |
| `SECONDARY`/`EE_COLOR` (dark) | `#00f1bd`→`#00f0bc` | EE feature badges | 2, 3, 4, 7 (EE chip present in top bar / Settings) | ✅ §2c: EE chip `rgb(0, 240, 188)` |
| `xtmhub.main` (both modes, `GradientButton` end-color) | `#00f1bd`→`#00f0bc` | XTM Hub tab (Settings → Filigran Experience) | **Not one of the 9** — separately verified live (see below) | ✅ §2c: gradient border-box end-stop |
| `gradient.main` | `#00f1bd`→`#00f0bc` | Confirmed dead code, zero consumers | none | n/a — source-level only |

## §C — ISO-OpenCTI alignments (tied to screens)

Full detail/classification/OpenCTI-comparison in `TOKEN-MAPPING.md` §3;
live-rendering proof in §2c. These carry the deltas you should actually
scrutinize on screen — **notable**-rated ones especially.

| Value | Old → New | Delta rating | Screens it touches | `getComputedStyle` proof |
|---|---|---|---|---|
| `SECONDARY`/`EE_COLOR` (light) | `#0c7e69`→`#00f0bc` | **notable** | 2, 3, 4, 7 (EE chip) | ✅ §2c: `rgb(0, 240, 188)` |
| `ACCENT` (dark) | `#0f1e38`→`#1f3965` | **notable** | Scrollbar thumb on all 9 (dark); `background.code`/matrix-widget usages **not confirmed** on any of the 9 (couldn't verify presence without deeper navigation) | ✅ §2c: scrollbar-color thumb `rgb(31, 57, 101)` |
| `ACCENT` (light) | `#dfdfdf`→`#e4e5e7` | minor | Scrollbar thumb on all 9 (light); same uncertainty as above for `background.code` | ✅ §2c: `rgb(228, 229, 231)` |
| `PAPER` (dark) | `#09101e`→`#0d172b` | minor | 4, 5, 6, 7 (drawers/dialogs/cards); map/leaflet containers if present | ✅ §2c: `rgb(13, 23, 43)` |
| `BACKGROUND` (dark) | `#070d19`→`#070d18` | none (imperceptible) | All 9 | ✅ §2c |
| `BACKGROUND` (light) | `#f8f8f8`→`#f2f2f3` | minor | All 9 | ✅ §2c: `rgb(242, 242, 243)` |
| `background.secondary` (§D, both modes — new wiring, was `undefined`) | none → `#101b33` dark / `#e4e5e7` light | n/a (was invisible before) | Import dialog (Threat Arsenal) — **not one of the 9** | ✅ §2c targeted follow-up: hover state `rgb(16, 27, 51)` dark / `rgb(228, 229, 231)` light |

## §2b — body/html gradient (the most consequential delta of the lot)

Present on the canvas of **every single screen**, both states relevant
(before = flat fill, no gradient at all; after = real 2-stop diagonal).
Confirmed via direct `getComputedStyle` (§2c), both modes:

| Mode | Rendered `background-image` |
|---|---|
| dark | `linear-gradient(100deg, rgb(7, 13, 24) 0%, rgb(12, 21, 39) 100%)` |
| light | `linear-gradient(100deg, rgb(242, 242, 243) 0%, rgb(255, 255, 255) 100%)` |

This is the one to look at most carefully on screen — it's subtle by
design (close stop values), and the BEFORE state has literally zero
gradient (flat `background.default` fill only, MUI `CssBaseline` default),
so any before/after screen pair is a clean gradient-vs-no-gradient
comparison.

## ⚠️ Unexpected findings, flagged separately (not folded into the tables above)

1. **`01-login` dark/light pair is not a real before/after-mode
   comparison — it's the same single image, twice.** Verified by hash,
   not just inference:

   ```
   after/dark/01-login.png  == after/light/01-login.png   (identical SHA1)
   before/dark/01-login.png == before/light/01-login.png  (identical SHA1)
   ```

   This is the known architectural limitation flagged earlier this
   mission (global `DEFAULT_THEME`, tenant `IS NULL`, has no PUT
   endpoint) — the login screen always renders the platform's true global
   default, unaffected by the per-tenant/per-profile toggle the capture
   script uses. Not a wiring bug in this pilot's scope; just means the
   `01-login` **light** files under both `before/` and `after/` don't
   independently tell you anything the **dark** files don't already show.
   The dark-vs-light *comparison* for login isn't exercisable with the
   current toggle mechanism at all.

2. **Login screenshot's exposed gradient area looked "patchy" at first
   glance — pixel-sampled it to check, came back clean.** The login page
   exposes much more raw `<body>` background than any other captured
   screen (small centered card, huge margins), which makes the subtle
   gradient more visible than elsewhere — and, at thumbnail scale, gave an
   impression of blotchiness. Direct pixel sampling (`PIL`, horizontal scan
   across a clear background band) shows a smooth, monotonic gradient
   matching the `getComputedStyle`-confirmed values exactly, no banding or
   repeating pattern. What reads as "blotches" in a casual look is the
   login card's own Paper-colored box plus its shadow, not a background
   defect. Flagging so you know it was checked, not waved away — worth
   your own look regardless since perceptual judgment on subtle gradients
   is exactly what this checkpoint is for.

3. **BEFORE `light/02-dashboard.png` required a manual targeted
   re-capture** — the automated run caught a transient state where
   per-KPI-card loading spinners hadn't resolved yet (a timing race in the
   capture script, not a rendering/theming issue). Re-captured cleanly
   after a longer settle wait; final file shows fully loaded widgets,
   confirmed visually.

## Known deltas NOT exercised by these 9 screens

Consistent with OpenCTI's own reporting practice — flagging honestly
rather than implying full coverage:

| Delta | Old → New | Why not covered | Independently verified? |
|---|---|---|---|
| `xtmhub.main` / `GradientButton` | `#00f1bd`→`#00f0bc` | Only renders on Settings → Filigran Experience (XTM Hub tab), not one of the 9 mandated screens | ✅ yes — §2c live probe on that page |
| `background.secondary` (§D) | new wiring, `#101b33`/`#e4e5e7` | Only renders on the Threat Arsenal import dialog's hover/active drag state | ✅ yes — targeted follow-up probe this session |
| `background.accent` (light, standalone, `#d3eaff`) | **not touched** — no FDS/OpenCTI equivalent found, per your arbitration | Attack-matrix / dashboard-widget screens, not in the 9 | n/a — deliberately left unwired |
| `ACCENT` `background.code` usages (`DateTimeFieldController`/`TextFieldController`) | `#0f1e38`→`#1f3965` (dark) / `#dfdfdf`→`#e4e5e7` (light) | Couldn't confirm these shared field components render on any of the 9 screens (e.g. no confirmed date field on the scenario creation drawer) | Partially — same hex confirmed live via scrollbar-thumb usage elsewhere on all 9, but not via this specific component |
| `THEME_LIGHT_DEFAULT_NAV` (7th item, explicitly not applied this lot) | `#ffffff` unchanged | You haven't arbitrated this one yet (see `TOKEN-MAPPING.md` §1) | n/a — deliberately held |

## For your review

Nothing above is a conclusion — it's the evidence. Suggested order for
your on-screen pass: (1) the gradient on 2–3 screens per mode, since it's
universal and the most consequential; (2) the §C **notable** EE/secondary
color on screens 2/3/4/7; (3) whatever else you want to eyeball. Tell me
if anything looks wrong, if you want the two "not exercised" items with
independent live proof (`xtmhub`/`background.secondary`) added to the
screenshot set, or if you want the `THEME_LIGHT_DEFAULT_NAV` question
resolved before or after this PR.

---

## Annex — cross-product wiring/consumption comparison (OpenCTI ↔ OpenAEV)

> **Token-name note.** This annex is a dated snapshot (2026-07-13) and keeps
> the token names in force at capture time. The lib#32 rename (2026-07-16)
> has since renamed them — e.g. `--color-elevation-background-layer-N` →
> `--bg-elevation-default-layer-N`, `--color-elevation-surface-highlight` →
> `--bg-elevation-highlight-layer-0`, `--color-text-default-primary` →
> `--text-default-primary`, `--color-darkblue-600` → `--darkblue-600`. Same
> tokens, same values; see `TOKEN-MAPPING.md`'s token-name note for the
> current nomenclature.

Requested before your checkpoint verdict, to separate **wiring divergence**
from **consumption divergence** as the explanation for visual differences
noticed comparing both products' dark mode side by side. Read-only analysis
— both products read from their respective `fds/tokens-colors` branch
(OpenCTI via `git show origin/fds/tokens-colors:...` against the OneDrive
checkout, without touching its working tree). Nothing was changed as a
result of this comparison; **verdict below is yours, dated 2026-07-13.**

### A1. Câblage (dark mode)

| Propriété | OpenCTI | OpenAEV | Hex OCTI | Hex OAEV | Verdict |
|---|---|---|---|---|---|
| `background.default` | `--color-elevation-background-layer-0` | idem | `#070d18` | `#070d18` | ISO |
| `background.paper` | `--color-elevation-background-layer-1` | idem | `#0d172b` | `#0d172b` | ISO |
| `background.nav` | `--color-elevation-surface-heading-layer-0` | idem | `#070d18` | `#070d18` | ISO (= `default` en dark) |
| `background.accent` | `--color-elevation-background-layer-3` | idem | `#1f3965` | `#1f3965` | ISO |
| `background.secondary` | fallback hardcodé `#0C1524` (mécanisme "paper personnalisé ?" — concept différent) | `--color-elevation-surface-highlight` | `#0c1524` (non-FDS) | `#101b33` | Faux-ami (même nom, sémantique différente) |
| `MuiDialog` paper | hardcodé dédié `#0F1D34`/`#FFFFFF` | aucun override → hérite `background.paper` | `#0f1d34` | `#0d172b` | Câblé d'un seul côté |
| `primary` (dark) | `--color-filigran-brand-primary` | idem | `#0fbcff` | `#0fbcff` | ISO |
| `primary.light` (dark) | hardcodé dédié `#B2ECFF` | non défini → auto-MUI | `#b2ecff` | (calculé) | Câblé d'un seul côté |
| `primary` (light, pour mémoire) | `--color-filigran-brand-primary` | `--color-darkblue-600` (exception arbitrée, §B) | `#0015a8` | `#001bdb` | Divergent assumé (décision, pas un bug) |
| `secondary`/`EE_COLOR`/`xtmhub`/`gradient.main` | secondary=tonic-primary ; EE_COLOR = littéral legacy non câblé, absent de leur TOKEN-MAPPING | les 4 unifiés sur tonic-primary (votre arbitrage) | secondary `#00f0bc`/EE `#00f18d` (≠) | tous `#00f0bc` | Câblé d'un seul côté (OpenAEV va plus loin) |
| `text.primary` | non défini (défaut MUI) | non défini (défaut MUI) | `#fff` | `#fff` | ISO (omission partagée) |
| `text.secondary` | `--color-text-default-primary` (opaque) | non défini du tout → défaut MUI translucide | `#f2f2f3` | `rgba(255,255,255,.7)` | Câblé d'un seul côté |
| `text.tertiary`/`.light`/`.disabled` | définis, existent dans le type Palette | n'existent pas côté OpenAEV | — | — | Câblé d'un seul côté |
| `divider` | non défini (défaut MUI) | non défini (défaut MUI) | `rgba(255,255,255,.12)` | idem | ISO (omission partagée) |
| `error.main` | `--color-feedback-error-primary` | hardcodé legacy | `#f14337` | `#f44336` | Câblé d'un seul côté |
| `warn`/`warning.main` | `--color-feedback-warning-primary` | hardcodé legacy | `#e6700f` | `#ffa726` | Câblé d'un seul côté |
| `success.main` | `--color-feedback-success-primary` | hardcodé legacy | `#17ab1f` | `#03a847` | Câblé d'un seul côté |
| `dangerZone`/`ai` | câblés (familles FDS error/ia) | hardcodés legacy | — | — | Câblé d'un seul côté |
| `severity.*` | 5/7 niveaux câblés | différé explicitement (design decision vous+Thibault) | — | — | Différé des deux côtés, documenté |

### A2. Consommation

| Surface | OpenCTI consomme | OpenAEV consomme | Verdict |
|---|---|---|---|
| Fond de page (body/html) | `background.default` + gradient | idem, structure identique | ISO |
| AppBar/TopBar | `background.nav` | `background.nav` | ISO (confondu avec le fond en dark, des 2 côtés) |
| **Drawer/nav latéral permanent** | PAS `background.nav` : container = `designSystem.background.main` (=`default`), header/hover = `leftBar.*` (littéraux legacy non câblés, `#253348`) | PAS `background.nav` : `<Drawer>` MUI sans override de fond → hérite du slot Paper par défaut = `background.paper` | **Structurellement différent** entre produits |
| Tiroir de création générique (slide-over) | `default`(light)/`nav`(dark), conditionnel | même pattern conditionnel exact | ISO (portage direct) |
| Paper/Cards (générique) | `background.paper`, aucun override de fond | idem | ISO |
| Dialogs/Modals | override dédié hardcodé | hérite de `background.paper` | Structurellement différent |
| Boutons primaires (contained) | `primary.main` (auto-MUI) | idem | ISO |
| Chip EE | `EE_COLOR` legacy | `EE_COLOR` = `secondary` | Divergent (câblage, cf. A1) |

### A3. Synthèse et arbitrage (2026-07-13)

**(a) Câblage divergent — GO donné : aucun dans ce lot.** `error`/`warning`/
`success`/`dangerZone`/`ai`, `text.secondary`, `primary.light`, et le fond
`MuiDialog` sont tous reportés en **vague 2** de `TOKEN-MAPPING.md` §7, avec
`text.secondary` marqué **priorité haute** de cette vague, et le fond
`MuiDialog` ajouté comme candidat token à la liste de courses Figma (§6).
Rien câblé sur ces propriétés ce lot.

**(b) Consommation divergente — documentée telle quelle, référence phase
composants.** Le Drawer/nav latéral permanent et le namespace `leftBar.*`
d'OpenCTI restent une divergence d'architecture pré-existante entre les deux
produits, non résoluble par un simple remappage de token — actée dans
`TOKEN-MAPPING.md` comme référence pour une future migration de composants,
pas comme un défaut de ce lot.

**(c) Durable déjà connu de la liste Figma :** EE_COLOR/tonic (traité ce
lot) ; `background.accent` (light) et `severity.none`/`.default` déjà en
§6 de `TOKEN-MAPPING.md` avant cette comparaison.
