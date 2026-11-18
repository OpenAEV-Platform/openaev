# Task.1 US.7 - Export and import marking definitions (issue #8690)

Complements `implementation-plan.md`, which deferred import/export (section 2, "Import/export behavior is deferred"). Parent: #7511. Feature flag: `MARKING` (`PreviewFeature.MARKING` on the backend, `isFeatureEnabled('MARKING')` on the frontend).

## 1) Goal

A user holding the access marking definitions capability exports the tenant's marking definitions to a CSV file, and a user holding the manage marking definitions capability imports a compatible CSV file into another tenant or instance. The import is one step, partial, and never overwrites. The UI shows a per-row report, including every conflicting row.

Out of scope: marking assignments to assets, roles and groups. Compatibility with the OpenCTI file format: a separate issue (the OpenCTI example is not consistent for now); here the format is the CSV of section 4, the same CSV as the other lists of the platform. Clearance on the import is in scope (D14): a row above the caller's clearance is skipped with the reason `INSUFFICIENT_ACCESS`. The clearance rules of the manual actions (create, delete, see) are a separate issue; the export is not narrowed by clearance, like the list.

## 2) Decisions

| # | Decision | Source |
|---|---|---|
| D1 | Conflict (the issue's "order conflict for the same type and reference"): a row whose type and definition already exist, whatever its order or colour, is rejected with the single reason `CONFLICT` (also for a repeat inside the file), never overwritten, and shown in the UI report with a message stating what differs. | Product |
| D2 | One-step import. The report is returned by the import call and displayed in the UI. No dry-run endpoint in this issue. | Product |
| D3 | Export includes protected default markings (TLP). Importing them back unchanged reports them as `IGNORED` (D8); if the file's colour or order was edited, as `CONFLICT`. | Product |
| D4 | The file is CSV, as for all the lists of the platform. Column names and structure are defined in section 4. OpenCTI compatibility is a separate issue. | Product |
| D5 | Feature flag is `MARKING`. | Product |
| D6 | The parser sits behind a `MarkingRow` boundary, so the row validation and the report do not depend on how the file is read. | Product |
| D7 | Export needs the `ACCESS` capability (`ACCESS_MARKING_DEFINITION`, `Action.READ`), import needs `MANAGE` (`Action.WRITE`). | Product |
| D8 | A row identical to an existing marking or to an earlier row of the file (same colour and order) is ignored (status `IGNORED`, counted, no error), not a conflict. | Product |
| D9 | The report and the report screen are generic, because other imports of the project will reuse them. Backend: one shared report model (`io.openaev.api.import_report`) that knows nothing about markings: counts, the names of the row fields, and rows carrying their values as read in a map. The reason is a plain code string, owned by each feature. Frontend: one shared report dialog in `components/common/import/` that renders any such report and builds the "Download skipped rows" file from the values. Marking only supplies its title and its reason labels. | Product |
| D10 | Export and import go through `MarkingDefinitionApi` and `MapperService`, with a new `CsvType.MARKING_DEFINITIONS`. Export: one new case in `resolveCsvExportConfig`, so the CSV writing (opencsv, `CustomColumnPositionStrategy`, content type, filename `MarkingDefinitions<yyyyMMddHHmm>.csv`) is the existing `exportCsv`. Import: one new case in `MapperService.importMappersCsv`, which now returns the `ImportReport` (`null` for the cases that do not produce one). The marking case does not use the method's positional `CsvToBean` reading (section 3): it hands the file to `MarkingCsvParser` and to the marking service, which do the per-row validation and the partial import. | Product |
| D11 | CSV injection is neutralised, since a CSV is opened in a spreadsheet. On export, a `type` or `definition` starting with `=`, `+`, `-`, `@`, tab or carriage return gets a leading `'`. On import, a single leading `'` followed by one of those characters is removed, so the round trip is lossless. | Product |
| D12 | The export endpoint follows the existing CSV export shape: `POST /export/csv` with a `SearchPaginationInput` body, so "Export this list" exports what the list currently shows (an empty input exports the whole tenant). | Product |
| D13 | Separator: strict, as for the Endpoints CSV. The parser reads and the export writes only `DEFAULT_SEPARATOR`; no sniffing of another separator (for example the `;` of a French-locale Excel). A file with another separator fails the header check with the file-level "required column missing" error. | Product |
| D14 | A marking above the caller's clearance cannot be imported: its row is `SKIPPED` with the reason `INSUFFICIENT_ACCESS`, listed in the report like any skipped row. Clearance is the one `MarkingScopeResolver` defines: per type, the caller holds every order at or below the highest order they were granted, and a type they were granted nothing on contributes nothing. A row is above the clearance when the caller holds no marking of its type, or its order is greater than the highest order they hold for that type. Admin and bypass callers are exempt (they hold the whole tenant scale, and can introduce a new type or a higher order). | Product |

## 3) What exists today (verified in code)

- Uniqueness is enforced on `(LOWER(type), LOWER(definition), tenant_id)`: migration `V6_20260914120000000__Add_marking_definitions` creates the unique index, and `MarkingDefinitionService.create` and `update` call `validateUniqueOrThrow`. So a duplicate type and definition cannot be created manually.
- Order carries no uniqueness rule: `create` and `update` only check `order >= 0` (`MarkingDefinitionInput`). Import applies the same rule and adds no order check of its own.
- `create` normalizes type and definition to upper case (`normalize`), forces `protected = false`, evicts the clearance cache and bumps a metric after commit.
- `MarkingDefinitionApi` writes with `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)` and resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)`. It injects `MarkingDefinitionService`, `TenantWriteScopeResolver` and `UserService`.
- Frontend list: `MarkingDefinitions.tsx`. `PaginationComponentV2` has a `topBarButtons` slot holding `ButtonCreate` inside `<Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>`. The list is local state fed by `searchMarkingDefinitions`.

### Existing import and export code to reuse or avoid

| Piece | Where | Verdict |
|---|---|---|
| CSV export | `MapperService#exportMappersCsv(CsvType, SearchPaginationInput, HttpServletResponse)` -> `resolveCsvExportConfig(csvType)` -> private static `exportCsv(response, filename, exports, exportClass)` (opencsv `StatefulBeanToCsvBuilder`, `CustomColumnPositionStrategy`, `DEFAULT_SEPARATOR`, `DEFAULT_QUOTE_CHARACTER`, `text/csv`, filename `Prefix + yyyyMMddHHmm + .csv`). Already used with `CsvType.ENDPOINTS` (`MapperApi#exportMappersCsv`) and `CsvType.INJECTOR_CONTRACTS` (`ThreatArsenalApiExporter#exportCsv`, a dedicated `POST /export/csv` endpoint on the feature's own API). | **Reuse** (D10). Add `MARKING_DEFINITIONS` to `CsvType`, one `CsvExportConfig` in `resolveCsvExportConfig`, and a `POST /export/csv` endpoint in `MarkingDefinitionApi`, the way `ThreatArsenalApiExporter` does. Nothing is changed in `exportCsv` itself. |
| CSV import | `MapperService.importMappersCsv(TxCtx, MultipartFile, CsvType)` (temp file, opencsv `CsvToBean`, positional columns, `withSkipLines(1)`, returns `void`, `switch` on `CsvType` with `ENDPOINTS` only), called by `MapperApi#importEndpoints` | **Reuse as the entry point** (D10): add a `MARKING_DEFINITIONS` case and change the return type to `ImportReport`. **Do not reuse its reading and persisting for markings**: the `ENDPOINTS` path binds positionally into a typed bean and is all-or-nothing (the first bad row throws, no per-row report), which AC3 and AC5 forbid, and one bad `order` would fail the whole file. So the marking case branches before the temp file and `CsvToBean`, and calls `MarkingCsvParser` (same opencsv `CSVReader` / `CSVParserBuilder` separator and quote settings) then `MarkingDefinitionService.importAll`. The `ENDPOINTS` behaviour is unchanged. |
| Generic JSON:API import and export | `ZipJsonApi`, `ZipJsonService`, `GenericJsonApiExporter`, `GenericJsonApiImporter` in `openaev-model` (`io.openaev.jsonapi`), used by `ThreatArsenalApiExporter`, `ThreatArsenalApiImporter` and `CustomDashboardApiImporter` | Avoid. Built for one root entity with its included graph, wrapped in a ZIP, aborts the import on a failure and has no per-row report and no conflict outcome. It cannot give the partial, never-overwrite, per-row-report import of D1, D2 and D8. `FileExportBase` (scenario and exercise exports) is tied to their entity graph and does not fit either. |
| Multipart upload endpoint | `MapperApi#importMappers`, `MapperApi#importEndpoints` (`@RequestPart("file") @NotNull MultipartFile`, `writeScopeResolver.tenantForWrite`) | Reuse the signature. |
| File validation | `MapperApi#validateUploadedFile` (extension allowlist, size cap, `UnsupportedMediaTypeException`, `FileTooBigException`) | Reuse the idea. It is private to `MapperApi`: mirror it in the marking API (extension `csv`), with a 1 MB cap. |
| Report returned to the client | `PayloadImportService` returns a `PayloadImportResult` | Look at it for naming before defining our report record. |
| Frontend export | `components/common/ExportButton.tsx`, `exportCsvMapper` | The flow (POST the search input, filename read from `content-disposition`, `download(data, filename, 'text/csv')`) is what we need. The component is tied to `exportType` strings, so write a small dedicated button around the same action shape. |
| Frontend import button | `components/common/ImportUploader.tsx` (`CloudUploadOutlined`, hidden file input, `fileAccepted`), used by `ImportUploaderEndpoints` | Reuse as is for file selection. The report dialog is new. |
| Frontend import action | `endpoint-actions.ts#importEndpoints` (`simplePostCall(url, FormData)`) | Same pattern for the multipart call. |

