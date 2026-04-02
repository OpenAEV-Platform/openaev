---
applyTo: "openaev-front/**/*.ts,openaev-front/**/*.tsx,openaev-front/**/*.js,openaev-front/**/*.jsx"
description: "Frontend React/TypeScript conventions: components, MUI, forms, permissions, i18n, Redux"
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

## MUI & Layout

- **No MUI for layout** — use native HTML (`div`, `section`, `header`)
- MUI only for interactive components (`Button`, `TextField`, `Dialog`)
- Styling: `sx` prop only — never `makeStyles` / `withStyles`
- Buttons: Create/Update → `secondary`, Cancel → `primary`, Delete → `secondary`/`error`

## Forms

- Zod for validation, React Hook Form for management
- Atomic form fields: `TextFieldController`, `SelectFieldController`, etc.
- Field `name` = JSON property name from API

## Permissions (CASL)

- Capability: `ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT)`
- Grant: `ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, resourceId)`
- Create/Edit: wrap with `<Can I={ACTIONS.MANAGE} a={SUBJECTS.X}>`
- Delete: `ability.can()` in popover entries `userRight`
- New subject → add in `src/utils/permissions/types.ts`

## i18n

- Call `t()` as early as possible — pass translated strings, not raw keys
- Null-check backend strings before `t()` (crashes on null/undefined)
- Keys = English text: `t('Tenant name')`

## Data Loading

- Small/medium datasets: Redux store (normalized via normalizr)
- Large datasets: fetch directly, bypass store
