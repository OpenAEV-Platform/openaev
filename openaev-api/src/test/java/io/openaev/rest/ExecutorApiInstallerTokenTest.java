package io.openaev.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.service.account.AgentServiceAccountPrivilegeService;
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
 * The installer token endpoint answers 404 when the tenant has no service-account token.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the endpoint's transaction must be the
 * outermost one and commit, which is where a handled error that marked it rollback-only turns the
 * 404 into a 500.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Agent installer token endpoint")
class ExecutorApiInstallerTokenTest extends IntegrationTest {

  private static final String TOKEN_URI = "/api/tenants/{tenantId}/agent/installer/openaev/token";
  private static final String SERVICE_ACCOUNT_EMAIL = "service-%s@openaev.invalid";

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
    String email = SERVICE_ACCOUNT_EMAIL.formatted(tenantId);
    jdbc.update(
        "DELETE FROM tokens WHERE token_user IN (SELECT user_id FROM users WHERE user_email = ?)",
        email);
    jdbc.update("DELETE FROM users WHERE user_email = ?", email);
    tenantHelper.deleteCommittedTenants(tenantId);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_tenantWithoutServiceAccountToken_should_return404")
  void given_tenantWithoutServiceAccountToken_should_return404() throws Exception {
    // Arrange
    jdbc.update(
        "DELETE FROM tokens WHERE token_user IN (SELECT user_id FROM users WHERE user_email = ?)",
        SERVICE_ACCOUNT_EMAIL.formatted(tenantId));

    // Act & Assert
    mvc.perform(get(TOKEN_URI, tenantId).accept(MediaType.TEXT_PLAIN_VALUE))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given_tenantWithServiceAccountToken_should_returnTheToken")
  void given_tenantWithServiceAccountToken_should_returnTheToken() throws Exception {
    // Arrange
    agentServiceAccountPrivilegeService.ensurePrivilegedUserExists(
        TxCtx.forTenant(tenantId), tenantId);
    String token =
        agentServiceAccountPrivilegeService
            .findTokenUserServiceAccountByTenant(tenantId)
            .orElseThrow();

    // Act
    String body =
        mvc.perform(get(TOKEN_URI, tenantId).accept(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertEquals(token, body);
  }
}
