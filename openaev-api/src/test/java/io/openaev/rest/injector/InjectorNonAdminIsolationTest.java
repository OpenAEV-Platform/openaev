package io.openaev.rest.injector;

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

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injectors")
@WithMockUser(isAdmin = false)
@DisplayName("injectors isolation holds for a non-admin spanning two tenants")
class InjectorNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_INJECTORS = Set.of(Capability.ACCESS_TENANT_SETTINGS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String injectorA;
  private String injectorB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneInjectorEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("nonadmin-iso-a", READ_INJECTORS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("nonadmin-iso-b", READ_INJECTORS).getId();
    injectorA = seedInjector(tenantA, "nonadmin-injector-a", "type-a");
    injectorB = seedInjector(tenantB, "nonadmin-injector-b", "type-b");
  }

  @Test
  @DisplayName("a non-admin listing under tenant A's path sees only A's injector")
  void listUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/injectors", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(injectorA), "A's injector must appear for the non-admin member of A");
    assertFalse(response.contains(injectorB), "B's injector must not leak to A's scope");
  }

  private String seedInjector(String tenantId, String name, String type) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO injectors (injector_id, tenant_id, injector_name, injector_type,"
                + " injector_external, injector_custom_contracts, injector_payloads,"
                + " injector_created_at, injector_updated_at)"
                + " VALUES (:id, :tenant, :name, :type, false, false, false, now(), now())")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("name", name)
        .setParameter("type", type)
        .executeUpdate();
    return id;
  }
}
