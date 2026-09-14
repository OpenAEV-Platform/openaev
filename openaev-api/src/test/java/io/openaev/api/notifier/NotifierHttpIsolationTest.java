package io.openaev.api.notifier;

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
import io.openaev.database.model.NotifierType;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
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
 * End-to-end proof that, with {@code notifiers} activated, the tenant scope resolved from the
 * request isolates the table through the real {@link NotifierApi} endpoints.
 *
 * <p>Each test stays on ONE tenant path: the scope is set once per transaction and changing it
 * inside the same one is refused by the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=notifiers")
@WithMockUser(isAdmin = true)
@DisplayName("notifiers read and write isolation through the real HTTP endpoint")
class NotifierHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_NOTIFIERS = "/api/tenants/{tenantId}/notifiers";
  private static final String TENANT_NOTIFIER_BY_ID = TENANT_NOTIFIERS + "/{notifierId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String notifierA;
  private String notifierB;

  @BeforeEach
  void seedTwoTenantsWithOneNotifierEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("notifier-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("notifier-iso-b").getId();
    notifierA = seedNotifier(tenantA, "notifier-a");
    notifierB = seedNotifier(tenantB, "notifier-b");
  }

  @Test
  @DisplayName("under tenant A's path: A's notifier is visible, B's is not found")
  void underTenantAPath() throws Exception {
    mvc.perform(get(TENANT_NOTIFIER_BY_ID, tenantA, notifierA)).andExpect(status().isOk());
    mvc.perform(get(TENANT_NOTIFIER_BY_ID, tenantA, notifierB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's notifier is visible, A's is not found")
  void underTenantBPath() throws Exception {
    mvc.perform(get(TENANT_NOTIFIER_BY_ID, tenantB, notifierB)).andExpect(status().isOk());
    mvc.perform(get(TENANT_NOTIFIER_BY_ID, tenantB, notifierA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: the list returns A's notifier and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_NOTIFIERS, tenantA).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(notifierA), "A's notifier must appear in A's list");
    assertFalse(response.contains(notifierB), "B's notifier must not leak into A's list");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's notifier and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post(TENANT_NOTIFIERS + "/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(notifierA), "A's notifier must appear in A's search results");
    assertFalse(response.contains(notifierB), "B's notifier must not appear in A's search results");
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header (no path tenant): search returns A's notifier only")
  void searchViaHeaderReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/notifiers/search")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(notifierA), "A's notifier must appear when A is selected");
    assertFalse(response.contains(notifierB), "B's notifier must not appear");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    NotifierInput input =
        NotifierInput.builder()
            .name("created-under-a")
            .type(NotifierType.WEBHOOK)
            .configuration(Map.of("url", "https://example.org/created-under-a"))
            .build();
    String response =
        mvc.perform(
                post(TENANT_NOTIFIERS, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(response, "$.notifier_id");
    assertEquals(tenantA, rawTenant(createdId), "the created notifier must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    NotifierInput input =
        NotifierInput.builder()
            .name("no-selector")
            .type(NotifierType.WEBHOOK)
            .configuration(Map.of("url", "https://example.org/no-selector"))
            .build();
    mvc.perform(
            post("/api/notifiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own notifier")
  void updateUnderTenantAUpdatesOwnNotifier() throws Exception {
    mvc.perform(
            put(TENANT_NOTIFIER_BY_ID, tenantA, notifierA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput("renamed-a")))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals("renamed-a", rawName(notifierA), "A's own notifier must be updated");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's notifier is not found and leaves it untouched")
  void updateUnderTenantAOfBNotifierIsBlocked() throws Exception {
    mvc.perform(
            put(TENANT_NOTIFIER_BY_ID, tenantA, notifierB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput("hijacked")))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("notifier-b", rawName(notifierB), "B's notifier must be untouched by tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: A can delete its own notifier")
  void deleteUnderTenantADeletesOwnNotifier() throws Exception {
    mvc.perform(delete(TENANT_NOTIFIER_BY_ID, tenantA, notifierA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(0L, rawCount(notifierA), "A's own notifier must be deleted");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's notifier is refused and leaves it in place")
  void deleteUnderTenantAOfBNotifierIsBlocked() throws Exception {
    mvc.perform(delete(TENANT_NOTIFIER_BY_ID, tenantA, notifierB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(notifierB), "B's notifier must survive tenant A's delete attempt");
  }

  private NotifierInput updateInput(String name) {
    return NotifierInput.builder()
        .name(name)
        .type(NotifierType.WEBHOOK)
        .configuration(Map.of("url", "https://example.org/" + name))
        .build();
  }

  private String seedNotifier(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO notifiers (notifier_id, notifier_name, notifier_type,"
                + " notifier_configuration, notifier_built_in, tenant_id)"
                + " VALUES (?1, ?2, ?3, CAST(?4 AS jsonb), ?5, ?6)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, NotifierType.WEBHOOK.name())
        .setParameter(4, "{\"url\":\"https://example.org/" + name + "\"}")
        .setParameter(5, false)
        .setParameter(6, tenantId)
        .executeUpdate();
    return id;
  }

  // Ground truth: raw JDBC is never rewritten by the inspector, so a fail-closed read cannot make
  // an assertion agree with a broken endpoint for the wrong reason.
  private String rawName(String notifierId) {
    return rawQuery(
        "SELECT notifier_name FROM notifiers WHERE notifier_id = ?",
        notifierId,
        rows -> rows.next() ? rows.getString(1) : null);
  }

  private String rawTenant(String notifierId) {
    return rawQuery(
        "SELECT tenant_id FROM notifiers WHERE notifier_id = ?",
        notifierId,
        rows -> rows.next() ? rows.getString(1) : null);
  }

  private long rawCount(String notifierId) {
    return rawQuery(
        "SELECT count(*) FROM notifiers WHERE notifier_id = ?",
        notifierId,
        rows -> {
          rows.next();
          return rows.getLong(1);
        });
  }

  private <T> T rawQuery(String sql, String notifierId, RawReader<T> reader) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, notifierId);
                try (ResultSet rows = statement.executeQuery()) {
                  return reader.read(rows);
                }
              }
            });
  }

  @FunctionalInterface
  private interface RawReader<T> {
    T read(ResultSet rows) throws java.sql.SQLException;
  }
}
