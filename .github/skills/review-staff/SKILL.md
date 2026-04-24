---
name: review-staff
description: >-
  Staff/technical review checklist: architecture fit, anti-pattern detection, code quality,
  naming conventions, layering, duplication. Includes good/bad examples for each check.
---

# Staff Review

## Procedure

### Step 1 — Check Architecture & Layering

Verify the feature follows OpenAEV's layered architecture:

```
Controller (API) → Service → Repository
     ↓                ↓          ↓
   DTOs only    Business logic  Data access
```

- [ ] Controllers only depend on Services (never inject Repository in a controller)
- [ ] Services handle business logic (not controllers, not repositories)
- [ ] Repositories are data access only (no business logic)
- [ ] No new code added to `openaev-framework` (deprecated)

**Search for violations**:
```bash
# Check for repositories injected in controllers
grep -rn "Repository" openaev-api/src/main/java/io/openaev/api/ --include="*.java" | grep -v "test\|Test"
```

### ✅ Good Layering

```java
// Controller → delegates to service
@RestController
@RequiredArgsConstructor
public class AssessmentApi {
    private final AssessmentService assessmentService;

    @PostMapping
    public ResponseEntity<AssessmentOutput> create(@Valid @RequestBody AssessmentInput input) {
        return ResponseEntity.status(201).body(toOutput(assessmentService.create(input)));
    }
}
```

### ❌ Bad Layering

```java
// Controller → accesses repository directly, contains business logic
@RestController
public class AssessmentApi {
    @Autowired private AssessmentRepository assessmentRepository;

    @PostMapping
    public Assessment create(@RequestBody Assessment assessment) {
        assessment.setCreatedAt(Instant.now());
        return assessmentRepository.save(assessment);
    }
}
```

### Step 2 — Check for Anti-Patterns

#### 🍝 Spaghetti Code
- [ ] No circular dependencies between services
- [ ] Methods under ~30 lines (split complex logic into private methods)
- [ ] Classes under ~500 lines (split into separate services if larger)

```bash
# Find large files
find openaev-api/src/main/java -name "*.java" -exec wc -l {} \; | sort -rn | head -20
```

#### 🏗️ God Class
- [ ] Each service handles one domain area
- [ ] No service with 50+ methods
- [ ] No class with 10+ injected dependencies

```bash
# Count methods per class
grep -c "public " openaev-api/src/main/java/io/openaev/service/*.java | sort -t: -k2 -rn | head -10
```

#### 📋 Code Duplication
- [ ] No copy-paste code blocks >20 lines
- [ ] Similar components extracted into generic reusable component
- [ ] Shared logic in utils or base classes

#### 🔫 Shotgun Surgery
- [ ] A single field addition doesn't touch 10+ unrelated files
- [ ] Changes are localized to the feature's module/package

### Step 3 — Check Naming & Conventions

- [ ] Entity columns: `{entity_singular}_{field}` (e.g., `assessment_name`)
- [ ] Table names: `snake_case_plural` (e.g., `assessments`)
- [ ] `@JsonProperty` matches column name
- [ ] Service annotations: `@Service @RequiredArgsConstructor @Transactional(rollbackFor = Exception.class)`
- [ ] Read methods: `@Transactional(readOnly = true)`
- [ ] Section comments: `// -- CREATE --`, `// -- READ --`, etc.
- [ ] JavaDoc on all public methods
- [ ] Uses `org.springframework.transaction.annotation.Transactional` (not `jakarta.transaction.Transactional`)

```bash
# Check for wrong @Transactional import
grep -rn "jakarta.transaction.Transactional" openaev-api/src/main/java/ --include="*.java"
```

### Step 4 — Check DTOs & Mappers

- [ ] No JPA entities returned from controllers
- [ ] Input DTO is a Java `record` with `@JsonProperty` and validation annotations
- [ ] Output DTO is a Java `record` with `@JsonProperty`
- [ ] Mapper has `private` constructor and static methods
- [ ] `tenant_id` is never in Output DTO

### ✅ Good DTO Pattern

```java
public record AssessmentInput(
    @JsonProperty("assessment_name") @NotBlank String name,
    @JsonProperty("assessment_description") String description) {}

public record AssessmentOutput(
    @JsonProperty("assessment_id") @NotBlank String id,
    @JsonProperty("assessment_name") @NotBlank String name,
    @JsonProperty("assessment_description") String description,
    @JsonProperty("assessment_created_at") Instant createdAt) {}

public class AssessmentMapper {
    private AssessmentMapper() {}
    public static AssessmentOutput toOutput(Assessment entity) {
        return new AssessmentOutput(entity.getId(), entity.getName(),
            entity.getDescription(), entity.getCreatedAt());
    }
}
```

### ❌ Bad DTO Pattern

```java
// Returning the JPA entity directly — exposes tenant_id, LAZY proxy issues
@GetMapping("/{id}")
public Assessment get(@PathVariable String id) {
    return repo.findById(id).orElseThrow();
}
```

### Step 5 — Check Frontend Conventions (if applicable)

- [ ] No MUI for layout — native HTML (`div`, `section`)
- [ ] Styling: `sx` prop only (no `makeStyles`)
- [ ] Types from `api-types.d.ts` (not manual type definitions)
- [ ] Zod for form validation
- [ ] CASL permission checks on actions/views
- [ ] `t()` called early for i18n
- [ ] `snake_case` folder names

### Step 6 — Check Test Conventions (if tests in scope)

- [ ] `@TestInstance(PER_CLASS) @Transactional` on integration tests
- [ ] `@WithMockUser` from `io.openaev.utils.mockUser` (NOT `org.springframework`)
- [ ] `@Nested @DisplayName` groups
- [ ] `given_X_should_Y` method naming
- [ ] AAA pattern with comments: `// Arrange` / `// Act` / `// Assert`
- [ ] Fixtures + Composers (no inline test data)

### Step 7 — Report

Document findings using conventional comments:

- `issue (blocking):` — layering violation, god class, missing DTOs, JPA entity in controller
- `suggestion (non-blocking):` — naming improvements, method extraction, test improvements
- `nitpick:` — minor style issues (usually auto-fixable)
- `praise:` — good patterns, clean code, effective reuse
- `note:` — context, alternatives considered

### Blocker Criteria

A finding is a **🚫 Blocker** if:
- New code added to `openaev-framework`
- Controller bypasses Service layer (direct repository access)
- JPA entity returned from a REST endpoint
- Class exceeds ~500 lines with no plan to split
- Feature duplicates >50% of existing code without reuse
- Circular dependency between services
- Wrong `@Transactional` import (`jakarta` instead of `org.springframework`)
