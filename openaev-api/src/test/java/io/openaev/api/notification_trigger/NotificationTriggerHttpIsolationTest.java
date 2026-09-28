package io.openaev.api.notification_trigger;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerPeriod;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
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

/**
 * Isolation of {@code notification_triggers} through the real {@link NotificationTriggerApi}.
 *
 * <p>The caller belongs to three tenants: the platform default one, A and B. The default membership
 * is what makes the write tests meaningful. {@code TenantContext} falls back to {@code
 * Tenant.DEFAULT_TENANT_UUID} whenever the route carries no {@code {tenantId}}, so on the header
 * route the v1 thread-local and the v2 request scope name DIFFERENT tenants. Every write that reads
 * the thread-local instead of the request scope therefore attributes the row, and resolves the
 * child triggers, against the default tenant while the caller selected A.
 *
 * <p>Each test method issues a single request so the per-request scope is set once: changing it
 * inside the test transaction would hit {@code TenantScopeTransactionAspect}'s nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=notification_triggers")
@WithMockUser(isAdmin = true)
@DisplayName("notification_triggers read and write isolation through the real HTTP endpoint")
class NotificationTriggerHttpIsolationTest extends IntegrationTest {

  private static final String TRIGGER_BY_ID = "/api/tenants/{tenantId}/notification-triggers/{id}";
  private static final String TRIGGERS = "/api/tenants/{tenantId}/notification-triggers";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String triggerA;
  private String triggerB;
  private String childDefault;
  private String notifierDefault;
  private String notifierA;

  @BeforeEach
  void seedThreeTenantsWithOneTriggerEach() throws Exception {
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantA = tenantHelper.createTenantWithCurrentUser("trigger-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("trigger-iso-b").getId();

    notifierDefault = seedNotifier(Tenant.DEFAULT_TENANT_UUID, "notifier-default");
    notifierA = seedNotifier(tenantA, "notifier-a");

    triggerA = seedTrigger(tenantA, "trigger-a");
    triggerB = seedTrigger(tenantB, "trigger-b");
    childDefault = seedTrigger(Tenant.DEFAULT_TENANT_UUID, "child-default");
  }

  // -- READS --

  @Test
  @DisplayName("under tenant A's path: A's trigger is visible, B's is hidden")
  void underTenantAPath() throws Exception {
    mvc.perform(get(TRIGGER_BY_ID, tenantA, triggerA)).andExpect(status().isOk());
    mvc.perform(get(TRIGGER_BY_ID, tenantA, triggerB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's trigger is visible, A's is hidden")
  void underTenantBPath() throws Exception {
    mvc.perform(get(TRIGGER_BY_ID, tenantB, triggerB)).andExpect(status().isOk());
    mvc.perform(get(TRIGGER_BY_ID, tenantB, triggerA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's trigger and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String response = search("/api/tenants/" + tenantA + "/notification-triggers/search", null);
    assertTrue(response.contains(triggerA), "A's trigger must appear in A's search results");
    assertFalse(response.contains(triggerB), "B's trigger must not appear in A's search results");
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: search returns A's trigger and not B's")
  void searchViaHeaderReturnsOnlyA() throws Exception {
    String response = search("/api/notification-triggers/search", tenantA);
    assertTrue(response.contains(triggerA), "A's trigger must appear when A is selected by header");
    assertFalse(response.contains(triggerB), "B's trigger must not appear");
  }

  // -- WRITES --

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAPathIsAttributedToA() throws Exception {
    String response =
        mvc.perform(
                post(TRIGGERS, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(liveTrigger("created-under-a-path", notifierA)))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(
        tenantA,
        rawTenantOf(JsonPath.read(response, "$.notification_trigger_id")),
        "the created trigger must belong to tenant A");
  }

  @Test
  @DisplayName(
      "a create selecting tenant A by header is attributed to A, not to the thread-local default")
  void createViaHeaderIsAttributedToTheSelectedTenant() throws Exception {
    String response =
        mvc.perform(
                post("/api/notification-triggers")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(liveTrigger("created-via-header", notifierDefault)))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(
        tenantA,
        rawTenantOf(JsonPath.read(response, "$.notification_trigger_id")),
        "the request selected tenant A, so the row must be attributed to A. The default tenant here"
            + " means the write read TenantContext (which falls back to the default when the route"
            + " carries no path tenant) instead of the request scope");
  }

  @Test
  @DisplayName(
      "a create under a multi-tenant scope must not compose a digest with another tenant's child")
  void createUnderAMultiTenantScopeDoesNotComposeAForeignChild() throws Exception {
    // The scope holds the default tenant AND tenant A. Resolving children from the v1 thread-local
    // (which is the default tenant here) picks a child the inspector happily returns, because the
    // default tenant is inside the read scope - while the row the caller means to write belongs to
    // exactly one tenant that the request never names. The write must be refused before any
    // child is resolved, not attributed to whichever tenant the thread-local happened to hold.
    mvc.perform(
            post("/api/notification-triggers")
                .header("X-Tenant-Ids", Tenant.DEFAULT_TENANT_UUID + "," + tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(digestWith(childDefault, notifierDefault)))
                .with(csrf()))
        .andExpect(status().isBadRequest());

    assertEquals(
        0L,
        rawChildLinkCount(childDefault),
        "no digest may compose the default tenant's trigger under a scope that does not name a"
            + " single write tenant");
  }

  @Test
  @DisplayName(
      "a create selecting only tenant A must not compose a digest with a default-tenant child")
  void createViaHeaderDoesNotResolveAChildOfAnotherTenant() throws Exception {
    mvc.perform(
            post("/api/notification-triggers")
                .header("X-Tenant-Ids", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(digestWith(childDefault, notifierDefault)))
                .with(csrf()))
        .andExpect(status().is4xxClientError());

    assertEquals(
        0L,
        rawChildLinkCount(childDefault),
        "the child belongs to the default tenant while the request writes into tenant A: composing"
            + " them builds a cross-tenant digest that would replay another tenant's outbox events");
  }

  @Test
  @DisplayName(
      "non-vacuity guard: the same digest shape DOES link a child owned by the write tenant")
  void childIsLinkableAtAll() throws Exception {
    String childA = seedTrigger(tenantA, "child-a");
    mvc.perform(
            post(TRIGGERS, tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(digestWith(childA, notifierA)))
                .with(csrf()))
        .andExpect(status().isOk());

    assertEquals(
        1L,
        rawChildLinkCount(childA),
        "a child owned by the write tenant must link; without this the cross-tenant assertion above"
            + " could pass simply because no digest ever composes anything");
  }

  @Test
  @DisplayName("a create with a multi-tenant scope is refused: a row belongs to exactly one tenant")
  void createUnderAMultiTenantScopeIsRefused() throws Exception {
    mvc.perform(
            post("/api/notification-triggers")
                .header("X-Tenant-Ids", tenantA + "," + tenantB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(liveTrigger("ambiguous-scope", notifierDefault)))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertEquals(
        0L, rawCountByName("ambiguous-scope"), "no trigger may be stored under an ambiguous scope");
  }

  @Test
  @DisplayName("a create with no tenant selector at all is refused")
  void createWithoutSelectorIsRefused() throws Exception {
    mvc.perform(
            post("/api/notification-triggers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(liveTrigger("no-selector", notifierDefault)))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertEquals(
        0L, rawCountByName("no-selector"), "no trigger may be stored without a tenant selector");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's trigger is not found and leaves it untouched")
  void updateUnderTenantAOfBTriggerIsBlocked() throws Exception {
    mvc.perform(
            put(TRIGGER_BY_ID, tenantA, triggerB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(liveTrigger("hijacked", notifierA)))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("trigger-b", rawName(triggerB), "B's trigger must be untouched by tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's trigger is not found and leaves it in place")
  void deleteUnderTenantAOfBTriggerIsBlocked() throws Exception {
    mvc.perform(delete(TRIGGER_BY_ID, tenantA, triggerB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(triggerB), "B's trigger must survive tenant A's delete attempt");
  }

  @Test
  @DisplayName("under tenant A's path: A can delete its own trigger")
  void deleteUnderTenantADeletesOwnTrigger() throws Exception {
    mvc.perform(delete(TRIGGER_BY_ID, tenantA, triggerA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(0L, rawCount(triggerA), "A's own trigger must be deleted");
  }

  // -- FIXTURES --

  private String search(String uri, String headerTenant) throws Exception {
    var request =
        post(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(PaginationFixture.getDefault().textSearch("").build()))
            .with(csrf());
    if (headerTenant != null) {
      request = request.header("X-Tenant-Ids", headerTenant);
    }
    return mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private NotificationTriggerInput digestWith(String childId, String notifierId) {
    return NotificationTriggerInput.builder()
        .name("digest-" + childId)
        .type(NotificationTriggerType.DIGEST)
        .period(NotificationTriggerPeriod.DAY)
        .triggerTime("09:00")
        .childTriggerIds(List.of(childId))
        .notifierIds(List.of(notifierId))
        .build();
  }

  private NotificationTriggerInput liveTrigger(String name, String notifierId) {
    return NotificationTriggerInput.builder()
        .name(name)
        .type(NotificationTriggerType.LIVE)
        .resourceType(ResourceType.SCENARIO)
        .eventTypes(List.of(NotificationTriggerEventType.CREATE))
        .notifierIds(List.of(notifierId))
        .build();
  }

  private String seedNotifier(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO notifiers (notifier_id, notifier_name, notifier_type,"
                + " notifier_configuration, notifier_built_in, tenant_id)"
                + " VALUES (:id, :name, 'UI', CAST('{}' AS jsonb), false, :tenant)")
        .setParameter("id", id)
        .setParameter("name", name + "-" + id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedTrigger(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO notification_triggers (notification_trigger_id,"
                + " notification_trigger_name, notification_trigger_type,"
                + " notification_trigger_enabled, notification_trigger_resource_type,"
                + " notification_trigger_event_types, user_id, tenant_id)"
                + " VALUES (:id, :name, 'LIVE', true, 'SCENARIO', CAST(:events AS jsonb),"
                + " :user, :tenant)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("events", "[\"CREATE\"]")
        .setParameter("user", testUserHolder.get().getId())
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  // Ground truth, scope-free: raw JDBC on the test's own connection sees the uncommitted seed and
  // the rewriter never touches a statement it did not generate. Flush first so any pending scoped
  // INSERT/UPDATE/DELETE has reached the database.
  private String rawTenantOf(String triggerId) {
    return rawString(
        "SELECT tenant_id FROM notification_triggers WHERE notification_trigger_id = ?", triggerId);
  }

  private String rawName(String triggerId) {
    return rawString(
        "SELECT notification_trigger_name FROM notification_triggers"
            + " WHERE notification_trigger_id = ?",
        triggerId);
  }

  private long rawCount(String triggerId) {
    return rawLong(
        "SELECT count(*) FROM notification_triggers WHERE notification_trigger_id = ?", triggerId);
  }

  private long rawCountByName(String name) {
    return rawLong(
        "SELECT count(*) FROM notification_triggers WHERE notification_trigger_name = ?", name);
  }

  private long rawChildLinkCount(String childId) {
    return rawLong(
        "SELECT count(*) FROM notification_triggers_children"
            + " WHERE child_notification_trigger_id = ?",
        childId);
  }

  private String rawString(String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawLong(String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
