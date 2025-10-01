package io.openaev.config.cache;

import io.openaev.ee.Ee;
import io.openaev.ee.License;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class LicenseCacheManager {
  private final Ee eeService;

  public LicenseCacheManager(Ee eeService) {
    this.eeService = eeService;
  }

  @Cacheable("license")
  public License getEnterpriseEditionInfo() {
    return eeService.getEnterpriseEditionInfo();
  }

  @CacheEvict(value = "license", allEntries = true)
  public void refreshLicense() {
    eeService.getEnterpriseEditionInfo();
  }
}
