package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.AttackPathDeltaService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathDeltaDTO;
import io.openaev.service.attackpath.dto.AttackPathEdges;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The delta contract of the attack-path real-time updates (#6647, spec 002).
 *
 * <p>The centrepiece is FR4 stated as an executable property: <b>snapshot(v) + delta(v→w) ≡
 * snapshot(w)</b>. Every other guarantee of the feature is downstream of it — if a delta can
 * produce a state a full reload would not, the front shows a graph that never existed, and no
 * amount of polling fixes it. Equivalence is checked on the serialized entities keyed by id, so it
 * covers the field set too, not just which nodes exist.
 *
 * <p>The rows are written the way a real writer writes them (bump the version, stamp it on the rows
 * of that write) rather than through the run pipeline: this test is about the read contract, and
 * the writers' own bump/stamp wiring is pinned by the ingestion and verdict-sync tests.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH")
@DisplayName("attack path: the delta read is equivalent to a full reload")
class AttackPathDeltaApiTest extends IntegrationTest {

  private static final String SIM = "SIM-DELTA";

  @Autowired private MockMvc mvc;
  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathDeltaService deltaService;
  @Autowired private AttackPathVersionService versionService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;
  @Autowired private ObjectMapper objectMapper;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-delta-tenant"));
  }

  @Test
  @DisplayName("given a graph that grew, applying the delta yields the same state as a full reload")
  void given_a_grown_graph_should_make_snapshot_plus_delta_equal_the_new_snapshot() {
    long v1 = write(() -> executionOn("dc-01", "nmap", null));
    AttackPathDTO atV1 = snapshot();

    write(
        () -> {
          AttackPathExecution execution = executionOn("dc-02", "hydra", "Not Prevented");
          findingOn(execution, "credentials", "admin:secret");
        });
    AttackPathDTO atV2 = snapshot();

    AttackPathDeltaDTO delta = deltaService.buildDelta(SIM, v1);

    assertThat(delta.resyncRequired()).isFalse();
    assertThat(delta.sinceVersion()).isEqualTo(v1);
    assertThat(delta.newVersion()).isGreaterThan(v1);
    assertThatApplyingEquals(atV1, delta, atV2);
  }

  @Test
  @DisplayName("given the same delta applied twice, the state is the same as applying it once")
  void given_a_delta_applied_twice_should_equal_applying_it_once() {
    long v1 = write(() -> executionOn("dc-01", "nmap", null));
    AttackPathDTO atV1 = snapshot();
    write(() -> executionOn("dc-02", "nmap", null));

    AttackPathDeltaDTO delta = deltaService.buildDelta(SIM, v1);

    Map<String, JsonNode> once = apply(atV1, delta);
    Map<String, JsonNode> twice = apply(atV1, delta, delta);
    assertThat(twice).isEqualTo(once);
  }

  @Test
  @DisplayName("given nothing changed, the delta is empty at the client's own version")
  void given_no_change_should_return_an_empty_delta_at_the_same_version() {
    long version = write(() -> executionOn("dc-01", "nmap", null));

    AttackPathDeltaDTO delta = deltaService.buildDelta(SIM, version);

    assertThat(delta.resyncRequired()).isFalse();
    assertThat(delta.newVersion()).isEqualTo(version);
    assertThat(delta.attackPathNodes()).isEmpty();
    assertThat(delta.attackPathEdges()).isEmpty();
    assertThat(delta.attackPathExecutions()).isEmpty();
    // Nothing changed, so the counters did not either: null means "keep the ones you have", which
    // is
    // what makes a quiet run's poll one indexed point read.
    assertThat(delta.counters()).isNull();
  }

  @Test
  @DisplayName("given a cursor ahead of the current version, a resync is demanded")
  void given_a_cursor_ahead_of_the_current_version_should_require_a_resync() {
    long version = write(() -> executionOn("dc-01", "nmap", null));

    AttackPathDeltaDTO delta = deltaService.buildDelta(SIM, version + 10);

    assertThat(delta.resyncRequired()).isTrue();
    assertThat(delta.attackPathNodes()).isEmpty();
  }

  @Test
  @DisplayName("given a simulation whose attack-path data was deleted, an old cursor resyncs")
  void given_a_deleted_simulation_should_require_a_resync_for_an_old_cursor() {
    long version = write(() -> executionOn("dc-01", "nmap", null));
    executionRepository.deleteAllBySimulationId(SIM);
    findingRepository.deleteAllBySimulationId(SIM);
    versionService.deleteBySimulationId(SIM);
    entityManager.flush();

    assertThat(deltaService.buildDelta(SIM, version).resyncRequired()).isTrue();
    // A client that holds nothing has nothing to catch up on: it gets an empty delta, not a resync.
    assertThat(deltaService.buildDelta(SIM, 0).resyncRequired()).isFalse();
  }

  @Test
  @DisplayName("given a simulation with no attack-path data, a fresh client gets an empty delta")
  void given_a_simulation_with_no_data_should_return_an_empty_delta_at_version_zero() {
    AttackPathDeltaDTO delta = deltaService.buildDelta("SIM-NEVER-RUN", 0);

    assertThat(delta.resyncRequired()).isFalse();
    assertThat(delta.newVersion()).isZero();
    assertThat(delta.attackPathNodes()).isEmpty();
  }

  @Test
  @DisplayName(
      "given a verdict change, the endpoint's colour is recomputed over all its executions")
  void given_a_verdict_change_should_recompute_the_endpoint_colour_over_all_its_executions() {
    // Two executions on one endpoint: one already RED (neither prevented nor detected). A delta
    // carrying only the second, PREVENTED one must not let the endpoint report GREEN — the colour
    // is
    // recomputed over both rows, which is exactly what a subset of rows cannot do on its own.
    write(() -> executionOn("dc-01", "nmap", null));
    long v1 = versionService.current(SIM).orElseThrow();
    write(() -> executionOn("dc-01", "hydra", "Prevented"));

    AttackPathDeltaDTO delta = deltaService.buildDelta(SIM, v1);

    AttackPathNodeDTO endpoint =
        delta.attackPathNodes().stream()
            .filter(node -> "dc-01".equals(node.getRef()))
            .findFirst()
            .orElseThrow();
    assertThat(endpoint.getStatus()).isEqualTo("RED");
  }

  @Test
  @DisplayName("the delta endpoint is reachable and reports the simulation's current version")
  void the_delta_endpoint_returns_the_current_version() throws Exception {
    long version = write(() -> executionOn("dc-01", "nmap", null));

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph/delta")
                .param("since", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newVersion").value(version))
        .andExpect(jsonPath("$.resyncRequired").value(false))
        .andExpect(jsonPath("$.attackPathNodes").isNotEmpty());
  }

  // -- the property, and the client-side reducer it is stated against --

  /**
   * The client's reducer, in the simplest form the contract allows: upsert every entity by its id,
   * replacing whole. If the property holds against THIS, it holds against any faithful client.
   */
  private Map<String, JsonNode> apply(AttackPathDTO base, AttackPathDeltaDTO... deltas) {
    Map<String, JsonNode> state = new LinkedHashMap<>();
    index(state, "node", base.attackPathNodes(), AttackPathNodeDTO::getId);
    index(state, "edge", base.attackPathEdges(), AttackPathEdges::getEdgeId);
    index(state, "exec", base.attackPathExecutions(), AttackPathNodeDTO::getId);
    index(state, "finding", base.staticAttackPathFindings(), AttackPathNodeDTO::getId);
    for (AttackPathDeltaDTO delta : deltas) {
      index(state, "node", delta.attackPathNodes(), AttackPathNodeDTO::getId);
      index(state, "edge", delta.attackPathEdges(), AttackPathEdges::getEdgeId);
      index(state, "exec", delta.attackPathExecutions(), AttackPathNodeDTO::getId);
      index(state, "finding", delta.staticAttackPathFindings(), AttackPathNodeDTO::getId);
    }
    return state;
  }

  private void assertThatApplyingEquals(
      AttackPathDTO base, AttackPathDeltaDTO delta, AttackPathDTO expected) {
    assertThat(apply(base, delta)).isEqualTo(apply(expected));
    assertThat(delta.counters()).isEqualTo(expected.counters());
  }

  private <T> void index(
      Map<String, JsonNode> state, String kind, List<T> entities, Function<T, String> id) {
    entities.forEach(
        entity -> state.put(kind + '#' + id.apply(entity), objectMapper.valueToTree(entity)));
  }

  // -- fixtures: a writer's bump-and-stamp, without the run pipeline --

  /**
   * One writer's transaction: take the simulation's next version, then stamp it on everything the
   * body writes. Returns the version, which is what a client would then hold.
   */
  private long write(Runnable body) {
    long version = versionService.bump(SIM, tenant.getId());
    pendingVersion = version;
    body.run();
    entityManager.flush();
    return version;
  }

  private long pendingVersion;

  private AttackPathExecution executionOn(String targetKey, String injector, String prevention) {
    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(SIM);
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector(injector);
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId(targetKey);
    execution.setTargetKey(targetKey);
    execution.setTargetHostname("HOST-" + targetKey);
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setPreventionStatus(prevention);
    execution.setRowVersion(pendingVersion);
    return executionRepository.save(execution);
  }

  private void findingOn(AttackPathExecution execution, String type, String value) {
    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(SIM);
    finding.setType(type);
    finding.setValue(value);
    finding.setEndpointId(execution.getTargetAssetId());
    finding.setEndpointKey(execution.getTargetKey());
    finding.setRowVersion(pendingVersion);
    finding = findingRepository.save(finding);

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execution.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
  }

  private AttackPathDTO snapshot() {
    entityManager.flush();
    return graphService.buildGraph(SIM, "full");
  }
}
