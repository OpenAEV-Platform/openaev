package io.openaev.rest.generated_report.service;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.database.model.*;
import io.openaev.database.repository.GeneratedReportRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.document.form.DocumentCreateInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.generated_report.form.GeneratedReportInput;
import io.openaev.rest.generated_report.form.GeneratedReportStatusInput;
import io.openaev.service.scenario.ScenarioService;
import jakarta.validation.constraints.NotBlank;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Handles the traceable lifecycle (PENDING -> RUNNING -> COMPLETED/FAILED) of structured PDF
 * reports. The PDF itself is assembled client-side (reusing the existing dashboard widget system
 * and pdfmake export pipeline); this service only tracks generation state and, once the browser
 * finishes building the PDF, stores it via the existing {@link DocumentService}/MinIO pipeline.
 */
@Service
@RequiredArgsConstructor
public class GeneratedReportService {

  private final GeneratedReportRepository generatedReportRepository;
  private final ExerciseService exerciseService;
  private final ScenarioService scenarioService;
  private final DocumentService documentService;
  private final UserRepository userRepository;

  private static void applyCommonInput(
      GeneratedReport generatedReport, GeneratedReportInput input) {
    generatedReport.setTemplate(input.getTemplate());
    generatedReport.setTriggerSource(
        input.getTriggerSource() != null
            ? input.getTriggerSource()
            : GeneratedReportTriggerSource.MANUAL);
    generatedReport.setLabel(input.getLabel());
    generatedReport.setStatus(GeneratedReportStatus.PENDING);
  }

  public List<GeneratedReport> generatedReportsFromExercise(@NotBlank final String exerciseId) {
    return generatedReportRepository.findAllByExerciseIdOrderByCreationDateDesc(exerciseId);
  }

  public GeneratedReport generatedReport(
      @NotBlank final String exerciseId, @NotBlank final String id) {
    return generatedReportRepository
        .findByIdAndExerciseId(id, exerciseId)
        .orElseThrow(() -> new ElementNotFoundException("Generated report not found"));
  }

