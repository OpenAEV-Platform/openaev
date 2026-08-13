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

### 2026-08-13 — post-review: the badges are actually announced, progress is named
- Branch: sandyghs-miniature-enigma
- Decision (Sandy, 2026-08-13): the bar's 768px content overflow with the rail
  expanded by hand moves from **57px to 69px** and is ACCEPTED. The 12px come from
  the Ask Ariane EE marker growing from the legacy 21x14 span to the library chip's
  33x24. Same reasoning as the original acceptance: a non-default state on a shell
  that already carries a 1400px page floor.
- R1, the announcement was a false green. The badge sat inside the icon slot, which
  both `TopBarIconLink` and the library's `IconButton` render `aria-hidden` — so its
  value reached nobody. The shipped test asserted `document.body.textContent`, which
  cannot tell "in the DOM" from "announced", and passed. Demonstrated before fixing,
  on the same code: the `textContent` assertion PASSED while
  `toHaveAccessibleDescription('7')` FAILED with an empty received value.
  Fixed by moving the Badge OUT of the icon slot, wrapping the control instead —
  which is the library's own mechanism: `Badge` clones its single-element child and
  appends `aria-describedby` pointing at itself, so the child must be the control.
  `TopBarIconLink` gained an `aria-describedby` pass-through for that clone to land
  on the anchor. Measured afterwards in Chromium's computed accessibility tree, both
  themes: `link "notifications"` description `"7"`, `button "bulk-operations-menu"`
  description `"2"`, names unchanged. The counter is 20x20 in the running app.
  Assertions are now on `toHaveAccessibleName`/`toHaveAccessibleDescription`;
  `textContent` is gone from these files. That required `@testing-library/jest-dom`
  as a devDependency plus `setupFiles` in `vitest.config.ts` — the matchers compute
  the name and description the way a screen reader does, honouring `aria-hidden`.
- R2, progress is now a named gap. `MuiCircularProgress`/`MuiLinearProgress` are
  detected by the shared guard and carry a dated exemption whose removal condition
  is a library `Progress`; deleting that entry turns the guard red. Both usages were
  measured and photographed with a temporary local shim (a fabricated running
  operation, never committed, patch kept at
  `~/.copilot/session-state/.../demo-progress-capture.patch`): the ring is
  INDETERMINATE (32px drawn, 2px stroke, brand at 50% alpha, no `aria-valuenow`),
  the bars are DETERMINATE (328x6, radius 12px, `aria-valuenow` 35/76/100, fill
  switching to success green once complete). Written up in LIBRARY-FEEDBACK #22 with
  a suggested API.
- Left alone on purpose: the panel's `Typography` (moves to the library `Text` in
  the loader bump), and Romuald's two threads (no re-solicitation).
- Friction / process feedback: `expectNoMuiControls` is only as good as the subtree
  it is handed. The bar-level test mocks the three interesting children to `null`,
  so for months the guard was asserting on an empty bar. The badges were only caught
  once each child got its own test. A guard that runs on a mocked-out subtree should
  say so, or the suite should assert that it saw something.

