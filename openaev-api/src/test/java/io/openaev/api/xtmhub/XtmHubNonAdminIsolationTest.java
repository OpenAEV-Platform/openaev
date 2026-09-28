package io.openaev.api.xtmhub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The v2 SQL rewrite must isolate XTM Hub registrations for a non-admin too. This proves the tenant
 * path, not the admin flag, is what keeps each tenant's singleton registration isolated.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=tenant_xtmhub_registrations")
@WithMockUser(isAdmin = false)
@DisplayName("tenant_xtmhub_registrations isolation holds for a non-admin spanning two tenants")
class XtmHubNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> XTM_HUB_ACCESS = Set.of(Capability.ACCESS_TENANT_SETTINGS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tokenA;
  private String tokenB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneRegistrationEach() throws Exception {
    tenantA =
        tenantHelper.createTenantWithCapabilities("xtmhub-nonadmin-a", XTM_HUB_ACCESS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("xtmhub-nonadmin-b", XTM_HUB_ACCESS).getId();
    tokenA = "nonadmin-token-a";
    tokenB = "nonadmin-token-b";
    seedRegistration(tenantA, tokenA);
    seedRegistration(tenantB, tokenB);
  }

  @Test
  @DisplayName("a non-admin reading under tenant A's path sees only tenant A's registration")
  void readUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String response =
        mvc.perform(
                get(XtmHubApi.TENANT_XTMHUB_URI + "/registration", tenantA)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(tokenA), "tenant A registration must appear for the non-admin");
    assertFalse(response.contains(tokenB), "tenant B registration must not leak to tenant A");
  }

  private void seedRegistration(String tenantId, String token) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO tenant_xtmhub_registrations (
              registration_id,
              tenant_id,
              registration_token,
              registration_date,
              registration_status,
              registration_last_connectivity_check,
              registration_connectivity_email_eligible
            ) VALUES (:id, :tenantId, :token, :registrationDate, :registrationStatus, :lastCheck, :eligible)
            """)
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("tenantId", tenantId)
        .setParameter("token", token)
        .setParameter("registrationDate", LocalDateTime.now())
        .setParameter("registrationStatus", "REGISTERED")
        .setParameter("lastCheck", LocalDateTime.now())
        .setParameter("eligible", true)
        .executeUpdate();
  }
}
