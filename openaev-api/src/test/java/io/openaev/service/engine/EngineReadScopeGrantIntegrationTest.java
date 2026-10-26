package io.openaev.service.engine;

import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GrantRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.ListRuntime;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.EsBase;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.Pagination;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The grant dimension of {@link EngineReadScopeResolver} against real grants, for a reader without
 * {@code ACCESS_ASSESSMENT}: a document is checked against the grant of its most specific owner, as
 * in the REST API. A simulation is only readable through a grant on that simulation; a grant on its
 * scenario does not open it, whether the simulation's own grant is absent (the simulation existed
 * before the scenario grant, so nothing was copied) or revoked. The simulation's injects and
 * findings follow the simulation.
 *
 * <p>Fixture: scenario {@code S1} with two simulations, {@code X} and {@code Y}, each running an
 * inject that produced a finding; a bare atomic testing inject {@code AT} with a finding. The
 * simulations are created directly, so no scenario grant is copied onto them.
 */
@Transactional
@WithMockUser(isAdmin = false)
@DisplayName("Engine read scope: grants of the most specific owner, no inheritance")
// es8 only until the reader filter is ported to es9 and OpenSearch (implementation-plan-dashboard
// steps 11-12): those engines ignore the scope, so the nightly would fail on them.
@DisabledIfEnvironmentVariable(named = "ENGINE_ENGINESELECTOR", matches = "elk9|opensearch")
class EngineReadScopeGrantIntegrationTest extends IntegrationTest {

