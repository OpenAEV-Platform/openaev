package io.openaev.rest.generated_report;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;

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
 * REST API for the structured PDF "Reports" feature (2 fixed templates: TECHNICAL, EXECUTIVE).
 *
 * <p>The PDF is assembled client-side (reusing the existing dashboard widget system + pdfmake
 * export pipeline). This API only tracks generation lifecycle (PENDING/RUNNING/COMPLETED/FAILED)
 * and stores/serves the resulting PDF via the existing Document/MinIO storage, so past reports can
 * be listed and re-downloaded.
 */
@RequiredArgsConstructor
@RestController
public class GeneratedReportApi extends RestBehavior {

  private static final String GENERATED_REPORT_URI = "/generated-reports";

  private final GeneratedReportService generatedReportService;
  private final FileService fileService;

  @GetMapping({
    TENANT_EXERCISE_URI + "/{exerciseId}" + GENERATED_REPORT_URI,
    "/api/exercises/{exerciseId}" + GENERATED_REPORT_URI
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "List generated reports (history) for a simulation")
  public List<GeneratedReport> generatedReports(@PathVariable String exerciseId) {
    return generatedReportService.generatedReportsFromExercise(exerciseId);
  }

  @GetMapping({
    TENANT_EXERCISE_URI + "/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}",
    "/api/exercises/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public GeneratedReport generatedReport(
      @PathVariable String exerciseId, @PathVariable String reportId) {
    return generatedReportService.generatedReport(exerciseId, reportId);
  }

  @PostMapping({
    TENANT_EXERCISE_URI + "/{exerciseId}" + GENERATED_REPORT_URI,
    "/api/exercises/{exerciseId}" + GENERATED_REPORT_URI
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Trigger the generation of a new structured report (PENDING)")
  public GeneratedReport createGeneratedReport(
      @PathVariable String exerciseId, @Valid @RequestBody GeneratedReportInput input) {
    return generatedReportService.createGeneratedReport(exerciseId, input);
  }

  @PutMapping({
    TENANT_EXERCISE_URI + "/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}/status",
    "/api/exercises/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}/status"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Update the generation status while the client builds the PDF")
  public GeneratedReport updateGeneratedReportStatus(
      @PathVariable String exerciseId,
      @PathVariable String reportId,
      @Valid @RequestBody GeneratedReportStatusInput input) {
    return generatedReportService.updateStatus(exerciseId, reportId, input);
  }

  @PostMapping({
    TENANT_EXERCISE_URI + "/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}/document",
    "/api/exercises/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}/document"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Upload the finished PDF built client-side and mark the report COMPLETED")
  public GeneratedReport uploadGeneratedReportDocument(
      @PathVariable String exerciseId,
      @PathVariable String reportId,
      @RequestPart("file") MultipartFile file)
      throws Exception {
    return generatedReportService.attachDocument(
        exerciseId,
        reportId,
        file.getOriginalFilename(),
        file.getInputStream(),
        file.getSize(),
        file.getContentType());
  }

  @GetMapping({
    TENANT_EXERCISE_URI + "/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}/file",
    "/api/exercises/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}/file"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Download a previously generated report PDF")
  public ResponseEntity<InputStreamResource> downloadGeneratedReport(
      @PathVariable String exerciseId, @PathVariable String reportId) {
    GeneratedReport generatedReport = generatedReportService.generatedReport(exerciseId, reportId);
    if (generatedReport.getDocument() == null) {
      throw new ElementNotFoundException("Report file not available yet");
    }
    InputStream in =
        fileService
            .getFile(generatedReport.getDocument())
            .orElseThrow(() -> new ElementNotFoundException("File not found"));
    String filename = generatedReport.getTemplate() + "_report_" + reportId + ".pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
        .body(new InputStreamResource(in));
  }

  @DeleteMapping({
    TENANT_EXERCISE_URI + "/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}",
    "/api/exercises/{exerciseId}" + GENERATED_REPORT_URI + "/{reportId}"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public void deleteGeneratedReport(
      @PathVariable String exerciseId, @PathVariable String reportId) {
    generatedReportService.deleteGeneratedReport(exerciseId, reportId);
  }
}
