package io.openaev.injectors.phishing;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.database.model.ExecutionTrace.getNewInfoTrace;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.CustomDomain;
import io.openaev.database.model.CustomDomain.CustomDomainStatus;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.Team;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.injector_contract.variables.contract.UserContract;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.injectors.phishing.api.HostedPublicApi;
import io.openaev.injectors.phishing.model.PhishingContent;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.model.ExecutionProcess;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    // Benign, tenant-less origin: the linked verified custom domain if any, else the platform URL.
    String origin = resolveOrigin(landingPage, baseUrl);

    String subject = StringUtils.firstNonBlank(content.getSubject(), emailTemplate.getSubject());
    String from = resolveFrom(content, emailTemplate, exercise);
    String fromName = resolveFromName(content, emailTemplate, exercise);
    List<String> replyTos =
        exercise != null
            ? exercise.getReplyTos()
            : List.of(this.context.getOpenAEVConfig().getDefaultReplyTo());

    // Persist the expectations before any trackable link is published: createResult commits each
    // per-recipient tracking token before its email is sent, so an early recipient can open/click
    // while the loop is still sending. If the expectations did not exist yet, that open/click would
    // find nothing to fulfill and would never be retried.
    injectExpectationService.computeAndSaveExpectations(injection, content.getExpectations(), null);

    // The execution context carries each recipient's team NAME (see InjectHelper), but
    // phishing_result_team is an FK to teams.team_id. Map the name back to the real id from the
    // inject's target teams; otherwise createResult inserts the name ("CEO") as the team id and
    // fails the phishing_results_team_fk constraint. Duplicate names keep the first (any is right).
    Map<String, String> teamIdByName =
        injection.getTeams().stream()
            .collect(Collectors.toMap(Team::getName, Team::getId, (first, ignored) -> first));

    for (ExecutionContext userContext : users) {
      try {
        UserContract targetUser = userContext.getUser();
        String teamName =
            userContext.getTeams() != null && !userContext.getTeams().isEmpty()
                ? userContext.getTeams().getFirst()
                : null;
        String teamId = teamName != null ? teamIdByName.get(teamName) : null;
        PhishingResult result =
            phishingTrackingService.createResult(
                inject, landingPage, targetUser.getId(), teamId, injection.getStepId());
        // Victim-facing landing URL: e.g. https://security.acme.com/auth/<token> - benign path, no
        // tenant id, resolved back to its tenant from the globally-unique token server-side.
        String landingUrl =
            origin + "/" + HostedPublicApi.LANDING_PATH_PREFIX + "/" + result.getToken();
        String pixelUrl = origin + HostedPublicApi.HOSTED_URI + "/o/" + result.getToken();
        String message = renderBody(emailTemplate, landingUrl, pixelUrl);
        // Lure content is operator-authored and must not be FreeMarker-evaluated (SSTI): the
        // per-recipient link is already substituted via the literal {{phishing_url}} placeholder.
        emailService.sendPreRenderedEmail(
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
   * Resolves the origin (scheme + host) the victim-facing links are built on: a verified custom
   * domain linked to the landing page (served over HTTPS), otherwise the platform base URL.
   */
  private String resolveOrigin(PhishingLandingPage landingPage, String baseUrl) {
    CustomDomain domain = landingPage.getCustomDomain();
    if (domain != null
        && domain.getStatus() == CustomDomainStatus.VERIFIED
        && StringUtils.isNotBlank(domain.getHostname())) {
      return "https://" + domain.getHostname();
    }
    return baseUrl;
  }

  /**
   * Builds the per-recipient HTML body: substitutes the landing URL placeholder (appending a link
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
