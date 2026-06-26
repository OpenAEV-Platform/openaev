package io.openaev.context;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
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
  @DisplayName("no scope denies every row")
  void noScopeDeniesAll() {
    assertFalse(canAccess("t1", false));
    assertFalse(canAccessNullRow(true));
  }

  @Test
  @DisplayName("empty scope denies every row")
  void emptyScopeDeniesAll() {
    setScope("");
    assertFalse(canAccess("t1", false));
    assertFalse(canAccessNullRow(true));
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
  @DisplayName("dual-scope table: a null row is denied when there is no scope")
  void dualScopeNullRowDeniedWithoutScope() {
    assertFalse(canAccessNullRow(true));
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
}
