# Implementation Plan: [FEATURE NAME]

**Spec**: [SPEC-NNN](../specs/SPEC-NNN-feature-name/spec.md)
**Branch**: `feature/[short-name]`
**Created**: [DATE]
**Status**: Draft | Ready | In Progress | Complete

---

## 1. Summary

[Extract from spec: primary requirement + technical approach]

## 2. Technical Context

**Stack**: Java 21 / Spring Boot / PostgreSQL / Elasticsearch / React / TypeScript
**Modules**: openaev-model, openaev-api, openaev-front
**Testing**: JUnit 5 + MockMvc (backend), Vitest + Playwright (frontend)
**Migrations**: Flyway Java-based (`V4_{XX}__Description.java`)

## 3. Constitution Check

> GATE: Must pass before implementation begins.

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Security-First | [✅/❌] | [@AccessControl, tenant filter, DTOs] |
| II. Layered Architecture | [✅/❌] | [Controller→Service→Repository] |
| III. Test-First Quality | [✅/❌] | [Tests defined in spec §6] |
| IV. Performance by Design | [✅/❌] | [Pagination, LAZY loading, ReferenceResolver] |
| V. Spec-Driven Development | [✅/❌] | [Spec exists and approved] |
| VI. Conventional Commits | [✅/❌] | [Branch from release/current] |
| VII. Frontend Discipline | [✅/❌] | [api-types.d.ts, Zod, CASL] |
| VIII. Simplicity | [✅/❌] | [No over-engineering] |

## 4. Architecture

### Module Mapping

```
openaev-model/src/main/java/io/openaev/database/
├── model/
│   └── {Entity}.java                    # JPA entity
└── repository/
    └── {Entity}Repository.java          # Spring Data repository

openaev-api/src/main/java/io/openaev/
├── api/{feature}/
│   ├── {Entity}Input.java               # Request DTO (record)
│   ├── {Entity}Output.java              # Response DTO (record)
│   ├── {Entity}Mapper.java              # Entity ↔ DTO mapping
│   └── {Feature}Api.java                # REST controller
├── service/
│   └── {Feature}Service.java            # Business logic
└── migration/
    └── V4_{XX}__{Description}.java      # Flyway migration

openaev-api/src/test/java/io/openaev/
├── utils/fixtures/files/
│   └── {Entity}Fixture.java             # Test data factory
├── utils/fixtures/composers/
│   └── {Entity}Composer.java            # Test data lifecycle
└── rest/ or api/
    └── {Feature}ApiTest.java            # Integration test

openaev-front/src/
├── actions/{feature}/
│   ├── {feature}-action.ts              # API calls
│   ├── {feature}-helper.d.ts            # Types (if not in api-types.d.ts)
│   └── {feature}-schema.ts             # Zod validation
└── admin/components/{section}/{feature}/
    ├── {Feature}s.tsx                   # List page
    └── {Feature}Form.tsx                # Create/Edit form
```

### Database Schema

[Copy from spec §5 — Database Schema]

### API Contract

[Copy from spec §5 — API Endpoints]

## 5. Implementation Phases

### Phase 1: Database & Model

1. Create Flyway migration (`V4_{XX}__Create{Entity}.java`)
2. Create JPA entity in `openaev-model`
3. Create repository interface
4. Add `ResourceType` + `Capability` entries

### Phase 2: Backend Service & API

5. Create service with CRUD + search
6. Create Input/Output DTOs (records)
7. Create Mapper
8. Create REST controller with `@AccessControl`
9. Configure access model in `PermissionService` (if needed)

### Phase 3: Tests

10. Create fixture (`{Entity}Fixture`)
11. Create composer (`{Entity}Composer`)
12. Create integration test (`{Feature}ApiTest`)
13. Add security tests (RBAC, tenant isolation)

### Phase 4: Frontend (if applicable)

14. Create actions file
15. Create Zod schema
16. Create list page
17. Create form component
18. Add CASL permission check
19. Run `yarn generate-types-from-api`

### Phase 5: Validation

20. `mvn spotless:apply && mvn test`
21. `cd openaev-front && yarn lint && yarn check-ts && yarn test`
22. `mvn jacoco:check` (verify coverage)

## 6. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| [e.g., Breaking existing API] | [High] | [Add new endpoint, deprecate old] |
| [e.g., Performance on large datasets] | [Medium] | [Pagination + DB index] |

## 7. Dependencies

- [External dependency, e.g. "Requires User entity (exists)"]
- [Ordering constraint, e.g. "Migration must run before entity is used"]
