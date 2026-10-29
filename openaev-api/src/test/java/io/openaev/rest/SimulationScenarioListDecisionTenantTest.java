package io.openaev.rest;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lists of simulations and scenarios decide in the tenant of each row whether the caller reads
 * every row or only the granted ones.
 *
 * <p>The caller holds the rights {@code @WithMockUser} grants in the default tenant, and is a
 * member of tenant B, where it holds only what a test grants there. The requests name both tenants
 * in the {@code X-Tenant-Ids} header while the ambient tenant stays the default one: a row of the
 * default tenant is listed, a row of B that the caller holds no grant on is not. The member
 * contrast grants the right in B and reads B's rows on B's tenant route.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Simulation and scenario list decisions hold in the tenant of the rows")
class SimulationScenarioListDecisionTenantTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            String.join(
                ",",
                Stream.concat(
                        ProductionActiveTables.names().stream(),
                        Stream.of("exercises", "scenarios"))
                    .distinct()
                    .toList()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    scenarioComposer.reset();
    exerciseComposer.reset();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("list-decision-b").getId();
  }

  @Nested
  @DisplayName("Scenario lists")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  class ScenarioLists {

    @Test
    @DisplayName("given the access right only outside B should list the default scenario only")
    void given_accessRightOnlyOutsideB_should_listDefaultScenarioOnly() throws Exception {
      // -- ARRANGE --
      String scenarioOfDefault = scenarioIn(Tenant.DEFAULT_TENANT_UUID);
      String scenarioOfB = scenarioIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inBothTenants(get(SCENARIO_URI)))
          .andExpect(status().isOk())
          .andExpect(jsonPath(listed("scenario_id", scenarioOfDefault)).isNotEmpty())
          .andExpect(jsonPath(listed("scenario_id", scenarioOfB)).isEmpty());
    }

    @Test
    @DisplayName(
        "given the access right only outside B should find the default scenario by id only")
    void given_accessRightOnlyOutsideB_should_findDefaultScenarioByIdOnly() throws Exception {
      // -- ARRANGE --
      String scenarioOfDefault = scenarioIn(Tenant.DEFAULT_TENANT_UUID);
      String scenarioOfB = scenarioIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(post(SCENARIO_URI + "/search-by-id"))
                  .content(
                      asJsonString(
                          Map.of("scenario_ids", List.of(scenarioOfDefault, scenarioOfB)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath(listed("scenario_id", scenarioOfDefault)).isNotEmpty())
          .andExpect(jsonPath(listed("scenario_id", scenarioOfB)).isEmpty());
    }

    @Test
    @DisplayName("given the access right only outside B should search the default scenario only")
    void given_accessRightOnlyOutsideB_should_searchDefaultScenarioOnly() throws Exception {
      // -- ARRANGE --
      String scenarioOfDefault = scenarioIn(Tenant.DEFAULT_TENANT_UUID);
      String scenarioOfB = scenarioIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inBothTenants(post(SCENARIO_URI + "/search")).content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched("scenario_id", scenarioOfDefault)).isNotEmpty())
          .andExpect(jsonPath(searched("scenario_id", scenarioOfB)).isEmpty());
    }

    @Test
    @DisplayName("given the access right in B should list, find and search B's scenario")
    void given_accessRightInB_should_listFindAndSearchBScenario() throws Exception {
      // -- ARRANGE --
      String scenarioOfB = scenarioIn(tenantB);
      grantInB(Capability.ACCESS_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(TENANT_SCENARIO_URI, tenantB)))
          .andExpect(status().isOk())
          .andExpect(jsonPath(listed("scenario_id", scenarioOfB)).isNotEmpty());
      mvc.perform(
              inB(post(TENANT_SCENARIO_URI + "/search-by-id", tenantB))
                  .content(asJsonString(Map.of("scenario_ids", List.of(scenarioOfB)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath(listed("scenario_id", scenarioOfB)).isNotEmpty());
      mvc.perform(inB(post(TENANT_SCENARIO_URI + "/search", tenantB)).content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched("scenario_id", scenarioOfB)).isNotEmpty());
    }
  }

  @Nested
  @DisplayName("Scenario bulk delete")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT, Capability.DELETE_ASSESSMENT})
  class ScenarioBulkDelete {

    @Test
    @DisplayName("given the delete right only outside B should delete the default scenario only")
    void given_deleteRightOnlyOutsideB_should_deleteDefaultScenarioOnly() throws Exception {
      // -- ARRANGE --
      String scenarioOfDefault = scenarioIn(Tenant.DEFAULT_TENANT_UUID);
      String scenarioOfB = scenarioIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(delete(SCENARIO_URI))
                  .content(
                      asJsonString(
                          Map.of(
                              "scenario_ids_to_process", List.of(scenarioOfDefault, scenarioOfB)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@ == '" + scenarioOfDefault + "')]").isNotEmpty())
          .andExpect(jsonPath("$[?(@ == '" + scenarioOfB + "')]").isEmpty());
      assertThat(rowCount("scenarios", "scenario_id", scenarioOfB)).isEqualTo(1);
    }

    @Test
    @DisplayName("given the delete right in B should delete B's scenario")
    void given_deleteRightInB_should_deleteBScenario() throws Exception {
      // -- ARRANGE --
      String scenarioOfB = scenarioIn(tenantB);
      grantInB(Capability.ACCESS_ASSESSMENT, Capability.DELETE_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(
              inB(delete(TENANT_SCENARIO_URI, tenantB))
                  .content(asJsonString(Map.of("scenario_ids_to_process", List.of(scenarioOfB)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@ == '" + scenarioOfB + "')]").isNotEmpty());
      assertThat(rowCount("scenarios", "scenario_id", scenarioOfB)).isZero();
    }
  }

  @Nested
  @DisplayName("Simulation lists")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  class SimulationLists {

    @Test
    @DisplayName("given the access right only outside B should list the default simulation only")
    void given_accessRightOnlyOutsideB_should_listDefaultSimulationOnly() throws Exception {
      // -- ARRANGE --
      String simulationOfDefault = simulationIn(Tenant.DEFAULT_TENANT_UUID);
      String simulationOfB = simulationIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inBothTenants(get(EXERCISE_URI)))
          .andExpect(status().isOk())
          .andExpect(jsonPath(listed("exercise_id", simulationOfDefault)).isNotEmpty())
          .andExpect(jsonPath(listed("exercise_id", simulationOfB)).isEmpty());
    }

    @Test
    @DisplayName(
        "given the access right only outside B should find the default simulation by id only")
    void given_accessRightOnlyOutsideB_should_findDefaultSimulationByIdOnly() throws Exception {
      // -- ARRANGE --
      String simulationOfDefault = simulationIn(Tenant.DEFAULT_TENANT_UUID);
      String simulationOfB = simulationIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(post(EXERCISE_URI + "/search-by-id"))
                  .content(
                      asJsonString(
                          Map.of("exercise_ids", List.of(simulationOfDefault, simulationOfB)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath(listed("exercise_id", simulationOfDefault)).isNotEmpty())
          .andExpect(jsonPath(listed("exercise_id", simulationOfB)).isEmpty());
    }

    @Test
    @DisplayName("given the access right only outside B should search the default simulation only")
    void given_accessRightOnlyOutsideB_should_searchDefaultSimulationOnly() throws Exception {
      // -- ARRANGE --
      String simulationOfDefault = simulationIn(Tenant.DEFAULT_TENANT_UUID);
      String simulationOfB = simulationIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(inBothTenants(post(EXERCISE_URI + "/search")).content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched("exercise_id", simulationOfDefault)).isNotEmpty())
          .andExpect(jsonPath(searched("exercise_id", simulationOfB)).isEmpty());
    }

    @Test
    @DisplayName("given the access right in B should list, find and search B's simulation")
    void given_accessRightInB_should_listFindAndSearchBSimulation() throws Exception {
      // -- ARRANGE --
      String simulationOfB = simulationIn(tenantB);
      grantInB(Capability.ACCESS_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(inB(get(TENANT_EXERCISE_URI, tenantB)))
          .andExpect(status().isOk())
          .andExpect(jsonPath(listed("exercise_id", simulationOfB)).isNotEmpty());
      mvc.perform(
              inB(post(TENANT_EXERCISE_URI + "/search-by-id", tenantB))
                  .content(asJsonString(Map.of("exercise_ids", List.of(simulationOfB)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath(listed("exercise_id", simulationOfB)).isNotEmpty());
      mvc.perform(inB(post(TENANT_EXERCISE_URI + "/search", tenantB)).content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched("exercise_id", simulationOfB)).isNotEmpty());
    }
  }

  @Nested
  @DisplayName("Simulation bulk delete")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT, Capability.DELETE_ASSESSMENT})
  class SimulationBulkDelete {

    @Test
    @DisplayName("given the delete right only outside B should delete the default simulation only")
    void given_deleteRightOnlyOutsideB_should_deleteDefaultSimulationOnly() throws Exception {
      // -- ARRANGE --
      String simulationOfDefault = simulationIn(Tenant.DEFAULT_TENANT_UUID);
      String simulationOfB = simulationIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(delete(EXERCISE_URI))
                  .content(
                      asJsonString(
                          Map.of(
                              "exercise_ids_to_process",
                              List.of(simulationOfDefault, simulationOfB)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@ == '" + simulationOfDefault + "')]").isNotEmpty())
          .andExpect(jsonPath("$[?(@ == '" + simulationOfB + "')]").isEmpty());
      assertThat(rowCount("exercises", "exercise_id", simulationOfB)).isEqualTo(1);
    }

    @Test
    @DisplayName("given the delete right in B should delete B's simulation")
    void given_deleteRightInB_should_deleteBSimulation() throws Exception {
      // -- ARRANGE --
      String simulationOfB = simulationIn(tenantB);
      grantInB(Capability.ACCESS_ASSESSMENT, Capability.DELETE_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(
              inB(delete(TENANT_EXERCISE_URI, tenantB))
                  .content(asJsonString(Map.of("exercise_ids_to_process", List.of(simulationOfB)))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@ == '" + simulationOfB + "')]").isNotEmpty());
      assertThat(rowCount("exercises", "exercise_id", simulationOfB)).isZero();
    }
  }

  // -- HELPERS --

  private String scenarioIn(String tenantId) {
    Scenario scenario = createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(tenantId));
    String id = scenarioComposer.forScenario(scenario).persist().get().getId();
    entityManager.flush();
    return id;
  }

  private String simulationIn(String tenantId) {
    Exercise exercise = createDefaultExercise();
    exercise.setTenant(new Tenant(tenantId));
    String id = exerciseComposer.forExercise(exercise).persist().get().getId();
    entityManager.flush();
    return id;
  }

  private long rowCount(String table, String idColumn, String id) {
    entityManager.flush();
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM " + table + " WHERE " + idColumn + " = :id")
                .setParameter("id", id)
                .getSingleResult())
        .longValue();
  }

  private static String listed(String idField, String id) {
    return "$[?(@." + idField + " == '" + id + "')]";
  }

  private static String searched(String idField, String id) {
    return "$.content[?(@." + idField + " == '" + id + "')]";
  }

  /**
   * The non-prefixed route naming the default tenant and B. The ambient tenant is cleared first:
   * fixtures leave one on the test thread, and the request must see production's fallback to the
   * default tenant.
   */
  private MockHttpServletRequestBuilder inBothTenants(MockHttpServletRequestBuilder request) {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return request
        .header(TENANT_IDS_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
        .contentType(MediaType.APPLICATION_JSON)
        .with(csrf());
  }

  /** B's tenant route. */
  private MockHttpServletRequestBuilder inB(MockHttpServletRequestBuilder request) {
    TenantContext.clearCurrentTenant();
    return request.contentType(MediaType.APPLICATION_JSON).with(csrf());
  }

  private void grantInB(Capability... capabilities) {
    tenantHelper.grantCapabilitiesInTenant(tenantB, Set.of(capabilities));
  }

  private String largeSearch() {
    return asJsonString(PaginationFixture.getDefault().size(100).textSearch("").build());
  }
}
