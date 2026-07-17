package io.openaev.rest.collector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The data isolation must not depend on the caller being an administrator. The other isolation
 * tests run as admin (RBAC bypassed); this one runs as a non-admin that is a member of two tenants
 * and holds only the capability needed to read collectors, and shows the rewriter still returns one
 * tenant's collectors under its path. It closes the gap left by the admin-only tests: that the
 * scope, set from the request and never from the isAdmin flag, is what isolates a non-admin too.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=collectors")
@WithMockUser(isAdmin = false)
@DisplayName("collectors isolation holds for a non-admin spanning two tenants")
class CollectorNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_COLLECTORS = Set.of(Capability.ACCESS_TENANT_SETTINGS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String collectorA;
  private String collectorB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneCollectorEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("nonadmin-iso-a", READ_COLLECTORS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("nonadmin-iso-b", READ_COLLECTORS).getId();
    collectorA = seedCollector(tenantA, "nonadmin-collector-a", "type-a");
    collectorB = seedCollector(tenantB, "nonadmin-collector-b", "type-b");
  }

  @Test
  @DisplayName("a non-admin listing under tenant A's path sees only A's collector")
  void listUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/collectors", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(collectorA), "A's collector must appear for the non-admin member of A");
    assertFalse(response.contains(collectorB), "B's collector must not leak to A's scope");
  }

  private String seedCollector(String tenantId, String name, String type) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO collectors (collector_id, tenant_id, collector_name, collector_type,"
                + " collector_period, collector_external, collector_created_at,"
                + " collector_updated_at)"
                + " VALUES (:id, :tenant, :name, :type, 60, false, now(), now())")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("name", name)
        .setParameter("type", type)
        .executeUpdate();
    return id;
  }
}
