# Gap inventory — library `Paper` vs OpenAEV container surfaces

What survives here are the RULES and the measured references the code and the
render guard point at. The round-by-round narrative that produced them was cut
on 2026-08-18 — see §20 for the two rules that pruning taught.

- Product: OpenAEV, branch `fds/paper-pilot` onto `design-system/current`.
- Every value below is **measured on the installed build** and on the product's
  real components mounted in the real MUI theme (`ThemeDark`/`ThemeLight`,
  `spacing: 8`) — never read from types, changelog or docs.

---

## 5. Child paddings — the sites the "no doubling" rule covers

Read at the DOM on the real components: computed padding of the direct
children, and of the first inner row that carries one. Four families, and they
do not call for the same decision.

### 5.1 — The padding lives in the child, the Paper is at 0 (**6 sites**)

If the Paper takes a non-zero padding, the child's must go.

| site | Paper padding | measured child padding | what it carries |
|---|---|---|---|
| **L1** `LessonsObjectives.jsx:26` | 0 | `MuiListItem` **8px 16px** | MUI row gutters |
| **L3** `simulations/LessonsCategories.jsx:140` | 0 | `MuiListItem` **8px 16px** | same |
| **L4** `simulations/LessonsCategories.jsx:203` | 0 | `MuiListItem` **8px 16px** | same |
| **L6** `scenarios/LessonsCategories.jsx:115` | 0 | `MuiListItem` **8px 16px** | same |
| **L9** `simulations/Lessons.tsx:355` | 0 | `LessonsPlaceholder` **32px** | the empty state's own margin |
| **L10** `scenarios/Lessons.tsx:217` | 0 | `LessonsPlaceholder` **32px** | same |

- **L1/L3/L4/L6** — the rows carry **full-width dividers**. Removing the
  gutters and giving the padding to the Paper **pulls the dividers in too**:
  the edge-to-edge pattern disappears. That is not a padding transfer, it is a
  change of pattern. Under strict iso these four keep `padding=0` and an
  untouched child.
- **L9/L10** — clear case: the placeholder carries 32px, no divider, no side
  effect. **`LessonsPlaceholder` is a shared component** — the removal happens
  at the CALL SITE, never in the component, or its other consumers break.

### 5.2 — Doubling **already present** in the product (**1 site**)

| site | Paper padding | child padding | real horizontal total |
|---|---|---|---|
| **E3** `SectionBlock` (`EntityDetailCommon.tsx:188`) | 16px | `MuiListItem` **8px 16px** | **32px** |

The only place in the perimeter where the container's padding and the rows'
already add up, before any migration. Two of the 61 `SectionBlock` usages pass
`disablePadding` to avoid it; the others accumulate. Correcting it **would not
be iso**, so it is arbitrated separately — see §5.7.

### 5.3 — **Intrinsic** child padding, NOT to be removed (**4 sites**)

| site | Paper padding | child padding | why it stays |
|---|---|---|---|
| **L5** `simulations/LessonsCategories.jsx:306` | 16px | chips **4px 8px** | a chip's inner padding, not a container's — removing it crushes the chip |
| **L7** `scenarios/LessonsCategories.jsx:189` | 16px | chips **4px 8px** | same |
| **L8** `simulations/Lessons.tsx:152` | 16px | `HeroStat` **4px 32px 4px 4px** | the 32px on the right is the `HeroStats` divider gutter, structural |
| **E4** `DetailHero` | 16px | `HeroStat` **4px** | out of the waves, see §5.8 |

### 5.4 — Nothing to do (**3 sites**)

