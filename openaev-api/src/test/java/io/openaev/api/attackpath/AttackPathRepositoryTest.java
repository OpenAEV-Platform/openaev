package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.model.attackpath.projection.AttackPathExecutionRow;
import io.openaev.database.model.attackpath.projection.AttackPathFindingRow;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the attack-path entities persist and the two JPQL projection reads (Read A, Read B)
 * return the expected flat projections filtered by simulation. Both repository reads are pure JPQL
 * (no native SQL); Read A omits the heavy columns and Read B keeps the finding→producing-execution
 * trace. Tenant isolation through the statement inspector is proven separately (see the isolation
 * test, which activates the tables in a dedicated context).
 */
@Transactional
class AttackPathRepositoryTest extends IntegrationTest {

  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  @Test
  @DisplayName("Read A and Read B return flat projections filtered by simulation")
  void reads_return_flat_projections_filtered_by_simulation() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-repo-tenant"));

    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId("S1");
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("NMAP");
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId("asset-1");
    execution.setTargetKey("asset-1");
    execution.setTargetHostname("CORP-DC-01");
    execution.setExecutedAt(Instant.parse("2026-06-18T08:38:51Z"));
    execution.setPreventionStatus("Prevented");
    execution.setCommand("secret-command");
    execution.setTerminalOutput("secret-terminal-output");
    execution = executionRepository.save(execution);

    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId("S1");
    finding.setType("credentials");
    finding.setValue("admin:secret");
    finding.setEndpointId("asset-1");
    finding.setEndpointKey("asset-1");
    finding = findingRepository.save(finding);

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execution.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
    entityManager.flush();

    // Read A: short display columns, including the run-snapshot ones; never the heavy columns
    // (the projection has no command/terminal_output fields — a compile-time guarantee).
    List<AttackPathExecutionRow> executionRows = executionRepository.findGraphRows("S1");
    assertThat(executionRows).hasSize(1);
    AttackPathExecutionRow executionRow = executionRows.get(0);
    assertThat(executionRow.id()).isEqualTo(execution.getId());
    assertThat(executionRow.sourceInjector()).isEqualTo("NMAP");
    assertThat(executionRow.targetHostname()).isEqualTo("CORP-DC-01");
    assertThat(executionRow.preventionStatus()).isEqualTo("Prevented");

    // Read B: finding joined to its producing execution (trace preserved)
    List<AttackPathFindingRow> findingRows = findingRepository.findGraphRows("S1");
    assertThat(findingRows).hasSize(1);
    AttackPathFindingRow findingRow = findingRows.get(0);
    assertThat(findingRow.value()).isEqualTo("admin:secret");
    assertThat(findingRow.type()).isEqualTo("credentials");
    assertThat(findingRow.executionId()).isEqualTo(execution.getId());

    // Filtered by simulation
    assertThat(executionRepository.findGraphRows("OTHER-SIM")).isEmpty();
    assertThat(findingRepository.findGraphRows("OTHER-SIM")).isEmpty();
  }
}
