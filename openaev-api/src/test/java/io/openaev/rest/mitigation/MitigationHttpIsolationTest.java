package io.openaev.rest.mitigation;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MitigationInputFixture.createMitigationInput;
import static io.openaev.utils.fixtures.MitigationInputFixture.createMitigationUpdateInput;
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

/**
 * End-to-end proof that, with {@code mitigations} activated, the tenant scope set from the URL path
 * isolates the table through the real {@link MitigationApi} endpoints. A user who belongs to two
 * tenants sees a mitigation only under its own tenant's path, never another tenant's.
 *
 * <p>Each test stays on a single tenant path so the per-request scope is set once: re-applying the
 * same scope inside the test transaction is tolerated, changing it would hit the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=mitigations")
@WithMockUser(isAdmin = true)
@DisplayName("mitigations read and write isolation through the real HTTP endpoint")
class MitigationHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_MITIGATIONS = "/api/tenants/{tenantId}/mitigations";
  private static final String TENANT_MITIGATION_BY_ID = TENANT_MITIGATIONS + "/{mitigationId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String mitigationA;
  private String mitigationB;

  @BeforeEach
  void seedTwoTenantsWithOneMitigationEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("miti-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("miti-iso-b").getId();
    mitigationA = seedMitigation(tenantA, "mitigation-a", "M9901");
    mitigationB = seedMitigation(tenantB, "mitigation-b", "M9902");
  }

  @Test
  @DisplayName("under tenant A's path: A's mitigation is visible, B's is hidden")
  void underTenantAPath() throws Exception {
    mvc.perform(get(TENANT_MITIGATION_BY_ID, tenantA, mitigationA)).andExpect(status().isOk());
    mvc.perform(get(TENANT_MITIGATION_BY_ID, tenantA, mitigationB))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's mitigation is visible, A's is hidden")
  void underTenantBPath() throws Exception {
    mvc.perform(get(TENANT_MITIGATION_BY_ID, tenantB, mitigationB)).andExpect(status().isOk());
    mvc.perform(get(TENANT_MITIGATION_BY_ID, tenantB, mitigationA))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header (no path tenant): search returns A's mitigation and not B's")
  void searchViaHeaderReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/mitigations/search")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(mitigationA), "A's mitigation must appear when A is selected via header");
    assertFalse(response.contains(mitigationB), "B's mitigation must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's mitigation and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post(TENANT_MITIGATIONS + "/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(mitigationA), "A's mitigation must appear in A's search results");
    assertFalse(
        response.contains(mitigationB), "B's mitigation must not appear in A's search results");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    // Arrange
    String response =
        mvc.perform(
                post(TENANT_MITIGATIONS, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createMitigationInput("created-under-a", "M9910")))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(response, "$.mitigation_id");
    String storedTenant =
        (String)
            entityManager
                .createNativeQuery("SELECT tenant_id FROM mitigations WHERE mitigation_id = ?1")
                .setParameter(1, createdId)
                .getSingleResult();
    assertEquals(tenantA, storedTenant, "the created mitigation must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    mvc.perform(
            post("/api/mitigations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createMitigationInput("no-selector", "M9911")))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("upserting the same external_id under A and B yields two distinct rows")
  void upsertSameExternalIdUnderTwoTenantsYieldsTwoRows() throws Exception {
    // Arrange: seed a row for tenant B directly (bypasses the scope, no tenant-switch mid-tx)
    String sharedExternalId = "M9920";
    seedMitigation(tenantB, "shared-b", sharedExternalId);

    // Act: upsert the same external_id under tenant A — must produce a second independent row
    String upsertBody =
        "{\"mitigations\":[{\"mitigation_name\":\"shared-a\","
            + "\"mitigation_external_id\":\""
            + sharedExternalId
            + "\","
            + "\"mitigation_stix_id\":\"course-of-action--"
            + UUID.randomUUID()
            + "\","
            + "\"mitigation_attack_patterns\":[]}]}";
    mvc.perform(
            post(TENANT_MITIGATIONS + "/upsert", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertBody)
                .with(csrf()))
        .andExpect(status().isOk());

    // Assert: two rows share the same external_id — one per tenant
    entityManager.flush();
    long rowCount =
        entityManager
            .unwrap(Session.class)
            .doReturningWork(
                connection -> {
                  try (PreparedStatement stmt =
                      connection.prepareStatement(
                          "SELECT count(*) FROM mitigations WHERE mitigation_external_id = ?")) {
                    stmt.setString(1, sharedExternalId);
                    try (ResultSet rs = stmt.executeQuery()) {
                      rs.next();
                      return rs.getLong(1);
                    }
                  }
                });
    assertEquals(2L, rowCount, "each tenant must own an independent row for the same external_id");
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own mitigation")
  void updateUnderTenantAUpdatesOwnMitigation() throws Exception {
    mvc.perform(
            put(TENANT_MITIGATION_BY_ID, tenantA, mitigationA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createMitigationUpdateInput("renamed-a", "M9901")))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals("renamed-a", rawName(mitigationA), "A's own mitigation must be updated");
  }

  @Test
  @DisplayName(
      "under tenant A's path: updating B's mitigation is not found and leaves it untouched")
  void updateUnderTenantAOfBMitigationIsBlocked() throws Exception {
    mvc.perform(
            put(TENANT_MITIGATION_BY_ID, tenantA, mitigationB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createMitigationUpdateInput("hijacked", "M9902")))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("mitigation-b", rawName(mitigationB), "B's mitigation must be untouched");
  }

  @Test
  @DisplayName("under tenant A's path: A can delete its own mitigation")
  void deleteUnderTenantADeletesOwnMitigation() throws Exception {
    mvc.perform(delete(TENANT_MITIGATION_BY_ID, tenantA, mitigationA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(0L, rawCount(mitigationA), "A's own mitigation must be deleted");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's mitigation is a no-op and leaves it in place")
  void deleteUnderTenantAOfBMitigationIsBlocked() throws Exception {
    mvc.perform(delete(TENANT_MITIGATION_BY_ID, tenantA, mitigationB).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(
        1L, rawCount(mitigationB), "B's mitigation must survive tenant A's delete attempt");
  }

  // Ground-truth reads, bypassing the scope: raw JDBC on the test's own connection sees the
  // uncommitted seed and the rewriter does not touch a statement it never generated. A flush first
  // forces any pending scoped UPDATE/DELETE to reach the database.
  private String rawName(String mitigationId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT mitigation_name FROM mitigations WHERE mitigation_id = ?")) {
                statement.setString(1, mitigationId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String mitigationId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM mitigations WHERE mitigation_id = ?")) {
                statement.setString(1, mitigationId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private String seedMitigation(String tenantId, String name, String externalId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO mitigations"
                + " (mitigation_id, mitigation_name, mitigation_external_id,"
                + "  mitigation_stix_id, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, externalId)
        .setParameter(4, "course-of-action--" + UUID.randomUUID())
        .setParameter(5, tenantId)
        .executeUpdate();
    return id;
  }
}
