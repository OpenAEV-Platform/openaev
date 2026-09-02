package io.openaev.injectors.phishing.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.database.repository.PhishingResultRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end coverage of the chaining FK fix: a {@link PhishingResult} created for a chaining
 * execution must reference the already-persisted {@link Step} instead of the not-yet-committed
 * {@link Inject}, then get its {@code inject} backfilled once a tracking hit resolves the token -
 * see {@link PhishingTrackingService#createResult} and {@link
 * PhishingTrackingService#resolveByToken}.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@code createResult} runs in its own {@code
 * REQUIRES_NEW} transaction (load-bearing, see its Javadoc), so every entity it references must be
 * genuinely committed - a test-managed rollback transaction would hide exactly the bug being fixed.
 * Fixtures are therefore persisted for real and torn down explicitly in {@code @AfterEach}.
 */
@SpringBootTest
class PhishingTrackingServiceIntegrationTest extends IntegrationTest {

  @Autowired private PhishingTrackingService phishingTrackingService;
  @Autowired private PhishingResultRepository phishingResultRepository;
  @Autowired private PhishingLandingPageRepository phishingLandingPageRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  private Step persistStep(String data) {
    Step step =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.RUN)
            .data(data)
            .build();
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();
    return step;
  }

  private PhishingLandingPage persistLandingPage() {
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setName("Test landing page");
    return phishingLandingPageRepository.save(landingPage);
  }

  private User persistUser() {
    return userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
  }

  private Inject persistInject() {
    return injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
  }

  /**
   * Tears down a fixture graph in an order that tolerates any entity already being gone (deleted by
   * the test itself, or cascade-removed by an earlier delete in this same cleanup): deleting the
   * user or the inject cascades any surviving {@code phishing_results} row, so the row is never
   * left dangling regardless of which delete runs first.
   */
  private void cleanup(User user, Inject inject, PhishingLandingPage landingPage, Step step) {
    if (user != null && userRepository.existsById(user.getId())) {
      userRepository.deleteById(user.getId());
    }
    if (inject != null && injectRepository.existsById(inject.getId())) {
      injectRepository.deleteById(inject.getId());
    }
    if (step != null) {
      String workflowId = step.getWorkflow().getId();
      String exerciseId = step.getWorkflow().getSimulation().getId();
      if (workflowRepository.existsById(workflowId)) {
        workflowRepository.deleteById(workflowId);
      }
      if (exerciseRepository.existsById(exerciseId)) {
        exerciseRepository.deleteById(exerciseId);
      }
    }
    if (landingPage != null && phishingLandingPageRepository.existsById(landingPage.getId())) {
      phishingLandingPageRepository.deleteById(landingPage.getId());
    }
  }

  @Nested
  @DisplayName("createResult")
  class CreateResult {

    private User user;
    private Inject inject;
    private PhishingLandingPage landingPage;
    private Step step;

    @AfterEach
    void tearDown() {
      cleanup(user, inject, landingPage, step);
    }

    @Test
    @DisplayName(
        "Given a stepId (chaining execution), the result references the step and no FK violation"
            + " occurs even though the inject does not exist yet")
    void given_stepId_should_referenceStepWithoutInjectAndNoFkViolation() {
      step = persistStep("{}");
      landingPage = persistLandingPage();
      user = persistUser();
      // Simulates the exact bug scenario: the inject is NOT persisted at all - only its id is
      // known - reproducing the not-yet-committed inject the ambient chaining transaction holds.
      Inject uncommittedInject = InjectFixture.getDefaultInject();
      uncommittedInject.setId(UUID.randomUUID().toString());

      PhishingResult result =
          Assertions.assertDoesNotThrow(
              () ->
                  phishingTrackingService.createResult(
                      uncommittedInject, landingPage, user.getId(), null, step.getId()));

      PhishingResult saved = phishingResultRepository.findById(result.getId()).orElseThrow();
      assertThat(saved.getStep()).isNotNull();
      assertThat(saved.getStep().getId()).isEqualTo(step.getId());
      assertThat(saved.getInject()).isNull();
    }

    @Test
    @DisplayName(
        "Given no stepId (time-based execution), the result references the inject directly")
    void given_noStepId_should_referenceInjectDirectly() {
      inject = persistInject();
      landingPage = persistLandingPage();
      user = persistUser();

      PhishingResult result =
          phishingTrackingService.createResult(inject, landingPage, user.getId(), null, null);

      PhishingResult saved = phishingResultRepository.findById(result.getId()).orElseThrow();
      assertThat(saved.getInject()).isNotNull();
      assertThat(saved.getInject().getId()).isEqualTo(inject.getId());
      assertThat(saved.getStep()).isNull();
    }
  }

  @Nested
  @DisplayName("resolveByToken backfill")
  class ResolveByTokenBackfill {

    private User user;
    private Inject inject;
    private PhishingLandingPage landingPage;
    private Step step;

    @AfterEach
    void tearDown() {
      cleanup(user, inject, landingPage, step);
    }

    @Test
    @DisplayName(
        "Given a step whose data now carries the committed inject id, resolving the token"
            + " backfills the inject")
    void given_stepDataCarriesInjectId_should_backfillInject() {
      inject = persistInject();
      step = persistStep("{\"inject_id\": \"" + inject.getId() + "\"}");
      landingPage = persistLandingPage();
      user = persistUser();
      PhishingResult created =
          phishingTrackingService.createResult(
              InjectFixture.getDefaultInject(), landingPage, user.getId(), null, step.getId());

      var resolved = phishingTrackingService.resolveByToken(created.getToken());

      assertThat(resolved).isPresent();
      assertThat(resolved.get().getInject()).isNotNull();
      assertThat(resolved.get().getInject().getId()).isEqualTo(inject.getId());
      PhishingResult saved = phishingResultRepository.findById(created.getId()).orElseThrow();
      assertThat(saved.getInject()).isNotNull();
      assertThat(saved.getInject().getId()).isEqualTo(inject.getId());
    }
  }

  @Nested
  @DisplayName("open / click / submit tracking events")
  class TrackingEvents {

    private User user;
    private Inject inject;
    private PhishingLandingPage landingPage;
    private Step step;

    @AfterEach
    void tearDown() {
      cleanup(user, inject, landingPage, step);
    }

    @Test
    @DisplayName(
        "Given a chaining-created result, open/click/submit hits are all correctly received once"
            + " the inject is backfilled")
    void given_chainingResult_should_receiveOpenClickSubmitEvents() {
      inject = persistInject();
      step = persistStep("{\"inject_id\": \"" + inject.getId() + "\"}");
      landingPage = persistLandingPage();
      user = persistUser();
      PhishingResult created =
          phishingTrackingService.createResult(
              InjectFixture.getDefaultInject(), landingPage, user.getId(), null, step.getId());
      // Pushes sentAt into the past so the automated-probe delay guard (a hit within the first
      // seconds of "sending") does not suppress these hits.
      created.setSentAt(Instant.now().minusSeconds(120));
      phishingResultRepository.save(created);

      phishingTrackingService.markOpened(created.getToken(), "10.0.0.1", "Mozilla/5.0");
      phishingTrackingService.markClicked(created.getToken(), "10.0.0.1", "Mozilla/5.0");
      phishingTrackingService.markSubmitted(
          created.getToken(),
          Map.of("username", "bob", "password", "secret"),
          "10.0.0.1",
          "Mozilla/5.0");

      PhishingResult saved = phishingResultRepository.findById(created.getId()).orElseThrow();
      assertThat(saved.getOpenedAt()).isNotNull();
      assertThat(saved.getClickedAt()).isNotNull();
      assertThat(saved.getSubmittedAt()).isNotNull();
      // The inject must have been backfilled by the very first resolveByToken call.
      assertThat(saved.getInject()).isNotNull();
      assertThat(saved.getInject().getId()).isEqualTo(inject.getId());
    }
  }

  @Nested
  @DisplayName("cascade delete")
  class CascadeDelete {

    private User user;
    private Inject inject;
    private PhishingLandingPage landingPage;
    private Step step;

    @AfterEach
    void tearDown() {
      cleanup(user, inject, landingPage, step);
    }

    @Test
    @DisplayName(
        "Deleting the step of a chaining result (inject not yet backfilled) cascades the delete to"
            + " the phishing_results row")
    void given_stepDeleted_should_cascadeDeletePhishingResult() {
      step = persistStep("{}");
      landingPage = persistLandingPage();
      user = persistUser();
      Inject uncommittedInject = InjectFixture.getDefaultInject();
      uncommittedInject.setId(UUID.randomUUID().toString());
      PhishingResult created =
          phishingTrackingService.createResult(
              uncommittedInject, landingPage, user.getId(), null, step.getId());
      assertThat(phishingResultRepository.findById(created.getId())).isPresent();

      workflowRepository.deleteById(step.getWorkflow().getId());

      assertThat(phishingResultRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @DisplayName(
        "Deleting the inject of a time-based result cascades the delete to the phishing_results"
            + " row")
    void given_injectDeleted_should_cascadeDeletePhishingResult() {
      inject = persistInject();
      landingPage = persistLandingPage();
      user = persistUser();
      PhishingResult created =
          phishingTrackingService.createResult(inject, landingPage, user.getId(), null, null);
      assertThat(phishingResultRepository.findById(created.getId())).isPresent();

      injectRepository.deleteById(inject.getId());

      assertThat(phishingResultRepository.findById(created.getId())).isEmpty();
    }
  }
}
