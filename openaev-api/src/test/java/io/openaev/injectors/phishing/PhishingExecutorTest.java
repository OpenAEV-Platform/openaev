package io.openaev.injectors.phishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Execution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injection;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.PhishingEmailTemplate;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.injectors.phishing.model.PhishingContent;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.service.InjectExpectationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Phishing executor tests")
class PhishingExecutorTest {

  @Test
  @DisplayName("createResult receives the resolved team id, not the team name (phishing_results_team_fk)")
  void process_should_resolveTeamNameToTeamId() throws Exception {
    // Collaborators
    InjectorContext context = mock(InjectorContext.class);
    EmailService emailService = mock(EmailService.class);
    InjectExpectationService injectExpectationService = mock(InjectExpectationService.class);
    PhishingTrackingService phishingTrackingService = mock(PhishingTrackingService.class);
    PhishingLandingPageRepository landingPageRepository = mock(PhishingLandingPageRepository.class);
    PhishingEmailTemplateRepository emailTemplateRepository =
        mock(PhishingEmailTemplateRepository.class);

    OpenAEVConfig config = mock(OpenAEVConfig.class);
    when(context.getOpenAEVConfig()).thenReturn(config);
    when(config.getBaseUrl()).thenReturn("https://app.test");
    when(config.getDefaultReplyTo()).thenReturn("noreply@app.test");
    when(config.getDefaultMailer()).thenReturn("mailer@app.test");
    when(config.getDefaultMailerName()).thenReturn("Mailer");

    PhishingExecutor executor =
        new PhishingExecutor(
            context,
            emailService,
            injectExpectationService,
            phishingTrackingService,
            landingPageRepository,
            emailTemplateRepository);

    // The target team: the execution context carries its NAME, the FK needs its ID.
    Team team = new Team();
    team.setId("team-ceo-id");
    team.setName("CEO");

    User user = new User();
    user.setId("user-1");
    user.setEmail("ceo@corp.test");
    ExecutionContext userContext = new ExecutionContext(user, List.of("CEO"));

    InjectorContract contract = new InjectorContract();
    contract.setId("lp-1");
    Inject inject = new Inject();
    inject.setId("inject-1");
    inject.setInjectorContract(contract);

    PhishingLandingPage landingPage = new PhishingLandingPage();
    when(landingPageRepository.findById("lp-1")).thenReturn(Optional.of(landingPage));

    PhishingEmailTemplate emailTemplate = new PhishingEmailTemplate();
    emailTemplate.setSubject("Subject");
    when(emailTemplateRepository.findById("template-1")).thenReturn(Optional.of(emailTemplate));

    PhishingContent content = new PhishingContent();
    content.setEmailTemplate("template-1");

    ExecutableInject injection = mock(ExecutableInject.class);
    Injection injectionEntity = mock(Injection.class);
    when(injection.getInjection()).thenReturn(injectionEntity);
    when(injectionEntity.getInject()).thenReturn(inject);
    when(injectionEntity.getExercise()).thenReturn(null);
    when(injection.getUsers()).thenReturn(List.of(userContext));
    when(injection.getTeams()).thenReturn(List.of(team));
    when(injectExpectationService.contentConvert(injection, PhishingContent.class))
        .thenReturn(content);

    PhishingResult result = new PhishingResult();
    result.setToken("token-1");
    when(phishingTrackingService.createResult(any(), any(), any(), any())).thenReturn(result);

    Execution execution = mock(Execution.class);

    executor.process(execution, injection);

    ArgumentCaptor<String> teamCaptor = ArgumentCaptor.forClass(String.class);
    verify(phishingTrackingService)
        .createResult(eq(inject), eq(landingPage), eq("user-1"), teamCaptor.capture());
    // The bug wrote the team NAME ("CEO") into phishing_result_team (an FK to teams.team_id),
    // failing phishing_results_team_fk. The fix resolves it to the real team id.
    assertEquals("team-ceo-id", teamCaptor.getValue());
  }
}
