package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("DebugTenantMdcInterceptor")
class DebugTenantMdcInterceptorTest {

  private final DebugTenantMdcInterceptor interceptor =
      new DebugTenantMdcInterceptor(new DebugTenantSource());

  @AfterEach
  void cleanup() {
    MDC.clear();
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("puts the current tenant into the MDC, then removes it")
  void putsAndRemovesTenant() {
    TenantContext.setCurrentTenant("tenant-123");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenants/tenant-123/x");
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());
    assertThat(MDC.get("tenant")).isEqualTo("tenant-123");

    interceptor.afterCompletion(request, response, new Object(), null);
    assertThat(MDC.get("tenant")).isNull();
  }

  @Test
  @DisplayName("removes tenant= when the request goes async (afterConcurrentHandlingStarted)")
  void removesTenantOnAsyncDispatch() {
    TenantContext.setCurrentTenant("tenant-123");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenants/tenant-123/x");
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());
    assertThat(MDC.get("tenant")).isEqualTo("tenant-123");

    interceptor.afterConcurrentHandlingStarted(request, response, new Object());
    assertThat(MDC.get("tenant")).isNull();
  }

  @Test
  @DisplayName("records the default tenant for non-tenant-scoped requests")
  void defaultTenantWhenNotScoped() {
    // No tenant set -> TenantContext returns the default, which is the correct thing to record.
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tags");

    interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(MDC.get("tenant")).isNotBlank().isEqualTo(TenantContext.getCurrentTenant());
  }
}