**L2** (ApexCharts graph: inner margins are SVG, no DOM padding to remove),
**E1** `Section` and **E2** `InformationGrid` (their `Field` children carry no
padding; E2's spacing comes from grid `gap`).

### 5.5 — Strict iso: transfer nothing

`padding={0}` on the Paper. The `MuiListItem` gutters (8px 16px) **stay**.
Reason: **the dividers must keep touching the edges** — this is the "the
padding means something" case of the general rule.

> **General rule.** When the Paper carries the padding, the children's padding
> is REMOVED — **except when that padding means something**: a full-width
> divider, a structural gutter.

Applies to L1, L3, L4 and L6: `padding={0}`, child unchanged.

### 5.6 — Transfer to the Paper, removal AT THE CALL SITE

The padding moves onto the Paper; the **32px of `LessonsPlaceholder` are
removed at the call site**. The shared component is not modified, or its other
consumers go with it. Applies to L9 and L10.

### 5.7 — Cumulated 32px on `SectionBlock`: not corrected here

A density decision of its own, taken cold, outside this wave. Converting
`SectionBlock` therefore reproduces the existing total unchanged.

| state | Paper | row gutters | horizontal total | effect on dividers |
|---|---|---|---|---|
| **Current** — 59 of 61 usages | 16px | 16px | **32px** | pulled in by 32px |
| **Option A** — `disablePadding` | 0 | 16px | 16px | **edge to edge** |

### 5.8 — `DetailHero` leaves the Paper waves for good

It becomes its own component. Recorded here because the code points at this
decision: two losses (accent gradient, transparent background), and the
transparent background falls under the "semi-transparent containers are a later
wave" exclusion. Listed, never converted.

---

## 10. Method — a product inventory read by name misses what it does not name

Worth writing down, because it will happen again at the next bump, OpenCTI
included.

Renaming 17 tokens left **three dead product references**, and **two were
silent**:

| reference | file | signal |
|---|---|---|
| `FDS.colors.*['--color-feedback-info-secondary-transparency']` | `ThemeDark.ts`, `ThemeLight.ts` | TypeScript error — **loud** |
| `var(--color-filigran-brand-primary-transparency)` | `TopBarIconLink.tsx` | **silent** — a `var()` hanging inside a string |
| `'bg-filigran-ia-secondary-transparency'` | `AskArianeButton.tsx` | **silent** — a utility class absent from the shipped sheet |

Neither `tsc`, nor ESLint, nor the conformity gate, nor the build says anything
about the last two. The only signal is visual, on states no screenshot of that
wave covered.

**What the inventory must look for at the next bump.** Regenerating the bridge
is necessary and not sufficient. Grep the product source — not only the
`wiredFiles` — for **both forms**:

- `var(--<token>)` inside `.ts`/`.tsx` strings, `style` objects and CSS;
- **library utility classes written as literals** (`bg-…`, `text-…`, `border-…`).

Then cross every occurrence against the tokens and utilities actually present
in the **installed** `dist/index.css` — the shipped sheet, not the source
`theme.css`. Both forms live in ordinary component files, outside the
`wiredFiles` by construction: that is exactly why they were missed. Also
recorded as LIBRARY-FEEDBACK #33.

---

## 13. The three titled wrappers adopt the library header

`EntityDetailCommon.tsx` holds three titled containers — `Section`,
`InformationGrid`, `SectionBlock` — used **106 times across 33 files**. Their
surface was already the library `Paper`; this passes the **title** into the
library's `title` slot instead of drawing it above with `SECTION_LABEL_SX`.

### 13.1 What the title becomes — measured in a real browser

|                 | before (product)             | after (library)     |
| --------------- | ---------------------------- | ------------------- |
| font            | Geologica                    | IBM Plex Sans       |
| size            | 11px                         | 12px                |
| weight          | 600                          | 400                 |
| case            | UPPERCASE (`textTransform`)  | as written          |
| letter-spacing  | 1.32px                       | 0.09px              |
| row             | 11px + `marginBottom: 12px`  | 24px, fixed height  |
| colour (dark)   | `rgba(255,255,255,0.7)`      | `rgb(175,176,182)`  |
| colour (light)  | `rgba(0,0,0,0.6)`            | `rgb(73,74,80)`     |

The number of library surfaces rendered is **identical on both sides** across
the four screens captured (5 / 11 / 6 / 8): none gained, none lost.

### 13.2 The flex trap — why the container becomes a grid

`style` reaches the **surface**, never the wrapper the library adds once
`title` is set. A `flex: 1` on the surface can therefore no longer stretch the
panel: the wrapper is what the product container must stretch. Measured on a
panel beside a taller one: **58px against an expected 130px**. The product
container becomes a one-row `1fr` grid — measured afterwards **268 / 268**.
Fixed product-side, with nothing asked of the library.

### 13.3 The column fix — `minmax(0, 1fr)`, not `1fr`

An **implicit** grid column is `auto`: it sizes to its content. With only the
row declared, the library wrapper measured **354px inside a 340px track** — it
overflowed by 15px and the title did not truncate, it left the panel. With
`gridTemplateColumns: 'minmax(0, 1fr)'`: wrapper **338px**, overflow **−1px**,
title ellipsized by 16px. Applied to all **three** wrappers.

### 13.4 Multilingual truncation — a KNOWN AND ACCEPTED gap

14 distinct titles reach these wrappers. Across every locale, **one** exceeds
the narrowest track `DetailSections` can produce (340px): the Spanish
*"Distribución de la puntuación total esperada por tipo de inyección"*, 66
characters, **16px cut**.

Decision: **the ellipsis is accepted**. One title in fourteen, at the narrowest
track, is no reason to shorten a translation or to compensate product-side on a
library component. The full text stays announced to screen readers. The library
does **not** expose the full title on hover when it truncates: that is
**feedback #37**.

### 13.7 Render guard

`src/__tests__/components/common/detail/EntityDetailCommonHeader.test.tsx`,
11 tests. The two checks `check-fds-conformity.mjs` runs look at imports and at
hardcoded paddings: **neither can see a header**. The guard asserts
**structure** — the title is in the header and not in the surface, no case or
font re-imposed, `flex` on the surface, grid container — never computed style,
because jsdom does not apply the library stylesheet. The pixel values are
measured in a real browser and live here, in §13.1.

---

## 14. Method — a surface qualifies on its ANCESTOR STACK

**Read this before any surface census, in any Filigran product. This section is
self-contained: it assumes nothing from the rest of the document.**

### The problem it addresses

Migrating a product's surfaces to the library `Paper` first means knowing
**which ones are in scope**. The perimeter is **first-level containers**: the
blocks laid on the page carrying the elevated background, of the kind a
dashboard tile is. Excluded: cards, tooltips, alerts, dialogs, popovers, menus,
autocompletes, accordions, and any surface **internal to another component**.

A census by text search gives a wrong answer, and wrong in the dangerous
direction: it lets in surfaces that have no business being there. On OpenAEV,
777 raw hits gave 395 containers then 130 in the perimeter — and **two surfaces
recommended for conversion should never have been listed**. Both times it was a
FLOATING surface.

### The rule

> **Every surface qualifies on its complete JSX ancestor stack, never on a
> window of lines. A surface carried by a floating component — tooltip, drawer,
> popover, popper, menu, dialog, autocomplete — NEVER enters the first-level
> container perimeter, whatever it looks like.**

### How to apply it

1. **Collect wide.** Search for the UI library's surface components (`Paper`,
   `Card`, `Accordion`, `Dialog`, `Popover`, `Menu`, `Drawer`, `Tooltip`,
   `Alert`…), **plus** the hand-painted surfaces: a `Box`/`div` whose style sets
   the elevated background colour. Those are invisible to any search by
   component name. On OpenAEV there were **48** of them, more than a third of
   the final perimeter.
