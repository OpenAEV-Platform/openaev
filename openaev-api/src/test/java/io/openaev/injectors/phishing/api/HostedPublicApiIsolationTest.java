package io.openaev.injectors.phishing.api;

import static io.openaev.injectors.phishing.api.HostedPublicApi.HOSTED_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import java.sql.Timestamp;
import java.time.Instant;
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
 * {@code /api/hosted/**} is a permitAll, token-only route with no path tenant and no
 * {@code @WithMockUser} identity (the token is the sole authenticator; see {@link
 * HostedPublicApi}'s class javadoc). This class deliberately carries no {@code @WithMockUser}, same
 * reasoning as {@code ExecutorApiUnauthenticatedAccessTest}: a mock-user annotation resolves an
 * identity a real caller never has.
 *
 * <p>Deliberately NOT {@code @Transactional}: once {@code phishing_results} is tenant-active, the
 * tracking calls run their own {@code REQUIRES_NEW} transaction (see {@code
 * PhishingTrackingService#markOpened(TxCtx, String, String, String)}), on its own connection, which
 * would not see rows still uncommitted in a test-managed transaction. Seeding and cleanup run
 * through auto-committed JDBC instead, the same pattern as {@code
 * TenantScopedTransactionIntegrationTest}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=phishing_results")
@DisplayName("Hosted public tracking endpoints, no authentication")
class HostedPublicApiIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectRepository injectRepository;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String tokenA;
  private String tokenB;
  private Inject injectA;
  private Inject injectB;

  @BeforeEach
  void seedTwoTenantsWithTheirOwnPhishingResult() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("hosted-iso-a-" + UUID.randomUUID());
    tenantB = seedTenant("hosted-iso-b-" + UUID.randomUUID());
    injectA = persistInject(tenantA);
    injectB = persistInject(tenantB);
    tokenA = "tok-a-" + UUID.randomUUID();
    tokenB = "tok-b-" + UUID.randomUUID();
    seedPhishingResult(tenantA, tokenA, injectA.getId());
    seedPhishingResult(tenantB, tokenB, injectB.getId());
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM phishing_results WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    injectRepository.deleteAllById(List.of(injectA.getId(), injectB.getId()));
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  private Inject persistInject(String tenantId) {
    String previous = TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
    } finally {
      if (previous == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previous);
      }
    }
  }

  @Nested
  @DisplayName("GET /api/hosted/o/{token}")
  class Open {

    @Test
    @DisplayName("given a valid token for tenant B, should mark only B's row as opened")
    void given_validTokenForTenantB_should_markOnlyThatTenantsRow() throws Exception {
      mvc.perform(get(HOSTED_URI + "/o/{token}", tokenB)).andExpect(status().isOk());

      assertThat(openedAt(tokenB))
          .as("the tracking write must land on the token's own tenant row")
          .isNotNull();
      assertThat(openedAt(tokenA))
          .as("an anonymous open on B's token must never mark A's row")
          .isNull();
    }

    @Test
    @DisplayName("given an unknown token, should return the pixel and mark nothing")
    void given_unknownToken_should_returnPixelAndMarkNothing() throws Exception {
      mvc.perform(get(HOSTED_URI + "/o/{token}", "does-not-exist")).andExpect(status().isOk());

      assertThat(openedAt(tokenA)).isNull();
      assertThat(openedAt(tokenB)).isNull();
    }
  }

  // -- helpers --

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private void seedPhishingResult(String tenantId, String token, String injectId) {
    jdbc.update(
        "INSERT INTO phishing_results (phishing_result_id, tenant_id, phishing_result_token,"
            + " phishing_result_inject, phishing_result_created_at, phishing_result_updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        tenantId,
        token,
        injectId,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
  }

  private Timestamp openedAt(String token) {
    return jdbc.queryForObject(
        "SELECT phishing_result_opened_at FROM phishing_results WHERE phishing_result_token = ?",
        Timestamp.class,
        token);
  }
}
