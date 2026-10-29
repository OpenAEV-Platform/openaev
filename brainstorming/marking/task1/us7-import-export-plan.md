# Task.1 US.7 - Export and import marking definitions (issue #8690)

Complements `implementation-plan.md`, which deferred import/export (section 2, "Import/export behavior is deferred"). Parent: #7511. Feature flag: `MARKING` (`PreviewFeature.MARKING` on the backend, `isFeatureEnabled('MARKING')` on the frontend).

## 1) Goal

A user holding the access marking definitions capability exports the tenant's marking definitions to a JSON file, and a user holding the manage marking definitions capability imports a compatible JSON file into another tenant or instance. The import is one step, partial, and never overwrites. The UI shows a per-row report, including every conflicting row.

Out of scope: marking assignments to assets, roles and groups. The exact OpenCTI file shape (open point 1, section 8): until the example file arrives, the format is the JSON contract of section 4. Clearance rules (who can create, delete or see which markings, for example an order above the caller's level): a separate issue will cover them for the manual actions, and they will be added to the import once that issue is done. Until then import and export work on the whole tenant scope with no clearance narrowing.

## 2) Decisions

| # | Decision | Source |
|---|---|---|
| D1 | Conflict (the issue's "order conflict for the same type and reference"): a row whose type and definition already exist, whatever its order or colour, is rejected with the single reason `CONFLICT` (also for a repeat inside the file), never overwritten, and shown in the UI report with a message stating what differs. | Product |
| D2 | One-step import. The report is returned by the import call and displayed in the UI. No dry-run endpoint in this issue. | Product |
| D3 | Export includes protected default markings (TLP). Importing them back unchanged reports them as `IGNORED` (D8); if the file's colour or order was edited, as `CONFLICT`. | Product |
| D4 | The file is JSON. Field names and structure are ours to define until the OpenCTI example file arrives (section 8), then they are aligned on it. | Product |
| D5 | Feature flag is `MARKING`. | Product |
| D6 | The OpenCTI team will provide an example file, in JSON. Until then section 4 is the working contract. The parser sits behind a `MarkingRow` boundary so the JSON shape can change (or a second shape be added) without touching validation. | Product |
| D7 | Export needs the `ACCESS` capability (`ACCESS_MARKING_DEFINITION`, `Action.READ`), import needs `MANAGE` (`Action.WRITE`). | Product |
| D8 | A row identical to an existing marking or to an earlier row of the file (same colour and order) is ignored (status `IGNORED`, counted, no error), not a conflict. | Product |
| D9 | The report and the report screen are generic, because other imports of the project will reuse them. Backend: one shared report model (`io.openaev.api.import_report`) that knows nothing about markings: counts, the names of the row fields, and rows carrying their values as read in a map. The reason is a plain code string, owned by each feature. Frontend: one shared report dialog in `components/common/import/` that renders any such report and builds the "Download skipped rows" JSON file from the values (JSON because the marking file is; a later CSV import reusing the dialog would add a format option then). Marking only supplies its title and its reason labels. | Product |

## 3) What exists today (verified in code)

- Uniqueness is enforced on `(LOWER(type), LOWER(definition), tenant_id)`: migration `V6_20260914120000000__Add_marking_definitions` creates the unique index, and `MarkingDefinitionService.create` and `update` call `validateUniqueOrThrow`. So a duplicate type and definition cannot be created manually.
- Order carries no uniqueness rule: `create` and `update` only check `order >= 0` (`MarkingDefinitionInput`). Import applies the same rule and adds no order check of its own.
- `create` normalizes type and definition to upper case (`normalize`), forces `protected = false`, evicts the clearance cache and bumps a metric after commit.
- `MarkingDefinitionApi` writes with `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)` and resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)`.
- Frontend list: `MarkingDefinitions.tsx`. `PaginationComponentV2` has a `topBarButtons` slot holding `ButtonCreate` inside `<Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>`. The list is local state fed by `searchMarkingDefinitions`.

### Existing import and export code to reuse or avoid

| Piece | Where | Verdict |
|---|---|---|
| CSV export endpoint | `MapperApi#exportMappersCsv` -> `MapperService.exportCsv` (opencsv `StatefulBeanToCsvBuilder`, `CustomColumnPositionStrategy`, `text/csv`, filename `Prefix + yyyyMMddHHmm + .csv`) | Reuse only the endpoint shape and the filename convention. It is bound to `CsvType`, to a `MapperService` that does not belong to markings, and to the CSV format, so implement a dedicated JSON export in the marking service and controller. |
| CSV import | `MapperService.importMappersCsv` (temp file, opencsv `CsvToBean`, positional columns, `withSkipLines(1)`) | Avoid as a model, and the file is not CSV here. Its flaw applies to any parser: it is all-or-nothing, the first bad row throws and there is no per-row report, which AC3 and AC5 forbid. Jackson data binding into a typed class would have the same flaw (see 6.2). |
| Generic JSON:API import and export | `ZipJsonApi`, `ZipJsonService`, `GenericJsonApiExporter`, `GenericJsonApiImporter` in `openaev-model` (`io.openaev.jsonapi`), used by `ThreatArsenalApiExporter`, `ThreatArsenalApiImporter` and `CustomDashboardApiImporter` | Avoid. This is the project's generic JSON import and export (reflection-based, works on any `Base` entity, and `MarkingDefinition` is one through `TenantBase`), but it is built for one root entity with its included graph, wrapped in a ZIP. It exports one entity per file (not a list), suffixes the name with " (Import)", persists the root and merges included entities on their id, aborts the import on a failure and has no per-row report and no conflict outcome. It cannot give the partial, never-overwrite, per-row-report import of D1, D2 and D8. `FileExportBase` (scenario and exercise exports) is tied to their entity graph and does not fit either. |
| Multipart upload endpoint | `ThreatArsenalApiImporter#importJson`, `MapperApi#importMappers` (`@RequestPart("file") @NotNull MultipartFile`, `writeScopeResolver.tenantForWrite`) | Reuse the signature. |
| File validation | `MapperApi#validateUploadedFile` (extension allowlist, size cap, `UnsupportedMediaTypeException`, `FileTooBigException`) | Reuse the idea. Do not call it, it is private to `MapperApi`. Mirror it in the marking API (extension `json`), with a 1 MB cap. |
| Report returned to the client | `PayloadImportService` returns a `PayloadImportResult` | Look at it for naming before defining our report record. |
| Frontend export button | `components/common/ExportButton.tsx` (`CSVLink`, `download(data, filename, 'text/csv')`, filename read from `content-disposition`) | Reuse `download` (called with `application/json`) and the `content-disposition` handling. The component itself is tied to `exportType` strings, so write a small dedicated button. |
| Frontend import button | `components/common/ImportUploader.tsx` (`CloudUploadOutlined`, hidden file input, `fileAccepted`), used by `ImportUploaderEndpoints` | Reuse as is for file selection. The report dialog is new. |
| Frontend import action | `endpoint-actions.ts#importEndpoints` (`simplePostCall(url, FormData)`) | Same pattern for the multipart call. |

The export is pretty-printed UTF-8 JSON written with Jackson (already a dependency, no new one).

## 4) JSON contract

A UTF-8 JSON document (a BOM is tolerated): a top-level array with one flat object per marking, read by key name, so key order does not matter. This is the working contract until the OpenCTI example file arrives (section 8).

```json
[
  { "type": "TLP", "definition": "TLP:AMBER", "color": "#ffc107", "order": 3 }
]
```

- `type` and `definition` are strings, normalized exactly as `create` does (trim, upper case).
- `color` is a required hex colour. Check how `MarkingDefinitionForm` validates colour and apply the same rule server-side.
- `order` is an integer `>= 0`, written as a JSON number (`3`) or as a string of digits (`"3"`). The check is made on the text, so both are the same; `3.5`, `"abc"` or `true` are `INVALID_ORDER`.
- Unknown extra keys are ignored. For one item, a missing key, a `null`, or an object or array where a scalar is expected reads as empty: `MISSING_FIELD` on that row only. Items are independent, so there is no file-level missing-column error. File-level errors reject the whole file with one clear message: not valid JSON, top level is not an array, an item is not an object, empty array (nothing to import).
- Export writes the same four keys (`order` as a JSON number), sorted by type then order. No CSV-injection neutralisation: the file is not meant for a spreadsheet, and the report dialog shows values as plain text.
- No ids, no tenant id, no `protected` flag in the file.

Limits (decided): 1 MB and 1000 items. Above either limit the whole file is rejected with a clear error (`FileTooBigException` for the size, as `MapperApi` does; `BadRequestException` for the items). The size cap follows the existing imports, which cap only the file size: the platform-wide multipart limit is 5 GB, the XLS inject import caps at 25 MB, the credential file at 16 KB, and the endpoint CSV import has no cap at all. No existing import caps rows. The 1000-row cap is here to bound the report returned to the UI.

## 5) Row validation order and report

Each row gets exactly one outcome. Checks run in this order and the first failure wins:

1. `MISSING_FIELD` - type, definition, color or order empty or absent.
2. `INVALID_COLOR` - not a valid hex colour.
3. `INVALID_ORDER` - not an integer, or below 0.
4. `CONFLICT` - one reason for both cases: the normalized `(type, definition)` already exists in the tenant (includes protected defaults), or an earlier row of the same file already used it (the first row wins). Never overwritten (D1). The existing marking is checked first, so a key that exists in the tenant is always compared with the existing marking, whatever the earlier file rows. The `message` is precise, built from fixed text and states two things:
   - Where the conflict is: "an existing marking" or "item N of the file".
   - What differs from that other marking: "color differs (this row #ffc107, the existing marking has #ff0000)", "order differs (this row 3, item 5 has 2)", or both. The values shown are the validated hex colour and integer order, never free text, so nothing from the file is echoed back unchecked.

   A row identical to that other marking (same colour and same order) is not a conflict: it is ignored. It gets the status `IGNORED` with no reason, it is counted in `ignored`, it is not an error, and it is neither shown in the skipped table nor included in "Download skipped rows". So re-importing an exported file reports its rows as ignored, not as conflicts.

   Users resolve it by editing the existing marking or the file.

Rows that pass all checks are created. The report is the generic one (D9), nothing in it is marking-specific:

```
ImportReport {
  int created;
  int skipped;               // rejected rows (status SKIPPED, with a reason)
  int ignored;               // identical to an existing or earlier row (status IGNORED, no reason)
  List<String> columns;      // the field names of a row, in display order: type, definition, color, order
  List<ImportReportRow> rows;  // every row with its status, so the UI can show each group
}
ImportReportRow {
  int position;                   // 1-based position of the item in the file (the first item of the array is 1)
  ImportReportRowStatus status;   // CREATED, SKIPPED, IGNORED
  String reason;                  // feature-owned code, null unless SKIPPED: for markings MISSING_FIELD, INVALID_COLOR, INVALID_ORDER, CONFLICT
  String message;                 // fixed text built server-side, null when CREATED or IGNORED
  Map<String, String> values;     // the row's fields as read (scalars as text), keyed by field name
}
```

`values` echoes the user's own fields back to them, including invalid ones (that is what lets the user see and fix them). They are rendered as plain text by the dialog, never as HTML, and the skipped-rows download writes them as JSON strings (nothing is interpreted, no neutralisation needed). `values` are read from `MarkingRow`, not from the raw JSON, so the report and the download keep the flat shape of section 4 whatever shape the OpenCTI file has (section 8). The `message` remains fixed text and never echoes anything.

Positions count the items of the array (the first item is 1), so the user can find the row in the file, and the dialog shows the row's values. The `message` is built server-side from fixed text, never from an exception message (security rule: no raw errors to clients).

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

### 6.2 Parser: new `MarkingJsonParser` (stateless, in the service package)

- Input: `InputStream`. Output: `List<MarkingRow>` where `MarkingRow` holds the raw fields as text plus the position of the item. It does no business validation, so a second parser (the OpenCTI shape, if it differs from section 4) can produce the same `MarkingRow`.
- File-level failures (invalid JSON, top level not an array, an item that is not an object, empty array, too many items, unreadable) raise a `BadRequestException` with a fixed message.
- Uses Jackson (already a dependency): reads the tree (`readTree`), then takes the four keys of each item by name. Do not bind to a typed class or record: a bad `order` would fail Jackson's mapping for the whole file, where it must be one row's `INVALID_ORDER`. Scalars become text, anything else is empty (section 4). Jackson reads a UTF-8 BOM; confirm it in `MarkingJsonParserTest`.

### 6.3 Controller: `MarkingDefinitionApi`

Add two endpoints under `// -- EXPORT --` and `// -- IMPORT --`:

- `GET /export`, `@AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.MARKING_DEFINITION)` (decision D7: `ACCESS_MARKING_DEFINITION` carries `READ` and `SEARCH`, as `MapperApi#exportMappersCsv` uses `Action.READ`). Read-only holders can therefore download. Streams `application/json` (a pretty-printed array of `MarkingDefinitionExportRow`) through `StreamingResponseBody`, filename `MarkingDefinitions<yyyyMMddHHmm>.json`.
- `POST /import`, `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`, `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)`, `@RequestPart("file") @NotNull MultipartFile`. Validates extension `json` and size, parses, resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)`, calls `importAll`, returns the generic `ImportReport`. `@LogExecutionTime` and `@Operation` on both.
- Both declare `TxCtx ctx` (the table is v2-active) and are registered in `TenantScopedEntrypointsTxCtxArchTest`.
- Feature flag: same as the other `MarkingDefinitionApi` endpoints, which carry no explicit flag guard in the controller. The two new endpoints add none either; the capability tree (`CapabilityTreeBuilder`) already hides the marking capabilities when `MARKING` is off, and the frontend masks the page.

### 6.4 DTOs (records, `io.openaev.api.marking_definition.form`)

- Shared report model in `io.openaev.api.import_report` (not in the marking package, D9): records `ImportReport` and `ImportReportRow`, enum `ImportReportRowStatus`. Marking keeps only its own enum of reason codes, `MarkingDefinitionImportRowReason`, whose `name()` goes into `ImportReportRow.reason`. All properties `@JsonProperty` snake_case like the other marking DTOs. No tenant id.
- The generic model must stay free of any marking type, so the next import (endpoints, mappers, ...) can return it as is. The endpoint returns `ImportReport`.
- Export row: record `MarkingDefinitionExportRow` (`type`, `definition`, `color`, `order` as `Integer`) in the same package, so the export is typed while the import reads loosely (6.2).

### 6.5 What does not need a migration

No schema change: no new column, no new index. The duplicate check runs in memory against one tenant's rows.

## 7) Frontend

Files in `openaev-front/src/admin/components/settings/marking_definitions/` and `src/actions/marking_definitions/`.

- Actions (`marking-definition-actions.ts`): `exportMarkingDefinitions()` (same shape as `exportCsvMapper`: returns data plus the filename from `content-disposition`, then the existing `download(..., 'application/json')`) and `importMarkingDefinitions(formData)` (`simplePostCall` with `FormData`, like `importEndpoints`).
- Toolbar, next to `ButtonCreate`, with the same icons as the Endpoints and Threat Arsenal lists. The import button goes inside the existing `topBarButtons` `<Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>`. The export button goes outside it, in its own `<Can I={ACTIONS.ACCESS} a={SUBJECTS.MARKING_DEFINITION}>` (decision D7), so a read-only user still sees it:
  - Import: the existing `ImportUploader` (secondary `IconButton`, `CloudUploadOutlined`, hidden file input), with `title={t('Import marking definitions')}` and `fileAccepted=".json"`. Its `handleUpload(formData, file)` calls the import action, then opens the report dialog. It sets its own spinner flag to false right after calling `handleUpload`, so loading state and the result live in the parent.
  - Export: a small dedicated button, secondary `IconButton`, `FileDownloadOutlined`, `aria-label` and tooltip "Export this list" (same label and icon as `ExportButton`, which is tied to its `exportType` strings), disabled when the list is empty.
  - Library components first (IconButton, Tooltip); the report dialog stays MUI with a `fds:keep-mui` comment where the conventions require it.
- No separate file-picker dialog: the picker is the hidden input behind `ImportUploader`. The report dialog also offers a way to download an example file (the export can serve as the template).
- Report dialog, generic (D9): `components/common/import/ImportReportDialog.tsx`, opened once the import call returns. It knows nothing about markings. Props: `open`, `onClose`, `title`, `report` (the generated `ImportReport` type) or `error` (file-level failure message), `reasonLabels` (reason code to translated label, a code without label falls back to the code), `downloadFileName`. It renders:
  - Summary line, for example "12 imported, 3 skipped, 2 ignored (already identical)".
  - A table of skipped rows: position, one column per entry of `report.columns` (field values as plain text), reason (translated), and the message. Created and ignored rows are not listed by default.
  - A "Download skipped rows" button that writes a JSON file of only the skipped rows: an array of their `values` objects, in the shape of the import file, so the user fixes it and re-imports. Built client-side from the report. JSON only for now: a later CSV import reusing the dialog adds a format option then.
  - A clear state for a file-level failure (400): the message from the server, no table.
- Marking side: `MarkingDefinitionImport.tsx` renders the generic dialog with `title={t('Import marking definitions')}` and the marking reason labels. No marking-specific report component.
- After a successful import, refetch the list (the `upsert` + `incrementTotalElements` helpers fit one row, not a batch). Check how `PaginationComponentV2` exposes a refetch; if none, bump a key or call `searchMarkingDefinitions` again with the current `searchPaginationInput`.
- i18n: every new string through `t()`, keys in English, added to the language files that the repo requires.
- Types: add the DTOs on the Java side first, then `yarn generate-types-from-api`. No hand-written copies of those types.
- Flag and permission: the page is already masked when `MARKING` is off (`SecurityMenu`, `isFeatureEnabled('MARKING')`), the import button is inside `Can MANAGE` and the export button inside `Can ACCESS`.

## 8) Open points (not blocking the plan)

1. OpenCTI file (D4, D6): the OpenCTI team will send an example file, in JSON. When it arrives, compare it with the contract of section 4: the envelope (a plain array, or an object wrapping the list), the key names, how type, definition, colour and order are carried, and whether it holds fields we ignore. Then either adapt `MarkingJsonParser` to read it, or add a second parser behind `MarkingRow`: the validation, the report and the dialog do not change. If the OpenCTI shape is to be the one format, the export must write it too, so that a file exported here imports in OpenCTI and the reverse; that changes `MarkingDefinitionExportRow` and the export, so decide it with the example in hand. Until then sections 4 and 6 are the working contract.

## 9) Tests

Follow `add-test` (fixture, composer, `@Nested` + `@DisplayName`, `given_X_should_Y`, AAA comments, OpenAEV's `@WithMockUser`).

Backend, `MarkingDefinitionServiceTest` and `MarkingDefinitionApiTest` (extend what exists):
- Export: returns the tenant's rows only, sorted, includes protected rows, no id or tenant id in the file, `order` written as a number, quotes and accents escaped and re-importable.
- Import, one test per outcome: all-valid file, `MISSING_FIELD`, `INVALID_COLOR`, `INVALID_ORDER`, `CONFLICT` (one test per message variant: against an existing marking and against an earlier file row, each with colour differs, order differs, both differ; including a protected TLP), and `IGNORED` (a row identical to an existing marking, and to an earlier file row: no reason, counted in `ignored`, not in `skipped`, not in the skipped-rows download).
- Partial import: a mixed file creates the valid rows, skips the others, and the report positions match the items of the file.
- Never overwrites: an existing marking keeps its colour and order after an import that conflicts with it.
- File handling: reordered keys, extra keys, BOM, a key missing from one item (`MISSING_FIELD` on that row, not a file-level error), a non-scalar value, `order` as a number and as a string of digits, `order` `3.5`, invalid JSON, a top-level object instead of an array, an item that is not an object, empty array, too many items, too large, wrong extension (the file-level cases are a 400).
- Query count with Hibernate `Statistics` (ORM doctrine): an import of N rows does not issue N uniqueness queries.
- Cache: one `evictAll()` per import.
- Permissions: no capability gets 403 on both endpoints; read-only (`ACCESS` without `MANAGE`) can export (200) but gets 403 on import; `MANAGE` can do both. Public access not applicable.
- Tenant isolation: use the tenant isolation test skill. Importing as tenant A never touches tenant B; exporting as A never contains B's rows. The same `(type, definition)` in two tenants is not a conflict.
- `TenantScopedEntrypointsTxCtxArchTest` stays green with the two new entrypoints registered.

Frontend (component tests next to the existing marking ones): import button only under the manage permission, export button under the access permission (visible to a read-only user), the import control accepts only `.json`, list refresh after an import. The generic `ImportReportDialog` gets its own tests, with a fake report that has nothing to do with markings (other columns, other reason codes): summary line, one table column per report column, each skipped row, no created or ignored row listed, reason label and fallback to the raw code, the "download skipped rows" JSON content (an array of the skipped rows' `values` objects), file-level error state.

## 10) Review gates and docs

Run: `review-code`, `review-security`, `review-multi-tenancy`, `review-performance`, `review-frontend`, `review-docs`. `review-migration` and `review-chaining-engine`: N/A (no migration, no chaining). `reduce-tx-baseline`: only if a new transaction pattern shows up.

Docs: add the import and export behaviour (JSON fields, the skip reasons, the "never overwrites" rule) to the marking definitions page under `docs/`.

Before opening the PR: format Java with `.\scripts\hooks\format-java.ps1`, run `yarn lint && yarn check-ts` and `yarn generate-types-from-api`. PR title follows Conventional Commits and ends with `(#8690)`; description follows `.github/PULL_REQUEST_TEMPLATE.md`.

## 11) Scope summary: files to create or modify

Paths are relative to the repository root. Backend main code is under `openaev-api/src/main/java/io/openaev/`, backend tests under `openaev-api/src/test/java/io/openaev/`, frontend under `openaev-front/src/`.

### Backend, create

| File | Purpose |
|---|---|
| `service/marking_definition/MarkingRow.java` | Record: raw fields as text plus the position of the item in the file. The format boundary (D6). |
| `service/marking_definition/MarkingJsonParser.java` | Stateless Jackson tree parser, file-level validation (JSON, array, objects, item cap). |
| `api/import_report/ImportReport.java` | Generic record: created, skipped, ignored counts, field names, rows (D9). |
| `api/import_report/ImportReportRow.java` | Generic record: position, status, reason code, message, values by field name. |
| `api/import_report/ImportReportRowStatus.java` | Enum `CREATED`, `SKIPPED`, `IGNORED`. |
| `api/marking_definition/form/MarkingDefinitionImportRowReason.java` | Marking's own reason codes: `MISSING_FIELD`, `INVALID_COLOR`, `INVALID_ORDER`, `CONFLICT`. |
| `api/marking_definition/form/MarkingDefinitionExportRow.java` | Record of the exported file: `type`, `definition`, `color`, `order` (`Integer`). |

### Backend, modify

| File | Change |
|---|---|
| `service/marking_definition/MarkingDefinitionService.java` | Add `exportAll(ctx)` and `importAll(ctx, tenantId, rows)`; extract the shared normalize and entity-building code used by `create`. Add a metric increment by N after commit. |
| `api/marking_definition/MarkingDefinitionApi.java` | Add `GET /export` (JSON stream) and `POST /import` (multipart) under `// -- EXPORT --` and `// -- IMPORT --`. File extension and size checks. |
| `api/marking_definition/MarkingDefinitionMapper.java` | Add the row mapping used by the export, and the import-row mapping, if the service does not need them directly. |
| `telemetry/metric_collectors/ActionMetricCollector.java` | Only if the existing created counter cannot be incremented by N; check before touching. |

### Backend, tests (modify unless stated)

| File | Change |
|---|---|
| `service/marking_definition/MarkingJsonParserTest.java` (create) | Parser unit tests: key handling, BOM, invalid JSON, not an array, item cap, empty array. |
| `service/marking_definition/MarkingDefinitionServiceTest.java` | Export and import outcomes, partial import, never-overwrite, query count, one cache eviction. |
| `api/marking_definition/MarkingDefinitionApiTest.java` | Both endpoints: permissions, file validation, report content, no id or tenant id in the export. |
| `service/marking_definition/MarkingDefinitionTenantScopeTest.java` | Tenant isolation of export and import (see the add-tenant-isolation-test skill). |
| `architecture/TenantScopedEntrypointsTxCtxArchTest.java` | Register `MarkingDefinitionApi#exportMarkingDefinitions` and `#importMarkingDefinitions` in the `marking_definitions (v2)` block (final method names to match the controller). |
| `utils/fixtures/MarkingDefinitionFixture.java` and `utils/fixtures/composers/MarkingDefinitionComposer.java` | Only if a JSON-content helper is needed; reuse them for the data setup. |

### Frontend, create

| File | Purpose |
|---|---|
| `components/common/import/ImportReportDialog.tsx` | Generic report dialog (D9): summary, skipped rows table, download of skipped rows, file-level error state. Reused by other imports. |
| `__tests__/components/common/import/ImportReportDialog.test.tsx` | Tests of the generic dialog with a non-marking fake report (check the existing test folder layout for the final path). |
| `admin/components/settings/marking_definitions/MarkingDefinitionExportButton.tsx` | Dedicated export icon button (`FileDownloadOutlined`, "Export this list"). |
| `admin/components/settings/marking_definitions/MarkingDefinitionImport.tsx` | Wraps `ImportUploader` (`.json`), calls the import action, opens the report dialog. |
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
- The generic JSON:API classes (`ZipJsonApi`, `GenericJsonApiImporter`, ...) are neither used nor changed (section 3).

## 12) Suggested order of work

1. `MarkingRow`, `MarkingJsonParser`, DTOs and their unit tests.
2. `MarkingDefinitionService.importAll` and `exportAll` with service tests.
3. Controller endpoints, `TxCtx` arch-test registration, API tests including permissions and tenant isolation.
4. Regenerate the TypeScript types.
5. Frontend actions, toolbar buttons (`ImportUploader` and the export icon button), import report dialog, component tests.
6. Docs and review gates.