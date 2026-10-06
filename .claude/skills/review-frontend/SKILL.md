---
name: review-frontend
description: >-
  Frontend review checklist for OpenAEV React/TypeScript code: component patterns, Filigran
  Design System adoption, forms, MUI usage, permissions, i18n, state management, dead code.
  Use when reviewing PRs or auditing frontend features.
---

# Frontend Review

## Procedure

### Step 1 — Check component structure

- One component per file, one folder per feature
- Functional components with `FunctionComponent` typing
- No class components in new code (`.jsx` → `.tsx` migration required when touching)
- Props defined via explicit `interface Props` (not inline)

### Step 2 — Check forms

- Zod for validation via `zodImplement<T>().with({...})`
- React Hook Form via `useForm<T>` + `FormProvider`
- Field controllers: `TextFieldController`, `SelectFieldController`, `TagFieldController`
- Form layout: flexbox with `gap: theme.spacing(2)`, no MUI Grid
- Validate types match `api-types.d.ts` (auto-generated, never manual)
- Forms hosting a `required` library field set `noValidate` (otherwise the native browser check hides the Zod message)
- Library labels only (`SelectLabel`, `ComboboxLabel`), no MUI `InputLabel` beside a library field

### Step 3 — Check design system, MUI & styling

Rules: `frontend.instructions.md` → *Design System & Layout*.

- **Library first** — no new `@mui/*` import of a component the design system replaces, unless it carries `// fds:keep-mui <reason>`:
  ```bash
  node fds-migration/scripts/check-mui-regression.mjs --base origin/main
  node fds-migration/scripts/check-fds-conformity.mjs
  ```
- **No local look-alike** of a library component, no MUI control inside a converted library container
- **Library prop contracts** — `IconButton` gets `icon` + `aria-label`; variant/priority named explicitly; `Paper padding` in `0/8/16/24/32` and `title` never `''`; control sizes match their row (`sm` in rows, forms, cards and `Paper` headers)
- **Navigation** — buttons/chips/cards that navigate wrap a real `Link` / `<a>` (`asChild`), no `onClick={navigate}`
- **Colours** — no `alpha()` / `darken()` / `lighten()` on a value that may be a token (use `tint()` from `utils/tint.ts`):
  ```bash
  cd openaev-front && yarn test src/__tests__/utils/tokenColoursNotInAlpha.test.ts
  ```
- **No arbitrary utility classes** — every class string used on a library component must exist in the shipped sheet:
  ```bash
  grep -c "\.<class>{" openaev-front/node_modules/@filigran/design-system/dist/index.css   # 0 = the class is a silent no-op
  ```
- **No MUI for layout** — native `div`, `section`, `header`, flexbox/grid
- **`sx` on MUI, `style` on library components** — never `style={{ }}` on a MUI component; when moving `sx` → `style`, every bare spacing number must be converted to px (`p: 2` → `16`)
- **Theme tokens** — use `theme.palette`, `theme.spacing()`, `theme.shape`, `theme.typography` or design-system tokens for all visual values; no hardcoded `#hex`, `rgba()`, or raw `px` (fixed `width` exempt). `src/components/fds-tokens.generated.ts` is never hand-edited
- **Visual delta** — for a conversion, ask for a before/after measurement in the running app (both themes); lint and type-check do not see most design-system regressions

### Step 4 — Check permissions

- Create/Edit: wrapped with `<Can I={ACTIONS.X} a={SUBJECTS.Y}>`
- No hardcoded role checks — use CASL `ability.can()`
- New subjects → added in `src/utils/permissions/types.ts`

### Step 5 — Check i18n

- `t()` called early — pass translated strings to child components
- Keys = English text: `t('Organization name')`, not `t('organization_name')`
- No missing translations:
  ```bash
  cd openaev-front && yarn i18n-checker 2>&1 | tail -20
  ```

### Step 6 — Check data loading patterns

- **Paginated lists**: `useQueryableWithLocalStorage` + `PaginationComponentV2`
- **Actions**: simple calls in `actions/{feature}/{feature}-actions.ts`
- **Hooks**: custom hook per feature (e.g., `useOrganizations.ts`) for local state

### Step 7 — Check TypeScript

- No `any` types
- No `@ts-ignore` without explanatory comment
- Auto-generated types from API used (not manual interfaces for API responses):
  ```bash
  grep -rn "interface.*Input\|interface.*Output" openaev-front/src/ --include="*.ts" --include="*.tsx" | grep -v api-types | grep -v node_modules | head -20
  ```

### Step 8 — Check dead code

- No unused imports (ESLint catches most)
- No orphaned `.jsx` files if a `.tsx` replacement exists
- No components imported but not rendered
- No dead props — grep all call-sites of modified components; any prop declared in `interface Props` that no consumer passes must be removed

### Step 9 — Report

Document findings using conventional comments format:
- `issue (blocking):` for pattern violations that cause bugs or inconsistency
- `suggestion (non-blocking):` for improvements and modernization
- `nitpick:` for style preferences
- `praise:` for well-implemented patterns

