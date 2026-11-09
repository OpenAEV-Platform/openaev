package io.openaev.context;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
@DisplayName("can_access_tenant SQL function")
class CanAccessTenantFunctionTest {

  @Autowired private EntityManager entityManager;

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  private boolean canAccess(String tenantId, boolean allowPlatform) {
    return (Boolean)
        entityManager
            .createNativeQuery("SELECT can_access_tenant(:tid, :ap)")
            .setParameter("tid", tenantId)
            .setParameter("ap", allowPlatform)
            .getSingleResult();
  }

  private boolean canAccessNullRow(boolean allowPlatform) {
    return (Boolean)
        entityManager
            .createNativeQuery("SELECT can_access_tenant(NULL, :ap)")
            .setParameter("ap", allowPlatform)
            .getSingleResult();
  }

  @Test
  @DisplayName("no scope denies every tenant row, and lets a platform row through")
  void noScopeDeniesEveryTenantRow() {
    assertFalse(canAccess("t1", false));
    assertFalse(canAccessNullRow(false));
    assertTrue(canAccessNullRow(true));
  }

  @Test
  @DisplayName("empty scope denies every tenant row, and lets a platform row through")
  void emptyScopeDeniesEveryTenantRow() {
    setScope("");
    assertFalse(canAccess("t1", false));
    assertFalse(canAccessNullRow(false));
    assertTrue(canAccessNullRow(true));
  }

  @Test
  @DisplayName("single-tenant scope allows only that tenant")
  void singleTenant() {
    setScope("t1");
    assertTrue(canAccess("t1", false));
    assertFalse(canAccess("t2", false));
  }

  @Test
  @DisplayName("multi-tenant scope allows each tenant in the list")
  void multiTenant() {
    setScope("t1,t2");
    assertTrue(canAccess("t1", false));
    assertTrue(canAccess("t2", false));
    assertFalse(canAccess("t3", false));
  }

  @Test
  @DisplayName("strict table: a null tenant row is denied even with an active scope")
  void strictTableNullRowDenied() {
    setScope("t1");
    assertFalse(canAccessNullRow(false));
  }

  @Test
  @DisplayName("dual-scope table: a null platform row is visible when a scope is active")
  void dualScopeNullRowAllowed() {
    setScope("t1");
    assertTrue(canAccessNullRow(true));
  }

  @Test
  @DisplayName("dual-scope table: a null platform row is visible when there is no scope")
  void dualScopeNullRowAllowedWithoutScope() {
    assertTrue(canAccessNullRow(true));
  }

  @Test
  @DisplayName("matches tenant ids exactly, never as a prefix or substring")
  void exactMatchOnly() {
    setScope("t1");
    assertFalse(canAccess("t10", false));
    assertFalse(canAccess("1", false));
  }

  @Test
  @DisplayName("dual-scope table: a non-null tenant is still restricted to the scope")
  void dualScopeRealTenantStillRestricted() {
    setScope("t1");
    assertTrue(canAccess("t1", true));
    assertFalse(canAccess("t2", true));
  }

  @Test
  @DisplayName("every answer but the platform-row read with no scope is that of the first version")
  void everyAnswerButThePlatformRowReadWithNoScopeIsUnchanged() {
    // Arrange: the body the function had when it was added (V5_27), under another name in this
    // rolled-back transaction, to compare it with the live one on every input
    entityManager
        .createNativeQuery(
            """
            CREATE FUNCTION pg_temp.first_can_access_tenant(
                row_tenant_id text, allow_platform boolean DEFAULT false)
            RETURNS boolean LANGUAGE sql STABLE AS $$
              SELECT CASE
                WHEN current_setting('app.current_tenants', true) IS NULL
                  OR current_setting('app.current_tenants', true) = '' THEN false
                ELSE COALESCE(
                       (allow_platform AND row_tenant_id IS NULL)
                         OR row_tenant_id = ANY (
                              string_to_array(current_setting('app.current_tenants', true), ',')),
                       false)
              END
            $$
            """)
        .executeUpdate();
    List<String> differing = new ArrayList<>();

    // Act
    for (String scope : new String[] {"unset", "", "t1", "t1,t2"}) {
      if (!"unset".equals(scope)) {
        setScope(scope);
      }
      for (String row : new String[] {null, "t1", "t2", "t3"}) {
        for (boolean allowPlatform : new boolean[] {false, true}) {
          boolean live = answer("can_access_tenant", row, allowPlatform);
          boolean first = answer("pg_temp.first_can_access_tenant", row, allowPlatform);
          if (live != first) {
            differing.add("scope=" + scope + " row=" + row + " allowPlatform=" + allowPlatform);
          }
        }
      }
    }

    // Assert: only a platform row read with allow_platform and no scope changed, the empty scope
    // (set_config leaves '' behind once used) and the never-set one both answer true now
    assertEquals(
        List.of("scope= row=null allowPlatform=true", "scope=unset row=null allowPlatform=true"),
        differing.stream().sorted().toList());
  }

  private boolean answer(String function, String tenantId, boolean allowPlatform) {
    String row = tenantId == null ? "NULL::text" : "'" + tenantId + "'";
    return (Boolean)
        entityManager
            .createNativeQuery("SELECT " + function + "(" + row + ", :ap)")
            .setParameter("ap", allowPlatform)
            .getSingleResult();
  }
}
