package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The leaf technical expectations read on every evaluation of an IOC validation come with their
 * inject and its execution status, so the evaluation reads the status of every inject without one
 * query per inject.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("InjectExpectationService.findTechnicalLeafExpectationsByInjectIds")
class InjectExpectationTechnicalLeavesQueryTest extends IntegrationTest {

  // Above hibernate.default_batch_fetch_size (50)
  private static final int INJECTS = 60;

  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    injectorContractComposer.reset();
    endpointComposer.reset();
    injectExpectationComposer.reset();
  }

  @Test
  @DisplayName(
      "reads the status of every inject in a number of queries that does not grow with them")
  void given_injects_should_readTheirStatusesWithoutAQueryPerInject() {
    // One endpoint without agent and one contract for every inject: only loading the injects
    // themselves could make the count grow with them. More injects than one batch fetch loads.
    EndpointComposer.Composer endpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
    InjectorContractComposer.Composer contract =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .persist();
    Exercise exercise = ExerciseFixture.createDefaultIncidentResponseExercise(Instant.now());
    exercise.setScenario(
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .persist()
            .get());
    ExerciseComposer.Composer exerciseWrapper = exerciseComposer.forExercise(exercise);
    for (int index = 0; index < INJECTS; index++) {
      exerciseWrapper.withInject(
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(contract)
              .withInjectStatus(
                  injectStatusComposer.forInjectStatus(InjectStatusFixture.createSuccessStatus()))
              .withExpectation(
                  injectExpectationComposer
                      .forExpectation(
                          InjectExpectationFixture.createExpectationWithTypeAndStatus(
                              EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.PENDING))
                      .withEndpoint(endpoint)));
    }
    exerciseWrapper.persist();
    List<String> injectIds = exercise.getInjects().stream().map(Inject::getId).toList();

    Loads fewInjects = statementsToReadStatuses(Set.copyOf(injectIds.subList(0, 2)), 2);
    Loads allInjects = statementsToReadStatuses(Set.copyOf(injectIds), INJECTS);

    assertThat(allInjects.statements())
        .as("statements for %d injects (%s) and for 2 (%s)", INJECTS, allInjects, fewInjects)
        .isEqualTo(fewInjects.statements());
  }

  /** The statements prepared, and the entities and collections fetched one by one. */
  private record Loads(long statements, String fetched) {}

  private Loads statementsToReadStatuses(Set<String> injectIds, int expectedInjects) {
    entityManager.flush();
    entityManager.clear();
    Statistics statistics =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    try {
      List<BaseInjectExpectation> leaves =
          injectExpectationService.findTechnicalLeafExpectationsByInjectIds(injectIds);
      Set<String> statuses =
          leaves.stream()
              .map(BaseInjectExpectation::getInject)
              .map(inject -> inject.getStatus().map(InjectStatus::getName).map(Enum::name))
              .map(status -> status.orElse("none"))
              .collect(Collectors.toSet());
      assertThat(leaves).hasSize(expectedInjects);
      assertThat(statuses).containsExactly("EXECUTED");
      String fetched =
          Stream.concat(
                  Arrays.stream(statistics.getEntityNames())
                      .map(
                          entity ->
                              Map.entry(
                                  entity, statistics.getEntityStatistics(entity).getFetchCount())),
                  Arrays.stream(statistics.getCollectionRoleNames())
                      .map(
                          role ->
                              Map.entry(
                                  role, statistics.getCollectionStatistics(role).getFetchCount())))
              .filter(entry -> entry.getValue() > 0)
              .map(entry -> entry.getKey() + "=" + entry.getValue())
              .sorted()
              .collect(Collectors.joining(", "));
      return new Loads(statistics.getPrepareStatementCount(), fetched);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }
}
