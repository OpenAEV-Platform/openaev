---
applyTo: "openaev-front/**/*.ts,openaev-front/**/*.tsx,openaev-front/**/*.js,openaev-front/**/*.jsx"
description: "Frontend React/TypeScript conventions: components, Filigran Design System, MUI, forms, permissions, i18n, Redux"
---

# Frontend Conventions

## File Structure

- Use `snake_case` for folder names, one folder per feature, one file per behavior
- Split by behavior: `{feature}-action.ts`, `{feature}-helper.d.ts`, `{feature}-schema.ts`
- Pages in `src/admin/components/{section}/{feature}/`

## TypeScript

- Use auto-generated `api-types.d.ts` — prefer over manual types
- Migrate `.js`/`.jsx` → `.ts`/`.tsx` when touching files
- TypeScript strict mode, annotate API responses with generated types
- No `any` types, no `@ts-ignore` without explanatory comment
- Props defined via explicit `interface Props` (not inline)

## Design System & Layout

UI components come from the **Filigran Design System** (`@filigran/design-system`). MUI is legacy: it stays only where the library has no equivalent yet.
Docs: <https://silver-doodle-mnyv84e.pages.github.io> (machine-readable: `/llms-full.txt`).

- **Library first** — wherever the library ships a component, use it (Button, IconButton, Tooltip, Chip, Badge, Paper, Text, Tabs, Menu, Select, Combobox, Input, Textarea, SearchField, Checkbox, Radio, Switch, DatePicker, TimePicker, Spinner, ProgressBar, Breadcrumbs, Header/Navbar…). The enforced list is `fds-migration/mui-regression-policy.generated.json` (`enforced`) — `Typography` → `Text`, `Autocomplete` → `Combobox`, `CircularProgress` → `Spinner`, `LinearProgress` → `ProgressBar`, `MenuItem` → `Menu`. Existing MUI usages of these are legacy: don't add new ones
- **Still MUI** (no library equivalent yet): `Dialog`, `Drawer`, `Popover`, `Accordion`, `Alert`, `Card`, `Avatar`, `Divider`, `TablePagination`, text-labelled `ToggleButtonGroup`, split buttons, filter chips. Icon glyphs (`@mui/icons-material`) stay MUI everywhere
- **Missing component → flag, never fork** — no local look-alike of a library component. Keep the MUI site with a `// fds:keep-mui <reason>` comment on (or right above) the import line; the reason is mandatory
- **Use the whole library component** — a library container with MUI controls inside is not an adoption (MUI keeps its own focus/hover/selected states)
- **No MUI for layout** — use native HTML (`div`, `section`, `header`)
- Styling: `sx` on MUI components, `style` on library components (they accept no `sx`) — never `makeStyles` / `withStyles` in new code
- **`sx` → `style` changes units**: a bare number in `sx` is `theme.spacing()` units (`p: 2` = 16px, `gap: 1.5` = 12px), in `style` it is pixels. Convert every spacing value (padding, margin, gap, top/left…) explicitly when moving it
- **No arbitrary Tailwind utilities** — the app has no Tailwind build; `@filigran/design-system/dist/index.css` only ships the utilities the library itself uses. A class absent from that sheet fails silently (no build, lint or console error) — check it is in the installed `dist/index.css` before relying on it. Library token classes are fine; product geometry goes in `style` / `theme.spacing()`
- **Library CSS is layered and loses to unlayered product CSS** (`body a` from CssBaseline, global classes like `.paper`, `:focus { outline: 0 }`) whatever the specificity — when a converted element also carries a global class, the class wins over the library prop
- **Never edit `src/components/fds-tokens.generated.ts`** (or its `.meta.json`) — regenerate it from the library (`pnpm generate:mui-bridge`). Never invent a token value: a colour with no token is a gap to report, not to improvise

### Colours & tokens

- Colours come from the theme (`theme.palette.*`, wired to the token bridge) or from library tokens (`var(--…)`) — no hardcoded `#hex` / `rgba()`
- **Never `alpha()` / `darken()` / `lighten()` on a value that may be a token** — MUI parses it in JS and throws on `var(--…)`, crashing the component. Use `tint(colour, percent)` from `utils/tint.ts` (CSS `color-mix`). `alpha()` stays fine on a value that is a hex by construction (theme palette entry, user branding colour). Guarded by `__tests__/utils/tokenColoursNotInAlpha.test.ts`
- Token names: write to the per-layer **base** (`--token-layer-N`), read the **alias** (`--token`, scoped by `.layer-N`), never a diluted `-transparency-NN` variant. An unresolvable `var()` invalidates the whole declaration silently
- Floating surfaces hosting forms (drawers, dialogs) use `fdsLayerClass(SURFACE_LAYER)` + `layerInputVars` from `utils/fdsLayer.ts` so library inputs stay visible on their background

### Library components — rules that are easy to get wrong

