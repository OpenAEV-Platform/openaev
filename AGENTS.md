# AGENTS.md

**Read [.github/copilot-instructions.md](.github/copilot-instructions.md) for full conventions.**

## Quick Reference

OpenAEV — Breach & Attack Simulation platform. Multi-tenant SaaS.
Java 21 / Spring Boot 3.3.7 / React 19 / TypeScript / PostgreSQL.

| Module | Role |
|---|---|
| `openaev-model/` | JPA entities, repositories |
| `openaev-framework/` | ⚠️ **Deprecated** — do not add new code |
| `openaev-api/` | REST API, services, migrations |
| `openaev-front/` | React SPA (Redux, CASL, MUI, Zod) |

### Key Commands

```bash
mvn clean install -DskipTests -Pdev   # Build backend
mvn spotless:apply                     # Format Java
mvn test                               # Tests (needs Docker services)
cd openaev-front && yarn build         # Build frontend
yarn lint && yarn check-ts             # Lint + type-check
yarn generate-types-from-api           # Sync API types
```

## Frontend Conventions

### Folder Naming

- **Directories** use `snake_case`: `platform_roles/`, `platform_groups/`, `users_capabilities/`
- **Component files** use `PascalCase`: `PlatformRoles.tsx`, `PlatformGroupCreate.tsx`
- **Non-component files** use `camelCase`: `platformRoles.queryable.ts`, `usePlatformRoles.ts`
- **Hooks** go in a `hooks/` subfolder: `hooks/usePlatformRoles.ts`

### Feature Structure

Each feature domain follows this layout:

```
feature_name/
  hooks/
    useFeatureName.ts          # Data fetching hook
  FeatureName.tsx              # Main list/page component
  FeatureNameCreate.tsx        # Create drawer
  FeatureNameUpdate.tsx        # Update drawer
  FeatureNameForm.tsx          # Shared form (create + update)
  FeatureNamePopover.tsx       # Actions popover (update/delete)
  featureName.queryable.ts     # Sort/filter/pagination config
```

### Section with Right Menu (like Security)

When a left-menu entry groups multiple sub-pages (e.g. "Users & capabilities" with Roles, Groups, Users):

1. Create a **parent folder** (e.g. `users_capabilities/`)
2. Create `UsersCapabilitiesMenu.tsx` — a `RightMenu` with entries for each sub-page
3. Create `UsersCapabilitiesIndex.tsx` — internal `<Routes>` with `<Navigate>` to default tab
4. Create `routes/UsersCapabilitiesRoutes.tsx` — `<Route>` with `<ProtectedRoute>`
5. Each sub-page is a **subfolder** inside the parent: `platform_roles/`, `platform_groups/`
6. Each sub-page component wraps its content in `div.container` + `div.bodyItems` + `<Menu />`

```tsx
// Pattern for a sub-page component
const useStyles = makeStyles()(() => ({
  container: { display: 'flex' },
  bodyItems: { flexGrow: 1 },
}));
return (
  <div className={classes.container}>
    <div className={classes.bodyItems}>
      {/* Breadcrumbs, list, create button... */}
    </div>
    <UsersCapabilitiesMenu />
  </div>
);
```

### DTOs & API Actions

- API types/functions go in `src/actions/{domain}/` (e.g. `actions/platform/platform-actions.ts`)
- Input/Output interfaces defined alongside the API functions
- Output DTOs should NOT contain nested entity data — use sub-resource endpoints instead

## Backend Conventions

### New Entities

- Audit timestamps: implement `Auditable` + add `AuditableListener` (not Hibernate `@CreationTimestamp`)
- ID: `@ControlledUuidGeneration` (not `@UuidGenerator` except User/Tenant/Token)
- Column naming: `{entity_singular}_{field}` → `@JsonProperty("same")`
- Collections: mutable (`new ArrayList<>()`) + `@Fetch(FetchMode.SUBSELECT)`

### Services

- `@Service @RequiredArgsConstructor @Transactional(rollbackFor = Exception.class)`
- Read methods: `@Transactional(readOnly = true)`
- Section comments in order: `// -- CREATE --`, `// -- READ --`, `// -- UPDATE --`, `// -- DELETE --`
- `findById()` must throw `EntityNotFoundException` if not found
- **Resolving associations from IDs**: use `ReferenceResolver.resolve(ids, Entity.class, repo::countByIdIn)` — never loop `findById()`

### API DTOs

For each entity exposed via REST:
- `{Entity}Input.java` — record for request body
- `{Entity}Output.java` — record for response body
- `{Entity}Mapper.java` — static `toOutput()` method, private constructor

### Performance

- Never iterate lazy collections just to extract IDs — use native queries on join tables
- For sub-resource data (users of a group, roles of a group), prefer dedicated `GET` endpoints over embedding in the parent output



