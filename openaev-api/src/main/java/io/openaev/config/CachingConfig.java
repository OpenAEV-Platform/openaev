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
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("license", "global", "adminUser");

    cacheManager.setCaffeine(
        Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(1)).maximumSize(100));

    return cacheManager;
  }

  @CacheEvict(value = "adminUser", allEntries = true)
  @Scheduled(fixedRateString = "1000")
  public void emptyHotelsCache() {
    log.info("emptying admin users cache");
  }
}
