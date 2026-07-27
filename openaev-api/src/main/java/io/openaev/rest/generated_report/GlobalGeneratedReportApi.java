package io.openaev.rest.generated_report;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.GeneratedReport;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.generated_report.form.GeneratedReportInput;
import io.openaev.rest.generated_report.form.GeneratedReportStatusInput;
import io.openaev.rest.generated_report.service.GeneratedReportService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for "global" structured PDF reports (2 fixed templates: TECHNICAL, EXECUTIVE), covering
 * every simulation rather than a single one. Same lifecycle (PENDING/RUNNING/COMPLETED/FAILED) and
 * Document/MinIO storage as the per-simulation {@link GeneratedReportApi}, but not scoped to a
 * single exercise: {@code generated_report_exercise} is left {@code null} for these reports. The
 * PDF itself is still assembled client-side, aggregating data across every simulation the current
 * user can access, and reusing the same pdfmake export pipeline / style kit as the per-simulation
 * templates.
 */
@RequiredArgsConstructor
@RestController
public class GlobalGeneratedReportApi extends RestBehavior {

  private static final String GLOBAL_GENERATED_REPORT_URI = "/generated-reports";

  private final GeneratedReportService generatedReportService;
  private final FileService fileService;

  @GetMapping({TENANT_PREFIX + GLOBAL_GENERATED_REPORT_URI, "/api" + GLOBAL_GENERATED_REPORT_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "List generated global reports (history), covering every simulation")
  public List<GeneratedReport> globalGeneratedReports() {
    return generatedReportService.globalGeneratedReports();
  }

  @GetMapping({
    TENANT_PREFIX + GLOBAL_GENERATED_REPORT_URI + "/{reportId}",
    "/api" + GLOBAL_GENERATED_REPORT_URI + "/{reportId}"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public GeneratedReport globalGeneratedReport(@PathVariable String reportId) {
    return generatedReportService.globalGeneratedReport(reportId);
  }

  @GetMapping({
    TENANT_PREFIX + GLOBAL_GENERATED_REPORT_URI + "/all",
    "/api" + GLOBAL_GENERATED_REPORT_URI + "/all"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "List every generated report regardless of scope",
      description =
          "Unified history for the left-menu \"Reports\" page: global, simulation and scenario"
              + " reports altogether, most recent first.")
  public List<GeneratedReport> allGeneratedReports() {
    return generatedReportService.allGeneratedReports();
  }

  @PostMapping({TENANT_PREFIX + GLOBAL_GENERATED_REPORT_URI, "/api" + GLOBAL_GENERATED_REPORT_URI})
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Trigger the generation of a new global report covering every simulation (PENDING)")
  public GeneratedReport createGlobalGeneratedReport(
      @Valid @RequestBody GeneratedReportInput input) {
    return generatedReportService.createGlobalGeneratedReport(input);
  }

  @PutMapping({
    TENANT_PREFIX + GLOBAL_GENERATED_REPORT_URI + "/{reportId}/status",
    "/api" + GLOBAL_GENERATED_REPORT_URI + "/{reportId}/status"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Update the generation status while the client builds the global PDF")
  public GeneratedReport updateGlobalGeneratedReportStatus(
      @PathVariable String reportId, @Valid @RequestBody GeneratedReportStatusInput input) {
    return generatedReportService.updateGlobalStatus(reportId, input);
  }

  @PostMapping({
    TENANT_PREFIX + GLOBAL_GENERATED_REPORT_URI + "/{reportId}/document",
    "/api" + GLOBAL_GENERATED_REPORT_URI + "/{reportId}/document"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Upload the finished global PDF built client-side and mark it COMPLETED")
  public GeneratedReport uploadGlobalGeneratedReportDocument(
      @PathVariable String reportId, @RequestPart("file") MultipartFile file) throws Exception {
    return generatedReportService.attachGlobalDocument(
        reportId,
        file.getOriginalFilename(),
        file.getInputStream(),
        file.getSize(),
        file.getContentType());
  }

  @GetMapping({
    TENANT_PREFIX + GLOBAL_GENERATED_REPORT_URI + "/{reportId}/file",
    "/api" + GLOBAL_GENERATED_REPORT_URI + "/{reportId}/file"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Download a previously generated global report PDF")
  public ResponseEntity<InputStreamResource> downloadGlobalGeneratedReport(
      @PathVariable String reportId) {
    GeneratedReport generatedReport = generatedReportService.globalGeneratedReport(reportId);
    if (generatedReport.getDocument() == null) {
      throw new ElementNotFoundException("Report file not available yet");
    }
    InputStream in =
        fileService
            .getFile(generatedReport.getDocument())
            .orElseThrow(() -> new ElementNotFoundException("File not found"));
    String filename = "global_" + generatedReport.getTemplate() + "_report_" + reportId + ".pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
        .body(new InputStreamResource(in));
  }

  @DeleteMapping({
    TENANT_PREFIX + GLOBAL_GENERATED_REPORT_URI + "/{reportId}",
    "/api" + GLOBAL_GENERATED_REPORT_URI + "/{reportId}"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public void deleteGlobalGeneratedReport(@PathVariable String reportId) {
    generatedReportService.deleteGlobalGeneratedReport(reportId);
  }
}
