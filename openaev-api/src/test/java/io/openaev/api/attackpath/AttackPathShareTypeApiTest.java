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
 * SMB {@code share} findings keep their stored type across every attack-path read: endpoint expand,
 * graph node, drawer, execution detail and the {@code shares} counter. A share is a complex finding
 * (host, share name, permissions), so it is never relabelled as — nor counted with — another type.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH")
@DisplayName("attack path: share findings keep the share type")
class AttackPathShareTypeApiTest extends IntegrationTest {

  private static final String SIM = "SIM-SHARE";
  private static final String ENDPOINT = "dc-01";
  private static final String SHARE_VALUE = "\\\\10.0.0.1\\NETLOGON (READ,WRITE)";

  @Autowired private MockMvc mvc;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-share-type"));
  }

  @Test
  @DisplayName("expand: a share finding reads type 'share'")
  void expandKeepsShareType() throws Exception {
    seedFinding("share", SHARE_VALUE);

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/endpoint/findings")
                .param("ref", ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.findings[?(@.value=='" + SHARE_VALUE + "')].typeFindings")
                .value(hasItem("share")));
  }

  @Test
  @DisplayName("share appears consistently on graph node, counter, collapsed, drawer, execution")
  void shareAppearsOnEveryRead() throws Exception {
    String executionId = seedFinding("share", SHARE_VALUE);

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "full"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.shares").value(1))
        .andExpect(
            jsonPath("$.attackPathNodes[?(@.value=='" + SHARE_VALUE + "')].typeFindings")
                .value(hasItem("share")));

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "collapsed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.shares").value(1))
        .andExpect(
            jsonPath("$.attackPathNodes[?(@.type=='ASSET')].findingCounts.share")
                .value(hasItem(1)));

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/findings")
                .param("category", "shares"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].type").value("share"))
        .andExpect(jsonPath("$.items[0].value").value(SHARE_VALUE));

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/execution")
                .param("ref", executionId))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.findings[?(@.value=='" + SHARE_VALUE + "')].type").value(hasItem("share")));
  }

  /**
   * A share and a native {@code file} on the same endpoint each count under their own name: folding
   * them together would report "2 files" for one share plus one file. Each read keeps them
   * distinct.
   */
  @Test
  @DisplayName("a share and a file on the same endpoint are counted separately")
  void shareAndFileDoNotMerge() throws Exception {
    String fileValue = "\\\\10.0.0.1\\NETLOGON\\scripts\\passwords.txt";
    seedFinding("share", SHARE_VALUE);
    seedFinding("file", fileValue);

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "full"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.shares").value(1))
        .andExpect(jsonPath("$.counters.files").value(1));

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/findings")
                .param("category", "shares"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].type").value("share"));

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/findings")
                .param("category", "files"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].type").value("file"))
        .andExpect(jsonPath("$.items[0].value").value(fileValue));
  }

  @Test
  @DisplayName("a file finding reads type 'file' on graph node, collapsed counts and counter")
  void fileAppearsOnEveryRead() throws Exception {
    String fileValue = "\\\\10.0.0.1\\SYSVOL\\scripts\\secret.ps1";
    seedFinding("file", fileValue);

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "full"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.files").value(1))
        .andExpect(
            jsonPath("$.attackPathNodes[?(@.value=='" + fileValue + "')].typeFindings")
                .value(hasItem("file")));

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "collapsed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.files").value(1))
        .andExpect(
            jsonPath("$.attackPathNodes[?(@.type=='ASSET')].findingCounts.file").value(hasItem(1)));
  }

  @Test
  @DisplayName("day one: no shares -> shares counter is 0")
  void dayOneNoShares() throws Exception {
    seedFinding("cve", "CVE-2026-1");

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph")
                .param("mode", "full"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.shares").value(0));
  }

  /** A finding needs a producing execution + link to appear on any read (graph invariant). */
  private String seedFinding(String type, String value) {
    AttackPathExecution execution = execution();
    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(SIM);
    finding.setType(type);
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
    execution.setSourceInjector("netexec");
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
