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

---

### 2026-08-14 — Paper pilot: gate + gap inventory, STOP before any conversion
- Branch: fds/paper-pilot (from `design-system/current` @ `dd7589963`)
- Changed: `fds-migration/PAPER-GAP-INVENTORY.md` (new),
  `fds-migration/LIBRARY-FEEDBACK.md` (entries 26-30 appended),
  this log. **No product source file was touched — nothing was converted.**
- Library pin unchanged: `35a476849ba72d48cacae2568643f0b5638bc468`.
- Step 0 gate, measured on the INSTALLED build (`dist/`), rendered and read
  back through computed styles, never from types/meta/changelog:
  - `padding` prop (0/8/16/24/32): **absent**. It leaks to the DOM as an
    attribute; padding stays 24px in every case.
  - elevations 0-3: **present**, four genuinely distinct surfaces in both
    modes (dark `#070d18`/`#0d172b`/`#13213e`/`#1f3965`, light
    `#f2f2f3`/`#ffffff`/`#f4f4f6`/`#e4e5e7`).
  - `title` / `action`: **absent**, and they fall through to native HTML
    attributes (`title` becomes a browser tooltip on the whole panel).
  - Gate therefore RED — the wave stopped there, nothing was worked around
    product-side.
- Gap inventory over the 14 real sites (10 in `admin/components/lessons`,
  4 `Paper` tags in `EntityDetailCommon.tsx` driving 127 usages in 42 files):
  four blocking gaps (padding 24px imposed on sites that render 0 or 16;
  border effectively invisible in light mode at 1.03:1; surface background
  ignores a customer-configured `paper_color`; `DetailHero`'s accent gradient
  and transparent fill have no equivalent) and one non-blocking
  (`title`/`action`, whose absence the arbitrated mapping already covers).
  Radius, shadow, states, density: **no gap** — verified, not assumed.
- Measurement bench (real product components in the real MUI theme, three
  themes including a customer-configured one, plus the before/after boards)
  was built and run **outside the repo tree**; nothing of it is committed.