2. **Compute each hit's ancestor stack over the whole file**, pushing opening
   tags and popping closing ones. A window of lines is not enough: a `Drawer`
   opened sixty lines above is out of reach of a forty-line window.
3. **Exclude every hit whose stack contains an open floating component.**
   Mechanical, no exceptions.
4. **Re-read by hand everything the tool classes as "convert".** On OpenAEV both
   catches came from that re-reading, not from a better-tuned detector.

### The four traps, each paid for by a real mistake

- **Do not look for a prop name, look for an ancestor.** A component paints its
  surface through `slotProps`, whose key is the name of the PART being styled:
  `slotProps={{ tooltip: { sx: { backgroundColor: … } } }}`. The word `paper`
  appears nowhere. Searching `PaperProps`/`slotProps.paper` misses it; looking
  for the `Tooltip` ancestor finds it.
- **A window of lines lies.** Moving from a 40-line window to the full stack
  took OpenAEV from 2 to 6 floating surfaces detected.
- **A hand-painted "elevated" background says nothing about the role.** The same
  `background.paper` serves page panels, chips, graph nodes and tooltip fills.
- **A role inferred from a file name is a hypothesis.** `…Panel.tsx` may be a
  page container; `…Card.tsx` may not be a card. The name orients the reading,
  it does not replace it.

