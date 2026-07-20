package io.openaev.service.endpoint;

import static io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointRegisterInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cross-tenant endpoint registration test — non-transactional so each MockMvc request gets its own
 * transaction and the v2 tenant scope (GUC) applies independently per request. Follows v2 isolation
 * test pattern: seed with JDBC, verify with JDBC, clean up explicitly.
 */
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.tenant.active-tables=executors")
public class EndpointCrossTenantRegistrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;

  @Test
  @DisplayName(
      "Registering same endpoint on different tenants creates a single upgrade job in each tenant")
  void registeringSameEndpointOnDifferentTenants() throws Exception {
    // Arrange — create two tenants and seed one executor per tenant via JDBC
    String tenantId1 =
        tenantIsolationTestHelper.createTenantWithCurrentUser("cross_tenant_reg_1").getId();
    serviceAccountPrivilegeService.ensurePrivilegedUserExists(tenantId1);

    String tenantId2 =
        tenantIsolationTestHelper.createTenantWithCurrentUser("cross_tenant_reg_2").getId();
    serviceAccountPrivilegeService.ensurePrivilegedUserExists(tenantId2);

    seedExecutor(OPENAEV_EXECUTOR_ID, tenantId1);
    seedExecutor(OPENAEV_EXECUTOR_ID, tenantId2);

    try {
      // Act — register same endpoint under each tenant path
      EndpointRegisterInput input = EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
      input.setAgentVersion("NOMATCH");

      mockMvc
          .perform(
              post("/api/tenants/" + tenantId1 + "/endpoints/register")
                  .content(mapper.writeValueAsString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      mockMvc
          .perform(
              post("/api/tenants/" + tenantId2 + "/endpoints/register")
                  .content(mapper.writeValueAsString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert — each tenant has exactly one upgrade job
      assertThat(countUpgradeJobs(tenantId1)).isEqualTo(1L);
      assertThat(countUpgradeJobs(tenantId2)).isEqualTo(1L);
    } finally {
      cleanup(tenantId1, tenantId2);
    }
  }

  private void seedExecutor(String executorId, String tenantId) {
    jdbcTemplate.update(
        "INSERT INTO executors (executor_id, tenant_id, executor_name, executor_type,"
            + " executor_external, executor_created_at, executor_updated_at)"
            + " VALUES (?, ?, 'OpenAEV Executor', 'openaev_node', false, now(), now())",
        executorId,
        tenantId);
  }

  private Long countUpgradeJobs(String tenantId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM asset_agent_jobs"
            + " WHERE tenant_id = ? AND asset_agent_inject IS NULL",
        Long.class,
        tenantId);
  }

  private void cleanup(String... tenantIds) {
    for (String tid : tenantIds) {
      jdbcTemplate.update("DELETE FROM asset_agent_jobs WHERE tenant_id = ?", tid);
      jdbcTemplate.update("DELETE FROM agents WHERE tenant_id = ?", tid);
      jdbcTemplate.update("DELETE FROM assets WHERE tenant_id = ?", tid);
      jdbcTemplate.update("DELETE FROM executors WHERE tenant_id = ?", tid);
    }
    tenantIsolationTestHelper.deleteCommittedTenants(tenantIds);
  }
}
