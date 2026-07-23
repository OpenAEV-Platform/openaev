package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.dto.AttackPathAttackPatternDTO;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathEdges;
import io.openaev.service.attackpath.dto.AttackPathEndpointRelationsDTO;
import io.openaev.service.attackpath.dto.AttackPathExpandDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuild service: two flat reads plus one in-memory pass produce {@code {nodes, edges, counters}}
 * with the deterministic IDs, and the read path issues a constant number of SQL statements
 * regardless of graph size (the two flat reads plus the bounded per-run contract/technique lookups).
 */
@Transactional
class AttackPathGraphServiceTest extends IntegrationTest {

  private static final String SIM = "SIM-GRAPH";

  @Autowired private AttackPathGraphService service;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;

  private Tenant tenant;
  private String exec1Id;
  private String exec2Id;

  @BeforeEach
  void seedGraph() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-graph-tenant"));
    // NMAP hits CORP-DC-01 twice: one prevented, one detected-but-not-prevented -> ORANGE
    // (worst-case), one grouped edge of count 2.
    exec1Id =
        injectorExecution(
            "NMAP", "dc-01", "CORP-DC-01", "Prevented", "Not Detected", "Nmap Scan", at(1));
    exec2Id =
        injectorExecution(
            "NMAP", "dc-01", "CORP-DC-01", "Not Prevented", "Detected", "Nmap CVE Scan", at(2));
    // NMAP hits CORP-APP-01 once, prevented -> GREEN.
    String exec3Id =
        injectorExecution(
            "NMAP", "app-01", "CORP-APP-01", "Prevented", "Not Detected", "Nmap Scan", at(3));
    // A pivot dc-01 -> web-01 (agent-based), neither prevented nor detected -> RED.
    pivotExecution(
        "dc-01", "OpenAEV", "web-01", "CORP-WEB-01", "Not Prevented", "Not Detected", at(4));

