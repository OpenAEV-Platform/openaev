package io.openaev.rest.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.DataAttachment;
import io.openaev.database.model.Document;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.ReportingGenerationTrigger;
import io.openaev.database.model.ReportingSchedule;
import io.openaev.database.model.ReportingSchedulePeriod;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.execution.ExecutionContext;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.rest.reporting.ReportingService;
import io.openaev.service.FileService;
import io.openaev.service.UserService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Reporting schedule engine")
class ReportingScheduleServiceTest {

  // 2026-07-20 09:00 UTC is a Monday
  private static final Instant DUE = Instant.parse("2026-07-20T09:00:00Z");
  private static final Instant DUE_MINUTE = DUE.truncatedTo(ChronoUnit.MINUTES);

  private ReportingScheduleLoader scheduleLoader;
  private ReportingService reportingService;
  private ReportingGenerationRepository generationRepository;
  private UserService userService;
  private EmailService emailService;
  private FileService fileService;
  private OpenAEVConfig openAEVConfig;
  private ReportingScheduleService scheduleService;

  @BeforeEach
  void setUp() {
    scheduleLoader = mock(ReportingScheduleLoader.class);
    reportingService = mock(ReportingService.class);
    generationRepository = mock(ReportingGenerationRepository.class);
    userService = mock(UserService.class);
    emailService = mock(EmailService.class);
    fileService = mock(FileService.class);
    openAEVConfig = mock(OpenAEVConfig.class);
    scheduleService =
        new ReportingScheduleService(
            scheduleLoader,
            reportingService,
            generationRepository,
            userService,
            emailService,
            fileService,
            openAEVConfig);
    when(openAEVConfig.getDefaultMailer()).thenReturn("noreply@filigran.io");
  }

  // -- FIXTURES --

  private User user(String id, String email) {
    User user = new User();
    user.setId(id);
    user.setEmail(email);
    return user;
  }

  private ReportingSchedule dailySchedule() {
    Reporting reporting = new Reporting();
    reporting.setId("reporting-id");
    reporting.setName("My report");
    reporting.setContextType(ReportingContextType.PLATFORM);

    ReportingSchedule schedule = new ReportingSchedule();
    schedule.setId("schedule-id");
    schedule.setReporting(reporting);
    schedule.setName("Daily report");
    schedule.setPeriod(ReportingSchedulePeriod.DAY);
    schedule.setTriggerTime("09:00");
    schedule.setFormat(ReportingFormat.PDF);
    schedule.setEnabled(true);
    schedule.setOwner(user("owner-id", "owner@filigran.io"));
    schedule.setTenant(new Tenant("tenant-a"));
    schedule.setRecipientUsers(new ArrayList<>(List.of(user("user-1", "alice@filigran.io"))));
    schedule.setRecipientEmails(new ArrayList<>(List.of("bob@filigran.io")));
    return schedule;
  }

  private ReportingGeneration pendingGeneration() {
    ReportingGeneration generation = new ReportingGeneration();
    generation.setId("generation-id");
    generation.setStatus(ReportingGenerationStatus.PENDING);
    generation.setFormat(ReportingFormat.PDF);
    return generation;
  }

  private ReportingGeneration successfulGeneration() {
    ReportingGeneration generation = pendingGeneration();
    generation.setStatus(ReportingGenerationStatus.SUCCESS);
    generation.setCompletedAt(DUE);
    Document document = new Document();
    document.setId("document-id");
    document.setName("report.pdf");
    document.setTarget("abc.pdf");
    document.setType("application/pdf");
    generation.setDocument(document);
    return generation;
  }

  private void stubTerminalGeneration(ReportingGeneration terminal) {
    when(generationRepository.findWithDocumentByIdAndTenantId("generation-id", "tenant-a"))
        .thenReturn(Optional.ofNullable(terminal));
  }

  @SuppressWarnings("unchecked")
  private List<ExecutionContext> capturedRecipients() throws Exception {
    ArgumentCaptor<List<ExecutionContext>> captor = ArgumentCaptor.forClass(List.class);
    verify(emailService)
        .sendEmail(
            any(),
            captor.capture(),
            anyString(),
            any(),
            anyList(),
            any(),
            anyString(),
            anyString(),
            anyList());
    return captor.getValue();
  }