The CSV is read with opencsv's `CSVReader` (already a dependency, used by `MapperService`), no new dependency.

## 4) CSV contract

A UTF-8 CSV document with a header row, written and read with the project's `DEFAULT_SEPARATOR` and `DEFAULT_QUOTE_CHARACTER` (the constants `MapperService` already uses, so a file exported here is read back here). A leading BOM is tolerated (Excel "CSV UTF-8" writes one; the parser strips U+FEFF itself, opencsv does not).

```csv
type,definition,color,order
TLP,TLP:AMBER,#ffc107,3
```

- Columns are `type`, `definition`, `color`, `order`. On import they are read by header name (case-insensitive, trimmed), so column order does not matter and unknown extra columns are ignored. Export writes them in this order, through `MarkingDefinitionCsvRow` (`@CsvBindByName` + `@CsvBindByPosition`, all `String`, like `InjectorContractExport`).
- `type` and `definition` are normalized exactly as `create` does (trim, upper case), after the D11 un-neutralisation.
- `color` is a required hex colour. Check how `MarkingDefinitionForm` validates colour and apply the same rule server-side.
- `order` is an integer `>= 0`, a string of digits. The check is made on the text; `3.5`, `abc` or `-1` are `INVALID_ORDER`.
- A row is one record. A row shorter than the header reads its missing cells as empty (`MISSING_FIELD` on that row only), a longer row ignores its extra cells. Fully blank lines are skipped and do not count as rows. Rows are independent: one bad row never rejects the file.
- File-level errors reject the whole file with one clear message (`BadRequestException`, fixed text): empty file, header row only (nothing to import), a required column (`type`, `definition`, `color`, `order`) missing from the header (it would make every row `MISSING_FIELD`), a malformed CSV (unbalanced quote, `CsvMalformedLineException`), unreadable file, not UTF-8.
- Export sorts by type then order and neutralises values against CSV injection (D11). It has the same shape as the import, so exporting then importing in another tenant works unchanged.
- No ids, no tenant id, no `protected` flag in the file.

