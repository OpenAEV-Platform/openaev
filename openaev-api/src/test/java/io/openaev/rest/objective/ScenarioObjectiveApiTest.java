package io.openaev.rest.objective;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Evaluation;
import io.openaev.database.model.Objective;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.EvaluationRepository;
import io.openaev.database.repository.ObjectiveRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EvaluationFixture;
import io.openaev.utils.fixtures.ObjectiveFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ObjectiveComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Scenario objectives API")
class ScenarioObjectiveApiTest extends IntegrationTest {

  private static final String OBJECTIVE_BODY =
      "{\"objective_title\":\"changed\",\"objective_description\":\"d\",\"objective_priority\":1}";
  private static final String EVALUATION_BODY = "{\"evaluation_score\":9}";

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ObjectiveComposer objectiveComposer;
  @Autowired private ObjectiveRepository objectiveRepository;
  @Autowired private EvaluationRepository evaluationRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String scenarioId;
  private String objectiveId;
  private String evaluationId;
  private String otherObjectiveId;
  private String otherEvaluationId;

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    objectiveComposer.reset();

    Scenario scenario = scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
    Scenario otherScenario =
        scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
    Objective objective = persistObjective(scenario);
    Objective otherObjective = persistObjective(otherScenario);
    Evaluation evaluation = persistEvaluation(objective);
    Evaluation otherEvaluation = persistEvaluation(otherObjective);
    entityManager.flush();
    entityManager.clear();

