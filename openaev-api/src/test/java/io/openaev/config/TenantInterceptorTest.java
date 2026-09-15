package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.aop.AccessControl;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exception.TenantAccessDeniedException;
import io.openaev.security.token.XtmJwksExtractor;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@DisplayName("TenantInterceptor")
class TenantInterceptorTest {

  private static final String USER_ID = "user-1";
  private static final String HEADER_TENANT = "tenant-header";

  private final TenantMembershipCacheManager tenantMembershipCacheManager =
      mock(TenantMembershipCacheManager.class);
  private final TenantUriUtils tenantUriUtils = new TenantUriUtils();
  private final TenantInterceptor interceptor =
      new TenantInterceptor(tenantMembershipCacheManager, tenantUriUtils);

  @BeforeEach
  @AfterEach
  void cleanup() {
    // Both before and after: isolate from any tenant a sibling test (in this class or another in
    // the same fork) may have left on this pooled thread, so an assertion on hasCurrentTenant()
    // reads this test's own effect, not a leaked one.
    TenantContext.clearCurrentTenant();
    SecurityContextHolder.clearContext();
  }

  private MockHttpServletRequest tenantScopedRequest(String tenantId) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/tenants/" + tenantId + "/x");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("tenantId", tenantId));
    return request;
  }

  // A regular (non-prefixed) request carrying exactly one X-Tenant-Ids id, no path tenant.
  private MockHttpServletRequest headerRequest(String tenantId) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
    request.addHeader(TxCtxArgumentResolver.TENANT_IDS_HEADER, tenantId);
    return request;
  }

  // Authenticates the SecurityContext as a non-anonymous principal with the given id.
  private void authenticateAs(String userId) {
    OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
    when(principal.getId()).thenReturn(userId);
    Authentication authentication = mock(Authentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getPrincipal()).thenReturn(principal);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  // A handler that manages the tenant resource itself (@AccessControl(resourceType = TENANT)).
  private static HandlerMethod tenantResourceHandler() throws NoSuchMethodException {
    return new HandlerMethod(
        new TenantManagedController(), TenantManagedController.class.getMethod("manage"));
  }

  static class TenantManagedController {
    @AccessControl(resourceType = ResourceType.TENANT)
    public void manage() {}
  }

  @Test
  @DisplayName(
      "a single header id on the regular route becomes the ambient tenant and marks Vary, even on a"
          + " handler with no TxCtx")
  void singleHeaderIdOnRegularRouteBecomesAmbientAndMarksVary() {
    authenticateAs(USER_ID);
    when(tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, HEADER_TENANT))
        .thenReturn(true);
    MockHttpServletRequest request = headerRequest(HEADER_TENANT);
    MockHttpServletResponse response = new MockHttpServletResponse();

    // A plain Object handler is deliberately used: it takes no TxCtx, so the Vary header can only
    // come from the interceptor, never from TxCtxArgumentResolver.
    interceptor.preHandle(request, response, new Object());

    assertThat(TenantContext.getCurrentTenant()).isEqualTo(HEADER_TENANT);
    assertThat(response.getHeaders(HttpHeaders.VARY))
        .contains(TxCtxArgumentResolver.TENANT_IDS_HEADER);
  }

  @Test
  @DisplayName(
      "a tenant-management handler on the regular route does not adopt the header (the tenant is"
          + " addressed by the path, not the header)")
  void tenantManagementHandlerOnRegularRouteDoesNotAdoptHeader() throws Exception {
    authenticateAs(USER_ID);
    when(tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, HEADER_TENANT))
        .thenReturn(true);
    MockHttpServletRequest request = headerRequest(HEADER_TENANT);
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, tenantResourceHandler());

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    assertThat(response.getHeaders(HttpHeaders.VARY)).isEmpty();
  }

  @Test
  @DisplayName(
      "the verified cross-platform service identity is never given the header tenant (its scope is"
          + " run-authoritative), even when it names a tenant it belongs to")
  void serviceIdentityOnRegularRouteDoesNotAdoptHeader() {
    authenticateAs(USER_ID);
    when(tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, HEADER_TENANT))
        .thenReturn(true);
    MockHttpServletRequest request = headerRequest(HEADER_TENANT);
    request.setAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE);
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    assertThat(response.getHeaders(HttpHeaders.VARY)).isEmpty();
  }

  @Test
  @DisplayName(
      "a single header id the caller is not a member of is refused with 403 (like the path)")
  void singleHeaderIdForNonMemberIsRefused() {
    authenticateAs(USER_ID);
    when(tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, HEADER_TENANT))
        .thenReturn(false);
    MockHttpServletRequest request = headerRequest(HEADER_TENANT);
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
        .isInstanceOf(TenantAccessDeniedException.class);
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
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