  public GeneratedReport createGeneratedReport(
      @NotBlank final String exerciseId, GeneratedReportInput input) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    GeneratedReport generatedReport = new GeneratedReport();
    generatedReport.setExercise(exercise);
    applyCommonInput(generatedReport, input);
    userRepository.findById(currentUser().getId()).ifPresent(generatedReport::setCreatedBy);
    return generatedReportRepository.save(generatedReport);
  }

  public GeneratedReport updateStatus(
      @NotBlank final String exerciseId,
      @NotBlank final String id,
      GeneratedReportStatusInput input) {
    GeneratedReport generatedReport = generatedReport(exerciseId, id);
    generatedReport.setStatus(input.getStatus());
    generatedReport.setErrorMessage(input.getErrorMessage());
    return generatedReportRepository.save(generatedReport);
  }

  public GeneratedReport attachDocument(
      @NotBlank final String exerciseId,
      @NotBlank final String id,
      String fileName,
      InputStream fileIS,
      long fileSize,
      String fileContentType)
      throws Exception {
    GeneratedReport generatedReport = generatedReport(exerciseId, id);
    DocumentCreateInput documentInput = new DocumentCreateInput();
    documentInput.setDescription(
        "Generated " + generatedReport.getTemplate() + " report for exercise " + exerciseId);
    documentInput.setExerciseIds(Collections.singletonList(exerciseId));
    Document document =
        documentService.upsert(fileName, fileIS, fileSize, fileContentType, documentInput);
    generatedReport.setDocument(document);
    generatedReport.setStatus(GeneratedReportStatus.COMPLETED);
    generatedReport.setErrorMessage(null);
    return generatedReportRepository.save(generatedReport);
  }

  public void deleteGeneratedReport(@NotBlank final String exerciseId, @NotBlank final String id) {
    GeneratedReport generatedReport = generatedReport(exerciseId, id);
    generatedReportRepository.delete(generatedReport);
  }

  // -- GLOBAL (cross-simulation) reports: same lifecycle, but not scoped to a single exercise --

  public List<GeneratedReport> globalGeneratedReports() {
    return generatedReportRepository
        .findAllByExerciseIsNullAndScenarioIsNullOrderByCreationDateDesc();
  }

  /**
   * Every report regardless of scope (global/simulation/scenario), for the unified "Reports" page.
   */
  public List<GeneratedReport> allGeneratedReports() {
    return generatedReportRepository.findAllByOrderByCreationDateDesc();
  }

  public GeneratedReport globalGeneratedReport(@NotBlank final String id) {
    return generatedReportRepository
        .findByIdAndExerciseIsNull(id)
        .orElseThrow(() -> new ElementNotFoundException("Generated report not found"));
  }

  public GeneratedReport createGlobalGeneratedReport(GeneratedReportInput input) {
    GeneratedReport generatedReport = new GeneratedReport();
    applyCommonInput(generatedReport, input);
    userRepository.findById(currentUser().getId()).ifPresent(generatedReport::setCreatedBy);
    return generatedReportRepository.save(generatedReport);
  }

  public GeneratedReport updateGlobalStatus(
      @NotBlank final String id, GeneratedReportStatusInput input) {
    GeneratedReport generatedReport = globalGeneratedReport(id);
    generatedReport.setStatus(input.getStatus());
    generatedReport.setErrorMessage(input.getErrorMessage());
    return generatedReportRepository.save(generatedReport);
  }

  public GeneratedReport attachGlobalDocument(
      @NotBlank final String id,
      String fileName,
      InputStream fileIS,
      long fileSize,
      String fileContentType)
      throws Exception {
    GeneratedReport generatedReport = globalGeneratedReport(id);
    DocumentCreateInput documentInput = new DocumentCreateInput();
    documentInput.setDescription(
        "Generated global " + generatedReport.getTemplate() + " report (all simulations)");
    Document document =
        documentService.upsert(fileName, fileIS, fileSize, fileContentType, documentInput);
    generatedReport.setDocument(document);
    generatedReport.setStatus(GeneratedReportStatus.COMPLETED);
    generatedReport.setErrorMessage(null);
    return generatedReportRepository.save(generatedReport);
  }

  public void deleteGlobalGeneratedReport(@NotBlank final String id) {
    GeneratedReport generatedReport = globalGeneratedReport(id);
    generatedReportRepository.delete(generatedReport);
  }

  // -- SCENARIO reports: aggregate every run of one scenario within a comparison window --

  public List<GeneratedReport> scenarioGeneratedReports(@NotBlank final String scenarioId) {
    return generatedReportRepository.findAllByScenarioIdOrderByCreationDateDesc(scenarioId);
  }

  public GeneratedReport scenarioGeneratedReport(
      @NotBlank final String scenarioId, @NotBlank final String id) {
    return generatedReportRepository
        .findByIdAndScenarioId(id, scenarioId)
        .orElseThrow(() -> new ElementNotFoundException("Generated report not found"));
  }

  public GeneratedReport createScenarioGeneratedReport(
      @NotBlank final String scenarioId, GeneratedReportInput input) {
    Scenario scenario = scenarioService.scenario(scenarioId);
    GeneratedReport generatedReport = new GeneratedReport();
    generatedReport.setScenario(scenario);
    applyCommonInput(generatedReport, input);
    userRepository.findById(currentUser().getId()).ifPresent(generatedReport::setCreatedBy);
    return generatedReportRepository.save(generatedReport);
  }

  public GeneratedReport updateScenarioStatus(
      @NotBlank final String scenarioId,
      @NotBlank final String id,
      GeneratedReportStatusInput input) {
    GeneratedReport generatedReport = scenarioGeneratedReport(scenarioId, id);
    generatedReport.setStatus(input.getStatus());
    generatedReport.setErrorMessage(input.getErrorMessage());
    return generatedReportRepository.save(generatedReport);
  }

  public GeneratedReport attachScenarioDocument(
      @NotBlank final String scenarioId,
      @NotBlank final String id,
      String fileName,
      InputStream fileIS,
      long fileSize,
      String fileContentType)
      throws Exception {
    GeneratedReport generatedReport = scenarioGeneratedReport(scenarioId, id);
    DocumentCreateInput documentInput = new DocumentCreateInput();
    documentInput.setDescription(
        "Generated " + generatedReport.getTemplate() + " report for scenario " + scenarioId);
    Document document =
        documentService.upsert(fileName, fileIS, fileSize, fileContentType, documentInput);
    generatedReport.setDocument(document);
    generatedReport.setStatus(GeneratedReportStatus.COMPLETED);
    generatedReport.setErrorMessage(null);
    return generatedReportRepository.save(generatedReport);
  }

  public void deleteScenarioGeneratedReport(
      @NotBlank final String scenarioId, @NotBlank final String id) {
    GeneratedReport generatedReport = scenarioGeneratedReport(scenarioId, id);
    generatedReportRepository.delete(generatedReport);
  }
}
