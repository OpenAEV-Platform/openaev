package io.openaev.service.chaining;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.autonomous.AutonomousRunApi;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The asset ids stored in the scope rules of a workflow resolve in the tenant of the workflow.
 *
 * <p>The workflow, its scope rules and the autonomous run live in tenant A. Its allowlist names one
 * endpoint of tenant A and one endpoint of tenant B: a rule stores the id its request names, and
 * the rule carries the tenant of its workflow. The caller belongs to both tenants, and the
 * non-prefixed autonomous run route takes {@code X-Tenant-Ids}, so the request scope holds both
 * tenants. The plan-scenario route reaches {@code getValidAssets} under the same scope through the
 * scope rules it writes. The class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Scope rules resolve the asset ids they store in the tenant of the workflow")
class ScopeRuleIdLookupTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String RUN_SCOPE_URI = AutonomousRunApi.AUTONOMOUS_URI + "/{runId}/scope";
  private static final String TENANT_RUN_SCOPE_URI =
      TENANT_PREFIX + AutonomousRunApi.AUTONOMOUS_URI.substring("/api".length()) + "/{runId}/scope";
  // The run names a simulation that is not in the seed: the lookup of the simulation answers 404
  // once the in-scope check has let the recipients through.
  private static final String UNKNOWN_SIMULATION = "scope-rule-unknown-simulation";
  private static final String TARGET_TEAM_URI =
      AutonomousRunApi.AUTONOMOUS_URI + "/{runId}/target-teams";
  private static final String TENANT_TARGET_TEAM_URI =
      TENANT_PREFIX
          + AutonomousRunApi.AUTONOMOUS_URI.substring("/api".length())
          + "/{runId}/target-teams";
  private static final String OWN_NAME = "scope-rule-own-endpoint";
  private static final String FOREIGN_NAME = "scope-rule-foreign-endpoint";

  // The run endpoints are EE-gated; the mock's license checks are set to "active" in the seed.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScopeService scopeService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private WorkflowComposer workflowComposer;

  private String tenantA;
  private String tenantB;
  private String workflowId;
  private String scenarioId;
  private String runId;
  private String scenarioOfLastWorkflow;
  private String endpointInA;
  private String endpointInB;

  @BeforeEach
  void seedWorkflowOfTenantAWithRulesNamingBothTenants() throws Exception {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    endpointComposer.reset();
    assetGroupComposer.reset();
    teamComposer.reset();
    userComposer.reset();
    scenarioComposer.reset();
    workflowComposer.reset();
    // The caller belongs to both tenants, so the header can name both of them.
    tenantA = tenantHelper.createTenantWithCurrentUser("scope-rule-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("scope-rule-b").getId();

    endpointInA = saveEndpoint(tenantA, OWN_NAME);
    endpointInB = saveEndpoint(tenantB, FOREIGN_NAME);

    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenantA));
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setSafeModeEnabled(true);
    workflow
        .getWorkflowScopeRules()
        .addAll(List.of(assetRule(workflow, endpointInA), assetRule(workflow, endpointInB)));
    workflowComposer
        .forWorkflow(workflow)
        .withScenario(scenarioComposer.forScenario(scenario))
        .persist();
    workflowId = workflow.getId();
    scenarioId = scenario.getId();

    runId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status, autonomous_run_scenario_id) VALUES (?, ?, 'Own the domain',"
            + " 'COMPLETED', ?)",
        runId,
        tenantA,
        scenarioId);
    // The seed must leave the persistence context: a read would otherwise be served from it with
    // no SQL for the inspector to scope.
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Valid assets of a workflow")
  class ValidAssets {

    @Test
    @DisplayName(
        "given a scope holding both tenants when the rules name an endpoint of each tenant then"
            + " only the endpoint of the workflow tenant is in scope")
    void given_scopeHoldingBothTenants_should_resolveOnlyTheEndpointOfTheWorkflowTenant() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenants(List.of(tenantA, tenantB)));

      // Act
      List<Asset> assets = scopeService.getValidAssets(workflowId);

      // Assert
      assertThat(assets).extracting(Asset::getId).containsExactly(endpointInA);
    }

    @Test
    @DisplayName(
        "given a scope holding the workflow tenant alone when the rules name an endpoint of each"
            + " tenant then the endpoint of the workflow tenant is in scope")
    void given_scopeOfTheWorkflowTenant_should_resolveTheEndpointOfTheWorkflowTenant() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act
      List<Asset> assets = scopeService.getValidAssets(workflowId);

      // Assert
      assertThat(assets).extracting(Asset::getId).containsExactly(endpointInA);
    }
  }

  @Nested
  @DisplayName("Asset groups stored in the rules of a workflow")
  class StoredAssetGroups {

    @Test
    @DisplayName(
        "given a scope holding both tenants when an allowlist rule names a group of the workflow"
            + " tenant then the assets of that group are in scope")
    void given_scopeHoldingBothTenants_should_resolveTheGroupOfTheWorkflowTenant() {
      // Arrange
      String endpointOfGroup = saveEndpoint(tenantA, "scope-rule-group-member");
      String group = saveGroup(tenantA, "scope-rule-own-group", endpointOfGroup);
      String workflow = seedWorkflowOfTenantA(groupRule(ScopeRuleSelectedMode.ALLOWLIST, group));
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenants(List.of(tenantA, tenantB)));

      // Act
      List<Asset> assets = scopeService.getValidAssets(workflow);

      // Assert
      assertThat(assets).extracting(Asset::getId).containsExactly(endpointOfGroup);
    }

    @Test
    @DisplayName(
        "given a scope holding both tenants when an allowlist rule names a group of another tenant"
            + " then the group is an unknown id")
    void given_scopeHoldingBothTenants_should_treatAnAllowlistedGroupOfAnotherTenantAsUnknown() {
      // Arrange
      String endpointOfForeignGroup = saveEndpoint(tenantB, "scope-rule-foreign-group-member");
      String foreignGroup = saveGroup(tenantB, "scope-rule-foreign-group", endpointOfForeignGroup);
      String workflow =
          seedWorkflowOfTenantA(groupRule(ScopeRuleSelectedMode.ALLOWLIST, foreignGroup));
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenants(List.of(tenantA, tenantB)));

      // Act + Assert
      assertThatThrownBy(() -> scopeService.getValidAssets(workflow))
          .isInstanceOf(ElementNotFoundException.class);
    }

    @Test
    @DisplayName(
        "given a scope holding both tenants when a denylist rule names a group of another tenant"
            + " then the group is an unknown id")
    void given_scopeHoldingBothTenants_should_treatADenylistedGroupOfAnotherTenantAsUnknown() {
      // Arrange
      String ownGroup =
          saveGroup(tenantA, "scope-rule-own-group", saveEndpoint(tenantA, "scope-rule-member"));
      String foreignGroup =
          saveGroup(
              tenantB, "scope-rule-foreign-group", saveEndpoint(tenantB, "scope-rule-foreign"));
      String workflow =
          seedWorkflowOfTenantA(
              groupRule(ScopeRuleSelectedMode.ALLOWLIST, ownGroup),
              groupRule(ScopeRuleSelectedMode.DENYLIST, foreignGroup));
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenants(List.of(tenantA, tenantB)));

      // Act + Assert
      assertThatThrownBy(() -> scopeService.getValidAssets(workflow))
          .isInstanceOf(ElementNotFoundException.class);
    }

    @Test
    @DisplayName(
        "given a scope of the workflow tenant alone when a rule names a group of another tenant"
            + " then the group is an unknown id")
    void given_scopeOfTheWorkflowTenant_should_treatAGroupOfAnotherTenantAsUnknown() {
      // Arrange
      String foreignGroup =
          saveGroup(
              tenantB, "scope-rule-foreign-group", saveEndpoint(tenantB, "scope-rule-foreign"));
      String workflow =
          seedWorkflowOfTenantA(groupRule(ScopeRuleSelectedMode.ALLOWLIST, foreignGroup));
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act + Assert
      assertThatThrownBy(() -> scopeService.getValidAssets(workflow))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Scope of an autonomous run")
  class RunScope {

    @Test
    @DisplayName(
        "given a header naming both tenants when the rules name an endpoint of each tenant then"
            + " only the endpoint of the run tenant carries its name")
    void given_headerNamingBothTenants_should_nameOnlyTheEndpointOfTheRunTenant() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      String response =
          mvc.perform(get(RUN_SCOPE_URI, runId).header(TENANT_HEADER, tenantA + "," + tenantB))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(nameOf(response, endpointInA)).isEqualTo(OWN_NAME);
      assertThat(nameOf(response, endpointInB)).isEqualTo(endpointInB);
    }

    @Test
    @DisplayName(
        "given the path of the run tenant when the rules name an endpoint of each tenant then only"
            + " the endpoint of the run tenant carries its name")
    void given_pathOfTheRunTenant_should_nameOnlyTheEndpointOfTheRunTenant() throws Exception {
      // Act
      String response =
          mvc.perform(get(TENANT_RUN_SCOPE_URI, tenantA, runId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(nameOf(response, endpointInA)).isEqualTo(OWN_NAME);
      assertThat(nameOf(response, endpointInB)).isEqualTo(endpointInB);
    }

    private String namesRunId;
    private String groupInA;
    private String groupInB;
    private String teamInA;
    private String teamInB;
    private String playerInA;
    private String playerInB;

    @BeforeEach
    void seedRunNamingEntitiesOfBothTenants() {
      groupInA = saveGroup(tenantA, "scope-rule-own-group", saveEndpoint(tenantA, "member-a"));
      groupInB = saveGroup(tenantB, "scope-rule-foreign-group", saveEndpoint(tenantB, "member-b"));
      playerInA = saveUser("ScopeRuleOwn", tenantA).getId();
      playerInB = saveUser("ScopeRuleForeign", tenantB).getId();
      teamInA = saveTeam(tenantA, "scope-rule-own-team");
      teamInB = saveTeam(tenantB, "scope-rule-foreign-team");
      namesRunId =
          seedRunOfTenantA(
              groupRule(ScopeRuleSelectedMode.ALLOWLIST, groupInA),
              groupRule(ScopeRuleSelectedMode.ALLOWLIST, groupInB),
              allowTeam(teamInA),
              allowTeam(teamInB),
              allowPlayer(playerInA),
              allowPlayer(playerInB));
    }

    @Test
    @DisplayName(
        "given a header naming both tenants when the rules name a group of each tenant then only"
            + " the group of the run tenant carries its name")
    void given_headerNamingBothTenants_should_nameOnlyTheGroupOfTheRunTenant() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      String response = scopeOnHeaderRoute(namesRunId);

      // Assert
      assertThat(nameOf(response, groupInA)).isEqualTo("scope-rule-own-group");
      assertThat(nameOf(response, groupInB)).isEqualTo(groupInB);
    }

    @Test
    @DisplayName(
        "given a header naming both tenants when the rules name a team of each tenant then only"
            + " the team of the run tenant carries its name")
    void given_headerNamingBothTenants_should_nameOnlyTheTeamOfTheRunTenant() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      String response = scopeOnHeaderRoute(namesRunId);

      // Assert
      assertThat(nameOf(response, teamInA)).isEqualTo("scope-rule-own-team");
      assertThat(nameOf(response, teamInB)).isEqualTo(teamInB);
    }

    @Test
    @DisplayName(
        "given a header naming both tenants when the rules name a player of each tenant then only"
            + " the member of the run tenant carries a name")
    void given_headerNamingBothTenants_should_nameOnlyThePlayerMemberOfTheRunTenant()
        throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      String response = scopeOnHeaderRoute(namesRunId);

      // Assert
      assertThat(nameOf(response, playerInA)).isEqualTo("ScopeRuleOwn Player");
      assertThat(nameOf(response, playerInB)).isEqualTo(playerInB);
    }

    @Test
    @DisplayName(
        "given the path of the run tenant when the rules name a player who is not a member of the"
            + " run tenant then the player carries no name")
    void given_pathOfTheRunTenant_should_nameOnlyThePlayerMemberOfTheRunTenant() throws Exception {
      // Act
      String response =
          mvc.perform(get(TENANT_RUN_SCOPE_URI, tenantA, namesRunId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(nameOf(response, playerInA)).isEqualTo("ScopeRuleOwn Player");
      assertThat(nameOf(response, playerInB)).isEqualTo(playerInB);
      assertThat(nameOf(response, groupInB)).isEqualTo(groupInB);
      assertThat(nameOf(response, teamInB)).isEqualTo(teamInB);
    }

    private String scopeOnHeaderRoute(String run) throws Exception {
      return mvc.perform(get(RUN_SCOPE_URI, run).header(TENANT_HEADER, tenantA + "," + tenantB))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }
  }

  @Nested
  @DisplayName("Target team of an autonomous run")
  class TargetTeam {

    private String playerOfBothTenants;

    @BeforeEach
    void seedPlayerOfBothTenants() {
      User player = saveUser("ScopeRuleBoth", tenantA);
      tenantHelper.attachUserToTenant(player.getId(), tenantB);
      playerOfBothTenants = player.getId();
    }

    @Test
    @DisplayName(
        "given a header naming both tenants when the allow scope names a team of another tenant"
            + " then the members of that team are outside the allow scope")
    void given_headerNamingBothTenants_should_refusePlayersOfAForeignAllowedTeam()
        throws Exception {
      // Arrange
      String foreignTeam = saveTeam(tenantB, "scope-rule-foreign-team", playerOfBothTenants());
      String run = seedRunOfTenantA(allowTeam(foreignTeam));
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      Exception refusal = targetTeamOnHeaderRoute(run, status().isForbidden());

      // Assert
      assertThat(refusal).isInstanceOf(ResponseStatusException.class);
      assertThat(((ResponseStatusException) refusal).getReason())
          .contains("outside the run's allow-scope");
    }

    @Test
    @DisplayName(
        "given a header naming both tenants when the allow scope names a team of the run tenant"
            + " then the members of that team pass the allow scope check")
    void given_headerNamingBothTenants_should_acceptPlayersOfAnAllowedTeamOfTheRunTenant()
        throws Exception {
      // Arrange
      String ownTeam = saveTeam(tenantA, "scope-rule-own-team", playerOfBothTenants());
      String run = seedRunOfTenantA(allowTeam(ownTeam));
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      Exception notFound = targetTeamOnHeaderRoute(run, status().isNotFound());

      // Assert
      assertThat(((ResponseStatusException) notFound).getReason())
          .isEqualTo("Simulation not found");
    }

    @Test
    @DisplayName(
        "given the path of the run tenant when the allow scope names a team of another tenant"
            + " then the members of that team are outside the allow scope")
    void given_pathOfTheRunTenant_should_refusePlayersOfAForeignAllowedTeam() throws Exception {
      // Arrange
      String foreignTeam = saveTeam(tenantB, "scope-rule-foreign-team", playerOfBothTenants());
      String run = seedRunOfTenantA(allowTeam(foreignTeam));

      // Act
      Exception refusal =
          mvc.perform(
                  post(TENANT_TARGET_TEAM_URI, tenantA, run)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(targetTeamBody())
                      .with(csrf()))
              .andExpect(status().isForbidden())
              .andReturn()
              .getResolvedException();

      // Assert
      assertThat(refusal).isInstanceOf(ResponseStatusException.class);
    }

    private User playerOfBothTenants() {
      return entityManager.getReference(User.class, playerOfBothTenants);
    }

    private String targetTeamBody() {
      return "{\"player_ids\":[\"" + playerOfBothTenants + "\"]}";
    }

    private Exception targetTeamOnHeaderRoute(String run, ResultMatcher expected) throws Exception {
      return mvc.perform(
              post(TARGET_TEAM_URI, run)
                  .header(TENANT_HEADER, tenantA + "," + tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(targetTeamBody())
                  .with(csrf()))
          .andExpect(expected)
          .andReturn()
          .getResolvedException();
    }
  }

  // -- FIXTURES --

  private String saveEndpoint(String tenantId, String name) {
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setTenant(new Tenant(tenantId));
    endpointComposer.forEndpoint(endpoint).persist();
    return endpoint.getId();
  }

  private String saveGroup(String tenantId, String name, String endpointId) {
    AssetGroup group = AssetGroupFixture.createDefaultAssetGroup(name);
    group.setTenant(new Tenant(tenantId));
    group.setAssets(
        new ArrayList<>(List.of(entityManager.getReference(Endpoint.class, endpointId))));
    assetGroupComposer.forAssetGroup(group).persist();
    return group.getId();
  }

  /** A workflow of tenant A with its own scenario, holding only the given rules. */
  private String seedWorkflowOfTenantA(RuleSpec... specs) {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenantA));
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setSafeModeEnabled(true);
    for (RuleSpec spec : specs) {
      workflow.getWorkflowScopeRules().add(spec.toRule(workflow));
    }
    workflowComposer
        .forWorkflow(workflow)
        .withScenario(scenarioComposer.forScenario(scenario))
        .persist();
    scenarioOfLastWorkflow = scenario.getId();
    entityManager.flush();
    entityManager.clear();
    return workflow.getId();
  }

  /** A run of tenant A bound to a scenario whose template workflow holds only the given rules. */
  private String seedRunOfTenantA(RuleSpec... specs) {
    seedWorkflowOfTenantA(specs);
    String run = UUID.randomUUID().toString();
    execute(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status, autonomous_run_scenario_id, autonomous_run_simulation_id)"
            + " VALUES (?, ?, 'Own the domain', 'COMPLETED', ?, ?)",
        run,
        tenantA,
        scenarioOfLastWorkflow,
        UNKNOWN_SIMULATION);
    entityManager.flush();
    entityManager.clear();
    return run;
  }

  private record RuleSpec(
      ScopeRuleSelectedMode mode,
      ScopeRuleSource source,
      ScopeRuleValueType valueType,
      String value) {
    WorkflowScopeRule toRule(Workflow workflow) {
      return WorkflowScopeRule.builder()
          .selectedMode(mode)
          .ruleSource(source)
          .valueType(valueType)
          .ruleValue(value)
          .workflow(workflow)
          .build();
    }
  }

  private static RuleSpec groupRule(ScopeRuleSelectedMode mode, String groupId) {
    return new RuleSpec(
        mode, ScopeRuleSource.ASSET_GROUP, ScopeRuleValueType.ASSET_GROUP_ID, groupId);
  }

  private static RuleSpec allowTeam(String teamId) {
    return new RuleSpec(
        ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleSource.TEAM, ScopeRuleValueType.TEAM_ID, teamId);
  }

  private static RuleSpec allowPlayer(String userId) {
    return new RuleSpec(
        ScopeRuleSelectedMode.ALLOWLIST,
        ScopeRuleSource.PLAYER,
        ScopeRuleValueType.PLAYER_ID,
        userId);
  }

  private String saveTeam(String tenantId, String name, User... members) {
    Team team = TeamFixture.getEmptyTeam();
    team.setName(name);
    team.setTenant(new Tenant(tenantId));
    team.setUsers(new ArrayList<>(List.of(members)));
    teamComposer.forTeam(team).persist();
    return team.getId();
  }

  private User saveUser(String name, String tenantId) {
    User user = UserFixture.getUser(name, "Player", UUID.randomUUID() + "@unittests.invalid");
    userComposer.forUser(user).persist();
    tenantHelper.attachUserToTenant(user.getId(), tenantId);
    return user;
  }

  private static WorkflowScopeRule assetRule(Workflow workflow, String endpointId) {
    return WorkflowScopeRule.builder()
        .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
        .ruleSource(ScopeRuleSource.ASSET)
        .valueType(ScopeRuleValueType.ASSET_ID)
        .ruleValue(endpointId)
        .workflow(workflow)
        .build();
  }

  private static String nameOf(String response, String entryId) {
    List<String> names = JsonPath.read(response, "$.allowlist[?(@.id == '" + entryId + "')].name");
    assertThat(names).hasSize(1);
    return names.getFirst();
  }

  private void execute(String sql, String... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                statement.executeUpdate();
              }
            });
  }
}
