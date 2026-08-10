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
  the one change the product actually wants.

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

**Needed.** A search cluster whose window is `min-width: 550px`,
`width: 50%`, `max-width: 680px`.

**Today.** `HeaderGroup grow` caps at Figma's 400px — **below this product's
minimum**, not merely tighter than its preference. `grow="unbounded"` exists
precisely for this and was, per the RFC, added after measuring this bar. The
capability is correct and the pilot used it as intended.

**The gap is that nothing steers you to it.** `grow={true}` is the obvious
choice from the prop name, it type-checks, and it renders a plausible-looking
bar — just a silently narrower search field. Nothing fails.

**Suggested.** Nothing in the component API. In the docs/meta: state the 400px
cap and the `"unbounded"` escape on the `grow` prop description itself, where an
implementer reads it, rather than only in the RFC's rationale.

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
border token; `Badge` and `Popover` stay MUI.

**Suggested.** A general-purpose `Separator` is nearly free and would remove
the last hand-painted rule. `Badge` and `Popover` are real components and
should be sized as such — but they should be *named*, so products stop
discovering the gap one pilot at a time.

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
control. Until then, no local patch: a product-side cross would be one more
hand-rolled control, which is precisely what the scope rule forbids.

**Status.** Open — waiting on a library fix and a pin bump. Nothing changed
product-side.
