package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.User;
import io.openaev.service.UserService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HttpMarkingScopeSupplier")
class HttpMarkingScopeSupplierTest {

  private static final String TENANT_ID = "tenant-1";
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
}
