# Task.1 US.7 - Export and import marking definitions (issue #8690)

Complements `implementation-plan.md`, which deferred import/export (section 2, "Import/export behavior is deferred"). Parent: #7511. Feature flag: `MARKING` (`PreviewFeature.MARKING` on the backend, `isFeatureEnabled('MARKING')` on the frontend).

## 1) Goal

A user holding the manage marking definitions capability exports the tenant's marking definitions to a CSV file and imports a compatible CSV into another tenant or instance. The import is one step, partial, and never overwrites. The UI shows a per-row report, including every conflicting row.

Out of scope: marking assignments to assets, roles and groups. STIX/OpenCTI format (see section 9).

## 2) Decisions

| # | Decision | Source |
|---|---|---|
| D1 | Conflict (the issue's "order conflict for the same type and reference"): a row whose type and definition already exist, whatever its order or colour, is rejected, never overwritten, and shown in the UI report. | Product |
| D2 | One-step import. The report is returned by the import call and displayed in the UI. No dry-run endpoint in this issue. | Product |
| D3 | Export includes protected default markings (TLP). Importing them back reports them as duplicates. | Product |
| D4 | CSV columns and naming are ours to define. | Product |
| D5 | Feature flag is `MARKING`. | Product |
| D6 | CSV vs STIX is pending with the OpenCTI team. The parser sits behind a `MarkingRow` boundary so the format can change without touching validation. | Product |

## 3) What exists today (verified in code)

- Uniqueness is enforced on `(LOWER(type), LOWER(definition), tenant_id)`: migration `V6_20260914120000000__Add_marking_definitions` creates the unique index, and `MarkingDefinitionService.create` and `update` call `validateUniqueOrThrow`. So a duplicate type and definition cannot be created manually.
- Order carries no uniqueness rule: `create` and `update` only check `order >= 0` (`MarkingDefinitionInput`). Import applies the same rule and adds no order check of its own.
- `create` normalizes type and definition to upper case (`normalize`), forces `protected = false`, evicts the clearance cache and bumps a metric after commit.
- `MarkingDefinitionApi` writes with `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)` and resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)`.
- Frontend list: `MarkingDefinitions.tsx`. `PaginationComponentV2` has a `topBarButtons` slot holding `ButtonCreate` inside `<Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>`. The list is local state fed by `searchMarkingDefinitions`.

### Existing import and export code to reuse or avoid

| Piece | Where | Verdict |
|---|---|---|
| CSV export endpoint | `MapperApi#exportMappersCsv` -> `MapperService.exportCsv` (opencsv `StatefulBeanToCsvBuilder`, `CustomColumnPositionStrategy`, `text/csv`, filename `Prefix + yyyyMMddHHmm + .csv`) | Reuse the shape and the filename convention. It is bound to `CsvType` and a `MapperService` that does not belong to markings, so implement a dedicated export in the marking service and controller instead of adding a `CsvType` value. |
| CSV import | `MapperService.importMappersCsv` (temp file, opencsv `CsvToBean`, positional columns, `withSkipLines(1)`) | Avoid as a model. It is all-or-nothing: the first bad row throws and there is no per-row report, which AC3 and AC5 forbid. Positional mapping also breaks when columns are reordered. Parse by header name. |
| Multipart upload endpoint | `ThreatArsenalApiImporter#importJson`, `MapperApi#importMappers` (`@RequestPart("file") @NotNull MultipartFile`, `writeScopeResolver.tenantForWrite`) | Reuse the signature. |
| File validation | `MapperApi#validateUploadedFile` (extension allowlist, size cap, `UnsupportedMediaTypeException`, `FileTooBigException`) | Reuse the idea. Do not call it, it is private to `MapperApi`. Mirror it in the marking API, with a 1 MB cap. |
| Report returned to the client | `PayloadImportService` returns a `PayloadImportResult` | Look at it for naming before defining our report record. |
| Frontend export button | `components/common/ExportButton.tsx` (`CSVLink`, `download(data, filename, 'text/csv')`, filename read from `content-disposition`) | Reuse `download` and the `content-disposition` handling. The component itself is tied to `exportType` strings, so write a small dedicated button. |
| Frontend import button | `components/common/ImportUploader.tsx` (`CloudUploadOutlined`, hidden file input, `fileAccepted`), used by `ImportUploaderEndpoints` | Reuse as is for file selection. The report dialog is new. |
| Frontend import action | `endpoint-actions.ts#importEndpoints` (`simplePostCall(url, FormData)`) | Same pattern for the multipart call. |

