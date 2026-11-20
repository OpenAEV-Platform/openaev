package io.openaev.api.graph;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS.FAILED;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static io.openaev.utils.fixtures.InjectExpectationFixture.createExpectationWithTypeAndStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.graph.dto.GraphEdgeOutput;
import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeOutput;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.api.graph.dto.GraphOverviewGroupOutput;
import io.openaev.api.graph.dto.GraphOverviewOutput;
import io.openaev.api.graph.dto.GraphResult;
import io.openaev.api.graph.dto.GraphStartingPointOutput;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exploration graph (#9388) over a real seed: every relation the service expands (asset, asset
 * group, finding, inject, simulation, scenario), the landing view, the path search, the
 * per-relation caps and the resource gate of simulations, scenarios and atomic testings.
 *
 * <p>Seed, in the ambient tenant: endpoint {@code e1} in group {@code g}; simulation {@code x} of
 * scenario {@code s} holds inject {@code i1}, which failed a prevention expectation on {@code e1},
 * passed a detection expectation on {@code g} and produced findings {@code f1} and {@code f2} on
 * {@code e1}; atomic testing {@code i2} failed on endpoint {@code e2}.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Exploration graph: relations, landing view, path and gate over a real seed")
class GraphExplorationServiceTest extends IntegrationTest {

  private static final String NEIGHBOURHOOD = TENANT_PREFIX + "/graph/{type}/{id}/neighbourhood";

  @Autowired private GraphExplorationService service;
  @Autowired private GraphLandingService landing;
  @Autowired private GraphLandingCache landingCache;
  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer expectationComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  private String tenant;
  private List<String> scope;
  private Endpoint e1;
  private Endpoint e2;
  private AssetGroup g;
  private Finding f1;
  private Finding f2;
  private Inject i1;
  private Inject i2;
  private Exercise x;
  private Scenario s;

  @BeforeEach
  void seed() {
    endpointComposer.reset();
    assetGroupComposer.reset();
    findingComposer.reset();
    injectComposer.reset();
    expectationComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
    landingCache.clear();
    tenant = TenantContext.getCurrentTenant();
    scope = List.of(tenant);
    tenantHelper.attachCurrentUserToTenant(tenant);

    EndpointComposer.Composer ep1 = endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
    EndpointComposer.Composer ep2 = endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
    AssetGroupComposer.Composer group =
        assetGroupComposer
            .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("graph-cov-group"))
            .withAsset(ep1);
    group.persist();

    FindingComposer.Composer finding1 =
        findingComposer
            .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
            .withEndpoint(ep1);
    FindingComposer.Composer finding2 =
        findingComposer
            .forFinding(FindingFixture.createDefaultTextFindingWithRandomValue())
            .withEndpoint(ep1);
    InjectComposer.Composer inject1 =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withExpectation(
                expectationComposer
                    .forExpectation(createExpectationWithTypeAndStatus(PREVENTION, FAILED))
                    .withEndpoint(ep1))
            .withExpectation(
                expectationComposer
                    .forExpectation(createExpectationWithTypeAndStatus(DETECTION, SUCCESS))
                    .withAssetGroup(group))
            .withFinding(finding1)
            .withFinding(finding2);
    ExerciseComposer.Composer simulation =
        exerciseComposer
            .forExercise(ExerciseFixture.createFinishedAttackExercise())
            .withInject(inject1);
    ScenarioComposer.Composer scenario =
        scenarioComposer.forScenario(ScenarioFixture.getScenario()).withSimulation(simulation);
    scenario.persist();

    InjectComposer.Composer inject2 =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withExpectation(
                expectationComposer
                    .forExpectation(createExpectationWithTypeAndStatus(PREVENTION, FAILED))
                    .withEndpoint(ep2));
    inject2.persist();

