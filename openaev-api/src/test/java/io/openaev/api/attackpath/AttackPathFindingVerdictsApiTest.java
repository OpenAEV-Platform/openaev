package io.openaev.api.attackpath;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-finding verdicts (A2), through the real endpoints. A finding node/row carries a {@code
 * verdicts} triple ({@code success|failed|unknown}) derived from its producing executions and
 * worst-of aggregated. Covers the four carriers: expand (default view, per endpoint), the graph
 * FINDING node (cross-endpoint rollup), the drawer row, and the execution detail item.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH")
@DisplayName("attack path: per-finding verdicts on every read")
class AttackPathFindingVerdictsApiTest extends IntegrationTest {

  private static final String SIM = "SIM-VERDICTS";
  private static final String CVE = "CVE-2026-1";

  @Autowired private MockMvc mvc;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-verdicts"));
  }

  @Test
  @DisplayName(
      "expand: worst-of across producers on one endpoint, and unknown for a status-less producer")
  void expandWorstOfAndUnknown() throws Exception {
    AttackPathExecution prevented = save(execution("host-a", "Prevented"));
    AttackPathExecution notPrevented = save(execution("host-a", "Not Prevented"));
    AttackPathFinding divergent = save(finding("host-a", CVE));
    link(prevented, divergent);
    link(notPrevented, divergent);

    AttackPathExecution noStatus = save(execution("host-a", null));
    AttackPathFinding unknown = save(finding("host-a", "CVE-2026-9"));
    link(noStatus, unknown);
    entityManager.flush();

    expand("host-a")
        .andExpect(verdict(CVE, "prevention", "failed"))
        .andExpect(verdict("CVE-2026-9", "prevention", "unknown"));
  }

  @Test
  @DisplayName(
      "the same finding diverges per endpoint on expand, and the graph node is the worst-of rollup")
  void perEndpointExpandAndCrossEndpointGraphNode() throws Exception {
    // Same (type, value) prevented on host-a, not prevented on host-b.
    link(save(execution("host-a", "Prevented")), save(finding("host-a", CVE)));
    link(save(execution("host-b", "Not Prevented")), save(finding("host-b", CVE)));
    entityManager.flush();

    // Expand is per endpoint: the verdict differs by endpoint.
    expand("host-a").andExpect(verdict(CVE, "prevention", "success"));
    expand("host-b").andExpect(verdict(CVE, "prevention", "failed"));

    // The single FINDING node of the full graph rolls up across endpoints: worst-of = failed.
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "full"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.attackPathNodes[?(@.type=='FINDING' && @.value=='"
                        + CVE
                        + "')].verdicts.prevention")
                .value(hasItem("failed")));
  }

  @Test
  @DisplayName(
      "drawer rows carry the aggregated verdict; execution detail items carry the execution's own")
  void drawerAndExecutionDetailCarryVerdicts() throws Exception {
    AttackPathExecution exec = execution("host-a", null);
    exec.setDetectionStatus("Detected"); // detection success label
    exec.setVulnerabilityStatus("Vulnerable"); // vulnerability failure label
    exec = executionRepository.save(exec);
    link(exec, save(finding("host-a", CVE)));
    entityManager.flush();

    // Drawer: worst-of of the row's producers (here one, so its own triple).
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/findings")
                .param("category", "cves"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].verdicts.detection").value("success"))
        .andExpect(jsonPath("$.items[0].verdicts.vulnerability").value("failed"));

    // Execution detail: this execution's own triple + the new vulnerabilityStatus field.
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/execution")
                .param("ref", exec.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vulnerabilityStatus").value("Vulnerable"))
        .andExpect(jsonPath("$.findings[0].verdicts.detection").value("success"))
        .andExpect(jsonPath("$.findings[0].verdicts.vulnerability").value("failed"));
  }

  // -- helpers --

  private ResultActions expand(String endpointKey) throws Exception {
    return mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/endpoint/findings")
                .param("ref", endpointKey))
        .andExpect(status().isOk());
  }

  private ResultMatcher verdict(String value, String bucket, String expected) {
    return jsonPath("$.findings[?(@.value=='" + value + "')].verdicts." + bucket)
        .value(hasItem(expected));
  }

  private AttackPathExecution execution(String endpointKey, String preventionStatus) {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector("nmap");
    e.setTargetKind("ASSET");
    e.setTargetAssetId(endpointKey);
    e.setTargetKey(endpointKey);
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    e.setPreventionStatus(preventionStatus);
    return e;
  }

  private AttackPathExecution save(AttackPathExecution e) {
    return executionRepository.save(e);
  }

  private AttackPathFinding finding(String endpointKey, String value) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType("cve");
    f.setValue(value);
    f.setEndpointId(endpointKey);
    f.setEndpointKey(endpointKey);
    return f;
  }

  private AttackPathFinding save(AttackPathFinding f) {
    return findingRepository.save(f);
  }

  private void link(AttackPathExecution execution, AttackPathFinding finding) {
    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execution.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
  }
}
