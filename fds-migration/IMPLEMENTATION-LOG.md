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

### 2026-08-14 — review reserves: the token bridge's provenance hash
- Branch: sandyghs-miniature-enigma
- Changed: openaev-front/src/components/fds-tokens.generated.{ts,meta.json} (regenerated).
- The reserve was right, and it is the false green this log predicted two entries
  ago. Library PR #116 touched `theme.css` (blob `aed2ab424` -> `14093a8`), so the
  bridge's recorded `themeCssHash` no longer described the pinned tokens. The
  conformity check kept reporting `bridge-freshness: OK` because it compares the
  bridge against the SIBLING library checkout, which was sitting 30+ commits back
  at `8ab126d`. Two bumps passed that way.
- Regenerated from the library at the pin. **No token value moved**: the emitted
  file differs by exactly one line, the provenance hash
  (`sha256:e2eb556…` -> `sha256:70ff37b…`), which is what #116 was expected to do —
  it changed `-webkit-text-fill-color` inside `@utility` blocks, not a token.
- Two things worth carrying upstream:
  1. `bridge-freshness` should hash `theme.css` **at the pinned commit** (the pin is
     in `package.json`, and the blob is fetchable), not at whatever the neighbouring
     clone happens to have checked out. As written, the check is only as fresh as a
     sibling nobody is required to update — so it can report OK on a stale bridge,
     which is the one thing it exists to prevent.
  2. `--write-to-product` resolves the product through `resolveWorkspaceRoot()`, so
     from a git worktree it writes into the MAIN clone, not the worktree the
     generator was run beside. It silently wrote to a checkout on another branch
     here; reverted, and re-run with the explicit `--out-dir`, which the script's
     own comment describes as the always-safe destination. A worktree-aware root,
     or a printed destination requiring confirmation, would have caught it.
- The sibling library checkout is left DETACHED at the pin `35a4768`, which is what
  makes `bridge-freshness` meaningful; its branch ref (`sandyghs-miniature-enigma`
  @ `8ab126d`) is untouched.
- Friction / process feedback: see the two points above.

### 2026-08-20 — Combobox wave: 23 sites adopt the library field
- Branch: fds/combobox-adoption
- Pin: f86e76e -> da333e7. Proven on the bytes the product serves, not on a local
  rebuild: `transparency-50` gone from `dist/index.css` (was 8 occurrences),
  `transparency-55` present 10 times, `--border-elevation-disabled` present,
  `yarn.lock` resolution on da333e7, and the regenerated bridge records
  `themeCssHash: 3c0ef256…`, which is the git hash of `theme.css` at that commit.
- The bridge was regenerated from a lib checkout DETACHED at da333e7, with its
  on-disk `theme.css` hash checked against git first. Generating from a stale
  sibling returns the previous values, which look perfectly plausible.
