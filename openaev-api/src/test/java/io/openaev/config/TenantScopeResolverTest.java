package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.rest.exception.TenantAccessDeniedException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantScopeResolver — rights are the boundary, the request is only a selector (B2)")
class TenantScopeResolverTest {

  private final TenantScopeResolver resolver = new TenantScopeResolver();

  @Test
  @DisplayName("a selector within the user's rights becomes the scope")
  void selectorWithinRightsIsKept() {
    assertEquals("t1", resolver.resolve(Set.of("t1"), Set.of("t1", "t2")).toGuc());
  }

  @Test
  @DisplayName("a selector outside the user's rights is refused")
  void selectorOutsideRightsIsRejected() {
    assertThrows(
        TenantAccessDeniedException.class,
        () -> resolver.resolve(Set.of("t3"), Set.of("t1", "t2")));
  }

  @Test
  @DisplayName("a selector partly outside the rights is refused (no silent narrowing)")
  void partiallyOutsideRightsIsRejected() {
    assertThrows(
        TenantAccessDeniedException.class,
        () -> resolver.resolve(Set.of("t1", "t3"), Set.of("t1", "t2")));
  }

  @Test
  @DisplayName("no selector means the full set of authorized tenants")
  void noSelectorYieldsFullAuthorized() {
    assertEquals("t1,t2", resolver.resolve(Set.of(), Set.of("t2", "t1")).toGuc());
  }

  @Test
  @DisplayName("a multi-tenant selector subset is honored as-is, never widened to all rights")
  void multiTenantSelectorSubsetIsKept() {
    assertEquals("t1,t2", resolver.resolve(Set.of("t2", "t1"), Set.of("t1", "t2", "t3")).toGuc());
  }

  @Test
  @DisplayName("no selector and no rights is the missing scope (fail-closed)")
  void noSelectorNoRightsIsMissing() {
    assertEquals("", resolver.resolve(Set.of(), Set.of()).toGuc());
  }

  @Test
  @DisplayName("a selector when the user has no rights is refused")
  void selectorWithNoRightsIsRejected() {
    assertThrows(TenantAccessDeniedException.class, () -> resolver.resolve(Set.of("t1"), Set.of()));
  }

  @Test
  @DisplayName("the resolved scope is deterministic regardless of selector order")
  void scopeOrderIsDeterministic() {
    assertEquals(
        "t1,t2,t3", resolver.resolve(Set.of("t3", "t1", "t2"), Set.of("t1", "t2", "t3")).toGuc());
  }

  @Test
  @DisplayName("a null selector is rejected (explicit contract)")
  void nullSelectorIsRejected() {
    assertThrows(NullPointerException.class, () -> resolver.resolve(null, Set.of("t1")));
  }

  @Test
  @DisplayName("null rights are rejected, even with an empty selector (no silent missing scope)")
  void nullAuthorizedIsRejected() {
    assertThrows(NullPointerException.class, () -> resolver.resolve(Set.of(), null));
  }
}
