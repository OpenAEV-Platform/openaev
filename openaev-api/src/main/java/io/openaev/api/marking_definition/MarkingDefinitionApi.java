package io.openaev.api.marking_definition;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.import_report.ImportReportMapper;
import io.openaev.api.import_report.dto.ImportReport;
import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.api.marking_definition.form.MarkingDefinitionOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.MapperService;
import io.openaev.service.UserService;
import io.openaev.service.marking_definition.MarkingDefinitionService;
import io.openaev.utils.CsvType;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping({MarkingDefinitionApi.TENANT_MARKING_DEFINITIONS_URI})
@Tag(name = "Marking definition API", description = "Operations related to marking definitions")
public class MarkingDefinitionApi extends RestBehavior {

  public static final String TENANT_MARKING_DEFINITIONS_URI =
      TENANT_PREFIX + "/marking_definitions";

  private final MarkingDefinitionService service;
  private final MapperService mapperService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final UserService userService;

  // -- SEARCH --

  @LogExecutionTime
  @GetMapping("/assignable")
  @Transactional(readOnly = true)
  // No capability needed: the result is already narrowed to the caller's own clearance.
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "Get the marking definitions the current user is cleared for",
      description =
          "The marking definitions of the tenants in scope narrowed to the caller's own clearance"
              + " (cumulative per type - holding TLP:AMBER also covers TLP:GREEN and TLP:CLEAR), the"
              + " same rule enforced server-side when a marking is actually assigned. Built for an"
              + " assignment picker and for the marking filter's value picker.")
  public List<MarkingDefinitionOutput> assignable(TxCtx ctx) {
    return service.listAssignable(ctx).stream().map(MarkingDefinitionMapper::toOutput).toList();
  }

  @LogExecutionTime
  @GetMapping("/{markingDefinitionId}/assigned")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#markingDefinitionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Check if a marking definition is currently assigned to any entity")
  public boolean assigned(TxCtx ctx, @PathVariable String markingDefinitionId) {
    return service.isAssigned(ctx, markingDefinitionId);
  }

  @LogExecutionTime
  @PostMapping("/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Search marking definitions")
  public Page<MarkingDefinitionOutput> search(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return service.search(ctx, searchPaginationInput).map(MarkingDefinitionMapper::toOutput);
  }

  // -- EXPORT --

  @LogExecutionTime
  @PostMapping("/export/csv")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(
      summary = "Export marking definitions as CSV",
      description =
          "Exports the marking definitions matching the search as CSV; answers 404 while the"
              + " marking preview feature is off.")
  public void exportMarkingDefinitionsCsv(
      TxCtx ctx,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      HttpServletResponse response) {
    service.assertFeatureEnabled();
    mapperService.exportMappersCsv(
        ctx, CsvType.MARKING_DEFINITIONS, searchPaginationInput, response);
  }

  // -- IMPORT --

  @LogExecutionTime
  @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(
      summary = "Import marking definitions from CSV",
      description =
          "Creates the valid rows of a CSV file (1 MB max) and reports every row; existing"
              + " markings are never overwritten.")
  public ImportReport importMarkingDefinitionsCsv(
      TxCtx ctx, @RequestPart("file") @NotNull MultipartFile file) throws Exception {
    service.assertFeatureEnabled();
    service.validateUploadedFile(file);
    return ImportReportMapper.toReport(
        mapperService.importMappersCsv(ctx, file, CsvType.MARKING_DEFINITIONS));
  }

  // -- CREATE --

  @PostMapping
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Create a marking definition")
  public MarkingDefinitionOutput create(
      TxCtx ctx, @Valid @RequestBody MarkingDefinitionInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    MarkingDefinition created = service.create(input, tenantId, userService.currentUser().getId());
    return MarkingDefinitionMapper.toOutput(created);
  }

  // -- UPDATE --

  @PutMapping("/{markingDefinitionId}")
  @Transactional
  @AccessControl(
      resourceId = "#markingDefinitionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Update a marking definition")
  public MarkingDefinitionOutput update(
      TxCtx ctx,
      @PathVariable String markingDefinitionId,
      @Valid @RequestBody MarkingDefinitionInput input) {
    return MarkingDefinitionMapper.toOutput(
        service.update(ctx, markingDefinitionId, input, userService.currentUser().getId()));
  }

  // -- DELETE --

  @DeleteMapping("/{markingDefinitionId}")
  @Transactional
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AccessControl(
      resourceId = "#markingDefinitionId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(summary = "Delete a marking definition")
  public void delete(TxCtx ctx, @PathVariable String markingDefinitionId) {
    service.delete(ctx, markingDefinitionId, userService.currentUser().getId());
  }
}