Limits (decided): 1 MB and 1000 rows. Above either limit the whole file is rejected with a clear error (`FileTooBigException` for the size, as `MapperApi` does; `BadRequestException` for the rows). The size cap follows the existing imports, which cap only the file size: the platform-wide multipart limit is 5 GB, the XLS inject import caps at 25 MB, the credential file at 16 KB, and the endpoint CSV import has no cap at all. No existing import caps rows. The 1000-row cap is here to bound the report returned to the UI.

## 5) Row validation order and report

Each row gets exactly one outcome. Checks run in this order and the first failure wins:

1. `MISSING_FIELD` - type, definition, color or order empty or absent.
2. `INVALID_COLOR` - not a valid hex colour.
3. `INVALID_ORDER` - not an integer, or below 0.
4. `CONFLICT` - one reason for both cases: the normalized `(type, definition)` already exists in the tenant (includes protected defaults), or an earlier row of the same file that is created already used it (the first row wins; a row skipped for any reason does not use its key). Never overwritten (D1). The existing marking is checked first, so a key that exists in the tenant is always compared with the existing marking, whatever the earlier file rows. The `message` is precise, built from fixed text and states two things:
   - Where the conflict is: "an existing marking" or "row N of the file".
   - What differs from that other marking: "color differs (this row #ffc107, the existing marking has #ff0000)", "order differs (this row 3, row 5 has 2)", or both. The values shown are the validated hex colour and integer order, never free text, so nothing from the file is echoed back unchecked.

   A row identical to that other marking (same colour and same order) is not a conflict: it is ignored. It gets the status `IGNORED` with no reason, it is counted in `ignored`, it is not an error, and it is neither shown in the skipped table nor included in "Download skipped rows". So re-importing an exported file reports its rows as ignored, not as conflicts.

   Users resolve it by editing the existing marking or the file.