- Friction / process feedback:
  1. `check-fds-conformity.mjs` is a GENERATED template copied from the
     library repo, and its state file only expresses `generatedBridgeFiles` /
     `wiredFiles` / `forbiddenPatterns`. Declaring a *component motif* ("this
     zone must render the library Paper") and a guard on hardcoded padding
     re-appearing on a library Paper needs a new check, i.e. a change to the
     upstream template — it cannot be done product-side without hand-editing
     a generated file. Left undone and raised rather than improvised; a
     regex-only approximation through `forbiddenPatterns` was considered and
     rejected (JSX spans lines, and a padding class three lines below a
     `<Paper` tag is not reliably expressible as one regex).
  2. `migration-state.json` was deliberately NOT touched: with zero migrated
     zones, arming `forbiddenPatterns` on files that still render MUI would
     report green about a migration that has not happened.

### 2026-08-14 (later) — Paper pilot: Sandy's arbitrations recorded, still no conversion
- Branch: fds/paper-pilot (PR #7427, open, targeting design-system/current)
- Changed: `fds-migration/PAPER-GAP-INVENTORY.md` (§4 arbitrations, §5 child-padding
  census), this log. **Still zero product source files touched.**
- Arbitrations: G1 wait for the library `padding` prop (a library PR is out) —
  no non-iso wave, plus a new conversion rule: when the Paper carries the
  padding, the children's own padding is REMOVED, case by case. G2 the library
  measures the gap against the Figma node first. G3 the surface background must
  follow the host theme like Navbar/Header — asked in the same library PR, and
  we do NOT migrate while custom-theme tenants would lose their colour. G4
  DetailHero leaves this wave (accent gradient + transparent fill; the
  transparency falls under the "semi-transparent = phase 2" exclusion). G5 the
  conformity-template change goes to the library repo. G6 measured scope wins.
  G7 the MUI-card rule waits for a later wave.
- Scope after arbitration: **13 surfaces** — 10 in admin/components/lessons, 3
  tags in EntityDetailCommon (Section, InformationGrid, SectionBlock) driving
  **106 usages in 33 files** (measured). DetailHero: 21 usages / 21 files, not
  converted.
- Child-padding census (measured at the DOM, §5): 6 sites where the padding
  currently lives in the child (4 of them behind full-bleed dividers — moving
  the padding to the Paper would change the pattern, not just the density),
  1 site where the doubling already exists today (SectionBlock: 16px + 16px
  gutters = 32px horizontal, only 2 of its 61 usages pass disablePadding),
  4 sites whose child padding is intrinsic and must NOT be removed, 3 with
  nothing to do.
- Friction / process feedback: none new. The measurement bench was stopped at
  the end of this session; it lives outside the repo tree and will be restarted
  at the library bump.

### 2026-08-14 (end of day) — Paper pilot: conversion arbitrations recorded, waiting on the library
- Branch: fds/paper-pilot (PR #7427, open). **Still zero product source files touched.**
- Changed: `fds-migration/PAPER-GAP-INVENTORY.md` (§6), this log.
- General rule confirmed: when the Paper carries the padding, the children's
  padding is removed — EXCEPT where that padding carries meaning (full-bleed
  divider, structural gutter).
- To apply at the bump, not to re-arbitrate:
  - L1/L3/L4/L6 → `padding={0}`, ListItem gutters untouched. Strict iso: the
    dividers must keep touching the edges.
  - L9/L10 → padding moves to the Paper, and LessonsPlaceholder's 32px is
    removed **at the call site**. The shared component itself is not modified.
  - E3 SectionBlock → the 32px cumulated padding is NOT corrected in this wave.
    Separate density decision, to be taken cold; the conversion reproduces the
    existing cumulation as-is. Three-state board captured
    (`planche-e3-densite-{dark,light}.png`, kept outside the tree): current
    (Paper 16 + gutters 16 = 32px), option A `disablePadding` (0 + 16 = 16px,
    dividers edge to edge), option B gutters removed (16 + 0 = 16px, dividers
    indented). The 2 usages already passing `disablePadding` are already on
    option A.
- Blocked on: the library phase-0 PR (padding prop, host-theme contract for the
  surface background, title/action, conformity-template change) is not merged
  yet. Nothing starts product-side before the pin bump.
- The bench was restarted only to produce the E3 board, then stopped again.

### 2026-08-14 (late) — Paper pilot: option A retained for SectionBlock, scoped per usage
- Branch: fds/paper-pilot (PR #7427, open). **Still zero product source files touched.**
- Changed: `fds-migration/PAPER-GAP-INVENTORY.md` (§6.3), this log.
- Option A (`disablePadding`: Paper 0 + 16px row gutters, dividers edge to edge)
  is the retained answer for E3 — but the board it was decided on showed a
  SectionBlock hosting a list, and only 23 of the 61 usages do.
- Content census of all 61 usages: 3 host a direct List/Table, 18 host a list
  COMPONENT that renders List+ListItem itself (AgentList, FindingList,
  ExpectationList, InjectResultList, …), 2 already pass disablePadding — those
  23 take option A. The other 38 (30 free content: forms, previews, Box, text,
  chips; 8 charts) have no gutter-bearing child at all and would drop to 0px
  padding, i.e. content glued to the panel border. Captured as a second row on
  `planche-e3-densite-{dark,light}.png`.
- Consequence recorded: option A applies PER USAGE, never as the component
  default. Formulated that way it is just §6's general rule — the child's
  padding carries meaning when there IS a row, and there is nothing not to
  double when there isn't.
- Caveat for the 18 component-borne ones: they also render a
  PaginationComponentV2 toolbar above the list, which would go edge to edge
  too — to be checked visually site by site at conversion, not assumed.
- Still out of this wave: the conversion reproduces the existing cumulation;
  applying option A to the 23 usages is a density change that gets its own
  isolable, revertable commit.

### 2026-08-15 — Paper pilot: phase-0 bump, gate re-passed green, 13 surfaces converted
- Branch: fds/paper-pilot (PR #7427, open, targeting design-system/current)
- Library pin: `35a4768` → **`2e774922e1c667ee3a1e2424b5b4014dfd1a4f55`** — ONE bump
  carrying both #121 (padding prop, host-theme contract, title/action, border
  token, 17 alpha-token renames) and #123 (navbar aligned on Figma). Cold
  install (`yarn cache clean` + package removed), pin proven from the lockfile
  resolution, not from package.json.
- Step 0 gate, second pass, all measured on the new installed build: padding
  0/8/16/24/32 emits p-0/p-2/p-4/p-6/p-8 and all five classes now exist in the
  shipped stylesheet; title/action render a header row outside the surface and
  no longer leak to the DOM; the host-theme contract works in the documented
  direction only (`--bg-elevation-default-layer-N` repaints,
  `--bg-elevation-default` does nothing); the light-mode border is back to
  1.33:1 where MUI measured 1.32:1. GREEN — conversion started.
- Token renames: the bridge was REGENERATED (`pnpm generate:mui-bridge
  --out-dir`), never hand-edited. Three product references to renamed tokens
  were dead after the bump — two of them silently (CSS): `ThemeDark`/`ThemeLight`
  (`--color-feedback-info-secondary-transparency` → `-30`, a TS error),
  `TopBarIconLink` (`var(--color-filigran-brand-primary-transparency)` → `-10`)
  and `AskArianeButton` (`bg-filigran-ia-secondary-transparency` → `-10`).
  Renamed, plus the stale token names in the neighbouring comments.
- Converted: 13 surfaces (10 lessons + Section/InformationGrid/SectionBlock).
  ISO verified at the DOM in all three themes — padding, background and radius
  identical before/after on every one. DetailHero left on MUI as arbitrated.
- `title`/`action` exist now but are NOT adopted: the product header has its own
  typography and height, adopting the library's would move 106 screens. Flagged
  as a separate design decision, not done here.
- Host theming wired in `AppThemeProvider`: a customer `paper_color` is set on
  `--bg-elevation-default-layer-1`, cleared when there is no override.
- Conformity gate: the Paper motif is declared in `migration-state.json`
  (`libComponentUsage`) with both library-owned guards. `no-hardcoded-padding`
  is the "lost compensation" guard that was asked for. `imported-from-library`
  is armed on the six fully-migrated files but NOT on EntityDetailCommon.tsx,
  which imports both Papers on purpose until DetailHero moves — reason in the
  manifest, gap raised as LIBRARY-FEEDBACK #31.
- Non-regressions: typecheck clean, eslint clean (`--max-warnings 0`), vitest
  43 files / 650 tests passed, conformity 19 checks / 0 issue.
- Navbar #123 measured in the product for the first time (both levels, both
  themes): hover on highlight (dark `rgb(19,33,62)`, light `rgb(228,229,231)`),
  focus as a 2px inset border in brand with `outline: none`, level-1 selected on
  a 10% brand background, level-2 selected with NO background (brand text only).
- Expected and confirmed: the warning token changed hue — `#e6700f` → `#b8550a`
  (`--color-feedback-warning-primary` and `--border-alert-warning`).
- Friction / process feedback: LIBRARY-FEEDBACK #31 (the `imported-from-library`
  guard cannot express a partially-migrated file).

### 2026-08-15 (later) — border on customer themes, navbar cascade check, method lesson
- Branch: fds/paper-pilot (PR #7427, open). Library pin unchanged (`2e77492`).
- **Border on a customer theme — product-side, nothing missing in the library.**
  Measured all three candidate overrides in a browser: the alias
  `--border-elevation-subtle-soft` does nothing (the `.layer-N` trap again),
  the per-layer base `--border-elevation-subtle-soft-layer-1` lands and the
  library dilutes it to 40% itself. Wired in `AppThemeProvider` next to the
  surface, under the same condition: with a `paper_color` the border takes the
  customer's card colour, without one both properties are removed. Default
  themes verified unchanged. Consequence, captured and reported rather than
  glossed: derived from the card colour, the border composites to exactly the
  surface — no foreign colour left, but no outline either. That is the phase-1
  trade-off; no dedicated theme entry created (Sandy's instruction).
- **Bench CSS stack was incomplete, now byte-identical to the app's.** It loaded
  2 of the 5 stylesheets `index.tsx` imports. With all five plus the real
  `app-navbar` class, exactly one measured value changes across the navbar's
  five states on both levels: `outline` goes `3px none` → `0px none`, the
  product's own `:focus { outline: 0 }` winning. It changes nothing visually
  because #123 replaced the focus ring with an inset border — the product rule
  would have killed a ring. Everything else identical.
- **Navbar hover left border, refined.** It is not white: the hover left border
  takes the default-primary TEXT colour — `rgb(242,242,243)` in dark,
  `rgb(24,25,27)` in light, i.e. near-black in light mode. Both levels. Nothing
  changed product-side; the fix comes from the library.
- **Not done, and why:** the verification inside the RUNNING app. The stack was
  built and started for it (JDK 21 via brew, `openaev-api.jar`, the dev docker
  services, front on 3021 proxying to 8080, `/api/settings/public` answering) —
  but reaching the navbar needs a login, and submitting credentials is outside
  what this agent does, even against a throwaway local instance. The substitute
  above answers the cascade question the run was wanted for; what stays
  unverified is anything that depends on the real `AppNavbar` shell (fixed
  positioning, banner offset, collapsed state) rather than on CSS.
- **Method lesson, recorded in PAPER-GAP-INVENTORY §10 and LIBRARY-FEEDBACK #33:**
  the product-side inventory of the token rename was incomplete. Regenerating
  the bridge is necessary and not sufficient — `var(--token)` in string
  literals and library utility classes written as literals live in ordinary
  component files, outside `wiredFiles`, and fail silently. Grep both shapes
  against the INSTALLED `dist/index.css` at the next bump, OpenCTI included.
- Non-regressions after this round: typecheck clean, eslint clean, conformity
  19 checks / 0 issue.

### 2026-08-15 (night) — re-bump to #124, login surface fixed to layer 1
- Branch: fds/paper-pilot (PR #7427, open).
- Library pin: `2e77492` → **`0472f45548c69032ccfa768c5434367d3ac749c6`** (#124,
  drops the navbar hover left accent). Cold install, pin proven from the
  lockfile resolution.
- **The hover bar survived the first re-measure — and it was NOT the pin.** The
  installed `dist/index.js` no longer contains any `hover:border-l-*` class, so
  the fix was there; the bench was still serving the previous module out of
  Vite's dependency cache (`node_modules/.vite`, 63 MB). Deleting the package
  and reinstalling is not enough: **the dep-optimizer cache has to be purged
  too**, otherwise a bump verifies the old code while the lockfile proves the
  new pin. Worth remembering at every future bump.
- After the purge: hover left border is `rgba(0, 0, 0, 0)` on BOTH levels in
  BOTH themes, and `selected` keeps its brand accent (`rgb(66,202,255)` dark,
  `rgb(0,21,168)` light) — the accent was removed from hover only, as intended.
- Bridge regenerated (theme.css lost 15 lines). Diff is the content hash alone:
  no token key added or removed, and the §10 procedure (grep `var(--token)` and
  literal utility classes against the INSTALLED sheet) came back clean this
  time — first bump where it was run deliberately rather than after the fact.
- **Login page**: `Login.tsx:118` painted the form panel with
  `background.secondary`, which resolves to `--bg-elevation-highlight-layer-0`
  = `#13213e`, i.e. layer 2's value. Override dropped; the panel now takes
  MUI's `background.paper` = layer 1 (`#0d172b`), measured on the running app
  (the login page is public, so no authentication was needed). The page's two
  other panels (consent, reset) are already `variant="outlined"` = layer 1;
  `LoginLayout`'s background and aside are not Papers. One surface was at
  fault, not several. Not a Paper migration — a colour correction on a MUI
  surface, so the gate's Paper motif does not cover it.
- Feedback #34 opened: `:focus { outline: 0 }` in this product's global CSS
  wins over the library sheet and would have disarmed the OLD ring-based focus
  indicator. #123's inset border is immune to it, so nothing broke — but every
  other library component adopting the mandated `focus-visible:ring-2` pattern
  is one such reset away from a WCAG 2.4.7 failure that no gate would report.
- The customer-theme border trade-off is now recorded as an ACCEPTED phase-1
  compromise in PAPER-GAP-INVENTORY §9, with the note that a dedicated theme
  entry lifts it if the need is confirmed.
- Non-regressions: typecheck clean, eslint clean, vitest 43/650, conformity
  19 checks / 0 issue.

### 2026-08-16 — final re-bump: Paper border at 15% (library #125)
- Branch: fds/paper-pilot (PR #7427, open). Pin `0472f45` → **`a22b188`**.
- Sequence applied in the order yesterday's incident taught: servers STOPPED
  first, `.vite` and the package purged with nothing running, reinstall,
  restart, then the late dynamic-import routes (`/src/admin/Index.tsx`,
  the login page) warmed from a real browser BEFORE concluding anything.
  Both imported OK; no 504 this time.
- Pin proven from the SERVED bytes: the dev server's CSS carries
  `transparency-15` and zero `transparency-40`.
- Border contrast measured across all four elevations, both themes: dark
  1.09 / 1.09 / 1.09 / 1.05, light 1.13 / 1.15 / 1.13 / 1.12 — matching the
  expected ~1.09 dark and ~1.15 light. Light base darkened `#afb0b6` → `#95969d`.
- §10 procedure run on the breaking rename `-transparency-40` →
  `-transparency-15`: zero product reference in either silent shape, and the
  bridge diff shows only the four per-layer diluted keys renamed — the per-layer
  BASE is untouched, which is exactly why the theme override needed no change.
  The `AppThemeProvider` comment claiming 40% was corrected.
- Customer-theme border still composites to the surface (no edge): NOT fixed,
  recorded as the accepted phase-1 compromise, visible on the checkpoint board.
- Non-regressions: typecheck clean, eslint 0 error, vitest 43 files / 650 tests,
  conformity 19 checks / 0 issue.
