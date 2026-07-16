package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Step;
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
 * Phase A create (issue 5048, #203): at RUN, one EXECUTION row per resolved edge, tenant-attributed
 * from the current tenant context and keyed by the deterministic id, on the columns the read
 * consumes.
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
    TenantContext.setCurrentTenant(tenant.getId());

    ExecutionContext ctx =
        new ExecutionContext(
            "SIM-INGEST",
            "step-1",
            "tmpl-1",
            "exec-1",
            Instant.parse("2026-07-16T08:00:00Z"),
            "crackmapexec");

    ResolvedExecutionEdge edge =
        new ResolvedExecutionEdge(
            "AGENT_ASSET",
            null,
            "src-asset-1",
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

    ingestionService.createRows(ctx, List.of(edge));

    String id = AttackPathIds.executionNode("exec-1", "victim-1", "agt-1");
    AttackPathExecution row = executionRepository.findById(id).orElseThrow();

    assertThat(row.getTenant().getId()).isEqualTo(tenant.getId());
    assertThat(row.getSimulationId()).isEqualTo("SIM-INGEST");
    assertThat(row.getStepId()).isEqualTo("step-1");
    assertThat(row.getSourceKind()).isEqualTo("AGENT_ASSET");
    assertThat(row.getSourceAssetId()).isEqualTo("src-asset-1");
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
    TenantContext.setCurrentTenant(tenant.getId());

    ExecutionContext ctx =
        new ExecutionContext(
            "SIM-IDEM",
            "step-1",
            "tmpl-1",
            "exec-idem",
            Instant.parse("2026-07-16T08:00:00Z"),
            "crackmapexec");
    ResolvedExecutionEdge edge =
        new ResolvedExecutionEdge(
            "AGENT_ASSET",
            null,
            "src-asset-1",
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

    ingestionService.createRows(ctx, List.of(edge));
    ingestionService.createRows(ctx, List.of(edge)); // same (execId, target, agent) → same row

    // The deterministic id makes the second write an update, not a new row.
    assertThat(executionRepository.count()).isEqualTo(1);
    assertThat(
            executionRepository.findById(
                AttackPathIds.executionNode("exec-idem", "victim-1", "agt-1")))
        .isPresent();
  }

  @Test
  @DisplayName("onRun extracts agent-based source/target from the inject and creates the row")
  void onRunExtractsFromInject() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-onrun-tenant"));
    TenantContext.setCurrentTenant(tenant.getId());

    // In-memory inject graph: only read by the extraction; only the EXECUTION rows are persisted.
    Agent agent = new Agent();
    agent.setId("agt-1");
    agent.setPrivilege(Agent.PRIVILEGE.admin);

    Endpoint endpoint = new Endpoint();
    endpoint.setId("ep-1");
    endpoint.setHostname("corp-dc");
    endpoint.setIps(new String[] {"10.0.0.5"});
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setAgents(List.of(agent));

    Command command = new Command();
    command.setId("cmd-1");
    command.setName("crackmapexec");
    command.setContent("cme --local-auth");

    InjectorContract contract = new InjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(command);

    Injector injector = new Injector();
    injector.setName("OpenAEV Implant");

    Exercise exercise = new Exercise();
    exercise.setId("SIM-ONRUN");

    Inject inject = new Inject();
    inject.setId("inj-1");
    inject.setExercise(exercise);
    inject.setInjector(injector);
    inject.setAssets(List.of(endpoint));

    Step step = new Step();
    step.setId("step-1");

    ingestionService.onRun(step, inject, contract);

    // Local Command (assets, no remote arg): source = the agent's endpoint, target = that endpoint.
    AttackPathExecution row =
        executionRepository
            .findById(AttackPathIds.executionNode("inj-1", "ep-1", "agt-1"))
            .orElseThrow();
    assertThat(row.getSimulationId()).isEqualTo("SIM-ONRUN");
    assertThat(row.getSourceKind()).isEqualTo("AGENT_ASSET");
    assertThat(row.getSourceAssetId()).isEqualTo("ep-1");
    assertThat(row.getAgentId()).isEqualTo("agt-1");
    assertThat(row.getAgentName()).isEqualTo("corp-dc");
    assertThat(row.getAgentPrivilege()).isEqualTo("admin");
    assertThat(row.getTargetKind()).isEqualTo("ASSET");
    assertThat(row.getTargetKey()).isEqualTo("ep-1");
    assertThat(row.getTargetHostname()).isEqualTo("corp-dc");
    assertThat(row.getPayloadName()).isEqualTo("crackmapexec");
  }

  @Test
  @DisplayName("onRun records nothing for an inject with no simulation (out of attack-path scope)")
  void onRunSkipsWhenNoSimulation() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-nosim-tenant"));
    TenantContext.setCurrentTenant(tenant.getId());

    Inject inject = new Inject();
    inject.setId("inj-nosim");
    // no exercise set → out of the simulation-scoped attack path

    ingestionService.onRun(new Step(), inject, new InjectorContract());

    assertThat(executionRepository.count()).isZero();
  }
}
