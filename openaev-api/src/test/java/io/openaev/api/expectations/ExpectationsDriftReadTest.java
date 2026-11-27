package io.openaev.api.expectations;

import static io.openaev.database.model.InjectorContract.AVAILABLE_EXPECTATIONS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.database.model.InjectorContract.IS_PREDEFINED_EXPECTATION;
import static io.openaev.utils.injector_contract.InjectorContractContentUtils.FIELDS;
import static io.openaev.utils.mapper.InjectExpectationMapper.NODE_EXPECTATION_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.api.expectations.dto.ExpectationsDriftOutput;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.ExecutionTraceFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExecutionTraceComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Expectations drift report reads")
class ExpectationsDriftReadTest extends IntegrationTest {

  /** The graph an inject entity load drags along, and the drift report must not load. */
  private static final List<Class<?>> INJECT_GRAPH =
      List.of(
          Inject.class,
          InjectorContract.class,
          InjectStatus.class,
          ExecutionTrace.class,
          PreventionInjectExpectation.class);

  @Autowired private ExpectationsDriftService expectationsDriftService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    exerciseComposer.reset();
    scenarioComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    injectStatusComposer.reset();
    executionTraceComposer.reset();
    injectExpectationComposer.reset();
  }

  /** A contract whose expectations field predefines the given expectation types. */
  private InjectorContractComposer.Composer contractPredefining(EXPECTATION_TYPE... types) {
    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    ObjectNode field = mapper.createObjectNode();
    field.put(CONTRACT_ELEMENT_CONTENT_KEY, CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
    ArrayNode available = field.putArray(AVAILABLE_EXPECTATIONS);
    for (EXPECTATION_TYPE type : types) {
      available
          .addObject()
          .put(NODE_EXPECTATION_TYPE, type.name())
          .put(IS_PREDEFINED_EXPECTATION, true);
    }
    ObjectNode content = contract.getConvertedContent();
    content.set(FIELDS, mapper.createArrayNode().add(field));
    contract.setContent(content.toString());
    contract.setConvertedContent(content);
    return injectorContractComposer.forInjectorContract(contract);
  }

  /**
   * An executed inject with its whole graph (status, execution traces, expectation rows), storing
   * the given expectations in its content, or none when no type is given.
   */
  private InjectComposer.Composer executedInject(
      InjectorContractComposer.Composer contract, EXPECTATION_TYPE... storedTypes) {
    Inject inject = InjectFixture.getDefaultInject();
    if (storedTypes.length > 0) {
      ObjectNode content = mapper.createObjectNode();
      ArrayNode stored = content.putArray(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
      for (EXPECTATION_TYPE type : storedTypes) {
        stored.addObject().put(NODE_EXPECTATION_TYPE, type.name());
      }
      inject.setContent(content);
    }
    return injectComposer
        .forInject(inject)
        .withInjectorContract(contract)
        .withInjectStatus(
            injectStatusComposer
                .forInjectStatus(InjectStatusFixture.createSuccessStatus())
                .withExecutionTrace(
                    executionTraceComposer.forExecutionTrace(
                        ExecutionTraceFixture.createDefaultExecutionTraceStart()))
                .withExecutionTrace(
                    executionTraceComposer.forExecutionTrace(
                        ExecutionTraceFixture.createDefaultExecutionTraceComplete())))
        .withExpectation(
            injectExpectationComposer.forExpectation(
                InjectExpectationFixture.createExpectationWithTypeAndStatus(
                    EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS)));
  }

  /** One drifted inject, one aligned, one following the contract dynamically. */
  private List<InjectComposer.Composer> executedInjects() {
    InjectorContractComposer.Composer contract =
        contractPredefining(EXPECTATION_TYPE.PREVENTION, EXPECTATION_TYPE.DETECTION);
    return List.of(
        executedInject(contract, EXPECTATION_TYPE.PREVENTION),
        executedInject(contract, EXPECTATION_TYPE.DETECTION, EXPECTATION_TYPE.PREVENTION),
        executedInject(contract));
  }

  private record Measured<T>(T result, long graphLoads) {}

  /** Runs the work on an empty session and counts the inject graph entities it loaded. */
  private <T> Measured<T> measure(Supplier<T> work) {
    entityManager.flush();
    entityManager.clear();
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    // The statistics belong to the session factory shared by every test: put them back as found
    boolean statisticsWereEnabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    try {
      stats.clear();
      T result = work.get();
      long graphLoads =
          INJECT_GRAPH.stream()
              .mapToLong(type -> stats.getEntityStatistics(type.getName()).getLoadCount())
              .sum();
      return new Measured<>(result, graphLoads);
    } finally {
      stats.setStatisticsEnabled(statisticsWereEnabled);
    }
  }

  /** The drift count the inject entities give, the way the report was computed from them. */
  private long driftedEntities(Collection<Inject> injects) {
    return injects.stream().filter(expectationsDriftService::hasDrift).count();
  }

  @Nested
  @DisplayName("Simulation")
  class Simulation {

    @Test
    @DisplayName(
        "given executed injects, should report the drift of their entities without loading them")
    void given_executedInjects_should_reportDriftWithoutLoadingInjectGraph() {
      // Arrange
      ExerciseComposer.Composer simulation =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());
      executedInjects().forEach(simulation::withInject);
      String simulationId = simulation.persist().get().getId();
      Measured<Long> fromEntities =
          measure(() -> driftedEntities(injectRepository.findByExerciseId(simulationId)));

      // Act
      Measured<ExpectationsDriftOutput> report =
          measure(() -> expectationsDriftService.exerciseDrift(simulationId));

      // Assert
      assertThat(fromEntities.result()).isEqualTo(1);
      assertThat(fromEntities.graphLoads())
          .as("the fixture builds the graph an inject entity load drags along")
          .isPositive();
      assertThat(report.result())
          .isEqualTo(new ExpectationsDriftOutput(true, fromEntities.result().intValue(), 3, false));
      assertThat(report.graphLoads()).isZero();
    }
  }

  @Nested
  @DisplayName("Scenario")
  class Scenario {

    @Test
    @DisplayName(
        "given executed injects, should report the drift of their entities without loading them")
    void given_executedInjects_should_reportDriftWithoutLoadingInjectGraph() {
      // Arrange
      ScenarioComposer.Composer scenario =
          scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());
      executedInjects().forEach(scenario::withInject);
      String scenarioId = scenario.persist().get().getId();
      Measured<Long> fromEntities =
          measure(() -> driftedEntities(injectRepository.findByScenarioId(scenarioId)));

      // Act
      Measured<ExpectationsDriftOutput> report =
          measure(() -> expectationsDriftService.scenarioDrift(scenarioId));

      // Assert
      assertThat(fromEntities.result()).isEqualTo(1);
      assertThat(fromEntities.graphLoads())
          .as("the fixture builds the graph an inject entity load drags along")
          .isPositive();
      assertThat(report.result())
          .isEqualTo(new ExpectationsDriftOutput(true, fromEntities.result().intValue(), 3, false));
      assertThat(report.graphLoads()).isZero();
    }
  }
}
