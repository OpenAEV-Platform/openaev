package io.openaev.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.service.account.AgentServiceAccountPrivilegeService;
import io.openaev.service.account.ReservedServiceAccount;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The installer token endpoint answers 404 when the tenant has no service-account token, and
 * returns the token of the tenant the request selects.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the endpoint's transaction must be the
 * outermost one and commit, which is where a handled error that marked it rollback-only turns the
 * 404 into a 500.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Agent installer token endpoint")
class ExecutorApiInstallerTokenTest extends IntegrationTest {

  private static final String TOKEN_URI = "/api/agent/installer/openaev/token";
  private static final String TENANT_TOKEN_URI =
      "/api/tenants/{tenantId}/agent/installer/openaev/token";
  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DELETE_TOKENS =
      "DELETE FROM tokens WHERE token_user IN (SELECT user_id FROM users WHERE user_email = ?)";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private AgentServiceAccountPrivilegeService agentServiceAccountPrivilegeService;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;

  @BeforeEach
  void createTenant() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = tenantHelper.createTenantWithCurrentUser("installer-token").getId();
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    String email = serviceAccountEmail(tenantId);
    jdbc.update(DELETE_TOKENS, email);
    jdbc.update("DELETE FROM users WHERE user_email = ?", email);
    tenantHelper.deleteCommittedTenants(tenantId);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_tenantWithoutServiceAccount_should_return404")
  void given_tenantWithoutServiceAccount_should_return404() throws Exception {
    // Act & Assert: a fresh tenant has no agent service account yet
    mvc.perform(get(TENANT_TOKEN_URI, tenantId).accept(MediaType.TEXT_PLAIN_VALUE))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given_serviceAccountWithoutToken_should_return404")
  void given_serviceAccountWithoutToken_should_return404() throws Exception {
    // Arrange: the service account exists, its token is gone
    provisionServiceAccount(tenantId);
    jdbc.update(DELETE_TOKENS, serviceAccountEmail(tenantId));

    // Act & Assert
    mvc.perform(get(TENANT_TOKEN_URI, tenantId).accept(MediaType.TEXT_PLAIN_VALUE))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given_tenantWithServiceAccountToken_should_returnTheToken")
  void given_tenantWithServiceAccountToken_should_returnTheToken() throws Exception {
    // Arrange
    String token = provisionServiceAccount(tenantId);

    // Act
    String body =
        mvc.perform(get(TENANT_TOKEN_URI, tenantId).accept(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertEquals(token, body);
  }

  @Test
  @DisplayName("given_tenantSelectedByHeader_should_returnItsTokenAndNotTheDefaultTenantOne")
  void given_tenantSelectedByHeader_should_returnItsTokenAndNotTheDefaultTenantOne()
      throws Exception {
    // Arrange: both the default tenant and the selected one hold a token
    String defaultToken = provisionServiceAccount(Tenant.DEFAULT_TENANT_UUID);
    String selectedToken = provisionServiceAccount(tenantId);

    // Act: the non-prefixed route leaves the ambient tenant on the default one
    String body =
        mvc.perform(
                get(TOKEN_URI).header(TENANT_HEADER, tenantId).accept(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertEquals(selectedToken, body);
    assertNotEquals(defaultToken, body);
  }

  private String provisionServiceAccount(String tenant) {
    agentServiceAccountPrivilegeService.ensurePrivilegedUserExists(TxCtx.forTenant(tenant), tenant);
    return agentServiceAccountPrivilegeService
        .findTokenUserServiceAccountByTenant(tenant)
        .orElseThrow();
  }

  private static String serviceAccountEmail(String tenant) {
    return ReservedServiceAccount.AGENT.emailPattern().formatted(tenant);
  }
}
