package io.openaev.config.cache;

import org.springframework.context.ApplicationEvent;

/** Published by {@link LicenseCacheManager} after the license cache is evicted and refreshed. */
public class LicenseRefreshedEvent extends ApplicationEvent {

  public LicenseRefreshedEvent(Object source) {
    super(source);
  }
}
