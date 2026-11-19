# Task.1 US.7 - Export and import marking definitions (issue #8690)

Complements `implementation-plan.md`, which deferred import/export (section 2, "Import/export behavior is deferred"). Parent: #7511. Feature flag: `MARKING` (`PreviewFeature.MARKING` on the backend, `isFeatureEnabled('MARKING')` on the frontend).

## 1) Goal

A user holding the access marking definitions capability exports the tenant's marking definitions to a CSV file, and a user holding the manage marking definitions capability imports a compatible CSV file into another tenant or instance. The import is one step, partial, and never overwrites. The UI shows a per-row report, including every conflicting row.

Out of scope: Compatibility with the OpenCTI file format: a separate issue (the OpenCTI example is not consistent for now); here the format is the CSV of section 4, the same CSV as the other lists of the platform. Clearance on the import follows the rule the manual create already applies (D14): a row above the caller's clearance is skipped with the reason `INSUFFICIENT_ACCESS`. The export mirrors the list (`search`) and adds no clearance narrowing of its own (D12).

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
| D9 | The report and the report screen are generic, because other imports of the project will reuse them. Backend: one shared report model that knows nothing about markings (a service-level `ImportResult` and its API DTO `ImportReport`, 6.4): counts, the names of the row fields, and rows carrying their values as read in a map. The reason is a plain code string, owned by each feature. Frontend: one shared report dialog in `components/common/import/` that renders any such report and builds the "Download skipped rows" file from the values. Marking only supplies its title and its reason labels. | Product |
| D10 | Export and import go through `MarkingDefinitionApi` and `MapperService`, with a new `CsvType.MARKING_DEFINITIONS`. Export: one new case in `resolveCsvExportConfig`, so the CSV writing (opencsv, `CustomColumnPositionStrategy`, content type, filename `MarkingDefinitions<yyyyMMddHHmm>.csv`) is the existing `exportCsv`. Import: one new case in `MapperService.importMappersCsv`, which now returns an `ImportResult` (`null` for the cases that do not produce one). The marking case does not use the method's positional `CsvToBean` reading (section 3): it hands the file to `MarkingCsvParser` and to the marking service, which do the per-row validation and the partial import. | Product |
| D11 | CSV injection is neutralised, since a CSV is opened in a spreadsheet. On export, a `type` or `definition` starting with `=`, `+`, `-`, `@`, tab or carriage return gets a leading `'`. On import, a single leading `'` followed by one of those characters is removed, so the round trip is lossless. | Product |
| D12 | The export endpoint follows the existing CSV export shape: `POST /export/csv` with a `SearchPaginationInput` body, so "Export this list" exports what the list currently shows: the export is what `search` returns for that input, without the paging, with the same restrictions as `search` (an empty input exports the whole tenant). | Product |
| D13 | Separator: strict, as for the Endpoints CSV. The parser reads and the export writes only `DEFAULT_SEPARATOR`; no sniffing of another separator (for example the `;` of a French-locale Excel). A file with another separator fails the header check with the file-level "required column missing" error. | Product |
| D14 | Import applies, per row, the clearance rule of the manual create (`MarkingDefinitionService.create`, `MarkingEscalationValidator.assertCanDefineMarking`). A row above the caller's clearance on its type is `SKIPPED` with the reason `INSUFFICIENT_ACCESS`, listed in the report like any skipped row. The clearance is the caller's resolved one in the tenant (`MarkingClearanceCacheManager.findClearance(userId, tenantId)`): per type, every order at or below the highest order their groups grant. A row is above it when its order is greater than the highest order the caller holds for its type, or the caller holds none of that type. Two exceptions, both as in `create`: the first admin (`User.ADMIN_UUID`) holds every level and is never skipped (there is no marking bypass any more: `user_admin` and `BYPASS` hold what their groups grant), and the first definition of a type the tenant does not have yet is accepted (a new scale marks no row and clears no group). A type created earlier in the file counts as existing for the later rows, as sequential manual creations would. The clearance is read once before the batch: a marking created by the file never widens it. | Product |

## 3) What exists today (verified in code)