### Corollary for review

When a human arbitration is needed on a surface's role, **provide the ancestor
stack rather than a screenshot**. The stack is verifiable evidence; a thumbnail
shows an appearance, and a tooltip styled as a card looks exactly like a
first-level container.

### Tool

The ancestor detector used for OpenAEV depends only on JSX structure, not on the
product: it pushes the file's opening tags and reports the hits whose stack
contains a floating component. It is reused as-is on another product by changing
the source path and the list of that UI library's floating components.

## 15. Class A — what the census got wrong about padding, and the twelve it blocks

Converting class A surfaced two defects in the census tooling and one hard
limit in the library. Recorded here because the arbitration was made on the
faulty numbers.

### 15.1 The two extractor defects

**The padding often lives in a `makeStyles` block**, reached through
`classes={{ root }}`, not in `sx`. The extractor read `sx` and `style` only, so
**18 sites** were recorded as carrying no padding when they carry one.

**And a bare number in `sx` is not a pixel.** In MUI's `sx`, `padding: 2` means
`theme.spacing(2)` = **16px**. Reading it as CSS made the first pass declare
fourteen perfectly on-scale sites off-scale. The interpretation now depends
on the source: spacing units inside `sx`, raw pixels inside `makeStyles` or
`style`.

After both fixes: **36 of the 51 convert as they stand, 15 do not.**

### 15.2 The three off-scale values — 15 → 16 and 20 → 16

`AtomicTesting.tsx:368` and `:422` carry 15px, `Policies.tsx:59` carries 20px.

**Both go to 16, the nearest value DOWNWARD**, and that is a deliberate choice
rather than a rounding rule: these three screens are dense — a remediation list
and a policy grid — and taking 20 up to 24 would add height to panels that
already scroll. Elsewhere in this migration a 12 went **up** to 16 and a 48 went
**down** to 32; the rule is not "always down", it is "whichever neighbour serves
the screen". **This is not an inconsistency to be tidied away later.**

### 15.3 The twelve asymmetric ones stay out of the wave

| padding | sites |
| --- | --- |
| `0 20px 0 0` | 9 — `ExerciseDistribution.tsx` charts |
| `20px 20px 0 20px` | 2 — `Inject.tsx:191`, `Inject.tsx:215` |
| `10px 15px 20px 15px` | 1 — `LessonsPlayer.jsx:160` |

`Paper`'s `padding` takes a single value for all four sides, so none of these
can be expressed. The workaround — `padding={0}` plus an inner wrapper — renders
identically and adds a technical level inside twelve files to satisfy a prop
signature. That is the debt this migration removes, so **they stay on MUI** until
the library can express a per-side padding. Upstream request: **feedback #38**.

### 15.4 Method — every spacing prop of an `sx` is in spacing units

A bare number in MUI's `sx` is **not a pixel**: it is multiplied by the theme's
spacing unit, 8px here. Moving an `sx` to the `style` prop — which the library
`Paper` accepts, and `sx` is not — switches to raw CSS, where the same number
means pixels. Every value moved across that boundary must be converted, or it
silently shrinks by a factor of eight.

This is not limited to `padding`. It applies to **every spacing prop**:

| in `sx` | in `style` |
| --- | --- |
| `padding` / `p` / `px` / `py` / `pt` `pr` `pb` `pl` | `padding…` in px |
| `margin` / `m` / `mx` / `my` / `mt` `mr` `mb` `ml` | `margin…` in px |
| `gap`, `rowGap`, `columnGap` | same, in px |
| `top` / `right` / `bottom` / `left` when given a bare number | same, in px |

`padding: 2` → 16px. `gap: 1.5` → 12px. `mt: 3` → 24px.

**Nothing catches this.** TypeScript accepts both forms, ESLint has no rule for
it, and a unit test on structure will not see a gutter go from 12px to 1.5px.
Only a measurement in a real browser, or reading each value at conversion time,
will.

**The rule for every remaining conversion:** before moving an `sx` to `style`,
list its properties, convert each spacing value to pixels explicitly, and check
that no property is dropped. Class G — the hand-painted `Box` surfaces — is
where this matters most: the whole `sx` moves, so every spacing value in it
crosses the boundary at once.