- **Read the component signature before swapping** — `icon`, `startIcon`, `label` and `children` are not interchangeable (e.g. `IconButton` takes `icon={<Add />}`; a child is overwritten and the button renders empty, silently in `.jsx`). Name the variant/priority explicitly: defaults are not neutral (`iconButtonVariants({})` is a filled primary button)
- **Button**: `contained` → `priority="primary"` (default), `outlined` → `"secondary"`, `text` → `"tertiary"`; `color="error|warning"` → `variant="destructive"`; EE / gradient → `variant="highlight"`; AI actions → `variant="ia"`. Dialog/drawer footers: affirmative (Create/Update/Confirm) → primary, Cancel → `priority="secondary"`, irreversible confirm (Delete) → `variant="destructive"`. A button that submits says `type="submit"`
- **Sizes**: default (36px) in page/list/detail headers, dialog and drawer footers/headers, bulk toolbars; `size="sm"` (24px) in list rows, forms, cards and `Paper` header rows (any control in a 24px header row must be `sm` — `ButtonCreate` has `size="sm"` for that). MUI icons inside a library `Button` get `fontSize="small"`
- **Navigation is a real link** — a button, chip or card that navigates wraps the router `Link` / `<a>` (`asChild`, icon inside the anchor since `startIcon` is ignored under `asChild`; chips use `chipLink.ts`), never an `onClick={navigate}` (keeps ⌘-click, new tab, copy link)
- **Accessible names**: `IconButton` always gets an `aria-label` (a `Tooltip` does not name it). `Badge` wraps the control (not the icon) and dots carry `accessibleText`
- **Tooltip**: compound `Tooltip` / `TooltipTrigger` / `TooltipContent`; the `TooltipProvider` is mounted once in `index.tsx`
- **Chip**: tone via `severity` (status ladders: `statusSeverity` / `colorStyleSeverity` in `utils/statusUtils.ts`); `color` accepts a hex only (data-driven colours); `onDelete` needs a `deleteLabel`; a chip in a list cell gets `maxWidth: '100%'` so the library truncates it
- **Tabs**: routed bars are `TabsTrigger asChild` around the `Link` with `aria-current="page"` and `panels="external"`; values are strings; `TabsContent forceMount` stays visible, so the caller sets `hidden` on inactive panels
- **Paper**: `padding` takes only `0|8|16|24|32` (anything else renders 0px — untyped in `.jsx`); use the `title` / `action` slots; pass `title={undefined}`, never `''` (an empty string renders an empty header band). Only first-level page containers become `Paper` — never a surface inside a tooltip, drawer, popover, menu or dialog. Converting a hand-painted `Box` surface means changing the **tag** (`<Box sx>` → `<Paper style>`), not just removing its `sx`. A `Paper` with a title inside a flex container needs a `gridTemplateColumns: 'minmax(0, 1fr)'` parent to stretch and truncate
- **Typography copied from the library**: use the composite class whole (`content-compact text-default-secondary`, see `LibHeaderRow.tsx`), never its decomposed utilities (they lose the weight)
- **Mixed file**: when a file keeps a MUI component next to its library counterpart, alias the library import (`import { Paper as FdsPaper } from '@filigran/design-system'`) and rename the closing tag too. Prefer converting a file whole

### Workaround comments

- One line per site, prose elsewhere: `// FDS-WORKAROUND: <summary> — remove when <condition> — see <upstream issue>`
- Only for a real library gap with a removal condition; product behaviour that is nobody's debt gets a plain factual comment

## Forms

- Zod for validation, React Hook Form for management
- Atomic form fields: `TextFieldController`, `SelectFieldController`, etc. (they render the library fields — `TextFieldFds` binds `Input` / `Textarea`)
- Field `name` = JSON property name from API
- A form hosting a `required` library field sets `noValidate` — the library sets the native `required` attribute, and without it the browser blocks submit before the Zod message shows
- Library field labels only (`SelectLabel`, `ComboboxLabel`) — never a MUI `InputLabel` next to a library field. `Select` renders no wrapper: put label + trigger in a column container when the parent is a row/grid

## Permissions (CASL)

- Capability: `ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT)`
- Grant: `ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, resourceId)`
- Create/Edit: wrap with `<Can I={ACTIONS.MANAGE} a={SUBJECTS.X}>`
- Delete: `ability.can()` in popover entries
- New subject → add in `src/utils/permissions/types.ts`

## i18n

- Call `t()` as early as possible — pass translated strings, not raw keys
- Null-check backend strings before `t()` (crashes on null/undefined)
- Keys = English text: `t('Tenant name')`

## Data Loading & State

- **Paginated lists** (new pattern): `useQueryableWithLocalStorage` + `PaginationComponentV2` + `SortHeadersComponentV2`
- **Feature hook**: `use{Feature}s.ts` with `useState` + `useCallback` for local state (add/update/remove)
- **Actions**: simple API calls in `actions/{feature}/{feature}-actions.ts` using `simpleCall` / `simplePostCall`