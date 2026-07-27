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
 * REST API for Scenario structured PDF reports (Executive + 3 alternative Technical layouts),
 * aggregating every simulation run of a scenario within a requested comparison window (last
 * run/last week/last month/custom). Same lifecycle (PENDING/RUNNING/COMPLETED/FAILED) and
 * Document/MinIO storage as the per-simulation {@link GeneratedReportApi} and global {@link
 * GlobalGeneratedReportApi}: {@code generated_report_scenario} is set while {@code
 * generated_report_exercise} stays {@code null} for these reports. The PDF itself is still
 * assembled client-side, reusing the same pdfmake export pipeline / style kit.
 */
@RequiredArgsConstructor
@RestController
public class ScenarioGeneratedReportApi extends RestBehavior {

  private static final String SCENARIO_GENERATED_REPORT_URI =
      "/scenarios/{scenarioId}/generated-reports";

  private final GeneratedReportService generatedReportService;
  private final FileService fileService;

  @GetMapping({
    TENANT_PREFIX + SCENARIO_GENERATED_REPORT_URI,
    "/api" + SCENARIO_GENERATED_REPORT_URI
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "List generated reports (history) for a scenario")
  public List<GeneratedReport> scenarioGeneratedReports(@PathVariable String scenarioId) {
    return generatedReportService.scenarioGeneratedReports(scenarioId);
  }

  @GetMapping({
    TENANT_PREFIX + SCENARIO_GENERATED_REPORT_URI + "/{reportId}",
    "/api" + SCENARIO_GENERATED_REPORT_URI + "/{reportId}"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public GeneratedReport scenarioGeneratedReport(
      @PathVariable String scenarioId, @PathVariable String reportId) {
    return generatedReportService.scenarioGeneratedReport(scenarioId, reportId);
  }

  @PostMapping({
    TENANT_PREFIX + SCENARIO_GENERATED_REPORT_URI,
    "/api" + SCENARIO_GENERATED_REPORT_URI
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Trigger the generation of a new scenario report (PENDING)")
  public GeneratedReport createScenarioGeneratedReport(
      @PathVariable String scenarioId, @Valid @RequestBody GeneratedReportInput input) {
    return generatedReportService.createScenarioGeneratedReport(scenarioId, input);
  }

  @PutMapping({
    TENANT_PREFIX + SCENARIO_GENERATED_REPORT_URI + "/{reportId}/status",
    "/api" + SCENARIO_GENERATED_REPORT_URI + "/{reportId}/status"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Update the generation status while the client builds the scenario PDF")
  public GeneratedReport updateScenarioGeneratedReportStatus(
      @PathVariable String scenarioId,
      @PathVariable String reportId,
      @Valid @RequestBody GeneratedReportStatusInput input) {
    return generatedReportService.updateScenarioStatus(scenarioId, reportId, input);
  }

  @PostMapping({
    TENANT_PREFIX + SCENARIO_GENERATED_REPORT_URI + "/{reportId}/document",
    "/api" + SCENARIO_GENERATED_REPORT_URI + "/{reportId}/document"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Upload the finished scenario PDF built client-side and mark it COMPLETED")
  public GeneratedReport uploadScenarioGeneratedReportDocument(
      @PathVariable String scenarioId,
      @PathVariable String reportId,
      @RequestPart("file") MultipartFile file)
      throws Exception {
    return generatedReportService.attachScenarioDocument(
        scenarioId,
        reportId,
        file.getOriginalFilename(),
        file.getInputStream(),
        file.getSize(),
        file.getContentType());
  }

  @GetMapping({
    TENANT_PREFIX + SCENARIO_GENERATED_REPORT_URI + "/{reportId}/file",
    "/api" + SCENARIO_GENERATED_REPORT_URI + "/{reportId}/file"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Download a previously generated scenario report PDF")
  public ResponseEntity<InputStreamResource> downloadScenarioGeneratedReport(
      @PathVariable String scenarioId, @PathVariable String reportId) {
    GeneratedReport generatedReport =
        generatedReportService.scenarioGeneratedReport(scenarioId, reportId);
    if (generatedReport.getDocument() == null) {
      throw new ElementNotFoundException("Report file not available yet");
    }
    InputStream in =
        fileService
            .getFile(generatedReport.getDocument())
            .orElseThrow(() -> new ElementNotFoundException("File not found"));
    String filename = "scenario_" + generatedReport.getTemplate() + "_report_" + reportId + ".pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
        .body(new InputStreamResource(in));
  }

  @DeleteMapping({
    TENANT_PREFIX + SCENARIO_GENERATED_REPORT_URI + "/{reportId}",
    "/api" + SCENARIO_GENERATED_REPORT_URI + "/{reportId}"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public void deleteScenarioGeneratedReport(
      @PathVariable String scenarioId, @PathVariable String reportId) {
    generatedReportService.deleteScenarioGeneratedReport(scenarioId, reportId);
  }
}
