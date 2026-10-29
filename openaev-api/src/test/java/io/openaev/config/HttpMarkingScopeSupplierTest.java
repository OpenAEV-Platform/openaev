package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.User;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HttpMarkingScopeSupplier")
class HttpMarkingScopeSupplierTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String USER_ID = "user-1";

  private MarkingClearanceCacheManager clearanceCache;
  private UserService userService;
  private PermissionService permissionService;
  private HttpMarkingScopeSupplier supplier;

  @BeforeEach
  void setUp() {
    clearanceCache = mock(MarkingClearanceCacheManager.class);
    userService = mock(UserService.class);
    permissionService = mock(PermissionService.class);
    // The user mock answers the same in every tenant: the decision runs as the supplier hands it.
    when(permissionService.holdsInTenant(anyString(), any()))
        .thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(1)).getAsBoolean());
    supplier = new HttpMarkingScopeSupplier(clearanceCache, userService);
    supplier.setPermissionService(permissionService);
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
  @DisplayName("the clearance capability counts in the tenant whose clearance is computed")
  void clearanceCapabilityIsEvaluatedPerTenant() {
    // -- ARRANGE --
    userWith(Set.of(Capability.MANAGE_STIX_BUNDLE));
    when(permissionService.holdsInTenant(eq("tenant-2"), any())).thenReturn(false);
    when(clearanceCache.findClearance(USER_ID, TENANT_ID, true))
        .thenReturn(MarkingCtx.forMarkings(Set.of("tlp-red")));
    when(clearanceCache.findClearance(USER_ID, "tenant-2", false)).thenReturn(MarkingCtx.none());

    // -- ACT --
    MarkingCtx clearance = supplier.clearanceFor(TxCtx.forTenants(Set.of(TENANT_ID, "tenant-2")));

    // -- ASSERT --
    assertEquals(MarkingCtx.forMarkings(Set.of("tlp-red")), clearance);
    verify(clearanceCache).findClearance(USER_ID, TENANT_ID, true);
    verify(clearanceCache).findClearance(USER_ID, "tenant-2", false);
  }
}
