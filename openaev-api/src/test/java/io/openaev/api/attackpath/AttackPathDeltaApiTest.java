package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.AttackPathDeltaService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.dto.AttackPathCounters;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathDeltaDTO;
import io.openaev.service.attackpath.dto.AttackPathEdges;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
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
 * <p>"snapshot" here means the state a client actually holds after a read — the collapsed seed with
 * the full graph merged over it — not one of the two reads alone; see {@link #clientState()} for
 * why that distinction is load-bearing rather than incidental.
 *
 * <p>The rows are written the way a real writer writes them (bump the version, stamp it on the rows
 * of that write) rather than through the run pipeline: this test is about the read contract, and
 * the writers' own bump/stamp wiring is pinned by the ingestion and verdict-sync tests.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=ATTACK_PATH")
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
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private Tenant tenant;

  @BeforeEach
  void setUp() throws Exception {
    // Attached to the mock user, not just persisted: the HTTP reads below resolve their tenant
    // scope
    // from the caller's memberships, and the version counter is read under that scope.
    tenant = tenantHelper.createTenantWithCurrentUser("ap-delta-tenant");
  }

  @Test
  @DisplayName("given a graph that grew, applying the delta yields the same state as a full reload")
  void given_a_grown_graph_should_make_snapshot_plus_delta_equal_the_new_snapshot() {
    long v1 = write(() -> executionOn("dc-01", "nmap", null));
    ClientState atV1 = clientState();

    write(
        () -> {
          AttackPathExecution execution = executionOn("dc-02", "hydra", "Not Prevented");
          findingOn(execution, "credentials", "admin:secret");
        });
    ClientState atV2 = clientState();

    AttackPathDeltaDTO delta = deltaSince(v1);

    assertThat(delta.resyncRequired()).isFalse();
    assertThat(delta.sinceVersion()).isEqualTo(v1);
    assertThat(delta.newVersion()).isGreaterThan(v1);
    assertThatApplyingEquals(atV1, delta, atV2);
  }

  @Test
  @DisplayName(
      "given a finding value rediscovered by a second execution, its new link reaches the delta")
  void given_a_relinked_finding_should_ship_the_new_execution_link_in_the_delta() {
    // The finding row already exists with this (type, value, endpoint), so the copy hits its
    // conflict branch. Only the link is new. If the conflict branch left the row's version alone,
    // the finding would stay attached to its first execution in every client's graph, forever —
    // the link is only reachable through the finding row.
    AttackPathFinding finding =
        writeReturning(
            () -> findingOn(executionOn("dc-01", "nmap", null), "credentials", "admin:secret"));
    long v1 = currentVersion();
    ClientState atV1 = clientState();

    write(() -> relinkFinding(finding, executionOn("dc-01", "hydra", null)));
    ClientState atV2 = clientState();

    assertThatApplyingEquals(atV1, deltaSince(v1), atV2);
  }

  @Test
  @DisplayName(
      "given findings copied in a bump after their execution's, the delta still links them")
  void given_findings_written_after_their_producer_should_still_carry_the_causal_wiring() {
    // The real ingestion sequence, and the one the tests above never reproduced: an execution is
    // persisted and versioned when its inject runs, and its findings are copied in their OWN later
    // bump. A delta taken in between therefore carries findings whose PRODUCER did not change — and
    // the rebuild pass derives an execution's produced-finding ids by intersecting the executions
    // and
    // the findings it is handed. Without the producer it emitted orphan finding nodes, and since
    // the
    // causal chain places a finding node only from its producer's list, the client accumulated the
    // whole finding layer and rendered none of it until a reload re-read the snapshot.
    AttackPathExecution execution = writeReturning(() -> executionOn("dc-01", "nmap", null));
    long atExecution = currentVersion();
    ClientState beforeFindings = clientState();

    write(() -> findingOn(execution, "port", "445"));

    AttackPathDeltaDTO delta = deltaSince(atExecution);
    assertThat(delta.attackPathExecutions())
        .singleElement()
        .satisfies(
            producer ->
                assertThat(producer.getFindingsNodeIds())
                    .containsExactly(AttackPathIds.findingNode("port", "445")));
    assertThatApplyingEquals(beforeFindings, delta, clientState());
  }

  @Test
  @DisplayName("given a second batch of findings, the producer keeps the ones it already shipped")
  void given_a_second_finding_bump_should_accumulate_the_producers_finding_ids() {
    // Each bump's delta computes the producer's finding ids from the findings THAT BUMP changed, so
    // the second batch ships `[port 3389]` alone. The client accumulates them (`mergeExecutionNode`
    // in attack-path-delta-store.ts) rather than replacing, which is what this states: replacing
    // would silently drop the first port from the chain, and only a reload would bring it back.
    AttackPathExecution execution = writeReturning(() -> executionOn("dc-01", "nmap", null));
    long atExecution = currentVersion();
    ClientState beforeFindings = clientState();

    write(() -> findingOn(execution, "port", "445"));
    long afterFirstFinding = currentVersion();
    AttackPathDeltaDTO first = deltaSince(atExecution);

    write(() -> findingOn(execution, "port", "3389"));
    AttackPathDeltaDTO second = deltaSince(afterFirstFinding);

    assertThat(second.attackPathExecutions())
        .singleElement()
        .satisfies(
            producer ->
                assertThat(producer.getFindingsNodeIds())
                    .containsExactly(AttackPathIds.findingNode("port", "3389")));
    assertThat(apply(beforeFindings, first, second)).isEqualTo(clientState().entities());
  }

  @Test
  @DisplayName("the snapshot carries the version a client then polls the delta with")
  void the_snapshot_is_labelled_with_the_current_version() throws Exception {
    long version = write(() -> executionOn("dc-01", "nmap", null));

    assertThat(snapshot().graphVersion()).isEqualTo(version);
    // A delta taken at the snapshot's own version is empty: the label is the cursor, not an
    // approximation of it.
    assertThat(deltaSince(snapshot().graphVersion()).attackPathNodes()).isEmpty();

    mvc.perform(get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graphVersion").value(version));
  }

  @Test
  @DisplayName("a negative cursor is rejected rather than reinterpreted")
  void a_negative_cursor_is_rejected() throws Exception {
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph/delta")
                .param("since", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("given the same delta applied twice, the state is the same as applying it once")
  void given_a_delta_applied_twice_should_equal_applying_it_once() {
    long v1 = write(() -> executionOn("dc-01", "nmap", null));
    ClientState atV1 = clientState();
    write(() -> executionOn("dc-02", "nmap", null));

    AttackPathDeltaDTO delta = deltaSince(v1);

    Map<String, JsonNode> once = apply(atV1, delta);
    Map<String, JsonNode> twice = apply(atV1, delta, delta);
    assertThat(twice).isEqualTo(once);
  }

  @Test
  @DisplayName("given nothing changed, the delta is empty at the client's own version")
  void given_no_change_should_return_an_empty_delta_at_the_same_version() {
    long version = write(() -> executionOn("dc-01", "nmap", null));

    AttackPathDeltaDTO delta = deltaSince(version);

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

    AttackPathDeltaDTO delta = deltaSince(version + 10);

    assertThat(delta.resyncRequired()).isTrue();
    assertThat(delta.attackPathNodes()).isEmpty();
  }

  @Test
  @DisplayName("given a simulation whose attack-path data was deleted, an old cursor resyncs")
  void given_a_deleted_simulation_should_require_a_resync_for_an_old_cursor() {
    long version = write(() -> executionOn("dc-01", "nmap", null));
    executionRepository.deleteAllBySimulationId(SIM);
    findingRepository.deleteAllBySimulationId(SIM);
    versionService.deleteBySimulationId(SIM, tenant.getId());
    entityManager.flush();

    assertThat(deltaSince(version).resyncRequired()).isTrue();
    // A client that holds nothing has nothing to catch up on: it gets an empty delta, not a resync.
    assertThat(deltaSince(0).resyncRequired()).isFalse();
  }

  @Test
  @DisplayName("given a simulation with no attack-path data, a fresh client gets an empty delta")
  void given_a_simulation_with_no_data_should_return_an_empty_delta_at_version_zero() {
    AttackPathDeltaDTO delta = deltaService.buildDelta("SIM-NEVER-RUN", 0, Set.of(tenant.getId()));

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
    long v1 = currentVersion();
    write(() -> executionOn("dc-01", "hydra", "Prevented"));

    AttackPathDeltaDTO delta = deltaSince(v1);

    AttackPathNodeDTO endpoint =
        delta.attackPathNodes().stream()
            .filter(node -> "dc-01".equals(node.getRef()))
            .findFirst()
            .orElseThrow();
    assertThat(endpoint.getStatus()).isEqualTo("RED");
  }

  @Test
  @DisplayName(
      "given a second agent on an endpoint, the delta keeps the endpoint's whole agent list")
  void given_a_second_agent_should_recompute_the_endpoint_agents_over_all_its_executions() {
    // Same shape as the colour recompute above, for the other aggregate an endpoint node carries.
    // The delta only sees the hydra row, so a list derived from the changed rows alone would ship
    // one agent and shrink an already-rendered node from two to one.
    write(() -> executionOn("dc-01", "nmap", null));
    long v1 = currentVersion();
    write(() -> executionOn("dc-01", "hydra", null));

    AttackPathNodeDTO endpoint =
        deltaSince(v1).attackPathNodes().stream()
            .filter(node -> "dc-01".equals(node.getRef()))
            .findFirst()
            .orElseThrow();
    assertThat(endpoint.getAgents()).containsExactly("agent-hydra", "agent-nmap");
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
   * What a client holds at one version: the entities it has accumulated, keyed by kind and id, plus
   * the counters it last received.
   */
  private record ClientState(Map<String, JsonNode> entities, AttackPathCounters counters) {}

  /**
   * The state a faithful client holds after reading a version, which is what FR4's equivalence is
   * stated against: the COLLAPSED snapshot it seeds from, with the FULL snapshot merged on top.
   * That is literally what the front does — {@code fromSnapshot} then {@code withFullSnapshot} in
   * {@code attack-path-delta-store.ts} — and it is the reference on purpose rather than the full
   * snapshot alone.
   *
   * <p>One delta stream feeds BOTH projections (the store derives {@code toCollapsedDto} and {@code
   * toFullDto} from one accumulated graph), so a delta's ASSET node deliberately carries the UNION
   * of the two shapes' fields: the causal fields the full graph needs, and the per-type {@code
   * findingCounts} the collapsed map renders from. Compared against the full snapshot alone, those
   * counts would look like something only the delta invents; compared against the state the client
   * really holds, they are the field it seeded with, recomputed.
   */
  private ClientState clientState() {
    AttackPathDTO collapsed = collapsedSnapshot();
    AttackPathDTO full = snapshot();
    Map<String, JsonNode> state = new LinkedHashMap<>();
    merge(state, "node", collapsed.attackPathNodes(), AttackPathNodeDTO::getId);
    merge(state, "edge", collapsed.attackPathEdges(), AttackPathEdges::getEdgeId);
    merge(state, "node", full.attackPathNodes(), AttackPathNodeDTO::getId);
    merge(state, "edge", full.attackPathEdges(), AttackPathEdges::getEdgeId);
    merge(state, "exec", full.attackPathExecutions(), AttackPathNodeDTO::getId);
    merge(state, "finding", full.staticAttackPathFindings(), AttackPathNodeDTO::getId);
    return new ClientState(state, full.counters());
  }

  /**
   * The client's reducer: upsert every entity by its id, merging field by field. Merging rather
   * than replacing is the store's actual contract (see {@code upsert} in {@code
   * attack-path-delta-store.ts}) and the reason the two projections can share one stream.
   */
  private Map<String, JsonNode> apply(ClientState base, AttackPathDeltaDTO... deltas) {
    Map<String, JsonNode> state = new LinkedHashMap<>(base.entities());
    for (AttackPathDeltaDTO delta : deltas) {
      merge(state, "node", delta.attackPathNodes(), AttackPathNodeDTO::getId);
      merge(state, "edge", delta.attackPathEdges(), AttackPathEdges::getEdgeId);
      merge(state, "exec", delta.attackPathExecutions(), AttackPathNodeDTO::getId);
      merge(state, "finding", delta.staticAttackPathFindings(), AttackPathNodeDTO::getId);
    }
    return state;
  }

  private void assertThatApplyingEquals(
      ClientState base, AttackPathDeltaDTO delta, ClientState expected) {
    assertThat(apply(base, delta)).isEqualTo(expected.entities());
    assertThat(delta.counters()).isEqualTo(expected.counters());
  }

  /**
   * The two list fields the client ACCUMULATES instead of replacing, by entity kind. Both are
   * per-entity histories the delta can only ever ship a slice of — the executions grouped under an
   * edge, and the findings one execution produced — because a delta sees the rows of one bump.
   * Every other field, aggregates included, is shipped whole and replaced (FR1); these two are the
   * stated exception, and the store's {@code mergeEdge} / {@code mergeExecutionNode} are the code
   * this mirrors.
   */
  private static final Map<String, String> ACCUMULATED_LIST_FIELD =
      Map.of("edge", "executionIds", "exec", "findingsNodeIds");

  private <T> void merge(
      Map<String, JsonNode> state, String kind, List<T> entities, Function<T, String> id) {
    entities.forEach(
        entity -> {
          String key = kind + '#' + id.apply(entity);
          ObjectNode incoming = objectMapper.valueToTree(entity);
          JsonNode existing = state.get(key);
          if (existing == null) {
            state.put(key, incoming);
            return;
          }
          ObjectNode merged = ((ObjectNode) existing.deepCopy()).setAll(incoming);
          String accumulated = ACCUMULATED_LIST_FIELD.get(kind);
          if (accumulated != null) {
            union(existing.get(accumulated), incoming.get(accumulated))
                .ifPresent(list -> merged.set(accumulated, list));
          }
          state.put(key, merged);
        });
  }

  /** The known entries then the incoming ones not already there; empty when nothing was known. */
  private Optional<ArrayNode> union(JsonNode known, JsonNode incoming) {
    if (known == null || !known.isArray() || known.isEmpty()) {
      return Optional.empty();
    }
    ArrayNode result = known.deepCopy();
    Set<String> seen = new LinkedHashSet<>();
    known.forEach(entry -> seen.add(entry.asText()));
    if (incoming != null && incoming.isArray()) {
      incoming.forEach(
          entry -> {
            if (seen.add(entry.asText())) {
              result.add(entry);
            }
          });
    }
    return Optional.of(result);
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

  /** {@link #write}, for a body whose written row the test needs to keep hold of. */
  private <T> T writeReturning(Supplier<T> body) {
    pendingVersion = versionService.bump(SIM, tenant.getId());
    T written = body.get();
    entityManager.flush();
    return written;
  }

  private long pendingVersion;

  /**
   * An execution on an endpoint, run by an agent named after its injector. The agent name is part
   * of the fixture rather than left null on purpose: the endpoint node's {@code agents} list is an
   * aggregate over ALL the endpoint's executions, so it is one of the values a delta carrying a
   * subset of rows cannot derive, and without a name in the fixture the list is empty on both sides
   * and a regression there would go unseen.
   */
  private AttackPathExecution executionOn(String targetKey, String injector, String prevention) {
    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(SIM);
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector(injector);
    execution.setAgentId("agent-" + injector + '-' + targetKey);
    execution.setAgentName("agent-" + injector);
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId(targetKey);
    execution.setTargetKey(targetKey);
    execution.setTargetHostname("HOST-" + targetKey);
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setPreventionStatus(prevention);
    execution.setRowVersion(pendingVersion);
    return executionRepository.save(execution);
  }

  private AttackPathFinding findingOn(AttackPathExecution execution, String type, String value) {
    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(SIM);
    finding.setType(type);
    finding.setValue(value);
    finding.setEndpointId(execution.getTargetAssetId());
    finding.setEndpointKey(execution.getTargetKey());
    finding.setRowVersion(pendingVersion);
    finding = findingRepository.save(finding);

    link(execution, finding);
    return finding;
  }

  /**
   * A finding value already in the projection, re-discovered by another execution: the copy's
   * conflict branch re-stamps the row version and adds the new link, which is exactly what makes
   * the new edge reachable by a delta.
   */
  private void relinkFinding(AttackPathFinding finding, AttackPathExecution execution) {
    finding.setRowVersion(pendingVersion);
    findingRepository.save(finding);
    link(execution, finding);
  }

  private void link(AttackPathExecution execution, AttackPathFinding finding) {
    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execution.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
  }

  private AttackPathDTO snapshot() {
    entityManager.flush();
    // The read path's order: the version first, then the rows (see AttackPathApi#graph).
    return graphService.buildGraph(SIM, "full", currentVersion());
  }

  /** The read a client actually seeds and resyncs from (see {@link #clientState()}). */
  private AttackPathDTO collapsedSnapshot() {
    entityManager.flush();
    return graphService.buildGraph(SIM, "collapsed", currentVersion());
  }

  private long currentVersion() {
    return versionService.current(SIM, Set.of(tenant.getId())).orElseThrow();
  }

  private AttackPathDeltaDTO deltaSince(long since) {
    entityManager.flush();
    return deltaService.buildDelta(SIM, since, Set.of(tenant.getId()));
  }
}
