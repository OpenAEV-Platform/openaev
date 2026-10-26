package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.service.UserService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    when(user.isAdmin()).thenReturn(false);
    when(user.getCapabilitiesIn(TENANT_ID)).thenReturn(capabilities);
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

  /**
   * One request can read several tenants, and the user's rights differ per tenant: the bypass is
   * decided per tenant, from the groups of that tenant (plus platform groups), never from the
   * ambient tenant. Real users and groups here, since the rule is computed from the groups'
   * tenants.
   */
  @Nested
  @DisplayName("Bypass decided per tenant")
  class BypassPerTenant {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private Group group(String tenantId, Capability... capabilities) {
      Role role = new Role();
      role.setCapabilities(new HashSet<>(Set.of(capabilities)));
      Group group = new Group();
      group.setTenant(tenantId == null ? null : new Tenant(tenantId));
      group.setRoles(new ArrayList<>(List.of(role)));
      return group;
    }

    private void currentUserIn(Group... groups) {
      User user = new User();
      user.setId(USER_ID);
      user.setGroups(new ArrayList<>(List.of(groups)));
      when(userService.currentUserOrNull()).thenReturn(user);
    }

    @Test
    @DisplayName("given BYPASS in one of two groups of the same tenant, should bypass that tenant")
    void given_bypassInOneGroupOfTheTenant_should_bypassTheTenant() {
      // -- ARRANGE --
      currentUserIn(group(TENANT_A, Capability.BYPASS), group(TENANT_A, Capability.ACCESS_ASSETS));

      // -- ACT --
      supplier.clearanceFor(TxCtx.forTenant(TENANT_A));

      // -- ASSERT --
      verify(clearanceCache).findClearance(USER_ID, TENANT_A, true);
    }

    @Test
    @DisplayName("given BYPASS in tenant A only, should not bypass tenant B")
    void given_bypassInOneTenant_should_notBypassTheOther() {
      // -- ARRANGE --
      currentUserIn(group(TENANT_A, Capability.BYPASS), group(TENANT_B, Capability.ACCESS_ASSETS));

      // -- ACT --
      supplier.clearanceFor(TxCtx.forTenants(List.of(TENANT_A, TENANT_B)));

      // -- ASSERT --
      verify(clearanceCache).findClearance(USER_ID, TENANT_A, true);
      verify(clearanceCache).findClearance(USER_ID, TENANT_B, false);
    }

    @Test
    @DisplayName("given AGENT_RUNTIME_ACCESS in tenant A only, should not bypass tenant B")
    void given_agentRuntimeAccessInOneTenant_should_notBypassTheOther() {
      // -- ARRANGE --
      currentUserIn(
          group(TENANT_A, Capability.AGENT_RUNTIME_ACCESS),
          group(TENANT_B, Capability.ACCESS_ASSETS));

      // -- ACT --
      supplier.clearanceFor(TxCtx.forTenants(List.of(TENANT_A, TENANT_B)));

      // -- ASSERT --
      verify(clearanceCache).findClearance(USER_ID, TENANT_A, true);
      verify(clearanceCache).findClearance(USER_ID, TENANT_B, false);
    }

    @Test
    @DisplayName("given a platform BYPASS, should not bypass markings (unchanged)")
    void given_platformBypass_should_notBypassMarkings() {
      // -- ARRANGE --
      // A platform BYPASS expands into platform-scoped capabilities only, not into the
      // tenant-scoped AGENT_RUNTIME_ACCESS / MANAGE_STIX_BUNDLE: it never bypassed markings.
      currentUserIn(group(null, Capability.BYPASS), group(TENANT_B, Capability.ACCESS_ASSETS));

      // -- ACT --
      supplier.clearanceFor(TxCtx.forTenants(List.of(TENANT_A, TENANT_B)));

      // -- ASSERT --
      verify(clearanceCache).findClearance(USER_ID, TENANT_A, false);
      verify(clearanceCache).findClearance(USER_ID, TENANT_B, false);
    }

    @Test
    @DisplayName("given a platform admin, should bypass every tenant")
    void given_platformAdmin_should_bypassEveryTenant() {
      // -- ARRANGE --
      User admin = new User();
      admin.setId(USER_ID);
      admin.setAdmin(true);
      when(userService.currentUserOrNull()).thenReturn(admin);

      // -- ACT --
      supplier.clearanceFor(TxCtx.forTenants(List.of(TENANT_A, TENANT_B)));

      // -- ASSERT --
      verify(clearanceCache).findClearance(USER_ID, TENANT_A, true);
      verify(clearanceCache).findClearance(USER_ID, TENANT_B, true);
    }
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