The existing endpoint export uses opencsv's `DEFAULT_SEPARATOR` (comma) with a quote character. Use the same separator and quoting, so the files look alike to users.

## 4) CSV contract

Header row required, UTF-8 (tolerate a BOM), parsed by column name, so column order does not matter:

```
type,definition,color,order
TLP,TLP:AMBER,#ffc107,3
```

- `type` and `definition` are normalized exactly as `create` does (trim, upper case).
- `color` is a required hex colour. Check how `MarkingDefinitionForm` validates colour and apply the same rule server-side.
- `order` is an integer `>= 0`.
- Unknown extra columns are ignored. Missing required columns reject the whole file with one clear error (nothing to import).
- Export writes the same four columns, sorted by type then order, and neutralizes cells that start with `=`, `+`, `-`, `@` (CSV injection). Import strips that neutralisation prefix for those four characters so an export re-imports cleanly.
- No ids, no tenant id, no `protected` flag in the file.

Limits (decided): 1 MB and 1000 data rows. Above either limit the whole file is rejected with a clear error (`FileTooBigException` for the size, as `MapperApi` does; `BadRequestException` for the rows). The size cap follows the existing imports, which cap only the file size: the platform-wide multipart limit is 5 GB, the XLS inject import caps at 25 MB, the credential file at 16 KB, and the endpoint CSV import has no cap at all. No existing import caps rows. The 1000-row cap is here to bound the report returned to the UI.

## 5) Row validation order and report

Each row gets exactly one outcome. Checks run in this order and the first failure wins:

1. `MISSING_FIELD` - type, definition, color or order empty.
2. `INVALID_COLOR` - not a valid hex colour.
3. `INVALID_ORDER` - not an integer, or below 0.
4. `DUPLICATE_IN_FILE` - same normalized `(type, definition)` as an earlier row of the same file. The first row wins.
5. `ALREADY_EXISTS` - `(type, definition)` already exists in the tenant (includes protected defaults). Never overwritten, even when the file's order or colour differs from the existing marking. The `message` states the existing order and colour, so the user sees what differs and can resolve it by editing the existing marking or the file.

Rows that pass all checks are created. The report:

```
MarkingDefinitionImportResult {
  int created;
  int skipped;
  List<Row> rows;       // every skipped row, plus created ones (status CREATED) so the UI can show both
}
Row { int line; String type; String definition; String color; Integer order; Status status; Reason reason; String message }
```

Line numbers count the file lines (header is line 1), so the user can find the row in a spreadsheet. The `message` is built server-side from fixed text, never from an exception message (security rule: no raw errors to clients).

## 6) Backend

Package `io.openaev.api.marking_definition` and `io.openaev.service.marking_definition`. No code in `openaev-framework` or `io.openaev.rest`.

### 6.1 Service: `MarkingDefinitionService`

- `exportAll(TxCtx ctx)`: reads the tenant-scoped list, sorted by type then order. Reuse `listInternal` (the non-transactional body noted in the class). Add `@Transactional(readOnly = true)`.
- `importAll(TxCtx ctx, String tenantId, List<MarkingRow> rows)`:
  - Load the tenant's existing markings once, in memory (keyed by normalized `(type, definition)`). No per-row query, no `findById` loop.
  - Validate every row in memory (section 5), build entities for the valid rows, then one `saveAll`.
  - One `markingClearanceCacheManager.evictAll()` after the batch (not per row: `create` evicts per call).
  - Metric: increment the created counter by the number created, after commit, through the existing `runAfterCommit`.
  - Returns the report. A bad row never throws, so it can't roll the batch back.
  - Rows go through the same `normalize` and set `protected = false`.
- Extract the shared bits (`normalize`, entity construction) so `create` and `importAll` don't diverge. Do not call `create` in a loop: it re-checks uniqueness with a query per row and evicts the cache per row.

### 6.2 Parser: new `MarkingCsvParser` (stateless, in the service package)

- Input: `InputStream`. Output: `List<MarkingRow>` where `MarkingRow` holds the raw string fields plus the line number. It does no business validation, so a STIX parser can later produce the same `MarkingRow`.
- File-level failures (no header, missing columns, too many rows, unreadable) raise a `BadRequestException` with a fixed message.
- Uses opencsv (already a dependency, see `MapperService`) with header-based mapping. Do not use `ColumnPositionMappingStrategy`.

