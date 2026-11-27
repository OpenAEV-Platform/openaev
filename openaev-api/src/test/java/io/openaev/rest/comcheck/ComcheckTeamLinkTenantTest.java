package io.openaev.rest.comcheck;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A communication check writes a status row for each player of the teams it names, among the teams
 * of the simulation's own tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The team ids of the
 * body resolve in the tenant of the simulation alone. The status rows are read from {@code
 * comchecks_statuses} through JDBC on the test connection, which the statement inspector does not
 * rewrite.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Communication check writes statuses for the teams of the simulation tenant only")
class ComcheckTeamLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String STATUS_USERS =
      "SELECT status_user FROM comchecks_statuses WHERE status_comcheck = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String exercise;
  private String teamInDefault;
  private String teamInB;
  private String playerInDefault;
  private String playerInB;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("comcheck-team-link-b").getId();
    playerInDefault = savePlayer();
    playerInB = savePlayer();
    teamInDefault = saveTeam(Tenant.DEFAULT_TENANT_UUID, playerInDefault);
    teamInB = saveTeam(tenantB, playerInB);
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    simulation.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    exercise = exerciseRepository.save(simulation).getId();
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given a simulation of the default tenant when the body names a team of each tenant then"
            + " only the player of the default tenant gets a status")
    void given_simulationOfDefaultTenant_should_writeStatusesForItsOwnTeamOnly() throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  post(EXERCISE_URI + "/" + exercise + "/comchecks")
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(body(teamInDefault, teamInB)))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String comcheck = JsonPath.read(response, "$.comcheck_id");
      assertThat(column(entityManager, STATUS_USERS, comcheck))
          .as(
              "players with a status: kept %s, dropped %s (team of the other tenant)",
              playerInDefault, playerInB)
          .containsExactly(playerInDefault);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a simulation of the default tenant when the body under its path names its team then"
            + " its player gets a status")
    void given_simulationOfDefaultTenant_should_writeTheStatusOnThePrefixedRoute()
        throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  post(
                          TENANT_EXERCISE_URI + "/{exerciseId}/comchecks",
                          Tenant.DEFAULT_TENANT_UUID,
                          exercise)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(body(teamInDefault)))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String comcheck = JsonPath.read(response, "$.comcheck_id");
      assertThat(column(entityManager, STATUS_USERS, comcheck)).containsExactly(playerInDefault);
    }
  }

  // -- Helpers --

  private Map<String, Object> body(String... teams) {
    return Map.of(
        "comcheck_name",
        "comcheck-team-link",
        "comcheck_end_date",
        Instant.now().plus(1, ChronoUnit.DAYS).toString(),
        "comcheck_subject",
        "subject",
        "comcheck_message",
        "message",
        "comcheck_teams",
        List.of(teams));
  }

  private String savePlayer() {
    String suffix = UUID.randomUUID().toString();
    User user =
        UserFixture.getUser("first", "last", "comcheck-" + suffix + "@unittests.invalid", false);
    return userRepository.save(user).getId();
  }

  private String saveTeam(String tenantId, String playerId) {
    Team team =
        TeamFixture.getTeam(
            userRepository.findById(playerId).orElseThrow(),
            "comcheck-" + UUID.randomUUID(),
            false);
    team.setTenant(new Tenant(tenantId));
    return teamRepository.save(team).getId();
  }

  /**
   * Each team carries the tenant the scenario relies on, then the ambient tenant is reset to the
   * default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(column(entityManager, "SELECT tenant_id FROM teams WHERE team_id = ?", teamInB))
        .containsExactly(tenantB);
    assertThat(
            column(entityManager, "SELECT tenant_id FROM teams WHERE team_id = ?", teamInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
