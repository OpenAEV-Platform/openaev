---
name: review-frontend
description: >-
  Frontend review checklist for OpenAEV React/TypeScript code: component patterns, forms,
  MUI usage, permissions, i18n, state management, dead code. Use when reviewing PRs or
  auditing frontend features.
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

### Step 3 — Check MUI & styling

- **No MUI for layout** — native `div`, `section`, `header`, flexbox/grid

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

### Step 9 — Report

Document findings using conventional comments format:
- `issue (blocking):` for pattern violations that cause bugs or inconsistency
- `suggestion (non-blocking):` for improvements and modernization
- `nitpick:` for style preferences
- `praise:` for well-implemented patterns

