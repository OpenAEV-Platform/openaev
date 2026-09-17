package io.openaev.service.tenants;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The after-commit deferral itself is centralized in and tested by {@link
 * TenantMembershipCacheManager} (see {@code TenantMembershipCacheManagerTest}), so every
 * membership-mutating caller — including this service — only needs to be verified to route through
 * {@link TenantMembershipCacheManager#evict(String, String)}.
 */
@ExtendWith(MockitoExtension.class)
class TenantUserServiceCacheEvictionTest {

  @Mock private UserService userService;
  @Mock private UserRepository userRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private TenantMembershipCacheManager tenantMembershipCacheManager;

  private TenantUserService newService() {
    return new TenantUserService(
        userService, userRepository, tenantRepository, tenantMembershipCacheManager);
  }

  @Test
  @DisplayName("given_attachToTenant_should_routeEvictionThroughCacheManager")
  void given_attachToTenant_should_routeEvictionThroughCacheManager() {
    // -- ARRANGE --
    TenantUserService tenantUserService = newService();

    // -- ACT --
    tenantUserService.attachToTenant("user-1", "tenant-1");

    // -- ASSERT --
    verify(tenantRepository).addUserToTenant("user-1", "tenant-1");
    verify(tenantMembershipCacheManager).evict("user-1", "tenant-1");
  }

  @Test
  @DisplayName("given_detach_should_routeEvictionThroughCacheManager")
  void given_detach_should_routeEvictionThroughCacheManager() {
    // -- ARRANGE --
    TenantUserService tenantUserService = newService();
    TenantContext.setCurrentTenant("tenant-1");
    User user = new User();
    user.setId("user-1");
    user.setEmail("user-1@test.invalid");
    when(userService.user(anyString())).thenReturn(user);

    try {
      // -- ACT --
      tenantUserService.detach("user-1");

      // -- ASSERT --
      verify(tenantRepository).removeUserFromTenant("user-1", "tenant-1");
      verify(tenantMembershipCacheManager).evict("user-1", "tenant-1");
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
