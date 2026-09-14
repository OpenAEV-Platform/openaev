package io.openaev.api.notification;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
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
@WithMockUser(isAdmin = true)
@DisplayName("notifications read and write isolation through the real HTTP endpoint")
class NotificationHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_NOTIFICATIONS = "/api/tenants/{tenantId}/notifications";
  private static final String TENANT_ME_SEARCH = TENANT_NOTIFICATIONS + "/me/search";
  private static final String TENANT_READ_BY_ID = TENANT_NOTIFICATIONS + "/{notificationId}/read";
  private static final String TENANT_DELETE_BY_ID = TENANT_NOTIFICATIONS + "/{notificationId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private UserService userService;

  private String tenantA;
  private String tenantB;
  private String notificationA;
  private String notificationB;

  @BeforeEach
  void seedTwoTenantsWithOneNotificationEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("notif-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("notif-iso-b").getId();
    String userId = userService.currentUser().getId();
    notificationA = seedNotification(tenantA, userId, "notif-a");
    notificationB = seedNotification(tenantB, userId, "notif-b");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's notification and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
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
    assertTrue(response.contains(notificationA), "A's notification must appear in A's scope");
    assertFalse(response.contains(notificationB), "B's notification must not leak into A's scope");
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header (no path tenant): search returns A's notification only")
  void searchViaHeaderReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/notifications/me/search")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(notificationA), "A's notification must appear when A is selected");
    assertFalse(response.contains(notificationB), "B's notification must not appear");
  }

  @Test
  @DisplayName(
      "under tenant A's path: marking B's notification read is rejected and leaves it unread")
  void markReadUnderTenantAOfBNotificationIsBlocked() throws Exception {
    mvc.perform(put(TENANT_READ_BY_ID, tenantA, notificationB).param("read", "true").with(csrf()))
        .andExpect(status().isNotFound());
    assertFalse(rawReadFlag(notificationB), "B's notification must stay unread");
  }

  @Test
  @DisplayName(
      "under tenant A's path: deleting B's notification is rejected and leaves it in place")
  void deleteUnderTenantAOfBNotificationIsBlocked() throws Exception {
    mvc.perform(delete(TENANT_DELETE_BY_ID, tenantA, notificationB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(notificationB), "B's notification must stay in the database");
  }

  private boolean rawReadFlag(String notificationId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT notification_is_read FROM notifications WHERE notification_id = ?")) {
                statement.setString(1, notificationId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() && rows.getBoolean(1);
                }
              }
            });
  }

  private long rawCount(String notificationId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM notifications WHERE notification_id = ?")) {
                statement.setString(1, notificationId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
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
