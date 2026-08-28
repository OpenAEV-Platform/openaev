package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.dto.AttackPathAttackPatternDTO;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathEdges;
import io.openaev.service.attackpath.dto.AttackPathEndpointRelationsDTO;
import io.openaev.service.attackpath.dto.AttackPathExpandDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import java.time.Instant;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuild service: two flat reads plus one in-memory pass produce {@code {nodes, edges, counters}}
 * with the deterministic IDs, and the read path issues a constant number of SQL statements
 * regardless of graph size (the two flat reads plus the bounded per-run contract/technique
 * lookups).
 */
@Transactional
class AttackPathGraphServiceTest extends IntegrationTest {

  private static final String SIM = "SIM-GRAPH";

  @Autowired private AttackPathGraphService service;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ExerciseComposer simulationComposer;

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
  @DisplayName(
      "An execution feed node carries its inject and its execution status, resolved from the step")
  void fills_execution_status_from_the_step() {
    // A run's inject id lives in the durable step's data (the frozen row only keys the step), and
    // its
    // "did it run" status on the inject itself - exactly what the Result drawer's detail read
    // resolves.
    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(InjectStatusFixture.createSuccessStatus()))
            .persist()
            .get();
    Step step =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .data("{\"inject_id\": \"" + inject.getId() + "\"}")
            .build();
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();
    String execId =
        injectorExecution(
            "NMAP", "status-01", "CORP-STATUS-01", "Not Prevented", "Not Detected", "Nmap", at(9));
    AttackPathExecution row = executionRepository.findById(execId).orElseThrow();
    row.setStepId(step.getId());
    executionRepository.save(row);
    entityManager.flush();
    entityManager.clear();

    AttackPathDTO dto = service.buildGraph(SIM);

