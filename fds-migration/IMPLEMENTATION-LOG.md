# Implementation Log — OpenAEV

Append-only session journal — never rewrite a previous entry, only add new
ones at the bottom. One entry per work session: date, what changed, why,
and any friction that should feed back into the process (prompts/scripts
in filigran-design-system).

## Log format

```
### YYYY-MM-DD — <short summary>
- Branch: fds/...
- Changed: <files>
- Friction / process feedback: <none, or what to fix upstream>
```

---

### 2026-07-18 — review pass: dedupe tonic-primary lookups
- Branch: fds/tokens-colors
- Changed: openaev-front/src/components/ThemeDark.ts, ThemeLight.ts —
  secondary/gradient.main/xtmhub.main now reuse the EE_COLOR constant
  instead of repeating the raw `--color-filigran-tonic-primary` lookup
  (4 Copilot suggestions on PR #6684); pure refactor, no value change.
- Friction / process feedback: Copilot also flagged that
  `scripts/check-fds-conformity.mjs` writes a volatile `generatedAt`
  timestamp into the tracked `reports/conformity-latest.json`, dirtying
  the working tree on every re-run even when results are identical. The
  script is a generated template copied verbatim from
  filigran-design-system (`scripts/fds-migration-templates/`), so per its
  own header the fix belongs upstream (make the report deterministic or
  gate the timestamp behind a flag), not in a local hand-edit here.

### 2026-08-10 — Header pilot: the bar's interior converts to the library
- Branch: sandyghs-miniature-enigma
- Changed: openaev-front/src/admin/components/nav/TopBar.tsx,
  TopBarIconLink.tsx (new), TopBarNotifications.tsx,
  BulkOperationsIndicator.tsx, admin/components/ariane/AskArianeButton.tsx,
  CtemCommandCenterButton.tsx, plus the matching tests and
  __tests__/utils/designSystemAssertions.tsx (new).
- What changed: the bar was `Header` + `HeaderGroup` from the library wrapped
  around an entirely MUI interior. Search is now the library `SearchField`,
  "Ask Ariane" the library `Button` (`ia`/`tertiary`, which paints the AI
  gradient the product used to hand-roll with `backgroundClip`), every icon
  button the library `IconButton` at `tertiary` priority, the account dropdown
  the library `Menu`, and every tooltip the library `Tooltip`. No MUI control
  is left in the bar — asserted, not reviewed.

- **SCOPE RULE (designer, 2026-08-10) — candidate for the playbook.**
  *Wherever the library ships a component, the pilot uses it.* Adopting the
  container and leaving the contents on the old stack is not adoption: a MUI
  control painted to look right still carries MUI's focus, hover and selected
  states, which is what the designer saw. Sole maintained exception: icon
  GLYPHS stay MUI for now (same arbitration as the Navbar pilot).
  Corollaries worth carrying upstream:
    - the rule needs a *test*, not a review — `expectNoMuiControls` fails on
      any surviving MUI control, so the rule cannot rot;
    - it needs an explicit answer for "the library has no such component"
      (here: Divider, Badge, Popover — recorded as feedback #22) so the
      exception is named rather than assumed;
    - it needs an explicit answer for "the library's component cannot express
      the required behaviour" (here: `IconButton` cannot be a link, feedback
      #21) — the escape hatch is the library's own variant function, never a
      hand-painted look-alike.

- Friction / process feedback: two traps cost real time and neither is
  visible from the library side.
  1. Default variants are not neutral. `iconButtonVariants({})` resolves to
     `priority: 'primary'`, a FILLED brand button. Swapping "the icon button"
     without naming the priority repaints every icon in the bar solid blue.
     Step 6b should say: name the variant, never take the default on trust.
  2. **Layered utilities lose to unlayered host CSS** (feedback #24). Two
     elements with the *same* class list rendered in different colours because
     MUI's CssBaseline injects an unlayered `body a`. Class present, stylesheet
     correct, no error, wrong pixels. The playbook should require, at Step 5b,
     measuring the same library class on more than one element type — that is
     what surfaced it here.

### 2026-08-10 — design decision: the bar's 57px overflow at 768px is accepted
- Branch: sandyghs-miniature-enigma
- Changed: this note only.
- Decision (Sandy, lead design, 2026-08-10): the top bar's 57px content overflow
  at a 768px viewport with the navigation rail expanded BY HAND is ACCEPTED — it
  is a non-default state, the shell already carries a pre-existing 1400px page
  floor (`openaev-front/src/admin/Index.tsx`), and global responsive behaviour
  belongs to the Layout chantier, not to this pilot.
- Friction / process feedback: none.

### 2026-08-11 — compress the narrative comments to one-line markers
- Branch: sandyghs-miniature-enigma
- Changed: the 14 product files this pilot touches (top bar, navbar, themes,
  host stylesheet, eslint config), plus LIBRARY-FEEDBACK.md entries 17 and 18.
- Why: the OpenCTI Navbar pilot was told by the product team that verbose
  comments spread through the code obstruct a component review. The norm since
  then is **at most one marker line per workaround site**, with the prose living
  in `fds-migration/`. This pilot had drifted well past that: 223 added comment
  lines across 44 blocks in product code, only 6 of them a single line, the
  largest 22 lines. Audited, then compressed to 47 lines — 41 one-liners plus
  the one deliberate exception below.
- The convention, identical to OpenCTI's `62669ad`:

  ```
  // FDS-WORKAROUND #N: <summary> — remove when <condition> — see fds-migration/LIBRARY-FEEDBACK.md #N
  ```

  A site that is **not** a library gap never gets that marker: declaring
  something removable that will never be removed misrepresents it. The 41
  one-line comments break down as:

  | Shape | Count | Used for |
  |---|---|---|
  | `FDS-WORKAROUND #N: … — remove when … — see …` | 10 | a live library gap with a removal condition |
  | `… — see fds-migration/…#N` (pointer only) | 6 | a library-owned value or constraint the product only reads |
  | a single factual sentence, no pointer | 25 | product behaviour that is nobody's debt |

- The 10 markers, by entry: **#17** the customer-configurable gradient (TopBar),
  **#20** positioning — bar offset, rail spacer, fixed-not-sticky (TopBar,
  AppNavbar ×2), **#21** the icon link (TopBarIconLink), **#22** the three MUI
  survivors (TopBar divider, BulkOperationsIndicator, TopBarNotifications),
  **#23** the Button active state (AskArianeButton), **#24** the cascade-layer
  colour (TopBarIconLink).
- The 6 pointers: **#18** the 400px cap and the search window (TopBar ×2),
  **#20** the rail widths and the transition curve (navbarConstants ×2), **#12**
  the overlay stacking level (host stylesheet), and the log itself for the
  Header adoption (TopBar's module line).
- What moved into the docs rather than being deleted:
  - **#18** was carrying the *superseded* window (`550px / 50% / 680px`) as its
    "Needed". Corrected to the round-4 `200–500px`, and gained the part that was
    only ever in the source: where a consumer can and cannot declare that window
    (the group works; the `SearchField` instance does not, because it spreads
    `style` onto its inner `<input>`; `className` needs a Tailwind build this app
    does not have; internal selectors are out of scope), plus the `min-w-0`
    consequence that makes an explicit floor load-bearing.
  - **#17** gained the opaque-stops rule: the legacy bar faded its stops at 90%
    and the library paints its layer at 94%, so pre-faded stops double the
    transparency. That belongs in the hook that eventually replaces the
    workaround, not in one product's source.
- Corrected a false statement while compressing: the host stylesheet claimed
  "this application's top bar is a `MuiAppBar` at z-index 1100". Since this
  pilot the admin bar is the library `Header`; it sits at `theme.zIndex.appBar`
  (1100) by way of an inline style. The value was right, the component named was
  not. `MuiAppBar` does still exist elsewhere in the app (`NoTenantAlert`,
  `Comcheck`, and the second top bar at `src/private/components/nav/TopBar.tsx`
  left out of this pilot's scope), which is why the claim read as plausible.
- Deliberate exception, left untouched: `deploy-feature-branch-build.yml`
  lines 89-94, six lines. It is a security rationale for a credential that must
  stay unarmed, anchored to the test that enforces it
  (`openaev-front/src/__tests__/ci-design-system-secret.test.ts`, see
  feedback #19). A pointer to another file does not protect at the point where
  someone would "helpfully" wire the token in.
- Behaviour: none. Verified mechanically rather than by reading — every touched
  file was parsed with TypeScript and re-printed with `removeComments`, before
  and after; the 14 outputs are identical (CSS and YAML compared with a textual
  comment strip). 3134 lines -> 2952.
- Friction / process feedback: the norm is real and it is nowhere a pilot will
  meet it. It came out of a review of another product's pull request and lives in
  that conversation; neither `PRODUCT-IMPLEMENTATION-PLAYBOOK.md` nor this
  repository's `fds-migration/AGENTS.md` states it, so this pilot wrote 223 lines
  in good faith and had to be told the same thing the previous pilot was told.
  It belongs in the playbook next to Step 8 ("build the product adapter"), as a
  budget: one marker line per site, prose in the product's `fds-migration/`.

### 2026-08-13 — bump to 8798cbb: the bar and rail become 100% library
- Branch: sandyghs-miniature-enigma
- Pin: `990810f` -> `8798cbbd9ae4590af8c79cd43c90b56cfb4497b7` (library PR #114, Badge).
- Rule applied (Sandy, 2026-08-13): an implemented component is composed of
  library components only — no MUI, nothing hand-styled, inside it. What has no
  library equivalent is LISTED, not improvised.
- Gate checked before bumping: `main` carries #114 **and** the two Chip EE fixes
  (label visible on hover, disabled border). Both landed *inside* #113, i.e. in
  the pin this branch was already on — verified in the installed build, not from
  the changelog: the label/icon spans carry `relative` with the painting-order
  rationale, and `disabled && isEnterpriseEdition && "border border-elevation-subtle"`
  is present. The `375c932` commit these were said to postdate does not exist in
  the repository, on any ref.
- Converted, three sites, each with a red-before-fix guard:
  1. **Ask Ariane EE marker** — was a hand-styled span in a MUI Tooltip
     (9px/600 text, 21x14 box, `theme.palette.ee.*`); now
     `<Chip label="EE" severity="ee" />`, decorative (`aria-hidden`), so the
     button keeps its own accessible name. The legacy `EEChip` component is
     untouched: it has ten other call sites outside this pilot's scope.
  2. **Unread notifications dot** — MUI `Badge variant="dot"` ->
     `<Badge content={unreadCount} dot>`. The count is still announced while the
     visual stays a dot.
  3. **Running bulk operations counter** — MUI `Badge` with an `sx` forcing 10px
     text in a 16px box -> `<Badge content={runningCount} circularAnchor>`, the
     library's own 20px counter. The 16 -> 20px growth is Sandy's arbitration.
- Feedback #22 partly closed: `Badge` received and adopted, before/after
  recorded. `Divider`/`Separator` and `Popover` remain open, re-verified against
  the export surface at `8798cbb` — #105's Popover primitive is internal, so
  `require()` returns `undefined` for it.
- Guard widened: `MuiBadge-` added to `expectNoMuiControls` (`MuiChip-` was added
  at the previous review). Both badges would now fail rather than pass unnoticed.
- Still not library, and deliberately not improvised — the list for Sandy:
  | What | Where | Why it stays |
  |---|---|---|
  | `Popover` | bulk-operations panel | no public export; #105's primitive is internal |
  | Circular + linear progress | the spinner ring, the per-operation bars | no progress component in the library |
  | General-purpose `Separator` | the AI/actions rule in the bar | only `NavbarSeparator`/`MenuSeparator`/`SelectSeparator`, each bound to its own component |
  | Icon glyphs (`SvgIcon`, `@mui/icons-material`) | every control | the designer's standing exception, same as the Navbar pilot |
  The panel's `Typography` **could** move to the library's `Text` today. It is
  listed with the block rather than converted, because the surface cannot reach
  100% until Popover and Progress exist and will be rebuilt then; converting its
  text only would be churn on a surface that must be revisited. Say the word and
  it is a ten-minute change.
- `theme.css` is byte-identical at both pins (blob `aed2ab424`), so the MUI token
  bridge stays fresh and needs no regeneration.
- Friction / process feedback: the conformity script's `bridge-freshness` compares
  against the *sibling checkout*, which is 30+ commits behind `main` here. It
  reported OK twice in a row, and both times that was only true because
  `theme.css` happened not to move. It should compare against the pinned commit,
  not against whatever the neighbouring clone has checked out, or it will one day
  report fresh on a stale bridge.