    e1 = ep1.get();
    e2 = ep2.get();
    g = group.get();
    f1 = finding1.get();
    f2 = finding2.get();
    i1 = inject1.get();
    i2 = inject2.get();
    x = simulation.get();
    s = scenario.get();
    entityManager.flush();
    entityManager.clear();
  }

  /** Scopes the test's own transaction for the direct service reads (no HTTP handler here). */
  private void scopeToAmbientTenant() {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenant));
  }

  private static String id(GraphNodeType type, String entityId) {
    return GraphNodeOutput.idOf(type, entityId);
  }

  private static List<String> nodeIds(GraphOutput out) {
    return out.nodes().stream().map(GraphNodeOutput::nodeId).toList();
  }

  private static List<GraphEdgeType> edgeTypes(GraphOutput out) {
    return out.edges().stream().map(GraphEdgeOutput::edgeType).toList();
  }

  private GraphOutput ring(GraphNodeType type, String entityId) {
    return service.neighbourhood(scope, type, entityId, Set.of(), 50, GraphVisibility.ALL);
  }

  // -- Relations, one entity type at a time --

  @Test
  @DisplayName("an asset expands to its group, its findings and the injects that targeted it")
  void assetRing() {
    scopeToAmbientTenant();
    GraphOutput out = ring(GraphNodeType.ASSET, e1.getId());

    assertThat(out.focus()).isEqualTo(id(GraphNodeType.ASSET, e1.getId()));
    assertThat(nodeIds(out))
        .contains(
            id(GraphNodeType.ASSET_GROUP, g.getId()),
            id(GraphNodeType.FINDING, f1.getId()),
            id(GraphNodeType.FINDING, f2.getId()),
            id(GraphNodeType.INJECT, i1.getId()));
    assertThat(edgeTypes(out))
        .contains(GraphEdgeType.MEMBER_OF, GraphEdgeType.FOUND_ON, GraphEdgeType.TARGETS);
    assertThat(out.edges())
        .filteredOn(e -> e.edgeType() == GraphEdgeType.TARGETS)
        .allSatisfy(e -> assertThat(e.result()).isEqualTo(GraphResult.FAILED));
    assertThat(out.truncated()).isFalse();
  }

  @Test
  @DisplayName("an asset ring narrowed to one relation and capped at 1 is truncated")
  void assetRingNarrowedAndCapped() {
    scopeToAmbientTenant();
    GraphOutput out =
        service.neighbourhood(
            scope,
            GraphNodeType.ASSET,
            e1.getId(),
            Set.of(GraphEdgeType.FOUND_ON),
            1,
            GraphVisibility.ALL);

    assertThat(edgeTypes(out)).containsOnly(GraphEdgeType.FOUND_ON);
    assertThat(out.edges()).hasSize(1);
    assertThat(out.truncated()).isTrue();
  }

  // -- Dynamic asset groups of #9447: membership resolved through the filter, both ways --

  private AssetGroup dynamicGroupOnPlatformOf(Endpoint endpoint) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("endpoint_platform");
    filter.setMode(Filters.FilterMode.and);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(List.of(endpoint.getPlatform().name()));
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(List.of(filter));
    AssetGroupComposer.Composer dynamic =
        assetGroupComposer.forAssetGroup(
            AssetGroupFixture.createAssetGroupWithDynamicFilter("graph-dyn-group", filterGroup));
    dynamic.persist();
    AssetGroup persisted = dynamic.get();
    entityManager.flush();
    entityManager.clear();
    return persisted;
  }

  @Test
  @DisplayName("an asset matched by a dynamic group filter expands to that group")
  void assetRingResolvesDynamicGroups() {
    AssetGroup dyn = dynamicGroupOnPlatformOf(e2);
    scopeToAmbientTenant();

    GraphOutput out = ring(GraphNodeType.ASSET, e2.getId());

    String groupNode = id(GraphNodeType.ASSET_GROUP, dyn.getId());
    assertThat(nodeIds(out)).contains(groupNode);
    assertThat(out.edges())
        .anySatisfy(
            e -> {
              assertThat(e.edgeType()).isEqualTo(GraphEdgeType.MEMBER_OF);
              assertThat(List.of(e.source(), e.target())).contains(groupNode);
            });
    assertThat(nodeIds(out)).doesNotContain(id(GraphNodeType.ASSET_GROUP, g.getId()));
  }

  @Test
  @DisplayName("a dynamic group expands to the assets its filter matches, static ones kept")
  void dynamicGroupRingListsMatchedAssets() {
    AssetGroup dyn = dynamicGroupOnPlatformOf(e2);
    scopeToAmbientTenant();

    GraphOutput out = ring(GraphNodeType.ASSET_GROUP, dyn.getId());

    assertThat(nodeIds(out)).contains(id(GraphNodeType.ASSET, e2.getId()));
    assertThat(edgeTypes(out)).contains(GraphEdgeType.MEMBER_OF);
  }

  @Test
  @DisplayName("a dynamic group ring is capped and says it was truncated")
  void dynamicGroupRingCapped() {
    AssetGroup dyn = dynamicGroupOnPlatformOf(e2);
    scopeToAmbientTenant();

    GraphOutput out =
        service.neighbourhood(
            scope,
            GraphNodeType.ASSET_GROUP,
            dyn.getId(),
            Set.of(GraphEdgeType.MEMBER_OF),
            1,
            GraphVisibility.ALL);

    assertThat(out.edges()).hasSize(1);
    assertThat(out.truncated()).isTrue();
  }

  // -- Links of #9447: assets to assets, assets to persons --

  private void linkAssets(String assetId, String linkedAssetId, String type) {
    entityManager
        .createNativeQuery(
            "INSERT INTO assets_links (asset_id, linked_asset_id, asset_link_type)"
                + " VALUES (:a, :b, :t)")
        .setParameter("a", assetId)
        .setParameter("b", linkedAssetId)
        .setParameter("t", type)
        .executeUpdate();
  }

  private void linkPerson(String assetId, String userId) {
    entityManager
        .createNativeQuery("UPDATE assets SET asset_linked_person = :u WHERE asset_id = :a")
        .setParameter("u", userId)
        .setParameter("a", assetId)
        .executeUpdate();
  }

  @Test
  @DisplayName(
      "a link between two assets is a LINKED_TO edge seen from both ends, in its direction")
  void linkedAssetsBothWays() {
    scopeToAmbientTenant();
    linkAssets(e2.getId(), e1.getId(), "RUNS_ON");
    linkAssets(e2.getId(), e1.getId(), "DEPENDS_ON");
    String from = id(GraphNodeType.ASSET, e2.getId());
    String to = id(GraphNodeType.ASSET, e1.getId());

    for (String focus : List.of(e1.getId(), e2.getId())) {
      GraphOutput out =
          service.neighbourhood(
              scope,
              GraphNodeType.ASSET,
              focus,
              Set.of(GraphEdgeType.LINKED_TO),
              50,
              GraphVisibility.ALL);

      assertThat(nodeIds(out)).contains(from, to);
      assertThat(out.edges())
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e.edgeType()).isEqualTo(GraphEdgeType.LINKED_TO);
                assertThat(e.source()).isEqualTo(from);
                assertThat(e.target()).isEqualTo(to);
              });
    }
  }

  @Test
  @DisplayName("the identity linked to an asset is a person node, dropped without the people right")
  void identityOfIsGatedByPeopleRight() {
    scopeToAmbientTenant();
    linkPerson(e1.getId(), testUserHolder.get().getId());
    String person = id(GraphNodeType.PERSON, testUserHolder.get().getId());

    GraphOutput visible =
        service.neighbourhood(
            scope,
            GraphNodeType.ASSET,
            e1.getId(),
            Set.of(GraphEdgeType.IDENTITY_OF),
            50,
            GraphVisibility.ALL);
    GraphOutput hidden =
        service.neighbourhood(
            scope,
            GraphNodeType.ASSET,
            e1.getId(),
            Set.of(GraphEdgeType.IDENTITY_OF),
            50,
            noPeople());

    assertThat(nodeIds(visible)).contains(person);
    assertThat(edgeTypes(visible)).containsExactly(GraphEdgeType.IDENTITY_OF);
    assertThat(nodeIds(hidden)).doesNotContain(person);
    assertThat(hidden.edges()).isEmpty();
  }

  @Test
  @DisplayName("a person expands back to the assets it is the identity of")
  void personRing() {
    scopeToAmbientTenant();
    linkPerson(e1.getId(), testUserHolder.get().getId());

    GraphOutput out = ring(GraphNodeType.PERSON, testUserHolder.get().getId());

    assertThat(out.focus()).isEqualTo(id(GraphNodeType.PERSON, testUserHolder.get().getId()));
    assertThat(nodeIds(out)).contains(id(GraphNodeType.ASSET, e1.getId()));
    assertThat(edgeTypes(out)).contains(GraphEdgeType.IDENTITY_OF);
  }

  private void insertStep(String sourceId, String targetId, String prevention) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
                + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
                + " attackpath_execution_source_asset_id, attackpath_execution_target_kind,"
                + " attackpath_execution_target_asset_id, attackpath_execution_target_key,"
                + " attackpath_execution_executed_at, attackpath_execution_row_version,"
                + " attackpath_execution_prevention_status)"
                + " VALUES (:id, :tenant, :sim, 'AGENT', :src, 'ASSET', :tgt, :tgt, now(), 0, :p)")
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("tenant", tenant)
        .setParameter("sim", x.getId())
        .setParameter("src", sourceId)
        .setParameter("tgt", targetId)
        .setParameter("p", prevention)
        .executeUpdate();
  }

  @Test
  @DisplayName(
      "an attack-path step is an ATTACK_STEP edge from both assets and from its simulation")
  void attackStepsFromAssetAndSimulation() {
    scopeToAmbientTenant();
    insertStep(e1.getId(), e2.getId(), "Not Prevented");
    String from = id(GraphNodeType.ASSET, e1.getId());
    String to = id(GraphNodeType.ASSET, e2.getId());

    for (String focus : List.of(e1.getId(), e2.getId())) {
      GraphOutput out =
          service.neighbourhood(
              scope,
              GraphNodeType.ASSET,
              focus,
              Set.of(GraphEdgeType.ATTACK_STEP),
              50,
              GraphVisibility.ALL);
      assertThat(out.edges())
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e.edgeType()).isEqualTo(GraphEdgeType.ATTACK_STEP);
                assertThat(e.source()).isEqualTo(from);
                assertThat(e.target()).isEqualTo(to);
              });
    }

    GraphOutput simulation =
        service.neighbourhood(
            scope,
            GraphNodeType.SIMULATION,
            x.getId(),
            Set.of(GraphEdgeType.ATTACK_STEP),
            50,
            GraphVisibility.ALL);
    assertThat(nodeIds(simulation)).contains(from, to);
    assertThat(edgeTypes(simulation)).contains(GraphEdgeType.ATTACK_STEP);
  }

  @Test
  @DisplayName("a step drawn only by a simulation the caller cannot read is dropped")
  void attackStepGatedBySimulation() {
    scopeToAmbientTenant();
    insertStep(e1.getId(), e2.getId(), "Prevented");

    GraphOutput hidden =
        service.neighbourhood(
            scope,
            GraphNodeType.ASSET,
            e1.getId(),
            Set.of(GraphEdgeType.ATTACK_STEP),
            50,
            visibility(false, true, true));

    assertThat(hidden.edges()).isEmpty();
  }

  @Test
  @DisplayName("asset links and steps are invisible from a tenant scope that does not hold them")
  void linksAndStepsStayInTheirTenant() {
    scopeToAmbientTenant();
    linkAssets(e2.getId(), e1.getId(), "RUNS_ON");
    insertStep(e1.getId(), e2.getId(), "Not Prevented");
    List<String> otherTenant = List.of(UUID.randomUUID().toString());

    GraphOutput out =
        service.neighbourhood(
            otherTenant,
            GraphNodeType.ASSET,
            e1.getId(),
            Set.of(GraphEdgeType.LINKED_TO, GraphEdgeType.ATTACK_STEP),
            50,
            GraphVisibility.ALL);

    assertThat(out.nodes()).isEmpty();
    assertThat(out.edges()).isEmpty();
  }

  @Test
  @DisplayName("the landing ring carries the attack-path steps between hot assets")
  void hotPathsCarryAttackSteps() {
    scopeToAmbientTenant();
    insertStep(e1.getId(), e2.getId(), "Not Prevented");

    GraphOutput out = service.hotPaths(scope, 15, GraphVisibility.ALL);

    assertThat(edgeTypes(out)).contains(GraphEdgeType.ATTACK_STEP);
  }

  private static GraphVisibility noPeople() {
    return new GraphVisibility() {
      @Override
      public boolean canReadSimulation(String simulationId) {
        return true;
      }

      @Override
      public boolean canReadScenario(String scenarioId) {
        return true;
      }

      @Override
      public boolean canReadAtomicTesting(String injectId) {
        return true;
      }

      @Override
      public boolean canReadPeople(GraphNodeType type) {
        return false;
      }
    };
  }

  @Test
  @DisplayName("an asset group expands to its members and the injects that targeted it")
  void assetGroupRing() {
    scopeToAmbientTenant();
    GraphOutput out = ring(GraphNodeType.ASSET_GROUP, g.getId());

    assertThat(out.focus()).isEqualTo(id(GraphNodeType.ASSET_GROUP, g.getId()));
    assertThat(nodeIds(out))
        .contains(id(GraphNodeType.ASSET, e1.getId()), id(GraphNodeType.INJECT, i1.getId()));
    assertThat(out.edges())
        .filteredOn(e -> e.edgeType() == GraphEdgeType.TARGETS)
        .singleElement()
        .satisfies(e -> assertThat(e.result()).isEqualTo(GraphResult.SUCCESS));
  }

  @Test
  @DisplayName("a finding expands to the assets it was found on and the inject that produced it")
  void findingRing() {
    scopeToAmbientTenant();
    GraphOutput out = ring(GraphNodeType.FINDING, f1.getId());

    assertThat(out.focus()).isEqualTo(id(GraphNodeType.FINDING, f1.getId()));
    assertThat(nodeIds(out))
        .contains(id(GraphNodeType.ASSET, e1.getId()), id(GraphNodeType.INJECT, i1.getId()));
    assertThat(edgeTypes(out)).contains(GraphEdgeType.FOUND_ON, GraphEdgeType.PRODUCED);
  }

  @Test
  @DisplayName("an inject expands to its targets, its findings and its simulation")
  void injectRing() {
    scopeToAmbientTenant();
    GraphOutput out = ring(GraphNodeType.INJECT, i1.getId());

    assertThat(out.focus()).isEqualTo(id(GraphNodeType.INJECT, i1.getId()));
    assertThat(nodeIds(out))
        .contains(
            id(GraphNodeType.ASSET, e1.getId()),
            id(GraphNodeType.ASSET_GROUP, g.getId()),
            id(GraphNodeType.FINDING, f1.getId()),
            id(GraphNodeType.FINDING, f2.getId()),
            id(GraphNodeType.SIMULATION, x.getId()));
    assertThat(edgeTypes(out))
        .contains(GraphEdgeType.TARGETS, GraphEdgeType.PRODUCED, GraphEdgeType.PART_OF);
  }

  @Test
  @DisplayName("an inject's targets capped at 1 pair stop at the cap and say so")
  void injectTargetsCapped() {
    scopeToAmbientTenant();
    GraphOutput out =
        service.neighbourhood(
            scope,
            GraphNodeType.INJECT,
            i1.getId(),
            Set.of(GraphEdgeType.TARGETS),
            1,
            GraphVisibility.ALL);

    assertThat(out.edges()).hasSize(1);
    assertThat(out.truncated()).isTrue();
  }

  @Test
  @DisplayName("a simulation expands to its injects and its scenario")
  void simulationRing() {
    scopeToAmbientTenant();
    GraphOutput out = ring(GraphNodeType.SIMULATION, x.getId());

    assertThat(out.focus()).isEqualTo(id(GraphNodeType.SIMULATION, x.getId()));
    assertThat(nodeIds(out))
        .contains(id(GraphNodeType.INJECT, i1.getId()), id(GraphNodeType.SCENARIO, s.getId()));
    assertThat(edgeTypes(out)).contains(GraphEdgeType.PART_OF, GraphEdgeType.RUN_OF);
  }

  @Test
  @DisplayName("a scenario expands to its simulations")
  void scenarioRing() {
    scopeToAmbientTenant();
    GraphOutput out = ring(GraphNodeType.SCENARIO, s.getId());

    assertThat(out.focus()).isEqualTo(id(GraphNodeType.SCENARIO, s.getId()));
    assertThat(nodeIds(out)).contains(id(GraphNodeType.SIMULATION, x.getId()));
    assertThat(edgeTypes(out)).containsOnly(GraphEdgeType.RUN_OF);
  }

  @Test
  @DisplayName("an unknown entity of any type is an empty slice, not an error")
  void unknownEntitiesAreEmpty() {
    scopeToAmbientTenant();
    String unknown = UUID.randomUUID().toString();
    for (GraphNodeType type : GraphNodeType.values()) {
      GraphOutput out = ring(type, unknown);
      assertThat(out.focus()).as(type.name()).isNull();
      assertThat(out.nodes()).as(type.name()).isEmpty();
      assertThat(out.edges()).as(type.name()).isEmpty();
    }
  }

  // -- Resource gate --

  @Test
  @DisplayName("a simulation the caller cannot read is an empty slice, its injects dropped too")
  void hiddenSimulation() {
    scopeToAmbientTenant();
    GraphVisibility noSimulation = visibility(false, true, true);

    GraphOutput focusHidden =
        service.neighbourhood(
            scope, GraphNodeType.SIMULATION, x.getId(), Set.of(), 50, noSimulation);
    assertThat(focusHidden.nodes()).isEmpty();
    assertThat(focusHidden.focus()).isNull();

    GraphOutput fromAsset =
        service.neighbourhood(scope, GraphNodeType.ASSET, e1.getId(), Set.of(), 50, noSimulation);
    assertThat(nodeIds(fromAsset))
        .contains(id(GraphNodeType.ASSET_GROUP, g.getId()))
        .doesNotContain(id(GraphNodeType.INJECT, i1.getId()));
  }

  @Test
  @DisplayName("a scenario or an atomic testing the caller cannot read is dropped with its edges")
  void hiddenScenarioAndAtomicTesting() {
    scopeToAmbientTenant();
    GraphOutput simulation =
        service.neighbourhood(
            scope,
            GraphNodeType.SIMULATION,
            x.getId(),
            Set.of(),
            50,
            visibility(true, false, true));
    assertThat(nodeIds(simulation))
        .contains(id(GraphNodeType.INJECT, i1.getId()))
        .doesNotContain(id(GraphNodeType.SCENARIO, s.getId()));
    assertThat(edgeTypes(simulation)).doesNotContain(GraphEdgeType.RUN_OF);

    GraphOutput atomic =
        service.neighbourhood(
            scope, GraphNodeType.ASSET, e2.getId(), Set.of(), 50, visibility(true, true, false));
    assertThat(nodeIds(atomic)).containsExactly(id(GraphNodeType.ASSET, e2.getId()));
    assertThat(atomic.edges()).isEmpty();
  }

  private static GraphVisibility visibility(
      boolean simulations, boolean scenarios, boolean atomicTestings) {
    return new GraphVisibility() {
      @Override
      public boolean canReadSimulation(String simulationId) {
        return simulations;
      }

      @Override
      public boolean canReadScenario(String scenarioId) {
        return scenarios;
      }

      @Override
      public boolean canReadAtomicTesting(String injectId) {
        return atomicTestings;
      }

      @Override
      public boolean canReadPeople(GraphNodeType type) {
        return true;
      }
    };
  }

  // -- Landing (#9447): story, overview, starting points --

  @Test
  @DisplayName("the story is the worst attack path, one connected subgraph under its budget")
  void storyFollowsWorstAttackPath() {
    scopeToAmbientTenant();
    insertStep(e1.getId(), e2.getId(), "Not Prevented");

    GraphOutput out = landing.story(scope, GraphVisibility.ALL);

    String from = id(GraphNodeType.ASSET, e1.getId());
    assertThat(out.focus()).isEqualTo(from);
    assertThat(nodeIds(out)).contains(from, id(GraphNodeType.ASSET, e2.getId()));
    assertThat(edgeTypes(out)).contains(GraphEdgeType.ATTACK_STEP, GraphEdgeType.FOUND_ON);
    assertThat(out.nodes()).hasSizeLessThanOrEqualTo(GraphLandingService.STORY_BUDGET + 2);
    assertConnected(out);
  }

  @Test
  @DisplayName("without attack path, the story is the most exposed asset and its ring")
  void storyFallsBackToMostExposedAsset() {
    scopeToAmbientTenant();

    GraphOutput out = landing.story(scope, GraphVisibility.ALL);

    assertThat(out.focus()).startsWith(GraphNodeType.ASSET.name() + ":");
    assertThat(edgeTypes(out)).contains(GraphEdgeType.TARGETS);
    assertConnected(out);
  }

  @Test
  @DisplayName("an attack path of a simulation the caller cannot read never becomes the story")
  void storySkipsUnreadableAttackPath() {
    scopeToAmbientTenant();
    insertStep(e1.getId(), e2.getId(), "Not Prevented");

    GraphOutput out = landing.story(scope, visibility(false, true, true));

    assertThat(edgeTypes(out)).doesNotContain(GraphEdgeType.ATTACK_STEP);
    assertThat(nodeIds(out)).doesNotContain(id(GraphNodeType.SIMULATION, x.getId()));
  }

  @Test
  @DisplayName("the story of a scope with nothing in it is empty")
  void storyEmptyOutsideScope() {
    GraphOutput out = landing.story(List.of(UUID.randomUUID().toString()), GraphVisibility.ALL);

    assertThat(out.nodes()).isEmpty();
    assertThat(out.edges()).isEmpty();
  }

  @Test
  @DisplayName("the overview aggregates groups, the ungrouped bucket and the simulations")
  void overviewAggregatesGroups() {
    scopeToAmbientTenant();

    GraphOverviewOutput out = landing.overview(scope, GraphVisibility.ALL);

    assertThat(out.assetCount()).isGreaterThanOrEqualTo(2);
    assertThat(out.groups())
        .anySatisfy(
            group -> {
              assertThat(group.groupId()).isEqualTo(g.getId());
              assertThat(group.assetCount()).isEqualTo(1);
              assertThat(group.scoredCount()).isEqualTo(1);
              assertThat(group.failedCount()).isEqualTo(1);
              assertThat(group.failureRate()).isEqualTo(1.0);
              assertThat(group.ungrouped()).isFalse();
            })
        .anySatisfy(
            group -> {
              assertThat(group.ungrouped()).isTrue();
              assertThat(group.assetCount()).isGreaterThanOrEqualTo(1);
              assertThat(group.failedCount()).isGreaterThanOrEqualTo(1);
            });
    assertThat(out.links())
        .anySatisfy(
            link -> {
              assertThat(link.groupId()).isEqualTo(g.getId());
              assertThat(link.simulationId()).isEqualTo(x.getId());
              assertThat(link.result()).isEqualTo(GraphResult.SUCCESS);
            });
  }

  @Test
  @DisplayName("the overview counts the members a dynamic group filter matches, out of ungrouped")
  void overviewCountsDynamicGroupMembers() {
    scopeToAmbientTenant();
    long ungroupedBefore = ungroupedCount(landing.overview(scope, GraphVisibility.ALL));
    AssetGroup dyn = dynamicGroupOnPlatformOf(e2);
    scopeToAmbientTenant();
    landingCache.clear();

    GraphOverviewOutput out = landing.overview(scope, GraphVisibility.ALL);

    assertThat(out.groups())
        .anySatisfy(
            group -> {
              assertThat(group.groupId()).isEqualTo(dyn.getId());
              assertThat(group.dynamic()).isTrue();
              assertThat(group.assetCount()).isGreaterThanOrEqualTo(1);
            });
    assertThat(ungroupedCount(out)).isLessThan(ungroupedBefore);
  }

  private static long ungroupedCount(GraphOverviewOutput out) {
    return out.groups().stream()
        .filter(GraphOverviewGroupOutput::ungrouped)
        .mapToLong(GraphOverviewGroupOutput::assetCount)
        .sum();
  }

  @Test
  @DisplayName("the overview keeps the counts but drops the links to unreadable simulations")
  void overviewGatesSimulationLinks() {
    scopeToAmbientTenant();

    GraphOverviewOutput out = landing.overview(scope, visibility(false, true, true));

    assertThat(out.groups()).anySatisfy(group -> assertThat(group.groupId()).isEqualTo(g.getId()));
    assertThat(out.links()).isEmpty();
  }

  @Test
  @DisplayName("the overview is served from the cache until it is cleared")
  void overviewIsCached() {
    scopeToAmbientTenant();
    GraphOverviewOutput first = landing.overview(scope, GraphVisibility.ALL);
    entityManager
        .createNativeQuery("DELETE FROM asset_groups_assets WHERE asset_group_id = :g")
        .setParameter("g", g.getId())
        .executeUpdate();

    assertThat(landing.overview(scope, GraphVisibility.ALL).groups()).isEqualTo(first.groups());
    landingCache.clear();
    assertThat(landing.overview(scope, GraphVisibility.ALL).groups())
        .anySatisfy(
            group -> {
              assertThat(group.groupId()).isEqualTo(g.getId());
              assertThat(group.assetCount()).isZero();
            });
  }

  @Test
  @DisplayName("the rail ranks exposed assets, findings, simulations and attack paths")
  void startingPoints() {
    scopeToAmbientTenant();
    insertStep(e1.getId(), e2.getId(), "Not Prevented");

    List<GraphStartingPointOutput> points =
        landing.startingPoints(scope, GraphVisibility.ALL).points();

    assertThat(points)
        .anySatisfy(
            p -> {
              assertThat(p.kind()).isEqualTo(GraphStartingPointOutput.Kind.EXPOSED_ASSET);
              assertThat(p.nodeType()).isEqualTo(GraphNodeType.ASSET);
              assertThat(p.score()).isPositive();
            })
        .anySatisfy(
            p -> {
              assertThat(p.kind()).isEqualTo(GraphStartingPointOutput.Kind.ATTACK_PATH);
              assertThat(p.entityId()).isEqualTo(x.getId());
            })
        .anySatisfy(p -> assertThat(p.kind()).isEqualTo(GraphStartingPointOutput.Kind.TOP_FINDING))
        .anySatisfy(
            p -> assertThat(p.kind()).isEqualTo(GraphStartingPointOutput.Kind.FAILING_GROUP))
        .anySatisfy(
            p -> assertThat(p.kind()).isEqualTo(GraphStartingPointOutput.Kind.LATEST_SIMULATION));
    assertThat(points.stream().filter(p -> p.kind() == GraphStartingPointOutput.Kind.TOP_FINDING))
        .hasSizeLessThanOrEqualTo(GraphLandingService.RAIL_SIZE);
  }

  @Test
  @DisplayName("the rail drops attack paths, simulations and persons the caller cannot read")
  void startingPointsGated() {
    scopeToAmbientTenant();
    insertStep(e1.getId(), e2.getId(), "Not Prevented");
    linkPerson(e1.getId(), testUserHolder.get().getId());

    List<GraphStartingPointOutput> points = landing.startingPoints(scope, noPeople()).points();

    assertThat(points)
        .noneSatisfy(
            p -> assertThat(p.kind()).isEqualTo(GraphStartingPointOutput.Kind.EXPOSED_PERSON));
    assertThat(landing.startingPoints(scope, visibility(false, true, true)).points())
        .noneSatisfy(p -> assertThat(p.nodeType()).isEqualTo(GraphNodeType.SIMULATION));
  }

  @Test
  @DisplayName("the rail names the person behind an exposed identity asset")
  void startingPointsExposedPerson() {
    scopeToAmbientTenant();
    String person = testUserHolder.get().getId();
    linkPerson(e1.getId(), person);

    assertThat(landing.startingPoints(scope, GraphVisibility.ALL).points())
        .anySatisfy(
            p -> {
              assertThat(p.kind()).isEqualTo(GraphStartingPointOutput.Kind.EXPOSED_PERSON);
              assertThat(p.entityId()).isEqualTo(person);
            });
  }

  // -- Marking clearance (ADR-009): the landing cache never crosses clearances --

  private void setClearance(String clearance) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_markings', :scope, true)")
        .setParameter("scope", clearance)
        .getSingleResult();
  }

  private List<String> exposedAssetIds() {
    return landing.startingPoints(scope, GraphVisibility.ALL).points().stream()
        .filter(p -> p.kind() == GraphStartingPointOutput.Kind.EXPOSED_ASSET)
        .map(GraphStartingPointOutput::entityId)
        .toList();
  }

  @Test
  @DisplayName(
      "two readers of different marking clearance get different landings, within the cache TTL")
  void landingCacheIsKeyedByClearance() {
    scopeToAmbientTenant();
    String red = "graph-landing-tlp-red-" + UUID.randomUUID();
    // Cleared for the marking first, so the write predicate lets the marking through.
    setClearance(red);
    entityManager
        .createNativeQuery("UPDATE assets SET marking_ids = :m WHERE asset_id = :a")
        .setParameter("m", new String[] {red})
        .setParameter("a", e2.getId())
        .executeUpdate();
    landingCache.clear();

    // A reader cleared for the marking fills the cache with both exposed assets.
    assertThat(exposedAssetIds()).contains(e1.getId(), e2.getId());
    assertThat(landing.clearance()).containsExactly(red);

    // Same tenant, same TTL window: a reader without the marking does not get the cached rows.
    setClearance("");
    assertThat(landing.clearance()).isEmpty();
    assertThat(exposedAssetIds()).contains(e1.getId()).doesNotContain(e2.getId());

    // And back: the cleared reader still sees the marked asset.
    setClearance(red);
    assertThat(exposedAssetIds()).contains(e2.getId());
  }

  @Test
  @DisplayName("a TLP-marked asset drops its asset links and attack-path steps from the slices")
  void markedAssetDropsItsLinksAndSteps() {
    scopeToAmbientTenant();
    String red = "graph-slice-tlp-red-" + UUID.randomUUID();
    setClearance(red);
    linkAssets(e2.getId(), e1.getId(), "RUNS_ON");
    insertStep(e2.getId(), e1.getId(), "Not Prevented");
    entityManager
        .createNativeQuery("UPDATE assets SET marking_ids = :m WHERE asset_id = :a")
        .setParameter("m", new String[] {red})
        .setParameter("a", e2.getId())
        .executeUpdate();
    landingCache.clear();
    String hidden = id(GraphNodeType.ASSET, e2.getId());

    // Cleared: the link and the step to the marked asset are there.
    assertThat(nodeIds(ring(GraphNodeType.ASSET, e1.getId()))).contains(hidden);

    // Not cleared: neither the link nor the step reveals the marked asset, and the attack-path
    // ranking of the landing no longer counts the step.
    setClearance("");
    GraphOutput out = ring(GraphNodeType.ASSET, e1.getId());
    assertThat(nodeIds(out)).doesNotContain(hidden);
    assertThat(out.edges())
        .noneSatisfy(e -> assertThat(List.of(e.source(), e.target())).contains(hidden));
    assertThat(landing.startingPoints(scope, GraphVisibility.ALL).points())
        .noneSatisfy(
            p -> assertThat(p.kind()).isEqualTo(GraphStartingPointOutput.Kind.ATTACK_PATH));
  }

  /** Every node of a story but its focus is reached by at least one edge: never a scatter. */
  private static void assertConnected(GraphOutput out) {
    Set<String> touched = new java.util.HashSet<>();
    out.edges()
        .forEach(
            e -> {
              touched.add(e.source());
              touched.add(e.target());
            });
    out.nodes().forEach(n -> assertThat(touched).contains(n.nodeId()));
  }

  // -- Landing view --

  @Test
  @DisplayName("the landing view holds the assets with failed expectations and their ring")
  void hotPaths() {
    scopeToAmbientTenant();
    GraphOutput out = service.hotPaths(scope, 25, GraphVisibility.ALL);

    assertThat(out.focus()).isNull();
    assertThat(nodeIds(out))
        .contains(
            id(GraphNodeType.ASSET, e1.getId()),
            id(GraphNodeType.ASSET, e2.getId()),
            id(GraphNodeType.ASSET_GROUP, g.getId()),
            id(GraphNodeType.FINDING, f1.getId()),
            id(GraphNodeType.INJECT, i1.getId()),
            id(GraphNodeType.INJECT, i2.getId()));
    assertThat(out.nodes())
        .filteredOn(n -> n.nodeId().equals(id(GraphNodeType.ASSET, e1.getId())))
        .singleElement()
        .satisfies(
            n -> {
              assertThat(n.failedCount()).isPositive();
              assertThat(n.findingCount()).isEqualTo(2);
            });
  }

  @Test
  @DisplayName("the landing view capped at one asset says there is more")
  void hotPathsCapped() {
    scopeToAmbientTenant();
    GraphOutput out = service.hotPaths(scope, 1, GraphVisibility.ALL);

    assertThat(out.truncated()).isTrue();
    assertThat(out.nodes()).filteredOn(n -> n.nodeType() == GraphNodeType.ASSET).isNotEmpty();
  }

  // -- Path --

  @Test
  @DisplayName("the path from an asset to its scenario goes through the inject and the simulation")
  void pathAssetToScenario() {
    scopeToAmbientTenant();
    GraphOutput out =
        service.path(
            scope,
            GraphNodeType.ASSET,
            e1.getId(),
            GraphNodeType.SCENARIO,
            s.getId(),
            GraphVisibility.ALL);

    String start = id(GraphNodeType.ASSET, e1.getId());
    assertThat(out.focus()).isEqualTo(start);
    assertThat(out.path()).first().isEqualTo(start);
    assertThat(out.path()).last().isEqualTo(id(GraphNodeType.SCENARIO, s.getId()));
    assertThat(out.path()).hasSizeGreaterThanOrEqualTo(3);
    assertThat(out.edges()).hasSize(out.path().size() - 1);
    assertThat(out.nodes())
        .filteredOn(n -> n.nodeId().equals(start))
        .singleElement()
        .satisfies(n -> assertThat(n.label()).isEqualTo(e1.getName()));
  }

  @Test
  @DisplayName("the path from a node to itself is that node alone")
  void pathToItself() {
    scopeToAmbientTenant();
    GraphOutput out =
        service.path(
            scope,
            GraphNodeType.ASSET,
            e1.getId(),
            GraphNodeType.ASSET,
            e1.getId(),
            GraphVisibility.ALL);

    assertThat(out.path()).containsExactly(id(GraphNodeType.ASSET, e1.getId()));
  }

  @Test
  @DisplayName("no path to an entity outside the graph")
  void noPath() {
    scopeToAmbientTenant();
    GraphOutput out =
        service.path(
            scope,
            GraphNodeType.ASSET,
            e1.getId(),
            GraphNodeType.ASSET,
            UUID.randomUUID().toString(),
            GraphVisibility.ALL);

    assertThat(out.path()).isEmpty();
    assertThat(out.nodes()).isEmpty();
  }

  // -- Through HTTP: the edge filter and the caller's grant gate --

  @Test
  @DisplayName("through HTTP, an inject's ring narrowed by edge type keeps its simulation")
  void httpInjectRingNarrowed() throws Exception {
    mvc.perform(
            get(NEIGHBOURHOOD, tenant, "inject", i1.getId())
                .param("edges", " ", "part_of", "produced")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graph_focus").value(id(GraphNodeType.INJECT, i1.getId())))
        .andExpect(jsonPath("$.graph_nodes[?(@.node_entity_id == '%s')]", x.getId()).isNotEmpty())
        .andExpect(jsonPath("$.graph_edges[?(@.edge_type == 'TARGETS')]").isEmpty());
  }

  @Test
  @DisplayName("through HTTP, the path from an atomic testing to its asset is read with grants")
  void httpPathThroughGrants() throws Exception {
    mvc.perform(
            get(TENANT_PREFIX + "/graph/path", tenant)
                .param("fromType", "inject")
                .param("fromId", i2.getId())
                .param("toType", "asset")
                .param("toId", e2.getId())
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graph_path.length()").value(2));
    mvc.perform(
            get(TENANT_PREFIX + "/graph/path", tenant)
                .param("fromType", "scenario")
                .param("fromId", s.getId())
                .param("toType", "inject")
                .param("toId", i1.getId())
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graph_path.length()").value(3));
  }
}
