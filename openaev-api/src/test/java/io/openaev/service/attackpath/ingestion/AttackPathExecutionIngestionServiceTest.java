package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService.ExecutionContext;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase A row building: one EXECUTION row per resolved edge, attributed to the tenant passed in and
 * keyed by the deterministic id, on the columns the read consumes.
 *
 * <p>Scope of this class is the row-building unit only. The real entry point is covered by {@code
 * AttackPathIngestionTenantAttributionTest}, which drives it the way the executor does and can
 * observe a write that commits in its own transaction. Nothing here sets an ambient tenant: the
 * write takes its tenant as an argument, which is the property worth pinning.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path Phase A: create EXECUTION rows")
class AttackPathExecutionIngestionServiceTest extends IntegrationTest {

  @Autowired private AttackPathExecutionIngestionService ingestionService;
  @Autowired private AttackPathExecutionRepository executionRepository;

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "Agent-based run creates one tenant-scoped row per (target, agent) with frozen columns")
  void createsRowPerEdge() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-ingest-tenant"));

    ExecutionContext ctx =
        new ExecutionContext(
            "SIM-INGEST",
            "step-1",
            "tmpl-1",
            "exec-1",
            Instant.parse("2026-07-16T08:00:00Z"),
            "crackmapexec",
            "contract-ext-1",
            "payload-1",
            "openaev_implant");

    ResolvedExecutionEdge edge =
        new ResolvedExecutionEdge(
            "AGENT_ASSET",
            null,
            "src-asset-1",
            "src-host",
            "10.0.0.1",
            "Windows",
            "ASSET",
            "victim-1",
            null,
            "victim-1",
            "victim",
            "10.0.0.9",
            "Linux",
            "agt-1",
            "agent-1",
            "admin");

    ingestionService.createRows(tenant.getId(), ctx, List.of(edge));

    String id = AttackPathIds.executionNode("exec-1", "victim-1", "agt-1");
    AttackPathExecution row = executionRepository.findById(id).orElseThrow();

    assertThat(row.getTenant().getId()).isEqualTo(tenant.getId());
    assertThat(row.getSimulationId()).isEqualTo("SIM-INGEST");
    assertThat(row.getInjectId()).isEqualTo("exec-1");
    assertThat(row.getStepId()).isEqualTo("step-1");
    assertThat(row.getSourceKind()).isEqualTo("AGENT_ASSET");
    assertThat(row.getSourceAssetId()).isEqualTo("src-asset-1");
    assertThat(row.getSourceHostname()).isEqualTo("src-host");
    assertThat(row.getSourceIp()).isEqualTo("10.0.0.1");
    assertThat(row.getSourcePlatform()).isEqualTo("Windows");
    assertThat(row.getTargetKind()).isEqualTo("ASSET");
    assertThat(row.getTargetAssetId()).isEqualTo("victim-1");
    assertThat(row.getTargetKey()).isEqualTo("victim-1");
    assertThat(row.getTargetHostname()).isEqualTo("victim");
    assertThat(row.getTargetPlatform()).isEqualTo("Linux");
    assertThat(row.getAgentId()).isEqualTo("agt-1");
    assertThat(row.getAgentName()).isEqualTo("agent-1");
    assertThat(row.getAgentPrivilege()).isEqualTo("admin");
    assertThat(row.getExecutedAt()).isEqualTo(Instant.parse("2026-07-16T08:00:00Z"));
    assertThat(row.getPayloadName()).isEqualTo("crackmapexec");
  }

  @Test
  @DisplayName(
      "Re-running the same edge converges on the same row (deterministic id, no duplicate)")
  void idempotentOnSameKey() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-ingest-idem"));

    ExecutionContext ctx =
        new ExecutionContext(
            "SIM-IDEM",
            "step-1",
            "tmpl-1",
            "exec-idem",
            Instant.parse("2026-07-16T08:00:00Z"),
            "crackmapexec",
            null,
            null,
            null);
    ResolvedExecutionEdge edge =
        new ResolvedExecutionEdge(
            "AGENT_ASSET",
            null,
            "src-asset-1",
            "src-host",
            "10.0.0.1",
            "Windows",
            "ASSET",
            "victim-1",
            null,
            "victim-1",
            "victim",
            "10.0.0.9",
            "Linux",
            "agt-1",
            "agent-1",
            "admin");

    ingestionService.createRows(tenant.getId(), ctx, List.of(edge));
    ingestionService.createRows(
        tenant.getId(), ctx, List.of(edge)); // same (execId, target, agent) → same row

    // Scoped to this simulation, not a global count: other suites commit rows into the same table
    // and a table-wide count made this assertion depend on what else had run.
    assertThat(executionRepository.findGraphRows("SIM-IDEM")).hasSize(1);
    assertThat(
            executionRepository.findById(
                AttackPathIds.executionNode("exec-idem", "victim-1", "agt-1")))
        .isPresent();
  }
}
