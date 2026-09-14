package io.openaev.rest.domain;

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
 * The isolation must not depend on the caller being an administrator. The admin isolation tests
 * bypass RBAC; this one runs as a non-admin that is a member of two tenants and holds only tenant
 * settings read capability, so the assertion validates tenant scoping itself.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=domains")
@WithMockUser(isAdmin = false)
@DisplayName("domains isolation holds for a non-admin spanning two tenants")
class DomainNonAdminIsolationTest extends IntegrationTest {

  private static final String TENANT_DOMAINS = "/api/tenants/{tenantId}/domains";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String domainA;
  private String domainB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneDomainEach() throws Exception {
    Set<Capability> readDomains = Set.of(Capability.ACCESS_TENANT_SETTINGS);
    tenantA = tenantHelper.createTenantWithCapabilities("domain-nonadmin-a", readDomains).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("domain-nonadmin-b", readDomains).getId();
    domainA = seedDomain(tenantA, "nonadmin-a");
    domainB = seedDomain(tenantB, "nonadmin-b");
  }

  @Test
  @DisplayName("a non-admin listing under tenant A's path sees only A's domain")
  void listUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String response =
        mvc.perform(get(TENANT_DOMAINS, tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(domainA), "A's domain must appear for the non-admin member of A");
    assertFalse(response.contains(domainB), "B's domain must not leak into A's scope");
  }

  private String seedDomain(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, "#778899")
        .setParameter(4, tenantId)
        .executeUpdate();
    return id;
  }
}