- Beyond the Combobox: the pin carries the disabled-token wave. Two tokens this
  product wires into its MUI theme move value —
  `--text-default-disabled` (dark #a0b4e3 -> #95969d, light #2b4f8d -> #62636a)
  and `--bg-elevation-disabled` (dark #0c1527 -> #313235, light #c8d6ee ->
  #cacbce). `palette.text.disabled` has 74 references in this front, so the shift
  from a blue-tinted to a neutral gray ramp is product-wide. Disabled controls are
  exempt from WCAG 1.4.3, so this is legibility, not conformance.
  `--color-filigran-brand-primary-transparency-50` was renamed to `-55`; no
  product code reads it, only the generated bridge, so the rename is inert here.
- Census, by TypeScript compiler API over 1537 files, negative controls passed in
  both directions: 44 authored MUI `<Autocomplete>` sites in 39 files, plus 33
  mount sites of 8 wrappers. The 15 files the earlier map missed all import
  `Autocomplete as X` — a renamed import, which the previous extractor did not
  resolve. Clean 2x2 split, no exception.
- Converted: 23 sites. Left on MUI: 21, each with its reason, the biggest bucket
  being the missing change cause (8 sites, 15 screens — see LIBRARY-FEEDBACK 39).
- Visual validation, both modes, three screens (creation form field, filter,
  multiple field with chips), measured at the DOM in this product's environment:
  field background, radius, label and text colours all match
  `Combobox.figma.json`; the single-row field is 37px against 36px declared, and
  the chip field 63px. Both deltas are library defects, filed as 40 and 41.
- Friction / process feedback:
  1. Three false results caught by controls rather than by luck. A `PUSH_EXIT=0`
     that was `tail`'s status, not `git push`'s — read the state, never a
     pipeline's exit code. A "the bump did not take" reading taken while yarn was
     still linking, because the wrapper had backgrounded yarn inside an already
     backgrounded command. And an "ArrowDown does nothing" verdict that was the
     key name: `Down` is not `ArrowDown`, found by a negative control on a second
     field.
  2. Two bench defects that looked like product defects: a field rendering dark on
     a light page (the bench bypassed `AppThemeProvider`, which sets the theme
     class on `documentElement`) and system-font typography (the bench did not
     import `@fontsource/*`). Both were mine. The third anomaly in the same
     capture was real — see 41.
  3. `mr-2` is NOT emitted in the served `dist/index.css`. A spacing utility taken
     from the library would have rendered 0 with no error anywhere; product-side
     spacing went through `theme.spacing()`.

### 2026-08-23 — Combobox wave: re-pin to 114854d, the three native-reset defects close
- Branch: fds/combobox-adoption
- Pin: da333e7 -> 114854d. Byte-proven on the installed package, and the first
  grep was against the wrong file: the class strings live in `dist/chunk-*.mjs`,
  not `dist/index.mjs`, which returned 0 for all three and looked like a bump that
  had not landed. In the chunk: `border-0 m-0 p-0 box-border`, `list-none`,
  `box-border`. And emitted in the SERVED stylesheet, which is the part that
  decides anything: `.border-0{border-width:0}`, `.list-none{list-style-type:none}`,
  `.box-border{box-sizing:border-box}`, `.m-0{margin:0}`, `.p-0{padding:0}`.
- No token moved: `theme.css` carries the SAME hash at both pins
  (`3c0ef256…`), so #145's token-family work was a gate, not a value change, and
  the regenerated bridge is byte-identical to the previous one. Verified by diff
  rather than assumed.
- Re-measured, both modes, the same three screens: single-row field, filter and
  chip field all **36px** — the declared value, down from 37/37/63. The input
  reports `border-width: 0` and `padding: 0`; the chip row reports
  `list-style-type: none`, `padding-inline-start: 0`, `margin-block: 0`. The grey
  rectangle and the bullet are gone from the render.
- LIBRARY-FEEDBACK 40 and 41 withdrawn, kept as a stub carrying the numbers.
  39 (the missing change cause, 8 sites / 15 screens) stays open.
- Friction / process feedback: `bridge-freshness` still SKIPS here. The library
  fixed it in #145 (`scripts/lib/fds-conformity-bridge-freshness.test.ts`), but
  this product runs its own generated copy of `check-fds-conformity.mjs`, which
  has not been regenerated. The fix does not reach the product until
  `pnpm generate:fds-migration --product openaev --write-to-product` runs —
  deliberately out of scope for this branch, which would otherwise churn the
  generated migration docs mid-review.

## 2026-08-25 — Combobox adoption closed at 41 of 44, pin `cd4b8ee` (library #155)

- Re-pinned on `cd4b8ee`. Proved on the SERVED dep chunk, not on the lockfile:
  `data-combobox-adornment` ×2, `empty:hidden` ×1, `combobox-start-icon` ×1,
  `onOpenChange` ×63 — and `leadingIcon` **×0**, the negative control for the
  rename the library made before merging.
- 13 sites converted: the 7 on controlled `open`, the row-interaction site, the
  leading-icon search, and the 4 create ornaments. Remaining: 3 `ToolBar`, settled.
- #155 answered three open questions in its own contract, so none needed a
  product guess: `open={false}` with no `onOpenChange` mounts no panel at all and
  leaves the keys their native meaning; `startIcon` lives on `ComboboxField`;
  `adornment` is `empty:hidden`, so a permissions gate that renders nothing costs
  neither width nor the shell's gap.
- `AutocompleteField`'s `renderOption` only ever tested its result for `null` — a
  caller's node was discarded. It is now `hideOption`. Its `variant` prop went
  with it (the library field has one style), across 5 typed callers.
- Chips-only inputs measured at the real pointer, not asserted: click → focus,
  `aria-expanded` stays `false`, no `aria-controls`, **0 listbox and 0 Radix
  popper**, `ArrowDown` inert, `Enter` commits a chip. The first pass showed the
  input keeping its text after committing — MUI cleared it through its `reset`
  cause, which the `cause === 'type'` filter drops. Fixed on all five and replayed.
- Three faults CI caught that local gates did not: three ornaments all named
  "Create" (ambiguous for a screen reader, and Playwright's `getByRole(name)` is
  a SUBSTRING match, so distinct names still collided — the locator became
  `exact`); `No result` existed in no locale (`No results found` does); and a
  required-field helper text invented here, removed rather than translated.
- Correction to the previous round's report: the two `arsenals` E2E jobs were
  already red on `3314a216e`, not green as reported. They asserted
  `.MuiFormHelperText-root.Mui-error` under a `MuiFormControl-root` ancestor
  around a field converted in the earlier wave. `MuiFormHelpers.getFieldError`
  now matches either shape. CI closed at **37/37**.
- V1 label pattern applied to the 4 chip-feeding fields: the name moves from
  `ComboboxLabel` to the input's placeholder, freeing ~24px of height each. Two
  measured consequences: `FilterAutocomplete`'s domains variant carries a 122-char
  guidance sentence, of which **24 characters** fit its 146px input; and
  `FilterChipPopoverInput`'s free-value input traded its "Press Enter to add a
  value" hint for the filter's name, a placeholder holding only one of the two.
- LIBRARY-FEEDBACK 42 opened: `min-h-8` as the single option-row floor. 12 sites
  render one glyph plus one line and pay 48px; no product compensation, arbitrated.

## 2026-08-27 — Select family converted, and the list toolbar given one break point

- Branch: fds/combobox-adoption
- The `Select` family was converted across the remaining product sites, over 11
  commits grouped by what made each group awkward rather than by directory. Not
  in the original scope: a page mixing the two families reads as two different
  form languages, so the review asked for the whole field surface at once.
- Three library gaps filed from this wave: **#42** the option-row floor should be
  `min-h-8`; **#43** `Select` renders no wrapper, so its label and trigger become
  siblings of whatever contains them — the most serious; **#44**
  `ComboboxLabel` has no `required`, so the two families expose different
  accessible names.
- Visual review findings, each traced to a cause rather than compensated:
  - 5 sites kept a MUI `InputLabel` after conversion. It is a floating label
    (`position:absolute; transform:scale(.75)`), so it rendered at a different
    size and offset from the library's. All five now use the library label.
  - `TagsFilter` laid its chip row out with `float:left` and a 5px top margin,
    offsetting the chips 3px from their neighbours. Now a centred flex row.
  - The toolbar's top row measured 52px. Two causes, each fixed where it is set:
    MUI's `TablePagination` wraps its content in a `Toolbar` with its own
    `minHeight`, and its captions are `<p>` carrying the browser's 1em vertical
    margins; separately the select-all `Checkbox` carries its own padding. Row
    now 36px, click targets 36x36 and 32x32 — above the 24x24 minimum.
- The list toolbar (62 pages) now has one fixed break point at 1600px: stacked
  below, one line at or above, chips on their own line either way. **Five
  adaptive attempts were built and discarded first**, each failing the same way —
  a measurement whose result depended on the layout it produced. The friction
  worth carrying forward: a layout decision taken from a measurement of that same
  layout has no fixed point. A media query has nothing to measure.
- Above the break point the container wraps rather than overflowing. Measured in
  CI: the card view carries the sort select the list view does not, so at 1600px
  it wraps to two rows while the list view sits on one. No overflow either way.
  An E2E assertion that read the line count instead of the arrangement failed on
  exactly this and was corrected, not the layout.
- Friction / process feedback: none upstream.

## 2026-09-14 — Form-field wave: SearchField, Input, Textarea, Checkbox

- Branch: fds/form-fields-adoption (PR target design-system/current).
- Inventory by the TypeScript compiler API (1 522 files, 1 438 resolved JSX sites),
  cross-checked by an import-alias pass that agreed on every line: 97 MUI
  `TextField` sites (4 of them the product wrappers carrying 186 fields), 43 MUI
  `Checkbox` sites, 3 search fields, 0 MUI `Radio` left.
- Wrapper first: `components/fields/TextFieldFds.tsx` binds the library `Input`
  and `Textarea` for both form libraries; the four existing wrappers
  (`TextFieldController`, `OldTextField`, `TextField`, `SearchFilter`) and the
  checkbox controller now render the library. Direct MUI sites were converted by a
  codemod (attribute mapping, `inputProps`/`slotProps.htmlInput` → spread,
  `InputLabelProps.required` → `required`), then reviewed.
- Elevation: drawers carry `layer-2` plus the three input aliases on the paper
  (`utils/fdsLayer.ts`, same mechanism as the sibling product's decision
  "elevation-layer-compensation"). Measured in the drawer, dark mode: header
  #070d18 → #101b33, body #0f1d34 → #13213e, library field #13213e (invisible on
  its paper) → #0c1527.
- `required`: the library sets the native attribute; measured on the asset-group
  form, the browser blocked the submit with its own message and the schema error
  never showed. `noValidate` was added to every form that hosts a converted
  required field (36 forms); re-measured: the schema message shows, no native block.
- Arbitrated in this wave (product decisions, not defects): boxed field style
  everywhere including the public login page; fixed-width search without focus
  growth; the library stepper on all number fields; `resize="none"` on every
  textarea; the info tooltip next to the label; the square library box on the two
  selection cards; the 255-character counter to be activated where a `maxLength` was
  declared and ignored before — that field is one of the eight held for #52, so
  the counter lands with that ruling.
- Held (each with a `fds:keep-mui` comment and a LIBRARY-FEEDBACK entry): two
  `type="time"` fields (#49), one text suffix (#50), one textarea action button
  (#51), the eight "Ask AI" name fields (#52), and the twelve fields plus one box
  on AI/EE-only screens, deferred until the EE chips are migrated.
- Gates: check-ts 0, lint 0 warnings, unit tests at the 81-Cron baseline (58
  files passed, 1 failed = Cron), conformity green, i18n-checker green. E2E
  selectors re-pointed for the library DOM (`Name*` → `Name`, search field →
  `searchbox`, a third helper-text shape in `MuiFormHelpers`).

## 2026-09-15 — Form-field wave, second pass: bump, held fields released, EE marker

- Library pin `3426fc3` → `4b54adb` (PR #224 only: `endText` and `endIcon.disabled`).
  Diff of the two installed `dist/` trees: Input, its meta and the type
  declarations, nothing else.
- Released from hold: the eight Ask AI name fields (library Input, the Ask AI
  action on the end slot, disabled outside the Enterprise Edition; the Ask AI
  component gained an external-trigger mode) and the regex-flags field
  (`endText="/gm"`, read as the field's description). LIBRARY-FEEDBACK #50 and
  #52 closed.
- Remaining MUI text fields are outlined and painted from the library input
  tokens (theme override, as the sibling product), so the two time fields sit on
  the same surface as their neighbours.
- Review findings fixed: the held name field had lost its form ref; outlined
  labels centred for 36px; drawer headers without bottom rule; accordions in
  drawers on the drawer surface; the assistant counter on the library number
  field (its own ± buttons removed, arbitrated).
- Enterprise Edition marker: the product `EEChip` renders the library Chip in
  its `ee` severity and small size; tooltip kept on the library Tooltip (the
  trigger's own click is kept off a plain marker); the two library chips already
  in place take the small size. On the five sites where the marker sat inside a
  tab, a card or a menu entry that already opens the licence dialog, it is
  informational: a button inside a button is invalid markup and nothing is lost
  (checked handler by handler). Self-hiding when the licence is active: not
  adopted, by arbitration.

## 2026-09-17 — Action components wave: ToggleButton held

- The seven `ToggleButtonGroup` sites stay on MUI with a `fds:keep-mui`
  reason: every one carries text segments (report format, dashboard mode,
  coverage filter with counts, preview / code, technique filter, tenant /
  platform scope, timeline scales) and the library ButtonGroup is icon-only
  by design (its RFC §7 lists these very sites as out of scope):
  LIBRARY-FEEDBACK #57. The one standalone `ToggleButton` — the inject import
  menu trigger — was an icon-only menu button and renders the library
  IconButton (secondary, 36px, named "Import injects") beside the view-mode
  ButtonGroup it already shared the row with.

## 2026-09-18 — Library bump: b4e952c, and what it lets the product drop

- The pin moves from `4b54adb` to `b4e952c`, which carries the three pull
  requests this wave asked for: the Button colour override (#226, with the
  label laid out as a row), the Tabs icon slot sizing (#227) and the default
  `type="button"` (#228).
- What the bump makes redundant, removed here and measured on a bench against
  the installed build: the `marginLeft: 4` the product had put on the EE marker
  inside six buttons (the library's label row now provides the 8px gap — 12px
  measured with the margin, 8px without), and the hand-set 16px on the two
  remediation tab logos (the slot sizes its child: 16×16 either way).
- What the bump lets the product convert: the licence banner's action button
  leaves MUI for the library Button with `color`, closing LIBRARY-FEEDBACK #59.
  The three bands keep their hexes through primitive tokens — `orange-700`
  (#884106) and `turquoise-800` (#005744) are exact; the blue band moves from
  #007399 to `blue-700` (#0079a8), the nearest primitive, one shade lighter.
  The library picks the label ink and guarantees it stays over 4.58:1.
- Still on MUI, unchanged: the reporting split button (#58) and the seven
  text-labelled toggle groups (#57). The 362 explicit `type="button"` stay too:
  correct, now redundant, and not worth churning inside an open pull request.
- Opened while doing it: LIBRARY-FEEDBACK #61, `PrimitiveColorToken` is
  declared by the library but not exported, so the banner reads the union off
  the prop instead of naming it.

## 2026-09-18 — Chip wave follow-up: a chip must not outgrow its list cell

- Visual pass finding. The MUI chips of two list columns carried a fixed
  width (`chipInList`: 120px on the scenario type, 140px on the injector
  contract) and truncated inside it. The library chip sizes to content
  (ruling B14), so a longer label now outgrew its cell and the CELL cut it:
  the scenario "Type" chip spilled 2px and lost its rounded edge against the
  status chip, and the atomic-testing contract chip spilled 49px out of a
  102px cell, cut mid-word with no ellipsis.
- Fixed by capping those two chips at their cell (`maxWidth: '100%'`), which
  hands the truncation back to the library: the label ellipsizes inside the
  chip and its own tooltip opens only while the label is actually clipped.
  The dead `withStyles` block the conversion had left in `InjectorContract`
  goes with it.
- Swept the ten main list screens at 1400px before and after: two clipped
  chips before, none after.
- Left alone, pre-existing: the scenario "Status" cell still draws a
  text-overflow ellipsis because its own padding makes `scrollWidth` exceed
  `clientWidth` by 11px, with the chip itself 8px narrower than the cell —
  the MUI chip overflowed it by far more.

## 2026-09-18 — Button wave follow-up: the implicit `type="button"`

- The library `Button` sets no `type`, so a converted button with none fell
  back to the HTML default, `submit`, where MUI's `ButtonBase` had resolved it
  to `"button"`. Every converted button without an explicit type inside a form
  became a submit button: 362 sites in 201 files, 355 of them in a file that
  carries a form.
- Caught by the product's end-to-end suite on the pull request, by nothing
  local: `check-ts`, `lint`, the unit tests and the conformity script are all
  blind to it. The threat-arsenal "New argument" button submitted the action
  form instead of appending a row, so `action_arguments.0.key` never existed
  (`threatArsenal-creation.spec.ts`, two tests, three attempts each).
- Fixed by writing `type="button"` on those 362 sites, which is exactly what
  MUI rendered before. Verified on the running product: the argument field
  appears and the drawer stays open. Reported as LIBRARY-FEEDBACK #60 — the
  library's own `IconButton` already defaults to `"button"`.

## 2026-09-17 — Action components wave: Button

- Button (473 MUI sites, 230 files) on the library: 468 sites by a codemod
  (TypeScript compiler, one pass, imports merged into the existing library
  import), the rest by hand — the three wrappers (ButtonCreate loses its MUI
  branch, DialogConfirmation reads `destructive` from its `submitColor`,
  ActionButtons), the two GradientButton components and their callers, the six
  EE-gated actions and four icon-only buttons.
- Mapping: `contained` → `priority="primary"` (default, omitted), `outlined` →
  `secondary`, `text` and the bare MUI button → `tertiary`; `color="error"` and
  `"warning"` → `variant="destructive"`, `"ee"` and the gradient buttons →
  `variant="highlight"`, the AI actions (Ask Ariane, generate with AI, suggest
  TTPs) → `variant="ia"`; `secondary`, `success`, `inherit` → the default.
- Placement rule (B1): the default 36px everywhere a MUI medium button stood
  and in dialog / drawer footers, list headers, detail headers and bulk bars
  even when MUI said `small`; `sm` (24px) where MUI said `small` inside a form,
  a card or a Paper header row. `large` has no library counterpart and reads
  36px.
- A button that navigates (35 sites: `component={Link}`, `component="a"`, bare
  `href`) is `asChild` around the anchor, with `to` / `href` / `target` / `rel` on
  the anchor and the MUI icon inside it (the library ignores `startIcon` under
  `asChild`); the parent-scenario pivot splits into a link when the scenario is
  reachable and a disabled button otherwise. The EE marker sits inside the
  button after the label, as OpenCTI does, and the EE-gated actions read
  `primary` with the licence and `secondary` without.
- MUI icons inside a button get `fontSize="small"` (the library slot does not
  size its icon: LIBRARY-FEEDBACK #56). `sx` layout keys (margins, alignSelf,
  flexShrink, whiteSpace, position) became `style` with the 8px spacing
  resolved; colour, typography, radius and hover keys (114 in all) leave with
  MUI. `disableElevation` and the ripple props are gone. The licence banner's
  button keeps the banner's urgency colour and stays on MUI (`fds:keep-mui`,
  LIBRARY-FEEDBACK #59: a colour override for exceptional cases). The
  reporting split button (a MUI ButtonGroup: generate now + format menu) stays
  on MUI with a `fds:keep-mui` reason until the library offers a split button
  (LIBRARY-FEEDBACK #58). Three icon-only MUI buttons received a name (`Add`,
  copy to clipboard); the copy one is an IconButton.
- One test adjusted: the XTM Hub tab test mocked a GradientButton path that the
  screen never imported; the assertion now looks for the connect button text.
- Measured on the running product at 1400px after the change: list header
  Create 36px primary beside a 36px highlight "Import from Hub" anchor, detail
  header Launch / Configuration 36px, the Deploy card action 24px secondary
  with the EE marker inside, the EE settings CTA 36px highlight, dialog and
  drawer footers 36px, no MUI button left on the scenario, simulation,
  settings and integrations screens, no horizontal overflow.

## 2026-09-17 — Action components wave: Tabs

- Tabs (15 files, every tab bar of the product) on the library: the product
  wrapper `components/common/tabs/Tabs.tsx` keeps its `entries` / `currentTab`
  / `onChange` contract so its 12 consumers did not move; the 14 direct MUI
  bars were rewritten by hand (six route-based bars, six stateful bars, the
  inject drawer and the variables dialog). `@mui/lab` TabContext, TabList and
  TabPanel leave with them.
- Route-based bars (scenario, simulation shell, atomic testing, simulation
  inject, phishing, integrations) are `TabsTrigger asChild` around the router
  `Link`, with `aria-current="page"` on the current one and `panels="external"`
  on the bar (the panels are the routed pages). The simulation shell's "no tab
  matches" value goes from `false` to `''`. The EE marker of the Remediations
  tab sits inside the link after the label (the trigger's own 8px gap replaces
  the MUI margin). The simulation inject's Remediations tab compared a path with
  a query string to the pathname and could never read as selected; it now
  compares the bare path like its siblings.
- Stateful bars: numeric MUI values (target tabs, configuration enums, platform
  index) become `String(index)` and the handlers take the value alone. The
  inject drawer keeps its three panels mounted (`TabsContent forceMount`) so the
  forms keep their state, and hides the inactive ones itself: the library leaves
  a force-mounted panel visible (LIBRARY-FEEDBACK #55). The variables dialog
  renders its two lists conditionally, as the MUI TabPanel did.
- Platform logos (threat arsenal remediation tabs, atomic remediation rail) go
  in the trigger's 16px `icon` slot with an empty `alt` (the name is the label);
  the rail is `orientation="vertical"` in a grid cell so it fills the column.
  The hard-coded English `aria-label` of the remediation bar becomes the
  translated "Security platforms" on the tablist. The 30px/13px MUI tab class,
  the `textTransform: none` and the `::first-letter` workarounds go with MUI.
- The library trigger activates on pointer down (manual activation; arrow keys
  move focus only, Enter or Space activates): the attack-path panel test fires
  `mouseDown` where it fired `click`.
- Measured on the running product at 1400px after the change: 44px triggers,
  14px labels (600 when active), brand ink and 2px brand underline on the
  active tab, `aria-current="page"` on the current routed link, arrow keys move
  focus without navigating and Enter follows the link, the inject drawer shows
  one panel and hides the other with `hidden`, no MUI tab bar left on the
  scenario, simulation, atomic testing, phishing, integrations, asset and threat
  arsenal screens, no horizontal overflow. The vertical rail and the platform
  tabs were not reached locally (they need a payload-backed atomic testing and
  the Enterprise Edition).

## 2026-09-17 — Action components wave: Chip

- Chip (173 sites, 107 files): 169 on the library, 4 filter chips kept on MUI
  with a reason (LIBRARY-FEEDBACK #54). Three codemod passes with placeholders
  both ways (154 sites) plus a hand pass on the status wrappers (ItemSeverity,
  ItemStatus, ItemCriticality, ItemBoolean, InfoChip, LabelChip, Tag, CvssBadge,
  ExerciseStatus and the other status chips), the tag components (ItemTags,
  ItemDomains, DocumentType) and the three chips that navigate.
- One colour table for every status chip (`statusSeverity` /
  `colorStyleSeverity` in utils/statusUtils.ts, `criticalitySeverity` in
  criticalityColor.ts), by ruling: green → low, blue → info, orange → medium
  for a status and high for a severity, red → critical, greys and the brown
  "canceled" → neutral, violets → info. Colours carried by the data (tag,
  domain, document type, notification operation, AI events) go through `color`
  and render as the library wash; brand-tinted outlined labels read `info`.
  Fixed widths (100–250px), the 20/25px heights, uppercase and italic go with
  the MUI classes; the MUI `icon` becomes `startIcon`, `onDelete` gets a
  `deleteLabel`.
- A chip that navigates (challenge simulations, context links, target links)
  is a real link around a non-clickable chip (`components/common/chips/chipLink.ts`
  carries the focus ring classes): ⌘-click and "open in a new tab" kept, no
  button inside a link. A non-clickable chip under a TooltipTrigger gets a span
  host. The loading state of ItemBoolean shows a library Spinner beside a
  "Loading" label instead of a spinner as label; its EE state renders the EEChip.
- Measured on the running product at 1400px after the change (see the measure
  log in the PR): 24px chips, 14px labels, white ink on the 30% wash, no
  horizontal overflow on the scenario and atomic-testing lists.

## 2026-09-17 — Action components wave: IconButton

- IconButton (135 sites, 99 files) on the library: 133 converted by a codemod
  in two passes (placeholders both ways, the loop never re-reads what it wrote),
  2 decorative icon holders replaced by plain spans (the domain icon bar and
  the challenge status glyph were never actions). The library names nothing
  through its tooltip, so 47 unnamed buttons received an `aria-label` first:
  the tooltip text when there was one, else the verb of the icon (`More
  actions`, `Delete`, `Close`, `Add`, `Back`, `Scheduling`, `Launch`, `Reset`,
  `Fullscreen`, `Refresh`, `Profile`, `Default value`, `Ask AI`, `Import`, and
  `Collapse`/`Expand` for the chevrons); five components gained the
  translation hook for it. No new translation key.
- Placement rule applied: `sm` (24px) in list rows, forms and cards; `md`
  (36px) in page headers, drawer and dialog headers, side panels and the bulk
  toolbar. The kebab wrapper takes its size from the `variant` it already
  received (`icon` in a row, `toggle` in a header). `color="error"` →
  `destructive`; AI-tinted buttons → `variant="ia"`; the read/unread and
  subscribe switches express their state with `active`; icon-only links keep
  their anchor under `asChild`; `sx` layout keys (position, margins, flex)
  moved to `style`, colour and radius keys dropped (the library button carries
  its own look).
- Measured on the running product at 1400px: row kebab 30 → 24px (icon 20px),
  header icon buttons 32 → 36px, drawer close 34 → 36px, no horizontal
  overflow on the list or the detail page. A React warning ("Cannot update a
  component while rendering a different component", Root) is present on
  unconverted screens too: pre-existing.

## 2026-09-17 — Action components wave: Badge

- Badge (4 sites, 2 files) on the library: the unread dot of the notification
  list and the three "advanced setting set" dots on the rule cogs of the XLS
  mapper form. One tone for badges by ruling (the default, red): the amber and
  the tonic dots go. Each dot carries an `accessibleText` (`Unread`, `Default
  value set`, added to the nine language files) so the state is announced
  instead of being colour only. The cog badge now wraps its button
  (`bareAnchor="md"`) rather than the glyph inside it.
- Measured before/after on the running product with a temporary notification
  and a temporary mapper (both created and deleted for the measurement): dot
  8×8 px before and after; rgb(255,167,38) → rgb(241,67,55) on the
  notification, rgb(0,240,188) → rgb(241,67,55) on the cog. On the cog the dot
  sits at the corner of the 40px MUI button for now (10px right of the glyph);
  the IconButton pass replaces that button by the 36px library one, which puts
  the dot back on the glyph's corner.

## 2026-09-15 — Visual passes and the tooltip wave

- Two visual passes, measured before and after on the rebuilt bundle: native
  time and date glyphs follow the colour scheme; list search fields share the
  36px toolbar height; 8px between the filter popover fields and between the
  pagination control and the actions; 16px between a breadcrumb and the toolbar
  (the toolbar no longer pulls itself up); every date picker outlined and
  painted by the theme (the MUI X picker draws its own outlined input, hence a
  `MuiPickersOutlinedInput` override and the type augmentation allowed in the
  lint config); dialogs back to the theme paper colour, iso the sibling product;
  report structure blocks one layer above the drawer with the input aliases, the
  title label left of its field (LIBRARY-FEEDBACK #53), destructive remove button;
  the e-mail warning block framed; the facet sidebar card scrolls inside its frame.
- E2E: the arguments validation test blurs the focused key field before saving.
  The library input forwards the form ref, so the new row really receives
  focus; the blur validation then moved the save button under the click and the
  submit handler never ran (proved with a probe on the trace).
- Tooltips: 283 sites converted by codemod and 25 by hand to the compound
  library Tooltip, one provider at the root; MUI `arrow`, `enterDelay`,
  `slotProps` surfaces dropped (the status chip's paper-like bubble and the
  widget title's info-coloured bubble now take the library surface; the tag
  tooltip keeps its text transform, not its first-letter rule). Two sites kept
  on MUI with a reason. Eight tests wrap their render in the provider; a hover
  test covers the converted custom tooltip.