  // -- TESTS --

  @Test
  @DisplayName("A due schedule generates under the owner identity and emails the recipients")
  void given_dueSchedule_should_generateUnderOwnerAndDeliverReport() throws Exception {
    // -- Arrange --
    ReportingSchedule schedule = dailySchedule();
    when(scheduleLoader.loadEnabledSchedules()).thenReturn(List.of(schedule));
    when(reportingService.requestGeneration(
            "reporting-id", ReportingFormat.PDF, ReportingGenerationTrigger.SCHEDULED))
        .thenReturn(pendingGeneration());
    stubTerminalGeneration(successfulGeneration());
    when(fileService.getFile(any(Document.class)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream("pdf-bytes".getBytes(StandardCharsets.UTF_8))));

    // -- Act --
    scheduleService.runDueSchedules(DUE);

    // -- Assert -- runs under the owner identity, marks the last run, requests a SCHEDULED
    // generation
    verify(userService).createUserSession(schedule.getOwner());
    verify(scheduleLoader).markLastRun("schedule-id", DUE_MINUTE);
    verify(reportingService)
        .requestGeneration(
            "reporting-id", ReportingFormat.PDF, ReportingGenerationTrigger.SCHEDULED);

    // -- Assert -- the produced document is emailed to both recipients as an attachment
    ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DataAttachment>> attachmentsCaptor = ArgumentCaptor.forClass(List.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ExecutionContext>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
    verify(emailService)
        .sendEmail(
            any(),
            recipientsCaptor.capture(),
            eq("noreply@filigran.io"),
            any(),
            anyList(),
            any(),
            subjectCaptor.capture(),
            anyString(),
            attachmentsCaptor.capture());
    assertThat(recipientsCaptor.getValue())
        .extracting(context -> context.getUser().getEmail())
        .containsExactly("alice@filigran.io", "bob@filigran.io");
    assertThat(subjectCaptor.getValue()).contains("Scheduled report: My report");
    assertThat(attachmentsCaptor.getValue()).hasSize(1);
  }

  @Test
  @DisplayName("A failed generation sends a failure notice to the owner only")
  void given_failedGeneration_should_notifyOwnerOnly() throws Exception {
    // -- Arrange --
    ReportingSchedule schedule = dailySchedule();
    when(scheduleLoader.loadEnabledSchedules()).thenReturn(List.of(schedule));
    when(reportingService.requestGeneration(
            "reporting-id", ReportingFormat.PDF, ReportingGenerationTrigger.SCHEDULED))
        .thenReturn(pendingGeneration());
    ReportingGeneration failed = pendingGeneration();
    failed.setStatus(ReportingGenerationStatus.ERROR);
    failed.setErrorMessage("Chromium crashed");
    stubTerminalGeneration(failed);

    // -- Act --
    scheduleService.runDueSchedules(DUE);

    // -- Assert -- no report file is touched, the owner alone gets the failure notice
    verify(fileService, never()).getFile(any(Document.class));
    ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ExecutionContext>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
    verify(emailService)
        .sendEmail(
            any(),
            recipientsCaptor.capture(),
            anyString(),
            any(),
            anyList(),
            any(),
            subjectCaptor.capture(),
            bodyCaptor.capture(),
            anyList());
    assertThat(recipientsCaptor.getValue())
        .extracting(context -> context.getUser().getEmail())
        .containsExactly("owner@filigran.io");
    assertThat(subjectCaptor.getValue()).contains("Scheduled report generation failed");
    assertThat(bodyCaptor.getValue()).contains("Chromium crashed");
  }

  @Test
  @DisplayName("A vanished generation sends a failure notice to the owner only")
  void given_vanishedGeneration_should_notifyOwnerOnly() throws Exception {
    // -- Arrange --
    ReportingSchedule schedule = dailySchedule();
    when(scheduleLoader.loadEnabledSchedules()).thenReturn(List.of(schedule));
    when(reportingService.requestGeneration(
            "reporting-id", ReportingFormat.PDF, ReportingGenerationTrigger.SCHEDULED))
        .thenReturn(pendingGeneration());
    stubTerminalGeneration(null);

    // -- Act --
    scheduleService.runDueSchedules(DUE);

    // -- Assert --
    List<ExecutionContext> recipients = capturedRecipients();
    assertThat(recipients)
        .extracting(context -> context.getUser().getEmail())
        .containsExactly("owner@filigran.io");
  }

  @Test
  @DisplayName("A recipient user sharing its email with a raw address is counted once")
  void given_duplicateRecipientEmail_should_deliverOnce() throws Exception {
    // -- Arrange -- user email and raw email differ only by case
    ReportingSchedule schedule = dailySchedule();
    schedule.setRecipientUsers(new ArrayList<>(List.of(user("user-1", "Shared@Filigran.io"))));
    schedule.setRecipientEmails(
        new ArrayList<>(List.of("shared@filigran.io", "second@filigran.io")));
    when(scheduleLoader.loadEnabledSchedules()).thenReturn(List.of(schedule));
    when(reportingService.requestGeneration(
            "reporting-id", ReportingFormat.PDF, ReportingGenerationTrigger.SCHEDULED))
        .thenReturn(pendingGeneration());
    stubTerminalGeneration(successfulGeneration());
    when(fileService.getFile(any(Document.class)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream("pdf-bytes".getBytes(StandardCharsets.UTF_8))));

    // -- Act --
    scheduleService.runDueSchedules(DUE);

    // -- Assert -- the platform user wins, the duplicate raw address is dropped
    // (User.setEmail normalizes to lowercase, hence the lowercase expectation)
    List<ExecutionContext> recipients = capturedRecipients();
    assertThat(recipients)
        .extracting(context -> context.getUser().getEmail())
        .containsExactly("shared@filigran.io", "second@filigran.io");
  }

  @Test
  @DisplayName("A schedule that already ran within the current period window is skipped")
  void given_alreadyRanSchedule_should_beSkipped() throws Exception {
    // -- Arrange -- lastRunAt within the last day for a DAY schedule
    ReportingSchedule schedule = dailySchedule();
    schedule.setLastRunAt(DUE.minus(1, ChronoUnit.HOURS));
    when(scheduleLoader.loadEnabledSchedules()).thenReturn(List.of(schedule));

    // -- Act --
    scheduleService.runDueSchedules(DUE);

    // -- Assert --
    verify(reportingService, never()).requestGeneration(anyString(), any(), any());
    verify(scheduleLoader, never()).markLastRun(anyString(), any());
    verify(emailService, never())
        .sendEmail(
            any(),
            anyList(),
            anyString(),
            any(),
            anyList(),
            any(),
            anyString(),
            anyString(),
            anyList());
  }

  @Test
  @DisplayName("A schedule ran one full period ago fires again")
  void given_scheduleRanOnePeriodAgo_should_fireAgain() throws Exception {
    // -- Arrange -- lastRunAt exactly one day before the due minute (window boundary)
    ReportingSchedule schedule = dailySchedule();
    schedule.setLastRunAt(DUE_MINUTE.minus(1, ChronoUnit.DAYS));
    when(scheduleLoader.loadEnabledSchedules()).thenReturn(List.of(schedule));
    when(reportingService.requestGeneration(
            "reporting-id", ReportingFormat.PDF, ReportingGenerationTrigger.SCHEDULED))
        .thenReturn(pendingGeneration());
    stubTerminalGeneration(successfulGeneration());
    when(fileService.getFile(any(Document.class)))
        .thenReturn(
            Optional.of(new ByteArrayInputStream("pdf-bytes".getBytes(StandardCharsets.UTF_8))));

    // -- Act --
    scheduleService.runDueSchedules(DUE);

    // -- Assert --
    verify(reportingService)
        .requestGeneration(
            "reporting-id", ReportingFormat.PDF, ReportingGenerationTrigger.SCHEDULED);
  }

  @Test
  @DisplayName("A schedule that is not due does nothing")
  void given_notDueSchedule_should_doNothing() throws Exception {
    // -- Arrange --
    ReportingSchedule schedule = dailySchedule();
    when(scheduleLoader.loadEnabledSchedules()).thenReturn(List.of(schedule));

    // -- Act -- 09:30 does not match the 09:00 daily trigger
    scheduleService.runDueSchedules(Instant.parse("2026-07-20T09:30:00Z"));

    // -- Assert --
    verify(reportingService, never()).requestGeneration(anyString(), any(), any());
    verify(userService, never()).createUserSession(any());
  }
}
