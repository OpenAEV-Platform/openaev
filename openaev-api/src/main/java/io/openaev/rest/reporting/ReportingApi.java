package io.openaev.rest.reporting;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Document;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationTrigger;
import io.openaev.database.model.ReportingSchedule;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.reporting.form.ReportingGenerateInput;
import io.openaev.rest.reporting.form.ReportingInput;
import io.openaev.rest.reporting.form.ReportingScheduleInput;
import io.openaev.service.FileService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ReportingApi.REPORTINGS_URI, ReportingApi.TENANT_REPORTINGS_URI})
@RequiredArgsConstructor
public class ReportingApi extends RestBehavior {

  public static final String REPORTINGS_URI = "/api/reportings";
  public static final String TENANT_REPORTINGS_URI = TENANT_PREFIX + "/reportings";

  private final ReportingService reportingService;
  private final FileService fileService;

  // -- CREATE --

  @PostMapping
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.REPORT)
  @Operation(summary = "Create a reporting template")
  public ResponseEntity<Reporting> createReporting(
      @RequestBody @Valid @NotNull final ReportingInput input, TxCtx ctx) {
    return ResponseEntity.ok(
        this.reportingService.createReporting(input.toReporting(new Reporting())));
  }

  // -- READ --

  @PostMapping("/search")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.REPORT)
  @Operation(summary = "Search reporting templates with pagination")
  public ResponseEntity<Page<Reporting>> reportings(
      @RequestBody @Valid @NotNull final SearchPaginationInput searchPaginationInput, TxCtx ctx) {
    return ResponseEntity.ok(this.reportingService.reportings(searchPaginationInput));
  }

  @GetMapping("/{reportingId}")
  @Transactional
  @AccessControl(
      resourceId = "#reportingId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.REPORT)
  @Operation(summary = "Get a reporting template by id")
  public ResponseEntity<Reporting> reporting(@PathVariable @NotBlank final String reportingId) {
    return ResponseEntity.ok(this.reportingService.reporting(reportingId));
  }

  @GetMapping({"/context/{contextType}", "/context/{contextType}/{contextId}"})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.REPORT)
  @Operation(summary = "List the reporting templates of a subject (context)")
  public ResponseEntity<List<Reporting>> reportingsByContext(
      @PathVariable @NotNull final ReportingContextType contextType,
      @PathVariable(required = false) final String contextId,
      TxCtx ctx) {
    return ResponseEntity.ok(this.reportingService.reportingsByContext(contextType, contextId));
  }

  // -- UPDATE --

  @PutMapping("/{reportingId}")
  @Transactional
  @AccessControl(
      resourceId = "#reportingId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.REPORT)
  @Operation(summary = "Update a reporting template")
  public ResponseEntity<Reporting> updateReporting(
      @PathVariable @NotBlank final String reportingId,
      @RequestBody @Valid @NotNull final ReportingInput input) {
    Reporting existing = this.reportingService.reporting(reportingId);
    return ResponseEntity.ok(this.reportingService.updateReporting(input.toReporting(existing)));
  }

  // -- DELETE --

  @DeleteMapping("/{reportingId}")
  @Transactional
  @AccessControl(
      resourceId = "#reportingId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.REPORT)
  @Operation(summary = "Delete a reporting template")
  public ResponseEntity<Void> deleteReporting(@PathVariable @NotBlank final String reportingId) {
    this.reportingService.deleteReporting(reportingId);
    return ResponseEntity.noContent().build();
  }

  // -- GENERATIONS --

  @PostMapping("/{reportingId}/generate")
  @Transactional
  @AccessControl(
      resourceId = "#reportingId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.REPORT)
  @Operation(summary = "Request the generation of a reporting template")
  public ResponseEntity<ReportingGeneration> generateReporting(
      @PathVariable @NotBlank final String reportingId,
      @RequestBody @Valid @NotNull final ReportingGenerateInput input) {
    return ResponseEntity.ok(
        this.reportingService.requestGeneration(
            reportingId, input.getFormat(), ReportingGenerationTrigger.MANUAL));
  }

  @GetMapping("/{reportingId}/generations")
  @Transactional
  @AccessControl(
      resourceId = "#reportingId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.REPORT)
  @Operation(summary = "List the generations of a reporting template")
  public ResponseEntity<List<ReportingGeneration>> reportingGenerations(
      @PathVariable @NotBlank final String reportingId) {
    return ResponseEntity.ok(this.reportingService.generations(reportingId));
  }

  @GetMapping("/generations/{generationId}")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.REPORT)
  @Operation(summary = "Get a reporting generation by id")
  public ResponseEntity<ReportingGeneration> reportingGeneration(
      @PathVariable @NotBlank final String generationId, TxCtx ctx) {
    return ResponseEntity.ok(this.reportingService.generation(generationId));
  }

  @DeleteMapping("/generations/{generationId}")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.REPORT)
  @Operation(summary = "Delete a reporting generation and its document")
  public ResponseEntity<Void> deleteReportingGeneration(
      @PathVariable @NotBlank final String generationId, TxCtx ctx) {
    this.reportingService.deleteGeneration(generationId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/generations/{generationId}/file")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.REPORT)
  @Operation(summary = "Download the document of a successful reporting generation")
  public ResponseEntity<InputStreamResource> downloadReportingGeneration(
      @PathVariable @NotBlank final String generationId, TxCtx ctx) {
    Document document = this.reportingService.generationDocument(generationId);
    String encodedFilename = DocumentService.encodeFileName(document.getName());
    InputStream in =
        this.fileService
            .getFile(document)
            .orElseThrow(() -> new ElementNotFoundException("File not found"));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encodedFilename)
        .header(HttpHeaders.CONTENT_TYPE, document.getType())
        .body(new InputStreamResource(in));
  }

  // -- SCHEDULES --

  @PostMapping("/{reportingId}/schedules")
  @Transactional
  @AccessControl(
      resourceId = "#reportingId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.REPORT)
  @Operation(summary = "Create a schedule on a reporting template")
  public ResponseEntity<ReportingSchedule> createReportingSchedule(
      @PathVariable @NotBlank final String reportingId,
      @RequestBody @Valid @NotNull final ReportingScheduleInput input) {
    return ResponseEntity.ok(this.reportingService.createSchedule(reportingId, input));
  }

  @PutMapping("/{reportingId}/schedules/{scheduleId}")
  @Transactional
  @AccessControl(
      resourceId = "#reportingId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.REPORT)
  @Operation(summary = "Update a schedule of a reporting template")
  public ResponseEntity<ReportingSchedule> updateReportingSchedule(
      @PathVariable @NotBlank final String reportingId,
      @PathVariable @NotBlank final String scheduleId,
      @RequestBody @Valid @NotNull final ReportingScheduleInput input) {
    return ResponseEntity.ok(this.reportingService.updateSchedule(reportingId, scheduleId, input));
  }

  @DeleteMapping("/{reportingId}/schedules/{scheduleId}")
  @Transactional
  @AccessControl(
      resourceId = "#reportingId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.REPORT)
  @Operation(summary = "Delete a schedule of a reporting template")
  public ResponseEntity<Void> deleteReportingSchedule(
      @PathVariable @NotBlank final String reportingId,
      @PathVariable @NotBlank final String scheduleId) {
    this.reportingService.deleteSchedule(reportingId, scheduleId);
    return ResponseEntity.noContent().build();
  }
}