    String feedNodeId = AttackPathIds.executionNode(execId, "status-01", null);
    AttackPathNodeDTO feedNode =
        dto.attackPathExecutions().stream()
            .filter(n -> feedNodeId.equals(n.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(feedNode.getInjectId()).isEqualTo(inject.getId());
    assertThat(feedNode.getExecutionStatus())
        .as("shipped with the graph, so a list of executions renders it without a round-trip")
        .isEqualTo("EXECUTED");
    // A row with no step resolves to nothing rather than guessing.
    String noStepNodeId = AttackPathIds.executionNode(exec1Id, "dc-01", null);
    AttackPathNodeDTO noStepNode =
        dto.attackPathExecutions().stream()
            .filter(n -> noStepNodeId.equals(n.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(noStepNode.getInjectId()).isNull();
    assertThat(noStepNode.getExecutionStatus()).isNull();
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
  @DisplayName("An endpoint node is enriched with its asset's criticality, name and seen IP")
  void endpoint_node_carries_asset_criticality_name_and_seen_ip() {
    // A real backing asset: the enrichment pass resolves criticality, name and seen IP live from
    // it (one batched read), so the map node shows a single relevant IP instead of the frozen list.
    Endpoint asset = EndpointFixture.createEndpoint("DC Primary");
    asset.setCriticality(AssetCriticality.VERY_HIGH);
    asset.setSeenIp("203.0.113.7");
    String assetId = assetRepository.save(asset).getId();
    injectorExecution(
        "NMAP", assetId, "CORP-DC-01", "Prevented", "Not Detected", "Nmap Scan", at(50));
    entityManager.flush();

    AttackPathNodeDTO node = nodeById(service.buildGraph(SIM), AttackPathIds.endpointNode(assetId));
    assertThat(node.getSeenIp())
        .as("the node carries the asset's seen (primary) IP, resolved live")
        .isEqualTo("203.0.113.7");
    assertThat(node.getCriticality()).isEqualTo("VERY_HIGH");
    assertThat(node.getLabel())
        .as("the asset's friendly name wins over the raw id fallback")
        .isEqualTo("DC Primary");
  }

  @Test
  @DisplayName("An asset with a blank seen IP leaves seenIp null (front falls back to the ip list)")
  void endpoint_node_with_blank_seen_ip_stays_null() {
    Endpoint asset = EndpointFixture.createEndpoint("Blank seen IP");
    asset.setSeenIp("");
    String assetId = assetRepository.save(asset).getId();
    injectorExecution(
        "NMAP", assetId, "CORP-APP-02", "Prevented", "Not Detected", "Nmap Scan", at(51));
    entityManager.flush();

    AttackPathNodeDTO node = nodeById(service.buildGraph(SIM), AttackPathIds.endpointNode(assetId));
    assertThat(node.getSeenIp())
        .as("a blank seen IP is omitted from the DTO so the front falls back to the frozen list")
        .isNull();
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
  @DisplayName(
      "Collapsed mode splits the injector node per contract, with its own techniques, label and edges")
  void collapsed_injector_node_splits_per_contract() {
    seedContractWithPattern("C-CA", "T1003");
    // A second contract with BOTH an attack pattern and a resolvable label (FR4 in collapsed).
    InjectorContract cb =
        InjectorContractFixture.createDefaultInjectorContractWithExternalId("C-CB");
    cb.setLabels(Map.of("en", "Kerberoasting"));
    injectorContractComposer
        .forInjectorContract(cb)
        .withAttackPattern(
            attackPatternComposer.forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId("T1004")))
        .persist();
    // Same injector "COLLSPLIT", two contracts on two targets.
    seedInjectorRun("COLLSPLIT", "cg-a", "CG-A", "C-CA", "openaev_impl", at(41));
    seedInjectorRun("COLLSPLIT", "cg-b", "CG-B", "C-CB", "openaev_impl", at(42));
    entityManager.flush();

    AttackPathDTO dto = service.buildGraph(SIM, "collapsed");
    AttackPathNodeDTO nodeA = nodeById(dto, AttackPathIds.injectorNode("COLLSPLIT", "C-CA"));
    AttackPathNodeDTO nodeB = nodeById(dto, AttackPathIds.injectorNode("COLLSPLIT", "C-CB"));
    // Each per-contract node shows only its own contract's technique.
    assertThat(nodeA.getAttackPatterns())
        .extracting(AttackPathAttackPatternDTO::externalId)
        .containsExactly("T1003");
    assertThat(nodeB.getAttackPatterns())
        .extracting(AttackPathAttackPatternDTO::externalId)
        .containsExactly("T1004");
    // FR4 label in collapsed: the labelled contract's name; the label-less one falls back.
    assertThat(nodeB.getLabel()).isEqualTo("Kerberoasting");
    assertThat(nodeA.getLabel()).isEqualTo("COLLSPLIT");
    // The grouped edges attach to the per-contract sources.
    assertThat(dto.attackPathEdges())
        .extracting(AttackPathEdges::getEdgeSourceId)
        .contains(
            AttackPathIds.injectorNode("COLLSPLIT", "C-CA"),
            AttackPathIds.injectorNode("COLLSPLIT", "C-CB"));
  }

  @Test
  @DisplayName(
      "An injector running several contracts renders one node per contract, each with its own"
          + " technique")
  void injector_node_splits_per_contract() {
    seedContractWithPattern("C-A", "T1001");
    seedContractWithPattern("C-B", "T1002");
    // Same injector "SPRAY", two runs under two different contracts.
    seedInjectorRun("SPRAY", "h-a", "H-A", "C-A", "openaev_impl", at(12));
    seedInjectorRun("SPRAY", "h-b", "H-B", "C-B", "openaev_impl", at(13));
    entityManager.flush();

    AttackPathDTO dto = service.buildGraph(SIM);
    // Two distinct injector nodes, one per contract (not one merged node). Selected by id, since
    // the
    // label-less fixture contracts make both nodes fall back to the injector name "SPRAY".
    AttackPathNodeDTO nodeA = nodeById(dto, AttackPathIds.injectorNode("SPRAY", "C-A"));
    AttackPathNodeDTO nodeB = nodeById(dto, AttackPathIds.injectorNode("SPRAY", "C-B"));
    assertThat(nodeA.getType()).isEqualTo("INJECTOR");
    assertThat(nodeB.getType()).isEqualTo("INJECTOR");
    // Each node shows only its own contract's technique, not the union across the injector.
    assertThat(nodeA.getAttackPatterns())
        .extracting(AttackPathAttackPatternDTO::externalId)
        .containsExactly("T1001");
    assertThat(nodeB.getAttackPatterns())
        .extracting(AttackPathAttackPatternDTO::externalId)
        .containsExactly("T1002");
  }

  @Test
  @DisplayName("The injector node label is its contract's name (fallback to the injector name)")
  void injector_node_label_is_contract_name() {
    // The default fixture contract carries no labels; set one so the node can present the contract
    // name instead of the injector name.
    InjectorContract labelled =
        InjectorContractFixture.createDefaultInjectorContractWithExternalId("C-LABEL");
    labelled.setLabels(Map.of("en", "Share Listing"));
    injectorContractComposer.forInjectorContract(labelled).persist();
    seedInjectorRun("NETEXEC", "srv-9", "SRV-9", "C-LABEL", "openaev_netexec", at(14));
    entityManager.flush();

    AttackPathNodeDTO node =
        nodeById(service.buildGraph(SIM), AttackPathIds.injectorNode("NETEXEC", "C-LABEL"));
    assertThat(node.getLabel()).isEqualTo("Share Listing");
  }

  @Test
  @DisplayName(
      "A contractless injector keeps the 2-segment per-injector id and injector-name label, full and"
          + " collapsed")
  void contractless_injector_falls_back_to_per_injector_id() {
    // The @BeforeEach NMAP runs carry no contract, so their node is the 2-segment per-injector id,
    // byte-identical to the pre-split form, and its label falls back to the injector name.
    String id = AttackPathIds.injectorNode("NMAP");
    assertThat(id).isEqualTo("NODE_INJECTOR|NMAP");

    AttackPathNodeDTO full = nodeById(service.buildGraph(SIM), id);
    assertThat(full.getType()).isEqualTo("INJECTOR");
    assertThat(full.getLabel()).isEqualTo("NMAP");

    AttackPathNodeDTO collapsed = nodeById(service.buildGraph(SIM, "collapsed"), id);
    assertThat(collapsed.getType()).isEqualTo("INJECTOR");
    assertThat(collapsed.getLabel()).isEqualTo("NMAP");
  }

  @Test
  @DisplayName(
      "An injector with both a contract run and a contractless run renders both a per-contract and a"
          + " per-injector node")
  void injector_with_mixed_contract_renders_both_nodes() {
    seedContractWithPattern("C-MIX", "T1005");
    // Same injector "MIXED": one run under a contract, one run with no contract.
    seedInjectorRun("MIXED", "mx-a", "MX-A", "C-MIX", "openaev_impl", at(43));
    injectorExecution("MIXED", "mx-b", "MX-B", "Prevented", "Not Detected", "Scan", at(44));
    entityManager.flush();

    AttackPathDTO dto = service.buildGraph(SIM);
    // The contract run is a 3-segment per-contract node; the contractless run is the 2-segment
    // node.
    assertThat(nodeById(dto, AttackPathIds.injectorNode("MIXED", "C-MIX")).getType())
        .isEqualTo("INJECTOR");
    assertThat(nodeById(dto, AttackPathIds.injectorNode("MIXED")).getType()).isEqualTo("INJECTOR");
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
  @DisplayName("Endpoint relations returns the targeting executions and grouped edge")
  void relations_returns_executions_and_grouped_edge() {
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    AttackPathEndpointRelationsDTO dto =
        service.endpointRelations(SIM, "dc-01", PageRequest.of(0, 50));
    // A bounded, constant number of statements whatever the endpoint's size: the edge set, the feed
    // page and its total. Not one any more (#6647, spec 003: the feed is paged), but still not a
    // function of the row count.
    assertThat(stats.getPrepareStatementCount()).isEqualTo(3);

    assertThat(dto.executions()).hasSize(2);
    assertThat(dto.totalExecutions()).isEqualTo(2);
    assertThat(dto.edges()).hasSize(1);
    AttackPathEdges edge = dto.edges().get(0);
    assertThat(edge.getCount()).isEqualTo(2);
    assertThat(edge.getExecutionIds()).containsExactlyInAnyOrder(exec1Id, exec2Id);
  }

  @Test
  @DisplayName("Endpoint relations page the feed but keep the edges whole")
  void relations_page_the_feed_and_keep_edges_whole() {
    // One execution per page, so the second one is only reachable through page 1 — while the edge
    // must still carry BOTH, because the front correlates edges against executions it may not have
    // fetched yet.
    AttackPathEndpointRelationsDTO first =
        service.endpointRelations(SIM, "dc-01", PageRequest.of(0, 1));
    assertThat(first.executions()).hasSize(1);
    assertThat(first.totalExecutions())
        .as("the client learns there is more without a second read")
        .isEqualTo(2);
    assertThat(first.edges().get(0).getExecutionIds())
        .as("edges are whole: bounded by the endpoint's in-degree, not by the page")
        .containsExactlyInAnyOrder(exec1Id, exec2Id);

    AttackPathEndpointRelationsDTO second =
        service.endpointRelations(SIM, "dc-01", PageRequest.of(1, 1));
    assertThat(second.executions()).hasSize(1);
    // Stable ordering (executedAt, id): the two pages are disjoint, so nothing is shown twice or
    // skipped between them.
    assertThat(second.executions().get(0).getRef())
        .isNotEqualTo(first.executions().get(0).getRef());
  }

  @Test
  @DisplayName(
      "Endpoint relations expose the same per-contract injector source id as the full graph")
  void relations_use_per_contract_injector_source_id() {
    // The front correlates /graph and /endpoint/relations by edgeSourceId string-equality, so both
    // must emit the SAME per-contract id for a given (injector, contract).
    seedContractWithPattern("C-REL", "T1210");
    seedInjectorRun("WINRM", "ep-rel", "EP-REL", "C-REL", "openaev_impl", at(40));
    entityManager.flush();

    String expectedSource = AttackPathIds.injectorNode("WINRM", "C-REL");
    AttackPathEndpointRelationsDTO relations =
        service.endpointRelations(SIM, "ep-rel", PageRequest.of(0, 50));
    assertThat(relations.edges())
        .singleElement()
        .satisfies(e -> assertThat(e.getEdgeSourceId()).isEqualTo(expectedSource));
    assertThat(nodeById(service.buildGraph(SIM), expectedSource).getType()).isEqualTo("INJECTOR");
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

    AttackPathEndpointRelationsDTO relations =
        service.endpointRelations(SIM, raw, PageRequest.of(0, 50));
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

  @Test
  @DisplayName("A real finding node is flagged is_finding=true")
  void realFinding_node_isFinding_true() {
    AttackPathDTO dto = service.buildGraph(SIM);
    AttackPathNodeDTO node =
        nodeById(dto, AttackPathIds.findingNode("credentials", "admin:secret"));
    assertThat(node.getIsFinding()).isTrue();
  }

  @Test
  @DisplayName(
      "An output-only value (is_finding=false) is rendered as a node flagged is_finding=false")
  void outputOnly_node_isFinding_false() {
    // A chaining output not persisted as a Finding (ADR-004): still on the map, flagged
    // output-only.
    finding("service_banner", "OpenSSH 9.0", "dc-01", exec1Id, false);
    entityManager.flush();

    AttackPathDTO dto = service.buildGraph(SIM);
    AttackPathNodeDTO node =
        nodeById(dto, AttackPathIds.findingNode("service_banner", "OpenSSH 9.0"));
    assertThat(node.getType()).isEqualTo("FINDING");
    assertThat(node.getIsFinding()).isFalse();
  }

  @Test
  @DisplayName("A (type,value) produced both as a finding and as an output keeps is_finding=true")
  void mixed_finding_and_output_node_isFinding_true() {
    // The same (type, value) is produced once as a real finding and once as an output-only value:
    // the deduped node keeps is_finding=true (a real finding wins, ADR-004).
    finding("port", "22", "dc-01", exec1Id, true);
    finding("port", "22", "app-01", exec2Id, false);
    entityManager.flush();

    AttackPathDTO dto = service.buildGraph(SIM);
    AttackPathNodeDTO node = nodeById(dto, AttackPathIds.findingNode("port", "22"));
    assertThat(node.getIsFinding()).isTrue();
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
    finding(type, value, endpointKey, executionId, true);
  }

  private void finding(
      String type, String value, String endpointKey, String executionId, boolean isFinding) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType(type);
    f.setValue(value);
    f.setEndpointId(endpointKey);
    f.setEndpointKey(endpointKey);
    f.setFinding(isFinding);
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
