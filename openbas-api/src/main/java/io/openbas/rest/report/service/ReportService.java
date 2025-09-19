package io.openbas.rest.report.service;

import static java.time.Instant.now;

import io.openbas.database.model.*;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.report.form.ReportInjectCommentInput;
import io.openbas.rest.report.form.ReportInput;
import io.openbas.rest.report.model.Report;
import io.openbas.rest.report.model.ReportInformation;
import io.openbas.rest.report.model.ReportInjectComment;
import io.openbas.rest.report.repository.ReportRepository;
import io.openbas.rest.report.specification.ReportSpecification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ReportService {
  private final ReportRepository reportRepository;

  public Report report(@NotNull final UUID reportId) {
    return this.reportRepository.findById(reportId).orElseThrow(ElementNotFoundException::new);
  }

  /**
   * Return the report based on the Simulation id, if the report doesn't exist or is not linked to
   * the simulation, it throws a ElementNotFoundException
   *
   * @param simulationId
   * @param reportId
   * @return
   */
  public Report reportFromSimulation(
      @NotBlank final String simulationId, @NotNull final UUID reportId) {
    return this.reportRepository
        .findByIdAndExercise_Id(reportId, simulationId)
        .orElseThrow(ElementNotFoundException::new);
  }

  public List<Report> reportsFromExercise(@NotNull final String exerciseId) {
    return this.reportRepository.findAll(ReportSpecification.fromExercise(exerciseId));
  }

  public Report updateReport(@NotNull final Report report, @NotNull final ReportInput input) {
    report.setUpdateAttributes(input);
    report.setUpdateDate(now());
    input
        .getReportInformations()
        .forEach(
            i -> {
              ReportInformation reportInformation =
                  report.getReportInformations().stream()
                      .filter(
                          r -> r.getReportInformationsType().equals(i.getReportInformationsType()))
                      .findFirst()
                      .orElse(null);
              if (reportInformation != null) {
                reportInformation.setReportInformationsDisplay(i.getReportInformationsDisplay());
              } else {
                reportInformation = new ReportInformation();
                reportInformation.setReport(report);
                reportInformation.setReportInformationsDisplay(i.getReportInformationsDisplay());
                reportInformation.setReportInformationsType(i.getReportInformationsType());
                report.getReportInformations().add(reportInformation);
              }
            });
    return this.reportRepository.save(report);
  }

  public Report updateReportInjectComment(
      @NotNull final Report report,
      @NotNull final Inject inject,
      @NotNull final ReportInjectCommentInput input) {
    Optional<ReportInjectComment> reportInjectComment =
        this.reportRepository.findReportInjectComment(
            UUID.fromString(report.getId()), inject.getId());
    ReportInjectComment injectComment;
    if (reportInjectComment.isPresent()) {
      injectComment = reportInjectComment.get();
      injectComment.setComment(input.getComment());
    } else {
      injectComment = new ReportInjectComment();
      injectComment.setInject(inject);
      injectComment.setReport(report);
      injectComment.setComment(input.getComment());
      report.getReportInjectsComments().add(injectComment);
    }
    return this.reportRepository.save(report);
  }

  public void deleteReport(@NotBlank final UUID reportId) {
    this.reportRepository.deleteById(reportId);
  }
}
