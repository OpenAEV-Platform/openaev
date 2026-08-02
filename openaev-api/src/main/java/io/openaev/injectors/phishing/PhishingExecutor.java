package io.openaev.injectors.phishing;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.database.model.ExecutionTrace.getNewInfoTrace;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ProtectUser;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.expectation.Expectation;
import io.openaev.expectation.ManualExpectation;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.injectors.phishing.model.PhishingContent;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.model.ExecutionProcess;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * Internal phishing injector. The landing page is resolved from the inject's {@link
 * InjectorContract} id (each landing page synthesizes a contract whose id equals the landing page
 * id), the lure email template from the inject content. For each recipient a {@link PhishingResult}
 * tracking row is created and the lure email - carrying a per-recipient tracking pixel and click
 * link built on the public tracking endpoints - is sent through the platform's global SMTP.
 */
public class PhishingExecutor extends Injector {

  /** Placeholder replaced in the email body with the per-recipient click URL. */
  private static final String URL_PLACEHOLDER = "{{phishing_url}}";

  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;
  private final PhishingTrackingService phishingTrackingService;
  private final PhishingLandingPageRepository landingPageRepository;
  private final PhishingEmailTemplateRepository emailTemplateRepository;

  public PhishingExecutor(
      InjectorContext injectorContext,
      EmailService emailService,
      InjectExpectationService injectExpectationService,
      PhishingTrackingService phishingTrackingService,
      PhishingLandingPageRepository landingPageRepository,
      PhishingEmailTemplateRepository emailTemplateRepository) {
    super(injectorContext);
    this.emailService = emailService;
    this.injectExpectationService = injectExpectationService;
    this.phishingTrackingService = phishingTrackingService;
    this.landingPageRepository = landingPageRepository;
    this.emailTemplateRepository = emailTemplateRepository;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection)
      throws Exception {
    Inject inject = injection.getInjection().getInject();
    PhishingContent content =
        injectExpectationService.contentConvert(injection, PhishingContent.class);

    String contractId =
        inject
            .getInjectorContract()
            .map(InjectorContract::getId)
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));
    PhishingLandingPage landingPage =
        landingPageRepository
            .findById(contractId)
            .orElseThrow(
                () -> new UnsupportedOperationException("Phishing landing page not found"));

    if (StringUtils.isBlank(content.getEmailTemplate())) {
      throw new UnsupportedOperationException("Phishing inject requires an email template");
    }
    PhishingEmailTemplate emailTemplate =
        emailTemplateRepository
            .findById(content.getEmailTemplate())
            .orElseThrow(
                () -> new UnsupportedOperationException("Phishing email template not found"));

    List<ExecutionContext> users = injection.getUsers();
    if (users.isEmpty()) {
      throw new UnsupportedOperationException("Phishing needs at least one target user");
    }

    Exercise exercise = injection.getInjection().getExercise();
    String baseUrl = this.context.getOpenAEVConfig().getBaseUrl();
    String tenantId =
        inject.getTenant() != null ? inject.getTenant().getId() : TenantContext.getCurrentTenant();

    String subject = StringUtils.firstNonBlank(content.getSubject(), emailTemplate.getSubject());
    String from = resolveFrom(content, emailTemplate, exercise);
    String fromName = resolveFromName(content, emailTemplate, exercise);
    List<String> replyTos =
        exercise != null
            ? exercise.getReplyTos()
            : List.of(this.context.getOpenAEVConfig().getDefaultReplyTo());

    for (ExecutionContext userContext : users) {
      try {
        ProtectUser targetUser = userContext.getUser();
        String teamId =
            userContext.getTeams() != null && !userContext.getTeams().isEmpty()
                ? userContext.getTeams().getFirst()
                : null;
        PhishingResult result =
            phishingTrackingService.createResult(inject, landingPage, targetUser.getId(), teamId);
        String clickUrl =
            baseUrl + "/api/phishing/tracking/" + tenantId + "/c/" + result.getToken();
        String pixelUrl =
            baseUrl + "/api/phishing/tracking/" + tenantId + "/o/" + result.getToken();
        String message = renderBody(emailTemplate, clickUrl, pixelUrl);
        emailService.sendEmail(
            execution,
            List.of(userContext),
            from,
            fromName,
            replyTos,
            null,
            subject,
            message,
            List.of());
      } catch (Exception e) {
        execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
      }
    }
    execution.addTrace(
        getNewInfoTrace(
            "Phishing emails sent to " + users.size() + " target(s)",
            ExecutionTraceAction.EXECUTION));

    List<Expectation> expectations =
        content.getExpectations().stream()
            .flatMap(
                entry ->
                    switch (entry.getType()) {
                      case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                      default -> Stream.of();
                    })
            .toList();
    injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);

    return new ExecutionProcess(false);
  }

  private String resolveFrom(
      PhishingContent content, PhishingEmailTemplate emailTemplate, Exercise exercise) {
    return StringUtils.firstNonBlank(
        content.getFromEmail(),
        emailTemplate.getFromEmail(),
        exercise != null ? exercise.getFrom() : null,
        this.context.getOpenAEVConfig().getDefaultMailer());
  }

  private String resolveFromName(
      PhishingContent content, PhishingEmailTemplate emailTemplate, Exercise exercise) {
    return StringUtils.firstNonBlank(
        content.getFromName(),
        emailTemplate.getFromName(),
        exercise != null ? exercise.getFromName() : null,
        this.context.getOpenAEVConfig().getDefaultMailerName());
  }

  /**
   * Builds the per-recipient HTML body: substitutes the click URL placeholder (appending a link
   * when the template carries none) and appends the tracking pixel when enabled.
   */
  private String renderBody(PhishingEmailTemplate emailTemplate, String clickUrl, String pixelUrl) {
    String body = emailTemplate.getHtmlBody() != null ? emailTemplate.getHtmlBody() : "";
    if (body.contains(URL_PLACEHOLDER)) {
      body = body.replace(URL_PLACEHOLDER, clickUrl);
    } else {
      body = body + "<p><a href=\"" + clickUrl + "\">" + clickUrl + "</a></p>";
    }
    if (emailTemplate.isAddTrackingPixel()) {
      body =
          body
              + "<img src=\""
              + pixelUrl
              + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\"/>";
    }
    return body;
  }
}