### 2026-08-13 — final bump 7e7b417: separator, Progress, Text; the bar is closed
- Branch: sandyghs-miniature-enigma
- Pin: `8798cbb` -> `7e7b4175d6c442ecb98d6c28dd42004ee71d6b29`.
- Gate, checked on the INSTALLED BUILD rather than the changelog, four for four:
  `Spinner`/`ProgressBar` resolve from the built entry point (#115); the
  `text-gradient-*` utilities carry `>*{-webkit-text-fill-color:currentColor}` ×4
  (#116); `separatorBefore?: boolean` is on `HeaderGroupProps` (#117); `Badge` was
  already adopted (#114). One near-miss worth recording: my first grep for the
  gradient fix looked only inside the `.text-gradient-*{…}` blocks and reported it
  MISSING — the fix emits a CHILD rule, `.text-gradient-ia>*{…}`. The build had it;
  the grep did not. A gate is only as good as its selector.
- Separator (Samuel's review, Sandy's arbitration): the bar is now
  `[AI cluster] 16 │ 16 [platform actions]`, both clusters at the library's own
  `gap-2`. Delivered by `HeaderGroup separatorBefore`, and the hand-painted
  `<div role="separator">` is gone. The composition is not obvious and is worth
  knowing: the 16px BEFORE the rule is the group's `ml-2` plus **the parent
  cluster's** `gap-2`, so the separator-bearing group has to sit inside a cluster.
  Directly under the `Header` (which has no gap) it would have measured 8px.
  Measured 16/16 in both themes, action order unchanged.
- Progress: both usages moved to the library (`Spinner` lg 24px on the bar button,
  `Spinner` sm 16px per running row, `ProgressBar` for the per-operation bars,
  named by the row title through `aria-labelledby` so the value is announced with
  it). The exemption in `expectNoMuiControls` is deleted — strict again. Visible
  consequences, accepted: the ring is 24px where it was 32 (the library's scale
  stops there), and the bars lose their per-status colour (`ProgressBar` has no
  colour axis) while keeping 4px instead of 6. Details in LIBRARY-FEEDBACK #22.
- `Typography` -> `Text` in the panel (7 sites). The status caption keeps its
  product colour inline: the library models no per-status text colour, and that is
  the signal Sandy asked to preserve.
- Balayage on the rendered DOM, both themes, running-operations state forced:
  **bar + rail 0 offenders**. The panel still shows the MUI `Popover` (backdrop +
  paper) and 14 `MuiBox` layout wrappers — listed, not bricolé: `Box` is layout
  rather than a control, this app compiles no Tailwind so utility classes it
  invented would be silent no-ops, and the whole panel moves when a library
  `Popover` exists.
- 768px overflow: 69px -> **85px**. The extra 16px are the separator's clear space.
  Same acceptance as before (non-default state, pre-existing 1400px page floor).
- Friction / process feedback: the library's own source was the only place the
  separator's spacing contract was written down — the prop's TSDoc says "16px on
  both sides", which is true only if the parent cluster runs `gap-2`. A consumer
  reading the prop alone would have built it directly under the `Header` and
  measured 8│16 without knowing why. Worth stating the required nesting in the
  prop's own documentation.

### 2026-08-13 — final bump 35a4768: the bar and rail are 100% library
- Branch: sandyghs-miniature-enigma
- Pin: `7e7b417` -> `35a476849ba72d48cacae2568643f0b5638bc468` (PRs #118 and #119).
- Gate, on the RENDERED build rather than the types — which mattered, because the
  types would have lied by omission the day before. Yesterday's attempt at this
  same bump was a STOP: `Spinner size="xl"` type-checked and silently rendered
  `size-6` (24px), and the `Badge` default was still `brand`. Both are now real:

  ```
  Spinner sm->size-4  md->size-5  lg->size-6  xl->size-8   (32px, the tier asked for)
  Badge   default -> bg-feedback-error-secondary            (red, was brand)
  ProgressBar tone default->brand  success->success  error->error
  ```
- Spinner: the bar's ring goes `lg` -> `xl`. Measured: the ring was 24px on a 24px
  glyph, i.e. **0.00px clearance** — invisible, hidden behind the glyph. At 32px the
  clearance is **4.00px** and it encircles the glyph again. (Sandy's brief said
  3.33px; the difference is the glyph, which measures 24px here at MUI's
  `fontSize="medium"`, not 20px.)
- ProgressBar: the per-status colour is back, from the library's `tone` axis rather
  than the `sx` the migration removed — RUNNING default, COMPLETED success, FAILED
  error. Red before fix on the three fills.
- Badge: both badges turn RED without a line of product change, because the
  library default moved. The product passes NO `tone`, deliberately: which sites
  read as alert and which as information is Sandy's call, one word per site.
- Guard, now biting per SYMBOL instead of per hand-kept name. The old list had to
  be extended three times (`Chip`, then `Badge`, then progress), and each time the
  control had been in the bar for weeks looking compliant because nothing asked
  about it. Inverted: every `Mui<Symbol>-` class is a violation unless explicitly
  allowed. Allowed = icon glyphs (standing designer exception) and the Popover
  subtree (dated exemption, removal condition = the library exports a `Popover`).
  Mutation-proven: a MUI `Typography` — a symbol the old list never contained —
  now reddens the guard.
- Sweep on the rendered DOM, both themes, running + unread forced: **155 elements
  across bar, rail and panel; 0 offenders.** 14 MUI nodes remain, all inside the
  Popover subtree and all covered by the dated exemption.
- Separator re-measured after the bump: 16 │ 16 with 8px cluster gaps, both themes.
- Accessible descriptions re-measured in Chromium's tree: bell "7", bulk "2".
- 768px overflow unchanged at 85px.
- Friction / process feedback: measuring the accessible tree with the panel OPEN
  returns nothing for the bar — MUI's `Popover` is a `Modal`, so it `aria-hidden`s
  the rest of the app while open. The first run of this checkpoint reported empty
  descriptions and I nearly recorded that as a regression. Any a11y measurement of
  the bar has to be taken with portalled surfaces closed.
