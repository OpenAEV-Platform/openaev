package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.User;
import io.openaev.service.UserService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HttpMarkingScopeSupplier")
class HttpMarkingScopeSupplierTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String OTHER_TENANT_ID = "tenant-2";
  private static final String USER_ID = "user-1";

  private MarkingClearanceCacheManager clearanceCache;
  private UserService userService;
  private HttpMarkingScopeSupplier supplier;

  @BeforeEach
  void setUp() {
    clearanceCache = mock(MarkingClearanceCacheManager.class);
    userService = mock(UserService.class);
    supplier = new HttpMarkingScopeSupplier(clearanceCache, userService);
  }

  private User userWith(Set<Capability> capabilities) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(USER_ID);
    when(user.isAdminOrBypass()).thenReturn(false);
    when(user.getCapabilities()).thenReturn(capabilities);
    when(userService.currentUserOrNull()).thenReturn(user);
    return user;
  }

  @Test
  @DisplayName("a user without any bypass capability is resolved from its own grants")
  void plainUserIsNotBypassed() {
    // -- ARRANGE --
    userWith(Set.of());
    when(clearanceCache.findClearance(USER_ID, TENANT_ID, false)).thenReturn(MarkingCtx.none());

    // -- ACT --
    MarkingCtx clearance = supplier.clearanceFor(TxCtx.forTenant(TENANT_ID));

    // -- ASSERT --
    assertEquals(MarkingCtx.none(), clearance);
    verify(clearanceCache).findClearance(USER_ID, TENANT_ID, false);
  }

  @Test
  @DisplayName("the agent service account (AGENT_RUNTIME_ACCESS) is bypassed")
  void agentRuntimeAccessIsBypassed() {
    // -- ARRANGE --
    userWith(Set.of(Capability.AGENT_RUNTIME_ACCESS));
    when(clearanceCache.findClearance(USER_ID, TENANT_ID, true))
        .thenReturn(MarkingCtx.forMarkings(Set.of("tlp-red")));

    // -- ACT --
    MarkingCtx clearance = supplier.clearanceFor(TxCtx.forTenant(TENANT_ID));

    // -- ASSERT --
    assertEquals(MarkingCtx.forMarkings(Set.of("tlp-red")), clearance);
    verify(clearanceCache).findClearance(USER_ID, TENANT_ID, true);
  }

  @Test
  @DisplayName("the OpenCTI connector user (MANAGE_STIX_BUNDLE) is bypassed")
  void manageStixBundleIsBypassed() {
    // -- ARRANGE --
    userWith(Set.of(Capability.MANAGE_STIX_BUNDLE));
    when(clearanceCache.findClearance(USER_ID, TENANT_ID, true))
        .thenReturn(MarkingCtx.forMarkings(Set.of("tlp-red")));

    // -- ACT --
    MarkingCtx clearance = supplier.clearanceFor(TxCtx.forTenant(TENANT_ID));

    // -- ASSERT --
    assertEquals(MarkingCtx.forMarkings(Set.of("tlp-red")), clearance);
    verify(clearanceCache).findClearance(USER_ID, TENANT_ID, true);
  }

  @Test
  @DisplayName("no authenticated user resolves to none()")
  void noUserResolvesToNone() {
    // -- ARRANGE --
    when(userService.currentUserOrNull()).thenReturn(null);

    // -- ACT & ASSERT --
    assertEquals(MarkingCtx.none(), supplier.clearanceFor(TxCtx.forTenant(TENANT_ID)));
  }

  @Test
  @DisplayName("the system clearance unions every tenant's markings, read fresh, with no principal")
  void systemClearanceUnionsTenantsWithoutPrincipal() {
    // -- ARRANGE --
    when(clearanceCache.findSystemClearance(TENANT_ID))
        .thenReturn(MarkingCtx.forMarkings(List.of("tlp-green", "tlp-red")));
    when(clearanceCache.findSystemClearance(OTHER_TENANT_ID))
        .thenReturn(MarkingCtx.forMarkings(List.of("pap-red")));

    // -- ACT --
    MarkingCtx clearance =
        supplier.systemClearanceFor(new TxCtx.Restricted(List.of(TENANT_ID, OTHER_TENANT_ID)));

    // -- ASSERT --
    assertEquals(MarkingCtx.forMarkings(List.of("tlp-green", "tlp-red", "pap-red")), clearance);
    // A scheduler is not a user, and the cached per-user entries must never serve it.
    verifyNoInteractions(userService);
    verify(clearanceCache, never()).findClearance(anyString(), anyString(), anyBoolean());
  }

  @Test
  @DisplayName("a system clearance over tenants without definitions resolves to none()")
  void systemClearanceWithoutDefinitionsIsNone() {
    // -- ARRANGE --
    when(clearanceCache.findSystemClearance(TENANT_ID)).thenReturn(MarkingCtx.none());

    // -- ACT & ASSERT --
    assertEquals(
        MarkingCtx.none(), supplier.systemClearanceFor(new TxCtx.Restricted(List.of(TENANT_ID))));
  }
}
