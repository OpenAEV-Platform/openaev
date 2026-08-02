package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.Tenant;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.injectexpectation.InjectExpectationHandler;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.CollectorComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression tests for the expectation indexing query under multi-tenancy v2 filtering, with the
 * {@code collectors} table activated as in production.
 *
 * <p>The default test profile activates no table, so the whole indexing pipeline runs with the
 * tenant statement inspector inert — which is exactly how the production regression slipped
 * through: with {@code collectors} active, {@code findForIndexing}'s collector joins are wrapped in
 * {@code can_access_tenant}, and the (then scope-less) engine sync sweep silently indexed every
 * collector-sourced security platform attribution as empty. Asset-sourced attributions (e.g. a
 * scanner registered as a security platform asset) kept working, which is why only collector-backed
 * platforms (EDRs such as Microsoft Defender) went dark.
 *
 * <p>These tests pin the two halves of the fix: the sweep must run under an explicit tenant scope
 * (the fail-closed test), and the collector joins must be tenant-correlated because built-in
 * collectors share the same {@code collector_id} across tenants (the cross-tenant test).
 */
@Transactional
@WithMockUser
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=collectors")
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("findForIndexing under tenant filtering (collectors active)")
class IndexingTenantScopeRegressionTest extends IntegrationTest {

  @Autowired private InjectExpectationHandler injectExpectationHandler;

  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;

  /** A point in time used as the {@code :from} parameter — 1 hour ago. */
  private static final Instant FROM = Instant.now().minus(1, ChronoUnit.HOURS);

  @BeforeEach
  void setUp() {
    assetGroupComposer.reset();
    collectorComposer.reset();
    endpointComposer.reset();
    injectComposer.reset();
    injectExpectationComposer.reset();
    scenarioComposer.reset();
    securityPlatformComposer.reset();
  }

  /**
   * Sets the transaction-local tenant scope, simulating what the tenant-scoped transaction
   * primitive does when the engine sync job opens its {@code allTenants()} sweep (the intention is
   * resolved into an explicit tenant list).
   */
  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /**
   * Persists an agentless detection expectation (under scenario/inject/endpoint) carrying a
   * collector-sourced result, and returns its id. The collector itself is created by the caller.
   */
  private String persistExpectationFilledByCollector(Collector collector) {
    EndpointComposer.Composer endpointWrapper =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
    endpointWrapper.persist();

    BaseInjectExpectation expectation =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    expectation.setResults(
        List.of(
            InjectExpectationResult.builder()
                .sourceId(collector.getId())
                .sourceType("collector")
                .sourceName(collector.getName())
                .result("detected")
                .score(100.0)
                .build()));
    InjectExpectationComposer.Composer expectationWrapper =
        injectExpectationComposer.forExpectation(expectation).withEndpoint(endpointWrapper);

    InjectComposer.Composer injectWrapper =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withExpectation(expectationWrapper);
    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .withInject(injectWrapper)
        .persist();
    entityManager.flush();
    entityManager.clear();
    return expectation.getId();
  }

  private EsInjectExpectation fetchDoc(String expectationId) {
    return injectExpectationHandler.fetch(FROM, 5000).stream()
        .filter(es -> es.getBase_id().equals(expectationId))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("a scoped sweep attributes the collector's security platform")
  void given_collectorFilledExpectation_when_sweepIsScoped_should_attributeSecurityPlatform() {
    setScope(Tenant.DEFAULT_TENANT_UUID);

    SecurityPlatformComposer.Composer securityPlatform =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR scoped", "EDR"))
            .persist();
    Collector collector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("collector-edr-scoped"))
            .withSecurityPlatform(securityPlatform)
            .persist()
            .get();

    String expectationId = persistExpectationFilledByCollector(collector);

    assertThat(fetchDoc(expectationId).getBase_security_platforms_side())
        .contains(securityPlatform.get().getId());
  }