- Uniqueness is enforced on `(LOWER(type), LOWER(definition), tenant_id)`: migration `V6_20260914120000000__Add_marking_definitions` creates the unique index, and `MarkingDefinitionService.create` and `update` call `validateUniqueOrThrow`. So a duplicate type and definition cannot be created manually.
- Order carries no uniqueness rule: `create` and `update` only check `order >= 0` (`MarkingDefinitionInput`). Import applies the same rule and adds no order check of its own.
- `create(input, tenantId, userId)` normalizes type and definition to upper case (`normalize`), forces `protected = false`, refuses an order above the caller's own clearance on an existing type (`assertWithinOwnClearance` -> `MarkingEscalationValidator.assertCanDefineMarking`; the first admin and the first definition of a type are exempt), grants the new marking to the groups flagged auto-new-marking (`tenantGroupService.grantToAutoNewMarkingGroups`), evicts the clearance cache and bumps a metric after commit. `update` and `delete` apply the clearance too.
- Clearance is `MarkingClearanceCacheManager.findClearance(userId, tenantId)` (cached); only the first admin (`User.ADMIN_UUID`) holds every marking, the former bypass is gone. `MarkingDefinitionSpecification` (`openaev-model`) holds the public `tenantSpecification`, `tenantAndClearanceSpecification`, `idsSpecification` and `typeSpecification`.
- `MarkingDefinitionApi` no longer has the plain list and the options endpoints; `GET /assignable` is narrowed by `markingScopeSupplier.clearanceFor(ctx)`. `search` filters by tenant only today.
- `MarkingDefinitionApi` writes with `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)` and resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)`. It injects `MarkingDefinitionService`, `TenantWriteScopeResolver` and `UserService`.
- Frontend list: `MarkingDefinitions.tsx`. `PaginationComponentV2` has a `topBarButtons` slot holding `ButtonCreate` inside `<Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>`. The list is local state fed by `searchMarkingDefinitions`.

### Existing import and export code to reuse or avoid

| Piece | Where | Verdict |
|---|---|---|
| CSV export | `MapperService#exportMappersCsv(CsvType, SearchPaginationInput, HttpServletResponse)` -> `resolveCsvExportConfig(csvType)` -> private static `exportCsv(response, filename, exports, exportClass)` (opencsv `StatefulBeanToCsvBuilder`, `CustomColumnPositionStrategy`, `DEFAULT_SEPARATOR`, `DEFAULT_QUOTE_CHARACTER`, `text/csv`, filename `Prefix + yyyyMMddHHmm + .csv`). Already used with `CsvType.ENDPOINTS` (`MapperApi#exportMappersCsv`) and `CsvType.INJECTOR_CONTRACTS` (`ThreatArsenalApiExporter#exportCsv`, a dedicated `POST /export/csv` endpoint on the feature's own API). | **Reuse** (D10). Add `MARKING_DEFINITIONS` to `CsvType`, one `CsvExportConfig` in `resolveCsvExportConfig`, and a `POST /export/csv` endpoint in `MarkingDefinitionApi`, the way `ThreatArsenalApiExporter` does. Nothing is changed in `exportCsv` itself. |
| CSV import | `MapperService.importMappersCsv(TxCtx, MultipartFile, CsvType)` (temp file, opencsv `CsvToBean`, positional columns, `withSkipLines(1)`, returns `void`, `switch` on `CsvType` with `ENDPOINTS` only), called by `MapperApi#importEndpoints` | **Reuse as the entry point** (D10): add a `MARKING_DEFINITIONS` case and change the return type to `ImportResult`. **Do not reuse its reading and persisting for markings**: the `ENDPOINTS` path binds positionally into a typed bean and is all-or-nothing (the first bad row throws, no per-row report), which AC3 and AC5 forbid, and one bad `order` would fail the whole file. So the marking case branches before the temp file and `CsvToBean`, and calls `MarkingCsvParser` (same opencsv `CSVReader` / `CSVParserBuilder` separator and quote settings) then `MarkingDefinitionService.importAll`. The `ENDPOINTS` behaviour is unchanged. |
| Generic JSON:API import and export | `ZipJsonApi`, `ZipJsonService`, `GenericJsonApiExporter`, `GenericJsonApiImporter` in `openaev-model` (`io.openaev.jsonapi`), used by `ThreatArsenalApiExporter`, `ThreatArsenalApiImporter` and `CustomDashboardApiImporter` | Avoid. Built for one root entity with its included graph, wrapped in a ZIP, aborts the import on a failure and has no per-row report and no conflict outcome. It cannot give the partial, never-overwrite, per-row-report import of D1, D2 and D8. `FileExportBase` (scenario and exercise exports) is tied to their entity graph and does not fit either. |
| Multipart upload endpoint | `MapperApi#importMappers`, `MapperApi#importEndpoints` (`@RequestPart("file") @NotNull MultipartFile`, `writeScopeResolver.tenantForWrite`) | Reuse the signature. |
| File validation | `MapperApi#validateUploadedFile` (extension allowlist, size cap, `UnsupportedMediaTypeException`, `FileTooBigException`) | Reuse the idea. It is private to `MapperApi`: mirror it in the marking API (extension `csv`), with a 1 MB cap. |
| Report returned to the client | `PayloadImportService` returns a `PayloadImportResult` | The service returns a result record (`ImportResult`, same naming), the API maps it to the `ImportReport` DTO (6.4). |
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
5. `INSUFFICIENT_ACCESS` - the row would be created, but its order is above the caller's clearance on its type (D14). The first admin, and the first definition of a type the tenant does not have yet, are never refused. It is checked last on purpose: it only applies to a row that would create a marking, so a row that is `IGNORED` (identical to an existing marking) or a `CONFLICT` keeps that outcome even when its order is above the caller's clearance, and re-importing an exported file does not flood a limited-clearance user with access errors. The `message` is fixed text with integers only, never the free-text type: "its order (5) is above the highest order you hold for its type (3)" or "you hold no marking of its type".