### 6.3 Controller: `MarkingDefinitionApi`

Add two endpoints under `// -- EXPORT --` and `// -- IMPORT --`:

- `GET /export`, `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)`. WRITE rather than SEARCH, because AC4 says export needs the manage capability, and `SEARCH` would let read-only holders download. Streams `text/csv` through `StreamingResponseBody`, filename `MarkingDefinitions<yyyyMMddHHmm>.csv`.
- `POST /import`, `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`, `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)`, `@RequestPart("file") @NotNull MultipartFile`. Validates extension `csv` and size, parses, resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)`, calls `importAll`, returns `MarkingDefinitionImportResult`. `@LogExecutionTime` and `@Operation` on both.
- Both declare `TxCtx ctx` (the table is v2-active) and are registered in `TenantScopedEntrypointsTxCtxArchTest`.
- Feature flag: same as the other `MarkingDefinitionApi` endpoints, which carry no explicit flag guard in the controller. The two new endpoints add none either; the capability tree (`CapabilityTreeBuilder`) already hides the marking capabilities when `MARKING` is off, and the frontend masks the page.

### 6.4 DTOs (records, `io.openaev.api.marking_definition.form`)

- `MarkingDefinitionImportResult`, `MarkingDefinitionImportRow`, enums `ImportRowStatus` and `ImportRowReason`. All properties `@JsonProperty` snake_case like the other marking DTOs. No tenant id.

### 6.5 What does not need a migration

No schema change: no new column, no new index. The duplicate check runs in memory against one tenant's rows.

## 7) Frontend

Files in `openaev-front/src/admin/components/settings/marking_definitions/` and `src/actions/marking_definitions/`.

- Actions (`marking-definition-actions.ts`): `exportMarkingDefinitions()` (same shape as `exportCsvMapper`: returns data plus the filename from `content-disposition`, then the existing `download(..., 'text/csv')`) and `importMarkingDefinitions(formData)` (`simplePostCall` with `FormData`, like `importEndpoints`).
- Toolbar: inside the existing `topBarButtons` `<Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>`, next to `ButtonCreate`, with the same icons as the Endpoints and Threat Arsenal lists:
  - Import: the existing `ImportUploader` (secondary `IconButton`, `CloudUploadOutlined`, hidden file input), with `title={t('Import marking definitions')}` and `fileAccepted=".csv"`. Its `handleUpload(formData, file)` calls the import action, then opens the report dialog. It sets its own spinner flag to false right after calling `handleUpload`, so loading state and the result live in the parent.
  - Export: a small dedicated button, secondary `IconButton`, `FileDownloadOutlined`, `aria-label` and tooltip "Export this list" (same label and icon as `ExportButton`, which is tied to its `exportType` strings), disabled when the list is empty.
  - Library components first (IconButton, Tooltip); the report dialog stays MUI with a `fds:keep-mui` comment where the conventions require it.
- No separate file-picker dialog: the picker is the hidden input behind `ImportUploader`. The report dialog also offers a way to download an example file (the export can serve as the template).
- Report dialog (`MarkingDefinitionImportReport.tsx`), opened once the import call returns:
  - Summary line, for example "12 imported, 3 skipped".
  - A table of skipped rows: line, type, definition, reason (translated), and a message. Created rows are not listed by default.
  - A "Download skipped rows" button that writes a CSV of only the skipped rows in the import format, so the user fixes the file and re-imports. Builds it client-side from the report.
  - A clear state for a file-level failure (400): the message from the server, no table.
- After a successful import, refetch the list (the `upsert` + `incrementTotalElements` helpers fit one row, not a batch). Check how `PaginationComponentV2` exposes a refetch; if none, bump a key or call `searchMarkingDefinitions` again with the current `searchPaginationInput`.
- i18n: every new string through `t()`, keys in English, added to the language files that the repo requires.
- Types: add the DTOs on the Java side first, then `yarn generate-types-from-api`. No hand-written copies of those types.
- Flag and permission: the page is already masked when `MARKING` is off (`SecurityMenu`, `isFeatureEnabled('MARKING')`), and the buttons are inside `Can`.

## 8) Open points (not blocking the plan)

1. Export capability: to check. The plan puts export under `Action.WRITE` because AC4 says only holders of "Manage marking definitions" can import or export. Normally an export is a `READ` (as `MapperApi#exportMappersCsv` does with `Action.READ`), so confirm with the product owner which one applies here, and align section 6.3, the frontend `Can` wrapper around the export button, and the permission tests.
2. Clearance and visibility: an admin exports all the tenant's markings, then a user (with the manage capability but a narrower clearance) imports that file. The user would then see only the markings they are cleared for, not everything the file created. Questions for the product owner: should export be narrowed to the caller's clearance (as `listAssignable` is), or always contain every marking of the tenant? After an import, should the importing user be able to see the markings the import just created, or only those their clearance covers? The plan currently exports and imports the whole tenant scope, with no clearance narrowing.
3. OpenCTI format (D6): once the OpenCTI team answers, decide whether a second parser behind `MarkingRow` is needed, and in which issue.

