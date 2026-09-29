package io.openaev.rest.asset_group;

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
import io.openaev.rest.asset_group.form.AssetGroupInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
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
 * End-to-end proof that, with {@code asset_groups} activated, the tenant scope carried by the URL
 * path isolates the table through the real {@link AssetGroupApi} endpoints.
 *
 * <p>Replaces the {@code TenantIsolation} nested class that used to live in {@code
 * AssetGroupApiTest}. That one could not be kept for three independent reasons: it carried no
 * {@code @TestPropertySource}, so with the test profile's empty allowlist the inspector never fired
 * and it proved v1 filtering only; its cross-tenant assertion was {@code assertEquals(0,
 * totalElements)}, which stays green through exactly the fail-closed regression being guarded
 * against; and it crossed two tenant paths inside one transaction, which {@link
 * io.openaev.aop.TenantScopeTransactionAspect}'s nesting guard refuses once the handlers carry
 * {@code TxCtx}.
 *
 * <p>Every test method here therefore stays on ONE tenant path, and every positive assertion is
 * non-empty: an assertion on an empty result proves nothing about scoping.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=asset_groups")
@WithMockUser(isAdmin = true)
@DisplayName("asset_groups read and write isolation through the real HTTP endpoints")
class AssetGroupHttpIsolationTest extends IntegrationTest {

  private static final String BY_ID = "/api/tenants/{tenantId}/asset_groups/{assetGroupId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String tenantB;
  private String groupA;
  private String groupB;

  @BeforeEach
  void seedTwoTenantsWithOneGroupEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("ag-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("ag-http-iso-b").getId();
    groupA = seedAssetGroup(tenantA, "iso-group-a");
    groupB = seedAssetGroup(tenantB, "iso-group-b");
  }

  // -- READ: own row visible (non-empty), other tenant's row hidden ---------

