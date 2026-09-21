package io.openaev.api.notification;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.TenantRepository;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
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

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=notifications")
@WithMockUser
@DisplayName("notifications isolation holds for a non-admin spanning two tenants")
class NotificationNonAdminIsolationTest extends IntegrationTest {

  private static final String ME_SEARCH = "/api/notifications/me/search";
  private static final String TENANT_ME_SEARCH = "/api/tenants/{tenantId}/notifications/me/search";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserService userService;

  private String tenantA;
  private String notificationA;
  private String notificationB;
  private String outOfRightsTenant;

  @BeforeEach
  void seedTenantsForNonAdminIsolation() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("notif-nonadmin-a", Set.of()).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("notif-nonadmin-b", Set.of()).getId();
    outOfRightsTenant =
        tenantRepository
            .save(TenantFixture.getTenant("notif-nonadmin-out-" + UUID.randomUUID()))
            .getId();

    String userId = userService.currentUser().getId();
    notificationA = seedNotification(tenantA, userId, "nonadmin-a");
    notificationB = seedNotification(tenantB, userId, "nonadmin-b");
    seedNotification(outOfRightsTenant, userId, "nonadmin-out");
  }

  @Test
  @DisplayName("a non-admin searching under tenant A's path sees only A's notification")
  void searchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post(TENANT_ME_SEARCH, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(notificationA), "A's notification must appear for the non-admin member");
    assertFalse(response.contains(notificationB), "B's notification must not leak into A's scope");
  }

  @Test
  @DisplayName("a selector for a tenant outside the caller's memberships is refused")
  void searchWithOutOfRightsTenantSelectorIsForbidden() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    mvc.perform(
            post(ME_SEARCH)
                .header("X-Tenant-Ids", outOfRightsTenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("TENANT_ACCESS_DENIED")));
  }

  private String seedNotification(String tenantId, String userId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO notifications"
                + " (notification_id, notification_name, notification_type, notification_content,"
                + " notification_is_read, user_id, tenant_id)"
                + " VALUES (?1, ?2, ?3, CAST(?4 AS jsonb), ?5, ?6, ?7)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, "LIVE")
        .setParameter(4, "[{\"title\":\"" + name + "\",\"events\":[]}]")
        .setParameter(5, false)
        .setParameter(6, userId)
        .setParameter(7, tenantId)
        .executeUpdate();
    return id;
  }
}