## 9) Tests

Follow `add-test` (fixture, composer, `@Nested` + `@DisplayName`, `given_X_should_Y`, AAA comments, OpenAEV's `@WithMockUser`).

Backend, `MarkingDefinitionServiceTest` and `MarkingDefinitionApiTest` (extend what exists):
- Export: returns the tenant's rows only, sorted, includes protected rows, no id or tenant id in the file, formula-looking cell neutralised.
- Import, one test per outcome: all-valid file, `MISSING_FIELD`, `INVALID_COLOR`, `INVALID_ORDER`, `DUPLICATE_IN_FILE`, `ALREADY_EXISTS` (including a protected TLP, and a row with the same type and definition but another order or colour).
- Partial import: a mixed file creates the valid rows, skips the others, and the report line numbers match the file.
- Never overwrites: an existing marking keeps its colour and order after an import that conflicts with it.
- Header handling: reordered columns, extra columns, BOM, missing required column (file-level 400), empty file, too many rows, too large, wrong extension.
- Query count with Hibernate `Statistics` (ORM doctrine): an import of N rows does not issue N uniqueness queries.
- Cache: one `evictAll()` per import.
- Permissions: no capability gets 403 on both endpoints; read-only (`ACCESS` without `MANAGE`) cannot export or import. Public access not applicable.
- Tenant isolation: use the tenant isolation test skill. Importing as tenant A never touches tenant B; exporting as A never contains B's rows. The same `(type, definition)` in two tenants is not a conflict.
- `TenantScopedEntrypointsTxCtxArchTest` stays green with the two new entrypoints registered.

Frontend (component tests next to the existing marking ones): buttons only under the manage permission, the import control accepts only `.csv`, report renders the summary and each skipped row, the "download skipped rows" CSV content, file-level error state, list refresh after an import.

## 10) Review gates and docs

Run: `review-code`, `review-security`, `review-multi-tenancy`, `review-performance`, `review-frontend`, `review-docs`. `review-migration` and `review-chaining-engine`: N/A (no migration, no chaining). `reduce-tx-baseline`: only if a new transaction pattern shows up.

Docs: add the import and export behaviour (CSV columns, the skip reasons, the "never overwrites" rule) to the marking definitions page under `docs/`.

Before opening the PR: format Java with `.\scripts\hooks\format-java.ps1`, run `yarn lint && yarn check-ts` and `yarn generate-types-from-api`. PR title follows Conventional Commits and ends with `(#8690)`; description follows `.github/PULL_REQUEST_TEMPLATE.md`.

## 11) Scope summary: files to create or modify

Paths are relative to the repository root. Backend main code is under `openaev-api/src/main/java/io/openaev/`, backend tests under `openaev-api/src/test/java/io/openaev/`, frontend under `openaev-front/src/`.

### Backend, create

| File | Purpose |
|---|---|
| `service/marking_definition/MarkingRow.java` | Record: raw CSV fields plus the file line number. The format boundary (D6). |
| `service/marking_definition/MarkingCsvParser.java` | Stateless header-based CSV parser (opencsv), file-level validation (columns, row cap). |
| `api/marking_definition/form/MarkingDefinitionImportResult.java` | Record: created count, skipped count, rows. |
| `api/marking_definition/form/MarkingDefinitionImportRow.java` | Record: line, type, definition, color, order, status, reason, message. |
| `api/marking_definition/form/MarkingDefinitionImportRowStatus.java` | Enum `CREATED`, `SKIPPED`. |
| `api/marking_definition/form/MarkingDefinitionImportRowReason.java` | Enum `MISSING_FIELD`, `INVALID_COLOR`, `INVALID_ORDER`, `DUPLICATE_IN_FILE`, `ALREADY_EXISTS`. |

### Backend, modify

| File | Change |
|---|---|
| `service/marking_definition/MarkingDefinitionService.java` | Add `exportAll(ctx)` and `importAll(ctx, tenantId, rows)`; extract the shared normalize and entity-building code used by `create`. Add a metric increment by N after commit. |
| `api/marking_definition/MarkingDefinitionApi.java` | Add `GET /export` (CSV stream) and `POST /import` (multipart) under `// -- EXPORT --` and `// -- IMPORT --`. File extension and size checks. |
| `api/marking_definition/MarkingDefinitionMapper.java` | Add the row mapping used by the export, and the import-row mapping, if the service does not need them directly. |
| `telemetry/metric_collectors/ActionMetricCollector.java` | Only if the existing created counter cannot be incremented by N; check before touching. |

### Backend, tests (modify unless stated)

| File | Change |
|---|---|
| `service/marking_definition/MarkingCsvParserTest.java` (create) | Parser unit tests: header handling, BOM, missing columns, row cap, empty file. |
| `service/marking_definition/MarkingDefinitionServiceTest.java` | Export and import outcomes, partial import, never-overwrite, query count, one cache eviction. |
| `api/marking_definition/MarkingDefinitionApiTest.java` | Both endpoints: permissions, file validation, report content, no id or tenant id in the export. |
| `service/marking_definition/MarkingDefinitionTenantScopeTest.java` | Tenant isolation of export and import (see the add-tenant-isolation-test skill). |
| `architecture/TenantScopedEntrypointsTxCtxArchTest.java` | Register `MarkingDefinitionApi#exportMarkingDefinitions` and `#importMarkingDefinitions` in the `marking_definitions (v2)` block (final method names to match the controller). |
| `utils/fixtures/MarkingDefinitionFixture.java` and `utils/fixtures/composers/MarkingDefinitionComposer.java` | Only if a CSV-content helper is needed; reuse them for the data setup. |

### Frontend, create

| File | Purpose |
|---|---|
| `admin/components/settings/marking_definitions/MarkingDefinitionImportReport.tsx` | Report dialog: summary, skipped rows table, download of skipped rows, file-level error state. |
| `admin/components/settings/marking_definitions/MarkingDefinitionExportButton.tsx` | Dedicated export icon button (`FileDownloadOutlined`, "Export this list"). |
| `admin/components/settings/marking_definitions/MarkingDefinitionImport.tsx` | Wraps `ImportUploader` (`.csv`), calls the import action, opens the report dialog. |
| `__tests__/admin/components/settings/marking_definitions/*.test.tsx` | Component tests (no marking frontend tests exist today, so this folder is new). |

### Frontend, modify

| File | Change |
|---|---|
| `actions/marking_definitions/marking-definition-actions.ts` | Add `exportMarkingDefinitions` and `importMarkingDefinitions` (multipart). |
| `admin/components/settings/marking_definitions/MarkingDefinitions.tsx` | Add the two buttons in `topBarButtons`, refetch the list after an import. |
| `utils/api-types.d.ts` | Regenerated by `yarn generate-types-from-api`; not edited by hand. |
| `utils/lang/en.json` and the other language files in `utils/lang/` | New strings (check the repo practice for which languages are filled by hand). |

### Other

| File | Change |
|---|---|
| `docs/` | Document the import and export behaviour. No marking page exists under `docs/` today (a search of `docs/` only finds `docs/docs/reference/deployment/configuration.md`), so the page location is to decide with the docs reviewer. |
| `brainstorming/marking/task1/implementation-plan.md` | Optional: update the "Import/export behavior is deferred" line to point to this plan. |

### Not touched

- No migration and no `adr/` change.
- No change to `openaev-model` (no entity or repository change, the in-memory duplicate check needs none), `openaev-framework`, the chaining engine or `application.properties`.
- No change to `ExportButton.tsx`, `ImportUploader.tsx` or the existing mapper import and export.
- No new capability and no new permission entry.

## 12) Suggested order of work

1. `MarkingRow`, `MarkingCsvParser`, DTOs and their unit tests.
2. `MarkingDefinitionService.importAll` and `exportAll` with service tests.
3. Controller endpoints, `TxCtx` arch-test registration, API tests including permissions and tenant isolation.
4. Regenerate the TypeScript types.
5. Frontend actions, toolbar buttons (`ImportUploader` and the export icon button), import report dialog, component tests.
6. Docs and review gates.