5. `INSUFFICIENT_ACCESS` - the row would be created, but its type and order are above the caller's clearance (D14). It is checked last on purpose: it only applies to a row that would create a marking, so a row that is `IGNORED` (identical to an existing marking) or a `CONFLICT` keeps that outcome even when its order is above the caller's clearance, and re-importing an exported file does not flood a limited-clearance user with access errors. The `message` is fixed text with integers only, never the free-text type: "its order (5) is above the highest order you hold for its type (3)" or "you hold no marking of its type".
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
  int position;                   // 1-based position of the data row in the file (the first row under the header is 1)
  ImportReportRowStatus status;   // CREATED, SKIPPED, IGNORED
  String reason;                  // feature-owned code, null unless SKIPPED: for markings MISSING_FIELD, INVALID_COLOR, INVALID_ORDER, CONFLICT, INSUFFICIENT_ACCESS
  String message;                 // fixed text built server-side, null when CREATED or IGNORED
  Map<String, String> values;     // the row's fields as read (text), keyed by field name
}
```

`values` echoes the user's own fields back to them, including invalid ones (that is what lets the user see and fix them). They are rendered as plain text by the dialog, never as HTML. `values` are read from `MarkingRow` (after the D11 un-neutralisation), not from the raw file, so the report keeps the same flat shape whatever the file reading is. The skipped-rows download is written by the frontend (section 7), which neutralises its cells as in D11. The `message` remains fixed text and never echoes anything.

Positions count the data rows (the first row under the header is 1), so the user can find the row in the file, and the dialog shows the row's values. The `message` is built server-side from fixed text, never from an exception message (security rule: no raw errors to clients).

## 6) Backend

Package `io.openaev.api.marking_definition` and `io.openaev.service.marking_definition`. No code in `openaev-framework` or `io.openaev.rest`. `MapperService` (`io.openaev.service`) and `CsvType` (`io.openaev.utils`) are existing classes that get a small addition, as `ENDPOINTS` and `INJECTOR_CONTRACTS` did.

### 6.1 Service: `MarkingDefinitionService`

- `exportAll(TxCtx ctx, String tenantId, SearchPaginationInput input)`: returns the marking definitions of that one tenant matching the input's filter and text search, unpaginated, sorted by type then order. The query is `repository.findAll(tenantSpecification(Set.of(tenantId)).and(computeFilterGroupJpa(...)).and(computeSearchJpa(...)), Sort.by("type", "order"))`, the same filter helpers `MapperService` uses for the endpoints export (`search` goes through `buildPaginationJPA`, which is paginated and does not fit). Add `@Transactional(readOnly = true)`. Called by `MapperService` (D10), so the owning service exposes it, as the layering rule requires (`MapperService` never touches the marking repository). It takes one tenant, not the whole `TxCtx` scope: a file mixing several tenants' markings would hold the same `(type, definition)` several times and could not be imported back.
- `importAll(TxCtx ctx, String tenantId, String userId, boolean bypass, List<MarkingRow> rows)` (same identity parameters as `listAssignable`):
  - Load the tenant's existing markings once, in memory (keyed by normalized `(type, definition)`). No per-row query, no `findById` loop.
  - Clearance (D14): unless `bypass`, read the caller's clearance once with `markingClearanceCacheManager.findClearance(userId, tenantId, bypass)` and reduce it to the highest held order per type, using the already loaded markings (the ids of the `MarkingCtx.Restricted` set mapped to their type and order; `MarkingCtx.None` means nothing held). No extra query. A row that would be created and whose order is above the held order of its type (or whose type is not held) gets `INSUFFICIENT_ACCESS`, is not built, and does not register its key. Do not call `MarkingEscalationValidator.assertCanAssignMarkings`: it throws on the first unheld marking and works on existing ids, where a row is not a marking yet. Keep the order comparison in one private helper, so the manual `create` can reuse it when the clearance rules of the manual actions land.
  - Validate every row in memory (section 5), build entities for the valid rows, then one `saveAll`.
  - One `markingClearanceCacheManager.evictAll()` after the batch (not per row: `create` evicts per call).
  - Metric: increment the created counter by the number created, after commit, through the existing `runAfterCommit`.
  - Returns the report. A bad row never throws, so it can't roll the batch back.
  - Concurrent imports of the same key can both pass the in-memory check; the second then fails on the unique `(LOWER(type), LOWER(definition), tenant_id)` index and its whole batch rolls back. Accepted, no extra code: the index is the safety net, nothing is half-written, and the user simply re-runs the import (the first one's rows then come back as `IGNORED` or `CONFLICT`).
  - Rows go through the same `normalize` and set `protected = false`.
- Extract the shared bits (`normalize`, entity construction) so `create` and `importAll` don't diverge. Do not call `create` in a loop: it re-checks uniqueness with a query per row and evicts the cache per row.

### 6.2 Export and import through `MapperService` (D10)

- `CsvType`: add `MARKING_DEFINITIONS`.
- `MapperService.exportMappersCsv` gets the request's `TxCtx` as a new first parameter, `exportMappersCsv(TxCtx ctx, CsvType, SearchPaginationInput, HttpServletResponse)`, and passes it to `resolveCsvExportConfig(ctx, csvType)`. Its two existing callers, `MapperApi#exportMappersCsv` and `ThreatArsenalApiExporter#exportCsv`, already declare `TxCtx ctx` and just pass it. The `ENDPOINTS` and `INJECTOR_CONTRACTS` cases ignore it.
- `MapperService.resolveCsvExportConfig`: add

  ```java
  case MARKING_DEFINITIONS ->
      new CsvExportConfig<>(
          "MarkingDefinitions",
          MarkingDefinitionCsvRow.class,
          input -> getMarkingDefinitionsToExport(ctx, input));
  ```

  The filename prefix is `MarkingDefinitions`, so `buildExportCsvFilename` gives `MarkingDefinitions<yyyyMMddHHmm>.csv`, the convention of the other exports.
