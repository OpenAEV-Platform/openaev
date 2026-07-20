package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathExecutionDetailDTO;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The execution Result & Terminal detail read (issue 5048). Reading one execution returns its
 * header (payload/agent/privilege), its result (target, prevention/detection status, findings), and
 * its terminal (command, output) from the frozen snapshot, with the linked credential secret masked
 * in the command, the output, and the finding value.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path execution Result & Terminal detail read")
class AttackPathExecutionDetailTest extends IntegrationTest {

  private static final String SIM = "SIM-DETAIL";

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectorFixture injectorFixture;

  private Tenant tenant;
  private String executionId;

  @BeforeEach
  void seed() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-detail-tenant"));

    // A real injector contract carrying an ATT&CK technique, referenced by the row's external id:
    // the read resolves the techniques from it for the drawer's chips.
    InjectorContract contract =
        injectorContractComposer
            .forInjectorContract(
                InjectorContractFixture.createDefaultInjectorContractWithExternalId(
                    "contract-ext-1"))
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withAttackPattern(
                attackPatternComposer.forAttackPattern(
                    AttackPatternFixture.createAttackPatternsWithExternalId("T1046")))
            .persist()
            .get();

    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setInjectId("inject-detail-1");
    e.setContractExternalId(contract.getExternalId());
    e.setSourceKind("INJECTOR");
    e.setSourceInjector("hydra");
    e.setTargetKind("ASSET");
    e.setTargetAssetId("dc-01");
    e.setTargetKey("dc-01");
    e.setTargetHostname("CORP-DC-01");
    e.setTargetIp("10.0.0.1");
    e.setTargetPlatform("Linux");
    e.setPayloadName("hydra-payload");
    e.setAgentName("agent-1");
    e.setAgentPrivilege("user");
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    e.setPreventionStatus("Not Prevented");
    e.setDetectionStatus("Detected");
    e.setCommand("hydra -l admin -p secret123 ssh://10.0.0.1");
    e.setTerminalOutput("[+] login: admin  password: secret123\n[+] valid credential found");
    executionId = executionRepository.save(e).getId();

    linkFinding("credentials", "admin:secret123");
    linkFinding("cve", "CVE-2026-1");
    entityManager.flush();
  }

  private void linkFinding(String type, String value) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType(type);
    f.setValue(value);
    f.setEndpointId("dc-01");
    f.setEndpointKey("dc-01");
    String findingId = findingRepository.save(f).getId();

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(executionId);
    link.setFindingId(findingId);
    entityManager.persist(link);
  }

  @Test
  @DisplayName(
      "returns header, result, and terminal with the secret masked in command, output, and finding")
  void returnsDetailWithMaskedSecret() {
    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);

    assertThat(d).isNotNull();
    // header
    assertThat(d.injectId()).isEqualTo("inject-detail-1");
    assertThat(d.payloadName()).isEqualTo("hydra-payload");
    // the run's contract resolves to its ATT&CK techniques (the drawer's chips)
    assertThat(d.attackPatterns()).hasSize(1);
    assertThat(d.attackPatterns().get(0).externalId()).isEqualTo("T1046");
    assertThat(d.agentName()).isEqualTo("agent-1");
    assertThat(d.agentPrivilege()).isEqualTo("user");
    // result
    assertThat(d.targetHostname()).isEqualTo("CORP-DC-01");
    assertThat(d.targetIp()).isEqualTo("10.0.0.1");
    assertThat(d.endpointKey()).isEqualTo("dc-01");
    assertThat(d.preventionStatus()).isEqualTo("Not Prevented");
    assertThat(d.detectionStatus()).isEqualTo("Detected");
    // the credential is masked (username kept), the cve is untouched
    assertThat(d.findings())
        .satisfiesExactlyInAnyOrder(
            item -> {
              assertThat(item.type()).isEqualTo("credentials");
              assertThat(item.value()).startsWith("admin:").doesNotContain("secret123");
            },
            item -> {
              assertThat(item.type()).isEqualTo("cve");
              assertThat(item.value()).isEqualTo("CVE-2026-1");
            });
    // terminal: the linked secret is masked in the command and the output
    assertThat(d.command()).contains("hydra -l admin").doesNotContain("secret123");
    assertThat(d.terminalOutput()).doesNotContain("secret123");
  }

  @Test
  @DisplayName("an unknown execution or a different simulation returns null (not found)")
  void unknownExecutionOrSimulationIsNull() {
    assertThat(graphService.executionDetail(SIM, "does-not-exist")).isNull();
    assertThat(graphService.executionDetail("OTHER-SIM", executionId)).isNull();
  }
}
