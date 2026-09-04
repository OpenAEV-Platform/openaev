package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Integration tests for the merged Attack Chaining CSV export. */
@Transactional
@WithMockUser(isAdmin = true)
class AttackPathExportApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  @Test
  @DisplayName("GET .../export/csv returns merged chokepoint and trace rows with both headers")
  void given_simulationWithData_should_exportMergedCsv() throws Exception {
    // Arrange
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-export-trace-tenant"));
    String simulationId = "SIM-TRACE-EXPORT";

    AttackPathExecution first = new AttackPathExecution();
    first.setTenant(tenant);
    first.setSimulationId(simulationId);
    first.setSourceKind("INJECTOR");
    first.setSourceInjector("NMAP");
    first.setContractExternalId("nmap-scan");
    first.setTargetKind("ASSET");
    first.setTargetAssetId("dc-01");
    first.setTargetKey("dc-01");
    first.setTargetHostname("CORP-DC-01");
    first.setCommand("nmap -sV corp-dc-01");
    first.setTerminalOutput("PORT 445/tcp open microsoft-ds");
    first.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    first.setPreventionStatus("Prevented");
    first = executionRepository.save(first);

    AttackPathExecution second = new AttackPathExecution();
    second.setTenant(tenant);
    second.setSimulationId(simulationId);
    second.setSourceKind("AGENT");
    second.setSourceAssetId("dc-01");
    second.setAgentName("caldera-agent");
    second.setAgentPrivilege("admin");
    second.setTargetKind("ASSET");
    second.setTargetAssetId("dc-02");
    second.setTargetKey("dc-02");
    second.setTargetHostname("CORP-DC-02");
    second.setExecutedAt(Instant.parse("2026-06-18T08:05:00Z"));
    executionRepository.save(second);

    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(simulationId);
    finding.setType("credentials");
    finding.setValue("admin:secret");
    finding.setEndpointId("dc-01");
    finding.setEndpointKey("dc-01");
    finding = findingRepository.save(finding);

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(first.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
    entityManager.flush();

    // Act
    String csv =
        mvc.perform(
                get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + simulationId + "/export/csv")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertThat(csv)
        .contains(
            "Criticality",
            "Targeted Asset",
            "Risk Score",
            "Step Order",
            "Terminal Output",
            "Remediation Note");
    assertThat(csv).contains("Chokepoint");
    assertThat(csv).contains("CORP-DC-01");
    assertThat(csv).contains("CORP-DC-02");
    assertThat(csv).contains("PORT 445/tcp open microsoft-ds");
    assertThat(csv).contains(simulationId);
    assertThat(csv.indexOf("Chokepoint,CORP-DC-01")).isLessThan(csv.indexOf("-,CORP-DC-02"));
    assertThat(csv.strip().lines().count()).isEqualTo(4);
  }

  @Test
  @DisplayName("GET .../export/csv on a simulation with no data returns only the header")
  void given_simulationWithNoData_should_exportOnlyHeader() throws Exception {
    // Act
    String csv =
        mvc.perform(
                get(AttackPathApi.ATTACK_PATH_URI + "/simulations/SIM-EMPTY/export/csv")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertThat(csv.strip().lines().count()).isEqualTo(1);
    assertThat(csv).contains("Criticality", "Targeted Asset", "Risk Score", "Step Order");
  }
}
