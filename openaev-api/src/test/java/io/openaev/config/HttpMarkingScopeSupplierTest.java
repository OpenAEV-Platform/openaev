package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TxCtx;
import io.openaev.database.model.User;
import io.openaev.service.UserService;
import java.util.List;
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

  private void givenCurrentUser() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(USER_ID);
    when(userService.currentUserOrNull()).thenReturn(user);
  }

  @Test
  @DisplayName("given a user, should resolve the clearance from the user id only")
  void given_user_should_resolveClearanceFromUserIdOnly() {
    // -- ARRANGE --
    givenCurrentUser();
    when(clearanceCache.findClearance(USER_ID, TENANT_ID)).thenReturn(MarkingCtx.none());

    // -- ACT --
    MarkingCtx clearance = supplier.clearanceFor(TxCtx.forTenant(TENANT_ID));

    // -- ASSERT --
    assertEquals(MarkingCtx.none(), clearance);
    verify(clearanceCache).findClearance(USER_ID, TENANT_ID);
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
  @DisplayName("the system clearance reads every tenant's markings at once, with no principal")
  void systemClearanceReadsTenantsAtOnceWithoutPrincipal() {
    // -- ARRANGE --
    when(clearanceCache.findSystemClearance(List.of(TENANT_ID, OTHER_TENANT_ID)))
        .thenReturn(MarkingCtx.forMarkings(List.of("tlp-green", "tlp-red", "pap-red")));

    // -- ACT --
    MarkingCtx clearance =
        supplier.systemClearanceFor(new TxCtx.Restricted(List.of(TENANT_ID, OTHER_TENANT_ID)));

    // -- ASSERT --
    assertEquals(MarkingCtx.forMarkings(List.of("tlp-green", "tlp-red", "pap-red")), clearance);
    // A scheduler is not a user, and the cached per-user entries must never serve it.
    verifyNoInteractions(userService);
    verify(clearanceCache, never()).findClearance(anyString(), anyString());
    // One read for the whole tenant set: an allTenants() job spans every tenant.
    verify(clearanceCache).findSystemClearance(List.of(TENANT_ID, OTHER_TENANT_ID));
  }

  @Test
  @DisplayName("a system clearance over tenants without definitions resolves to none()")
  void systemClearanceWithoutDefinitionsIsNone() {
    // -- ARRANGE --
    when(clearanceCache.findSystemClearance(List.of(TENANT_ID))).thenReturn(MarkingCtx.none());

    // -- ACT & ASSERT --
    assertEquals(
        MarkingCtx.none(), supplier.systemClearanceFor(new TxCtx.Restricted(List.of(TENANT_ID))));
  }
}
