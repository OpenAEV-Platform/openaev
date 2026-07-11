package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collapsed graph mode (issue 6647, ADR-002): the DB-aggregated view returned for large
 * simulations, and the automatic full/collapsed switch. Seeds a tiny graph and drives the service
 * directly; the threshold is set low so a small simulation exercises the switch.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH_POC",
      "openaev.attackpath.collapse-threshold=2"
    })
@DisplayName("attack path collapsed mode and automatic switching")
class AttackPathCollapsedTest extends IntegrationTest {

  private static final String SIM = "SIM-COLLAPSED";

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  @BeforeEach
  void seedSmallGraph() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("collapsed-tenant"));
    // dc-01 gets a Prevented and a Not-Prevented execution (mixed -> ORANGE); dc-02 gets two
    // Prevented (all -> GREEN). nmap sprays both, hydra only hits dc-01.
    execution(tenant, "nmap", "dc-01", "Prevented");
    execution(tenant, "hydra", "dc-01", "Not Prevented");
    execution(tenant, "nmap", "dc-02", "Prevented");
    execution(tenant, "nmap", "dc-02", "Prevented");
    // dc-01: one credential + one CVE; dc-02: the same credential value (shared).
    finding(tenant, "dc-01", "credentials", "admin:secret");
    finding(tenant, "dc-01", "cve", "CVE-2026-1");
    finding(tenant, "dc-02", "credentials", "admin:secret");
    entityManager.flush();
  }

  @Test
  @DisplayName("collapsed returns aggregated endpoint groups, grouped edges and counters")
  void collapsedShape() {
    AttackPathDTO dto = graphService.buildCollapsedGraph(SIM);

    assertThat(dto.mode()).isEqualTo("collapsed");
    assertThat(dto.attackPathExecutions()).as("no per-execution feed in collapsed").isEmpty();
    assertThat(dto.staticAttackPathFindings()).as("no per-finding nodes in collapsed").isEmpty();

    List<AttackPathNodeDTO> endpoints = nodesOfType(dto, "ASSET");
    List<AttackPathNodeDTO> injectors = nodesOfType(dto, "INJECTOR");
    assertThat(endpoints).as("one node per endpoint").hasSize(2);
    assertThat(injectors).as("nmap and hydra").hasSize(2);

    AttackPathNodeDTO dc01 = endpointByLabel(endpoints, "dc-01");
    AttackPathNodeDTO dc02 = endpointByLabel(endpoints, "dc-02");
    assertThat(dc01.getStatus()).as("mixed prevention -> ORANGE").isEqualTo("ORANGE");
    assertThat(dc02.getStatus()).as("all prevented -> GREEN").isEqualTo("GREEN");
    assertThat(dc01.getFindingCounts()).containsEntry("credentials", 1L).containsEntry("cve", 1L);
    assertThat(dc02.getFindingCounts()).containsEntry("credentials", 1L);

    // nmap->dc-01, hydra->dc-01, nmap->dc-02 (count 2): three grouped edges.
    assertThat(dto.attackPathEdges()).hasSize(3);
    assertThat(dto.attackPathEdges().stream().mapToInt(e -> e.getCount()).sum())
        .as("edges group all four executions")
        .isEqualTo(4);

    assertThat(dto.counters().endpoints()).isEqualTo(2);
    assertThat(dto.counters().credentials()).as("distinct credential value, shared").isEqualTo(1);
    assertThat(dto.counters().cves()).isEqualTo(1);
  }

  @Test
  @DisplayName("above the threshold the graph auto-switches to collapsed")
  void autoSwitchAboveThreshold() {
    assertThat(graphService.buildGraph(SIM, null).mode()).isEqualTo("collapsed");
  }

  @Test
  @DisplayName("the mode parameter forces full or collapsed")
  void modeOverride() {
    assertThat(graphService.buildGraph(SIM, "full").mode()).isEqualTo("full");
    assertThat(graphService.buildGraph(SIM, "collapsed").mode()).isEqualTo("collapsed");
  }

  private void execution(Tenant tenant, String injector, String endpoint, String prevention) {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector(injector);
    e.setTargetKind("ASSET");
    e.setTargetAssetId(endpoint);
    e.setTargetKey(endpoint);
    e.setTargetHostname(endpoint);
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    e.setPreventionStatus(prevention);
    executionRepository.save(e);
  }

  private void finding(Tenant tenant, String endpoint, String type, String value) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType(type);
    f.setValue(value);
    f.setEndpointId(endpoint);
    f.setEndpointKey(endpoint);
    findingRepository.save(f);
  }

  private static List<AttackPathNodeDTO> nodesOfType(AttackPathDTO dto, String type) {
    return dto.attackPathNodes().stream().filter(n -> type.equals(n.getType())).toList();
  }

  private static AttackPathNodeDTO endpointByLabel(List<AttackPathNodeDTO> nodes, String label) {
    return nodes.stream()
        .filter(n -> label.equals(n.getLabel()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("endpoint node not found: " + label));
  }
}