## 16. Two more method rules, from the hand-painted surfaces

These belong next to §14 and §15: they are what a census does not tell you, and
what the OpenCTI pilot will meet the moment it touches a surface painted by hand
rather than drawn by a `Paper`.

### 16.1 Converting a hand-painted background changes the TAG, not the style

A `Paper` → `Paper` conversion moves props between two components that both draw
a surface. A hand-painted surface is different: the colour lives in the `sx` of a
`Box`, a `div` or a `header`, and **removing that `sx` removes the surface**. The
element must become the library `Paper`.

Recorded because the mistake was made here: the `sx` of a
`<Box component="section">` was replaced with `padding={0} style={…}` and the
`Box` was left in place. The surface simply vanished — **no type error, no lint
error, no failing test**. Nothing catches it but reading the diff or looking at
the screen.

> **Rule.** For a hand-painted surface, the conversion is `<Box sx={{…}}>` →
> `<Paper padding={N} style={{…}}>` — tag included. Check the closing tag too.

The practical consequence is that these conversions cannot be scripted the way a
`Paper` → `Paper` swap can: each one is a structural edit, and each one needs its
own before/after read.

### 16.2 A conditionally painted surface has two faces

`EmptyPlaceholder` paints its border and background inside a conditional spread:

```
...(bordered ? { border, backgroundColor } : {})
```

The component renders **with or without a surface** depending on a prop. A
library `Paper` always has one, so converting it would give the bare variant a
border and a background it never had — a change in every screen that uses the
bare form.

> **Rule.** Before converting, check whether the surface is unconditional. A
> painted background behind a ternary, a spread or an `&&` means the component
> has two faces, and the conversion must be arbitrated, not assumed.

The same shape hides a second question: `SecurityDomainsModule` varies only the
**opacity** by state (`alpha(paper, hasData ? 1 : 0.5)`). That one is convertible
— its content carries the state independently, in four other places — but only
because it was checked. The test is always the same: **does anything but the
surface tell the user which state this is?**


## 17. A decided line is decided

**Cost, measured on this wave: ten approved surfaces never converted on one
side, one excluded surface converted on the other.**

### What happened

The arbitration was produced class by class, then refined **line by line**: a
role was proposed for each surface, and the reviewer confirmed or overturned it.
Several were overturned — a card that was not a card, a panel that was.

The conversion list was then built as `class == A AND role == first-level
container`. That single `AND` re-applied the inference **after** it had been
overruled. Every surface whose role had been corrected fell out of the list:

- `PlatformInfoPanel.tsx:39` and `ToolsPanel.tsx:13` — approved, not converted.
- `LessonsPreview.jsx:161` — approved, not converted.
- The whole of class C, seven surfaces — approved, never treated as a lot at
  all, because it was not class A.

Symmetrically, `Logs.jsx:211` had been held out of the waves under the
"clickable card" motif, recorded in LIBRARY-FEEDBACK #35. It reappeared in a
later census under a fresh identifier, its role read as a plain container, and
it was converted. The technical argument was sound — the `Paper` contains the
`ButtonBase` rather than being it — but the line had already been decided, and
re-opening it was not the converter's call.

### The rule

> **A decided line is decided.** Build the work list **from the decisions**,
> never from the categories that produced them. Categories exist to present a
> choice; once the choice is made they carry no authority, and intersecting them
> with a decision means re-arbitrating after the arbiter.
>
> **Recognise an excluded surface when it returns under another number.** Cross
> the work list against every earlier exclusion **on `file:line`**, never on the
> identifier, and redo that crossing before each lot.

### Why the identifier is not enough

Identifiers were regenerated by a sort whose key included the recommendation, so
any reclassification shifted them: one surface moved from `S087` to `S085`
between two runs. They are now frozen on `file:line`. But even frozen, an
identifier says nothing about *which screen* a reviewer had in mind: the
exclusion of `DetailHero` was given by name, months of context before it
resurfaced as `S105` in an approval list. **Names travel; numbers do not.**


## 18. Measurement traps, for whoever verifies a library bump

Three ways a measurement lied during this migration. Each cost a wrong
conclusion that had to be retracted.

### 18.1 A `Chip` is measured on its inner span, not on its root

