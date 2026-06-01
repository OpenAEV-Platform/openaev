package io.openaev.config.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.openaev.config.CachingConfig;
import io.openaev.database.repository.TenantRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests for {@link TenantMembershipCacheManager} verifying that caching and eviction work correctly
 * for tenant membership checks.
 */
@SpringBootTest(classes = {CachingConfig.class, TenantMembershipCacheManager.class})
@DisplayName("TenantMembershipCacheManager")
class TenantMembershipCacheManagerTest {

  private static final String USER_ID = "user-1";
  private static final String TENANT_ID = "tenant-1";

  @Autowired private TenantMembershipCacheManager tenantMembershipCacheManager;
  @Autowired private CacheManager cacheManager;
  @MockitoBean private TenantRepository tenantRepository;

  @BeforeEach
  void setUp() {
    // Arrange — clear cache before each test
    var cache = cacheManager.getCache("tenantMembership");
    if (cache != null) {
      cache.clear();
    }
    reset(tenantRepository);
  }

  @Nested
  @DisplayName("existsByUserIdAndTenantId")
  class ExistsByUserIdAndTenantId {

    @Test
    @DisplayName("given_cached_result_should_not_call_repository_on_second_call")
    void given_cached_result_should_not_call_repository_on_second_call() {
      // Arrange
      when(tenantRepository.existsByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(true);

      // Act
      boolean firstResult =
          tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      boolean secondResult =
          tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);

      // Assert
      assertThat(firstResult).isTrue();
      assertThat(secondResult).isTrue();
      verify(tenantRepository, times(1)).existsByUserIdAndTenantId(USER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("given_false_result_should_also_cache")
    void given_false_result_should_also_cache() {
      // Arrange
      when(tenantRepository.existsByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(false);

      // Act
      boolean firstResult =
          tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      boolean secondResult =
          tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);

      // Assert
      assertThat(firstResult).isFalse();
      assertThat(secondResult).isFalse();
      verify(tenantRepository, times(1)).existsByUserIdAndTenantId(USER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("given_different_keys_should_call_repository_for_each")
    void given_different_keys_should_call_repository_for_each() {
      // Arrange
      String otherTenantId = "tenant-2";
      when(tenantRepository.existsByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(true);
      when(tenantRepository.existsByUserIdAndTenantId(USER_ID, otherTenantId)).thenReturn(false);

      // Act
      boolean result1 = tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      boolean result2 =
          tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, otherTenantId);

      // Assert
      assertThat(result1).isTrue();
      assertThat(result2).isFalse();
      verify(tenantRepository).existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      verify(tenantRepository).existsByUserIdAndTenantId(USER_ID, otherTenantId);
    }
  }

  @Nested
  @DisplayName("evict")
  class Evict {

    @Test
    @DisplayName("given_cached_result_should_call_repository_again_after_eviction")
    void given_cached_result_should_call_repository_again_after_eviction() {
      // Arrange
      when(tenantRepository.existsByUserIdAndTenantId(USER_ID, TENANT_ID))
          .thenReturn(true)
          .thenReturn(false);

      // Act — populate cache
      boolean firstResult =
          tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);

      // Act — evict
      tenantMembershipCacheManager.evict(USER_ID, TENANT_ID);

      // Act — should hit repository again
      boolean secondResult =
          tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);

      // Assert
      assertThat(firstResult).isTrue();
      assertThat(secondResult).isFalse();
      verify(tenantRepository, times(2)).existsByUserIdAndTenantId(USER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("given_eviction_for_one_key_should_not_affect_other_keys")
    void given_eviction_for_one_key_should_not_affect_other_keys() {
      // Arrange
      String otherTenantId = "tenant-2";
      when(tenantRepository.existsByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(true);
      when(tenantRepository.existsByUserIdAndTenantId(USER_ID, otherTenantId)).thenReturn(true);

      // Act — populate both cache entries
      tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, otherTenantId);

      // Act — evict only one
      tenantMembershipCacheManager.evict(USER_ID, TENANT_ID);

      // Act — access both again
      tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, otherTenantId);

      // Assert — evicted key hit DB twice, other key only once
      verify(tenantRepository, times(2)).existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      verify(tenantRepository, times(1)).existsByUserIdAndTenantId(USER_ID, otherTenantId);
    }
  }
}