  @Test
  @DisplayName("a scope-less sweep silently loses the attribution (the production incident shape)")
  void given_collectorFilledExpectation_when_sweepIsNotScoped_should_loseAttribution() {
    // Arrange with a scope so the composers can write the collector row...
    setScope(Tenant.DEFAULT_TENANT_UUID);
    SecurityPlatformComposer.Composer securityPlatform =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR unscoped", "EDR"))
            .persist();
    Collector collector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("collector-edr-unscoped"))
            .withSecurityPlatform(securityPlatform)
            .persist()
            .get();
    String expectationId = persistExpectationFilledByCollector(collector);

    // ...then drop the scope, as the engine sync sweep did before it carried allTenants():
    // can_access_tenant is fail-closed, the collectors join reads empty, the document indexes
    // with no security platform. This is the exact production incident shape.
    setScope("");

    assertThat(fetchDoc(expectationId).getBase_security_platforms_side()).isEmpty();
  }

  @Test
  @DisplayName("an all-tenant sweep does not attribute another tenant's security platform")
  void given_sameCollectorIdInAnotherTenant_should_notAttributeForeignSecurityPlatform() {
    String tenantB = UUID.randomUUID().toString();
    // The sweep sees every tenant at once (resolved allTenants() scope).
    setScope(Tenant.DEFAULT_TENANT_UUID + "," + tenantB);

    SecurityPlatformComposer.Composer platformA =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR tenant A", "EDR"))
            .persist();
    SecurityPlatformComposer.Composer platformB =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR tenant B", "EDR"))
            .persist();
    Collector collector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("collector-edr-shared"))
            .withSecurityPlatform(platformA)
            .persist()
            .get();
    // Built-in collectors share the same collector_id across tenants (composite PK
    // (collector_id, tenant_id)): create the same collector id under another tenant, pointing at
    // that tenant's own security platform.
    entityManager
        .createNativeQuery("INSERT INTO tenants (tenant_id, tenant_name) VALUES (:id, :name)")
        .setParameter("id", tenantB)
        .setParameter("name", "tenant-b-" + tenantB)
        .executeUpdate();
    entityManager
        .createNativeQuery(
            "INSERT INTO collectors (collector_id, tenant_id, collector_name, collector_type,"
                + " collector_period, collector_external, collector_security_platform)"
                + " VALUES (:id, :tenant, :name, :type, 60, true, :platform)")
        .setParameter("id", collector.getId())
        .setParameter("tenant", tenantB)
        .setParameter("name", collector.getName())
        .setParameter("type", "collector-edr-shared-tenant-b")
        .setParameter("platform", platformB.get().getId())
        .executeUpdate();

    String expectationId = persistExpectationFilledByCollector(collector);

    // The expectation belongs to the default tenant: only that tenant's collector row may
    // attribute its platform, even though the other tenant's row shares the collector id and is
    // visible to the all-tenant sweep.
    assertThat(fetchDoc(expectationId).getBase_security_platforms_side())
        .contains(platformA.get().getId())
        .doesNotContain(platformB.get().getId());
  }

  /**
   * Agent-level children contribute their collector-sourced results to the parent (agentless)
   * document through the {@code agent_security_platforms} CTE, whose collectors join must be
   * tenant-correlated exactly like the {@code sp_self} one.
   */
  @Test
  @DisplayName("agent-level child results are attributed through the same tenant-correlated join")
  void given_agentChildFilledByCollector_when_sweepIsScoped_should_attributeSecurityPlatform() {
    setScope(Tenant.DEFAULT_TENANT_UUID);

    SecurityPlatformComposer.Composer securityPlatform =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR child", "EDR"))
            .persist();
    Collector collector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("collector-edr-child"))
            .withSecurityPlatform(securityPlatform)
            .persist()
            .get();

    EndpointComposer.Composer endpointWrapper =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
    endpointWrapper.persist();

    BaseInjectExpectation agentlessExpectation =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    InjectExpectationComposer.Composer agentlessExpectationWrapper =
        injectExpectationComposer
            .forExpectation(agentlessExpectation)
            .withEndpoint(endpointWrapper);

    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setAsset(endpointWrapper.get());
    entityManager.persist(agent);
    entityManager.flush();
    Agent persistedAgent = entityManager.getReference(Agent.class, agent.getId());

    DetectionInjectExpectation agentExpectation =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    agentExpectation.setAgent(persistedAgent);
    agentExpectation.setResults(
        List.of(
            InjectExpectationResult.builder()
                .sourceId(collector.getId())
                .sourceType("collector")
                .sourceName(collector.getName())
                .result("detected")
                .score(100.0)
                .build()));
    InjectExpectationComposer.Composer agentExpectationWrapper =
        injectExpectationComposer.forExpectation(agentExpectation).withEndpoint(endpointWrapper);

    InjectComposer.Composer injectWrapper =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withExpectation(agentlessExpectationWrapper)
            .withExpectation(agentExpectationWrapper);
    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .withInject(injectWrapper)
        .persist();
    entityManager.flush();
    entityManager.clear();

    assertThat(fetchDoc(agentlessExpectation.getId()).getBase_security_platforms_side())
        .contains(securityPlatform.get().getId());
  }

  /**
   * Asset-group parent documents roll their agent-level children's platforms up too, but only the
   * children of THEIR OWN group: an inject targeting two asset groups must not cross-attribute one
   * group's platforms onto the other group's synthesis document.
   */
  @Test
  @DisplayName("asset-group docs roll up their own group's children platforms only")
  void given_assetGroupExpectation_should_attributeOwnGroupChildrenPlatformsOnly() {
    setScope(Tenant.DEFAULT_TENANT_UUID);

    SecurityPlatformComposer.Composer platformOwnGroup =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR own group", "EDR"))
            .persist();
    SecurityPlatformComposer.Composer platformOtherGroup =
        securityPlatformComposer
            .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR other group", "EDR"))
            .persist();
    Collector ownCollector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("collector-edr-own-group"))
            .withSecurityPlatform(platformOwnGroup)
            .persist()
            .get();
    Collector otherCollector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("collector-edr-other-group"))
            .withSecurityPlatform(platformOtherGroup)
            .persist()
            .get();

    AssetGroupComposer.Composer ownGroup =
        assetGroupComposer.forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("own-group"));
    AssetGroupComposer.Composer otherGroup =
        assetGroupComposer.forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("other-group"));

    // The group-level synthesis expectation of the OWN group (no asset, no agent).
    BaseInjectExpectation groupExpectation =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    InjectExpectationComposer.Composer groupExpectationWrapper =
        injectExpectationComposer.forExpectation(groupExpectation).withAssetGroup(ownGroup);

    InjectComposer.Composer injectWrapper =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withExpectation(groupExpectationWrapper)
            .withExpectation(agentChildExpectation(ownCollector, ownGroup, "endpoint-own-group"))
            .withExpectation(
                agentChildExpectation(otherCollector, otherGroup, "endpoint-other-group"));
    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .withInject(injectWrapper)
        .persist();
    entityManager.flush();
    entityManager.clear();

    assertThat(fetchDoc(groupExpectation.getId()).getBase_security_platforms_side())
        .contains(platformOwnGroup.get().getId())
        .doesNotContain(platformOtherGroup.get().getId());
  }

  /**
   * Builds an agent-level detection expectation bound to the given asset group and filled by the
   * given collector, on a fresh endpoint carrying a fresh agent.
   */
  private InjectExpectationComposer.Composer agentChildExpectation(
      Collector collector, AssetGroupComposer.Composer group, String endpointKey) {
    EndpointComposer.Composer endpointWrapper =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
    endpointWrapper.persist();

    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setAsset(endpointWrapper.get());
    entityManager.persist(agent);
    entityManager.flush();
    Agent persistedAgent = entityManager.getReference(Agent.class, agent.getId());

    DetectionInjectExpectation agentExpectation =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    agentExpectation.setAgent(persistedAgent);
    agentExpectation.setResults(
        List.of(
            InjectExpectationResult.builder()
                .sourceId(collector.getId())
                .sourceType("collector")
                .sourceName(collector.getName())
                .result("detected")
                .score(100.0)
                .build()));
    return injectExpectationComposer
        .forExpectation(agentExpectation)
        .withEndpoint(endpointWrapper)
        .withAssetGroup(group);
  }
}
