---
applyTo: "openaev-front/**/*.ts,openaev-front/**/*.tsx,openaev-front/**/*.js,openaev-front/**/*.jsx"
description: "Frontend React/TypeScript conventions: components, Filigran Design System, MUI, forms, permissions, i18n, data loading (TanStack Query, legacy Redux)"
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
- Prefer a default export for a module's main component; helpers, constants and hooks modules may use named exports
- Never export a mutable binding (`export let` / `export var`): export a `const`, or a getter/setter function (enforced by `custom-rules/no-mutable-exports`)
- No Ramda: write native JavaScript (array methods, spread, optional chaining). Ramda is being removed; the files that still import it are listed in `legacyRamdaFiles` in `eslint.config.js`. Never add a file to that list: a cleaned file leaves it

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

- **Server data** (new pattern): TanStack Query, with its defaults (stale at once, refetch on mount, focus and reconnect, garbage collected after 5 min); override them per query only for a reason, and never raise `staleTime` because a query is live (the stream misses some changes, e.g. lost permissions). The client only adds: no retry on a 4xx, one toast per failure streak. Redux (`useDataLoader` + `useHelper`) stays for existing screens only; a migrated screen never reads the same data from both (`AssetDetail` reads the asset from a query, and the users and tags it shows still from Redux)
- **Query and mutation functions**: new plain calls in the feature's action file (`actions/{feature}/{feature}-action.ts`, or the existing `-actions.ts`) that resolve to the data and never dispatch, e.g. `simpleCall(uri, { signal }, false).then(response => response.data)`. Never `dispatch(thunk)`: the `*Referential` thunks resolve even on an HTTP error and write to Redux. A `queryFn` passes TanStack's `signal` and turns the toast off (the query cache shows one per failure streak, none for a 404 or a 401); a `mutationFn` keeps the default toast
- **Query options**: one `actions/{feature}/{feature}-queries.ts` per feature, with a key factory (`{feature}Keys`) whose keys start with the entity type and carry every parameter of the query, and `queryOptions` (`{feature}Queries.list(filters)`, `.detail(id)`). Never mutate the query's `data` (sort in `select`, with a stable function) nor copy it into state. Reference: `actions/assets/asset-queries.ts`, used by `admin/components/assets/asset/AssetDetail.tsx`
- **Live queries**: every query that a change can make stale, the user's own mutations included, sets `meta: liveMeta<TData>({ refs, collections, watch, throttleMs })` (`utils/query/live.ts`):
  - `refs(data)`: the entities its data shows, embedded ones included (a change on one refetches it)
  - `collections`: the types it lists (a create or delete of that type refetches it)
  - `watch`: the types any change of which can change the result although the query does not show the entity (a count by status, a list filtered or sorted on a field that changes)
  - `throttleMs`: the minimum delay between two refetches (1 s by default; raise it for high-frequency types)

  Type names come from `ENTITY_TYPES` (`utils/query/entityChange.ts`, which explains how a name is derived; add a missing one before using it). A query without `meta.live` is never refetched by a change: only TanStack's defaults and a change of the user's rights refresh it. A change also updates the `parents` it lists (an agent → its asset): mutations list them in their changes, and the stream adapter derives them in `PARENTS_OF` (`utils/stream/streamClient.ts`)
- **Invalidation only**: never write to the cache (`setQueryData`, `removeQueries`, optimistic updates) and never call `invalidateQueries` directly (a team rule: `utils/query/liveRouter.ts` is its only caller, so the stream and mutations refresh the same way). A mutation uses `useLiveMutation({ mutationFn, changes })` and describes what changed; the queries showing it are refetched before the mutation resolves (and once more, throttled, when the stream echoes the change):
  ```ts
  // tag-action.ts
  export const updateTagCall = (tagId: string, input: TagUpdateInput): Promise<Tag> =>
    simplePutCall(`/api/tags/${tagId}`, input).then(response => response.data);
  export const deleteTagCall = (tagId: string) => simpleDelCall(`/api/tags/${tagId}`);
  // in the component
  const update = useLiveMutation({
    mutationFn: ({ tagId, input }: { tagId: string; input: TagUpdateInput }) => updateTagCall(tagId, input),
    changes: tag => [{ type: ENTITY_TYPES.TAGS, id: tag.tag_id, action: 'update' }],
  });
  const remove = useLiveMutation({
    mutationFn: (tagId: string) => deleteTagCall(tagId),
    changes: (_result, tagId) => [{ type: ENTITY_TYPES.TAGS, id: tagId, action: 'delete' }],
  });
  ```
  `useApplyChanges()` describes changes made outside a `useLiveMutation` (a legacy drawer's `onUpdate`, an import); pass `{ immediate: true }` to refetch at once and wait for it, otherwise the changes are batched and throttled like the stream's
- **States**: render `isPending` (first load), then the error only when there is no `data` or the server refused the request (`isClientError(error)`: deleted, forbidden); otherwise render `data`: a failed refetch keeps the previous data on screen, and the query cache already shows a toast. A 404 on a detail is the deleted state (`isNotFoundError(error)`). Both helpers come from `utils/query/httpErrors.ts`; errors are typed `unknown` (the API client rejects with `{ status, ...body }` or `false`)
- **Background tabs**: a change received while the tab is hidden only marks the query stale, and TanStack refetches it when the tab is shown again: a live query never turns `refetchOnWindowFocus` off
- **Paginated lists**: on queries, `usePaginatedQuery(storageKey, initInput, {feature}Queries.list)` (`components/common/queryable/usePaginatedQuery.ts`) keeps the input of `useQueryableWithLocalStorage` (text search, filters, sorts, page, size, URI and localStorage) and reads each page from the query, whose key carries the whole input; render its `queryableHelpers` with `QueryableToolbar` + `SortHeadersComponentV2`. The total comes from the data: never call `handleChangeTotalElements` / `incrementTotalElements`, describe the change instead. Show the loader on `isPending || isPlaceholderData` (another page, filter or sort), not on a refetch of the same page. The list query declares `collections` for its type and `refs` for the rows, not `watch`: every change of the type would refetch every open page, so an entity updated into the filter or sort window from another page shows at the next refetch. What `PaginationComponentV2` took as props: `contextId` (a scope) goes in the query key (`input => {feature}Queries.list(scopeId, input)`), `reloadContentCount` becomes a described change (`useApplyChanges`), `setLoading` the query state. Reference: `actions/assets/asset-queries.ts` (`list`), used by `admin/components/assets/endpoints/Endpoints.tsx`. Screens not migrated yet stay on `PaginationComponentV2`, which fetches on its own (`fetch` / `setContent`)
- **Live sources**: changes reach the router through `utils/stream/connectStreamToLiveRouter.ts`, today from the platform stream (`utils/stream/streamClient.ts`, kept open by the admin and private layouts through `useLiveStream`). Queries never depend on where changes come from. Never open another `EventSource` for platform data changes; only the Redux adapter (`useDataLoader.js`) reads raw stream messages (`onStreamMessage`)
- **Feature hook**: `use{Feature}s.ts` with `useState` + `useCallback` for UI-only local state, never a copy of server data
- **Actions**: simple API calls in the feature's action file using `simpleCall` / `simplePostCall`