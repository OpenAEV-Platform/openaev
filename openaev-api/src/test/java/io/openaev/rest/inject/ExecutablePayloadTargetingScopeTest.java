package io.openaev.rest.inject;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The implant fetches the command of the inject it is running through {@code GET
 * /api/tenants/{tenantId}/injects/{injectId}/{agentId}/executable-payload}, authenticated with the
 * per-tenant service account. The endpoint carries an object-level ownership gate: the request is
 * refused with a 403 unless the requesting agent's asset is actually targeted by the inject,
 * directly or through one of its asset groups.
 *
 * <p>That gate resolves its answer by reading {@code assets} and {@code asset_groups}, both
 * tenant-active, and a dynamic group's membership is a filter evaluation rather than a row lookup.
 * The caller is the service account, whose scope is exactly its own tenant. This class measures the
 * three targeting shapes a real inject can use under that scope, and the two refusals the gate
 * exists for, with the tables armed as production arms them.
 *
 * <p>The identity is the production one: {@link ServiceAccountPrivilegeService} provisions the
 * account with the same call the tenant onboarding uses, and the request authenticates as it rather
 * than as a mock user carrying the capability.
 */
@TestPropertySource(
    properties =
        "openaev.tenant.active-tables=import_mappers,lessons_templates,mitigations,cwes,collectors,executors,injectors,tags,tag_rules,attackpath_execution,attackpath_finding,secret_references,secrets,connector_instances,autonomous_runs,autonomous_events,autonomous_directives,kill_chain_phases,security_coverages,domains,challenges,asset_groups,channels,notifications,assets,findings,tenant_xtmhub_registrations,notifiers,notification_triggers,notification_events,custom_dashboards,widgets,marking_definitions,documents,custom_domains,collector_types,phishing_email_templates,phishing_landing_pages,attackpath_execution_collector,attackpath_execution_remediation,asset_agent_jobs,payloads,vulnerabilities,phishing_results,reporting_schedules,reportings,reporting_generations,datapacks")
@WithMockUser(isAdmin = true)
@Transactional
@DisplayName("Executable payload ownership gate under the agent's own tenant scope")
class ExecutablePayloadTargetingScopeTest extends IntegrationTest {

  /** Mirrors {@code InjectApi.TENANT_INJECT_URI}, which is private to the controller. */
  private static final String TENANT_INJECT_URI = TENANT_PREFIX + "/injects";

  private static final String EXECUTABLE_PAYLOAD_URI =
      TENANT_INJECT_URI + "/{injectId}/{agentId}/executable-payload";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;