Rows that pass all checks are created. The report is the generic one (D9), nothing in it is marking-specific:

The JSON names are the ones the frontend already reads (`<object>_<field>`, `api.instructions.md`):

```
ImportReport {
  int import_created;
  int import_skipped;               // rejected rows (status SKIPPED, with a reason)
  int import_ignored;               // identical to an existing or earlier row (status IGNORED, no reason)
  List<String> import_columns;      // the field names of a row, in display order: type, definition, color, order
  List<ImportReportRow> import_rows;  // every row with its status, so the UI can show each group
}
ImportReportRow {
  int import_row_position;                   // 1-based position of the data row in the file (the first row under the header is 1)
  ImportRowStatus import_row_status;         // CREATED, SKIPPED, IGNORED
  String import_row_reason;                  // feature-owned code, null unless SKIPPED: for markings MISSING_FIELD, INVALID_COLOR, INVALID_ORDER, CONFLICT, INSUFFICIENT_ACCESS
  String import_row_message;                 // fixed text built server-side, null when CREATED or IGNORED
  Map<String, String> import_row_values;     // the row's fields as read (text), keyed by field name
}
```

The text of this section calls the fields `created`, `skipped`, `ignored`, `position`, `reason`, `message` and `values` for short.

`values` echoes the user's own fields back to them, including invalid ones (that is what lets the user see and fix them). They are rendered as plain text by the dialog, never as HTML. `values` are read from `MarkingRow` (after the D11 un-neutralisation), not from the raw file, so the report keeps the same flat shape whatever the file reading is. The skipped-rows download is written by the frontend (section 7), which neutralises its cells as in D11. The `message` remains fixed text and never echoes anything.

Positions count the data rows (the first row under the header is 1), so the user can find the row in the file, and the dialog shows the row's values. The `message` is built server-side from fixed text, never from an exception message (security rule: no raw errors to clients).

## 6) Backend

Package `io.openaev.api.marking_definition` and `io.openaev.service.marking_definition`. No code in `openaev-framework` or `io.openaev.rest`. `MapperService` (`io.openaev.service`) and `CsvType` (`io.openaev.utils`) are existing classes that get a small addition, as `ENDPOINTS` and `INJECTOR_CONTRACTS` did.

### 6.1 Service: `MarkingDefinitionService`

