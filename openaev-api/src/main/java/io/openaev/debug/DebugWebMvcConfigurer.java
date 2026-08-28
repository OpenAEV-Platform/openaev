package io.openaev.debug;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the debug-only interceptors. Created only when debug mode is on. */
public class DebugWebMvcConfigurer implements WebMvcConfigurer {

  private final DebugTenantMdcInterceptor tenantMdcInterceptor;
  private final DebugUserMdcInterceptor userMdcInterceptor;

  public DebugWebMvcConfigurer(
      DebugTenantMdcInterceptor tenantMdcInterceptor, DebugUserMdcInterceptor userMdcInterceptor) {
    this.tenantMdcInterceptor = tenantMdcInterceptor;
    this.userMdcInterceptor = userMdcInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // Lowest precedence: after the platform tenant interceptor and after authentication.
    registry.addInterceptor(tenantMdcInterceptor).order(Ordered.LOWEST_PRECEDENCE);
    registry.addInterceptor(userMdcInterceptor).order(Ordered.LOWEST_PRECEDENCE);
  }
}
