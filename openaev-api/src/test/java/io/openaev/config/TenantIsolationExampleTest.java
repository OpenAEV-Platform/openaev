package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end proof that the tenant filter actually isolates rows when wired. The real inspector is
 * activated for {@code tags} through the production wiring ({@code openaev.tenant.active-tables}),
 * so the same query returns different rows depending only on the transaction's {@code
 * app.current_tenants} scope: a tenant sees its own rows, never another's, and an empty scope sees
 * nothing (fail-closed). This exercises the whole chain against PostgreSQL (set_config, the SQL
 * rewrite, {@code can_access_tenant}, and real execution), so it proves correctness, not just that
 * a guard is present.
 */
@SpringBootTest(properties = "openaev.tenant.active-tables=tags")
@DisplayName("Tenant isolation — end-to-end example on the tags table")
class TenantIsolationExampleTest extends TenantIsolationIntegrationTest {

  private static final String TENANT_A = "example-tenant-a";
  private static final String TENANT_B = "example-tenant-b";
  private static final String TAG_A = "example-tag-a";
  private static final String TAG_B = "example-tag-b";

  @BeforeEach
  void seedTwoTenantsWithOneTagEach() {
    seedTenant(TENANT_A);
    seedTenant(TENANT_B);
    insertTag(TAG_A, TENANT_A);
    insertTag(TAG_B, TENANT_B);
  }

  @Test
  @DisplayName("the active scope alone decides which tenant's rows a query returns")
  void scopeControlsRowVisibility() {
    setScope("");
    assertEquals(0, visibleTags(), "no scope must see nothing (fail-closed)");

    setScope(TENANT_A);
    assertEquals(1, visibleTags(), "tenant A sees only its own tag");
    assertEquals(
        TAG_A, onlyVisible("tags", "tag_id", TAG_A, TAG_B), "and it is tenant A's, not B's");

    setScope(TENANT_B);
    assertEquals(1, visibleTags(), "tenant B sees only its own tag");
    assertEquals(
        TAG_B, onlyVisible("tags", "tag_id", TAG_A, TAG_B), "and it is tenant B's, not A's");

    setScope(TENANT_A + "," + TENANT_B);
    assertEquals(2, visibleTags(), "a multi-tenant scope sees both");
  }

  @Test
  @DisplayName("a write under one scope cannot reach another tenant's row")
  void scopeProtectsWrites() {
    setScope(TENANT_A);
    assertEquals(0, deleteRow("tags", "tag_id", TAG_B), "A must not delete B's tag");
    assertEquals(1, deleteRow("tags", "tag_id", TAG_A), "A can delete its own tag");

    setScope(TENANT_B);
    assertEquals(1, visibleTags(), "tenant B's row is intact and still its own");
    assertEquals(TAG_B, onlyVisible("tags", "tag_id", TAG_A, TAG_B));
  }

  @Test
  @DisplayName("UPDATE ... FROM runs under the scope and touches only in-scope rows")
  void updateFromRunsUnderScope() {
    setScope(TENANT_A);
    int updated =
        entityManager
            .createNativeQuery(
                "UPDATE tags t SET tag_name = 'x' FROM tags s"
                    + " WHERE s.tag_id = t.tag_id AND t.tag_id IN (:a, :b)")
            .setParameter("a", TAG_A)
            .setParameter("b", TAG_B)
            .executeUpdate();
    assertEquals(1, updated, "only tenant A's tag is in scope for both the target and the source");
  }

  @Test
  @DisplayName("DELETE ... USING runs under the scope and touches only in-scope rows")
  void deleteUsingRunsUnderScope() {
    setScope(TENANT_A);
    int deleted =
        entityManager
            .createNativeQuery(
                "DELETE FROM tags t USING tags s"
                    + " WHERE s.tag_id = t.tag_id AND t.tag_id IN (:a, :b)")
            .setParameter("a", TAG_A)
            .setParameter("b", TAG_B)
            .executeUpdate();
    assertEquals(1, deleted, "only tenant A's tag is in scope for both the target and the source");
  }

  @Test
  @DisplayName("INSERT ... SELECT into a tenant table writes only in-scope rows")
  void insertSelectRunsUnderScope() {
    setScope(TENANT_A);
    assertEquals(1, copyTag(TAG_A, "copy-of-a"), "a tag the scope can read and own is copied");
    assertEquals(
        0, copyTag(TAG_B, "copy-of-b"), "tenant B's tag is neither readable nor writable from A");
  }

  @Test
  @DisplayName("SQL the filter cannot parse is refused on an active table (fail-closed)")
  void unparseableOnActiveTableIsRefused() {
    setScope(TENANT_A);
    Throwable thrown =
        assertThrows(
            Exception.class,
            () -> entityManager.createNativeQuery("GARBAGE NOT SQL tags ;;;").executeUpdate());
    assertTrue(
        causedByTenantFiltering(thrown),
        "an un-filterable statement must fail closed via TenantFilteringException, got: " + thrown);
  }

  private int visibleTags() {
    return countVisible("tags", "tag_id", TAG_A, TAG_B);
  }

  private int copyTag(String sourceId, String newId) {
    return entityManager
        .createNativeQuery(
            "INSERT INTO tags (tag_id, tag_name, tag_created_at, tag_updated_at, tenant_id)"
                + " SELECT :newId, :newId, now(), now(), t.tenant_id"
                + " FROM tags t WHERE t.tag_id = :src")
        .setParameter("newId", newId)
        .setParameter("src", sourceId)
        .executeUpdate();
  }

  private static boolean causedByTenantFiltering(Throwable thrown) {
    for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
      if (cause instanceof TenantFilteringException) {
        return true;
      }
    }
    return false;
  }

  private void insertTag(String id, String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO tags (tag_id, tag_name, tag_created_at, tag_updated_at, tenant_id)"
                + " VALUES (:id, :name, now(), now(), :tenant)")
        .setParameter("id", id)
        .setParameter("name", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
  }
}
