package io.openaev.api.custom_domain;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.custom_domain.form.CustomDomainInput;
import io.openaev.context.TenantContext;
import io.openaev.utils.TenantIsolationTestHelper;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * custom_domains read/write isolation through the real HTTP endpoint (v2 activation, #7903), on
 * both routes: the tenant path and the {@code X-Tenant-Ids} header. Every row is seeded with a raw
 * native INSERT carrying an explicit tenant_id, never through v1 TenantContext.
 *
 * <p>The one deliberately cross-tenant path ({@code CustomDomainPublicLookupService}, behind the
 * unauthenticated {@code domain-check} endpoint) is covered separately in {@code
 * io.openaev.injectors.phishing.api.HostedPublicApiTest}, not here: this class is about the admin
 * CRUD scope.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=custom_domains")
@io.openaev.utils.mockUser.WithMockUser(isAdmin = true)
@DisplayName("custom_domains read and write isolation through the real HTTP endpoint, both routes")
class CustomDomainHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_CUSTOM_DOMAINS = "/api/tenants/{tenantId}/custom-domains";
  private static final String CUSTOM_DOMAINS = "/api/custom-domains";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String domainA;
  private String domainB;

  @BeforeEach
  void seedTwoTenantsWithOneDomainEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("custom-domain-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("custom-domain-iso-b").getId();
    domainA = seedDomain(tenantA, "a-" + System.currentTimeMillis() + ".example.test");
    domainB = seedDomain(tenantB, "b-" + System.currentTimeMillis() + ".example.test");
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("under tenant A's path: read returns A's domain")
  void readOwnRowUnderOwnPath() throws Exception {
    String response =
        mvc.perform(
                get(TENANT_CUSTOM_DOMAINS + "/{id}", tenantA, domainA)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(domainA, JsonPath.read(response, "$.custom_domain_id"));
  }

  @Test
  @DisplayName("under tenant B's path: reading tenant A's domain is not found")
  void readCrossTenantUnderOwnPathIsNotFound() throws Exception {
    mvc.perform(
            get(TENANT_CUSTOM_DOMAINS + "/{id}", tenantB, domainA)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: reading tenant A's domain is not found for tenant B")
  void readCrossTenantViaHeaderIsNotFound() throws Exception {
    TenantContext.clearCurrentTenant();
    mvc.perform(
            get(CUSTOM_DOMAINS + "/{id}", domainA)
                .header("X-Tenant-Ids", tenantB)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's domain and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body =
        asJsonString(
            io.openaev.utils.fixtures.PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post(TENANT_CUSTOM_DOMAINS + "/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains(domainA), "A's domain must appear in A's search results");
    assertFalse(response.contains(domainB), "B's domain must not appear in A's search results");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    CustomDomainInput input = new CustomDomainInput();
    input.setHostname("created-a-" + System.currentTimeMillis() + ".example.test");

    String response =
        mvc.perform(
                post(TENANT_CUSTOM_DOMAINS, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.custom_domain_id");
    assertEquals(tenantA, rawTenantId(createdId), "the created domain must belong to tenant A");
  }

  @Test
  @DisplayName("a create via the X-Tenant-Ids header is attributed to the header tenant")
  void createViaHeaderIsAttributedToHeaderTenant() throws Exception {
    TenantContext.clearCurrentTenant();
    CustomDomainInput input = new CustomDomainInput();
    input.setHostname("created-header-b-" + System.currentTimeMillis() + ".example.test");

    String response =
        mvc.perform(
                post(CUSTOM_DOMAINS)
                    .header("X-Tenant-Ids", tenantB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.custom_domain_id");
    assertEquals(tenantB, rawTenantId(createdId), "the created domain must belong to tenant B");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    TenantContext.clearCurrentTenant();
    CustomDomainInput input = new CustomDomainInput();
    input.setHostname("no-selector-" + System.currentTimeMillis() + ".example.test");

    mvc.perform(
            post(CUSTOM_DOMAINS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "under tenant B's path: deleting tenant A's domain is not found and leaves it in place")
  void deleteCrossTenantIsRejected() throws Exception {
    mvc.perform(delete(TENANT_CUSTOM_DOMAINS + "/{id}", tenantB, domainA).with(csrf()))
        .andExpect(status().isNotFound());

    entityManager.flush();
    entityManager.clear();
    assertEquals(
        1L, rawCount(domainA), "tenant A's domain must not have been deleted cross-tenant");
  }

  private String rawTenantId(String domainId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM custom_domains WHERE custom_domain_id = ?")) {
                stmt.setString(1, domainId);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String domainId) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT count(*) FROM custom_domains WHERE custom_domain_id = ?")) {
                stmt.setString(1, domainId);
                try (var rows = stmt.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private String seedDomain(String tenantId, String hostname) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO custom_domains (custom_domain_id, custom_domain_hostname,"
                + " custom_domain_status, custom_domain_verification_token, custom_domain_created_at,"
                + " custom_domain_updated_at, tenant_id) VALUES (?1, ?2, 'PENDING', ?3, now(), now(),"
                + " ?4)")
        .setParameter(1, id)
        .setParameter(2, hostname)
        .setParameter(3, "token-" + id)
        .setParameter(4, tenantId)
        .executeUpdate();
    return id;
  }
}
