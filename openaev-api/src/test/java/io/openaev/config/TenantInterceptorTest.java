package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@DisplayName("TenantInterceptor")
class TenantInterceptorTest {

  private final TenantMembershipCacheManager tenantMembershipCacheManager =
      mock(TenantMembershipCacheManager.class);
  private final TenantUriUtils tenantUriUtils = new TenantUriUtils();
  private final TenantInterceptor interceptor = new TenantInterceptor(tenantMembershipCacheManager, tenantUriUtils);

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
  }

  private MockHttpServletRequest tenantScopedRequest(String tenantId) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/tenants/" + tenantId + "/x");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("tenantId", tenantId));
    return request;
  }

  @Test
  @DisplayName("sets the tenant from the path variable and clears it on afterCompletion")
  void setsAndClearsTenant() {
    MockHttpServletRequest request = tenantScopedRequest("tenant-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());
    assertThat(TenantContext.getCurrentTenant()).isEqualTo("tenant-123");

    interceptor.afterCompletion(request, response, new Object(), null);
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
  }

  @Test
  @DisplayName("clears the tenant when the request goes async, so the next request is not stale")
  void clearsTenantOnAsyncDispatch() {
    // Async requests (e.g. StreamingResponseBody) do not call afterCompletion on the initial
    // dispatch: without afterConcurrentHandlingStarted the pooled servlet thread would keep the
    // previous request's tenant in the TenantContext thread-local.
    MockHttpServletRequest request = tenantScopedRequest("tenant-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());
    assertThat(TenantContext.getCurrentTenant()).isEqualTo("tenant-123");

    interceptor.afterConcurrentHandlingStarted(request, response, new Object());
    assertThat(TenantContext.hasCurrentTenant()).isFalse();

    // The next non-tenant-scoped request on this (pooled) thread resolves the default tenant,
    // not the stale one.
    MockHttpServletRequest nextRequest = new MockHttpServletRequest("GET", "/api/tags");
    interceptor.preHandle(nextRequest, new MockHttpServletResponse(), new Object());
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
