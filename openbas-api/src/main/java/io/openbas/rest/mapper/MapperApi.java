package io.openbas.rest.mapper;

import static io.openbas.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.fasterxml.jackson.core.type.TypeReference;
import io.openbas.aop.LogExecutionTime;
import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.ImportMapper;
import io.openbas.database.model.ResourceType;
import io.openbas.database.model.Scenario;
import io.openbas.database.raw.RawPaginationImportMapper;
import io.openbas.database.repository.ImportMapperRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.exception.FileTooBigException;
import io.openbas.rest.exception.ImportException;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.mapper.form.ExportMapperInput;
import io.openbas.rest.mapper.form.ImportMapperAddInput;
import io.openbas.rest.mapper.form.ImportMapperUpdateInput;
import io.openbas.rest.scenario.form.InjectsImportTestInput;
import io.openbas.rest.scenario.response.ImportPostSummary;
import io.openbas.rest.scenario.response.ImportTestSummary;
import io.openbas.service.InjectImportService;
import io.openbas.service.MapperService;
import io.openbas.utils.TargetType;
import io.openbas.utils.constants.Constants;
import io.openbas.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MapperApi extends RestBehavior {

  private final ImportMapperRepository importMapperRepository;
  private final MapperService mapperService;
  private final InjectImportService injectImportService;

  // 25mb in byte
  private static final int MAXIMUM_FILE_SIZE_ALLOWED = 25 * 1000 * 1000;
  private static final List<String> ACCEPTED_FILE_TYPES = List.of("xls", "xlsx");

  @PostMapping("/api/mappers/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.MAPPER)
  public Page<RawPaginationImportMapper> getImportMapper(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
            this.importMapperRepository::findAll, searchPaginationInput, ImportMapper.class)
        .map(RawPaginationImportMapper::new);
  }

  @GetMapping("/api/mappers/{mapperId}")
  @RBAC(resourceId = "#mapperId", actionPerformed = Action.READ, resourceType = ResourceType.MAPPER)
  public ImportMapper getImportMapperById(@PathVariable String mapperId) {
    return importMapperRepository
        .findById(UUID.fromString(mapperId))
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping("/api/mappers")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.MAPPER)
  public ImportMapper createImportMapper(
      @RequestBody @Valid final ImportMapperAddInput importMapperAddInput) {
    return mapperService.createAndSaveImportMapper(importMapperAddInput);
  }

  @PostMapping(value = "/api/mappers/export")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.MAPPER)
  public void exportMappers(
      @RequestBody @Valid final ExportMapperInput exportMapperInput, HttpServletResponse response) {
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
  @PostMapping(value = "/api/mappers/export/csv")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.MAPPER)
  @LogExecutionTime
  public void exportMappersCsv(
      @RequestParam TargetType targetType,
      @RequestBody @Valid final SearchPaginationInput input,
      HttpServletResponse response) {
    mapperService.exportMappersCsv(targetType, input, response);
  }

  @PostMapping("/api/mappers/import")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  public void importMappers(@RequestPart("file") @NotNull MultipartFile file)
      throws ImportException {
    try {
      mapperService.importMappers(
          mapper.readValue(file.getInputStream().readAllBytes(), new TypeReference<>() {}));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new ImportException("Mapper import", "Error during import");
    }
  }

  @PostMapping("/api/mappers/{mapperId}")
  @RBAC(
      resourceId = "#mapperId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.MAPPER)
  @Operation(summary = "Duplicate XLS mapper by id")
  public ImportMapper duplicateMapper(@PathVariable @NotBlank final String mapperId) {
    return mapperService.getDuplicateImportMapper(mapperId);
  }

  @PutMapping("/api/mappers/{mapperId}")
  @RBAC(
      resourceId = "#mapperId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MAPPER)
  public ImportMapper updateImportMapper(
      @PathVariable String mapperId,
      @Valid @RequestBody ImportMapperUpdateInput importMapperUpdateInput) {
    return mapperService.updateImportMapper(mapperId, importMapperUpdateInput);
  }

  @DeleteMapping("/api/mappers/{mapperId}")
  @RBAC(
      resourceId = "#mapperId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.MAPPER)
  public void deleteImportMapper(@PathVariable String mapperId) {
    importMapperRepository.deleteById(UUID.fromString(mapperId));
  }

  @PostMapping("/api/mappers/store")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Import injects into an xls file")
  public ImportPostSummary importXLSFile(@RequestPart("file") @NotNull MultipartFile file) {
    validateUploadedFile(file);
    return injectImportService.storeXlsFileForImport(file);
  }

  @PostMapping("/api/mappers/store/{importId}")
  @RBAC(
      resourceId = "#importId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MAPPER)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Test the import of injects from an xls file")
  public ImportTestSummary testImportXLSFile(
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
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  @PostMapping("/api/mappers/import/csv")
  @LogExecutionTime
  @Transactional(rollbackOn = Exception.class)
  public void importEndpoints(
      @RequestParam TargetType targetType, @RequestPart("file") @NotNull MultipartFile file)
      throws Exception {
    mapperService.importMappersCsv(file, targetType);
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
