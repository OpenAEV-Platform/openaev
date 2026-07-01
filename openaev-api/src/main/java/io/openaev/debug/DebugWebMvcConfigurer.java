package io.openaev.debug;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the debug-only interceptors. Created only when debug mode is on. */
public class DebugWebMvcConfigurer implements WebMvcConfigurer {

  private final DebugTenantMdcInterceptor tenantMdcInterceptor;

  public DebugWebMvcConfigurer(DebugTenantMdcInterceptor tenantMdcInterceptor) {
    this.tenantMdcInterceptor = tenantMdcInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // After the platform tenant interceptor.
    registry.addInterceptor(tenantMdcInterceptor).order(Ordered.LOWEST_PRECEDENCE);
  }
}
