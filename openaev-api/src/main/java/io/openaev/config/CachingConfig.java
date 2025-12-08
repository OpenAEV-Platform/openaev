package io.openaev.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableCaching
@Slf4j
public class CachingConfig {

  @Bean
  public CacheManager cacheManager() {
    /**
     * Creating some cache : - license is for the EE license - global for global settings that do
     * not need to be fetched from the DB everytime we need it (like features flags) - adminUsers is
     * a low retention cache for users that are admin. This is useful when receiving a lot of calls.
     * Execution traces for instance can receive several thousands a sec and not fetching the user
     * everytime helps for the RBAC
     */
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("license", "global", "adminUsers");

    cacheManager.setCaffeine(
        Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(1)).maximumSize(100));

    return cacheManager;
  }

  /** Emptying the cache every second to avoid old data on the admin users being persisted */
  @CacheEvict(value = "adminUsers", allEntries = true)
  @Scheduled(fixedRateString = "1000")
  public void emptyAdminUsersCache() {
    log.debug("emptying admin users cache");
  }
}