    scenarioId = scenario.getId();
    objectiveId = objective.getId();
    evaluationId = evaluation.getId();
    otherObjectiveId = otherObjective.getId();
    otherEvaluationId = otherEvaluation.getId();
  }

  private Objective persistObjective(Scenario scenario) {
    Objective objective = ObjectiveFixture.getDefaultObjective();
    objective.setScenario(scenario);
    return objectiveComposer.forObjective(objective).persist().get();
  }

  private Evaluation persistEvaluation(Objective objective) {
    return evaluationRepository.save(
        EvaluationFixture.createEvaluation(objective, testUserHolder.get()));
  }

  private String objectiveUri(String id) {
    return SCENARIO_URI + "/" + scenarioId + "/objectives/" + id;
  }

  private String evaluationUri(String objective, String evaluation) {
    return objectiveUri(objective) + "/evaluations/" + evaluation;
  }

  private static MockHttpServletRequestBuilder withBody(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  /** The rows of the other scenario the calls could reach: objective title and evaluations. */
  private String otherScenarioRows() {
    entityManager.flush();
    entityManager.clear();
    Objective objective = objectiveRepository.findById(otherObjectiveId).orElseThrow();
    return objective.getTitle()
        + "|"
        + objective.getEvaluations().size()
        + "|"
        + evaluationRepository.findById(otherEvaluationId).orElseThrow().getScore();
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Child of another scenario")
  class ChildOfAnotherScenario {

    Stream<Arguments> callsOnAChildOfAnotherScenario() {
      return Stream.of(
          call(
              "update its objective",
              () -> withBody(put(objectiveUri(otherObjectiveId)), OBJECTIVE_BODY)),
          call("delete its objective", () -> delete(objectiveUri(otherObjectiveId))),
          call(
              "list the evaluations of its objective",
              () -> get(objectiveUri(otherObjectiveId) + "/evaluations")),
          call(
              "evaluate its objective",
              () ->
                  withBody(post(objectiveUri(otherObjectiveId) + "/evaluations"), EVALUATION_BODY)),
          call(
              "read its evaluation", () -> get(evaluationUri(otherObjectiveId, otherEvaluationId))),
          call(
              "update its evaluation",
              () -> withBody(put(evaluationUri(objectiveId, otherEvaluationId)), EVALUATION_BODY)),
          call(
              "delete its evaluation", () -> delete(evaluationUri(objectiveId, otherEvaluationId))),
          call(
              "update its evaluation through its own objective",
              () ->
                  withBody(
                      put(evaluationUri(otherObjectiveId, otherEvaluationId)), EVALUATION_BODY)),
          call(
              "delete its evaluation through its own objective",
              () -> delete(evaluationUri(otherObjectiveId, otherEvaluationId))));
    }

    private static Arguments call(String name, Supplier<MockHttpServletRequestBuilder> request) {
      return Arguments.of(name, request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callsOnAChildOfAnotherScenario")
    @DisplayName("Given a child of another scenario, should answer not found and change nothing")
    void given_childOfAnotherScenario_should_answerNotFoundAndChangeNothing(
        String name, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
      // Arrange
      String before = otherScenarioRows();

      // Act
      mvc.perform(request.get().with(csrf()))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(otherScenarioRows()).isEqualTo(before);
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @WithMockUser
  @DisplayName("Scenario of another tenant")
  class ScenarioOfAnotherTenant {

    private String foreignScenarioId;
    private String foreignObjectiveId;
    private String foreignEvaluationId;

    @BeforeEach
    void setUpForeignScenario() throws Exception {
      String otherTenant = tenantHelper.createTenant("scenario-objectives-scope").getId();
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setTenant(new Tenant(otherTenant));
      scenarioComposer.forScenario(scenario).persist();
      Objective objective = persistObjective(scenario);
      Evaluation evaluation = persistEvaluation(objective);
      entityManager.flush();
      entityManager.clear();
      foreignScenarioId = scenario.getId();
      foreignObjectiveId = objective.getId();
      foreignEvaluationId = evaluation.getId();
      // The caller manages assessments in the default tenant only.
      tenantHelper.grantCapabilitiesInTenant(
          Tenant.DEFAULT_TENANT_UUID,
          Set.of(Capability.ACCESS_ASSESSMENT, Capability.MANAGE_ASSESSMENT));
    }

    private String foreignObjectiveUri() {
      return "/api/tenants/"
          + Tenant.DEFAULT_TENANT_UUID
          + "/scenarios/"
          + foreignScenarioId
          + "/objectives/"
          + foreignObjectiveId;
    }

    /** The foreign rows the calls could reach: objective title, evaluations and their score. */
    private String foreignRows() {
      entityManager.flush();
      entityManager.clear();
      Objective objective = objectiveRepository.findById(foreignObjectiveId).orElseThrow();
      return objective.getTitle()
          + "|"
          + objective.getEvaluations().size()
          + "|"
          + evaluationRepository.findById(foreignEvaluationId).orElseThrow().getScore();
    }

    Stream<Arguments> callsOnAScenarioOfAnotherTenant() {
      return Stream.of(
          call("list its objectives", () -> get(foreignObjectiveUri().replaceAll("/[^/]+$", ""))),
          call("update its objective", () -> withBody(put(foreignObjectiveUri()), OBJECTIVE_BODY)),
          call("delete its objective", () -> delete(foreignObjectiveUri())),
          call("list its evaluations", () -> get(foreignObjectiveUri() + "/evaluations")),
          call(
              "read its evaluation",
              () -> get(foreignObjectiveUri() + "/evaluations/" + foreignEvaluationId)),
          call(
              "evaluate its objective",
              () -> withBody(post(foreignObjectiveUri() + "/evaluations"), EVALUATION_BODY)),
          call(
              "update its evaluation",
              () ->
                  withBody(
                      put(foreignObjectiveUri() + "/evaluations/" + foreignEvaluationId),
                      EVALUATION_BODY)),
          call(
              "delete its evaluation",
              () -> delete(foreignObjectiveUri() + "/evaluations/" + foreignEvaluationId)));
    }

    private static Arguments call(String name, Supplier<MockHttpServletRequestBuilder> request) {
      return Arguments.of(name, request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callsOnAScenarioOfAnotherTenant")
    @DisplayName("Given a scenario of another tenant, should answer not found and change nothing")
    void given_scenarioOfAnotherTenant_should_answerNotFoundAndChangeNothing(
        String name, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
      // Arrange
      String before = foreignRows();
      // Fixtures leave a tenant on the test thread: the request must resolve its own scope.
      TenantContext.clearCurrentTenant();

      // Act
      String body =
          mvc.perform(request.get().with(csrf()))
              // Assert
              .andExpect(status().isNotFound())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).doesNotContain(foreignObjectiveId);
      assertThat(foreignRows()).isEqualTo(before);
    }
  }

  @Nested
  @DisplayName("Child of the scenario")
  class ChildOfTheScenario {

    @Test
    @DisplayName("Given its own objective, should update it")
    void given_ownObjective_should_updateIt() throws Exception {
      // Act
      mvc.perform(withBody(put(objectiveUri(objectiveId)), OBJECTIVE_BODY).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(objectiveRepository.findById(objectiveId).orElseThrow().getTitle())
          .isEqualTo("changed");
    }

    @Test
    @DisplayName("Given its own objective, should evaluate it")
    void given_ownObjective_should_evaluateIt() throws Exception {
      // Act
      mvc.perform(
              withBody(post(objectiveUri(objectiveId) + "/evaluations"), EVALUATION_BODY)
                  .with(csrf()))
          // Assert
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(objectiveRepository.findById(objectiveId).orElseThrow().getEvaluations())
          .hasSize(2);
    }

    @Test
    @DisplayName("Given its own evaluation, should update it")
    void given_ownEvaluation_should_updateIt() throws Exception {
      // Act
      mvc.perform(
              withBody(put(evaluationUri(objectiveId, evaluationId)), EVALUATION_BODY).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(evaluationRepository.findById(evaluationId).orElseThrow().getScore())
          .isEqualTo(9L);
    }

    @Test
    @DisplayName("Given its own evaluation and objective, should delete them")
    void given_ownEvaluationAndObjective_should_deleteThem() throws Exception {
      // Act
      mvc.perform(delete(evaluationUri(objectiveId, evaluationId)).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      mvc.perform(delete(objectiveUri(objectiveId)).with(csrf())).andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(evaluationRepository.findById(evaluationId)).isEmpty();
      assertThat(objectiveRepository.findById(objectiveId)).isEmpty();
    }
  }
}
