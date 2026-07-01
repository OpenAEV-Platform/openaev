package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.context.TxCtx;
import io.openaev.rest.exception.TenantWriteScopeException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName(
    "TenantWriteScopeResolver — a create is attributed to a single tenant within the scope (B3)")
class TenantWriteScopeResolverTest {

  private final TenantWriteScopeResolver resolver = new TenantWriteScopeResolver();

  @Test
  @DisplayName("no supplied tenant and a single-tenant scope attributes to that tenant")
  void singleTenantScopeAttributesToIt() {
    assertEquals("t1", resolver.tenantForWrite(TxCtx.forTenant("t1"), null));
  }

  @Test
  @DisplayName("no supplied tenant and a multi-tenant scope is refused (ambiguous)")
  void multiTenantScopeWithoutSuppliedIsRefused() {
    assertThrows(
        TenantWriteScopeException.class,
        () -> resolver.tenantForWrite(TxCtx.forTenants(List.of("t1", "t2")), null));
  }

  @Test
  @DisplayName("no supplied tenant and a missing scope is refused (fail-closed)")
  void missingScopeWithoutSuppliedIsRefused() {
    assertThrows(
        TenantWriteScopeException.class, () -> resolver.tenantForWrite(TxCtx.missing(), null));
  }

  @Test
  @DisplayName("a supplied tenant within the scope is kept")
  void suppliedTenantWithinScopeIsKept() {
    assertEquals("t2", resolver.tenantForWrite(TxCtx.forTenants(List.of("t1", "t2")), "t2"));
  }

  @Test
  @DisplayName("a supplied tenant outside the scope is refused")
  void suppliedTenantOutsideScopeIsRefused() {
    assertThrows(
        TenantWriteScopeException.class,
        () -> resolver.tenantForWrite(TxCtx.forTenants(List.of("t1", "t2")), "t3"));
  }

  @Test
  @DisplayName("a supplied tenant with a missing scope is refused")
  void suppliedTenantWithMissingScopeIsRefused() {
    assertThrows(
        TenantWriteScopeException.class, () -> resolver.tenantForWrite(TxCtx.missing(), "t1"));
  }

  @Test
  @DisplayName("a blank supplied tenant falls back to deriving from the scope")
  void blankSuppliedTenantFallsBackToScope() {
    assertEquals("t1", resolver.tenantForWrite(TxCtx.forTenant("t1"), "  "));
  }
}
