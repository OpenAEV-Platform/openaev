package io.openaev.rest.team;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.team.TeamApi.TENANT_TEAM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The team endpoints return the raw {@code Team} entity, whose {@code team_exercise_injects} and
 * {@code team_scenario_injects} walk the team's simulations and scenarios down to their lazy {@code
 * injects} collections. They are serialized open-in-view, after the controller transaction has
 * committed, where the tenant scope is gone and a lazy load of the active {@code injects} table
 * fails closed. The collections are initialized inside the scoped transaction.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the scope would be set during serialization and hide the failure. Rows are seeded
 * through an auto-committing {@link JdbcTemplate} and removed on teardown.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Team endpoints serialize the injects of the team's simulations and scenarios")
class TeamInjectsSerializationTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenant;
  private final List<String[]> seededRows = new ArrayList<>();

  @BeforeEach
  void seedTenant() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("team-injects-serialization").getId();
  }

  @AfterEach
  void cleanup() {
    // Reverse insertion order: links before the rows they reference.
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    tenantHelper.deleteCommittedTenants(tenant);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Team writes")
  class TeamWrites {

    @Test
    @DisplayName(
        "given a team on a simulation and a scenario with injects when updating it then the response lists their injects")
    void given_teamWithInjects_should_serializeInjectsOnUpdate() throws Exception {
      // Arrange
      String simulationId = seedSimulation();
      String simulationInjectId = seedInject("inject_exercise", simulationId);
      String scenarioId = seedScenario();
      String scenarioInjectId = seedInject("inject_scenario", scenarioId);
      String teamId = seedTeam();
      link("exercises_teams", "exercise_id", simulationId, teamId);
      link("scenarios_teams", "scenario_id", scenarioId, teamId);

      // Act
      String response =
          perform(
              put(TENANT_TEAM_URI + "/{teamId}", tenant, teamId)
                  .content(asJsonString(Map.of("team_name", "team-injects-" + teamId))));

      // Assert
      assertThatJson(response)
          .node("team_exercise_injects")
          .isArray()
          .containsExactly(simulationInjectId);
      assertThatJson(response)
          .node("team_scenario_injects")
          .isArray()
          .containsExactly(scenarioInjectId);
    }
  }

  @Nested
  @DisplayName("Inject teams of a simulation")
  class InjectTeams {

    @Test
    @DisplayName(
        "given a team on a simulation when reading the teams of its inject then each team lists the inject")
    void given_teamOnSimulation_should_serializeExerciseInjectsOnInjectTeams() throws Exception {
      // Arrange
      String simulationId = seedSimulation();
      String injectId = seedInject("inject_exercise", simulationId);
      String teamId = seedTeam();
      link("exercises_teams", "exercise_id", simulationId, teamId);
      jdbc.update("UPDATE injects SET inject_all_teams = false WHERE inject_id = ?", injectId);
      link("injects_teams", "inject_id", injectId, teamId);

      // Act
      String response =
          perform(
              get(
                  TENANT_EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams",
                  tenant,
                  simulationId,
                  injectId));

      // Assert
      assertThatJson(response).inPath("$[0].team_id").isEqualTo(teamId);
      assertThatJson(response)
          .inPath("$[0].team_exercise_injects")
          .isArray()
          .containsExactly(injectId);
    }
  }

  private String perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(
            request
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  // -- Seeds --

  private String seedSimulation() {
    String simulationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
            + " VALUES (?, ?, 'SCHEDULED', ?)",
        simulationId,
        "team-injects-" + simulationId,
        tenant);
    seededRows.add(new String[] {"exercises", "exercise_id", simulationId});
    return simulationId;
  }

  private String seedScenario() {
    String scenarioId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id) VALUES (?, ?, ?)",
        scenarioId,
        "team-injects-" + scenarioId,
        tenant);
    seededRows.add(new String[] {"scenarios", "scenario_id", scenarioId});
    return scenarioId;
  }

  /** An inject for all teams, attached through the given column to a simulation or a scenario. */
  private String seedInject(String ownerColumn, String ownerId) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, "
            + ownerColumn
            + ") VALUES (?, ?, ?, true, true, 0, ?)",
        injectId,
        tenant,
        "team-injects-" + injectId,
        ownerId);
    seededRows.add(new String[] {"injects", "inject_id", injectId});
    return injectId;
  }

  private String seedTeam() {
    String teamId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        teamId,
        "team-injects-" + teamId,
        tenant);
    seededRows.add(new String[] {"teams", "team_id", teamId});
    return teamId;
  }

  private void link(String table, String ownerColumn, String ownerId, String teamId) {
    jdbc.update(
        "INSERT INTO " + table + " (" + ownerColumn + ", team_id) VALUES (?, ?)", ownerId, teamId);
    seededRows.add(new String[] {table, "team_id", teamId});
  }
}
