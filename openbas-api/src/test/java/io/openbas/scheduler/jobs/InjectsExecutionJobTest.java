package io.openbas.scheduler.jobs;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.repository.SecurityCoverageSendJobRepository;
import io.openbas.rest.exercise.service.ExerciseService;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InjectsExecutionJobTest extends IntegrationTest {

  @Autowired private InjectsExecutionJob job;

  @Autowired private ExerciseService exerciseService;
  @Autowired private InjectRepository injectRepository;

  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private SecurityCoverageSendJobRepository securityCoverageSendJobRepository;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;

  @DisplayName("Not start children injects at the same time as parent injects")
  @Test
  void given_cron_in_one_minute_should_not_start_children_injects() throws JobExecutionException {
    // -- PREPARE --
    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setStart(Instant.now().minus(1, ChronoUnit.MINUTES));
    Exercise exerciseSaved = this.exerciseService.createExercise(exercise);
    Inject injectParent =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withEndpoint(
                endpointComposer
                    .forEndpoint(EndpointFixture.createEndpoint())
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .persist()
            .get();
    Inject injectChildren =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withEndpoint(
                endpointComposer
                    .forEndpoint(EndpointFixture.createEndpoint())
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .withDependsOn(injectParent)
            .persist()
            .get();
    entityManager.flush();

    injectParent.setExercise(exerciseSaved);
    injectChildren.setExercise(exerciseSaved);
    injectParent.setStatus(null);
    injectChildren.setStatus(null);
    exerciseSaved.setInjects(new ArrayList<>(List.of(injectParent, injectChildren)));
    String exerciseId = exerciseSaved.getId();

    injectRepository.saveAll(new ArrayList<>(List.of(injectParent, injectChildren)));
    entityManager.flush();
    // -- EXECUTE --
    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    List<Inject> injectsSaved = injectRepository.findByExerciseId(exerciseId);
    Optional<Inject> savedInjectParent =
        injectsSaved.stream()
            .filter(inject -> inject.getId().equals(injectParent.getId()))
            .findFirst();
    Optional<Inject> savedInjectChildren =
        injectsSaved.stream()
            .filter(inject -> inject.getId().equals(injectChildren.getId()))
            .findFirst();
    // Checking that only the parent inject has a status
    assertTrue(savedInjectParent.isPresent());
    assertTrue(savedInjectChildren.isPresent());

    assertTrue(savedInjectParent.get().getStatus().isPresent());
    assertTrue(savedInjectChildren.get().getStatus().isEmpty());

    assertNotNull(savedInjectParent.get().getStatus().get().getName());
  }

  @Test
  @DisplayName("When auto closing of stix-created simulation, trigger stix coverage job")
  public void whenAutoClosingStixCreatedSimulation_TriggerStixCoverageJob()
      throws JobExecutionException {
    ExerciseComposer.Composer exerciseWrapper =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withSecurityCoverage(
                securityCoverageComposer.forSecurityCoverage(
                    SecurityCoverageFixture.createDefaultSecurityCoverage()))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer.forInjectStatus(
                            InjectStatusFixture.createSuccessStatus())))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer.forInjectStatus(
                            InjectStatusFixture.createSuccessStatus())));

    injectComposer.generatedItems.forEach(
        i -> i.setCollectExecutionStatus(CollectExecutionStatus.COMPLETED));
    exerciseWrapper.get().setStatus(ExerciseStatus.RUNNING);
    exerciseWrapper.persist();
    entityManager.flush();

    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // assert
    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobRepository.findBySimulation(exerciseWrapper.get());
    assertThat(job).isNotEmpty();
  }

  @Test
  @DisplayName(
      "When auto closing of NON stix-created simulation, DOES NOT trigger stix coverage job")
  public void whenAutoClosingNONStixCreatedSimulation_DoesNotTriggerStixCoverageJob()
      throws JobExecutionException {
    ExerciseComposer.Composer exerciseWrapper =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer.forInjectStatus(
                            InjectStatusFixture.createSuccessStatus())))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer.forInjectStatus(
                            InjectStatusFixture.createSuccessStatus())));

    injectComposer.generatedItems.forEach(
        i -> i.setCollectExecutionStatus(CollectExecutionStatus.COMPLETED));
    exerciseWrapper.get().setStatus(ExerciseStatus.RUNNING);
    exerciseWrapper.persist();
    entityManager.flush();

    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // assert
    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobRepository.findBySimulation(exerciseWrapper.get());
    assertThat(job).isEmpty();
  }
}
