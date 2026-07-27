package io.openaev.api.attackpath;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The POC graph endpoint returns the {@code AttackPathDTO} when the POC preview feature is on. */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH")
class AttackPathApiTest extends IntegrationTest {

  private static final String SIM = "SIM-API";

  @Autowired private MockMvc mvc;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  @Test
  @DisplayName("GET /simulations/{id}/graph returns the AttackPathDTO for the simulation")
  void graph_endpoint_returns_dto() throws Exception {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-api-tenant"));

    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(SIM);
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("NMAP");
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId("dc-01");
    execution.setTargetKey("dc-01");
    execution.setTargetHostname("CORP-DC-01");
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setPreventionStatus("Prevented");
    execution = executionRepository.save(execution);

    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(SIM);
    finding.setType("credentials");
    finding.setValue("admin:secret");
    finding.setEndpointId("dc-01");
    finding.setEndpointKey("dc-01");
    finding = findingRepository.save(finding);

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execution.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
    entityManager.flush();

    mvc.perform(get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/graph"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.endpoints").value(1))
        .andExpect(jsonPath("$.counters.credentials").value(1))
        .andExpect(jsonPath("$.attackPathNodes").isNotEmpty())
        .andExpect(jsonPath("$.attackPathEdges").isNotEmpty())
        .andExpect(jsonPath("$.attackPathExecutions").isNotEmpty());
  }

  @Test
  @DisplayName("The endpoint also resolves via the tenant-prefixed route the front uses")
  void graph_endpoint_reachable_via_tenant_prefixed_route() throws Exception {
    mvc.perform(get(tenantUri(TENANT_PREFIX + "/attack-path/simulations/" + SIM + "/graph")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /endpoint/findings?ref= resolves and returns the expand shape")
  void expand_endpoint_route_resolves() throws Exception {
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/endpoint/findings")
                .param("ref", "dc-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.findingTypes").exists())
        .andExpect(jsonPath("$.findings").exists());
  }

  @Test
  @DisplayName("GET /endpoint/relations?ref= resolves and returns the relations shape")
  void relations_endpoint_route_resolves() throws Exception {
    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + SIM + "/endpoint/relations")
                .param("ref", "dc-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executions").exists())
        .andExpect(jsonPath("$.edges").exists());
  }

  @Test
  @DisplayName("GET /graph?mode=collapsed returns the DB-aggregated collapsed DTO")
  void graph_collapsed_mode_returns_aggregated_dto() throws Exception {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-collapsed-ep"));
    String sim = "SIM-COLLAPSED-EP";

    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(sim);
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("nmap");
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId("dc-01");
    execution.setTargetKey("dc-01");
    execution.setTargetHostname("CORP-DC-01");
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setPreventionStatus("Prevented");
    executionRepository.save(execution);

    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(sim);
    finding.setType("credentials");
    finding.setValue("admin:secret");
    finding.setEndpointKey("dc-01");
    findingRepository.save(finding);
    entityManager.flush();

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + sim + "/graph")
                .param("mode", "collapsed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("collapsed"))
        .andExpect(jsonPath("$.attackPathExecutions").isEmpty())
        .andExpect(jsonPath("$.attackPathNodes").isNotEmpty())
        .andExpect(jsonPath("$.counters.endpoints").value(1));
  }

  @Test
  @DisplayName("GET /simulations lists the tenant's simulations with endpoint counts")
  void simulations_list_returns_summaries() throws Exception {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-list-tenant"));
    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId("SIM-LIST");
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("nmap");
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId("dc-9");
    execution.setTargetKey("dc-9");
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setPreventionStatus("Prevented");
    executionRepository.save(execution);
    entityManager.flush();

    mvc.perform(get(AttackPathApi.ATTACK_PATH_URI + "/simulations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].simulationId").value("SIM-LIST"))
        .andExpect(jsonPath("$[0].endpointCount").value(1))
        .andExpect(jsonPath("$[0].executionCount").value(1));
  }

  @Test
  @DisplayName("GET /simulations/{id}/findings?category=credentials returns a masked, paged list")
  void findings_endpoint_returns_masked_page() throws Exception {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-findings-api"));
    String sim = "SIM-FINDINGS-API";

    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(sim);
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("nmap");
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId("dc-01");
    execution.setTargetKey("dc-01");
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setPreventionStatus("Prevented");
    execution = executionRepository.save(execution);

    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(sim);
    finding.setType("credentials");
    finding.setValue("admin:secret");
    finding.setEndpointId("dc-01");
    finding.setEndpointKey("dc-01");
    finding = findingRepository.save(finding);

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execution.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
    entityManager.flush();

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + sim + "/findings")
                .param("category", "credentials")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].type").value("credentials"))
        .andExpect(jsonPath("$.items[0].value", not(containsString("secret"))))
        .andExpect(jsonPath("$.items[0].endpointKey").value("dc-01"))
        .andExpect(jsonPath("$.items[0].executionIds").isNotEmpty());
  }

  @Test
  @DisplayName(
      "GET /simulations/{id}/executions/{executionId} returns the masked Result & Terminal detail")
  void execution_detail_endpoint_returns_masked_detail() throws Exception {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-detail-api"));
    String sim = "SIM-DETAIL-API";

    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(sim);
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("hydra");
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId("dc-01");
    execution.setTargetKey("dc-01");
    execution.setTargetHostname("CORP-DC-01");
    execution.setPayloadName("hydra-payload");
    execution.setAgentName("agent-1");
    execution.setAgentPrivilege("user");
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setPreventionStatus("Not Prevented");
    execution.setCommand("hydra -l admin -p secret123 ssh://10.0.0.1");
    execution.setTerminalOutput("password: secret123");
    execution = executionRepository.save(execution);

    AttackPathFinding finding = new AttackPathFinding();
    finding.setTenant(tenant);
    finding.setSimulationId(sim);
    finding.setType("credentials");
    finding.setValue("admin:secret123");
    finding.setEndpointKey("dc-01");
    finding = findingRepository.save(finding);

    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execution.getId());
    link.setFindingId(finding.getId());
    entityManager.persist(link);
    entityManager.flush();

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + sim + "/execution")
                .param("ref", execution.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payloadName").value("hydra-payload"))
        .andExpect(jsonPath("$.agentName").value("agent-1"))
        .andExpect(jsonPath("$.preventionStatus").value("Not Prevented"))
        .andExpect(jsonPath("$.command", not(containsString("secret123"))))
        .andExpect(jsonPath("$.terminalOutput", not(containsString("secret123"))))
        .andExpect(jsonPath("$.findings[0].type").value("credentials"))
        .andExpect(jsonPath("$.findings[0].value", not(containsString("secret123"))));

    mvc.perform(
            get(AttackPathApi.ATTACK_PATH_URI + "/simulations/" + sim + "/execution")
                .param("ref", "does-not-exist"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /seed is allowed for an admin and returns the row counts")
  void seed_endpoint_returns_counts_for_admin() throws Exception {
    mvc.perform(post(AttackPathApi.ATTACK_PATH_URI + "/seed").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.simulations").value(6))
        .andExpect(jsonPath("$.executions").isNumber())
        .andExpect(jsonPath("$.findings").isNumber());
  }

  @Test
  @DisplayName("POST /seed is forbidden for a non-admin")
  @WithMockUser(isAdmin = false)
  void seed_endpoint_forbidden_for_non_admin() throws Exception {
    mvc.perform(post(AttackPathApi.ATTACK_PATH_URI + "/seed").with(csrf()))
        .andExpect(status().isForbidden());
  }
}