- `exportAll(TxCtx ctx, String tenantId, SearchPaginationInput input)`: returns the marking definitions of that one tenant matching the input's filter and text search, unpaginated, sorted by type then order. The query is `repository.findAll(tenantSpecification(Set.of(tenantId)).and(computeFilterGroupJpa(...)).and(computeSearchJpa(...)), Sort.by("type", "order"))` (`tenantSpecification` is the public one of `MarkingDefinitionSpecification`), the same filter helpers `MapperService` uses for the endpoints export (`search` goes through `buildPaginationJPA`, which is paginated and does not fit). It is `search` without the paging (D12): the tenant part of `findAllByTenantIds` is extracted into one private helper that `search` and `exportAll` both use, so a restriction added to `search` later applies to the export with no change of its own. Today that restriction is the tenant only. Add `@Transactional(readOnly = true)`. Called by `MapperService` (D10), so the owning service exposes it, as the layering rule requires (`MapperService` never touches the marking repository). It takes one tenant, not the whole `TxCtx` scope: a file mixing several tenants' markings would hold the same `(type, definition)` several times and could not be imported back.
- `importAll(TxCtx ctx, String tenantId, String userId, List<MarkingRow> rows)`, returning an `ImportResult` (same identity parameters as `create`):
  - Load the tenant's existing markings once, in memory (keyed by normalized `(type, definition)`). No per-row query, no `findById` loop.
  - Clearance (D14): unless `userId` is `User.ADMIN_UUID`, read the caller's clearance once with `markingClearanceCacheManager.findClearance(userId, tenantId)` and keep the held definitions: the ids of the `MarkingCtx.Restricted` set mapped onto the already loaded markings (`MarkingCtx.None` = nothing held). No extra query. The comparison is the one of `create`: extract it from `MarkingEscalationValidator.assertCanDefineMarking` into a boolean `isWithinClearance(held, type, order)` that `assertCanDefineMarking` also calls, so manual create and import share one rule (the throwing method stays for `create`). A row that would be created gets `INSUFFICIENT_ACCESS` when its type already exists (in the tenant, or created earlier in the file) and `isWithinClearance` is false; it is not built and does not register its key. Do not use `assertCanAssignMarkings`: it throws on the first unheld marking and works on existing ids, where a row is not a marking yet.
  - Validate every row in memory (section 5), build entities for the valid rows, then one `saveAll`.
  - Grant the created markings to the groups flagged auto-new-marking, as `create` does (`grantToAutoNewMarkingGroups`), in one call for the batch: add an overload taking a `Collection<MarkingDefinition>` to `TenantGroupService` (one group query, `grantMissingMarkings(group, markings)` once per group), not one call per marking.
  - One `markingClearanceCacheManager.evictAll()` after the batch and the grants (not per row: `create` evicts per call).
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
- `MapperService.getMarkingDefinitionsToExport(TxCtx ctx, SearchPaginationInput input)`: resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)` (as the import and `MarkingDefinitionApi#create` do: one tenant, an ambiguous multi-tenant scope is refused with a 400), calls `markingDefinitionService.exportAll(ctx, tenantId, input)` and maps each entity to `MarkingDefinitionCsvRow` through the mapper of 6.4, which applies the D11 neutralisation.
- `MapperApi` guard: `MapperApi#exportMappersCsv` and `#importEndpoints` forward a client-supplied `csvType` under `ResourceType.MAPPER`, so without a guard `?csvType=MARKING_DEFINITIONS` would export or create markings with only the mapper capability, bypassing D7. The same hole already exists for `INJECTOR_CONTRACTS`: `?csvType=INJECTOR_CONTRACTS` exports the threat arsenal with only the mapper READ, bypassing the `THREAT_ARSENAL` READ of `ThreatArsenalApiExporter#exportCsv`. One guard covers both: a constant `Set<CsvType> TYPES_WITH_DEDICATED_API = Set.of(INJECTOR_CONTRACTS, MARKING_DEFINITIONS)` in `MapperApi`, and both endpoints reject a type of the set with a `BadRequestException` ("This CSV type is handled by its own API") before calling `MapperService`. This is safe for the existing callers: the frontend's threat arsenal list passes its own export function (`exportThreatArsenalCsvMapper`, URL `.../threat_arsenal/export/csv`), so nothing in the frontend calls the mapper endpoint with `INJECTOR_CONTRACTS`, and `INJECTOR_CONTRACTS` has no import case anyway. The dedicated endpoints are the only way to reach those two cases, under their own capabilities.
- New dependencies of `MapperService`: `MarkingDefinitionService` (export and import) and `UserService` (the caller's id for the import clearance check, as `MarkingDefinitionApi#create` passes it to the service). Check there is no circular dependency (neither the marking service nor `UserService` may depend on `MapperService`).
- `MapperService.exportCsv` is not modified. Its error handling is the existing one: `exportMappersCsv` wraps any failure in `RuntimeException("Error during export CSV")`, returned without the cause.
- Streaming: `exportCsv` writes through `response.getWriter()`, not `StreamingResponseBody`, like the two existing CSV exports. Acceptable here: the list is one tenant's marking definitions, a small reference list bounded by the unique `(type, definition)` rule.
- `MapperService.importMappersCsv`: the return type changes from `void` to `ImportResult` (`MapperApi#importEndpoints` keeps ignoring it, the `ENDPOINTS` case returns `null`). Add the case `MARKING_DEFINITIONS`, handled **before** the temp file and the `CSVReader` of the existing flow (the marking file is read by name from its header, not by position with `withSkipLines(1)`):

  ```java
  case MARKING_DEFINITIONS -> importMarkingDefinitionsCsv(ctx, file);
  ```

  `importMarkingDefinitionsCsv` parses `file.getInputStream()` with `MarkingCsvParser`, resolves the tenant with `writeScopeResolver.tenantForWrite(ctx, null)` (as `importEndpointsCsv` does) and the caller with `userService.currentUser().getId()`, and returns `markingDefinitionService.importAll(ctx, tenantId, userId, rows)`. It stays outside the `try / catch` that wraps the `ENDPOINTS` failures in a `RuntimeException`: the file-level `BadRequestException` must reach the client as a 400 with its fixed message.

### 6.3 Parser: new `MarkingCsvParser` (stateless, in the service package)

- Input: `InputStream`. Output: `List<MarkingRow>` where `MarkingRow` holds the raw fields as text plus the position of the row. It does no business validation (that is `importAll`, section 6.1). Called by `MapperService.importMappersCsv` (6.2).
- Uses opencsv `CSVReader` with the same `CSVParserBuilder` settings as the `ENDPOINTS` import (`DEFAULT_SEPARATOR`, quotes honoured), reading `String[]` lines directly from the stream in UTF-8: no temp file, no `CsvToBean`, no typed bean. Reads the header, strips a BOM, maps the four column names to indexes (case-insensitive), then turns each line into a `MarkingRow`. An absent cell (short row) reads as empty.
- File-level failures (section 4) raise a `BadRequestException` with a fixed message. The 1000-row cap is checked while reading, so a huge file is not materialised.
- Removes the D11 leading `'` on `type` and `definition`.

### 6.4 DTOs and mapper

- A service never returns an API DTO (`backend.instructions.md`), so the shared report is two layers (not in the marking package, D9). Service side, `io.openaev.service.import_report`: records `ImportResult` and `ImportResultRow`, enum `ImportRowStatus`, no annotation. API side, `io.openaev.api.import_report`: DTO records `ImportReport` and `ImportReportRow` in `dto/` with the `@JsonProperty` names of section 5, and `ImportReportMapper.toReport(ImportResult)`. Marking keeps only its own enum of reason codes, `MarkingDefinitionImportRowReason` (in `io.openaev.service.marking_definition`, since the service produces it), whose `name()` goes into the row's reason. No tenant id.
- The generic model must stay free of any marking type, so the next import (endpoints, mappers, ...) can return it as is. The import endpoint returns `ImportReport`.
- Export row: class `MarkingDefinitionCsvRow` in `io.openaev.api.marking_definition.dto` (new DTOs follow the `dto/` convention of `backend.instructions.md`; the existing marking DTOs are still in `form/`) (a `@Data` class with opencsv annotations, like `EndpointExportImport` and `InjectorContractExport`; a record does not fit opencsv bean binding here). Fields `type`, `definition`, `color`, `order` (all `String`).
- `MarkingDefinitionMapper`: add `toCsvRow(MarkingDefinition)`, which applies the D11 neutralisation (one private static helper, tested alone).

### 6.5 Controller: `MarkingDefinitionApi`

Inject `MapperService` and `PreviewFeatureService`. Add two endpoints under `// -- EXPORT --` and `// -- IMPORT --`:

- `POST /export/csv` (same path as `ThreatArsenalApiExporter#exportCsv`), `@AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.MARKING_DEFINITION)` (D7: `ACCESS_MARKING_DEFINITION` carries `READ` and `SEARCH`, as `MapperApi#exportMappersCsv` uses `Action.READ`; read-only holders can download). `@Transactional`, `TxCtx ctx`, `@RequestBody @Valid SearchPaginationInput input`, `HttpServletResponse response`, body `mapperService.exportMappersCsv(ctx, CsvType.MARKING_DEFINITIONS, input, response)`. `@LogExecutionTime` (an export is a slow-endpoint case) and `@Operation(summary = "Export marking definitions as CSV")` (summary 60 characters at most, no description).
- `POST /import/csv`, `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`, `@AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)`, `@RequestPart("file") @NotNull MultipartFile`. Validates extension `csv` and size (the controller's own check, mirroring `MapperApi#validateUploadedFile`), then returns `ImportReportMapper.toReport(mapperService.importMappersCsv(ctx, file, CsvType.MARKING_DEFINITIONS))`, the generic `ImportReport`. `@Transactional(rollbackFor = Exception.class)`, `@LogExecutionTime` and `@Operation(summary = "Import marking definitions from CSV")`. Parsing, tenant resolution and `importAll` happen in `MapperService` (6.2).
- Both declare `TxCtx ctx` (the table is v2-active) and are registered in `TenantScopedEntrypointsTxCtxArchTest`.
- Feature flag: `backend.instructions.md` now asks a preview-feature endpoint to check the flag itself with `PreviewFeatureService.isFeatureEnabled(...)` and throw `ElementNotFoundException` (404) when it is off. The two new endpoints do it with `PreviewFeature.MARKING` (check the exact API of the service). The existing `MarkingDefinitionApi` endpoints carry no such check today (they rely on the capability tree, `CapabilityTreeBuilder`, which hides the marking capabilities when the flag is off); aligning them is out of scope.

### 6.6 What does not need a migration

No schema change: no new column, no new index. The duplicate check runs in memory against one tenant's rows.

## 7) Frontend

The frontend POC is already in the branch (commits "Beginning of import/export markings list", "POC conflits dialog", "POC JSON"): the toolbar buttons, the generic report dialog and its helpers run against a mock backend (`USE_MOCK_IMPORT_EXPORT`) and a JSON file. The work of this issue is to plug the real endpoints, switch the format to CSV and add the new reason. The design (buttons, permissions, generic dialog, skipped-rows table) is unchanged.

Already in place, kept as they are:
- Toolbar in `MarkingDefinitions.tsx`: the export button in `<Can I={ACTIONS.ACCESS} a={SUBJECTS.MARKING_DEFINITION}>` (D7, so a read-only user still sees it, disabled when the list is empty) and the import in `<Can I={ACTIONS.MANAGE} a={SUBJECTS.MARKING_DEFINITION}>`, with the list reload after an import (`onImported`).
- `MarkingDefinitionImport.tsx` (the existing `ImportUploader`, the report dialog, the marking reason labels and severities) and `MarkingDefinitionExportButton.tsx` (secondary `IconButton`, `FileDownloadOutlined`, tooltip "Export this list").
- `components/common/import/ImportReportDialog.tsx`, generic (D9), knows nothing about markings. Props: `open`, `onClose`, `title`, `report` or `fileError`, `columnLabels`, `reasonLabels` (a code without label falls back to the code), `reasonSeverities`, `downloadFileName`. It renders the summary line, the table of skipped rows (position, one column per `import_columns` entry as plain text, reason, message; created and ignored rows are not listed), the "Download skipped rows" button, and the file-level failure state (the server's message, no table).

To change:
- Actions (`marking-definition-actions.ts`): remove `USE_MOCK_IMPORT_EXPORT` and the mock imports. `exportMarkingDefinitions(searchPaginationInput)`: `POST .../marking_definitions/export/csv` with the list's current search input (D12), same shape as `exportCsvMapper` (data plus the filename from `content-disposition`). `importMarkingDefinitions(file)`: multipart `POST .../marking_definitions/import/csv` with `FormData`, like `importEndpoints`. A 400 must become an `ImportFileError` carrying the server's message (the mock threw it; the real call must map the response: check how `simplePostCall` surfaces the error and that the generic error notification does not also fire for it).
- Delete `marking-definition-import-mock.ts` and its test, the "Mock: sample file with conflicts" button, and the dialog's `notice` prop if nothing else uses it (no unused code).
- Format: `fileAccepted=".csv"`; the export is downloaded with `download(..., 'text/csv')`; the skipped-rows download becomes `skippedRowsToCsv` (header = `import_columns`, one line per skipped row from `import_row_values`, quoting, the same separator as the backend `DEFAULT_SEPARATOR`, cells neutralised as in D11, so the file can be fixed and re-imported), downloaded as `text/csv` with a `downloadFileName` ending in `.csv`. It replaces `skippedRowsToJson` and its tests.
- Types: replace the hand-written model of `import-report-types.ts` with the generated `ImportReport` and `ImportReportRow` (`yarn generate-types-from-api`; the backend JSON names are already the `import_*` ones of the POC); keep `ImportFileError`. Add `INSUFFICIENT_ACCESS` to `MarkingDefinitionImportRowReason`, its translated label "Insufficient access" in `reasonLabels` (its server message says why) and a severity in `reasonSeverities`.
- After a successful import the list reloads (`onImported`, already wired to `setReloadContentCount`).
- i18n: every new string through `t()`, keys in English, added to the language files that the repo requires.
- Flag and permission: the page is already masked when `MARKING` is off (`SecurityMenu`, `isFeatureEnabled('MARKING')`), the import button is inside `Can MANAGE` and the export button inside `Can ACCESS`.

## 8) Open points (not blocking the plan)

Follow-up, separate issue: OpenCTI compatibility (file shape, columns). The `MarkingRow` boundary (D6) keeps that change local to the parser and `MarkingDefinitionCsvRow`.

## 9) Tests

Follow `add-test` (fixture, composer, `@Nested` + `@DisplayName`, `given_X_should_Y`, AAA comments, OpenAEV's `@WithMockUser`).

Backend, `MarkingDefinitionServiceTest`, `MarkingCsvParserTest` and `MarkingDefinitionApiTest` (extend what exists):
- Export (through `POST /export/csv`): `text/csv`, `Content-Disposition` filename `MarkingDefinitions<digits>.csv`, header row `type,definition,color,order`, returns the tenant's rows only, sorted, includes protected rows, no id or tenant id in the file, `order` written as digits, separators, quotes, line breaks and accents in values escaped and re-importable, injection neutralisation (`=`, `+`, `-`, `@` prefixes) and its lossless round trip, a filter in the input narrows the rows, an empty input exports everything, and the exported rows are the ones `search` returns for the same input (same set, no paging). The other `CsvType` exports keep working (`ENDPOINTS`, `INJECTOR_CONTRACTS`), and so does the `ENDPOINTS` import through `importMappersCsv` (`MapperApi#importEndpoints`, unchanged behaviour). An import of a marking file with a file-level error is a 400 with the fixed message, not a wrapped `RuntimeException`.
- Import, one test per outcome: all-valid file, `MISSING_FIELD`, `INVALID_COLOR`, `INVALID_ORDER`, `CONFLICT` (one test per message variant: against an existing marking and against an earlier file row, each with colour differs, order differs, both differ; including a protected TLP), and `IGNORED` (a row identical to an existing marking, and to an earlier file row: no reason, counted in `ignored`, not in `skipped`, not in the skipped-rows download).
- Clearance (`INSUFFICIENT_ACCESS`), with a caller whose group grants `TLP:AMBER` (order 3): a `TLP` row of order 3 or below is created (cumulative per type); a `TLP` row of order 4 is skipped with the message carrying 4 and 3; a row of an existing type the caller holds nothing on (`PAP`) is skipped with the "no marking of its type" message; a caller with no grant at all skips every row of an existing type; the first row of a brand new type is created, and a later row of that same type in the file is skipped (the type now exists and the clearance, read once, holds nothing of it); the first admin creates all the rows, including an order above every existing marking, while `user_admin` and a `BYPASS` holder without grants are skipped like any caller. Same outcomes as the manual `create` for the same inputs. Priority: a row identical to an existing marking above the caller's clearance is `IGNORED`, a conflicting one is `CONFLICT`. A skipped row does not register its key: two rows with the same key, the first above the clearance and the second within it, give `INSUFFICIENT_ACCESS` then created. The skipped rows are in "Download skipped rows". The message holds no free text.
- Partial import: a mixed file creates the valid rows, skips the others, and the report positions match the data rows of the file.
- Never overwrites: an existing marking keeps its colour and order after an import that conflicts with it.
- File handling: reordered columns, extra columns, header names in another case, BOM, a short row (`MISSING_FIELD` on that row, not a file-level error), a long row, blank lines skipped, quoted cells with separators and line breaks, `order` `3.5`, a required column missing from the header, header only, empty file, malformed quote, too many rows, too large, wrong extension (the file-level cases are a 400).
- Query count with Hibernate `Statistics` (ORM doctrine): an import of N rows does not issue N uniqueness queries.
- Cache: one `evictAll()` per import.
- Auto-grant: the created markings are granted to a group flagged auto-new-marking, with one group query for the batch (Statistics), like `create` does for one marking.
- `MarkingEscalationValidator`: unit tests of `isWithinClearance`; the existing `assertCanDefineMarking` tests stay green.
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
| `service/import_report/ImportResult.java` | Generic service-level record: created, skipped, ignored counts, field names, rows (D9). |
| `service/import_report/ImportResultRow.java` | Generic record: position, status, reason code, message, values by field name. |
| `service/import_report/ImportRowStatus.java` | Enum `CREATED`, `SKIPPED`, `IGNORED`. |
| `api/import_report/dto/ImportReport.java` | API DTO of the report, `import_*` JSON names (section 5). |
| `api/import_report/dto/ImportReportRow.java` | API DTO of a row, `import_row_*` JSON names. |
| `api/import_report/ImportReportMapper.java` | `toReport(ImportResult)`. |
| `service/marking_definition/MarkingDefinitionImportRowReason.java` | Marking's own reason codes: `MISSING_FIELD`, `INVALID_COLOR`, `INVALID_ORDER`, `CONFLICT`, `INSUFFICIENT_ACCESS`. |
| `api/marking_definition/dto/MarkingDefinitionCsvRow.java` | Exported CSV row (opencsv annotations): `type`, `definition`, `color`, `order`. |

### Backend, modify

| File | Change |
|---|---|
| `utils/CsvType.java` | Add `MARKING_DEFINITIONS`. |
| `service/MapperService.java` | Inject `MarkingDefinitionService` and `UserService`; add the `MARKING_DEFINITIONS` case in `resolveCsvExportConfig` (+ `getMarkingDefinitionsToExport`) and in `importMappersCsv` (+ `importMarkingDefinitionsCsv`); `importMappersCsv` returns `ImportResult` instead of `void`. `exportCsv` itself is untouched. |
| `rest/mapper/MapperApi.java` | Reject `CsvType.MARKING_DEFINITIONS` and `CsvType.INJECTOR_CONTRACTS` in `exportMappersCsv` and `importEndpoints` (guard, 6.2); pass `ctx` to `exportMappersCsv`. |
| `api/threat_arsenal/ThreatArsenalApiExporter.java` | Pass `ctx` to `exportMappersCsv` (signature change, no behaviour change). |
| `service/marking_definition/MarkingDefinitionService.java` | Add `exportAll(ctx, tenantId, input)` and `importAll(ctx, tenantId, userId, rows)` (with the clearance check, D14, and the auto-grant); extract the shared normalize and entity-building code used by `create`. Add a metric increment by N after commit. |
| `service/marking/MarkingEscalationValidator.java` | Add the boolean `isWithinClearance(held, type, order)`, called by `assertCanDefineMarking` and by `importAll` (one rule). |
| `service/TenantGroupService.java` | Add `grantToAutoNewMarkingGroups(Collection<MarkingDefinition>)`: one group query for a batch. |
| `api/marking_definition/MarkingDefinitionApi.java` | Inject `MapperService` and `PreviewFeatureService`. Add `POST /export/csv` and `POST /import/csv` (multipart), both delegating to `MapperService`, under `// -- EXPORT --` and `// -- IMPORT --`. File extension and size checks on the import. |
| `api/marking_definition/MarkingDefinitionMapper.java` | Add `toCsvRow` (with the D11 neutralisation). |
| `telemetry/metric_collectors/ActionMetricCollector.java` | Only if the existing created counter cannot be incremented by N; check before touching. |

### Backend, tests (modify unless stated)

| File | Change |
|---|---|
| `service/MapperServiceTest.java` | The existing `exportMappersCsv(CsvType.INJECTOR_CONTRACTS, ...)` calls (and any other) get the new `ctx` argument; no assertion changes. |
| `rest/ImportExportMapperApiTest.java` | Guard tests for the two mapper endpoints (see section 9); the existing `ENDPOINTS` and `AGENT` cases stay as they are. |
| `service/marking_definition/MarkingCsvParserTest.java` (create) | Parser unit tests: header handling, BOM, quotes, short and long rows, blank lines, malformed CSV, row cap, empty and header-only files. |
| `service/marking_definition/MarkingDefinitionServiceTest.java` | Export and import outcomes, clearance outcomes, partial import, never-overwrite, query count, auto-grant, one cache eviction. |
| `service/marking/MarkingEscalationValidatorTest.java` | `isWithinClearance` cases. |
| `api/marking_definition/MarkingDefinitionApiTest.java` | Both endpoints: permissions, file validation, report content, CSV content and filename, no id or tenant id in the export. |
| `api/marking_definition/MarkingDefinitionMapperTest.java` | `toCsvRow` neutralisation (create if there is none). |
| `service/marking_definition/MarkingDefinitionTenantScopeTest.java` | Tenant isolation of export and import (see the add-tenant-isolation-test skill). |
| `architecture/TenantScopedEntrypointsTxCtxArchTest.java` | Register `MarkingDefinitionApi#exportMarkingDefinitionsCsv` and `#importMarkingDefinitionsCsv` in the `marking_definitions (v2)` block (final method names to match the controller). |
| `utils/fixtures/MarkingDefinitionFixture.java` and `utils/fixtures/composers/MarkingDefinitionComposer.java` | Only if a CSV-content helper is needed; reuse them for the data setup. |

### Frontend (the POC is already in the branch: modify and delete, nothing to create)

| File | Change |
|---|---|
| `actions/marking_definitions/marking-definition-actions.ts` | Real `POST .../export/csv` (with the search input) and `POST .../import/csv`; remove `USE_MOCK_IMPORT_EXPORT`; map a 400 to `ImportFileError`. |
| `actions/marking_definitions/marking-definition-import-mock.ts` and its test | Delete. |
| `actions/marking_definitions/marking-definition-import-types.ts` | Add `INSUFFICIENT_ACCESS`. |
| `components/common/import/import-report-types.ts` | Use the generated `ImportReport` and `ImportReportRow`; keep `ImportFileError`. |
| `components/common/import/import-report-helpers.ts` and its test | `skippedRowsToJson` becomes `skippedRowsToCsv` (D11 neutralisation, same separator as the backend). |
| `components/common/import/ImportReportDialog.tsx` and its test | CSV download of the skipped rows; drop the `notice` prop if only the mock uses it. Tests keep the non-marking fake report. |
| `admin/components/settings/marking_definitions/MarkingDefinitionImport.tsx` | `fileAccepted=".csv"`, remove the mock button, label and severity of `INSUFFICIENT_ACCESS`, `downloadFileName` in `.csv`. |
| `admin/components/settings/marking_definitions/MarkingDefinitionExportButton.tsx` | Receive and send the list's search input; `download(..., 'text/csv')`. |
| `admin/components/settings/marking_definitions/MarkingDefinitions.tsx` | Pass the search input to the export button. |
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
5. Frontend: replace the mock of the POC with the real endpoints, switch to CSV, add `INSUFFICIENT_ACCESS`, update the component tests.
6. Docs and review gates.
