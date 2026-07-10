package io.openaev.api.attackpath;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionFindingRepository;
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
@TestPropertySource(properties = "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH_POC")
class AttackPathPocApiTest extends IntegrationTest {

  private static final String SIM = "SIM-API";

  @Autowired private MockMvc mvc;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;
  @Autowired private AttackPathExecutionFindingRepository executionFindingRepository;

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
    executionFindingRepository.save(link);
    entityManager.flush();

    mvc.perform(get(AttackPathPocApi.ATTACK_PATH_POC_URI + "/simulations/" + SIM + "/graph"))
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
    mvc.perform(get(tenantUri(TENANT_PREFIX + "/poc/attack-path/simulations/" + SIM + "/graph")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /endpoint/findings?ref= resolves and returns the expand shape")
  void expand_endpoint_route_resolves() throws Exception {
    mvc.perform(
            get(AttackPathPocApi.ATTACK_PATH_POC_URI + "/simulations/" + SIM + "/endpoint/findings")
                .param("ref", "dc-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.findingTypes").exists())
        .andExpect(jsonPath("$.findings").exists());
  }

  @Test
  @DisplayName("GET /endpoint/relations?ref= resolves and returns the relations shape")
  void relations_endpoint_route_resolves() throws Exception {
    mvc.perform(
            get(AttackPathPocApi.ATTACK_PATH_POC_URI
                    + "/simulations/"
                    + SIM
                    + "/endpoint/relations")
                .param("ref", "dc-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executions").exists())
        .andExpect(jsonPath("$.edges").exists());
  }
}
