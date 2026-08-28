package io.openaev.rest.mapper;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.fasterxml.jackson.core.type.TypeReference;
import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.database.raw.RawPaginationImportMapper;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.FileTooBigException;
import io.openaev.rest.exception.ImportException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.mapper.form.ExportMapperInput;
import io.openaev.rest.mapper.form.ImportMapperAddInput;
import io.openaev.rest.mapper.form.ImportMapperUpdateInput;
import io.openaev.rest.scenario.form.InjectsImportTestInput;
import io.openaev.rest.scenario.response.ImportPostSummary;
import io.openaev.rest.scenario.response.ImportTestSummary;
import io.openaev.service.InjectImportService;
import io.openaev.service.MapperService;
import io.openaev.utils.CsvType;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

@RestController
@RequiredArgsConstructor
@RequestMapping({MapperApi.MAPPER_URI, MapperApi.TENANT_MAPPER_URI})
@Slf4j
public class MapperApi extends RestBehavior {

  public static final String MAPPER_URI = "/api/mappers";
  public static final String TENANT_MAPPER_URI = TENANT_PREFIX + "/mappers";

  private final ImportMapperRepository importMapperRepository;
  private final MapperService mapperService;
  private final InjectImportService injectImportService;
  private final TenantWriteScopeResolver writeScopeResolver;

  // 25mb in byte
  private static final int MAXIMUM_FILE_SIZE_ALLOWED = 25 * 1000 * 1000;
  private static final List<String> ACCEPTED_FILE_TYPES = List.of("xls", "xlsx");

