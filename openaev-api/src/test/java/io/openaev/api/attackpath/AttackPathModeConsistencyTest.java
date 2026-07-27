package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathExpandDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full and collapsed must agree for one simulation (issue 6647, audit CF1). The invariant: a
 * finding is in the graph iff a producing execution links to it. A finding with no execution link
 * is out of the graph in BOTH modes; it must not be counted by collapsed/expand while dropped by
 * full, which would make the top-bar counters and the visible finding set jump when the collapse
 * threshold is crossed for the identical simulation.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path full and collapsed modes are consistent")
class AttackPathModeConsistencyTest extends IntegrationTest {

  private static final String SIM = "SIM-CONSISTENCY";

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  private Tenant tenant;

  @BeforeEach
  void seed() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-consistency-tenant"));
    // One execution on host-x produces a linked credentials finding.
    String execId = execution("nmap", "host-x", "Prevented", "Not Detected");
    linkedFinding("host-x", "credentials", "admin:secret", execId);
    // A cve finding on host-x that no execution produced (orphan): must be invisible in both modes.
    orphanFinding("host-x", "cve", "CVE-2026-9");

    // Endpoints covering the severity matrix (no findings, so the finding counters above are
    // unaffected). host-x is already GREEN (prevented). These exercise the collapsed severity SQL,
    // including RED and the null-status branch, against the in-memory full-mode severity.
    execution("nmap", "host-red", "Not Prevented", "Not Detected"); // -> RED
    execution("nmap", "host-null", null, null); // -> RED via the null-safe branch
    execution("nmap", "host-orange", "Prevented", "Not Detected");
    execution("hydra", "host-orange", "Not Prevented", "Detected"); // worst-case -> ORANGE
    execution("nmap", "host-mixed-null", "Prevented", "Not Detected");
    execution("hydra", "host-mixed-null", null, null); // null row must still rank RED (worst-case)
    entityManager.flush();
  }

  @Test
  @DisplayName("counters agree between full and collapsed, ignoring findings with no producer")
  void full_and_collapsed_counters_agree() {
    AttackPathDTO full = graphService.buildGraph(SIM, "full");
    AttackPathDTO collapsed = graphService.buildGraph(SIM, "collapsed");

    assertThat(collapsed.counters().credentials()).isEqualTo(full.counters().credentials());
    assertThat(collapsed.counters().cves()).isEqualTo(full.counters().cves());
    assertThat(collapsed.counters().users()).isEqualTo(full.counters().users());
    assertThat(collapsed.counters().ports()).isEqualTo(full.counters().ports());

    assertThat(full.counters().credentials()).as("the linked credential is counted").isEqualTo(1);
    assertThat(full.counters().cves()).as("the orphan cve has no producer").isZero();
  }

  @Test
  @DisplayName("expand ignores findings with no producing execution")
  void expand_ignores_orphan_findings() {
    AttackPathExpandDTO expand = graphService.expandEndpoint(SIM, "host-x");
    assertThat(expand.findings())
        .extracting(AttackPathNodeDTO::getValue)
        .containsExactly("admin:secret");
  }

  @Test
  @DisplayName("full and collapsed agree on endpoint severity, including RED and null statuses")
  void full_and_collapsed_agree_on_severity() {
    Map<String, String> full = coloursByEndpoint(graphService.buildGraph(SIM, "full"));
    Map<String, String> collapsed = coloursByEndpoint(graphService.buildGraph(SIM, "collapsed"));

    assertThat(collapsed)
        .as("collapsed severity SQL matches the in-memory full-mode severity per endpoint")
        .isEqualTo(full);
    assertThat(full)
        .containsEntry(AttackPathIds.endpointNode("host-x"), "GREEN")
        .containsEntry(AttackPathIds.endpointNode("host-orange"), "ORANGE")
        .containsEntry(AttackPathIds.endpointNode("host-red"), "RED")
        .containsEntry(AttackPathIds.endpointNode("host-null"), "RED")
        .containsEntry(AttackPathIds.endpointNode("host-mixed-null"), "RED");
  }

  private static Map<String, String> coloursByEndpoint(AttackPathDTO dto) {
    Map<String, String> colours = new LinkedHashMap<>();
    for (AttackPathNodeDTO n : dto.attackPathNodes()) {
      if ("ASSET".equals(n.getType())) {
        colours.put(n.getId(), n.getStatus());
      }
    }
    return colours;
  }

  private String execution(String injector, String endpoint, String prevention, String detection) {
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
    e.setDetectionStatus(detection);
    return executionRepository.save(e).getId();
  }

  private void linkedFinding(String endpoint, String type, String value, String executionId) {
    String findingId = saveFinding(endpoint, type, value);
    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(executionId);
    link.setFindingId(findingId);
    entityManager.persist(link);
  }

  private void orphanFinding(String endpoint, String type, String value) {
    saveFinding(endpoint, type, value);
  }

  private String saveFinding(String endpoint, String type, String value) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType(type);
    f.setValue(value);
    f.setEndpointId(endpoint);
    f.setEndpointKey(endpoint);
    return findingRepository.save(f).getId();
  }
}
