package io.openaev.rest.collector;

import static io.openaev.rest.collector.CollectorApi.COLLECTOR_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.rest.collector.form.CollectorCreateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * collector_types write isolation through the real HTTP endpoint (v2 activation, #7933), on both
 * routes: the tenant path and the {@code X-Tenant-Ids} header. collector_types has no CRUD API of
 * its own ({@code CollectorTypeApi} does not exist): it is populated as a side effect of {@code
 * POST /api/collectors} ({@code CollectorService#ensureCollectorTypeExists}), so isolation is
 * proven through that endpoint. The unique constraint on {@code collector_type_name} is per-tenant
 * (migration V4_92), so the same type name registered by two tenants must land as two distinct
 * rows, one per tenant, never a shared or cross-tenant one.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=collector_types")
@WithMockUser(isAdmin = true)
@DisplayName("collector_types write isolation through the real HTTP endpoint, both routes")
class CollectorTypeHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_COLLECTOR_URI = "/api/tenants/{tenantId}/collectors";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("collector-type-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("collector-type-iso-b").getId();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private MockMultipartFile inputPart(CollectorCreateInput input) {
    return new MockMultipartFile(
        "input", "input.json", MediaType.APPLICATION_JSON_VALUE, asJsonString(input).getBytes());
  }

  // Each test stays on a single tenant path: the tenant aspect refuses to redefine an
  // already-resolved scope inside one @Transactional test transaction (a second mvc.perform
  // against a different tenant path in the same test hits that nesting guard).

  @Test
  @DisplayName("under tenant A's path: registering a collector attributes its type to tenant A")
  void createUnderTenantAAttributesCollectorTypeToA() throws Exception {
    String typeName = "openaev_iso_a_" + System.currentTimeMillis();

    CollectorCreateInput input = new CollectorCreateInput();
    input.setId("collector-iso-a-" + System.currentTimeMillis());
    input.setType(typeName);
    input.setName("Collector A");
    input.setPeriod(60);

    mvc.perform(
            multipart(TENANT_COLLECTOR_URI, tenantA)
                .file(inputPart(input))
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    entityManager.flush();
    entityManager.clear();

    org.junit.jupiter.api.Assertions.assertNotNull(
        rawCollectorTypeId(typeName, tenantA),
        "tenant A must have its own collector_types row for " + typeName);
    org.junit.jupiter.api.Assertions.assertNull(
        rawCollectorTypeId(typeName, tenantB),
        "the collector type must not be attributed to any other tenant");
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header: registering a collector attributes its collector_types row"
          + " to the header tenant")
  void createViaHeaderAttributesCollectorTypeToHeaderTenant() throws Exception {
    TenantContext.clearCurrentTenant();
    String typeName = "openaev_iso_header_" + System.currentTimeMillis();

    CollectorCreateInput input = new CollectorCreateInput();
    input.setId("collector-iso-header-" + System.currentTimeMillis());
    input.setType(typeName);
    input.setName("Collector header");
    input.setPeriod(60);

    mvc.perform(
            multipart(COLLECTOR_URI)
                .file(inputPart(input))
                .header("X-Tenant-Ids", tenantB)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    entityManager.flush();
    entityManager.clear();
    org.junit.jupiter.api.Assertions.assertNotNull(
        rawCollectorTypeId(typeName, tenantB),
        "the collector type must be attributed to the X-Tenant-Ids header tenant");
    org.junit.jupiter.api.Assertions.assertNull(
        rawCollectorTypeId(typeName, tenantA),
        "the collector type must not be attributed to any other tenant");
  }

  private String rawCollectorTypeId(String typeName, String expectedTenantId) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT collector_type_id FROM collector_types WHERE"
                          + " collector_type_name = ? AND tenant_id = ?")) {
                stmt.setString(1, typeName);
                stmt.setString(2, expectedTenantId);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
