package io.openaev.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A non-admin caller may only select a tenant it is a member of. The check lives at the request
 * binding ({@link TenantScopeResolver} via the TxCtx resolver): it runs before RBAC and regardless
 * of which tables are activated, so it holds for every endpoint that takes a TxCtx. Membership, not
 * the isAdmin flag, decides the scope. The other isolation tests run as admin but are members of
 * the tenants they address; this one proves the boundary for a caller that belongs to one tenant
 * but not the other. The mapper endpoint is only a convenient TxCtx-bearing route here.
 *
 * <p>The foreign tenant is saved straight through the repository: creating one through the service
 * would enrol the current user as a member, which is exactly what must not happen here.
 */
@Transactional
@WithMockUser(isAdmin = false)
@DisplayName("a non-admin caller cannot select a tenant it does not belong to")
class TenantSelectorMembershipTest extends IntegrationTest {

  private static final String MAPPER_BY_ID = "/api/tenants/{tenantId}/mappers/{mapperId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantRepository tenantRepository;

  private String memberTenant;
  private String foreignTenant;

  @BeforeEach
  void twoTenantsOneOfWhichTheCallerDoesNotBelongTo() throws Exception {
    memberTenant = tenantHelper.createTenantWithCurrentUser("member-tenant").getId();
    foreignTenant =
        tenantRepository.save(TenantFixture.getTenant("foreign-" + UUID.randomUUID())).getId();
  }

  @Test
  @DisplayName("selecting a foreign tenant is refused with TENANT_ACCESS_DENIED, before RBAC")
  void selectingForeignTenantIsRefused() throws Exception {
    mvc.perform(get(MAPPER_BY_ID, foreignTenant, UUID.randomUUID().toString()))
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("TENANT_ACCESS_DENIED")));
  }

  @Test
  @DisplayName("selecting the caller's own tenant passes the membership check (RBAC aside)")
  void selectingOwnTenantPassesMembershipCheck() throws Exception {
    mvc.perform(get(MAPPER_BY_ID, memberTenant, UUID.randomUUID().toString()))
        .andExpect(content().string(not(containsString("TENANT_ACCESS_DENIED"))));
  }
}
