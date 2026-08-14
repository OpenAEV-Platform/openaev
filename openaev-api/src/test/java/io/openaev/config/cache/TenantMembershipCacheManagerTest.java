package io.openaev.config.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.openaev.config.CachingConfig;
import io.openaev.database.repository.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
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
  @MockitoBean private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    // Arrange — clear cache before each test
    var membershipCache = cacheManager.getCache("tenantMembership");
    if (membershipCache != null) {
      membershipCache.clear();
    }
    var userTenantIdsCache = cacheManager.getCache("userTenantIds");
    if (userTenantIdsCache != null) {
      userTenantIdsCache.clear();
    }
    reset(tenantRepository, jdbcTemplate);
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

    @Test
    @DisplayName("given_cached_tenant_ids_should_call_jdbc_again_after_eviction")
    void given_cached_tenant_ids_should_call_jdbc_again_after_eviction() {
      // Arrange
      when(jdbcTemplate.queryForList(
              TenantMembershipCacheManager.USER_TENANT_IDS_SQL, String.class, USER_ID))
          .thenReturn(List.of(TENANT_ID));

      // Act — populate cache
      tenantMembershipCacheManager.findTenantIdsByUserId(USER_ID);

      // Act — evict membership also busts userTenantIds
      tenantMembershipCacheManager.evict(USER_ID, TENANT_ID);

      // Act — should hit jdbc again
      tenantMembershipCacheManager.findTenantIdsByUserId(USER_ID);

      // Assert
      verify(jdbcTemplate, times(2))
          .queryForList(TenantMembershipCacheManager.USER_TENANT_IDS_SQL, String.class, USER_ID);
    }
  }

  @Nested
  @DisplayName("findTenantIdsByUserId")
  class FindTenantIdsByUserId {

    @Test
    @DisplayName("given_cached_result_should_not_call_jdbc_on_second_call")
    void given_cached_result_should_not_call_jdbc_on_second_call() {
      // Arrange
      when(jdbcTemplate.queryForList(
              TenantMembershipCacheManager.USER_TENANT_IDS_SQL, String.class, USER_ID))
          .thenReturn(List.of(TENANT_ID));

      // Act
      List<String> firstResult = tenantMembershipCacheManager.findTenantIdsByUserId(USER_ID);
      List<String> secondResult = tenantMembershipCacheManager.findTenantIdsByUserId(USER_ID);

      // Assert
      assertThat(firstResult).containsExactly(TENANT_ID);
      assertThat(secondResult).containsExactly(TENANT_ID);
      verify(jdbcTemplate, times(1))
          .queryForList(TenantMembershipCacheManager.USER_TENANT_IDS_SQL, String.class, USER_ID);
    }
  }

  @Nested
  @DisplayName("evictForUser")
  class EvictForUser {

    @Test
    @DisplayName("given_cached_membership_and_ids_should_call_backends_again_after_evictForUser")
    void given_cached_membership_and_ids_should_call_backends_again_after_evictForUser() {
      // Arrange
      when(tenantRepository.existsByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(true);
      when(jdbcTemplate.queryForList(
              TenantMembershipCacheManager.USER_TENANT_IDS_SQL, String.class, USER_ID))
          .thenReturn(List.of(TENANT_ID));

      tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      tenantMembershipCacheManager.findTenantIdsByUserId(USER_ID);

      // Act - must bust both caches without relying on self-invocation of evict()
      tenantMembershipCacheManager.evictForUser(USER_ID, List.of(TENANT_ID));

      tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      tenantMembershipCacheManager.findTenantIdsByUserId(USER_ID);

      // Assert
      verify(tenantRepository, times(2)).existsByUserIdAndTenantId(USER_ID, TENANT_ID);
      verify(jdbcTemplate, times(2))
          .queryForList(TenantMembershipCacheManager.USER_TENANT_IDS_SQL, String.class, USER_ID);
    }
  }
}
