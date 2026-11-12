package io.openaev.rest.inject;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The teams named in the body of a simulation inject update are read in the tenant of the inject. A
 * team id of a different tenant is treated as an unknown id: {@code findAllById} skips it.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so each request runs its own transaction with its own
 * tenant scope, as in production, and they are removed on teardown.
 *
 * <p>Header cases select the default tenant and a second one through {@code X-Tenant-Ids}; the
 * simulation and its inject are the default tenant's, so the second tenant's team is the foreign
 * one.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Teams of a simulation inject are linked within the tenant of the inject")
class SimulationInjectTeamsTenantScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantB;
  private final List<Object[]> deletes = new ArrayList<>();
  private final List<Object[]> firstDeletes = new ArrayList<>();
  private final List<String[]> seeded = new ArrayList<>();

  private record SeededInject(String simulationId, String injectId) {}

  @BeforeEach
  void seedTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // The caller is a member of the default tenant first, then of a tenant of its own.
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    tenantB = tenantHelper.createTenantWithCurrentUser("simulation-inject-teams").getId();
    assertNotEquals(DEFAULT_TENANT, tenantB);
  }

  @AfterEach
  void cleanup() {
    for (Object[] delete : firstDeletes) {
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    firstDeletes.clear();
    // Reverse insertion order: links and children before the rows they reference.
    for (int i = deletes.size() - 1; i >= 0; i--) {
      Object[] delete = deletes.get(i);
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    deletes.clear();
    seeded.clear();
    tenantHelper.deleteCommittedTenants(tenantB);
  }

  @Nested
  @DisplayName("Through the header route")
  class ThroughHeaderRoute {

    @Test
    @DisplayName(
        "given a team of each tenant when updating the inject teams then only the inject's tenant team is linked")
    void given_teamsOfTwoTenants_should_linkOnlyTeamOfInjectTenant() throws Exception {
      // Arrange
      SeededInject inject = seedInject(DEFAULT_TENANT);
      String ownTeam = seedTeam(DEFAULT_TENANT);
      String foreignTeam = seedTeam(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateInjectTeams(inject, List.of(ownTeam, foreignTeam), false);

      // Assert
      result.andExpect(status().isOk());
      assertThat(teamsOf(inject.injectId())).containsExactly(ownTeam);
    }

    @Test
    @DisplayName(
        "given a team of the inject's tenant on an inject without team when updating then it is linked")
    void given_ownTeam_should_linkTeam() throws Exception {
      // Arrange
      SeededInject inject = seedInject(DEFAULT_TENANT);
      String ownTeam = seedTeam(DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateInjectTeams(inject, List.of(ownTeam), false);

      // Assert
      result.andExpect(status().isOk());
      assertThat(teamsOf(inject.injectId())).containsExactly(ownTeam);
    }

    // Guards the null check of the repository.
    @Test
    @DisplayName("given a null team list when updating then it is refused and the team stays")
    void given_nullTeamList_should_refuseAndKeepTeam() throws Exception {
      // Arrange
      SeededInject inject = seedInject(DEFAULT_TENANT);
      String ownTeam = seedTeam(DEFAULT_TENANT);
      linkTeam(inject.injectId(), ownTeam);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateInjectTeams(inject, null, false);

      // Assert
      result.andExpect(status().isBadRequest());
      assertThat(teamsOf(inject.injectId())).containsExactly(ownTeam);
    }
  }

  @Nested
  @DisplayName("Through the tenant route")
  class ThroughTenantRoute {

    @Test
    @DisplayName(
        "given a team of the inject's tenant when updating through the tenant path then it is linked")
    void given_ownTeam_should_linkTeamThroughTenantPath() throws Exception {
      // Arrange
      SeededInject inject = seedInject(tenantB);
      String ownTeam = seedTeam(tenantB);
      assertSeededTenants();

      // Act
      ResultActions result = updateInjectTeams(inject, List.of(ownTeam), true);

      // Assert
      result.andExpect(status().isOk());
      assertThat(teamsOf(inject.injectId())).containsExactly(ownTeam);
    }
  }

  // -- Requests --

  private ResultActions updateInjectTeams(
      SeededInject inject, List<String> teamIds, boolean tenantRoute) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("inject_teams", teamIds);
    MockHttpServletRequestBuilder request =
        tenantRoute
            ? put(
                TENANT_EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams",
                tenantB,
                inject.simulationId(),
                inject.injectId())
            : put(
                    EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams",
                    inject.simulationId(),
                    inject.injectId())
                .header(TENANT_HEADER, scopeHeader());
    return mvc.perform(
        request
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(asJsonString(body))
            .with(csrf()));
  }

  private String scopeHeader() {
    return DEFAULT_TENANT + "," + tenantB;
  }

  private void arrangeHeaderRoute() {
    TenantContext.clearCurrentTenant();
    assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());
    assertSeededTenants();
  }

  private void assertSeededTenants() {
    for (String[] row : seeded) {
      String actual =
          jdbc.queryForObject(
              "SELECT tenant_id FROM " + row[0] + " WHERE " + row[1] + " = ?",
              String.class,
              row[2]);
      assertEquals(row[3], actual, row[0] + " " + row[2]);
    }
  }

  // -- Reads --

  private List<String> teamsOf(String injectId) {
    return jdbc.queryForList(
        "SELECT team_id FROM injects_teams WHERE inject_id = ? ORDER BY team_id",
        String.class,
        injectId);
  }

  // -- Seeds --

  /** A simulation and one of its injects, both of the given tenant, with a contract of it. */
  private SeededInject seedInject(String tenantId) {
    String simulationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
            + " VALUES (?, ?, 'SCHEDULED', ?)",
        simulationId,
        "simulation-inject-teams-" + simulationId,
        tenantId);
    trackDelete("DELETE FROM exercises WHERE exercise_id = ?", simulationId);
    seeded.add(new String[] {"exercises", "exercise_id", simulationId, tenantId});

    String contractId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
            + " injector_contract_content, injector_contract_import_available)"
            + " VALUES (?, ?, '{}', false)",
        contractId,
        tenantId);
    trackDelete("DELETE FROM injectors_contracts WHERE injector_contract_id = ?", contractId);
    seeded.add(new String[] {"injectors_contracts", "injector_contract_id", contractId, tenantId});

    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_injector_contract, inject_exercise)"
            + " VALUES (?, ?, ?, false, true, 0, ?, ?)",
        injectId,
        tenantId,
        "simulation-inject-teams-" + injectId,
        contractId,
        simulationId);
    trackDelete("DELETE FROM injects WHERE inject_id = ?", injectId);
    trackFirst("DELETE FROM injects_teams WHERE inject_id = ?", injectId);
    seeded.add(new String[] {"injects", "inject_id", injectId, tenantId});
    return new SeededInject(simulationId, injectId);
  }

  private String seedTeam(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "simulation-inject-teams-" + id,
        tenantId);
    trackDelete("DELETE FROM teams WHERE team_id = ?", id);
    trackFirst("DELETE FROM injects_teams WHERE team_id = ?", id);
    seeded.add(new String[] {"teams", "team_id", id, tenantId});
    return id;
  }

  private void linkTeam(String injectId, String teamId) {
    jdbc.update("INSERT INTO injects_teams (inject_id, team_id) VALUES (?, ?)", injectId, teamId);
  }

  private void trackDelete(String sql, Object... params) {
    deletes.add(statement(sql, params));
  }

  private void trackFirst(String sql, Object... params) {
    firstDeletes.add(statement(sql, params));
  }

  private static Object[] statement(String sql, Object[] params) {
    Object[] statement = new Object[params.length + 1];
    System.arraycopy(params, 0, statement, 1, params.length);
    statement[0] = sql;
    return statement;
  }
}
