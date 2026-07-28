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
import org.springframework.transaction.annotation.Transactional;

/**
 * SMB {@code share} findings present as the native {@code file} type across the attack-path reads,
 * via the interim {@code share -> file} stand-in. A persisted {@code share} finding reads {@code
 * typeFindings == "file"} on the endpoint expand, the graph node, the drawer, the execution detail,
 * and the {@code files} counter.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH")
@DisplayName("attack path: share findings present as the native file type")
class AttackPathFileTypeApiTest extends IntegrationTest {

  private static final String SIM = "SIM-FILE";
  private static final String ENDPOINT = "dc-01";
  private static final String SHARE_VALUE = "public-share-01";

  @Autowired private MockMvc mvc;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-file-type"));
  }

  @Test
  @DisplayName("expand: a share finding reads type 'file'")
  void expandPresentsShareAsFile() throws Exception {
    seedShare(SHARE_VALUE);

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/endpoint/findings")
                .param("ref", ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.findings[?(@.value=='" + SHARE_VALUE + "')].typeFindings")
                .value(hasItem("file")));
  }

  @Test
  @DisplayName(
      "file appears consistently on graph node, counter, collapsed, drawer, execution detail")
  void fileAppearsOnEveryRead() throws Exception {
    String executionId = seedShare(SHARE_VALUE);

    // Full graph: the FINDING node presents file, and the real files counter is populated.
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "full"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.files").value(1))
        .andExpect(
            jsonPath("$.attackPathNodes[?(@.value=='" + SHARE_VALUE + "')].typeFindings")
                .value(hasItem("file")));

    // Collapsed (default view): the top-bar files counter AND the per-endpoint findingCounts.file
    // (what the front's focused count reads) are populated — second counter switch +
    // presentEndpointTypeCounts.
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "collapsed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.files").value(1))
        .andExpect(
            jsonPath("$.attackPathNodes[?(@.type=='ASSET')].findingCounts.file").value(hasItem(1)));

    // Drawer: the files category lists the finding as file.
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/findings")
                .param("category", "files"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].type").value("file"))
        .andExpect(jsonPath("$.items[0].value").value(SHARE_VALUE));

    // Execution detail: the produced finding reads file (attack-path store).
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/execution")
                .param("ref", executionId))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.findings[?(@.value=='" + SHARE_VALUE + "')].type").value(hasItem("file")));
  }

  @Test
  @DisplayName("day one: no shares -> files counter is 0, no file nodes")
  void dayOneNoShares() throws Exception {
    // A CVE finding only: the files counter stays 0 and nothing presents as file.
    AttackPathExecution execution = execution();
    AttackPathFinding cve = new AttackPathFinding();
    cve.setTenant(tenant);
    cve.setSimulationId(SIM);
    cve.setType("cve");
    cve.setValue("CVE-2026-1");
    cve.setEndpointId(ENDPOINT);
    cve.setEndpointKey(ENDPOINT);
    cve = findingRepository.save(cve);
    link(execution, cve);
    entityManager.flush();

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "full"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.files").value(0));
  }

  /** A share finding needs a producing execution + link to appear on any read (graph invariant). */
  private String seedShare(String value) {
    AttackPathExecution execution = execution();
    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(SIM);
    finding.setType("share");
    finding.setValue(value);
    finding.setEndpointId(ENDPOINT);
    finding.setEndpointKey(ENDPOINT);
    finding = findingRepository.save(finding);
    link(execution, finding);
    entityManager.flush();
    return execution.getId();
  }

  private AttackPathExecution execution() {
    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(SIM);
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("nmap");
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId(ENDPOINT);
    execution.setTargetKey(ENDPOINT);
    execution.setTargetHostname("CORP-DC-01");
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setPreventionStatus("Prevented");
    return executionRepository.save(execution);
  }

  private void link(AttackPathExecution execution, AttackPathFinding finding) {
    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execution.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
  }
}
