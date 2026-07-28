package io.openaev.rest.reporting.service;

import static org.springframework.util.StringUtils.hasText;

import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.DataAttachment;
import io.openaev.database.model.Document;
import io.openaev.database.model.Execution;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.ReportingGenerationTrigger;
import io.openaev.database.model.ReportingSchedule;
import io.openaev.database.model.User;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.execution.ExecutionContext;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.rest.reporting.ReportingService;
import io.openaev.service.FileService;
import io.openaev.service.UserService;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Scheduling engine of the reporting module, evaluated every minute by {@code
 * ReportingScheduleJob}. Schedules due at the current minute (same firing semantics as the
 * notification digest engine, see {@link ReportingScheduleTimeUtils}) trigger a generation under
 * their owner's identity and tenant, wait for the renderer to complete, and deliver the produced
 * document by email to the schedule recipients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingScheduleService {

  /** Delay between two polls of a generation waiting for a terminal status. */
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

  /** Bounded wait for the renderer: past this point the generation is treated as failed. */
  private static final Duration POLL_TIMEOUT = Duration.ofMinutes(5);

  private static final DateTimeFormatter GENERATION_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  private final ReportingScheduleLoader reportingScheduleLoader;
  private final ReportingService reportingService;
  private final ReportingGenerationRepository reportingGenerationRepository;
  private final UserService userService;
  private final EmailService emailService;
  private final FileService fileService;
  private final OpenAEVConfig openAEVConfig;

  /**
   * Fires every enabled schedule due at the current minute. Each schedule is guarded against
   * double-firing through its {@code lastRunAt} marker: a schedule that already ran within the
   * current period window is skipped.
   *
   * @param now the evaluation instant (the minute tick)
   */
  public void runDueSchedules(Instant now) {
    Instant dueMinute = now.truncatedTo(ChronoUnit.MINUTES);
    List<ReportingSchedule> schedules = reportingScheduleLoader.loadEnabledSchedules();
    for (ReportingSchedule schedule : schedules) {
      try {
        if (!ReportingScheduleTimeUtils.isDue(
            schedule.getPeriod(), schedule.getTriggerTime(), now)) {
          continue;
        }
        if (alreadyRanThisPeriod(schedule, dueMinute)) {
          continue;
        }
        executeSchedule(schedule, dueMinute);
      } catch (Exception e) {
        log.error("Reporting schedule {} processing failed", schedule.getId(), e);
      }
    }
  }

  /**
   * Double-fire guard: lastRunAt is always recorded as the due minute, and two due minutes of the
   * same schedule are at least one period apart, so a lastRunAt strictly after the window start
   * means this occurrence (or a later one) already fired.
   */
  private boolean alreadyRanThisPeriod(ReportingSchedule schedule, Instant dueMinute) {
    Instant lastRunAt = schedule.getLastRunAt();
    if (lastRunAt == null) {
      return false;
    }
    Instant windowStart = ReportingScheduleTimeUtils.windowStart(schedule.getPeriod(), dueMinute);
    return lastRunAt.isAfter(windowStart);
  }

  /**
   * Runs one due schedule: under the owner's identity and the schedule's tenant, requests a
   * SCHEDULED generation, waits (bounded) for the renderer to reach a terminal status, then either
   * emails the produced document to the recipients or notifies the owner of the failure.
   */
  private void executeSchedule(ReportingSchedule schedule, Instant dueMinute) {
    String tenantId = schedule.getTenant().getId();
    try {
      // Generations run under the owner's identity so RBAC and tenant checks apply as if the
      // owner had requested the report manually.
      TenantContext.setCurrentTenant(tenantId);
      userService.createUserSession(schedule.getOwner());
      reportingScheduleLoader.markLastRun(schedule.getId(), dueMinute);
      ReportingGeneration generation =
          reportingService.requestGeneration(
              schedule.getReporting().getId(),
              schedule.getFormat(),
              ReportingGenerationTrigger.SCHEDULED);
      ReportingGeneration terminal = awaitTerminalStatus(generation.getId(), tenantId);
      if (terminal != null && ReportingGenerationStatus.SUCCESS.equals(terminal.getStatus())) {
        deliverReport(schedule, terminal);
      } else {
        String error = describeFailure(terminal);
        log.warn(
            "Reporting schedule {} generation failed, no report delivered: {}",
            schedule.getId(),
            error);
        notifyOwnerOfFailure(schedule, error);
      }
    } catch (Exception e) {
      log.error("Reporting schedule {} execution failed", schedule.getId(), e);
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }
  }

  /**
   * Bounded poll of a generation until it reaches a terminal status (the renderer is asynchronous
   * by contract): re-reads the row every {@link #POLL_INTERVAL} for up to {@link #POLL_TIMEOUT}.
   * Returns the last observed state, which may still be non-terminal on timeout, or null when the
   * row disappeared.
   */
  private ReportingGeneration awaitTerminalStatus(String generationId, String tenantId) {
    Instant deadline = Instant.now().plus(POLL_TIMEOUT);
    while (true) {
      ReportingGeneration generation =
          reportingGenerationRepository
              .findWithDocumentByIdAndTenantId(generationId, tenantId)
              .orElse(null);
      if (generation == null || isTerminal(generation.getStatus())) {
        return generation;
      }
      if (Instant.now().isAfter(deadline)) {
        return generation;
      }
      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return generation;
      }
    }
  }

  private boolean isTerminal(ReportingGenerationStatus status) {
    return ReportingGenerationStatus.SUCCESS.equals(status)
        || ReportingGenerationStatus.ERROR.equals(status);
  }

  private String describeFailure(ReportingGeneration terminal) {
    if (terminal == null) {
      return "Generation no longer exists";
    }
    if (ReportingGenerationStatus.ERROR.equals(terminal.getStatus())) {
      return hasText(terminal.getErrorMessage()) ? terminal.getErrorMessage() : "Rendering failed";
    }
    return "Generation still " + terminal.getStatus() + " after " + POLL_TIMEOUT.toMinutes() + "m";
  }

  // -- DELIVERY --

  /** Emails the produced document to the schedule recipients (users + raw emails, deduplicated). */
  private void deliverReport(ReportingSchedule schedule, ReportingGeneration generation)
      throws Exception {
    Reporting reporting = schedule.getReporting();
    Document document = generation.getDocument();
    if (document == null) {
      log.warn(
          "Reporting schedule {} generation {} succeeded without a document",
          schedule.getId(),
          generation.getId());
      notifyOwnerOfFailure(schedule, "Generation succeeded but produced no document");
      return;
    }
    List<ExecutionContext> recipients = buildRecipients(schedule);
    if (recipients.isEmpty()) {
      log.warn("Reporting schedule {} has no recipients, skipping delivery", schedule.getId());
      return;
    }
    byte[] fileBytes;
    try (InputStream stream =
        fileService
            .getFile(document)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Report file not found in storage: " + document.getTarget()))) {
      fileBytes = stream.readAllBytes();
    }
    DataAttachment attachment =
        new DataAttachment(document.getId(), document.getName(), fileBytes, document.getType());
    String subject = "[" + platformName() + "] Scheduled report: " + reporting.getName();
    sendEmail(recipients, subject, buildReportBody(reporting, generation), List.of(attachment));
    log.info(
        "Reporting schedule {} delivered generation {} to {} recipient(s)",
        schedule.getId(),
        generation.getId(),
        recipients.size());
  }

  /** Short failure notice sent to the schedule owner only (never to the recipients). */
  private void notifyOwnerOfFailure(ReportingSchedule schedule, String error) {
    User owner = schedule.getOwner();
    if (owner == null || !hasText(owner.getEmail())) {
      return;
    }
    try {
      String subject =
          "["
              + platformName()
              + "] Scheduled report generation failed: "
              + schedule.getReporting().getName();
      sendEmail(
          List.of(new ExecutionContext(owner, List.of())),
          subject,
          buildFailureBody(schedule.getReporting(), error),
          List.of());
    } catch (Exception e) {
      log.warn("Could not notify owner of reporting schedule {} failure", schedule.getId(), e);
    }
  }

  /**
   * Resolves the recipients of a schedule: platform users first, then raw external addresses, with
   * duplicates removed case-insensitively on the email.
   */
  private List<ExecutionContext> buildRecipients(ReportingSchedule schedule) {
    Map<String, ExecutionContext> byEmail = new LinkedHashMap<>();
    for (User user : schedule.getRecipientUsers()) {
      if (user != null && hasText(user.getEmail())) {
        byEmail.putIfAbsent(
            user.getEmail().toLowerCase(Locale.ROOT), new ExecutionContext(user, List.of()));
      }
    }
    for (String email : schedule.getRecipientEmails()) {
      if (hasText(email)) {
        // Raw external addresses have no platform account: wrap them in a transient user so
        // the email service can address them like any other recipient.
        User external = new User();
        external.setEmail(email);
        byEmail.putIfAbsent(
            email.toLowerCase(Locale.ROOT), new ExecutionContext(external, List.of()));
      }
    }
    return new ArrayList<>(byEmail.values());
  }

  private void sendEmail(
      List<ExecutionContext> recipients,
      String subject,
      String body,
      List<DataAttachment> attachments)
      throws Exception {
    emailService.sendEmail(
        new Execution(false),
        recipients,
        openAEVConfig.getDefaultMailer(),
        openAEVConfig.getDefaultMailerName(),
        resolveReplyTos(),
        null,
        subject,
        body,
        attachments);
  }

  private List<String> resolveReplyTos() {
    String replyTo =
        hasText(openAEVConfig.getDefaultReplyTo())
            ? openAEVConfig.getDefaultReplyTo()
            : openAEVConfig.getDefaultMailer();
    return replyTo != null ? List.of(replyTo) : List.of();
  }

  // -- EMAIL BODIES --

  private String buildReportBody(Reporting reporting, ReportingGeneration generation) {
    Instant generatedAt =
        generation.getCompletedAt() != null ? generation.getCompletedAt() : Instant.now();
    return emailShell(
        escapeHtml(reporting.getName()),
        "<p style=\"margin: 0 0 8px\">Your scheduled report <strong>"
            + escapeHtml(reporting.getName())
            + "</strong> ("
            + escapeHtml(describeContext(reporting))
            + ") was generated on "
            + GENERATION_DATE_FORMAT.format(generatedAt)
            + ".</p>"
            + "<p style=\"margin: 0\">The report is attached to this email.</p>");
  }

  private String buildFailureBody(Reporting reporting, String error) {
    return emailShell(
        "Report generation failed",
        "<p style=\"margin: 0 0 8px\">The scheduled generation of report <strong>"
            + escapeHtml(reporting.getName())
            + "</strong> ("
            + escapeHtml(describeContext(reporting))
            + ") failed.</p>"
            + "<p style=\"margin: 0\">Reason: "
            + escapeHtml(error)
            + "</p>");
  }

  /** Minimal branded HTML shell, modeled on the platform notification email template. */
  private String emailShell(String title, String contentHtml) {
    String platformName = escapeHtml(platformName());
    String baseUrl = openAEVConfig.getBaseUrl();
    String linkHtml =
        hasText(baseUrl)
            ? "<div style=\"padding-top: 24px; text-align: center\">"
                + "<a href=\""
                + escapeHtml(baseUrl)
                + "\" style=\"display: inline-block; border-radius: 4px; padding: 12px 20px;"
                + " font-size: 14px; font-weight: 600; text-decoration: none; color: #F4F4F6;"
                + " background-color: #001BDB\">Access platform</a></div>"
            : "";
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"></head>"
        + "<body style=\"margin: 0; background-color: #F1F5F9; padding: 24px;"
        + " font-family: ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif\">"
        + "<div style=\"max-width: 552px; margin: 0 auto\">"
        + "<h2 style=\"margin: 0 0 16px; text-align: center; font-size: 20px; color: #00020c\">"
        + platformName
        + "</h2>"
        + "<div style=\"border-radius: 4px; background-color: #ffffff; padding: 32px;"
        + " font-size: 14px; color: #00020c\">"
        + "<h3 style=\"margin: 0 0 16px; font-size: 18px\">"
        + title
        + "</h3>"
        + contentHtml
        + linkHtml
        + "</div>"
        + "<p style=\"padding: 16px; text-align: center; font-size: 12px; color: #00020c\">"
        + platformName
        + " - Open Adversarial Exposure Validation Platform</p>"
        + "</div></body></html>";
  }

  private String describeContext(Reporting reporting) {
    String contextType =
        reporting.getContextType() != null ? reporting.getContextType().name() : "PLATFORM";
    String label = contextType.toLowerCase(Locale.ROOT).replace('_', ' ');
    return hasText(reporting.getContextId()) ? label + " " + reporting.getContextId() : label;
  }

  private String platformName() {
    return hasText(openAEVConfig.getName()) ? openAEVConfig.getName() : "OpenAEV";
  }

  private String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