    finding("credentials", "admin:secret", "dc-01", exec1Id);
    finding("cve", "CVE-2023-1", "dc-01", exec2Id);
    // Same (type, value) on another endpoint: one deduped finding node, shared.
    finding("credentials", "admin:secret", "app-01", exec3Id);
    entityManager.flush();
  }

  @Test
  @DisplayName("Injector, asset, finding-type and finding nodes are built with endpoint colours")
  void builds_nodes_and_colours() {
    AttackPathDTO dto = service.buildGraph(SIM);

    assertThat(nodeById(dto, AttackPathIds.injectorNode("NMAP")).getType()).isEqualTo("INJECTOR");

    AttackPathNodeDTO dc01 = nodeById(dto, AttackPathIds.endpointNode("dc-01"));
    assertThat(dc01.getType()).isEqualTo("ASSET");
    assertThat(dc01.getHostname()).isEqualTo("CORP-DC-01");
    assertThat(dc01.getStatus()).isEqualTo("ORANGE");
    assertThat(nodeById(dto, AttackPathIds.endpointNode("app-01")).getStatus()).isEqualTo("GREEN");
    assertThat(nodeById(dto, AttackPathIds.endpointNode("web-01")).getStatus()).isEqualTo("RED");
    // agents on an endpoint come from the executions targeting it (the pivot carried an agent).
    assertThat(nodeById(dto, AttackPathIds.endpointNode("web-01")).getAgents())
        .containsExactly("OpenAEV");
  }

  @Test
  @DisplayName("Executions sharing (source, target) collapse into one edge with count and ids")
  void groups_execution_edges() {
    AttackPathDTO dto = service.buildGraph(SIM);
    AttackPathEdges edge =
        edgeById(
            dto,
            AttackPathIds.executionsEdge(
                AttackPathIds.injectorNode("NMAP"), AttackPathIds.endpointNode("dc-01")));
    assertThat(edge.getType()).isEqualTo("EDGE_EXECUTIONS");
    assertThat(edge.getCount()).isEqualTo(2);
    assertThat(edge.getExecutionIds()).containsExactlyInAnyOrder(exec1Id, exec2Id);
  }

  @Test
  @DisplayName("A finding shared across endpoints is one deduped finding node")
  void dedups_shared_finding() {
    AttackPathDTO dto = service.buildGraph(SIM);
    long credentialNodes =
        dto.attackPathNodes().stream()
            .filter(n -> "FINDING".equals(n.getType()) && "admin:secret".equals(n.getValue()))
            .count();
    assertThat(credentialNodes).isEqualTo(1);

    AttackPathNodeDTO findingNode =
        nodeById(dto, AttackPathIds.findingNode("credentials", "admin:secret"));
    assertThat(findingNode.getAssetNodeId()).isNotNull();
    assertThat(findingNode.getFindingsTypeNodeId()).isNotNull();
  }

  @Test
  @DisplayName("Counters: endpoints from targets, finding types from findings, no files")
  void computes_counters() {
    AttackPathDTO dto = service.buildGraph(SIM);
    assertThat(dto.counters().endpoints()).isEqualTo(3);
    assertThat(dto.counters().credentials()).isEqualTo(1);
    assertThat(dto.counters().cves()).isEqualTo(1);
    assertThat(dto.counters().users()).isZero();
    assertThat(dto.counters().ports()).isZero();
  }

  @Test
  @DisplayName("The feed lists every execution; the static findings are the deduped finding nodes")
  void feed_and_static_findings() {
    AttackPathDTO dto = service.buildGraph(SIM);
    assertThat(dto.attackPathExecutions()).hasSize(4);
    assertThat(dto.attackPathExecutions())
        .allSatisfy(n -> assertThat(n.getType()).isEqualTo("EXECUTION"));
    assertThat(dto.staticAttackPathFindings()).hasSize(2);
  }

  @Test
  @DisplayName("An execution feed node carries the ids of the findings it produced")
  void fills_execution_to_finding_cross_reference() {
    AttackPathDTO dto = service.buildGraph(SIM);
    // exec1 (injector, no agent) produced the credentials finding on dc-01.
    String feedNodeId = AttackPathIds.executionNode(exec1Id, "dc-01", null);
    AttackPathNodeDTO feedNode =
        dto.attackPathExecutions().stream()
            .filter(n -> feedNodeId.equals(n.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(feedNode.getFindingsNodeIds())
        .containsExactly(AttackPathIds.findingNode("credentials", "admin:secret"));
    assertThat(feedNode.getRef())
        .as("the feed node carries the raw execution id for the drawer cross-focus")
        .isEqualTo(exec1Id);
  }

  @Test
  @DisplayName("An empty simulation rebuilds to an empty graph without error")
  void empty_simulation_is_safe() {
    AttackPathDTO dto = service.buildGraph("SIM-WITH-NO-DATA");
    assertThat(dto.attackPathNodes()).isEmpty();
    assertThat(dto.attackPathEdges()).isEmpty();
    assertThat(dto.attackPathExecutions()).isEmpty();
    assertThat(dto.staticAttackPathFindings()).isEmpty();
    assertThat(dto.counters().endpoints()).isZero();
    assertThat(dto.counters().credentials()).isZero();
  }

  @Test
  @DisplayName("A source-only endpoint node carries its frozen hostname, ip and platform")
  void source_only_endpoint_carries_its_frozen_attributes() {
    // "gw-01" attacks "victim-only" and is itself never a target, so the ASSET pass never fills it;
    // its attributes must come from the frozen source columns of its own execution.
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("AGENT_ASSET");
    e.setSourceAssetId("gw-01");
    e.setSourceHostname("GATEWAY-01");
    e.setSourceIp("10.0.9.1");
    e.setSourcePlatform("Linux");
    e.setAgentId("agent-9");
    e.setAgentName("OpenAEV");
    e.setTargetKind("ASSET");
    e.setTargetAssetId("victim-only");
    e.setTargetKey("victim-only");
    e.setExecutedAt(at(15));
    executionRepository.save(e);
    entityManager.flush();

    AttackPathNodeDTO sourceNode =
        nodeById(service.buildGraph(SIM), AttackPathIds.endpointNode("gw-01"));
    assertThat(sourceNode.getHostname()).isEqualTo("GATEWAY-01");
    assertThat(sourceNode.getIp()).isEqualTo("10.0.9.1");
    assertThat(sourceNode.getPlatform()).isEqualTo("Linux");
    assertThat(sourceNode.getLabel()).isEqualTo("GATEWAY-01");
  }

  @Test
  @DisplayName("Collapsed mode also renders a source-only endpoint's frozen attributes")
  void collapsed_source_only_endpoint_carries_its_frozen_attributes() {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("AGENT_ASSET");
    e.setSourceAssetId("gw-02");
    e.setSourceHostname("GATEWAY-02");
    e.setSourceIp("10.0.9.2");
    e.setSourcePlatform("Windows");
    e.setAgentId("agent-8");
    e.setTargetKind("ASSET");
    e.setTargetAssetId("victim-coll");
    e.setTargetKey("victim-coll");
    e.setExecutedAt(at(16));
    executionRepository.save(e);
    entityManager.flush();

    AttackPathNodeDTO sourceNode =
        nodeById(service.buildGraph(SIM, "collapsed"), AttackPathIds.endpointNode("gw-02"));
    assertThat(sourceNode.getHostname()).isEqualTo("GATEWAY-02");
    assertThat(sourceNode.getIp()).isEqualTo("10.0.9.2");
    assertThat(sourceNode.getPlatform()).isEqualTo("Windows");
    assertThat(sourceNode.getLabel()).isEqualTo("GATEWAY-02");
  }

  @Test
  @DisplayName("The injector node carries its ATT&CK techniques and real injector type")
  void injector_node_exposes_attack_patterns_and_type() {
    // A real contract with an ATT&CK technique, frozen onto an injector execution by its external
    // id.
    String externalId = "C-INJ-1";
    injectorContractComposer
        .forInjectorContract(
            InjectorContractFixture.createDefaultInjectorContractWithExternalId(externalId))
        .withAttackPattern(
            attackPatternComposer.forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId("T1046")))
        .persist();
    // A fresh injector name, so its node is created from this run (with the type) rather than from
    // the @BeforeEach's "NMAP" runs, which carry no contract or type.
    seedInjectorRun("CME", "srv-01", "SRV-01", externalId, "openaev_nmap", at(10));
    entityManager.flush();

    AttackPathNodeDTO injectorNode =
        service.buildGraph(SIM).attackPathNodes().stream()
            .filter(n -> "INJECTOR".equals(n.getType()) && "CME".equals(n.getLabel()))
            .findFirst()
            .orElseThrow();

    assertThat(injectorNode.getInjectorType()).isEqualTo("openaev_nmap");
    assertThat(injectorNode.getAttackPatterns())
        .extracting(AttackPathAttackPatternDTO::externalId)
        .containsExactly("T1046");
  }

  @Test
  @DisplayName("The collapsed injector node carries its ATT&CK techniques and type too")
  void collapsed_injector_node_exposes_attack_patterns_and_type() {
    String externalId = "C-INJ-COLL";
    injectorContractComposer
        .forInjectorContract(
            InjectorContractFixture.createDefaultInjectorContractWithExternalId(externalId))
        .withAttackPattern(
            attackPatternComposer.forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId("T1021")))
        .persist();
    seedInjectorRun("HYDRA", "srv-02", "SRV-02", externalId, "openaev_hydra", at(11));
    entityManager.flush();

    AttackPathNodeDTO injectorNode =
        service.buildGraph(SIM, "collapsed").attackPathNodes().stream()
            .filter(n -> "INJECTOR".equals(n.getType()) && "HYDRA".equals(n.getLabel()))
            .findFirst()
            .orElseThrow();

    assertThat(injectorNode.getInjectorType()).isEqualTo("openaev_hydra");
    assertThat(injectorNode.getAttackPatterns())
        .extracting(AttackPathAttackPatternDTO::externalId)
        .containsExactly("T1021");
  }

  @Test
  @DisplayName("An injector running several contracts shows the union of their techniques")
  void injector_node_unions_techniques_across_contracts() {
    seedContractWithPattern("C-A", "T1001");
    seedContractWithPattern("C-B", "T1002");
    // Same injector "SPRAY", two runs under two different contracts.
    seedInjectorRun("SPRAY", "h-a", "H-A", "C-A", "openaev_impl", at(12));
    seedInjectorRun("SPRAY", "h-b", "H-B", "C-B", "openaev_impl", at(13));
    entityManager.flush();

    AttackPathNodeDTO injectorNode =
        service.buildGraph(SIM).attackPathNodes().stream()
            .filter(n -> "INJECTOR".equals(n.getType()) && "SPRAY".equals(n.getLabel()))
            .findFirst()
            .orElseThrow();

    assertThat(injectorNode.getAttackPatterns())
        .extracting(AttackPathAttackPatternDTO::externalId)
        .containsExactlyInAnyOrder("T1001", "T1002");
  }

  @Test
  @DisplayName("Injector techniques resolve in one batched query, not one per injector")
  void injector_techniques_resolve_in_a_single_query() {
    String externalId = "C-INJ-BATCH";
    injectorContractComposer
        .forInjectorContract(
            InjectorContractFixture.createDefaultInjectorContractWithExternalId(externalId))
        .withAttackPattern(
            attackPatternComposer.forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId("T1110")))
        .persist();
    // Many injector nodes, all resolving their techniques: the batch must stay one query.
    for (int i = 0; i < 20; i++) {
      seedInjectorRun("INJ-" + i, "t-" + i, "T-" + i, externalId, "openaev_nmap", at(20 + i));
    }
    entityManager.flush();

    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    service.buildGraph(SIM);

    // Two flat reads + the batched technique query + the contract-name resolution
    // (applyContractNames), constant regardless of the execution count (all runs share one
    // contract). Follow-up (#6647): applyContractNames could JOIN FETCH labels to save a read.
    assertThat(stats.getPrepareStatementCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("The collapsed injector resolution is a constant query count, not one per injector")
  void collapsed_injector_resolution_is_constant_query_count() {
    seedContractWithPattern("C-COLL-COUNT", "T1595");
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);

    seedInjectorRun("ONE", "c-1", "C-1", "C-COLL-COUNT", "openaev_impl", at(30));
    entityManager.flush();
    stats.clear();
    service.buildGraph(SIM, "collapsed");
    long few = stats.getPrepareStatementCount();

    for (int i = 0; i < 15; i++) {
      seedInjectorRun("MANY-" + i, "c-" + i, "C-" + i, "C-COLL-COUNT", "openaev_impl", at(31 + i));
    }
    entityManager.flush();
    stats.clear();
    service.buildGraph(SIM, "collapsed");
    long many = stats.getPrepareStatementCount();

    assertThat(many).as("collapsed must not scale queries with the injector count").isEqualTo(few);
  }

  @Test
  @DisplayName("The read path is a constant number of SQL statements, independent of graph size")
  void constant_two_queries_regardless_of_size() {
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);

    stats.clear();
    service.buildGraph(SIM);
    long small = stats.getPrepareStatementCount();

    for (int i = 0; i < 40; i++) {
      injectorExecution(
          "NMAP", "extra-" + i, "H-" + i, "Prevented", "Not Detected", "Nmap Scan", at(100 + i));
    }
    entityManager.flush();

    stats.clear();
    service.buildGraph(SIM);
    long large = stats.getPrepareStatementCount();

    // Two flat reads + the batched contract-name resolution; still constant regardless of graph
    // size.
    assertThat(small).isEqualTo(3);
    assertThat(large).isEqualTo(3);
  }

  @Test
  @DisplayName("Expand an endpoint returns its finding types and findings in a single query")
  void expand_returns_finding_types_and_findings() {
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    AttackPathExpandDTO dto = service.expandEndpoint(SIM, "dc-01");
    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);

    assertThat(dto.findingTypes())
        .extracting(AttackPathNodeDTO::getTypeFindings)
        .containsExactlyInAnyOrder("credentials", "cve");
    assertThat(dto.findings())
        .extracting(AttackPathNodeDTO::getValue)
        .containsExactlyInAnyOrder("admin:secret", "CVE-2023-1");
  }

  @Test
  @DisplayName("Endpoint relations returns the targeting executions and grouped edge in one query")
  void relations_returns_executions_and_grouped_edge() {
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    AttackPathEndpointRelationsDTO dto = service.endpointRelations(SIM, "dc-01");
    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);

    assertThat(dto.executions()).hasSize(2);
    assertThat(dto.edges()).hasSize(1);
    AttackPathEdges edge = dto.edges().get(0);
    assertThat(edge.getCount()).isEqualTo(2);
    assertThat(edge.getExecutionIds()).containsExactlyInAnyOrder(exec1Id, exec2Id);
  }

  @Test
  @DisplayName("Expand and relations work for a discovered (raw-value) endpoint")
  void expand_and_relations_for_raw_endpoint() {
    String raw = "honey.scanme.sh";
    String rawExecId = rawExecution("NUCLEI", raw, "Not Prevented", "Not Detected", at(10));

    AttackPathFinding rawFinding = new AttackPathFinding();
    rawFinding.setTenant(tenant);
    rawFinding.setSimulationId(SIM);
    rawFinding.setType("cve");
    rawFinding.setValue("CVE-2024-9");
    rawFinding.setEndpointKey(raw); // endpoint_id stays null: a discovered endpoint has no asset id
    String rawFindingId = findingRepository.save(rawFinding).getId();
    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(rawExecId);
    link.setFindingId(rawFindingId);
    entityManager.persist(link);
    entityManager.flush();

    AttackPathExpandDTO expand = service.expandEndpoint(SIM, raw);
    assertThat(expand.findings())
        .extracting(AttackPathNodeDTO::getValue)
        .containsExactly("CVE-2024-9");

    AttackPathEndpointRelationsDTO relations = service.endpointRelations(SIM, raw);
    assertThat(relations.executions()).hasSize(1);
    assertThat(relations.edges()).hasSize(1);
  }

  // --- seeding helpers ---

  /** A persisted injector contract with one ATT&CK pattern, keyed by external id. */
  private void seedContractWithPattern(String contractExternalId, String patternExternalId) {
    injectorContractComposer
        .forInjectorContract(
            InjectorContractFixture.createDefaultInjectorContractWithExternalId(contractExternalId))
        .withAttackPattern(
            attackPatternComposer.forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId(patternExternalId)))
        .persist();
  }

  /** An injector run carrying the frozen contract external id and injector type. */
  private String seedInjectorRun(
      String injector,
      String targetKey,
      String hostname,
      String contractExternalId,
      String injectorType,
      Instant executedAt) {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector(injector);
    e.setContractExternalId(contractExternalId);
    e.setInjectorType(injectorType);
    e.setTargetKind("ASSET");
    e.setTargetAssetId(targetKey);
    e.setTargetKey(targetKey);
    e.setTargetHostname(hostname);
    e.setTargetPlatform("Windows");
    e.setPreventionStatus("Prevented");
    e.setExecutedAt(executedAt);
    return executionRepository.save(e).getId();
  }

  private String injectorExecution(
      String injector,
      String targetKey,
      String hostname,
      String prevention,
      String detection,
      String payload,
      Instant executedAt) {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector(injector);
    e.setTargetKind("ASSET");
    e.setTargetAssetId(targetKey);
    e.setTargetKey(targetKey);
    e.setTargetHostname(hostname);
    e.setTargetIp("10.10.0.1");
    e.setTargetPlatform("Windows");
    e.setPreventionStatus(prevention);
    e.setDetectionStatus(detection);
    e.setPayloadName(payload);
    e.setExecutedAt(executedAt);
    return executionRepository.save(e).getId();
  }

  private String rawExecution(
      String injector, String rawValue, String prevention, String detection, Instant executedAt) {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector(injector);
    e.setTargetKind("RAW");
    e.setTargetRawValue(rawValue);
    e.setTargetKey(rawValue);
    e.setPreventionStatus(prevention);
    e.setDetectionStatus(detection);
    e.setPayloadName("Nuclei Scan");
    e.setExecutedAt(executedAt);
    return executionRepository.save(e).getId();
  }

  private void pivotExecution(
      String sourceKey,
      String agentName,
      String targetKey,
      String hostname,
      String prevention,
      String detection,
      Instant executedAt) {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("AGENT_ASSET");
    e.setSourceAssetId(sourceKey);
    e.setAgentId("agent-1");
    e.setAgentName(agentName);
    e.setAgentPrivilege("Domain Admin");
    e.setTargetKind("ASSET");
    e.setTargetAssetId(targetKey);
    e.setTargetKey(targetKey);
    e.setTargetHostname(hostname);
    e.setPreventionStatus(prevention);
    e.setDetectionStatus(detection);
    e.setExecutedAt(executedAt);
    executionRepository.save(e);
  }

  private void finding(String type, String value, String endpointKey, String executionId) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType(type);
    f.setValue(value);
    f.setEndpointId(endpointKey);
    f.setEndpointKey(endpointKey);
    String findingId = findingRepository.save(f).getId();

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(executionId);
    link.setFindingId(findingId);
    entityManager.persist(link);
  }

  private static Instant at(int minute) {
    return Instant.parse("2026-06-18T08:00:00Z").plusSeconds(60L * minute);
  }

  private AttackPathNodeDTO nodeById(AttackPathDTO dto, String id) {
    return dto.attackPathNodes().stream()
        .filter(n -> id.equals(n.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("node not found: " + id));
  }

  private AttackPathEdges edgeById(AttackPathDTO dto, String id) {
    return dto.attackPathEdges().stream()
        .filter(e -> id.equals(e.getEdgeId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("edge not found: " + id));
  }
}
