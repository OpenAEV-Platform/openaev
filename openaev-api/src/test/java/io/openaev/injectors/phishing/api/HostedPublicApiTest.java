package io.openaev.injectors.phishing.api;

import static io.openaev.injectors.phishing.api.HostedPublicApi.HOSTED_URI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import java.util.ArrayList;
import java.util.HashMap;
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
@TestPropertySource(
    properties = "openaev.tenant.active-tables=custom_domains,phishing_landing_pages")
@DisplayName("Public hosted endpoints, no authentication")
class HostedPublicApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;

  private JdbcTemplate jdbc;
  private final List<String> seededTenantIds = new ArrayList<>();
  private final List<String> seededLandingPageIds = new ArrayList<>();
  private final List<String> seededPhishingResultTokens = new ArrayList<>();
  private final List<ExerciseComposer.Composer> exerciseComposers = new ArrayList<>();

  @BeforeEach
  void setUpJdbc() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void cleanupInjects() {
    exerciseComposers.forEach(ExerciseComposer.Composer::delete);
    exerciseComposers.clear();
  }

  /**
   * {@code phishing_results} carries a check constraint requiring a step or an inject: an
   * unrelated, default-tenant inject is enough to satisfy it (the FK is not tenant-checked at the
   * DB level, and none of these tests exercise expectation scoring, which needs a matching user).
   */
  private String persistUnrelatedInject() {
    Inject inject = InjectFixture.getDefaultInject();
    ExerciseComposer.Composer exercise =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(injectComposer.forInject(inject));
    exercise.persist();
    exerciseComposers.add(exercise);
    return inject.getId();
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

  /**
   * {@code GET page} and {@code POST s}: the anonymous caller carries no {@code {tenantId}} path
   * segment, so its own {@code TxCtx} resolves to {@code missing()} regardless of the recipient's
   * tenant bound onto the legacy {@code TenantContext} - a lazy load of {@code
   * phishing_landing_pages} run directly under that scope admits no row (fail-closed) and the
   * reader's field access throws. {@code HostedPublicApi} must instead resolve the landing page
   * through {@code PhishingLandingPagePublicLookupService}, scoped to the one tenant the token
   * already resolved.
   *
   * <p>Seeded under {@link Tenant#DEFAULT_TENANT_UUID}, not a freshly created tenant: {@code
   * phishing_results} keeps the legacy v1 {@code @Filter}, whose {@code
   * HibernateFilterTransactionAspect} fixes the filter parameter from {@code TenantContext} on
   * transaction entry, i.e. BEFORE {@link HostedPublicApi#bindTenant} runs inside the method body -
   * a separate, pre-existing gap that fails every {@code resolveAndBackfillByToken} lookup for a
   * non-default tenant regardless of the fix under test here (see the report). The default tenant
   * sidesteps it: {@code TenantContext#getCurrentTenant()} already falls back to it, so the
   * aspect's early read matches.
   */
  @Nested
  @DisplayName("GET /api/hosted/page/{token}")
  class Page {

    @Test
    @DisplayName("given a real token, should serve its own tenant's landing page")
    void given_realToken_should_serveOwnTenantsLandingPage() throws Exception {
      // Arrange
      String landingPageId = seedLandingPage(Tenant.DEFAULT_TENANT_UUID, "Login page", null);
      String token = seedPhishingResult(Tenant.DEFAULT_TENANT_UUID, landingPageId);

      // Act & Assert
      mvc.perform(get(HOSTED_URI + "/page/{token}", token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.phishing_landing_page_name").value("Login page"));
    }

    @Test
    @DisplayName("given an unknown token, should answer with no body")
    void given_unknownToken_should_answerWithNoBody() throws Exception {
      // Act & Assert
      mvc.perform(get(HOSTED_URI + "/page/{token}", "unknown-" + UUID.randomUUID()))
          .andExpect(status().isOk())
          .andExpect(content().string(""));
    }
  }

  @Nested
  @DisplayName("POST /api/hosted/s/{token}")
  class Submit {

    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("given a real token, should return its own tenant's redirect url")
    void given_realToken_should_returnOwnTenantsRedirectUrl() throws Exception {
      // Arrange
      String redirectUrl = "https://example.test/thanks-" + UUID.randomUUID();
      String landingPageId =
          seedLandingPage(Tenant.DEFAULT_TENANT_UUID, "Submit page", redirectUrl);
      String token = seedPhishingResult(Tenant.DEFAULT_TENANT_UUID, landingPageId);

      // Act & Assert
      mvc.perform(
              post(HOSTED_URI + "/s/{token}", token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(new HashMap<>())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.redirect_url").value(redirectUrl));
    }
  }

  @AfterEach
  void cleanup() {
    if (!seededPhishingResultTokens.isEmpty()) {
      jdbc.update(
          "DELETE FROM phishing_results WHERE phishing_result_token IN ("
              + String.join(",", seededPhishingResultTokens.stream().map(id -> "?").toList())
              + ")",
          seededPhishingResultTokens.toArray());
    }
    if (!seededLandingPageIds.isEmpty()) {
      jdbc.update(
          "DELETE FROM phishing_landing_pages WHERE phishing_landing_page_id IN ("
              + String.join(",", seededLandingPageIds.stream().map(id -> "?").toList())
              + ")",
          seededLandingPageIds.toArray());
    }
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

  private String seedLandingPage(String tenantId, String name, String redirectUrl) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO phishing_landing_pages (phishing_landing_page_id,"
            + " phishing_landing_page_name, phishing_landing_page_capture_submitted_data,"
            + " phishing_landing_page_capture_passwords, phishing_landing_page_redirect_url,"
            + " phishing_landing_page_created_at, phishing_landing_page_updated_at, tenant_id)"
            + " VALUES (?, ?, true, true, ?, now(), now(), ?)",
        id,
        name,
        redirectUrl,
        tenantId);
    seededLandingPageIds.add(id);
    return id;
  }

  private String seedPhishingResult(String tenantId, String landingPageId) {
    String id = UUID.randomUUID().toString();
    String token = "token-" + UUID.randomUUID();
    String injectId = persistUnrelatedInject();
    jdbc.update(
        "INSERT INTO phishing_results (phishing_result_id, tenant_id, phishing_result_token,"
            + " phishing_result_landing_page, phishing_result_inject,"
            + " phishing_result_created_at, phishing_result_updated_at)"
            + " VALUES (?, ?, ?, ?, ?, now(), now())",
        id,
        tenantId,
        token,
        landingPageId,
        injectId);
    seededPhishingResultTokens.add(token);
    return token;
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
