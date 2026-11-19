package io.openaev.rest.team;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link ScenarioTeamApi#teams} returns only the teams of the request's tenants, with {@code teams}
 * and {@code scenarios} both v2-active, on the prefixed and the non-prefixed route.
 *
 * <p>The endpoint's {@code @AccessControl} names {@link
 * io.openaev.database.model.ResourceType#SCENARIO}, which is grant-managed: {@code
 * PermissionService#resolveTarget} keeps the target as given and loads no row, so a caller with no
 * scenario-reading capability and no grant on that id gets 403. A capability such as {@code
 * ACCESS_ASSESSMENT} grants {@code SCENARIO READ} whatever the id; the statement inspector then
 * scopes both {@code teams} (the query root) and {@code scenarios} (joined by {@link
 * io.openaev.database.specification.TeamSpecification#fromScenario}) to the request's tenants. An
 * admin skips the permission check and goes through the same scoped query.
 *
 * <p>Not {@code @Transactional}: each HTTP call runs in its own transaction with its own tenant
 * scope, as a real request does. One tenant selector per test method: the scope is set once per
 * transaction and {@code TenantScopeTransactionAspect} refuses to redefine it.
 */
@DisplayName("ScenarioTeamApi search isolates scenarios_teams by tenant")
class ScenarioTeamSearchIsolationTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_TEAMS_SEARCH_URI =
      TENANT_SCENARIO_URI + "/{scenarioId}/teams/search";
  private static final String TEAMS_SEARCH_URI = SCENARIO_URI + "/{scenarioId}/teams/search";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TeamComposer teamComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  private final List<String> createdTenantIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    if (!createdTenantIds.isEmpty()) {
      tenantHelper.deleteCommittedTenants(createdTenantIds.toArray(new String[0]));
    }
  }

  @Test
  @WithMockUser(isAdmin = false)
  @DisplayName(
      "given a caller of tenant A with no scenario capability or grant, when searching the teams"
          + " of tenant B's scenario under A's path, then the request is refused with 403")
  void given_callerOfTenantAWithNoGrant_should_beRefusedOnTenantBScenario_underPrefixedRoute()
      throws Exception {
    // -- ARRANGE --
    String tenantA = plainMemberTenant("sc-team-iso-refused-a");
    SeededScenario scenarioB = seedScenarioWithOwnTenant("sc-team-iso-refused-b");

    // -- ACT / ASSERT --
    mvc.perform(
            post(TENANT_TEAMS_SEARCH_URI, tenantA, scenarioB.scenarioId())
                .param("contextualOnly", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(PaginationFixture.getDefault().build()))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(isAdmin = false)
  @DisplayName(
      "given a caller of tenant A with no scenario capability or grant, when searching the teams"
          + " of tenant B's scenario via the X-Tenant-Ids header, then the request is refused with"
          + " 403")
  void given_callerOfTenantAWithNoGrant_should_beRefusedOnTenantBScenario_underHeaderRoute()
      throws Exception {
    // -- ARRANGE --
    String tenantA = plainMemberTenant("sc-team-iso-refused-hdr-a");
    SeededScenario scenarioB = seedScenarioWithOwnTenant("sc-team-iso-refused-hdr-b");

    // -- ACT / ASSERT --
    mvc.perform(
            post(TEAMS_SEARCH_URI, scenarioB.scenarioId())
                .header("X-Tenant-Ids", tenantA)
                .param("contextualOnly", "false")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(PaginationFixture.getDefault().build()))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName(
      "given an admin selecting tenant A via the X-Tenant-Ids header, when searching the teams of"
          + " tenant B's scenario, then no team of B is returned (admin skips the permission parent"
          + " load, the join scope still holds)")
  void given_adminWithSelectorNamingTenantA_should_seeNoTeamOfTenantBScenario_underHeaderRoute()
      throws Exception {
    // -- ARRANGE --
    String tenantA = plainMemberTenant("sc-team-iso-admin-a");
    SeededScenario scenarioB = seedScenarioWithOwnTenant("sc-team-iso-admin-b");

    // -- ACT --
    String response =
        mvc.perform(
                post(TEAMS_SEARCH_URI, scenarioB.scenarioId())
                    .header("X-Tenant-Ids", tenantA)
                    .param("contextualOnly", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(PaginationFixture.getDefault().build()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertFalse(
        response.contains(scenarioB.teamId()),
        "tenant B's team must never appear for an admin scoped to tenant A, even though the admin"
            + " skips the permission check entirely");
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName(
      "given a caller of tenant A, when searching the teams of A's own scenario under A's path,"
          + " then A's team is returned and B's never is")
  void given_callerOfTenantA_should_seeOnlyOwnTeam_onOwnScenario_underPrefixedRoute()
      throws Exception {
    // -- ARRANGE --
    String tenantA = plainMemberTenant("sc-team-iso-own-a");
    SeededScenario scenarioA = seedScenarioWithTeam(tenantA, "sc-team-iso-own-a");
    SeededScenario scenarioB = seedScenarioWithOwnTenant("sc-team-iso-own-b");

    // -- ACT --
    String response =
        mvc.perform(
                post(TENANT_TEAMS_SEARCH_URI, tenantA, scenarioA.scenarioId())
                    .param("contextualOnly", "false")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(PaginationFixture.getDefault().build()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertTrue(response.contains(scenarioA.teamId()), "A's own team must appear on A's scenario");
    assertFalse(response.contains(scenarioB.teamId()), "B's team must never appear under A's path");
  }

  @Test
  @WithMockUser(isAdmin = false)
  @DisplayName(
      "given a non-admin capability holder of tenant B, when searching the teams of B's own"
          + " scenario on B's route, then B's team is returned and A's never is")
  void
      given_nonAdminCapabilityHolderOfTenantB_should_seeOnlyOwnTeam_onOwnScenario_underPrefixedRoute()
          throws Exception {
    // -- ARRANGE --
    SeededScenario scenarioA = seedScenarioWithOwnTenant("sc-team-iso-nonadmin-a");
    String tenantB = capabilityHolderTenant("sc-team-iso-nonadmin-b");
    SeededScenario scenarioB = seedScenarioWithTeam(tenantB, "sc-team-iso-nonadmin-b-scenario");

    // -- ACT --
    String response =
        mvc.perform(
                post(TENANT_TEAMS_SEARCH_URI, tenantB, scenarioB.scenarioId())
                    .param("contextualOnly", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(PaginationFixture.getDefault().build()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertTrue(response.contains(scenarioB.teamId()), "B's own team must appear on B's scenario");
    assertFalse(
        response.contains(scenarioA.teamId()), "A's team must never appear under B's scope");
  }

  private record SeededScenario(String tenantId, String scenarioId, String teamId) {}

  private String plainMemberTenant(String name) throws Exception {
    String tenantId = tenantHelper.createTenantWithCurrentUser(name).getId();
    createdTenantIds.add(tenantId);
    return tenantId;
  }

  private String capabilityHolderTenant(String name) throws Exception {
    String tenantId =
        tenantHelper
            .createTenantWithCapabilities(name, Set.of(Capability.ACCESS_ASSESSMENT))
            .getId();
    createdTenantIds.add(tenantId);
    return tenantId;
  }

  private SeededScenario seedScenarioWithOwnTenant(String label) throws Exception {
    String tenantId = tenantHelper.createTenantWithCurrentUser(label).getId();
    createdTenantIds.add(tenantId);
    return seedScenarioWithTeam(tenantId, label);
  }

  private SeededScenario seedScenarioWithTeam(String tenantId, String label) {
    Team team = TeamFixture.createTeamWithName("team-" + label);
    team.setTenant(new Tenant(tenantId));
    Team persistedTeam = teamComposer.forTeam(team).persist().get();

    Scenario scenario = ScenarioFixture.getScenario(List.of(persistedTeam), null);
    scenario.setTenant(new Tenant(tenantId));
    Scenario persistedScenario = scenarioComposer.forScenario(scenario).persist().get();

    return new SeededScenario(tenantId, persistedScenario.getId(), persistedTeam.getId());
  }
}
