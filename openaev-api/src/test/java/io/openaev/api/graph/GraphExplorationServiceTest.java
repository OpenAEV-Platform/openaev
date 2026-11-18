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
import io.openaev.api.graph.dto.GraphResult;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
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
    };
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