- `MapperService.getMarkingDefinitionsToExport(TxCtx ctx, SearchPaginationInput input)`: resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)` (as the import and `MarkingDefinitionApi#assignable` do: one tenant, an ambiguous multi-tenant scope is refused with a 400), calls `markingDefinitionService.exportAll(ctx, tenantId, input)` and maps each entity to `MarkingDefinitionCsvRow` through the mapper of 6.4, which applies the D11 neutralisation.
- `MapperApi` guard: `MapperApi#exportMappersCsv` and `#importEndpoints` forward a client-supplied `csvType` under `ResourceType.MAPPER`, so without a guard `?csvType=MARKING_DEFINITIONS` would export or create markings with only the mapper capability, bypassing D7. The same hole already exists for `INJECTOR_CONTRACTS`: `?csvType=INJECTOR_CONTRACTS` exports the threat arsenal with only the mapper READ, bypassing the `THREAT_ARSENAL` READ of `ThreatArsenalApiExporter#exportCsv`. One guard covers both: a constant `Set<CsvType> TYPES_WITH_DEDICATED_API = Set.of(INJECTOR_CONTRACTS, MARKING_DEFINITIONS)` in `MapperApi`, and both endpoints reject a type of the set with a `BadRequestException` ("This CSV type is handled by its own API") before calling `MapperService`. This is safe for the existing callers: the frontend's threat arsenal list passes its own export function (`exportThreatArsenalCsvMapper`, URL `.../threat_arsenal/export/csv`), so nothing in the frontend calls the mapper endpoint with `INJECTOR_CONTRACTS`, and `INJECTOR_CONTRACTS` has no import case anyway. The dedicated endpoints are the only way to reach those two cases, under their own capabilities.
- New dependencies of `MapperService`: `MarkingDefinitionService` (export and import) and `UserService` (the caller's identity for the import clearance check, as `MarkingDefinitionApi#assignable` uses it). Check there is no circular dependency (neither the marking service nor `UserService` may depend on `MapperService`).
- `MapperService.exportCsv` is not modified. Its error handling is the existing one: `exportMappersCsv` wraps any failure in `RuntimeException("Error during export CSV")`, returned without the cause.
- Streaming: `exportCsv` writes through `response.getWriter()`, not `StreamingResponseBody`, like the two existing CSV exports. Acceptable here: the list is one tenant's marking definitions, a small reference list bounded by the unique `(type, definition)` rule.
- `MapperService.importMappersCsv`: the return type changes from `void` to `ImportReport` (`MapperApi#importEndpoints` keeps ignoring it, the `ENDPOINTS` case returns `null`). Add the case `MARKING_DEFINITIONS`, handled **before** the temp file and the `CSVReader` of the existing flow (the marking file is read by name from its header, not by position with `withSkipLines(1)`):

  ```java
  case MARKING_DEFINITIONS -> importMarkingDefinitionsCsv(ctx, file);
  ```

  `importMarkingDefinitionsCsv` parses `file.getInputStream()` with `MarkingCsvParser`, resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)` (as `importEndpointsCsv` does) and the caller with `userService.currentUser()`, and returns `markingDefinitionService.importAll(ctx, tenantId, user.getId(), user.isAdminOrBypass(), rows)`. It stays outside the `try / catch` that wraps the `ENDPOINTS` failures in a `RuntimeException`: the file-level `BadRequestException` must reach the client as a 400 with its fixed message.

### 6.3 Parser: new `MarkingCsvParser` (stateless, in the service package)

- Input: `InputStream`. Output: `List<MarkingRow>` where `MarkingRow` holds the raw fields as text plus the position of the row. It does no business validation (that is `importAll`, section 6.1). Called by `MapperService.importMappersCsv` (6.2).
- Uses opencsv `CSVReader` with the same `CSVParserBuilder` settings as the `ENDPOINTS` import (`DEFAULT_SEPARATOR`, quotes honoured), reading `String[]` lines directly from the stream in UTF-8: no temp file, no `CsvToBean`, no typed bean. Reads the header, strips a BOM, maps the four column names to indexes (case-insensitive), then turns each line into a `MarkingRow`. An absent cell (short row) reads as empty.
- File-level failures (section 4) raise a `BadRequestException` with a fixed message. The 1000-row cap is checked while reading, so a huge file is not materialised.
- Removes the D11 leading `'` on `type` and `definition`.

### 6.4 DTOs and mapper

- Shared report model in `io.openaev.api.import_report` (not in the marking package, D9): records `ImportReport` and `ImportReportRow`, enum `ImportReportRowStatus`. Marking keeps only its own enum of reason codes, `MarkingDefinitionImportRowReason`, whose `name()` goes into `ImportReportRow.reason`. All properties `@JsonProperty` snake_case like the other marking DTOs. No tenant id.
- The generic model must stay free of any marking type, so the next import (endpoints, mappers, ...) can return it as is. The import endpoint returns `ImportReport`.
- Export row: class `MarkingDefinitionCsvRow` in `io.openaev.api.marking_definition.form` (a `@Data` class with opencsv annotations, like `EndpointExportImport` and `InjectorContractExport`; a record does not fit opencsv bean binding here). Fields `type`, `definition`, `color`, `order` (all `String`).
- `MarkingDefinitionMapper`: add `toCsvRow(MarkingDefinition)`, which applies the D11 neutralisation (one private static helper, tested alone).

### 6.5 Controller: `MarkingDefinitionApi`

Inject `MapperService`. Add two endpoints under `// -- EXPORT --` and `// -- IMPORT --`:

