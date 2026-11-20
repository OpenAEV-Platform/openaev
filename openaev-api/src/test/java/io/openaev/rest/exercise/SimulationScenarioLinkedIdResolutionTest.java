package io.openaev.rest.exercise;

import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The simulations and scenarios named in a request body resolve in the tenant of the entity
 * written. The lookup is {@code findAllByIdInTenant(ids, tenantId)}: an id of another tenant is
 * treated like an unknown id even when the request scope holds both tenants. The class arms the
 * production list, so the inspector scopes the lookups the way it does in production; the links are
 * read from the join tables through plain JDBC, which the inspector never rewrites.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Simulations and scenarios named in a request resolve in the tenant of the entity")
class SimulationScenarioLinkedIdResolutionTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String UPSERT = "/upsert";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String exerciseInB;
  private String exerciseInDefault;
  private String scenarioInB;
  private String scenarioInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so only the tenant of the lookup separates a row of the
    // default tenant from an entity written in B.
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("linked-id-b").getId();
    exerciseInB = saveExercise(tenantB);
    exerciseInDefault = saveExercise(Tenant.DEFAULT_TENANT_UUID);
    scenarioInB = saveScenario(tenantB);
    scenarioInDefault = saveScenario(Tenant.DEFAULT_TENANT_UUID);
    entityManager.flush();
    entityManager.clear();
    // Onboarding leaves B on the test thread and the request thread of the header route carries
    // none: the ambient tenant must fall back to the default one here.
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Nested
  @DisplayName("Lookup by tenant under a scope holding both tenants")
  class LookupUnderTwoTenantScope {

    @Test
    @DisplayName(
        "given a scope holding both tenants when ids of both are looked up in B then only the rows"
            + " of B come back")
    void given_idsOfTwoTenants_should_returnOnlyTheRowsOfTheGivenTenant() {
      // -- Arrange --
      setScope(Tenant.DEFAULT_TENANT_UUID + "," + tenantB);

      // -- Act --
      List<Exercise> exercises =
          exerciseRepository.findAllByIdInTenant(List.of(exerciseInB, exerciseInDefault), tenantB);
      List<Scenario> scenarios =
          scenarioRepository.findAllByIdInTenant(List.of(scenarioInB, scenarioInDefault), tenantB);

      // -- Assert --
      assertThat(exercises).extracting(Exercise::getId).containsExactly(exerciseInB);
      assertThat(scenarios).extracting(Scenario::getId).containsExactly(scenarioInB);
    }

    @Test
    @DisplayName("given a null id list when it is looked up then the lookup refuses it")
    void given_nullIds_should_refuseTheLookup() {
      // -- Act & Assert --
      assertThatThrownBy(() -> exerciseRepository.findAllByIdInTenant(null, tenantB))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasMessageContaining("Ids must not be null");
      assertThatThrownBy(() -> scenarioRepository.findAllByIdInTenant(null, tenantB))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasMessageContaining("Ids must not be null");
    }
  }

  @Nested
  @DisplayName("Team written in B on the header route")
  class TeamOnHeaderRoute {

    @Test
    @DisplayName(
        "given a create scoped to B by the header when it names a simulation and a scenario of"
            + " each tenant then the team links only those of B")
    void given_createOnHeaderRoute_should_linkOnlyTheParentsOfB() throws Exception {
      // -- Arrange --
      Map<String, Object> input = teamInput("linked-id-create");

      // -- Act --
      String teamId = send(TEAM_URI, tenantB, input);

      // -- Assert --
      assertThat(linked("exercises_teams", "exercise_id", teamId)).containsExactly(exerciseInB);
      assertThat(linked("scenarios_teams", "scenario_id", teamId)).containsExactly(scenarioInB);
    }

    @Test
    @DisplayName(
        "given an upsert of a new team scoped to B by the header when it names a simulation and a"
            + " scenario of each tenant then the team links only those of B")
    void given_upsertOnHeaderRoute_should_linkOnlyTheParentsOfB() throws Exception {
      // -- Arrange --
      Map<String, Object> input = teamInput("linked-id-upsert");

      // -- Act --
      String teamId = send(TEAM_URI + UPSERT, tenantB, input);

      // -- Assert --
      assertThat(linked("exercises_teams", "exercise_id", teamId)).containsExactly(exerciseInB);
      assertThat(linked("scenarios_teams", "scenario_id", teamId)).containsExactly(scenarioInB);
    }

    @Test
    @DisplayName(
        "given a create whose header names both tenants when it names parents of each then the"
            + " write is refused as ambiguous")
    void given_createOnTwoTenantScope_should_refuseTheWrite() throws Exception {
      // -- Act & Assert --
      mvc.perform(
              post(TEAM_URI)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(teamInput("linked-id-ambiguous")))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  // -- Helpers --

  private Map<String, Object> teamInput(String namePrefix) {
    return Map.of(
        "team_name", namePrefix + "-" + UUID.randomUUID(),
        "team_exercises", List.of(exerciseInB, exerciseInDefault),
        "team_scenarios", List.of(scenarioInB, scenarioInDefault));
  }

  private String send(String uri, String tenantHeader, Map<String, Object> input) throws Exception {
    String response =
        mvc.perform(
                post(uri)
                    .header(TENANT_HEADER, tenantHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.team_id");
  }

  private String saveExercise(String tenantId) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(tenantId));
    return exerciseRepository.save(exercise).getId();
  }

  private String saveScenario(String tenantId) {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(tenantId));
    return scenarioRepository.save(scenario).getId();
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /** Raw JDBC on the test connection: the statement inspector never rewrites it. */
  private List<String> linked(String joinTable, String column, String teamId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> values = new ArrayList<>();
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT "
                          + column
                          + " FROM "
                          + joinTable
                          + " WHERE team_id = ? ORDER BY 1")) {
                statement.setString(1, teamId);
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                  }
                }
              }
              return values;
            });
  }
}