  @BeforeEach
  void resetComposers() {
    injectComposer.reset();
    injectStatusComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    domainComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    assetGroupComposer.reset();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("A legitimately targeted agent fetches its payload")
  class TargetedAgent {

    @Test
    @DisplayName("given a directly targeted agent should return the payload")
    void given_directlyTargetedAgent_should_returnThePayload() throws Exception {
      // Arrange
      String tenantId = seedTenant("exec-payload-direct");
      Targeting targeting = seedDirectTargeting(tenantId);
      authenticateAsServiceAccountOf(tenantId);

      // Act & Assert
      mvc.perform(
              get(EXECUTABLE_PAYLOAD_URI, tenantId, targeting.injectId(), targeting.agentId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given an agent targeted through a static asset group should return the payload")
    void given_agentTargetedThroughStaticAssetGroup_should_returnThePayload() throws Exception {
      // Arrange
      String tenantId = seedTenant("exec-payload-static");
      Targeting targeting = seedStaticGroupTargeting(tenantId);
      authenticateAsServiceAccountOf(tenantId);

      // Act & Assert
      mvc.perform(
              get(EXECUTABLE_PAYLOAD_URI, tenantId, targeting.injectId(), targeting.agentId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given an agent targeted through a dynamic asset group should return the payload")
    void given_agentTargetedThroughDynamicAssetGroup_should_returnThePayload() throws Exception {
      // Arrange
      String tenantId = seedTenant("exec-payload-dynamic");
      Targeting targeting = seedDynamicGroupTargeting(tenantId);
      authenticateAsServiceAccountOf(tenantId);

      // Act & Assert
      mvc.perform(
              get(EXECUTABLE_PAYLOAD_URI, tenantId, targeting.injectId(), targeting.agentId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("The gate still refuses what it exists for")
  class GateRefusals {

    @Test
    @DisplayName("given an agent the inject does not target should return forbidden")
    void given_agentNotTargetedByInject_should_returnForbidden() throws Exception {
      // Arrange
      String tenantId = seedTenant("exec-payload-stranger");
      Targeting targeting = seedUntargetedAgent(tenantId);
      authenticateAsServiceAccountOf(tenantId);

      // Act & Assert
      mvc.perform(
              get(EXECUTABLE_PAYLOAD_URI, tenantId, targeting.injectId(), targeting.agentId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given an agent of another tenant should return forbidden on this tenant's inject")
    void given_agentOfAnotherTenant_should_returnForbidden() throws Exception {
      // Arrange - tenant A owns the inject, tenant B owns the requesting agent
      String tenantA = seedTenant("exec-payload-cross-a");
      Targeting targetingA = seedDirectTargeting(tenantA);
      String tenantB = seedTenant("exec-payload-cross-b");
      Targeting targetingB = seedDirectTargeting(tenantB);
      authenticateAsServiceAccountOf(tenantB);

      // Act & Assert - B's own scope, B's own agent, A's inject
      mvc.perform(
              get(EXECUTABLE_PAYLOAD_URI, tenantB, targetingA.injectId(), targetingB.agentId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
        "given another tenant's service account addressing this tenant should return forbidden")
    void given_anotherTenantsServiceAccount_should_returnForbidden_onThisTenantsRoute()
        throws Exception {
      // Arrange - tenant A owns the inject and the agent; the caller is tenant B's account
      String tenantA = seedTenant("exec-payload-foreign-caller-a");
      Targeting targetingA = seedDirectTargeting(tenantA);
      String tenantB = seedTenant("exec-payload-foreign-caller-b");
      authenticateAsServiceAccountOf(tenantB);

      // Act & Assert - B is not a member of A, so the scope is refused before the gate
      mvc.perform(
              get(EXECUTABLE_PAYLOAD_URI, tenantA, targetingA.injectId(), targetingA.agentId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given a target owned by another tenant should return forbidden")
    void given_targetOwnedByAnotherTenant_should_returnForbidden() throws Exception {
      // Arrange - a malformed graph on purpose: the inject belongs to A and names an asset of B
      String tenantB = seedTenant("exec-payload-foreign-asset-b");
      EndpointComposer.Composer foreignEndpoint = endpointWithAgent(tenantB, "foreign");
      foreignEndpoint.persist();
      String foreignAssetId = foreignEndpoint.get().getId();
      String foreignAgentId = foreignEndpoint.get().getAgents().getFirst().getId();
      String tenantA = seedTenant("exec-payload-foreign-asset-a");
      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(implantContract(tenantA))
              .withInjectStatus(
                  injectStatusComposer.forInjectStatus(
                      InjectStatusFixture.createPendingInjectStatus()))
              .persist()
              .get();
      // The association is written directly: re-saving the foreign endpoint through the composer
      // would be an update the active-table scope refuses, and the row is what matters here.
      entityManager
          .createNativeQuery("INSERT INTO injects_assets (inject_id, asset_id) VALUES (:i, :a)")
          .setParameter("i", inject.getId())
          .setParameter("a", foreignAssetId)
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();
      authenticateAsServiceAccountOf(tenantA);

      // Act & Assert - under A's scope the foreign asset is not in the inject's target list
      mvc.perform(
              get(EXECUTABLE_PAYLOAD_URI, tenantA, inject.getId(), foreignAgentId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  // region helpers

  private record Targeting(String injectId, String agentId) {}

  private String seedTenant(String name) throws Exception {
    String tenantId =
        tenantHelper.createTenantWithCurrentUser(name + "-" + UUID.randomUUID()).getId();
    serviceAccountPrivilegeService.ensurePrivilegedUserExists(tenantId);
    tenantHelper.switchToTenant(tenantId, entityManager);
    return tenantId;
  }

  /**
   * Authenticates the request as the tenant's own service account, the identity the installer
   * writes onto every endpoint host. Its authorized set is exactly that one tenant, so the request
   * scope on the prefixed route is {@code Restricted({tenantId})}.
   */
  private void authenticateAsServiceAccountOf(String tenantId) {
    entityManager.flush();
    entityManager.clear();
    User serviceAccount =
        serviceAccountPrivilegeService
            .getUserServiceAccountByTenant(tenantId)
            .orElseThrow(
                () -> new IllegalStateException("no service account provisioned for " + tenantId));
    tenantMembershipCacheManager.evict(serviceAccount.getId(), tenantId);
    SecurityContextHolder.getContext().setAuthentication(buildAuthenticationToken(serviceAccount));
  }

  /** An inject whose targets list the agent's endpoint directly. */
  private Targeting seedDirectTargeting(String tenantId) {
    EndpointComposer.Composer endpoint = endpointWithAgent(tenantId, "direct");
    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(implantContract(tenantId))
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .withEndpoint(endpoint)
            .persist()
            .get();
    return new Targeting(inject.getId(), endpoint.get().getAgents().getFirst().getId());
  }

  /** An inject that targets an asset group the agent's endpoint is a static member of. */
  private Targeting seedStaticGroupTargeting(String tenantId) {
    EndpointComposer.Composer endpoint = endpointWithAgent(tenantId, "static");
    AssetGroupComposer.Composer group =
        assetGroupComposer
            .forAssetGroup(
                AssetGroupFixture.createDefaultAssetGroup("static-group-" + UUID.randomUUID()))
            .withAsset(endpoint);
    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(implantContract(tenantId))
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .withAssetGroup(group)
            .persist()
            .get();
    return new Targeting(inject.getId(), endpoint.get().getAgents().getFirst().getId());
  }

  /**
   * An inject that targets an asset group with no static member, whose dynamic filter matches the
   * agent's endpoint by name. Membership is resolved by running the filter as a query over {@code
   * assets}, which is the shape the gate evaluates differently from a row lookup.
   */
  private Targeting seedDynamicGroupTargeting(String tenantId) {
    String marker = "dynamic-" + UUID.randomUUID();
    EndpointComposer.Composer endpoint = endpointWithAgent(tenantId, marker);
    endpoint.persist();

    Filters.Filter filter = new Filters.Filter();
    filter.setKey("asset_name");
    filter.setOperator(Filters.FilterOperator.contains);
    filter.setValues(List.of(marker));
    Filters.FilterGroup dynamicFilter = new Filters.FilterGroup();
    dynamicFilter.setMode(Filters.FilterMode.and);
    dynamicFilter.setFilters(new ArrayList<>(List.of(filter)));

    AssetGroup assetGroup =
        AssetGroupFixture.createAssetGroupWithDynamicFilter("group-" + marker, dynamicFilter);
    assetGroup.setTenant(new Tenant(tenantId));
    AssetGroupComposer.Composer group = assetGroupComposer.forAssetGroup(assetGroup);
    // No static member: the only way the gate can see the endpoint is the dynamic resolution.
    assertThat(assetGroup.getAssets()).isEmpty();

    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(implantContract(tenantId))
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .withAssetGroup(group)
            .persist()
            .get();
    return new Targeting(inject.getId(), endpoint.get().getAgents().getFirst().getId());
  }

  /** An inject of the same tenant that targets another endpoint than the requesting agent's. */
  private Targeting seedUntargetedAgent(String tenantId) {
    EndpointComposer.Composer stranger = endpointWithAgent(tenantId, "stranger");
    stranger.persist();
    EndpointComposer.Composer targeted = endpointWithAgent(tenantId, "targeted");
    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(implantContract(tenantId))
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .withEndpoint(targeted)
            .persist()
            .get();
    return new Targeting(inject.getId(), stranger.get().getAgents().getFirst().getId());
  }

  private EndpointComposer.Composer endpointWithAgent(String tenantId, String nameHint) {
    Endpoint endpoint = EndpointFixture.createEndpoint(nameHint + "-" + UUID.randomUUID());
    endpoint.setTenant(new Tenant(tenantId));
    return endpointComposer
        .forEndpoint(endpoint)
        .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()));
  }

  private InjectorContractComposer.Composer implantContract(String tenantId) {
    Domain domain = DomainFixture.getRandomDomain();
    domain.setTenant(new Tenant(tenantId));
    return injectorContractComposer
        .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
        .withDomain(domainComposer.forDomain(domain))
        .withInjector(InjectorFixture.createDefaultPayloadInjector())
        .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()));
  }

  // endregion
}
