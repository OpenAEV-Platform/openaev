package io.openaev.api.marking_definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.import_report.ImportReportMapper;
import io.openaev.api.import_report.dto.ImportReport;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.UserService;
import io.openaev.service.marking_definition.MarkingDefinitionService;
import io.openaev.service.marking_definition.MarkingDefinitionStixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping({MarkingDefinitionApi.TENANT_MARKING_DEFINITIONS_URI})
@Tag(
    name = "Marking definition importer API",
    description = "Operations related to the import of marking definitions")
public class MarkingDefinitionImporterApi extends RestBehavior {

  private final MarkingDefinitionService service;
  private final MarkingDefinitionStixService stixService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final UserService userService;

  // -- STIX (OpenCTI format) --

  @PostMapping("/stix")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(
      summary = "Import marking definitions from OpenCTI's STIX 2.1 format",
      description =
          "Creates the marking-definition objects of a STIX bundle the tenant does not have,"
              + " keeping their STIX id. Nothing is overwritten: each object is reported CREATED,"
              + " EXISTING (same STIX id), CONFLICT (same type and definition, another id) or"
              + " INVALID.")
  public List<MarkingDefinitionStixService.ImportLine> importStix(
      TxCtx ctx, @RequestBody JsonNode stix) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return stixService.importStix(stix, tenantId, userService.currentUser().getId());
  }

  // -- CSV --

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
      TxCtx ctx, @RequestPart("file") @NotNull MultipartFile file) {
    service.assertFeatureEnabled();
    service.validateUploadedFile(file);
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return ImportReportMapper.toReport(
        service.importCsv(tenantId, userService.currentUser().getId(), file));
  }
}
