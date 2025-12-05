package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.rest.report.form.ReportInformationInput;
import io.openaev.rest.report.form.ReportInjectCommentInput;
import io.openaev.rest.report.form.ReportInput;
import io.openaev.rest.report.model.Report;
import io.openaev.rest.report.model.ReportInformation;
import io.openaev.rest.report.model.ReportInformationsType;
import io.openaev.rest.report.model.ReportInjectComment;
import io.openaev.rest.report.repository.ReportRepository;
import io.openaev.rest.report.service.ReportService;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@ExtendWith(MockitoExtension.class)
public class ReportServiceTest extends IntegrationTest {

  @Mock private ReportRepository reportRepository;

  private ReportService reportService;

  @BeforeEach
  void before() {
    // Injecting mocks into the controller
    reportService = new ReportService(reportRepository);
  }

  @DisplayName("Test create a report")
  @Test
  void createReport() throws Exception {
    // -- PREPARE --
    Report report = new Report();

    ReportInput reportInput = new ReportInput();
    reportInput.setName("test");
    ReportInformationInput reportInformationInput = new ReportInformationInput();
    reportInformationInput.setReportInformationsType(ReportInformationsType.MAIN_INFORMATION);
    reportInformationInput.setReportInformationsDisplay(true);
    ReportInformationInput reportInformationInput2 = new ReportInformationInput();
    reportInformationInput2.setReportInformationsType(ReportInformationsType.SCORE_DETAILS);
    reportInformationInput2.setReportInformationsDisplay(true);
    reportInput.setReportInformations(List.of(reportInformationInput, reportInformationInput2));

    when(reportRepository.save(any(Report.class))).thenReturn(report);

    // -- EXECUTE --
    reportService.updateReport(report, reportInput);

    // -- ASSERT --
    ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(reportCaptor.capture());
    Report capturedReport = reportCaptor.getValue();
    assertEquals(reportInput.getName(), capturedReport.getName());
    assertEquals(2, capturedReport.getReportInformations().size());
    ReportInformation reportInformationCaptured =
        capturedReport.getReportInformations().stream()
            .filter(r -> r.getReportInformationsType() == ReportInformationsType.MAIN_INFORMATION)
            .findFirst()
            .orElse(null);
    assert reportInformationCaptured != null;
    assertEquals(true, reportInformationCaptured.getReportInformationsDisplay());
    ReportInformation reportInformationCaptured2 =
        capturedReport.getReportInformations().stream()
            .filter(r -> r.getReportInformationsType() == ReportInformationsType.SCORE_DETAILS)
            .findFirst()
            .orElse(null);
    assert reportInformationCaptured2 != null;
    assertEquals(true, reportInformationCaptured2.getReportInformationsDisplay());
  }

  @DisplayName("Test update a report")
  @Test
  void updateReport() throws Exception {
    // -- PREPARE --
    Report report = new Report();
    report.setName("test");
    ReportInformation reportInformation = new ReportInformation();
    reportInformation.setReportInformationsType(ReportInformationsType.MAIN_INFORMATION);
    reportInformation.setReportInformationsDisplay(false);
    report.setReportInformations(List.of(reportInformation));

    ReportInput reportInput = new ReportInput();
    reportInput.setName("new name test");
    ReportInformationInput reportInformationInput = new ReportInformationInput();
    reportInformationInput.setReportInformationsType(ReportInformationsType.MAIN_INFORMATION);
    reportInformationInput.setReportInformationsDisplay(true);
    reportInput.setReportInformations(List.of(reportInformationInput));

    when(reportRepository.save(any(Report.class))).thenReturn(report);

    // -- EXECUTE --
    reportService.updateReport(report, reportInput);

    // -- ASSERT --
    ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(reportCaptor.capture());
    Report capturedReport = reportCaptor.getValue();
    assertEquals(reportInput.getName(), capturedReport.getName());
    assertEquals(1, capturedReport.getReportInformations().size());
    assertEquals(
        true, capturedReport.getReportInformations().getFirst().getReportInformationsDisplay());
  }

  @Nested
  @DisplayName("Reports inject comment")
  class ReportInjectCommentTest {
    @DisplayName("Test update existing report inject comment")
    @Test
    void updateExistingReportInjectComment() {
      // -- PREPARE --
      Report report = new Report();
      report.setName("test");
      report.setId(UUID.randomUUID().toString());
      Inject inject = new Inject();
      inject.setId("fakeID123");

      // add report inject comment
      ReportInjectComment existingReportInjectComment = new ReportInjectComment();
      existingReportInjectComment.setReport(report);
      existingReportInjectComment.setComment("comment");
      existingReportInjectComment.setInject(inject);
      report.setReportInjectsComments(List.of(existingReportInjectComment));

      ReportInjectCommentInput commentInput = new ReportInjectCommentInput();
      commentInput.setInjectId(inject.getId());
      commentInput.setComment("New comment");

      // Mock
      when(reportRepository.findReportInjectComment(
              eq(UUID.fromString(report.getId())), eq(inject.getId())))
          .thenReturn(Optional.of(existingReportInjectComment));

      // -- EXECUTE --
      reportService.updateReportInjectComment(report, inject, commentInput);

      // -- ASSERT --
      ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
      verify(reportRepository).save(reportCaptor.capture());
      Report capturedReport = reportCaptor.getValue();
      assertEquals(1, capturedReport.getReportInjectsComments().size());
      assertEquals(
          commentInput.getComment(),
          capturedReport.getReportInjectsComments().getFirst().getComment());
    }

    @DisplayName("Test add new report inject comment")
    @Test
    void addReportInjectComment() throws Exception {
      // -- PREPARE --
      Report report = new Report();
      report.setName("test");
      report.setId(UUID.randomUUID().toString());
      Inject inject = new Inject();
      inject.setId("fakeID123");

      ReportInjectCommentInput commentInput = new ReportInjectCommentInput();
      commentInput.setInjectId(inject.getId());
      commentInput.setComment("New test comment");

      // Mock
      when(reportRepository.findReportInjectComment(
              eq(UUID.fromString(report.getId())), eq(inject.getId())))
          .thenReturn(Optional.empty());

      // -- EXECUTE --
      reportService.updateReportInjectComment(report, inject, commentInput);

      // -- ASSERT --
      ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
      verify(reportRepository).save(reportCaptor.capture());
      Report capturedReport = reportCaptor.getValue();
      assertEquals(1, capturedReport.getReportInjectsComments().size());
      assertEquals(
          commentInput.getComment(),
          capturedReport.getReportInjectsComments().getFirst().getComment());
    }
  }
}
