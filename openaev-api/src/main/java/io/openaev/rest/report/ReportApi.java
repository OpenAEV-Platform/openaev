package io.openaev.rest.report;

import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.report.form.ReportInjectCommentInput;
import io.openaev.rest.report.form.ReportInput;
import io.openaev.rest.report.model.Report;
import io.openaev.rest.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ReportApi extends RestBehavior {

  private final ExerciseService exerciseService;
  private final ReportService reportService;
  private final InjectService injectService;

  @GetMapping("/api/reports/{reportId}")
  @RBAC(
      resourceId = "#reportId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Report report(@PathVariable String reportId) {
    return this.reportService.report(UUID.fromString(reportId));
  }

  @GetMapping("/api/exercises/{simulationId}/reports/{reportId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Get a Report from a simulation")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Report returned"),
        @ApiResponse(
            responseCode = "404",
            description = "Report doesn't exist or not linked to the simulation")
      })
  public Report reportFromSimulationExercise(
      @PathVariable String simulationId, @PathVariable String reportId) {
    return this.reportService.reportFromSimulation(simulationId, UUID.fromString(reportId));
  }

  @GetMapping("/api/exercises/{exerciseId}/reports")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Report> exerciseReports(@PathVariable String exerciseId) {
    return this.reportService.reportsFromExercise(exerciseId);
  }

  @PostMapping("/api/exercises/{exerciseId}/reports")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Report createExerciseReport(
      @PathVariable String exerciseId, @Valid @RequestBody ReportInput input) {
    Exercise exercise = this.exerciseService.exercise(exerciseId);
    Report report = new Report();
    report.setExercise(exercise);
    return this.reportService.updateReport(report, input);
  }

  @PutMapping("/api/exercises/{exerciseId}/reports/{reportId}/inject-comments")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Report updateReportInjectComment(
      @PathVariable String exerciseId,
      @PathVariable String reportId,
      @Valid @RequestBody ReportInjectCommentInput input) {
    Report report = this.reportService.report(UUID.fromString(reportId));
    assert exerciseId.equals(report.getExercise().getId());
    Inject inject = this.injectService.inject(input.getInjectId());
    assert exerciseId.equals(inject.getExercise().getId());
    return this.reportService.updateReportInjectComment(report, inject, input);
  }

  @PutMapping("/api/exercises/{exerciseId}/reports/{reportId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Report updateExerciseReport(
      @PathVariable String exerciseId,
      @PathVariable String reportId,
      @Valid @RequestBody ReportInput input) {
    Report report = this.reportService.report(UUID.fromString(reportId));
    assert exerciseId.equals(report.getExercise().getId());
    return this.reportService.updateReport(report, input);
  }

  @DeleteMapping("/api/exercises/{exerciseId}/reports/{reportId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public void deleteExerciseReport(@PathVariable String exerciseId, @PathVariable String reportId) {
    Report report = this.reportService.report(UUID.fromString(reportId));
    assert exerciseId.equals(report.getExercise().getId());
    this.reportService.deleteReport(UUID.fromString(reportId));
  }
}
