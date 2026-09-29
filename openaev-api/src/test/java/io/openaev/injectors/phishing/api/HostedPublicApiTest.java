package io.openaev.injectors.phishing.api;

import static io.openaev.injectors.phishing.api.HostedPublicApi.HOSTED_URI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Public {@code /api/hosted/**} endpoints, called with no {@code @WithMockUser} anywhere in this
 * class so the request runs as a truly anonymous caller (same reasoning as {@code
 * ExecutorApiUnauthenticatedAccessTest}: a mock-user annotation resolves an identity that a real
 * unauthenticated caller never has).
 *
 * <p>{@code GET domain-check} is the one deliberately cross-tenant read in the {@code
 * custom_domains} family (v2 activation, #7903): it fronts on-demand TLS at the edge and must keep
 * resolving a verified hostname regardless of which tenant owns it, through {@code
 * CustomDomainPublicLookupService}'s {@code TxCtx.allTenants()} scope, which suspends the caller's
 * transaction and opens its own ({@code Propagation.NOT_SUPPORTED}). This is the real location of
 * that coverage; {@code CustomDomainHttpIsolationTest} names this class instead of duplicating the
 * case because its own scope is the admin CRUD path only.
 *
 * <p>Deliberately NOT {@code @Transactional}: the suspended transaction the lookup opens runs on
 * its own connection and would not see rows still pending in a test-managed transaction. Seeding
 * and cleanup run in auto-committed JDBC, same shape as {@code
 * TenantScopedTransactionIntegrationTest}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=custom_domains")
@DisplayName("Public hosted endpoints, no authentication")
class HostedPublicApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String> seededTenantIds = new ArrayList<>();

  @BeforeEach
  void setUpJdbc() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @Nested
  @DisplayName("GET /api/hosted/domain-check")
  class DomainCheck {

    @Test
    @DisplayName(
        "given a VERIFIED domain owned by a non-default tenant, should resolve it anonymously")
    void given_verifiedDomainOwnedByNonDefaultTenant_should_resolveAnonymously() throws Exception {
      // Arrange
      String tenantId = seedTenant();
      String hostname = seedDomain(tenantId, "VERIFIED");

      // Act & Assert
      mvc.perform(get(HOSTED_URI + "/domain-check").param("domain", hostname))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given a hostname that does not exist, should answer not found")
    void given_unknownHostname_should_answerNotFound() throws Exception {
      // Arrange
      String hostname = "unknown-" + UUID.randomUUID() + ".example.test";

      // Act & Assert
      mvc.perform(get(HOSTED_URI + "/domain-check").param("domain", hostname))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given a domain that is not VERIFIED, should answer not found")
    void given_pendingDomain_should_answerNotFound() throws Exception {
      // Arrange
      String tenantId = seedTenant();
      String hostname = seedDomain(tenantId, "PENDING");

      // Act & Assert
      mvc.perform(get(HOSTED_URI + "/domain-check").param("domain", hostname))
          .andExpect(status().isNotFound());
    }
  }

  @AfterEach
  void cleanup() {
    if (seededTenantIds.isEmpty()) {
      return;
    }
    jdbc.update(
        "DELETE FROM custom_domains WHERE tenant_id IN ("
            + String.join(",", seededTenantIds.stream().map(id -> "?").toList())
            + ")",
        seededTenantIds.toArray());
    jdbc.update(
        "DELETE FROM tenants WHERE tenant_id IN ("
            + String.join(",", seededTenantIds.stream().map(id -> "?").toList())
            + ")",
        seededTenantIds.toArray());
  }

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "hosted-domain-check-" + id);
    seededTenantIds.add(id);
    return id;
  }

  private String seedDomain(String tenantId, String status) {
    String id = UUID.randomUUID().toString();
    String hostname = "hosted-" + UUID.randomUUID() + ".example.test";
    jdbc.update(
        "INSERT INTO custom_domains (custom_domain_id, custom_domain_hostname,"
            + " custom_domain_status, custom_domain_verification_token, custom_domain_created_at,"
            + " custom_domain_updated_at, tenant_id) VALUES (?, ?, ?, ?, now(), now(), ?)",
        id,
        hostname,
        status,
        "token-" + id,
        tenantId);
    return hostname;
  }
}