All sixteen chip variants reported `font-size: 16px` — the value inherited from
the page, read off the chip's root element. The text lives in an inner `<span>`,
where the real value is 14px, and where the bumped variant drops to 12px.

> **Rule.** Walk to the node that actually carries the text — the first text
> node's `parentElement` — and read the computed style there. A uniform value
> across every variant of a component is the signature of reading a container
> that inherits, not the component.

### 18.2 `.focus()` does not trigger `:focus-visible`

Focusing an element programmatically reports no focus ring, because
`:focus-visible` only matches keyboard-driven focus. A first pass concluded the
converted accordion had lost its focus indicator. Tabbing to it for real showed
the indicator intact, and `Mui-focusVisible` applied.

> **Rule.** Any focus-appearance check is done by pressing Tab until the element
> is reached, never by calling `.focus()`.

And once there: act on the element that **actually has focus**
(`document.activeElement`), not the one queried beforehand. Reading
`aria-expanded` off a different accordion produced a second false negative.

### 18.3 Compare colours normalised, not as strings

Six "discrepancies" between a measured background and its token were the same
colour written two ways: the DOM returns `#ffffff`, the token is declared
`#fff`. Expand three-digit hex and lowercase both sides before comparing.

### The general shape

All three share one form: **the measurement was taken on the wrong node, in the
wrong state, or in the wrong representation** — and each time the wrong answer
looked plausible enough to report. A measurement that contradicts an expectation
deserves a second reading of the method before it is announced as a finding.


## 19. One file, two surfaces: the import breaks and only the typechecker sees it

**Three occurrences in a single day**, on `TenantParameters.tsx`,
`ReportingForm.tsx` and `GettingStartedHero.tsx`.

### The shape

A file holds two container surfaces: one is converted to the library `Paper`,
the other stays on MUI — because it is a real link, a click target, or a surface
the arbitration held back. Importing the library component under its own name
then collides with the MUI one:

```
import { Paper } from '@filigran/design-system';
import { Box, Paper, Typography } from '@mui/material';   // Duplicate identifier
```

**ESLint reports nothing.** No rule covers a duplicate import binding here, and
the file lints clean. `tsc` is the only thing that catches it —
`TS2300: Duplicate identifier 'Paper'` — and a second error follows on the
untouched site, because its `variant="outlined"` no longer type-checks against
the library's props.

### The remedy

Alias the library import and rename only the converted usage:

```
import { Paper as FdsPaper } from '@filigran/design-system';
import { Box, Paper, Typography } from '@mui/material';
```

`<FdsPaper>` for what was converted, `<Paper>` for what stays. **Rename the
closing tag too** — the opening and closing tags are edited separately, and a
`<FdsPaper>` closed by `</Paper>` is a different error again.

### The operating rules

> **Convert a file whole, never site by site.** A partial pass leaves the MUI
> import in place for the remaining sites and produces the collision.
>
> **Run the typechecker after each file, not at the end of the lot.** Lint is
> not a substitute: it passed on all three.
>
> **Before converting, count the container surfaces in the file** and check
> whether any of them is out of the wave. If so, plan the alias from the start
> instead of discovering it from a type error.


## 20. Two rules for pruning this document

Learned while cutting 888 lines out of it.

### 20.1 Numbers are addresses, not a table of contents

Sections keep their number for ever, even when the ones before them are
removed. The gaps left behind cost nothing; renumbering would break every
`§13.2` written in a code comment, in a commit message already pushed, or in a
feedback entry. **Never renumber. Fold a surviving rule into another section and
give it a fresh sub-number there.**

### 20.2 A section the code cites is never round memory

The first pass of this cut classified §6 as "conversion arbitrations for a bump
that happened" and marked it for deletion. Its heading said exactly that. But
its three sub-sections carried standing rules, and **seven code comments pointed
at them** — deleting the section would have left those comments addressing
nothing.

> **Before cutting a section, grep the source tree for its number.** A citation
> from the code is proof the section still serves someone: it is the reader of
> that comment, tomorrow, following the pointer.

The same pass found §4 in the same position, for one line that records why
`DetailHero` stays out of the waves. Both were folded into §5 instead of being
dropped, and the seven comments were re-addressed in the same commit — a cut
that breaks what it claims to preserve is not a cut, it is a regression.