  @Test
  @DisplayName("under tenant A's path: A's group is readable and carries its real name")
  void readOwnGroupUnderOwnPath() throws Exception {
    String body =
        mvc.perform(get(BY_ID, tenantA, groupA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Non-empty on purpose: a 200 with a hollow body would pass a status-only assertion while the
    // fail-closed regression is exactly what makes bodies hollow.
    assertEquals("iso-group-a", JsonPath.read(body, "$.asset_group_name"));
  }

  @Test
  @DisplayName("under tenant A's path: B's group is not found")
  void readOtherTenantGroupIsNotFound() throws Exception {
    mvc.perform(get(BY_ID, tenantA, groupB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's group is readable, A's is not")
  void readIsSymmetricUnderTenantB() throws Exception {
    mvc.perform(get(BY_ID, tenantB, groupB)).andExpect(status().isOk());
    mvc.perform(get(BY_ID, tenantB, groupA)).andExpect(status().isNotFound());
  }

  // -- SEARCH: A's rows present, B's absent --------------------------------

  @Test
  @DisplayName("under tenant A's path: search returns A's group and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body =
        mvc.perform(
                post("/api/tenants/{tenantId}/asset_groups/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        asJsonString(
                            PaginationFixture.getDefault().textSearch("iso-group").build()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(body.contains(groupA), "A's group must appear in A's search results: " + body);
    assertFalse(body.contains(groupB), "B's group must not appear in A's search results");
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: search returns A's group and not B's")
  void searchViaHeaderReturnsOnlyA() throws Exception {
    String body =
        mvc.perform(
                post("/api/asset_groups/search")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        asJsonString(
                            PaginationFixture.getDefault().textSearch("iso-group").build()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        body.contains(groupA), "A's group must appear when A is selected via header: " + body);
    assertFalse(body.contains(groupB), "B's group must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: the option list returns A's group and not B's")
  void optionsUnderTenantAReturnsOnlyA() throws Exception {
    // With no inputFilterOption and no sourceId the endpoint falls back to ATOMIC_TESTING, whose
    // native query INNER JOINs injects_asset_groups. An unlinked asset group is absent by design,
    // not by fail-closed, so both tenants need a linked inject for this to test scoping at all.
    linkToNewInject(tenantA, groupA);
    linkToNewInject(tenantB, groupB);

    String body =
        mvc.perform(get("/api/tenants/{tenantId}/asset_groups/options", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(body.contains(groupA), "A's group must be offered as an option: " + body);
    assertFalse(body.contains(groupB), "B's group must not be offered to A");
  }

  // -- WRITE: attribution and cross-tenant refusal --------------------------

  @Test
  @DisplayName("a create under tenant A's path is stored with tenant A's id")
  void createUnderTenantAIsAttributedToA() throws Exception {
    AssetGroupInput input = new AssetGroupInput();
    input.setName("created-under-a");
    String body =
        mvc.perform(
                post("/api/tenants/{tenantId}/asset_groups", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(body, "$.asset_group_id");
    entityManager.flush();
    String storedTenant =
        rawSingle("SELECT tenant_id FROM asset_groups WHERE asset_group_id = ?", createdId);
    assertEquals(tenantA, storedTenant, "the created asset group must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused, not silently attributed")
  void createWithoutSelectorIsRejected() throws Exception {
    // An unscoped create must be refused loudly rather than attributed to whichever tenant the v1
    // thread-local happens to hold. The refusal happens upstream, in TenantWriteScopeResolver,
    // before the row is ever built, so it is a 400 rather than a row quietly landing in the ambient
    // tenant (AssetGroup still carries TenantBaseListener, which #7844 removes) or, once that
    // listener is gone, a constraint violation on the NOT NULL tenant column.
    AssetGroupInput input = new AssetGroupInput();
    input.setName("no-selector-" + UUID.randomUUID());
    mvc.perform(
            post("/api/asset_groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: updating B's group is not found and leaves it untouched")
  void updateOtherTenantGroupIsBlocked() throws Exception {
    AssetGroupInput input = new AssetGroupInput();
    input.setName("hijacked");
    mvc.perform(
            put(BY_ID, tenantA, groupB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("iso-group-b", rawName(groupB), "B's group must be untouched by tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's group leaves it in place")
  void deleteOtherTenantGroupIsBlocked() throws Exception {
    mvc.perform(delete(BY_ID, tenantA, groupB).with(csrf()));
    assertEquals(1L, rawCount(groupB), "B's group must survive tenant A's delete attempt");
  }

  // -- helpers -------------------------------------------------------------

  private void linkToNewInject(String tenantId, String assetGroupId) {
    String injectId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
                + " inject_depends_duration, tenant_id)"
                + " VALUES (:id, 'iso-inject', false, true, 0, :tenantId)")
        .setParameter("id", injectId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    entityManager
        .createNativeQuery(
            "INSERT INTO injects_asset_groups (inject_id, asset_group_id)"
                + " VALUES (:injectId, :assetGroupId)")
        .setParameter("injectId", injectId)
        .setParameter("assetGroupId", assetGroupId)
        .executeUpdate();
  }

  private String seedAssetGroup(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO asset_groups (asset_group_id, asset_group_name,"
                + " asset_group_dynamic_filter, asset_group_created_at, asset_group_updated_at,"
                + " tenant_id) VALUES (:id, :name, CAST('{}' AS json), now(), now(), :tenantId)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    return id;
  }

  /**
   * Ground truth, unaffected by the request scope: the seed and the assertion share this session.
   */
  private String rawName(String id) {
    return rawSingle("SELECT asset_group_name FROM asset_groups WHERE asset_group_id = ?", id);
  }

  private long rawCount(String id) {
    return Long.parseLong(
        rawSingle("SELECT count(*) FROM asset_groups WHERE asset_group_id = ?", id));
  }

  /**
   * Ground truth. Raw JDBC on the test's own connection, NOT {@code createNativeQuery}: the latter
   * goes through Hibernate and therefore through the tenant statement inspector, so it would be
   * scoped like any other read and could not see the row it is meant to prove untouched. Getting
   * this wrong turns a correctly-behaving product into a fake cross-tenant leak.
   */
  private String rawSingle(String sql, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement st = connection.prepareStatement(sql)) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                  return rs.next() ? rs.getString(1) : null;
                }
              }
            });
  }
}
