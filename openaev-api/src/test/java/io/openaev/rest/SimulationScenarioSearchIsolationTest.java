package io.openaev.rest;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Capability;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A list read of simulations and scenarios returns nothing of another tenant: a search and a
 * search-by-id sent on the tenant route of B never see a row of A. Both reads resolve their rows
 * from the request's tenant scope, so the pin is on the isolation of the read itself, not on a
 * by-id scope check in the handler.
 *
 * <p>The class follows the production list of isolated tables, which is what enforces the property
 * once {@code exercises} and {@code scenarios} are on it. The rows of A are seeded natively, since
 * the create endpoints would pin the transaction's scope on A and the search below sets it to B.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT})
@DisplayName("Simulation and scenario list reads stay inside the request's tenant scope")
class SimulationScenarioSearchIsolationTest extends IntegrationTest {

  private static final String SIMULATION_NAME = "cross-tenant-search-simulation";
  private static final String SCENARIO_NAME = "cross-tenant-search-scenario";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @Nested
  @DisplayName("Simulations")
  class Simulations {

    @Test
    @DisplayName("given a simulation of tenant A, a search from tenant B does not return it")
    void given_simulationOfTenantA_should_notAppearInTenantBSearch() throws Exception {
      // Arrange
      Tenant tenantA = tenantWithAssessmentRights("Search isolation A");
      Tenant tenantB = tenantWithAssessmentRights("Search isolation B");
      seedSimulationIn(tenantA);

      // Act
      String response =
          search(TENANT_EXERCISE_URI + "/search", tenantB, textSearch(SIMULATION_NAME));

      // Assert
      assertThat((Integer) JsonPath.read(response, "$.totalElements")).isZero();
    }
  }

  @Nested
  @DisplayName("Scenarios")
  class Scenarios {

    @Test
    @DisplayName("given a scenario of tenant A, a search from tenant B does not return it")
    void given_scenarioOfTenantA_should_notAppearInTenantBSearch() throws Exception {
      // Arrange
      Tenant tenantA = tenantWithAssessmentRights("Search isolation A");
      Tenant tenantB = tenantWithAssessmentRights("Search isolation B");
      seedScenarioIn(tenantA);

      // Act
      String response = search(TENANT_SCENARIO_URI + "/search", tenantB, textSearch(SCENARIO_NAME));

      // Assert
      assertThat((Integer) JsonPath.read(response, "$.totalElements")).isZero();
    }

    @Test
    @DisplayName("given a scenario of tenant A, a search by id from tenant B does not return it")
    void given_scenarioOfTenantA_should_notBeReturnedByIdFromTenantB() throws Exception {
      // Arrange
      Tenant tenantA = tenantWithAssessmentRights("Search isolation A");
      Tenant tenantB = tenantWithAssessmentRights("Search isolation B");
      String scenarioId = seedScenarioIn(tenantA);

      // Act
      String response =
          search(
              TENANT_SCENARIO_URI + "/search-by-id",
              tenantB,
              Map.of("scenario_ids", List.of(scenarioId)));

      // Assert
      assertThat((List<?>) JsonPath.read(response, "$")).isEmpty();
    }
  }

  // -- Helpers --

  private Tenant tenantWithAssessmentRights(String name) throws Exception {
    return tenantHelper.createTenantWithCapabilities(
        name, Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
  }

  private SearchPaginationInput textSearch(String name) {
    return PaginationFixture.simpleTextSearch(name);
  }

  private String search(String uriTemplate, Tenant tenant, Object body) throws Exception {
    return mvc.perform(
            post(uriTemplate, tenant.getId())
                .content(asJsonString(body))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** Seeded natively: a create under tenant A would pin this transaction's scope on A. */
  private void seedSimulationIn(Tenant tenant) {
    entityManager
        .createNativeQuery(
            "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
                + " VALUES (:id, :name, :status, CAST(:tenant AS uuid))")
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("name", SIMULATION_NAME)
        .setParameter("status", ExerciseStatus.SCHEDULED.name())
        .setParameter("tenant", tenant.getId())
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
  }

  /** Seeded natively, for the same reason as the simulation above. */
  private String seedScenarioIn(Tenant tenant) {
    String scenarioId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id)"
                + " VALUES (:id, :name, CAST(:tenant AS uuid))")
        .setParameter("id", scenarioId)
        .setParameter("name", SCENARIO_NAME)
        .setParameter("tenant", tenant.getId())
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
    return scenarioId;
  }
}
