# Library feedback — OpenAEV → filigran-design-system

Gaps and friction found while consuming `@filigran/design-system` in this
product. Nothing here is fixed product-side: per the migration contract a
missing or awkward library capability is reported, never forked or worked
around with a local approximation.

Each entry states what the product needed, what the library offers today, and
what the product had to do instead. Carry these upstream (the library's
`process/AI-BACKLOG.md` / `ROADMAP.json`) — this file is the product-side
record, not the upstream tracker.

Raised during: the navigation pilot (replacing the legacy left menu with
`Navbar`), library pin `ad108751b6611c0a4f613bd0f1dcc15358821c81`.

**Resolution status at the current pin.** Entries 1, 4, 6, 7 and 9 have been
fixed upstream and verified here by re-measurement after a pin bump; each says
so in its own **Status** line, with the library change that closed it. Entry 3
is **resolved** too, by the `Menu` component (#74). The entries left open are 2,
3b, 3c, 5, 8, 11 and 13; 10 is a recorded decision, not a gap. Entry 14 was
blocking and is now **resolved** (#79), with a consumer-install proof running on
every library pull request.

**Status of the Paper-pilot entries (26 → 34), at pin `a22b188`.**

| entry | state |
|---|---|
| 26 padding prop | **closed** — the 0/8/16/24/32 scale ships and all five classes exist in `dist` |
| 27 border | **closed as ARBITRATED** — the per-layer token exists; the 15% opacity is a decision, and the converted panels end up with a weaker border than before (see the entry) |
| 28 host theming | **closed** — `--bg-elevation-default-layer-N` repaints, the alias does nothing |
| 29 DetailHero | **closed as a Paper question** — it becomes its own component and leaves the Paper waves permanently |
| 30 title/action | **closed** — both are real props; the product now adopts them |
| 31 guard vs mixed file | **open** |
| 32 off-scale padding → 0px | **open** |
| 33 incomplete rename inventory | **open** (method finding, not a library defect) |
| 34 host `outline` reset | **open** |
| 35 clickable card must be a real link | **open** (raised for the future `Card`) |
| 36 header renders on prop presence, not value | **open** |
| 37 truncated title unreadable in full | **open** |

---

## 1. `NavbarItem` has no link destination (`href` / `to`)

**Needed.** Every row of a product navigation is a link. Users middle-click and
Ctrl/Cmd-click them to open sections in a new tab; that only works if the row
is a real `<a>` with an `href`.

**Today.** `NavbarSubmenu` already accepts `href` / `to`, but `NavbarItem` does
not. The only way to get a real anchor is `asChild`.

**Consequence.** `asChild` documents that `icon`, `showIcon` and `chevron` are
ignored (Radix's `Slot` cannot inject wrappers inside an arbitrary child), so
the product must hand-compose the row's internals — and therefore duplicate the
library's own internal Tailwind classes (`inline-flex shrink-0`,
`text-default-secondary`, the collapsed `text-default-primary mr-0.5`,
`flex-1 truncate text-left`, the collapsed `sr-only`). See
`openaev-front/src/components/common/menu/navbar/NavbarRowContent.tsx`: that
whole file exists only because of this gap, and it will silently drift the day
the library restyles its rows.

**Ask.** Give `NavbarItem` the same `href` / `to` props `NavbarSubmenu` already
has, so a link row stays a fully library-owned row.

---

## 2. `NavbarItem` has no trailing slot

**Needed.** The tenant switcher row carries, after its label, an "EE" badge and
a chevron affordance. Regular rows carry neither.

**Today.** There is no trailing/adornment slot; extra content can only be
appended by going through `asChild` and appending siblings by hand, with the
alignment classes re-declared product-side.

**Ask.** A `trailing` (or `endAdornment`) slot on `NavbarItem`.

**Still open, narrower than before.** Since the switcher's granted state is now
a real `NavbarSubmenu` (see entry 3), the hand-composed trailing content only
remains on the Enterprise-Edition-gated row — one row instead of all of them,
but the missing slot is the same.

---

## 3. No generic action-menu primitive

> **Correction, later in the pilot.** This entry originally cited the tenant
> switcher as its motivating case. That was wrong, and the correction matters
> more than the entry: switching tenant is not an action, it is a **URL
> navigation** (`useTenant.navigateToTenant` assigns `window.location.href`).
> Every tenant has an address, so the switcher is now a real `NavbarSubmenu`
> whose rows are real `<a href>` — the library's navigation semantics were
> truthful there all along, and adopting them additionally bought
> ⌘/Ctrl-click "open this tenant in a new tab", which the MUI popover could
> not do. The lesson for the next pilot: before declaring "the library has no
> primitive for this", check what the product's own handler actually *does*.
> A handler that assigns `location.href` is a link wearing a disguise.
>
> The ask below still stands for genuine action menus — it is simply not
> blocking anything in OpenAEV's navigation today.

**Needed.** A menu whose items run a function and navigate nowhere (bulk
actions, "export as…", row context menus).

**Today.** The library's menus are navigation menus: `NavbarSubmenu` renders
navigation items, `ProductSwitcher`'s dropdown renders links. There is no
"menu of actions" primitive (the `DropdownMenu` Radix usage is internal, not
exported).

**Ask.** Export a generic action-menu (trigger + items + separators, items take
`onSelect`, no destination required).

**Status — RESOLVED** at pin `9cd42715b218a65f945f01196fa1fc40d7e73de9` by
[filigran-design-system#74](https://github.com/XTM-Foundation/filigran-design-system/pull/74),
which adds `Menu`, `MenuTrigger`, `MenuContent`, `MenuItem`, `MenuGroup`,
`MenuLabel` and `MenuSeparator`. Every point below was addressed:

| Product need | How `Menu` answers it |
|---|---|
| A composable trigger | `MenuTrigger asChild` around any library component — here a `NavbarItem asChild` rail row, so the trigger *is* a navigation row and needs no imitation. |
| Rows that are real links | `MenuItem asChild` wraps an `<a href>` and composes the row around it, so ⌘/Ctrl-click still opens a tenant in a new tab. |
| An indication of the current item | `MenuItem selected` renders a `check` in the trailing slot **and** sets `aria-current="true"`, scoped to the panel — it never leaks onto the trigger, which was the exact reason the switcher previously refused `aria-current`. |
| A controlled open state | `open` / `onOpenChange` on `Menu` (unused here: the Enterprise-Edition gate is handled by not mounting the menu at all, which is stronger). |
| Test hooks | `MenuItem` spreads its remaining props, so `data-testid` reaches the DOM — unlike the composite components of feedback entry 11. |

**Verified in the product.** The panel measured identical to the documentation
site on the first try, both themes, with no product CSS: panel
`rgb(19,33,62)` dark / `rgb(228,229,231)` light, radius 4px, `min-width 200px`,
`max-width 300px`; rows `min-height 32px`, padding `0 16px`, gap 8px,
`12px/18px IBM Plex Sans`, letter-spacing `0.09px`.

**What it let the product delete.** The tenant switcher had been rebuilt on
`NavbarSubmenu` — a navigation primitive doing a selector's job — precisely
because this primitive was missing. That structural compensation is gone.

**The analysis that produced the ask**, from the pilot's attempt to reuse
`ProductSwitcher` as a tenant menu (read from the built bundle at pin
`d7ea4f2`). All four points are why reuse was impossible; they are kept here
because they remain **open questions for `ProductSwitcher` itself**, which is
expected to consume `Menu` in turn:

| Finding | Detail | What `Menu` should do |
|---|---|---|
| Items have **no text mode** | `ProductSwitcherOption.logo` is the only visible content; it renders inside a fixed `span.h-7.w-[100px].shrink-0.overflow-hidden` marked `aria-hidden="true"`, and `label` is emitted **only** in an `sr-only` span. A label-only option renders as an empty, invisible row. | Item content is a label (optionally with a leading icon and a trailing slot); no fixed width, with truncation. |
| **No current-item indication** | Nothing in `ProductSwitcherOption` (`{ id, label, logo, tooltip?, href?, to? }`) expresses "this is the one you are on", and nothing in the render marks it. | A `selected` / `checked` state on items, visually and via `aria-checked`. |
| **Trigger is not composable** | The trigger is hard-wired: a logo slot plus an `IconButton` with `Icon name="chevron-down"`. `...props` are spread onto that icon button. A product row (icon + label + badge + `chevrons-up-down`) cannot be the trigger. | `MenuTrigger asChild`, so any product element can open it. |
| **No controlled open state** | The Radix root is uncontrolled — no `open` / `onOpenChange`. A permission gate that must intercept the opening (show an upsell dialog instead) has nothing to hook onto. | Support controlled `open` / `onOpenChange`, and honour `disabled` on the trigger. |

**Also worth keeping.** What `ProductSwitcher` gets *right* and `Menu` should
keep: an item with `to` renders a plain `<a href>` inside
`DropdownMenuPrimitive.Item asChild`, so ⌘/Ctrl-click opens a new tab. Any menu
whose items have addresses must stay real anchors.

---

## 3b. `NavbarSubmenu` propagates a child's `aria-current` to its trigger

**Needed.** The tenant submenu wants to mark which tenant is the current one.

**Today.** `hasActiveDescendant` copies a child's `aria-current="page"` onto the
trigger row. That is right for a page group (the group containing the open page
should look active) but wrong for a *selector*: the current tenant is always in
the list, so the switcher row would be permanently lit even when the user is
nowhere near it.

**Product-side decision (at the time).** The current tenant was marked with a
`check` icon in its own leading-icon slot and named through `aria-label`; no
`aria-current` was used. This is a deliberate arbitration, not a defect report — the library's
behaviour is correct for the case it was designed for.

**Ask (low priority).** Either a way to opt out of the propagation, or a
documented "selector" variant. Worth a note in the RFC either way, since the
next consumer will hit the same fork.

**No longer blocking this product** since the switcher moved to `Menu` at pin
`9cd4271`: `MenuItem selected` sets `aria-current="true"` on the row itself,
inside the panel, and nothing propagates to the trigger. The observation stays
on file for the next consumer who uses `NavbarSubmenu` as a selector — and the
contrast between the two components is the useful part: `Menu` scopes the state
to the panel, `NavbarSubmenu` lifts it to the rail. Both are right for their own
job, which is exactly why the choice must be documented.

---

## 3c. A gated submenu cannot intercept its own opening safely

**Needed.** Without a validated Enterprise Edition licence, activating the
tenant switcher must open an upsell dialog instead of the tenant list.

**Today.** The obvious wiring — controlled `open` plus `onOpenChange` — is a
trap: while collapsed, the library opens its flyout **on hover**
(`openOnHover` → `setOpen(true)` → `onOpenChange`), so the upsell dialog would
fire on a simple mouse-over.

**Product-side decision.** The component branches instead: no licence renders a
plain `NavbarItem` button (with the EE chip) that opens the dialog; a valid
licence renders the real menu. Cleaner than fighting the primitive,
and it keeps the chip out of the submenu trigger.

**Ask (low priority).** Document that `onOpenChange` also fires on hover intent
in collapsed mode — it is discoverable only by reading the source.

**Still the shape used at pin `9cd4271`**, now with `Menu` instead of
`NavbarSubmenu`: the ungated path mounts no menu at all. That is stronger than
intercepting an open event — there is no panel to reach by any route, hover,
keyboard or otherwise — and it is the pattern to reuse for any gated menu.

---

## 4. `ProductSwitcher` trigger has no home destination

**Needed.** In this product (and in OpenCTI) clicking the platform logo goes to
the home page; the chevron next to it opens the product switcher.

**Today.** Expanded, the logo is a non-interactive `aria-hidden` span and only
the chevron button is interactive. Collapsed, the logo itself is the dropdown
trigger. There is no way to make the logo a link.

**Status. RESOLVED** upstream by #69, adopted here at pin
`3442003aa644923b2c479e385244567d5dffd6d3`. `logoHref` / `logoTo` make the logo
a real link, `logoLabel` names it, and `logoCollapsed` gives the collapsed rail
its own 28px square slot. `LeftBarHeader.tsx` now passes `theme.logo` and
`theme.logo_collapsed` into the two slots and the product-side compensation is
deleted — see the pin-bump section of the implementation playbook, now
`process/PRODUCT-IMPLEMENTATION-PLAYBOOK.md` in the design-system repository.

**One residual rough edge, worth a doc line rather than a fix.** Neither slot
sizes its child: both wrap it in a `shrink-0` span and clip with
`overflow-hidden`. A consumer passing a plain `<img>` with a percentage height
gets `auto` — the raw asset at natural size, clipped to a corner. Measured here
before sizing the asset explicitly: the 350×346 collapsed mark rendered at
350×346 inside a 28×28 slot, i.e. a crop of its middle. Stating the expected
asset size next to each slot (126×28 expanded, 28×28 collapsed) would save the
next consumer the same round trip.

---

## 5. `ProductSwitcher` internal destinations are plain anchors, not router links

**Needed.** An internal destination should navigate inside the single-page app,
without a full document reload, and should respect the router's basename.

**Today.** `ProductSwitcherOptionItem` renders `to` as `<a href={option.to}>`,
and #69's `logoTo` does the same by design ("an identical plain same-tab
anchor"). For this product's "Connect your product" option that turns a
client-side route change into a full page reload — functionally correct,
noticeably slower, and it drops in-memory app state.

The basename consequence is sharper and easy to miss. OpenAEV mounts its router
under a **tenant-prefixed basename** (`/<tenant-uuid>`), so every product `<Link
to="/admin">` resolves to `/<tenant-uuid>/admin`. A plain anchor does not: it
would send the user to `/admin`, outside the tenant. `LeftBarHeader.tsx`
therefore has to prefix the basename by hand
(`logoHref={`${computeTenantBasename()}/admin`}`), which is exactly the kind of
router knowledge a component should not force onto its consumer.

**Ask.** Either render internal destinations through an injectable link
component (a router adapter on the provider), or expose the row and the logo
with `asChild` so the consumer can supply its own `<Link>`. Until then, please
say in the prop documentation that these are plain anchors **and that a router
basename is not applied** — the current wording ("no router integration")
does not make the basename trap obvious.

---

## 6. The stylesheet ships no CSS reset for native controls

**Needed.** A library control built on a real `<button>` should look like the
library designed it, in any host.

**Today.** `dist/index.css` contains `@layer properties` plus the generated
utilities — no Tailwind preflight. MUI's `CssBaseline` does not reset `<button>`
either. In this host every library button (the navigation's own collapse
toggle, the `ProductSwitcher` trigger, the recomposed tenant switcher row)
rendered with the user-agent `buttonface` background — measured
`rgb(239, 239, 239)` — and a 3D border.

**Consequence.** The product ships a host-side reset,
`openaev-front/src/static/css/design-system-host.css`, marked temporary with
its own removal instructions. Every consumer will hit this and, worse, will hit
it *silently* — nothing in the README mentions it.

**The `<button>` background is not the only symptom.** A computed-style diff
against this library's own documentation site (which imports full Tailwind, so
it *has* the preflight) found a second one: `NavbarSeparator` is an `<hr>`, and
the user agent styles `<hr>` with `border: 1px inset` on all four sides. The
preflight resets that; `dist/index.css` does not. Measured on the same
component, same classes:

| | documentation site | this product, before the fix |
|---|---|---|
| separator height | 1px | 2px |
| separator border widths | `1px 0px 0px 0px` | `1px 1px 1px 1px` |

The general lesson: **any UA-styled element the library renders is a latent
defect in a host without the preflight.** A single reset for the elements the
library actually emits (`button`, `hr`, and any future `fieldset`, `input`,
`table`) would close the whole category rather than one symptom at a time.

**Status — RESOLVED** at pin `d7ea4f2` by *"make every component
self-defensive against browser UA defaults"* (library #70). The library did not
add a preflight; each component now restates locally what a reset would have
given it (`bg-transparent border-0 p-0 m-0 box-border text-inherit`), so hosts
that already ship a reset are unaffected. Verified here by deleting both host
blocks and re-measuring in Chromium:

| | before removal | after removal, no host reset |
|---|---|---|
| collapse toggle `background-color` | `rgba(0, 0, 0, 0)` (host reset) | `rgba(0, 0, 0, 0)` |
| separator border widths | `1px 0px 0px 0px` (host reset) | `1px 0px 0px` |
| separator height | 1px | 1px |

Both host blocks were deleted in the same pin bump. Nothing in
`design-system-host.css` compensates for UA defaults any more.

**Ask (still open, documentation only).** The README's consumer section still
does not mention that `dist/index.css` ships no preflight. A host with its own
reset is fine either way, but a host without one should know *why* the library
is self-defensive rather than global.

---

## 7. `ProductSwitcher`'s logo slot keeps its expanded width when collapsed

**Needed.** A collapsed 48px rail should show the collapsed logo centred on the
rail.

**Today.** The trigger's logo span is `h-7 w-[126px]` in *both* states. On the
collapsed rail the library centres that 126px box inside 48px, so it starts at
`x = -39` and the box hangs off the left edge of the viewport. A naturally-sized
collapsed emblem (28px, left-aligned inside the span) lands at `x = -39…-11` and
is **completely invisible**.

**Consequence.** The product had to make its collapsed logo fill the slot
(`width: 100%; object-fit: contain; object-position: center`) purely to
compensate for the slot's geometry. Any consumer passing a plain `<img>` got a
header that silently disappeared when collapsed.

**Status. RESOLVED** upstream by #69, adopted here at pin
`3442003aa644923b2c479e385244567d5dffd6d3`. The collapsed rail now has its own
`h-7 w-7` (28px square) slot, centred, and the compensation is deleted from
`openaev-front/src/admin/components/nav/LeftBarHeader.tsx`. Re-measured after
removal: collapsed logo 28×28 at `x = 10` inside the 48px rail — centred and
fully visible, against `x = -39` and invisible before. See entry 4 for the one
residual rough edge (neither slot sizes its child).

---

## 8. Icon sizing expectations are undocumented

**Needed.** Knowing what glyph size the navigation rows are designed around.

**Today.** The library's own rows use 16px glyphs, but nothing in the consumer
documentation says so. A host feeding its existing icon set (MUI's, which
defaults to 24px) into `icon` gets visibly oversized rows and has to discover
the intended size by reading the library's source.

**Ask.** State the expected icon size per slot in the component documentation.

---

## 9. `Navbar`'s scrollable list compresses its rows instead of scrolling

**Needed.** A navigation row keeps its designed height whatever the length of
the menu; a list that no longer fits scrolls, which is what `overflow-y-auto`
asks for.

**Today.** `Navbar.tsx` renders its children into
`<div className="flex flex-1 flex-col gap-1 overflow-y-auto py-2">`. The header
(`h-17 shrink-0`) and the footer (`shrink-0`) are protected from flex
compression; the children of that scroll list are not. As soon as the menu is
taller than the rail, every row is flex-shrunk instead of the list scrolling.

Measured with a real browser, reading computed styles — same components, same
class attributes on both sides (`h-9` = 36px is the designed height):

| Case | Entries | Viewport height | List overflows? | Measured row height |
|---|---|---|---|---|
| Documentation site | 4 | 1000px | no | **36px** (correct) |
| This product | 17 | 1600px | no | **36px** (correct) |
| This product | 16 | 1000px | marginally | **35.875px** |
| This product | 17 | 1000px | yes | **32px** |

The A/B is unambiguous: the only variable is whether the list overflows its
rail. Nothing else differs — not the markup, not the classes, not the host CSS
(removing the compensation and re-measuring reproduces it exactly, adding it
back restores 36px *and* makes the list scroll as `overflow-y-auto` intends).

It degrades continuously: the shorter the viewport, the shorter every row. It
is invisible on the documentation site precisely because its own menu is short,
so no library test or visual review would catch it.

**How to reproduce.** Render `Navbar` with enough entries to overflow the rail
at a 1000px-tall viewport, and read the computed `height` of any `NavbarItem`.

**Consequence.** The product ships a scoped compensation —
`.app-navbar .overflow-y-auto > * { flex-shrink: 0 }` in
`design-system-host.css` — which restores 36px rows and lets the list scroll.

**Ask.** Mark the scroll list's children non-shrinking in `Navbar` itself. This
one is worth prioritising: it silently degrades every consumer with a real menu,
and the smaller the screen the worse it gets.

**Status — RESOLVED** at pin `d7ea4f2` by *"tighten vertical rhythm of the rail
to fit more entries"* (library #71), which carries the `shrink-0` fix. Verified
by deleting the compensation and re-measuring the same worst case: 17 entries at
a 1000px viewport now render **36px** rows with the list scrolling, against 32px
before. The compensation was deleted in the same pin bump.

---

## 10. Icon-set convergence — a design mission, not a defect

**Not a gap.** The library's icon slots accept `ReactNode` by design, so a host
may feed its own icon set. This entry records a *deliberate divergence* so that
it is not rediscovered as a bug by the next reviewer.

**Observed.** Measured against the documentation site, the navigation icons are
the only element that differs by intent rather than by accident:

| | documentation site | this product |
|---|---|---|
| source | library `Icon` (lucide) | MUI glyphs (the product's existing set) |
| `fill` | `none` | `currentColor` |
| `stroke` / `stroke-width` | `currentColor` / `2` | `none` / — |
| box, colour | 16×16, same token | 16×16, same token |

Outlines versus filled glyphs — same box, same colour, no layout consequence.

**Decision (pilot).** The product keeps its MUI icons. The pilot's principle is
iso-functionality, and no capability is lost.

**Ask.** Converging OpenAEV's navigation onto the library's lucide set is worth
a dedicated design mission, out of this pilot's scope: it means mapping ~17
menu entries, arbitrating each icon individually, and possibly adding glyphs to
the library's registry where no equivalent exists.

---

## 11. Composite components accept no test attribute (`data-testid`)

**Needed.** A product's end-to-end suite must be able to address a library
component from the outside. In this product every navigation flow is covered by
Playwright page objects, and the tenant switcher is exercised by six specs.

**Today.** `NavbarSubmenu` types its props explicitly and does **not** spread
the rest onto the rendered element:

```ts
interface NavbarSubmenuProps {
  label: string;
  icon?: React.ReactNode;
  // …no `…React.ComponentPropsWithoutRef<'div'>`, no rest spread
}
```

So `<NavbarSubmenu data-testid="tenant-switcher">` is a TypeScript error and,
if cast through, renders nothing — the attribute is silently dropped. The same
shape applies to the other composite navigation parts; the leaf components that
accept `asChild` are unaffected, because the host owns the rendered node.

**Consequence.** The product had to re-anchor its page object on what the
component *does* emit — its own wrapper class (`.app-navbar`), the element role
and the visible label — and keep `data-testid` only on nodes it renders itself
through `asChild`. That is workable but fragile: those anchors are incidental
DOM, not a contract, so any internal restyle of the library can break a
product's test suite without any API change.

**Ask.** Spread the remaining props onto the root element of each composite
component (`...rest`), or at minimum accept and forward `data-testid`. This is
a small change with a large effect: it turns "incidental DOM" test anchors into
an explicit, stable contract. The next product migration will hit this on its
very first end-to-end run.

---

## 12. Portalled surfaces carry a hard-coded `z-index: 50`

**Needed.** A design system dropped into an existing application has to stack
above that application's own chrome. OpenAEV is a MUI application: its top bar
is a `MuiAppBar` at `z-index: 1100`, its drawers at `1200`, its modals at
`1300` — that is MUI's published `theme.zIndex` scale, and every MUI popover
the library replaces used to open at `1300`.

**Today.** Every floating surface in the library is fixed at Tailwind's `z-50`.
Grepping the bundle finds seven sites, i.e. all of them. Re-verified at pin
`ad108751b6611c0a4f613bd0f1dcc15358821c81`: still seven, and still no
`--fds-z-overlay` or any other stacking hook, so the compensation stays.

| Surface | `index.mjs` |
| --- | --- |
| `Tooltip` content | ~1593 |
| `Dialog` overlay | ~2567 |
| `Dialog` content | ~2652 |
| `MenuContent` | ~4549 |
| `Navbar` submenu flyout | ~6030 |
| `ProductSwitcher` dropdown | ~6226 |
| `Select` content | ~8062 |

Radix copies the content's computed `z-index` onto the portal wrapper it
appends to `<body>`, **as an inline style**:

```html
<body>
  <div data-radix-popper-content-wrapper style="position:fixed; z-index:50; …">
```

So in this product the header (1100) wins over every library menu, tooltip and
flyout. The symptom the checkpoint reported — "the ProductSwitcher menu goes
under the header" — is one cause, not one bug: all seven surfaces are affected
at once.

**Why it cannot be fixed at the call site.** `MenuContent` accepts `className`,
so `z-[1300]` would win there through tailwind-merge. But the `ProductSwitcher`
dropdown, the `Navbar` submenu flyout, `Tooltip` and `Dialog` expose no
`className` for their portalled part — the surface is internal. There is no
prop, no CSS variable, and no portal-container option. A host has no supported
way in.

**Consequence.** The product compensates with a global rule in
`design-system-host.css`, next to the button reset of entry 6:

```css
body > [data-radix-popper-content-wrapper] {
  z-index: 1300 !important; /* MUI zIndex.modal */
}
```

`!important` is not a preference: Radix writes the value inline, so nothing
short of it can win. The rule is deliberately scoped to `body >` so it only
touches portalled surfaces, and it is uniform — one cause, one rule. It is
recorded in the product's compensation→removal table.

**Ask.** Expose the stacking level as a CSS custom property consumed by every
portalled surface — `z-[var(--fds-z-overlay,50)]` — so a host sets it once:

```css
:root { --fds-z-overlay: 1300; }
```

That is better than a prop per component (seven components, and every call site
would have to remember), and better than a configurable portal container, which
solves DOM location rather than stacking. The default stays `50`, so nothing
changes for the library's own documentation site.

**Removal test.** After a pin bump that ships the variable: delete the rule,
set `--fds-z-overlay` in the host stylesheet, open the ProductSwitcher menu
over the header in both themes and both rail states, and check the computed
`z-index` of `body > [data-radix-popper-content-wrapper]` is above `1100`.

**Status — RESOLVED upstream** by library PR #96, adopted here at pin
`c0d6f0753df6d295d6a9de04060ad3f7b7ad232b`. The ask was implemented as stated:
six floating surfaces now resolve their level from `z-[var(--fds-z-overlay,50)]`
(Dialog panel + scrim, `TooltipContent`, `SelectContent`, `Menu`'s shared panel
surface, `NavbarSubmenu`'s flyout). The compensation has been deleted and
replaced by the one-line host declaration this entry asked for:

```css
:root { --fds-z-overlay: 1300; }
```

*Why the variable reaches the wrapper the old rule had to target.* The element
carrying the z-index for popper-positioned surfaces is Radix-generated, takes no
`className`, and is styled inline — which is why the compensation needed
`!important`. Radix derives that inline value from
`getComputedStyle(content).zIndex`, so once the content resolves to 1300 Radix
copies 1300 outward on its own. The declaration must sit on `:root`, not on an
app wrapper: portalled content mounts on `document.body`, so a variable scoped
to a subtree never reaches it.

**Adoption verification — DONE, entry CLOSED.** Measured in the running
product during the Header pilot checkpoint (library pin `c0d6f07`), with the
compensation removed:

| state | `body > [data-radix-popper-content-wrapper]` z-index | header z-index |
|---|---|---|
| dark / rail expanded | **1300** | 1100 |
| light / rail expanded | **1300** | 1100 |

The portalled surface stacks above the bar with **no `!important` anywhere** —
`:root { --fds-z-overlay: 1300 }` alone is sufficient, which is exactly what
library PR #96 promised. Radix copies the resolved level outward on its own.

The collapsed rail is reported as not applicable rather than passed: the
`ProductSwitcher` trigger is **not rendered at all** when the rail is
collapsed (it exposes only the footer and the expand control), so there is no
surface to open from it. Established by enumerating the collapsed rail's
controls, not assumed.

**Original outstanding note, for the record.** The removal test above is a
*measurement in the running product*, and it has not been run yet: this pin bump
was made ahead of the Header implementation, with no visual checkpoint attached.
Recording it as verified on the strength of the library's own tests would repeat
the mistake [#16](#16-the-productswitcher-trigger-has-no-pointer-cursor)
documents — a defect that survived a full visual checkpoint because it was
looked at rather than measured. To be executed at the first checkpoint where the
product runs: computed `z-index` of `body > [data-radix-popper-content-wrapper]`
with the ProductSwitcher menu open, in both themes × both rail states, expected
above `1100`.

---

## 13. The stylesheet only carries the utilities the library itself uses

**Needed.** A product composes around library components — a logo in a slot, a
signature row in the footer — and needs a little geometry to do it. The obvious
move, and the one the library's own documentation examples show, is to pass
Tailwind utility classes.

**Today.** `@filigran/design-system/dist/index.css` is the *compiled output of
the library's own components*, not a Tailwind distribution. It contains exactly
the utilities the library happens to render, and nothing else. Verified at this
pin, in a product that has **no Tailwind build of its own**:

| Class | In `index.css`? |
| --- | --- |
| `w-auto`, `shrink-0`, `flex-1`, `truncate`, `sr-only`, `size-4`, `h-7`, `w-7` | yes |
| `text-default-primary`, `text-content-caption`, the token sets | yes |
| `size-3`, `object-cover`, `object-contain`, `object-left` | **no** |

A class that does not exist fails **silently**: no build error, no console
warning, no visual marker — just no style. This product shipped four inert
classes without noticing, and one of them mattered: the header logo carried
`object-contain` on a `w-full h-7` box, so instead of being letterboxed it was
**stretched** — natural aspect ratio 5.118 rendered at 4.500, a 12% horizontal
squash on the product's own logo. It survived several visual checkpoints.

Worse, one inert-looking class *did* work: at the pin where this was first
audited, `h-3` resolved only because an unrelated sibling package,
`@filigran/chatbot/dist/styles.css`, happened to ship it. (At pin `ad10875` the
design system ships `h-3` itself, so that one class is no longer borrowed — the
hazard is not.) A product that audits against "whatever stylesheet is loaded"
rather than against the design system's own is one sibling-package release away
from a silent regression it did not cause.

**Consequence.** The product's rule is now: **token-bearing classes from the
library are fine, geometry is inline.** Product-specific sizing uses inline
styles, which are guaranteed to apply and are visible in review.

**Ask.** Two things, both cheap. (1) Say in the consumer documentation that
`index.css` is not a Tailwind runtime and that a consuming product without its
own Tailwind build must not rely on arbitrary utilities — with the list of what
*is* dependable (the token classes). (2) Where a slot's geometry is genuinely
the host's business — the `ProductSwitcher` logo slots are the case here — say
what the slot expects (a 28px square that clips, in that instance), so the host
sizes its asset instead of guessing with a class that may not exist.

---

## 14. The root manifest does not declare the package's dependencies

**Status: ✅ RESOLVED** upstream by library PR #79, verified here by
re-installing at pin `ad108751b6611c0a4f613bd0f1dcc15358821c81` — `yarn install`
completes in 17 s and `dist/index.mjs` is built.

**The cause was broader than this entry first stated, and the correction is
worth recording.** This was filed as a Yarn-lockfile drift. It was in fact two
faults, and the lockfile was the *second* one:

1. **The root `package.json` is the install proxy.** No package manager can
   target a subdirectory of a git dependency, so npm, pnpm and Yarn all install
   the repository root as `@filigran/design-system` and run its
   `prepare`/`prepack` build, resolving against **that** manifest alone. PR #73
   declared its two new dependencies only in
   `packages/filigran-design-system/package.json`. The build therefore could not
   see them **on any package manager** — not a Yarn-specific failure.
2. `yarn.lock` was stale as well, which is what this product's own bisection
   surfaced first, because Yarn Berry was the manager it happened to use.

The pilot's diagnosis was right about the trigger and the first broken commit,
and **one manifest too narrow about the blast radius**. Worth remembering when
reading any product-side report: a product sees the failure through the single
package manager it uses, so it will tend to name that manager as the cause.
Reproduce on a second one before believing the scope.

**Severity when open: blocking.** For the record, this is not a styling gap —
while it was open, no product could pin the library at `main`.

**Needed.** The documented consumption method is a direct git dependency
(`XTM-Foundation/filigran-design-system#commit=<sha>`). Yarn Berry clones the
repository, sees `__metadata` in `yarn.lock`, bootstraps with Yarn against that
lockfile, then runs `prepack` to build `dist`. So **`yarn.lock` is part of the
published contract**, even though no library developer ever uses it.

**Today.** The repository carries two lockfiles. Everything in the library's own
workflow uses pnpm — `.github/workflows/*.yml` contains 4 `pnpm install` steps
and not a single `yarn` invocation — so `pnpm-lock.yaml` stays fresh while
`yarn.lock` only moves when someone thinks to touch it. Its last update is
`41a0a57`, *the Yarn-Berry-support commit itself* (#68).

PR #73 (Checkbox / Radio) added two runtime dependencies. It updated
`packages/filigran-design-system/package.json` and `pnpm-lock.yaml` correctly,
and left `yarn.lock` untouched:

| Commit | `react-checkbox` in `pnpm-lock.yaml` | in `yarn.lock` | Installs? |
| --- | --- | --- | --- |
| `9cd4271` (#74, `Menu`) | 0 | 0 | ✅ |
| `4e2b0bf` (#73, Checkbox/Radio) | 3 | **0** | ❌ |
| `b6855f4` (#77, list seam) | 3 | **0** | ❌ |

The failure, reproduced in this product at both `4e2b0bf` and `b6855f4`:

```
➤ YN0036: Calling the "prepack" lifecycle script
    STDERR src/components/checkbox/Checkbox.tsx(2,36): error TS2307:
           Cannot find module '@radix-ui/react-checkbox' or its
           corresponding type declarations.
    STDERR Error: error occurred in dts build
➤ YN0058: Packing the package failed (exit code 1)
```

Bisected empirically, not inferred: `9cd4271` installs, `4e2b0bf` does not, so
**#73 is the first broken commit** and everything merged after it — including
the #77 fix this product is waiting for — is unreachable.

**Consequence while it was open.** The OpenAEV pilot was frozen at pin
`9cd4271`; the pin bump requested for #77 could not be performed at all. The
product did **not** compensate, because an install failure has no product-side
workaround — the pin simply stayed put until the library fixed it, two hours
later.

**Ask — the lockfile is a symptom, the missing guard is the defect.** Two
things, in this order (both delivered by #79):

1. **Regenerate `yarn.lock`** so `main` installs again (`yarn install` at the
   root, commit the result). That unblocks consumers today.
2. **Add a consumer-install job to CI**, which is the only durable fix. Nothing
   in the library's pipeline exercises the path products actually use, so this
   class of breakage is invisible to a green build. The job is small:

   ```yaml
   consumer-install:
     runs-on: ubuntu-latest
     steps:
       - run: mkdir /tmp/c && cd /tmp/c && npm init -y
       - run: cd /tmp/c && yarn set version berry &&
              yarn add "XTM-Foundation/filigran-design-system#commit=${{ github.sha }}"
       - run: test -f /tmp/c/node_modules/@filigran/design-system/…/dist/index.mjs
   ```

   Any PR that adds a dependency without refreshing `yarn.lock` then fails on
   its own branch, where it is cheap to fix, instead of silently blocking every
   product until one of them tries to bump.

**Removal test.** At a candidate pin, `yarn install` in a consuming product
completes without a `YN0058 Packing the package failed`, and
`node_modules/@filigran/design-system/packages/filigran-design-system/dist/index.mjs`
exists.

**Proof of resolution**, run in this product at pin `ad10875`:

```
$ yarn install
➤ YN0000: · Done with warnings in 14s 221ms          # was: YN0058 Packing failed
$ ls node_modules/@filigran/design-system/packages/filigran-design-system/dist/index.mjs
… exists
$ grep -c Checkbox …/dist/index.d.ts
9                                                     # the #73 components built
```

**What #79 put in place, and why it closes the class and not just the case.**
The two dependencies were mirrored into the root manifest and the lockfiles
regenerated — that fixes the instance. The durable part is the guard: an
install proof from a blank external project now runs **as a step of the required
job**, so it is blocking by construction with no repository setting to
configure, plus a parity check between the two manifests. The failure mode this
entry describes can no longer reach `main` unseen.

**The lesson for the next product.** A library's own CI can be entirely green
and the library still be uninstallable, because a library's pipeline builds from
its workspace and a product installs from its git tree — two different paths.
Before trusting a pin, know whether the library exercises the consumer path.
This one now does.

---

## 15. The collapse toggle has no pointer cursor

**Status: ✅ RESOLVED** upstream by library PR #84, *"give button-rendered rail
rows the pointer cursor"*, adopted here at pin `486cec92c`. Nothing was ever
compensated product-side, so nothing had to be removed — the fix simply arrived.

**Proof, measured in a browser rather than read off a class list.** Every
`<button>` the rail renders was queried with `getComputedStyle(el).cursor` in
the running product, in both rail states:

| Rail state | Button row | Computed `cursor` |
|---|---|---|
| Expanded | `Components` (submenu trigger) | `pointer` |
| Expanded | `Settings` (submenu trigger) | `pointer` |
| Expanded | `By Filigran` (footer signature) | `pointer` |
| Expanded | `Collapse` | `pointer` |
| Collapsed | `By Filigran` | `pointer` |
| Collapsed | `Expand` | `pointer` |

Six button-rendered rows across the two states, all `pointer`. The anchors were
measured too — 17 expanded, 19 collapsed — and none regressed. The upstream fix
is guarded by a computed-style suite of the library's own, so the assertion is
not a source-level `toHaveClass("cursor-pointer")` that would share this
defect's blind spot.

**What happens.** The `Navbar`'s collapse/expand control renders without
`cursor: pointer`, so the pointer stays an arrow over a control that is
clickable. Raised in product review of the OpenAEV pilot (PR #7150).

**Why it is a library issue and not a product one.** The control is rendered
entirely by the library; the product never sees the element. The rule the
library's own stylesheet applies to its other interactive elements is simply
missing on this one. A product-side fix would mean reaching into the library's
internals with a descendant selector in the host stylesheet — a compensation
that is invisible to the library's own tests, that would silently rot the day
the class names change, and that every consuming product would have to write
independently. Nothing is gained by writing it three times.

**What the library should do.** Apply the same pointer affordance the library
gives its other activatable controls, on the collapse toggle.

**Removal test.** Not applicable — there is nothing to remove product-side.
**Verified at pin `486cec92c`:** the table above. The condition stated when this
entry was opened is met.

**Why this defect survived the computed-style diff.** It is worth recording how
it was found, because the method missed it. The Step 5b diff compares the
product against the library's own documentation site — and the documentation
site has the same defect. **A check that compares against a reference is blind
to defects present in the reference.** Only interacting with the running product
surfaces it. That lesson is now in the playbook's visual-verification step.

---

## 16. The `ProductSwitcher` trigger has no pointer cursor

**Status.** Fixed upstream by library PR #94, shipped in pin
`c0d6f0753df6d295d6a9de04060ad3f7b7ad232b`. **Adoption not yet measured** — see
the bottom of this entry. **No product compensation** existed, so there is
nothing to remove; closing this entry is a measurement, not a deletion.
Found while verifying that
[#15](#15-the-collapse-toggle-has-no-pointer-cursor) was fixed — the same
defect class, on a component the fix did not reach.

**What happens.** Library PR #84 added `cursor-pointer` to `NavbarItem`, which
covers every navigation row of the rail. The `ProductSwitcher` trigger in the
rail header is not a `NavbarItem`; it is the library's icon button, and it still
declares no cursor. Measured in the running product at pin `486cec92c`:

| Element | Tag | Size | Computed `cursor` |
|---|---|---|---|
| `Filigran products` (ProductSwitcher trigger) | `button` | 24×24 | **`default`** |

Its class list is entirely library-owned — `border-0 bg-transparent p-0 m-0 …
inline-flex items-center justify-center rounded-sm transition focus-visible:…`
— and contains no product class. The product never styles this element.

**Why this is worth a separate entry rather than reopening #15.** #15 was
stated, and closed, against a testable condition: the rail's button-rendered
*rows*. That condition is genuinely met. Reopening it would blur a verified
result; recording the residue separately keeps both facts true. It also makes
the more useful point visible: **the fix was applied at the component that had
the reported symptom, not at the layer where the cause lives.** Any library
`<button>` that is not a `NavbarItem` still has it.

**What the library should do.** Rather than a third per-component patch, put the
affordance where the cause is: every activatable, non-disabled control the
library renders as a native `<button>` should carry it — the shared button base
is the natural place. A per-component fix will keep leaking, because the browser
gives `<a href>` a hand cursor for free and gives `<button>` nothing, so every
new button-rendered control starts out wrong by default.

**Why no compensation.** A product-side fix means a descendant selector reaching
into the library's internals from the host stylesheet: invisible to the
library's tests, silently rotting when the class names change, and rewritten
independently by every consuming product. The affordance is missing, not wrong;
nothing in the product is broken by waiting for the pin that carries it.

**Removal test.** Not applicable — nothing to remove product-side.
**Verification at the next pin bump:** in the running product, read
`getComputedStyle(trigger).cursor` on the ProductSwitcher trigger in both rail
states and both themes; it must be `pointer`. Hovering by hand is not enough —
this exact defect survived a full visual checkpoint before it was measured.

**What #94 shipped.** The library took the argument above rather than a third
per-component patch: the affordance moved to the shared interactive layers —
`buttonVariants`, `iconButtonVariants`, `TabsTrigger`, `SelectTrigger` and the
`Switch` root — so the `ProductSwitcher` trigger inherits it as an icon button,
and any future button-rendered control starts out right by default. This is the
cause-level fix this entry asked for.

**Adoption verification — DONE, entry CLOSED.** Measured in the running
product during the Header pilot checkpoint (library pin `c0d6f07`), reading
`getComputedStyle(trigger).cursor`, never by hovering:

| state | `ProductSwitcher` trigger | other rail controls |
|---|---|---|
| dark / rail expanded | **pointer** | Components, Settings, By Filigran, Collapse — all `pointer` |
| light / rail expanded | **pointer** | idem |
| dark / rail collapsed | not rendered | By Filigran, Expand — both `pointer` |
| light / rail collapsed | not rendered | idem |

The cause-level fix in #94 holds: every button-rendered control in the rail
reports `pointer`, not just the one that was patched. As with entry 12, the
collapsed rail does not render this trigger at all, so it is recorded as not
applicable rather than silently counted as a pass.

**Original outstanding note, for the record.** Not yet measured in this product. The
pin bump carrying #94 was made ahead of the Header implementation, so the app
was never brought up. Per the paragraph above, this entry is closed only by a
computed-style reading, and asserting `pointer` from the library diff alone is
precisely the shortcut that let this defect survive its first checkpoint. To be
executed at the first checkpoint where the product runs.

---

Raised during: the **Header pilot** (replacing the hand-built MUI
`AppBar`/`Toolbar` admin top bar with `Header`), library pin
`c0d6f0753df6d295d6a9de04060ad3f7b7ad232b`.

---

## 17. A themeable surface has no supported hook for a product-driven colour

**Needed.** This product's top bar is **customer-configurable**: the platform
`background_color` setting (per-tenant, editable from the admin UI) reaches the
bar through `palette.background.gradient`, and the bar's gradient has always
followed it. Adopting `Header` must not take that away.

**Today.** `Header` paints its glass layer with `before:bg-gradient-default`,
i.e. the library token `--gradient-default`, which is assembled at `:root` from
two stop tokens. The component exposes **no** prop, slot or documented custom
property for a consumer-supplied background. `--fds-header-height` was added for
the height, so the pattern exists — it simply was not extended to the colour.

**Consequence.** The product had to re-declare `--gradient-default` itself as an
inline style on the bar. That works, and `Header.tsx` even documents *why* it
works, but it is a consumer reaching into a token the library considers
internal:

- it depends on the current internal shape (a single assembled gradient
  property). If the library later inlines the two stops into the utility, or
  renames the token, this product's customers silently lose their colour again
  — with no build error and no visual marker beyond "a slightly different
  shade";
- overriding the two *stop* tokens instead is not an option at this scope: a
  `var()` inside a custom-property declaration is substituted on the element
  that declares it, so stops re-declared on a wrapper cannot reach a gradient
  already assembled at `:root`. Only `:root` works for the stops, and that
  would repaint every other library surface — a far larger blast radius than
  the one change the product actually wants;
- the stops must be passed **opaque**. The legacy bar faded them itself at 90%;
  the library paints its own gradient layer at Figma's 94%. Pre-faded stops
  would therefore apply the transparency twice, which is visible as a washed-out
  bar and is the kind of mistake a consumer makes exactly once — so it belongs
  in whatever hook replaces this workaround, as an explicit statement that the
  consumer supplies colour and the library owns opacity.

**Suggested.** Publish a first-class hook, in the same spirit as
`--fds-header-height`: either a `--fds-header-background` custom property read
by the component (`before:[background:var(--fds-header-background,var(--gradient-default))]`),
or a documented guarantee that `--gradient-default` may be re-declared per
element. Either makes the product's intent expressible and the contract stable;
today it is neither.

**Generalisation.** This is not about the Header. Any library surface a product
lets its customers colour will hit the same wall — the question "how does a
consumer supply a colour without forking the component or overriding a global
token?" has no answer yet. Worth settling once, at token level.

---

## 18. `grow` and `grow="unbounded"` — the cap is right, its discoverability is not

**Needed.** A search cluster whose window is `min-width: 200px`,
`max-width: 500px` (design decision, Sandy, round 4 — it replaced the
`550px / 50% / 680px` window the pilot first shipped).

**Today.** `HeaderGroup grow` caps at Figma's 400px — **below this product's
ceiling**, so the cap is not merely tighter than the product's preference, it
cannot express it. `grow="unbounded"` exists precisely for this and was, per the
RFC, added after measuring this bar. The capability is correct and the pilot used
it as intended.

**The gap is that nothing steers you to it.** `grow={true}` is the obvious
choice from the prop name, it type-checks, and it renders a plausible-looking
bar — just a silently narrower search field. Nothing fails.

**Where a consumer can and cannot declare that window.** Worth stating, because
the obvious place is the wrong one:

- **On the `HeaderGroup`** — works, and is what this product does. The group is
  the product's own layer; the field then fills it through `SearchField
  fullWidth`, so the rendered field is exactly 200–500 wide.
- **On the `SearchField` instance** — does *not* work. The component forwards
  `className` to its wrapper but spreads its remaining props, **`style`
  included**, onto the inner `<input>`. An inline width passed to the component
  therefore sizes the text box inside the field rather than the field itself.
- **Via `className` on the field** — not available to this product: it consumes
  the library's prebuilt CSS and has no Tailwind build of its own, so a utility
  class it invented would resolve to nothing (see
  [#13](#13-the-stylesheet-only-carries-the-utilities-the-library-itself-uses)).
- **Reaching into the component's internal selectors** — forbidden by the scope
  rule, and the reason this entry exists rather than a product-side override.

**One consequence worth keeping.** `grow="unbounded"` gives the group `min-w-0`,
which lets it shrink past its own content, so an explicit floor is not cosmetic:
without `min-width` the field is squeezed below its intended minimum at narrow
viewports.

**Suggested.** Two things. In the docs/meta: state the 400px cap and the
`"unbounded"` escape on the `grow` prop description itself, where an implementer
reads it, rather than only in the RFC's rationale. On `SearchField`: document
which props reach the wrapper and which reach the inner input — the split is
invisible from the type signature and a width silently lands on the wrong box.

---

## 19. The CI-secret guard artifact cannot express a call site that must stay unarmed

**Context.** `process/artifacts/ci-design-system-secret.test.ts` is designed to
be copied verbatim into a consuming product, and was — it is now
`openaev-front/src/__tests__/ci-design-system-secret.test.ts`. It is a good
artifact and it caught real wiring.

**Needed.** This product has a workflow that must **never** receive the
credential: `deploy-feature-branch-build.yml` checks out untrusted pull-request
code (`ref: head_sha`) and then resolves the composite action from *that same
tree*, so any secret passed in is attacker-controlled. It is deliberately left
unarmed, and stays that way until the library is published to npm and the token
disappears entirely.

**Today.** The artifact's composite rule is "every caller of an action that
declares the input passes it". That rule cannot express "this caller must not".
The only offered escape is to exclude the file, which **silently un-guards it** —
the one site where the guard matters most.

**What this product did.** Inverted the exemption into an assertion: exempt
sites are asserted to be *free* of the credential, so the security decision is
machine-enforced rather than resting on a comment. Plus a staleness assertion
that fails if an exemption stops matching a real call site, so the list cannot
rot into a silent pass. Both are marked `PRODUCT-SPECIFIC ADDITION` in the file.

**Suggested.** Fold the concept upstream: a `NEVER_ARMED` list in the artifact,
asserted negatively, with the staleness check. "Must not be armed" is a normal
state for a workflow handling untrusted input, not an exception — every product
adopting this artifact will meet it.

---

## 20. `Navbar` and `Header` ship no positioning, so every product re-invents it — and gets it wrong

**Severity.** Medium — silent visual defect, escapes every unit test.

**What happened.** Both shell components render in flow and leave positioning to
the product. Pilot 1 gave the rail `position: sticky; top: bannerHeight;
align-self: flex-start`, which reads as correct and passed review. It is not.
A sticky element resolves against its containing block, and the app shell's
height is the *fractional* document height. Measured on `/admin`:

| | value |
|---|---|
| shell (containing block) height | `1677.59px` |
| document height | `1678px` |
| remainder | `0.41px` |
| rail drift at maximum scroll | `-0.41px` |

The rail slid up by exactly the remainder at the end of a long scroll. It was
caught by a designer's eye, not by any test — the drift is sub-pixel and only
appears at maximum scroll on a page whose height has a fractional part, which
is to say on most pages, unpredictably.

The Header dodged this only because the pilot happened to give it
`position: fixed`. Same doctrine ("fixed = immobile to the pixel"), two
components, and nothing in the library makes the correct choice the easy one.

**Why this is the library's problem, not the product's.** The fix is not one
line. `fixed` takes the rail out of flow, so the product must also hand-roll an
in-flow spacer, match its width to the rail's two states, *and* replay the
library's own width transition (`width 0.15s cubic-bezier(0.4, 0, 0.2, 1)`) or
the content visibly lags the rail while it animates. That transition is an
internal library value the product has to read out of the browser and hard-code
— the day the library retimes it, every product desynchronises silently.

**Suggested.** Own the positioning contract, since the doctrine is already the
library's:

1. Ship `position: fixed` as the default on both components, with an offset
   prop for the banner (`offsetTop`).
2. Ship the spacer as part of `Navbar` — it is the only component that knows
   its own two widths and its own transition curve.
3. Failing both, export `NAVBAR_WIDTH_OPEN`, `NAVBAR_WIDTH_COLLAPSED` and
   `NAVBAR_WIDTH_TRANSITION` so the product stops hard-coding measured values.

**Condition for removal (product side).** When the library positions the rail
itself, delete the `position/top/left` override and the `navbar-spacer` element
in `AppNavbar.tsx`, and delete `navbarConstants.ts`; the rail must still measure
`0.00px` drift under `openaev-front/rail-drift` measurement.

---

## 21. `IconButton` renders a hard `<button>` and accepts no `asChild`

**Severity.** Medium — forces the product to choose between the library's
component and correct link behaviour.

**What happened.** Four controls in the top bar are genuine links: three router
routes and one external XTM One URL. `IconButton` hard-renders `<button>` and
its props are `Omit<ComponentPropsWithoutRef<"button">, …>` with no `asChild`,
so it cannot carry a navigation target. Turning them into buttons with an
`onClick` would drop middle-click, ⌘/Ctrl-click "open in new tab", "copy link
address" and the browser's status-bar preview — a real behavioural loss, and
the pilot's rule is iso-functionality.

The product therefore applies `iconButtonVariants` to its own `<a>`/`<Link>`
(`TopBarIconLink.tsx`). That keeps the library's states, but it re-implements
the component's DOM contract by hand — including the `aria-hidden` glyph
wrapper and the one class the component adds for `active`, neither of which is
part of any published API.

**Notable.** `Button` already has `asChild`, and `MenuTrigger`'s own
documentation says it is "always meant to be used with `asChild` around a real
library component … IconButton (~80% of sites)". The gap is `IconButton`'s
alone, and it is the component most likely to be a link.

**Suggested.** Give `IconButton` the same `asChild` `Button` already has.

**Condition for removal (product side).** Delete `TopBarIconLink.tsx` and wrap
`<Link>`/`<a>` in `<IconButton asChild>`; the icon-link tests must stay green.

---

## 22. Three controls in one bar have no library equivalent

**Severity.** Low — each is small; together they decide how much of a product
surface the library can actually own.

**What happened.** Applying "where the library ships a component, use it" to
the top bar left exactly three MUI survivors, all for the same reason — the
library ships no counterpart:

| Control | Used for | Nearest library export |
|---|---|---|
| `Divider` | the rule between the AI actions and the platform actions | `NavbarSeparator`, `MenuSeparator`, `SelectSeparator` — all bound to their own component |
| `Badge` | the unread-notifications dot, the running-bulk-operations count | none |
| `Popover` | the bulk-operations panel (progress bars, not a menu) | `Menu` (command rows), `Dialog` (modal) |

The divider was replaced with a plain rule painted from the library's own
border token; `Badge` and `Popover` stayed MUI.

**Suggested.** A general-purpose `Separator` is nearly free and would remove
the last hand-painted rule. `Badge` and `Popover` are real components and
should be sized as such — but they should be *named*, so products stop
discovering the gap one pilot at a time.

### Status, 2026-08-13 — one of the three is closed

**`Badge`: RECEIVED and ADOPTED** at pin `8798cbb` (library PR #114). Both MUI
badges in the bar are gone, and the compensation markers with them:

| | before (MUI) | after (library `Badge`) |
|---|---|---|
| unread notifications | `<Badge color="secondary" variant="dot">` | `<Badge content={unreadCount} dot>` — the count is still announced while the visual stays a dot |
| running bulk operations | `<Badge badgeContent color="primary" overlap="circular">` + an `sx` forcing 10px text in a 16px box | `<Badge content={runningCount} circularAnchor>` — the library's own 20px counter |

Two consequences, both accepted rather than compensated:

- the counter grows from **16px to 20px** (`h-5 min-w-5`). Sandy's arbitration:
  it displays a count, so it takes the default counter size; the growth is
  assumed. The three `sx` overrides that forced the old size are deleted, not
  re-expressed.
- the unread dot's colour moves from the product's MUI `secondary` to the
  library `Badge`'s default `brand` tone. `BadgeTone` is
  `brand | error | success | neutral`, with no equivalent of the old value, and
  inventing one product-side is what this entry exists to stop.

Guarded by `TopBarNotifications.test.tsx` and `BulkOperationsIndicator.test.tsx`,
plus `MuiBadge-` added to the shared `expectNoMuiControls` list so a relapse
fails rather than being noticed at a checkpoint.

### Status, 2026-08-13 (later the same day) — three of four are closed

**The bar's rule: RECEIVED and ADOPTED** at pin `7e7b417` (library PR #117). Not
as a general-purpose `Separator` — as `HeaderGroup separatorBefore`, a `::before`
on the trailing cluster. That answers this bar's need better than a component
would: no DOM node to announce or focus, and 16px of clear space anchored to the
group rather than inherited from whatever gap the parent runs. The hand-painted
`<div role="separator">` is deleted.

Composition matters and cost a read of the library's own source: the 16px BEFORE
the rule is `ml-2` **plus the parent cluster's `gap-2`**, so the separator-bearing
group must sit INSIDE a cluster, not directly under the `Header` (which has no
gap). Modelled as Figma does — an outer `HeaderGroup` for the whole trailing
frame, holding the AI cluster and an inner `<HeaderGroup separatorBefore>` around
the platform actions. Measured in both themes:
`AI cluster (gap 8) │ 16 │ rule 1px @50% │ 16 │ actions (gap 8)`.

**`Progress`: RECEIVED and ADOPTED** at the same pin (library PR #115), as two
components rather than one — `Spinner` (indeterminate) and `ProgressBar`
(determinate). Both usages converted, and the dated exemption that tolerated MUI
progress in `expectNoMuiControls` is **deleted**: the guard is strict again.

Three consequences, all accepted rather than compensated:

| | before (MUI) | after (library) |
|---|---|---|
| ring on the bar button | `CircularProgress size={32} thickness={2}`, brand at 50% alpha | `Spinner size="lg"` — **24px**, the largest designed step; full-opacity brand |
| ring per running row | `CircularProgress size={14} thickness={5}` | `Spinner size="sm"` — 16px |
| bar per operation | `LinearProgress` 6px, radius 3, coloured per status | `ProgressBar` — **4px** (`md`), radius 4, **one colour** |

`ProgressBar` has no colour axis by design ("nothing in either product colours a
progress bar" — its RFC §7). This product did: brand while running, success green
once complete, error red on failure. The colour is **gone from the bar**; the
status is still carried by the leading icon and by the caption, whose colour the
product keeps. If that reads as a loss, the ask is a `tone` on `ProgressBar`, not
a product-side override.

**`Popover`: the only one still open.** Re-verified against the export surface at
`35a4768`: `Popover`, `PopoverTrigger` and `PopoverContent` all resolve to
`undefined` from the built entry point — PR #105's primitive remains internal (it
backs `Combobox`). The bulk-operations panel therefore stays a MUI `Popover`, and
with it the `MuiBox` wrappers that lay its rows out. That subtree is the single
exemption left in `expectNoMuiControls`, dated and conditioned on this entry.

### Status, 2026-08-13 (final bump `35a4768`) — everything but Popover is closed

The three narrownesses this entry recorded as accepted consequences were all
answered by the library, and the product now takes each one:

| | recorded as accepted | answered by | now |
|---|---|---|---|
| spinner capped at 24px, sitting ON a 24px glyph (0.00px clearance) | PR #119 adds an `xl` tier | `Spinner size="xl"` — 32px, **4.00px** clearance, measured |
| `ProgressBar` has no colour axis, so the bars lost their per-status colour | PR #118 adds `tone` | `tone` = success / error / default from `bulk_operation_status`; the completed bar is green again |
| `Badge` default was `brand` | PR #119 makes `error` the default | both badges are red **with no product change** — the product passes no `tone`; per-site arbitration is Sandy's |

One gap remains inside a shipped component, worth naming rather than working
around: **`ProgressBar` has no `warning` tone**. Nothing in this bar needs one
today (the three statuses map onto default/success/error), but a status that did
would keep the default rather than borrow a neighbouring family.

---|---|---|---|---|
| ring around the glyph | the bar's bulk-operations button | circular, `stroke-width: 2px` | **no** — no `aria-valuenow`, it spins while `runningCount > 0` | declared 32×32, centred over a 20×20 glyph in a 36×36 button. Its *rect* reads ~42px because an indeterminate ring rotates and `getBoundingClientRect` returns the rotated axis-aligned box — the drawn circle is 32 |
| one bar per operation | the bulk-operations panel | linear, `border-radius: 12px` | **yes** — `aria-valuenow` = `processed/total` (35, 76, 100 in the captures), plus a `%` caption the consumer renders | 328×6, full panel width minus padding |

Colours, measured in both themes: the running ring takes the brand colour at 50%
alpha (`rgba(66, 202, 255, 0.5)` dark, `rgba(0, 21, 168, 0.5)` light). A linear
bar pairs a solid fill with the same colour at 15% as its track, and switches
family with status — brand while running, success green once complete.

Both are MUI today (`CircularProgress`, `LinearProgress`). They are now detected
by the shared `expectNoMuiControls` guard and carry an explicit **dated
exemption** whose removal condition is this entry: the day a library `Progress`
ships, deleting the exemption turns the guard red and forces the migration.

**Suggested.** One component with a `variant` (`circular` | `linear`) and an
optional `value`: omitted means indeterminate, present means determinate. Both
usages in this bar need the same colour treatment (the running state uses the
brand colour at 50% alpha today) and the linear one needs a caption slot or the
consumer keeps rendering the `%` itself.

---

## 23. `Button` has no `active` prop, `IconButton` does

**Severity.** Low — small asymmetry, guessable fix, silent when guessed wrong.

**What happened.** "Ask Ariane" is a toggle: while the chat panel is open the
button stays tinted. `IconButton` expresses this with `active`, which also
sets `aria-pressed`. `Button` has no equivalent, so the product applies
`bg-filigran-ia-secondary-transparency` — the class `IconButton` uses
internally for that state — through `className`. It renders correctly and is
undocumented; if the library retints its active state, this button silently
stops matching.

**Suggested.** Give `Button` the same `active` prop, with the same
`aria-pressed` behaviour. Two sibling components in one bar should not express
the same state two different ways.

**Condition for removal (product side).** Replace the `className` in
`AskArianeButton.tsx` with `active={isOpen}`.

---

## 24. Layered utilities lose to the host's unlayered CSS — silently

**Severity.** High — silent visual defect, class present, no error, wrong pixels.

**What happened.** Two controls in the bar carried the *identical* class list
from `iconButtonVariants({ priority: 'tertiary' })`. Measured:

| Element | Class list | Computed colour |
|---|---|---|
| `<button>` (account menu) | identical | `rgb(66, 202, 255)` — brand blue, correct |
| `<a>` (triggers) | identical | `rgb(242, 242, 243)` — white, wrong |

The cause is not specificity. The library ships its utilities inside a CSS
cascade layer, and **any** unlayered rule beats a layered one regardless of
specificity. MUI's `CssBaseline` injects an unlayered `body a { color: … }`, so
on an anchor the library's `text-filigran-brand-primary` never applies. On a
`<button>`, nothing unlayered competes, so it does.

This is the worst failure shape there is: the class is present in the DOM, the
stylesheet contains it, no tool reports anything, and the pixels are wrong. It
was found by measuring two elements that should have matched — not by review.

**Why every product will meet this.** Any host with a CSS reset or a component
library that writes unlayered global rules — which is to say every product
adopting this library into an existing app — overrides library styling for
whichever elements those globals happen to target. Which elements, and in which
product, is unknowable from the library side.

**Suggested.** Say it in the adoption documentation, in the loudest terms:
library classes are layered and lose to unlayered host CSS; audit the host's
global element selectors (`a`, `button`, `input`) before adopting. Better,
publish the layer name so products can order it explicitly with
`@layer host, filigran;`. Best, ship the recipe.

**Condition for removal (product side).** When the layer order is declared and
`body a` no longer wins, delete the inline `color` in `TopBarIconLink.tsx` and
`CtemCommandCenterButton.tsx`; the measured colours must stay equal.

---

## Step 5b — computed-style diff against the documentation site

Run at pin `c0d6f07`, docs site at the same SHA, product on the same machine,
one measurement function applied to both pages. Script kept at
`fds-migration/scripts/compare-header-vs-docs.cjs`; re-run it at every pin bump.

Every value the library owns is **identical** on both sides:

| Property | Docs | Product |
|---|---|---|
| `height` | 68px | 68px |
| `padding` | 16px | 16px |
| `display` / `align-items` / `justify-content` | flex / center / space-between | flex / center / space-between |
| `border-bottom-width` | 1px | 1px |
| `backdrop-filter` | blur(4px) | blur(4px) |
| `::before` `opacity` | 0.94 | 0.94 |
| `::before` `inset` / `z-index` | 0px / -10 | 0px / -10 |
| `background-color` | transparent | transparent |

The five differences, each with a named cause — none is a defect:

| Property | Docs | Product | Cause |
|---|---|---|---|
| `position` | `relative` | `fixed` | **By design.** The Header ships no positioning, only `relative` as a containing block; the doctrine is never sticky, always fixed to the top of the viewport. The product supplies the fixing. |
| `z-index` | `auto` | `1100` | Set by the product, above content and below MUI's poppers at 1300. |
| `min-height` | `auto` | `0px` | The product's MUI `CssBaseline` reset. Does not affect layout: `height` resolves to 68px on both sides. |
| `font-size` | 16px | 14.4px | The product's MUI theme sets a 90% base font size product-wide; pre-existing and intentional, not Header-specific. |
| `font-family` | `…, "IBM Plex Sans Fallback"` | `…, sans-serif` | `next/font` local fallback, an artifact of the documentation site only. Same first family on both sides. |

**Verdict.** No host CSS bleed, no missing reset, no pin lag, no design delta.

---

## 25. `SearchField` has no clear control — the visible cross is the browser's

**Status.** **Fixed upstream by library PR #100**, shipped in pin
`c8a3289ec950289e92ee4353c4cfce2be2394f77`, and **verified in the product** —
see "Adoption measured" at the bottom. **No product compensation existed**, so
there is nothing to remove: the entry was deliberately filed without a local
patch, since a product-side cross would have been exactly the hand-rolled
control the scope rule forbids. Closing this entry is a measurement.

**Severity.** Medium — visual inconsistency with the rest of the library, on
the most exposed field of the product.

**Reported by.** Design review of the Header pilot, 2026-08-10: *"the clear
cross that appears when typing does not look like the library's crosses (Chip,
Dialog)."*

**What it actually is.** It is not a component at all. `SearchField` renders its
input as `type="search"` and does not neutralise the user-agent's
`::-webkit-search-cancel-button`, so Chromium paints its own cancel control
inside the field. Measured in the product, before and after typing:

| Probe | Before typing | After typing `adversar` |
|---|---|---|
| `button` elements inside `[role="search"]` | 0 | **0** |
| `svg` elements inside `[role="search"]` | 1 (the magnifier) | 1 (the magnifier) |
| Children of the wrapper | `svg`, `input` | `svg`, `input` |
| Total nodes in the wrapper | 4 | **4** |
| `input[type]` | `search` | `search` |
| `getComputedStyle(input, '::-webkit-search-cancel-button').display` | — | `block` (UA control active) |

The cross is plainly visible in the rendered field, yet **not one DOM node is
added when it appears** — conclusive that it is a UA pseudo-element and not
markup. It therefore cannot inherit any library styling, which is exactly why it
does not match the crosses of `Chip` and `Dialog`: those are library-drawn, this
one is drawn by the browser.

**Not a product addition.** The product passes no `searchOption`, adds no clear
control, and ships no `::-webkit-search-cancel-button` rule (grepped:
`searchOption|search-cancel` has no hit in the top bar path). It passes only
`aria-label`, `placeholder`, `fullWidth`, `value`, `onChange`, `onSubmit`,
`onClear`. The behaviour is entirely the library's.

**Second-order consequence.** The library's `onClear` is wired only to the
Escape key. The UA cross clears the input natively and fires `input`/`change`,
so a controlled consumer stays in sync by luck — but `onClear` never runs, so
any consumer doing more than resetting the value (closing a result panel,
re-running a query) silently misses it when the user clicks the cross rather
than pressing Escape.

**Consistency note.** The component's base already carries an explicit
"UA-default defense" for fonts, borders, padding and the box model. The cancel
button is the same class of problem, missed in the same place.

**Suggested.** Neutralise the UA control
(`[&::-webkit-search-cancel-button]:appearance-none`) and, when the field is
non-empty, render a real clear control composed from the library's own
`IconButton`, invoking `onClear`.

**Removal condition.** `SearchField` composes `IconButton` for its clear
control. **Met** by library PR #100.

---

### Adoption measured — pin `c8a3289`, in the product's top bar, 2026-08-10

The removal condition, verbatim: *"`SearchField` composes `IconButton` for its
clear control."* Measured in the running instance, same field, same browser:

| Probe | Before (`c0d6f07`) | After (`c8a3289`) |
|---|---|---|
| `button` in `[role="search"]`, field empty | 0 | 0 |
| `button` in `[role="search"]`, field filled | **0** | **1** |
| Nodes in the wrapper, filled | **4** (unchanged from empty) | **9** |
| The clear control | no DOM node — UA pseudo-element | `<button aria-label="Clear search" data-search-clear>` |
| Crosses painted in the field | 1 (the browser's) | 1 (the library's) — no double cross |

The decisive line is the node count: the cross used to appear without the
wrapper gaining a single node. It now costs real markup, which is what a
component looks like.

**States now match the rest of the bar**, i.e. they are `IconButton`'s:

| State | Clear cross | Other icon buttons in the bar |
|---|---|---|
| rest | `rgba(0, 0, 0, 0)`, `cursor: pointer` | identical |
| hover | `color(srgb 0.258824 0.792157 1 / 0.1)` | identical |
| keyboard focus | `rgb(7,13,24) 0 0 0 2px` + `rgb(66,202,255) 0 0 0 4px` | identical |

**Behaviour, verified rather than assumed.** The product's field is controlled,
and in controlled mode the library deliberately does not touch the DOM value —
it only calls `onClear`. So the field emptying on click *is* the proof that
`onClear` ran: measured `"adversar"` → `""`, cross gone, focus returned to the
input. The product's handler is `onClear={() => setSearchValue('')}` and does
nothing else — no panel to close, no query to re-run — so nothing is lost.
Escape still clears (`"scenario"` → `""`), and is now vetoable by a consumer's
own `onKeyDown`.

**The second-order consequence is resolved too.** Under the old behaviour the
UA cross cleared the DOM value and a controlled consumer stayed in sync only by
luck, while `onClear` never ran. Both paths now go through the same `clear()`.

---

Raised during: the **Paper pilot** (first container-surface wave in OpenAEV —
`admin/components/lessons` + `components/common/detail/EntityDetailCommon.tsx`),
library pin `35a476849ba72d48cacae2568643f0b5638bc468`. Every number below is
measured on the **installed build** (`dist/`) and on the product's own
components mounted in the real MUI theme — not read from types, meta or
changelog. See `fds-migration/PAPER-GAP-INVENTORY.md` for the site-by-site
inventory these entries summarise. Entries 26-29 blocked the wave: no
conversion was made.

## 26. `Paper` has no `padding` prop, and 24px is nobody's value

**Needed.** An iso-density migration: each converted surface keeps the exact
padding it renders today. Across the 14 sites of this wave that means **0px on
9 sites and 16px on 5** — measured, not estimated.

**Today.** `Paper` hardcodes `p-6` (24px) in its `cva()` base. There is no
`padding` prop. Passing one is not a type error at the call site of a
polymorphic component: `<Paper padding={16}>` renders
`class="… p-6 …" padding="16"` — the value **leaks to the DOM as an
attribute** and changes nothing. Measured padding stays 24px in every case.

**Consequence.** Not a single site of the wave is at 24px, so a mechanical swap
changes the density of 100% of them. Worse, the 7 zero-padding surfaces host
full-bleed content — `List`s with edge-to-edge dividers, one full-width
ApexCharts area chart. At 24px those dividers detach from the edges: it is a
different visual pattern, not a slightly roomier one.

**The documented escape hatch does not close the gap.** `Paper.meta.ts` points
at `className` ("e.g. override the default p-6"). Measured against the shipped
stylesheet, `p-0` (0px), `p-2` (8px), `p-3` (12px), `p-4` (16px) and `p-6`
(24px) exist — but **`p-8` resolves to `0px`: the class is simply not in
`dist/index.css`**, so 32px is unreachable even in hardcoded form. And a
consumer with no Tailwind build of its own (see [#13]) cannot invent one.
Beyond that, re-adding a hardcoded padding class on a library Paper is exactly
what this migration's conformity guard is meant to redden — it is a
compensation, not a capability.

**Ask.** The `padding` prop on the 0 / 8 / 16 / 24 / 32 scale, 24 staying the
default. That scale covers every value measured in this product plus the 32
that no class can express today.

---

## 27. `Paper`'s border is invisible in light mode

**Needed.** The border is what delimits a panel in this product: all 14 sites
render MUI's `variant="outlined"`, i.e. they draw one on purpose. (Worth noting
for cross-product readers: unlike OpenCTI's panels, which render borderless,
OpenAEV's do render a border — the gap here is the colour, not the presence.)

**Today.** `Paper` draws
`border-[color:color-mix(in_srgb,var(--border-elevation-subtle)_10%,transparent)]`
— always on, not disableable. Measured composites and border-vs-surface
contrast:

| mode | product (MUI outlined) | Paper | product ratio | Paper ratio |
|---|---|---|---|---|
| dark | `rgba(255,255,255,0.12)` → `#2a3344` | `rgba(43,79,141,0.1)` → `#101d35` | **1.41:1** | **1.06:1** |
| light | `rgba(0,0,0,0.12)` → `#e0e0e0` | `rgba(228,229,231,0.1)` → `#fcfcfd` | **1.32:1** | **1.03:1** |

**Consequence.** In light mode the border is effectively **not there** — 1.03:1
against its own surface. On the before/after board, converted panels float as
white blocks on the light-grey page with no outline at all. In dark mode the
outline survives but at half the contrast, and shifts from neutral grey to a
dark blue.

This is not a WCAG finding — the surfaces are non-interactive and the library
documents its border as decorative and non-gating, which is a defensible
arbitration. It is a *rendering* gap: what the library treats as decoration is,
in this product, the panel's only delimiter.

**Ask.** Either a border colour that stays perceptible in light mode, or a
supported way to opt out of it. The product cannot neutralise it: `border-*`
utilities are not in the shipped stylesheet either ([#13] again).

---

## 28. `Paper` ignores a customer-configured surface colour

**Needed.** This product's theme is customer-configurable per tenant
(`platform_dark_theme` / `platform_light_theme`, editable from the admin UI).
`background.paper` is one of those fields. A migrated panel must keep following
it, exactly as the top bar had to keep following `background_color` in [#17].

**Today.** `Paper` paints `bg-elevation-default`, resolved from the library's
own token family. Measured with a customer theme
(`paper_color: #3b2450`) passed through `themeDark()` the same way
`AppThemeProvider` does it:

| | measured background |
|---|---|
| product (MUI Paper) | `rgb(59,36,80)` = `#3b2450` — the customer's colour |
| library `Paper` | `rgb(13,23,43)` = `#0d172b` — the Filigran default |

**Consequence.** On any install with a custom paper colour, every converted
panel reverts to Filigran's default, right next to unconverted panels still
wearing the customer's. Contrast between the two surfaces: **1.32:1** — clearly
two different colours side by side, per tenant, silently.

**Why it stayed invisible until now.** On the *default* themes the two are
byte-identical (`#0d172b` dark, `#ffffff` light): the token bridge already
aligned `background.paper` onto `--bg-elevation-default-layer-1`. The gap only
appears once a customer theme exists — which is why this was measured against
one rather than assumed from the default.

**Ask.** This is [#17]'s "Generalisation" paragraph coming true on a second
component, so the answer should be the token-level one that entry already
asked for, not a Paper-specific patch: a first-class, documented hook by which
a consumer supplies a surface colour (`--fds-paper-background`, or a documented
guarantee that the background token may be re-declared per element).

---

## 29. A gradient-backed surface has no expressible equivalent

**Needed.** `DetailHero` — the hero header of **every** entity detail page in
this product (21 screens, 21 files) — is a surface with two properties the
library's `Paper` cannot reproduce:

1. an accent gradient following the theme's primary colour, therefore the
   customer's: `linear-gradient(135deg, alpha(primary,0.08), transparent 60%)`
   — measured `rgba(66,202,255,0.08)` dark, `rgba(0,21,168,0.08)` light,
   `rgba(255,138,61,0.08)` on a customer theme;
2. **a transparent background**: the `sx` `background` shorthand zeroes the
   paper fill (measured `rgba(0,0,0,0)`), so the page's own two-stop gradient
   shows through the hero.

**Today.** `Paper` paints an opaque `bg-elevation-default` and exposes no
gradient or transparency affordance; `bg-gradient-*` utilities are not in the
shipped stylesheet, so the product cannot supply one by class either.

**Consequence.** A mechanical swap loses both at once — the accent *and* the
see-through. Even a Paper that grew a gradient prop would still need the
opt-out on its own fill, so the two are worth answering together.

**Ask.** Not necessarily a gradient prop. The question to settle is whether a
`Paper` can ever be transparent / consumer-painted at all, or whether a hero
surface is simply a different component. Either answer unblocks this site; no
answer means it stays on MUI while everything around it moves.

---

## 30. `title` and `action` silently become HTML attributes

**Not a blocker** — the wave's arbitration already says the product keeps its
own header above the surface when the library has no slot for it. Recorded
because of the failure mode, not the missing feature.

**Today.** `Paper` has no `title` / `action` props. Being polymorphic, it
spreads unknown props onto the rendered element. Measured:

- `<Paper title="Section">` → `title="Section"` on the `<div>`, i.e. a **browser
  tooltip on the entire panel**. No error, no warning, no type complaint, and a
  plausible-looking render.
- `<Paper action={<button/>}>` → `action="[object Object]"` on a `<div>`, an
  invalid attribute (React does warn on this one).

**Consequence.** `title` is the dangerous half: an agent migrating a titled
panel will reach for it by name, get no feedback of any kind, and ship a panel
that shows a tooltip instead of a heading. 106 of this wave's 127 usages are
titled panels, so the temptation is not hypothetical.

**Ask.** Either the props, or a line in `Paper.meta.ts` / the docs stating that
`title` is not a slot and lands on the native attribute. The cheap half of this
(the documentation line) is worth doing even if the props never come.

---

Raised during: the **Paper pilot, wave 1 conversion** (after the phase-0 bump to
pin `2e774922e1c667ee3a1e2424b5b4014dfd1a4f55`, carrying #121 and #123).
Entries 26-29 are **closed by that bump**; what follows is what the conversion
itself surfaced.

**Closure of 26-29, re-measured on the new installed build, not assumed:**

- **#26 padding — CLOSED.** `padding={0|8|16|24|32}` renders `p-0/p-2/p-4/p-6/p-8`,
  and all five now exist in the shipped `dist/index.css` (`p-8` used to resolve
  to `0px`). Default 24. The 13 converted sites render byte-identical padding to
  their pre-migration state.
- **#27 border — CLOSED AS ARBITRATED, not as "satisfied".** What the entry
  asked for exists: the border is now its own per-layer token
  (`--border-elevation-subtle-soft`), themeable by a host. The **opacity is
  Sandy's decision** (15% since #125), and the measured consequence is that the
  converted panels' border is **weaker than before migration**, not equal to it:

  | | before migration (MUI) | after, at pin `a22b188` |
  |---|---|---|
  | dark | 1.42:1 | **1.09:1** |
  | light | 1.32:1 | **1.15:1** |

  The invisible 1.03:1 that opened this entry **is gone**. Parity with MUI's
  `divider` is explicitly **no longer an objective** — the library's border is
  its own design decision, not a reproduction of the product's. Full table
  across all four elevations and both themes: PAPER-GAP-INVENTORY §12.
- **#28 host theme — CLOSED, and the contract is the important part.** Measured
  both directions in a real browser: re-declaring `--bg-elevation-default-layer-1`
  on `:root` repaints the surface to the customer's colour, and re-declaring the
  semantic alias `--bg-elevation-default` at the same time does **nothing**.
  Wiring `paper_color` → `--bg-elevation-default-layer-1` was a three-line change
  in this product's `AppThemeProvider`.
- **#29 DetailHero — CLOSED as a Paper question, 2026-08-15.** Sandy's decision:
  `DetailHero` becomes **its own component** (accent gradient + transparent
  fill). It therefore leaves the Paper waves **permanently** — this is not "a
  site still to migrate", and no future wave should pick it up. What the entry
  asked ("can a Paper ever be transparent or consumer-painted") is answered by
  not asking Paper to be either: the need gets a component of its own. The
  product keeps it on MUI until that component exists.

### Status, pin `a22b188` — #30 is CLOSED

`title` and `action` became real props at pin `2e77492`, so the two documented
breakages this entry described are gone:

- `<Paper title="X">` no longer lands on the native `title` attribute (no more
  browser tooltip over the whole panel) — it renders a header row above the
  surface, outside its border and padding;
- `<Paper action={node}>` no longer serialises to `action="[object Object]"` on
  a `<div>` — it renders in a right-aligned slot.

Measured on the installed build, not read from the changelog. The product now
**adopts** both props (see PAPER-GAP-INVENTORY §13): the visual change they
bring is deliberate, the library being the reference.

---

## 31. The `imported-from-library` guard cannot express a mixed file

**Context.** `check-fds-conformity.mjs` now ships two named guards, and both are
exactly what this migration needed — this entry is a limit found by using them,
not a complaint about them.

**Needed.** `EntityDetailCommon.tsx` holds four surfaces. Three are migrated
(`Section`, `InformationGrid`, `SectionBlock`); the fourth, `DetailHero`, stays
on MUI by arbitration. So the file legitimately imports **both** Papers, and
will keep doing so until DetailHero's gap is answered.

**Today.** `imported-from-library` is file-granular: it fails as soon as the
file contains `import { … Paper … } from "@mui/material"`. An alias
(`Paper as MuiPaper`) still matches, correctly — the regex reads the imported
name, not the local one.

**Consequence.** Three ways out, and two of them are bad:

- arm the guard → a permanent red on a file that is in the state its
  arbitration says it should be in;
- switch the MUI import to a deep default import (`@mui/material/Paper`) purely
  because the guard's regex does not look there → the gate reports green about
  something it did not verify, which is worse than the red;
- **what this product did**: split the declaration in two, arm both guards on
  the six fully-migrated files, and arm only `no-hardcoded-padding` on this one,
  with the reason recorded in `migration-state.json` and a note to re-arm the
  day DetailHero moves.

**Ask.** A way to say "this file is partially migrated, and here is the symbol
that is allowed to remain" — e.g. an `allowMuiFor: ["DetailHero"]` field the
guard checks the enclosing component of, or simply a documented
`guards`-per-file granularity so the third option above is the *supported*
answer rather than a workaround a product invented. The middle option
(dodging the regex) is the one worth making impossible.

---

## 32. An off-scale `padding` renders 0px, and half this wave's call sites are untyped

**Measured on the installed build at `2e77492`**: `<Paper padding={12}>` — a
value outside the 0/8/16/24/32 scale — renders **no padding class at all**:

```
<div class="box-border bg-elevation-default … border-elevation-subtle-soft layer-1">X</div>
```

No `p-*`, so the surface computes to **0px**, silently. Not 24 (the default),
not the nearest step, not a warning: zero.

**Why this is not just a typing footnote.** TypeScript rejects `padding={12}`,
so in a `.tsx` call site the trap is closed at compile time. But **4 of the 7
files this wave converted are `.jsx`** (`LessonsObjectives.jsx`,
`CrysisIntensity.jsx`, and both `LessonsCategories.jsx`), and this product's
tsconfig sets `allowJs` **without** `checkJs` — so those files get no prop
checking whatsoever. The same is true of any product with legacy JSX, which is
most of them at this stage of the migration. A dynamic value
(`padding={someTheme.spacing}`) escapes the types even in `.tsx`.

**The conformity guard does not catch it either**, and correctly so:
`no-hardcoded-padding` looks for padding re-declared through `className`/`sx`/
`style`, which is a different mistake. An off-scale *prop value* passes every
gate and renders a panel with no padding.

**Ask.** Make the runtime say something. The library already has the precedent
and the rule for exactly this shape — AGENTS.md, "Prop contract violations —
dev-only warning, never throw": a `console.warn` behind
`process.env.NODE_ENV !== "production"`, naming the component, the offending
prop and the effective fallback. Falling back to the default 24 rather than 0
would also be defensible, but the warning matters more than the fallback: 0px
is at least visible, whereas a silent 24 on a site that asked for 12 would just
move the problem. Whichever is chosen, it belongs in `Paper.meta.ts` too.


---

## 33. The product-side inventory of a token rename was incomplete — and two of the misses were silent

**Not a library defect. A method finding, recorded here because the next
product bump will hit it.**

When #121 renamed 17 alpha tokens, this product's migration state pointed at
one thing: the generated bridge (`fds-tokens.generated.ts`) and the two theme
files declared as `wiredFiles`. Regenerating the bridge and fixing the theme
files felt like the whole job. It was not. **Three product references were
dead after the bump, and only one of them said so:**

| reference | where | how it failed |
|---|---|---|
| `FDS.colors.*['--color-feedback-info-secondary-transparency']` | `ThemeDark.ts`, `ThemeLight.ts` | **TypeScript error** — loud, caught by `tsc` |
| `var(--color-filigran-brand-primary-transparency)` | `TopBarIconLink.tsx` | **silent** — a dangling `var()` in a plain string, no error anywhere |
| `'bg-filigran-ia-secondary-transparency'` | `AskArianeButton.tsx` | **silent** — a utility class that no longer exists in the shipped sheet, so it resolves to nothing |

The two silent ones are the point. Neither `tsc`, nor eslint, nor the
conformity gate, nor the build says a word: the class simply stops matching and
the custom property simply stops resolving. The only signal is visual, on a
state (a selected top-bar link, an open Ariane button) that no screenshot in
this wave's checkpoint even covers.

**What the inventory has to look for at the next bump — OpenCTI included.**
Regenerating the bridge is necessary and not sufficient. Grep the product
source, not just the wired files, for **both** of these shapes:

```
var(--<token>)          # in .ts/.tsx string literals, style objects, CSS files
bg-/text-/border-<name> # library utility classes written as string literals
```

and cross-check every hit against the tokens and utilities actually present in
the **installed** `dist/index.css` — the shipped sheet, not the source
`theme.css`. `migration-state.json`'s `wiredFiles` describes the *theme*
wiring; these two shapes live in ordinary component files, outside it by
design, and that is exactly why they were missed here.

**Ask (small, and optional).** The renames are documented in the token diff, so
a product can already do this. What would make it mechanical is a machine-
readable rename map in the release — old name → new name — so a consumer can
grep for the old names rather than having to notice their absence.


---

## 34. A product reset can silently disarm a library focus indicator

**Found by accident, worth more than the accident.** When this pilot's bench was
corrected to load the app's COMPLETE stylesheet stack (it had been loading 2 of
the 5 sheets `index.tsx` imports), exactly one measured value moved across the
navbar's ten states:

```
outline:  3px none   →   0px none
```

The cause is one rule in this product's own global CSS
(`src/static/css/index.css`, line 5):

```css
:focus { outline: 0; }
```

It wins over the library sheet, and it applies to **every** focusable element in
the app.

**Why nothing broke.** #123 had just replaced the navbar's focus ring with an
**inset border**. A border is not an outline, so the product rule cannot touch
it. The focus indicator survives — by construction, not by luck.

**Why it matters anyway.** The previous shape — `focus-visible:ring-2` — is
`outline`-based in Tailwind, and the library's own accessibility contract
mandates exactly that pattern for every interactive component:

> `focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus`

So **every other library component this product adopts is one `:focus { outline: 0 }`
away from having no visible focus indicator at all** — a WCAG 2.4.7 failure that
is invisible in the library's own test suite, invisible in the docs site, and
invisible in any bench that does not load the host's global CSS. Nothing in the
library or in the product would report it; only a measurement in the host's real
cascade shows it.

**Two asks, and the first is cheap:**

1. **Document the host prerequisite.** The consumer section already lists what a
   host must do (theme class, fonts, no preflight). Add this: a host must not
   neutralise `outline` globally, or must exempt the library's focus pattern —
   and say which rule shape the library relies on, so a host can check.
2. **Consider the sturdier indicator.** #123's inset border is immune to the
   most common reset in the wild. If that is a deliberate robustness property
   and not only a Figma alignment, it is worth stating as such — and worth
   asking whether the ring-based pattern should move the same way for the other
   components.

**Product-side note for OpenAEV:** the rule is old, broad, and not this
migration's to remove. Flagged here rather than deleted, because deleting it
changes focus rendering across the entire application — a decision, not a fix.

---

## 35. A clickable card must be a real link, not a click handler

**Raised while classifying the 81 container surfaces still on MUI** (Paper pilot,
wave 2 preparation). Recorded now, before `Card` is designed, because it is a
behaviour to preserve — not an implementation detail to rediscover afterwards.

**What the product does today.** Three surfaces are clickable cards, and all
three carry the same comment in their source:

```tsx
// Real router link (not a JS navigate) so ctrl/cmd+click opens a new tab.
component={Link}
to={`/admin/reporting/${reporting.reporting_id}`}
```

| site | screen |
|---|---|
| `SecurityPlatformCard.tsx:30` | Security platforms |
| `ReportingCard.tsx:34` | Reporting |
| `CustomDashboardCard.tsx:28` | Custom dashboards |

The whole surface is the control, and it is a **real `<a href>`** — deliberately,
not a `<div onClick={navigate}>`. That single choice is what makes ⌘-click and
middle-click open the card in a new tab, what puts the destination in the
browser's status bar on hover, and what lets "copy link address" work. A JS
click handler silently loses all three, and nothing in a test suite notices.

**Why this belongs to the library now.** This is the same shape as
[#1](#1-navbaritem-has-no-link-destination-href--to): `NavbarItem` had to grow
`href`/`to` for exactly this reason, and until it did, the product had to reach
for `asChild` and re-declare the row's internals by hand. A `Card` that only
offers `onClick` will push products into the same corner — either a `div` that
breaks ⌘-click, or an `asChild` escape that forces them to re-declare the card's
anatomy and drift from the library's own styling.

**Ask.** When `Card` is designed, give it a link destination as a first-class
prop (`href` / `to`, or a documented polymorphic `as`), and state in its meta
that a clickable card renders an `<a>` by default. Two properties worth pinning
in its tests, because both are invisible to a snapshot: the rendered element is
an anchor with a real `href`, and a modified click is not intercepted.

**Product-side status.** Nothing is forced meanwhile: these three cards, plus
`Logs.jsx:211` (a `ButtonBase` surface) and `ReportingForm.tsx:511` (a choice
card carrying `onClick` and a `selected` state), are all held out of the Paper
waves under the "clickable card" motif, waiting for `Card`. They are listed as
class (g) of the wave-2 decision sheet.

---

## 36. `Paper` renders its header on the PRESENCE of the prop, not on its value

**Measured on the installed build at pin `a22b188`, while writing the render
guard for the three converted wrappers** — not read from the source.

```tsx
<Paper title="">body</Paper>
```

renders the full header row — a 24px band, empty — above the surface. The
library keys the header on whether the prop was passed, not on whether it
carries anything. `title={undefined}` correctly renders no header; `title=""`,
`title={null}` and any expression that resolves to an empty string all render a
blank band.

**Why it matters beyond one product.** A wrapper that always forwards `title`
makes the header unconditional for every one of its call sites. OpenAEV's three
wrappers do exactly that, so a call site whose title resolves to `''` would show
a blank 24px band instead of a tight panel — a silent, purely visual defect.

Checked here rather than assumed: **no such call site exists in OpenAEV today**.
Every `?? ''` title in the product belongs to `Drawer` or `Tooltip`, never to a
`Paper` wrapper. A regression test now pins the behaviour as a tripwire.

**OpenCTI is the exposed one.** Its panel titles frequently come from data —
entity names, external references, observable values — where an empty string is
a normal runtime value, not a coding mistake. There, the blank band is not a
hypothetical: it is what an unnamed entity will render.

**Nothing warns.** Not TypeScript (`string` accepts `""`), not a lint rule, not
the conformity gate, not a snapshot — the DOM is *correct*, it simply contains
an empty row. Only a human looking at the screen sees it.

**Ask.** Pick one and document it, either is defensible:

- **treat an empty title as absent** — render no header when the resolved title
  is empty and no `action` is set, which is what a consumer intuitively expects;
- **or keep the current behaviour and say so** in `Paper.meta.ts`, so a product
  knows it must guard its own call sites.

What should not stay is the current silence: the behaviour is reasonable, it is
just undiscoverable until it renders wrong on someone's screen.

---

## 37. A truncated `Paper` title offers no way to read the full text

**Measured on the installed build at pin `a22b188`**, with real translations at
the narrowest panel width this product can produce.

`Paper`'s header truncates its title — `<div class="min-w-0 truncate">` — which
is the right behaviour for a fixed 24px row. What it does not do is give a
sighted user any way back to the full string:

| checked on the truncated element | result |
|---|---|
| native `title` attribute | **absent** |
| `aria-label` / `aria-labelledby` | **absent** |
| tooltip trigger (Radix or otherwise) | **absent** |
| full text present in the DOM | **yes**, as `textContent` |

**The important nuance, so the severity is not overstated.** Because the full
string stays in the DOM, a screen reader announces it in full. This is **not**
an assistive-technology failure. It is a loss for the sighted user who sees
`Distribución de la puntuación total esperada por tipo de iny…` and has no
hover, no focus and no click that reveals the rest.

**How often, measured rather than feared.** Across the 68 distinct titles this
product passes to its three wrappers, in nine locales: median 13 characters in
English, 66 at worst in Spanish, and 15 titles whose translation grows by 60% or
more. At the narrowest track (`minmax(340px, 1fr)`, with an action in the row)
exactly **one** title truncates today. So this is rare here — and it is rare
*because* the component happens to fit, not because anything guarantees it. A
product with data-driven titles (OpenCTI) has no such luck.

**Ask.** Have the header expose the full title when, and only when, it actually
truncates — the standard shape is a `title` attribute set from a width
comparison, or the library's own `Tooltip` on the trigger. Doing it in the
library is what makes it conditional: only the component knows whether its own
text overflowed.

**What a product can do meanwhile, and why it is not free.** A consumer can pass
a node instead of a string — `title={<span title={text}>{text}</span>}` — and
the native tooltip then works; verified, the attribute survives into the
library's truncating div. But a consumer cannot know whether truncation
happened, so the tooltip appears on **every** header, truncated or not: 106
panels in this product would grow a hover tooltip to fix one. Not applied here
for that reason; recorded so the trade-off is explicit rather than rediscovered.


## 38. `Paper`'s `padding` cannot express a per-side value

**Measured on the installed build at pin `a22b188`**, against the real product
sites that carry an asymmetric padding today.

`padding` takes one value from the `0 | 8 | 16 | 24 | 32` scale and applies it to
all four sides. Seventeen container surfaces in OpenAEV cannot express their padding with the
prop as it stands. Fourteen pad their sides differently:

| value in the product | sites | what it does |
| --- | --- | --- |
| `0 20px 0 0` | 9 — the simulation overview charts | right gutter only, so a chart's axis labels clear the panel edge while the plot itself runs full-bleed |
| `20px 20px 0 20px` | 2 — the simulation e-mail panels | no bottom padding: the last row of the list sits flush on the border |
| `10px 15px 20px 15px` | 2 — the lessons player and the lessons preview | a heavier bottom than top |
| `6px 12px` | 1 — the attack-path header badge | asymmetric AND off-scale on both axes (`theme.spacing(0.75, 1.5)`) |

**This is not a rounding question.** The other off-scale paddings we met (12, 15,
20, 48) each have a nearest neighbour on the scale, and the product picked one.
A per-side value has no nearest neighbour: any single value changes the layout
on at least one side.

**Why the product will not work around it.** The obvious workaround is
`padding={0}` plus an inner wrapper carrying the asymmetric padding. It renders
identically, and it adds one technical level inside twelve files for no reason
other than a prop signature. That is exactly the debt this migration exists to
remove, so these twelve stay on MUI until the library can express it.

**Three more forms, found while converting the hand-painted surfaces.** The
request is wider than asymmetry:

| form | site | what it does |
| --- | --- | --- |
| **responsive** — `padding: { xs: 2, md: 3 }` | threat-arsenal hero | 16px on a narrow screen, 24px from the medium breakpoint up |
| **logical** — `paddingBlock: 8` + `paddingInline: 4` | threat-arsenal empty state | 64px vertical, 32px horizontal, expressed on the block/inline axes rather than per physical side |
| **layered background** — `padding: 6` with a four-layer `backgroundImage` | report cover module | the surface paints a 28px grid pattern over its colour; padding is only half the story |

The logical form matters beyond padding: it is the writing-mode-aware way to
express spacing, and a design system that only offers `top/right/bottom/left`
pushes consumers back to physical sides.

**Ask.** Let `padding` accept, alongside the scalar:

- a **per-side** form — an object (`{ top, right, bottom, left }`) or a tuple;
- a **logical** form — `{ block, inline }`;
- a **responsive** form — a value per breakpoint, as the host UI library already
  allows.

Each side still constrained to the scale. On the OpenAEV sites: the per-side
form covers 13 of the 17 blocked surfaces, logical covers 1, responsive covers
1; the remaining 2 also need a value off the scale and will round — including
`theme.spacing(0.75, 1.5)`, which is off-scale on both axes at once.

The layered background is a separate question and not part of this request —
recorded here only so the two are not confused when the padding work is scoped.

**Not urgent, but it caps the wave.** Seventeen surfaces out of the 130 in the
OpenAEV container perimeter — 13%, and they are the only ones with no path
forward. Everything else either converts or is out of scope by design.
