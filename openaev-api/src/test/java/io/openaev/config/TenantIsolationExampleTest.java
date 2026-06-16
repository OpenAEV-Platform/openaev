package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

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
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
@DisplayName("Tenant isolation — end-to-end example on the tags table")
class TenantIsolationExampleTest {

  private static final String TENANT_A = "example-tenant-a";
  private static final String TENANT_B = "example-tenant-b";
  private static final String TAG_A = "example-tag-a";
  private static final String TAG_B = "example-tag-b";

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void seedTwoTenantsWithOneTagEach() {
    insertTenant(TENANT_A);
    insertTenant(TENANT_B);
    insertTag(TAG_A, TENANT_A);
    insertTag(TAG_B, TENANT_B);
  }

  @Test
  @DisplayName("the active scope alone decides which tenant's rows a query returns")
  void scopeControlsRowVisibility() {
    setScope("");
    assertEquals(0, visibleExampleTags(), "no scope must see nothing (fail-closed)");

    setScope(TENANT_A);
    assertEquals(1, visibleExampleTags(), "tenant A sees only its own tag");
    assertEquals(TAG_A, theOnlyVisibleTag(), "and it is tenant A's tag, not B's");

    setScope(TENANT_B);
    assertEquals(1, visibleExampleTags(), "tenant B sees only its own tag");
    assertEquals(TAG_B, theOnlyVisibleTag(), "and it is tenant B's tag, not A's");

    setScope(TENANT_A + "," + TENANT_B);
    assertEquals(2, visibleExampleTags(), "a multi-tenant scope sees both");
  }

  @Test
  @DisplayName("a write under one scope cannot reach another tenant's row")
  void scopeProtectsWrites() {
    setScope(TENANT_A);
    assertEquals(0, deleteTag(TAG_B), "tenant A must not be able to delete tenant B's tag");
    assertEquals(1, deleteTag(TAG_A), "tenant A can delete its own tag");

    setScope(TENANT_B);
    assertEquals(1, visibleExampleTags(), "tenant B's row is intact and still its own");
    assertEquals(TAG_B, theOnlyVisibleTag());
  }

  private int deleteTag(String id) {
    return entityManager
        .createNativeQuery("DELETE FROM tags WHERE tag_id = :id")
        .setParameter("id", id)
        .executeUpdate();
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /**
   * Count of the two seeded tags visible under the current scope — the query is tenant-filtered.
   */
  private int visibleExampleTags() {
    Number count =
        (Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM tags WHERE tag_id IN (:a, :b)")
                .setParameter("a", TAG_A)
                .setParameter("b", TAG_B)
                .getSingleResult();
    return count.intValue();
  }

  private String theOnlyVisibleTag() {
    return (String)
        entityManager
            .createNativeQuery("SELECT tag_id FROM tags WHERE tag_id IN (:a, :b)")
            .setParameter("a", TAG_A)
            .setParameter("b", TAG_B)
            .getSingleResult();
  }

  private void insertTenant(String id) {
    entityManager
        .createNativeQuery("INSERT INTO tenants (tenant_id, tenant_name) VALUES (:id, :name)")
        .setParameter("id", id)
        .setParameter("name", id)
        .executeUpdate();
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
