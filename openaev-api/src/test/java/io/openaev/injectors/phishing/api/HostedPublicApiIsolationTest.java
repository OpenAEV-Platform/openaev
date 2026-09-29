package io.openaev.injectors.phishing.api;

import static io.openaev.injectors.phishing.api.HostedPublicApi.HOSTED_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
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
    jdbc.update("DELETE FROM phishing_landing_pages WHERE tenant_id IN (?, ?)", tenantA, tenantB);
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
  @DisplayName("GET /api/hosted/page/{token}")
  class Page {

    @Test
    @DisplayName(
        "given a valid token with a landing page, should serve it without a lazy-initialization"
            + " failure")
    void given_validTokenWithALandingPage_should_serveTheLandingPage() throws Exception {
      String landingPageId = seedLandingPage(tenantB, "wattr landing page");
      String token = "tok-page-" + UUID.randomUUID();
      seedPhishingResultWithLandingPage(tenantB, token, injectB.getId(), landingPageId);

      // The scoped resolveAndBackfillByToken runs in its own REQUIRES_NEW transaction and commits
      // before returning; PhishingResult#landingPage is LAZY, so unless it is materialized inside
      // that transaction, this dereferences a detached proxy with no session and throws
      // LazyInitializationException instead of a 200 with the page content.
      mvc.perform(get(HOSTED_URI + "/page/{token}", token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.phishing_landing_page_name").value("wattr landing page"));
    }
  }

  @Nested
  @DisplayName("POST /api/hosted/s/{token}")
  class Submit {

    @Test
    @DisplayName(
        "given a valid token with a landing page, should record the submission and return its"
            + " redirect url")
    void given_validTokenWithALandingPage_should_recordSubmissionAndReturnRedirectUrl()
        throws Exception {
      String landingPageId = seedLandingPage(tenantB, "wattr submit landing page");
      String token = "tok-submit-" + UUID.randomUUID();
      seedPhishingResultWithLandingPage(tenantB, token, injectB.getId(), landingPageId);

      // markSubmitted's scoped overload runs in its own REQUIRES_NEW transaction too; the returned
      // result's lazy landingPage is dereferenced here (getRedirectUrl()) after that transaction
      // has committed, the same detached-proxy shape as GET /page/{token}.
      mvc.perform(
              post(HOSTED_URI + "/s/{token}", token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"username\":\"bob\",\"password\":\"secret\"}"))
          .andExpect(status().isOk());

      assertThat(submittedAt(token))
          .as("the submission must be recorded on the token's row")
          .isNotNull();
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

  private String seedLandingPage(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO phishing_landing_pages (phishing_landing_page_id, tenant_id,"
            + " phishing_landing_page_name, phishing_landing_page_created_at,"
            + " phishing_landing_page_updated_at) VALUES (?, ?, ?, now(), now())",
        id,
        tenantId,
        name);
    return id;
  }

  private void seedPhishingResultWithLandingPage(
      String tenantId, String token, String injectId, String landingPageId) {
    jdbc.update(
        "INSERT INTO phishing_results (phishing_result_id, tenant_id, phishing_result_token,"
            + " phishing_result_inject, phishing_result_landing_page,"
            + " phishing_result_created_at, phishing_result_updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        tenantId,
        token,
        injectId,
        landingPageId,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
  }

  private Timestamp openedAt(String token) {
    return jdbc.queryForObject(
        "SELECT phishing_result_opened_at FROM phishing_results WHERE phishing_result_token = ?",
        Timestamp.class,
        token);
  }

  private Timestamp submittedAt(String token) {
    return jdbc.queryForObject(
        "SELECT phishing_result_submitted_at FROM phishing_results WHERE phishing_result_token = ?",
        Timestamp.class,
        token);
  }
}