  private static final TxCtx DEFAULT_CTX = TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID);

  @Autowired private EngineReadScopeResolver resolver;
  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private IndexingStatusRepository indexingStatusRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GrantRepository grantRepository;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private EndpointComposer endpointComposer;

  private Group grantGroup;
  private String scenario1;
  private String simulationX;
  private String simulationY;
  private String injectOfX;
  private String findingOfX;
  private String atomicTesting;
  private String findingOfAtomicTesting;

  @BeforeEach
  void seed() throws Exception {
    tenantGroupComposer.reset();
    scenarioComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();
    findingComposer.reset();
    endpointComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }

    // Findings readable through the capability; scenarios and simulations only through grants.
    tenantHelper.grantCapabilitiesInTenant(
        Tenant.DEFAULT_TENANT_UUID,
        Set.of(Capability.ACCESS_DASHBOARDS, Capability.ACCESS_FINDINGS));
    User user = testUserHolder.get();
    grantGroup =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("engine-scope-grants-" + uniqueDefinition()))
            .persist()
            .get();
    grantGroup.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(grantGroup);

    Scenario s1 =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .persist()
            .get();
    scenario1 = s1.getId();
    InjectWithFinding x = injectWithFinding();
    simulationX = simulationOf(s1, x.inject());
    injectOfX = x.inject().get().getId();
    findingOfX = x.finding().get().getId();
    simulationY = simulationOf(s1, injectWithFinding().inject());
    InjectWithFinding at = injectWithFinding();
    at.inject().persist();
    atomicTesting = at.inject().get().getId();
    findingOfAtomicTesting = at.finding().get().getId();

    entityManager.flush();
    entityManager.clear();
    indexingStatusRepository.deleteAll();
    engineService.bulkProcessing(engineContext.getModels().stream());
    // ES processes indexing asynchronously — give it time
    Thread.sleep(1_000);
  }

  @Test
  @DisplayName("given a grant on the scenario only, should not reveal its simulations")
  void given_scenarioGrantOnly_should_notRevealItsSimulations() {
    // -- ARRANGE --
    // The simulations existed before the scenario grant: nothing was copied onto them.
    grant(scenario1, Grant.GRANT_RESOURCE_TYPE.SCENARIO);

    // -- ACT & ASSERT --
    assertThat(ids("scenario")).contains(scenario1);
    assertThat(ids("simulation")).doesNotContain(simulationX, simulationY);
    assertThat(ids("inject")).doesNotContain(injectOfX);
    assertThat(ids("finding")).doesNotContain(findingOfX);
  }

  @Test
  @DisplayName("given a grant on a simulation, should reveal it and its children, not its sibling")
  void given_simulationGrant_should_revealItAndItsChildren() {
    // -- ARRANGE --
    grant(simulationX, Grant.GRANT_RESOURCE_TYPE.SIMULATION);

    // -- ACT & ASSERT --
    assertThat(ids("simulation")).contains(simulationX).doesNotContain(simulationY);
    assertThat(ids("inject")).contains(injectOfX);
    assertThat(ids("finding")).contains(findingOfX);
    assertThat(ids("scenario")).doesNotContain(scenario1);
  }

  @Test
  @DisplayName("given the simulation grant revoked and the scenario grant kept, should hide it")
  void given_simulationGrantRevoked_should_hideItDespiteTheScenarioGrant() {
    // -- ARRANGE --
    grant(scenario1, Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    Grant onX = grant(simulationX, Grant.GRANT_RESOURCE_TYPE.SIMULATION);
    assertThat(ids("simulation")).contains(simulationX);
    revoke(onX);

    // -- ACT & ASSERT --
    assertThat(ids("simulation")).doesNotContain(simulationX);
    assertThat(ids("inject")).doesNotContain(injectOfX);
    assertThat(ids("finding")).doesNotContain(findingOfX);
    assertThat(ids("scenario")).contains(scenario1);
  }

  @Test
  @DisplayName("given an atomic testing, should reveal it only through its own grant")
  void given_atomicTesting_should_onlyBeRevealedByItsOwnGrant() {
    // -- ARRANGE --
    grant(scenario1, Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    assertThat(ids("inject")).doesNotContain(atomicTesting);
    assertThat(ids("finding")).doesNotContain(findingOfAtomicTesting);
    grant(atomicTesting, Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING);

    // -- ACT & ASSERT --
    assertThat(ids("inject")).contains(atomicTesting);
    assertThat(ids("finding")).contains(findingOfAtomicTesting);
  }

  // -- Helpers --

  /** The ids of the documents of {@code entity} the reader gets, with a freshly resolved scope. */
  private List<String> ids(String entity) {
    ListConfiguration config = engineService.createListConfiguration(entity, Map.of());
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    ListRuntime runtime = new ListRuntime(config, Map.of(), Map.of(), new Pagination(0, 100));
    return engineService
        .entities(DEFAULT_CTX, resolver.resolve(DEFAULT_CTX), runtime)
        .getEsDatas()
        .stream()
        .map(EsBase::getBase_id)
        .toList();
  }

  private Grant grant(String resourceId, Grant.GRANT_RESOURCE_TYPE type) {
    Grant grant =
        grantRepository.save(Grant.of(Grant.GRANT_TYPE.OBSERVER, grantGroup, resourceId, type));
    entityManager.flush();
    return grant;
  }

  private record InjectWithFinding(
      InjectComposer.Composer inject, FindingComposer.Composer finding) {}

  /**
   * Revokes a grant the way the product does ({@code TenantGroupService.removeGrant}): out of the
   * group's collection, deleted by orphan removal. A repository delete would be undone at flush,
   * the group (loaded by the previous read) still holding the grant in a cascading collection.
   */
  private void revoke(Grant grant) {
    Group group = groupRepository.findById(grantGroup.getId()).orElseThrow();
    group.getGrants().removeIf(candidate -> candidate.getId().equals(grant.getId()));
    groupRepository.save(group);
    entityManager.flush();
  }

  /** An inject that produced a finding on a new endpoint; not persisted yet. */
  private InjectWithFinding injectWithFinding() {
    EndpointComposer.Composer endpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint("grant-" + uniqueDefinition()));
    FindingComposer.Composer finding =
        findingComposer
            .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
            .withEndpoint(endpoint);
    return new InjectWithFinding(
        injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(finding), finding);
  }

  private String simulationOf(Scenario scenario, InjectComposer.Composer inject) {
    Exercise exercise = ExerciseFixture.createDefaultIncidentResponseExercise(Instant.now());
    exercise.setScenario(scenario);
    return exerciseComposer.forExercise(exercise).withInject(inject).persist().get().getId();
  }
}
