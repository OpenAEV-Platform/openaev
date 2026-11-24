package io.openaev.service.chaining;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The teams in the scope of a workflow are the teams its scope rules name in the workflow's own
 * tenant. A rule stores the team id its request names, so a rule can name a team of another tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The teams of a
 * chained simulation are read from the scope of its workflow, and the team ids of the scope rules
 * resolve in the tenant of the workflow alone. The class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Workflow scope resolves the teams of its rules in the workflow tenant only")
class ScopeTeamsTenantTest extends ProductionTablesIntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private WorkflowScopeRuleRepository workflowScopeRuleRepository;
  @Autowired private TeamRepository teamRepository;

  private String tenantB;
  private String exercise;
  private String teamInDefault;
  private String teamInB;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("scope-teams-b").getId();
    teamInDefault = saveTeam(Tenant.DEFAULT_TENANT_UUID);
    teamInB = saveTeam(tenantB);
    exercise = saveChainedSimulationAllowing(teamInDefault, teamInB);
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
        "given a workflow of the default tenant whose rules name a team of each tenant when its"
            + " simulation teams are read then only the team of the default tenant is in scope")
    void given_rulesNamingATeamOfEachTenant_should_resolveOnlyTheTeamOfTheWorkflowTenant()
        throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  get(EXERCISE_URI + "/" + exercise + "/teams")
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      List<String> teams = JsonPath.read(response, "$[*].team_id");
      assertThat(teams)
          .as("teams in scope: kept %s, dropped %s", teamInDefault, teamInB)
          .containsExactly(teamInDefault);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a workflow of the default tenant whose rules name its team when its simulation"
            + " teams are read under its path then the team is in scope")
    void given_rulesNamingTheTeamOfTheWorkflowTenant_should_resolveItOnThePrefixedRoute()
        throws Exception {
      // -- Arrange --
      armRequest();

      // -- Act --
      String response =
          mvc.perform(
                  get(
                      TENANT_EXERCISE_URI + "/{exerciseId}/teams",
                      Tenant.DEFAULT_TENANT_UUID,
                      exercise))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      List<String> teams = JsonPath.read(response, "$[*].team_id");
      assertThat(teams).containsExactly(teamInDefault);
    }
  }

  // -- Helpers --

  private String saveTeam(String tenantId) {
    Team team = TeamFixture.createTeamWithName("scope-teams-" + UUID.randomUUID());
    team.setTenant(new Tenant(tenantId));
    return teamRepository.save(team).getId();
  }

  /** A simulation of the default tenant whose workflow template allowlists the given teams. */
  private String saveChainedSimulationAllowing(String... teams) {
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    simulation.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    simulation = exerciseRepository.save(simulation);
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setSimulation(simulation);
    workflow = workflowRepository.save(workflow);
    for (String team : teams) {
      workflowScopeRuleRepository.save(
          WorkflowScopeRule.builder()
              .workflow(workflow)
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.TEAM)
              .valueType(ScopeRuleValueType.TEAM_ID)
              .ruleValue(team)
              .build());
    }
    entityManager.flush();
    entityManager.clear();
    return simulation.getId();
  }

  /**
   * Each team and the scope rules carry the tenant the scenario relies on, then the ambient tenant
   * is reset to the default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(column(entityManager, "SELECT tenant_id FROM teams WHERE team_id = ?", teamInB))
        .containsExactly(tenantB);
    assertThat(
            column(
                entityManager,
                "SELECT DISTINCT r.tenant_id FROM workflow_scope_rules r JOIN workflows w ON"
                    + " w.workflow_id = r.workflow_id WHERE w.workflow_simulation_id = ?",
                exercise))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
