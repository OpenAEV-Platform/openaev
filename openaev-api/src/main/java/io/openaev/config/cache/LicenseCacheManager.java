package io.openaev.config.cache;

import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class LicenseCacheManager {
  private final EnterpriseEditionService enterpriseEditionService;

  public LicenseCacheManager(EnterpriseEditionService enterpriseEditionService) {
    this.enterpriseEditionService = enterpriseEditionService;
  }

  @Cacheable("license")
  public License getEnterpriseEditionInfo() {
    return enterpriseEditionService.getEnterpriseEditionInfo();
  }

  @CacheEvict(value = "license", allEntries = true)
  public void refreshLicense() {
    enterpriseEditionService.getEnterpriseEditionInfo();
  }
}