  @PostMapping("/search")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.MAPPER)
  public Page<RawPaginationImportMapper> getImportMapper(
      TxCtx ctx, @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
            this.importMapperRepository::findAll, searchPaginationInput, ImportMapper.class)
        .map(RawPaginationImportMapper::new);
  }

  @GetMapping("/{mapperId}")
  @Transactional
  @AccessControl(
      resourceId = "#mapperId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.MAPPER)
  // TxCtx is resolved from the request and applied by the transaction aspect; it scopes this read
  // to the caller's tenants. The handler does not use it directly.
  public ImportMapper getImportMapperById(TxCtx ctx, @PathVariable String mapperId) {
    return importMapperRepository
        .findById(UUID.fromString(mapperId))
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.MAPPER)
  public ImportMapper createImportMapper(
      TxCtx ctx, @RequestBody @Valid final ImportMapperAddInput importMapperAddInput) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return mapperService.createAndSaveImportMapper(tenantId, importMapperAddInput);
  }

  @PostMapping(value = "/export")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.MAPPER)
  // TxCtx scopes the export to the caller's tenants; an id outside the scope resolves to nothing
  // and is silently skipped. The handler does not use it directly.
  public void exportMappers(
      TxCtx ctx,
      @RequestBody @Valid final ExportMapperInput exportMapperInput,
      HttpServletResponse response) {
    try {
      String jsonMappers = mapperService.exportMappers(exportMapperInput.getIdsToExport());

      String rightNow = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
      String name =
          exportMapperInput
              .getName()
              .replace(Constants.IMPORTED_OBJECT_NAME_SUFFIX, "")
              .replace(" ", "");
      String exportFileName = name.length() > 15 ? name.substring(0, 15) : name;
      String filename = MessageFormat.format("{0}-{1}.json", exportFileName, rightNow);

      response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      response.setStatus(HttpServletResponse.SC_OK);

      response.getOutputStream().write(jsonMappers.getBytes(StandardCharsets.UTF_8));
      response.getOutputStream().flush();
      response.getOutputStream().close();
    } catch (IOException e) {
      throw new RuntimeException("Error during export", e);
    }
  }

  @Operation(description = "Export all datas from a specific target (endpoint,...)")
  @PostMapping(value = "/export/csv")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.MAPPER)
  @LogExecutionTime
  public void exportMappersCsv(
      @RequestParam CsvType csvType,
      @RequestBody @Valid final SearchPaginationInput input,
      HttpServletResponse response) {
    mapperService.exportMappersCsv(csvType, input, response);
  }

  @PostMapping("/import")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  public void importMappers(TxCtx ctx, @RequestPart("file") @NotNull MultipartFile file)
      throws ImportException {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    try {
      mapperService.importMappers(
          tenantId,
          mapper.readValue(file.getInputStream().readAllBytes(), new TypeReference<>() {}));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new ImportException("Mapper import", "Error during import");
    }
  }

  @PostMapping("/{mapperId}")
  @Transactional
  @AccessControl(
      resourceId = "#mapperId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.MAPPER)
  @Operation(summary = "Duplicate XLS mapper by id")
  // TxCtx scopes the lookup of the source mapper to the caller's tenants; the copy inherits the
  // source's tenant. A source outside the scope is not found, so a cross-tenant duplicate cannot
  // reach it. The handler does not use it directly.
  public ImportMapper duplicateMapper(TxCtx ctx, @PathVariable @NotBlank final String mapperId) {
    return mapperService.getDuplicateImportMapper(mapperId);
  }

  @PutMapping("/{mapperId}")
  @Transactional
  @AccessControl(
      resourceId = "#mapperId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MAPPER)
  // TxCtx scopes the lookup and the update to the caller's tenants; a mapper outside the scope is
  // not found, so a cross-tenant write cannot reach it. The handler does not use it directly.
  public ImportMapper updateImportMapper(
      TxCtx ctx,
      @PathVariable String mapperId,
      @Valid @RequestBody ImportMapperUpdateInput importMapperUpdateInput) {
    return mapperService.updateImportMapper(mapperId, importMapperUpdateInput);
  }

  @DeleteMapping("/{mapperId}")
  @Transactional
  @AccessControl(
      resourceId = "#mapperId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.MAPPER)
  // TxCtx scopes the delete to the caller's tenants; a delete outside the scope matches no row and
  // removes nothing. The handler does not use it directly.
  public void deleteImportMapper(TxCtx ctx, @PathVariable String mapperId) {
    importMapperRepository.deleteById(UUID.fromString(mapperId));
  }

  @PostMapping("/store")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Import injects into an xls file")
  public ImportPostSummary importXLSFile(@RequestPart("file") @NotNull MultipartFile file) {
    validateUploadedFile(file);
    return injectImportService.storeXlsFileForImport(file);
  }

  @PostMapping("/store/{importId}")
  @AccessControl(
      resourceId = "#importId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MAPPER)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Test the import of injects from an xls file")
  public ImportTestSummary testImportXLSFile(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set the
      // tenant scope for this read (importInjectIntoScenarioFromXLS reads InjectorContract#
      // getFirstInjector() to attach an injector to each previewed inject).
      TxCtx ctx,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final InjectsImportTestInput input) {
    ImportMapper importMapper = mapperService.createImportMapper(input.getImportMapper());
    importMapper
        .getInjectImporters()
        .forEach(
            injectImporter -> {
              injectImporter.setId(UUID.randomUUID().toString());
              injectImporter
                  .getRuleAttributes()
                  .forEach(ruleAttribute -> ruleAttribute.setId(UUID.randomUUID().toString()));
            });
    Scenario scenario = new Scenario();
    scenario.setRecurrenceStart(Instant.now());
    return injectImportService.importInjectIntoScenarioFromXLS(
        scenario, importMapper, importId, input.getName(), input.getTimezoneOffset(), false);
  }

  // -- IMPORT --
  @Operation(
      description = "Import all datas from a specific target (endpoint,...) through a csv file")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  @PostMapping("/import/csv")
  @LogExecutionTime
  @Transactional(rollbackFor = Exception.class)
  public void importEndpoints(
      @RequestParam CsvType csvType, @RequestPart("file") @NotNull MultipartFile file)
      throws Exception {
    mapperService.importMappersCsv(file, csvType);
  }

  private void validateUploadedFile(MultipartFile file) {
    validateExtension(file);
    validateFileSize(file);
  }

  private void validateExtension(MultipartFile file) {
    String extension = FilenameUtils.getExtension(file.getOriginalFilename());
    if (!ACCEPTED_FILE_TYPES.contains(extension)) {
      throw new UnsupportedMediaTypeException(
          "Only the following file types are accepted : " + String.join(", ", ACCEPTED_FILE_TYPES));
    }
  }

  private void validateFileSize(MultipartFile file) {
    if (file.getSize() >= MAXIMUM_FILE_SIZE_ALLOWED) {
      throw new FileTooBigException("File size cannot be greater than 25 Mb");
    }
  }
}