- `POST /export/csv` (same path as `ThreatArsenalApiExporter#exportCsv`), `@AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.MARKING_DEFINITION)` (D7: `ACCESS_MARKING_DEFINITION` carries `READ` and `SEARCH`, as `MapperApi#exportMappersCsv` uses `Action.READ`; read-only holders can download). `@Transactional`, `TxCtx ctx`, `@RequestBody @Valid SearchPaginationInput input`, `HttpServletResponse response`, body `mapperService.exportMappersCsv(ctx, CsvType.MARKING_DEFINITIONS, input, response)`. `@LogExecutionTime` and `@Operation`.
- `POST /import/csv`, `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`, `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)`, `@RequestPart("file") @NotNull MultipartFile`. Validates extension `csv` and size (the controller's own check, mirroring `MapperApi#validateUploadedFile`), then returns `mapperService.importMappersCsv(ctx, file, CsvType.MARKING_DEFINITIONS)`, the generic `ImportReport`. `@Transactional(rollbackFor = Exception.class)`, `@LogExecutionTime` and `@Operation`. Parsing, tenant resolution and `importAll` happen in `MapperService` (6.2).
- Both declare `TxCtx ctx` (the table is v2-active) and are registered in `TenantScopedEntrypointsTxCtxArchTest`.
- Feature flag: same as the other `MarkingDefinitionApi` endpoints, which carry no explicit flag guard in the controller. The two new endpoints add none either; the capability tree (`CapabilityTreeBuilder`) already hides the marking capabilities when `MARKING` is off, and the frontend masks the page.

### 6.6 What does not need a migration

No schema change: no new column, no new index. The duplicate check runs in memory against one tenant's rows.

## 7) Frontend

Files in `openaev-front/src/admin/components/settings/marking_definitions/` and `src/actions/marking_definitions/`.

- Actions (`marking-definition-actions.ts`): `exportMarkingDefinitions(searchPaginationInput)` (POST of the list's current search input, same shape as `exportCsvMapper`: returns data plus the filename from `content-disposition`, then the existing `download(..., 'text/csv')`) and `importMarkingDefinitions(formData)` (`simplePostCall` with `FormData`, like `importEndpoints`, URL `.../marking_definitions/import/csv`).
- Toolbar, next to `ButtonCreate`, with the same icons as the Endpoints and Threat Arsenal lists. The import button goes inside the existing `topBarButtons` `<Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>`. The export button goes outside it, in its own `<Can I={ACTIONS.ACCESS} a={SUBJECTS.MARKING_DEFINITION}>` (decision D7), so a read-only user still sees it:
  - Import: the existing `ImportUploader` (secondary `IconButton`, `CloudUploadOutlined`, hidden file input), with `title={t('Import marking definitions')}` and `fileAccepted=".csv"`. Its `handleUpload(formData, file)` calls the import action, then opens the report dialog. It sets its own spinner flag to false right after calling `handleUpload`, so loading state and the result live in the parent.
  - Export: a small dedicated button, secondary `IconButton`, `FileDownloadOutlined`, `aria-label` and tooltip "Export this list" (same label and icon as `ExportButton`, which is tied to its `exportType` strings), disabled when the list is empty. It receives the list's current `searchPaginationInput` (D12).
  - Library components first (IconButton, Tooltip); the report dialog stays MUI with a `fds:keep-mui` comment where the conventions require it.
- No separate file-picker dialog: the picker is the hidden input behind `ImportUploader`. The report dialog also offers a way to download an example file (the export can serve as the template).
- Report dialog, generic (D9): `components/common/import/ImportReportDialog.tsx`, opened once the import call returns. It knows nothing about markings. Props: `open`, `onClose`, `title`, `report` (the generated `ImportReport` type) or `error` (file-level failure message), `reasonLabels` (reason code to translated label, a code without label falls back to the code), `downloadFileName` (ends in `.csv`). It renders:
  - Summary line, for example "12 imported, 3 skipped, 2 ignored (already identical)".
  - A table of skipped rows: position, one column per entry of `report.columns` (field values as plain text), reason (translated), and the message. Created and ignored rows are not listed by default.
  - A "Download skipped rows" button that writes a CSV of only the skipped rows: the report's `columns` as header, then one line per row from its `values`, in the shape of the import file, so the user fixes it and re-imports. Cells are neutralised as in D11. Built client-side from the report.
  - A clear state for a file-level failure (400): the message from the server, no table.
- Marking side: `MarkingDefinitionImport.tsx` renders the generic dialog with `title={t('Import marking definitions')}` and the marking reason labels, including `INSUFFICIENT_ACCESS` (displayed as "Insufficient access"; its server message says why). No marking-specific report component.
- After a successful import, refetch the list (the `upsert` + `incrementTotalElements` helpers fit one row, not a batch). Check how `PaginationComponentV2` exposes a refetch; if none, bump a key or call `searchMarkingDefinitions` again with the current `searchPaginationInput`.
- i18n: every new string through `t()`, keys in English, added to the language files that the repo requires.
- Types: add the DTOs on the Java side first, then `yarn generate-types-from-api`. No hand-written copies of those types.
- Flag and permission: the page is already masked when `MARKING` is off (`SecurityMenu`, `isFeatureEnabled('MARKING')`), the import button is inside `Can MANAGE` and the export button inside `Can ACCESS`.

## 8) Open points (not blocking the plan)

None. Follow-up, separate issue: OpenCTI compatibility (file shape, columns). The `MarkingRow` boundary (D6) keeps that change local to the parser and `MarkingDefinitionCsvRow`.

## 9) Tests

Follow `add-test` (fixture, composer, `@Nested` + `@DisplayName`, `given_X_should_Y`, AAA comments, OpenAEV's `@WithMockUser`).

Backend, `MarkingDefinitionServiceTest`, `MarkingCsvParserTest` and `MarkingDefinitionApiTest` (extend what exists):
- Export (through `POST /export/csv`): `text/csv`, `Content-Disposition` filename `MarkingDefinitions<digits>.csv`, header row `type,definition,color,order`, returns the tenant's rows only, sorted, includes protected rows, no id or tenant id in the file, `order` written as digits, separators, quotes, line breaks and accents in values escaped and re-importable, injection neutralisation (`=`, `+`, `-`, `@` prefixes) and its lossless round trip, a filter in the input narrows the rows, an empty input exports everything. The other `CsvType` exports keep working (`ENDPOINTS`, `INJECTOR_CONTRACTS`), and so does the `ENDPOINTS` import through `importMappersCsv` (`MapperApi#importEndpoints`, unchanged behaviour). An import of a marking file with a file-level error is a 400 with the fixed message, not a wrapped `RuntimeException`.
- Import, one test per outcome: all-valid file, `MISSING_FIELD`, `INVALID_COLOR`, `INVALID_ORDER`, `CONFLICT` (one test per message variant: against an existing marking and against an earlier file row, each with colour differs, order differs, both differ; including a protected TLP), and `IGNORED` (a row identical to an existing marking, and to an earlier file row: no reason, counted in `ignored`, not in `skipped`, not in the skipped-rows download).
- Clearance (`INSUFFICIENT_ACCESS`), with a caller whose group grants `TLP:AMBER` (order 3): a `TLP` row of order 3 or below is created (cumulative per type); a `TLP` row of order 4 is skipped with the message carrying 4 and 3; a row of a type the caller holds nothing on (`PAP`, or a brand new type) is skipped with the "no marking of its type" message; a caller with no grant at all skips every row; an admin and a bypass caller create all the rows, including a new type and an order above every existing marking. Priority: a row identical to an existing marking above the caller's clearance is `IGNORED`, a conflicting one is `CONFLICT`. A skipped row does not register its key: two rows with the same key, the first above the clearance and the second within it, give `INSUFFICIENT_ACCESS` then created. The skipped rows are in "Download skipped rows". The message holds no free text.
- Partial import: a mixed file creates the valid rows, skips the others, and the report positions match the data rows of the file.
- Never overwrites: an existing marking keeps its colour and order after an import that conflicts with it.
- File handling: reordered columns, extra columns, header names in another case, BOM, a short row (`MISSING_FIELD` on that row, not a file-level error), a long row, blank lines skipped, quoted cells with separators and line breaks, `order` `3.5`, a required column missing from the header, header only, empty file, malformed quote, too many rows, too large, wrong extension (the file-level cases are a 400).
- Query count with Hibernate `Statistics` (ORM doctrine): an import of N rows does not issue N uniqueness queries.
- Cache: one `evictAll()` per import.
- Mapper endpoints guard: `POST /mappers/export/csv` and `POST /mappers/import/csv` with `csvType=MARKING_DEFINITIONS`, and `POST /mappers/export/csv?csvType=INJECTOR_CONTRACTS`, called by a user holding the mapper capabilities but not the marking or threat arsenal ones, return 400 and export or create nothing. `POST /threat_arsenal/export/csv` is unaffected (its own `ThreatArsenalApiExporterTest` stays green).
- Export tenant scope: a caller whose `TxCtx` spans several tenants gets a 400 (ambiguous scope), a single-tenant caller gets only that tenant's rows.
- Permissions: no capability gets 403 on both endpoints; read-only (`ACCESS` without `MANAGE`) can export (200) but gets 403 on import; `MANAGE` can do both. Public access not applicable.
- Tenant isolation: use the tenant isolation test skill. Importing as tenant A never touches tenant B; exporting as A never contains B's rows. The same `(type, definition)` in two tenants is not a conflict.
- `TenantScopedEntrypointsTxCtxArchTest` stays green with the two new entrypoints registered.

Frontend (component tests next to the existing marking ones): import button only under the manage permission, export button under the access permission (visible to a read-only user), the import control accepts only `.csv`, the export sends the current search input, list refresh after an import. The generic `ImportReportDialog` gets its own tests, with a fake report that has nothing to do with markings (other columns, other reason codes): summary line, one table column per report column, each skipped row, no created or ignored row listed, reason label and fallback to the raw code, the "download skipped rows" CSV content (header from `columns`, one line per skipped row, quoting and neutralisation), file-level error state.

## 10) Review gates and docs

Run: `review-code`, `review-security`, `review-multi-tenancy`, `review-performance`, `review-frontend`, `review-docs`. `review-migration` and `review-chaining-engine`: N/A (no migration, no chaining). `reduce-tx-baseline`: only if a new transaction pattern shows up.

Docs: add the import and export behaviour (CSV columns, the skip reasons, the "never overwrites" rule) to the marking definitions page under `docs/`.

Before opening the PR: format Java with `.\scripts\hooks\format-java.ps1`, run `yarn lint && yarn check-ts` and `yarn generate-types-from-api`. PR title follows Conventional Commits and ends with `(#8690)`; description follows `.github/PULL_REQUEST_TEMPLATE.md`.

## 11) Scope summary: files to create or modify

Paths are relative to the repository root. Backend main code is under `openaev-api/src/main/java/io/openaev/`, backend tests under `openaev-api/src/test/java/io/openaev/`, frontend under `openaev-front/src/`.

### Backend, create

| File | Purpose |
|---|---|
| `service/marking_definition/MarkingRow.java` | Record: raw fields as text plus the position of the row in the file. The format boundary (D6). |
| `service/marking_definition/MarkingCsvParser.java` | Stateless opencsv `CSVReader` parser, header by name, file-level validation (empty, header, malformed, row cap). |
| `api/import_report/ImportReport.java` | Generic record: created, skipped, ignored counts, field names, rows (D9). |
| `api/import_report/ImportReportRow.java` | Generic record: position, status, reason code, message, values by field name. |
| `api/import_report/ImportReportRowStatus.java` | Enum `CREATED`, `SKIPPED`, `IGNORED`. |
| `api/marking_definition/form/MarkingDefinitionImportRowReason.java` | Marking's own reason codes: `MISSING_FIELD`, `INVALID_COLOR`, `INVALID_ORDER`, `CONFLICT`, `INSUFFICIENT_ACCESS`. |
| `api/marking_definition/form/MarkingDefinitionCsvRow.java` | Exported CSV row (opencsv annotations): `type`, `definition`, `color`, `order`. |

### Backend, modify

| File | Change |
|---|---|
| `utils/CsvType.java` | Add `MARKING_DEFINITIONS`. |
| `service/MapperService.java` | Inject `MarkingDefinitionService` and `UserService`; add the `MARKING_DEFINITIONS` case in `resolveCsvExportConfig` (+ `getMarkingDefinitionsToExport`) and in `importMappersCsv` (+ `importMarkingDefinitionsCsv`); `importMappersCsv` returns `ImportReport` instead of `void`. `exportCsv` itself is untouched. |
| `rest/mapper/MapperApi.java` | Reject `CsvType.MARKING_DEFINITIONS` and `CsvType.INJECTOR_CONTRACTS` in `exportMappersCsv` and `importEndpoints` (guard, 6.2); pass `ctx` to `exportMappersCsv`. |
| `api/threat_arsenal/ThreatArsenalApiExporter.java` | Pass `ctx` to `exportMappersCsv` (signature change, no behaviour change). |
| `service/marking_definition/MarkingDefinitionService.java` | Add `exportAll(ctx, tenantId, input)` and `importAll(ctx, tenantId, userId, bypass, rows)` (with the clearance check, D14); extract the shared normalize and entity-building code used by `create`. Add a metric increment by N after commit. |
| `api/marking_definition/MarkingDefinitionApi.java` | Inject `MapperService`. Add `POST /export/csv` and `POST /import/csv` (multipart), both delegating to `MapperService`, under `// -- EXPORT --` and `// -- IMPORT --`. File extension and size checks on the import. |
| `api/marking_definition/MarkingDefinitionMapper.java` | Add `toCsvRow` (with the D11 neutralisation). |
| `telemetry/metric_collectors/ActionMetricCollector.java` | Only if the existing created counter cannot be incremented by N; check before touching. |

### Backend, tests (modify unless stated)

| File | Change |
|---|---|
| `service/MapperServiceTest.java` | The existing `exportMappersCsv(CsvType.INJECTOR_CONTRACTS, ...)` calls (and any other) get the new `ctx` argument; no assertion changes. |
| `rest/ImportExportMapperApiTest.java` | Guard tests for the two mapper endpoints (see section 9); the existing `ENDPOINTS` and `AGENT` cases stay as they are. |
| `service/marking_definition/MarkingCsvParserTest.java` (create) | Parser unit tests: header handling, BOM, quotes, short and long rows, blank lines, malformed CSV, row cap, empty and header-only files. |
| `service/marking_definition/MarkingDefinitionServiceTest.java` | Export and import outcomes, partial import, never-overwrite, query count, one cache eviction. |
| `api/marking_definition/MarkingDefinitionApiTest.java` | Both endpoints: permissions, file validation, report content, CSV content and filename, no id or tenant id in the export. |
| `api/marking_definition/MarkingDefinitionMapperTest.java` | `toCsvRow` neutralisation (create if there is none). |
| `service/marking_definition/MarkingDefinitionTenantScopeTest.java` | Tenant isolation of export and import (see the add-tenant-isolation-test skill). |
| `architecture/TenantScopedEntrypointsTxCtxArchTest.java` | Register `MarkingDefinitionApi#exportMarkingDefinitionsCsv` and `#importMarkingDefinitionsCsv` in the `marking_definitions (v2)` block (final method names to match the controller). |
| `utils/fixtures/MarkingDefinitionFixture.java` and `utils/fixtures/composers/MarkingDefinitionComposer.java` | Only if a CSV-content helper is needed; reuse them for the data setup. |

### Frontend, create

| File | Purpose |
|---|---|
| `components/common/import/ImportReportDialog.tsx` | Generic report dialog (D9): summary, skipped rows table, CSV download of skipped rows, file-level error state. Reused by other imports. |
| `__tests__/components/common/import/ImportReportDialog.test.tsx` | Tests of the generic dialog with a non-marking fake report (check the existing test folder layout for the final path). |
| `admin/components/settings/marking_definitions/MarkingDefinitionExportButton.tsx` | Dedicated export icon button (`FileDownloadOutlined`, "Export this list"). |
| `admin/components/settings/marking_definitions/MarkingDefinitionImport.tsx` | Wraps `ImportUploader` (`.csv`), calls the import action, opens the report dialog. |
| `__tests__/admin/components/settings/marking_definitions/*.test.tsx` | Component tests (no marking frontend tests exist today, so this folder is new). |

### Frontend, modify

| File | Change |
|---|---|
| `actions/marking_definitions/marking-definition-actions.ts` | Add `exportMarkingDefinitions` (POST, CSV) and `importMarkingDefinitions` (multipart). |
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
- No change to `MapperService.exportCsv`, to the `ENDPOINTS` behaviour, to the dedicated threat arsenal export, to `ExportButton.tsx` or `ImportUploader.tsx`. The mapper endpoints only get the guard on `MARKING_DEFINITIONS` and `INJECTOR_CONTRACTS` (the latter is the one behaviour change outside markings: `?csvType=INJECTOR_CONTRACTS` on `/mappers/export/csv` now returns 400, no frontend caller uses it), the `ctx` parameter of `exportMappersCsv` and the wider return type of `importMappersCsv` (still ignored by `MapperApi#importEndpoints`).
- No new capability and no new permission entry.
- The generic JSON:API classes (`ZipJsonApi`, `GenericJsonApiImporter`, ...) are neither used nor changed (section 3).

## 12) Suggested order of work

1. `MarkingRow`, `MarkingCsvParser`, DTOs and their unit tests.
2. `MarkingDefinitionService.importAll` and `exportAll` with service tests.
3. `CsvType` + `MapperService` export and import cases, controller endpoints, `TxCtx` arch-test registration, API tests including permissions and tenant isolation.
4. Regenerate the TypeScript types.
5. Frontend actions, toolbar buttons (`ImportUploader` and the export icon button), import report dialog, component tests.
6. Docs and review gates.
