package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.aop.AccessControl;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
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

  // A public phishing tracking request: a {tenantId} path variable on a route that is NOT
  // tenant-prefixed (/api/phishing/tracking/{tenantId}/...), so the id there is a handler
  // parameter,
  // not a request tenant for the interceptor.
  private MockHttpServletRequest phishingTrackingRequest(String tenantId) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/phishing/tracking/" + tenantId + "/o/some-token");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("tenantId", tenantId));
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

  // A handler whose scope is derived from the parent run (@RunTenantScope), i.e. an orchestrator
  // callback: the verified service identity is left on the run-derived scope for it, exactly as
  // TxCtxArgumentResolver diverts such a parameter.
  private static HandlerMethod runScopedHandler() throws NoSuchMethodException {
    return new HandlerMethod(
        new RunScopedController(), RunScopedController.class.getMethod("callback", TxCtx.class));
  }

  static class RunScopedController {
    public void callback(@RunTenantScope TxCtx ctx) {}
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
      "the verified cross-platform service identity on a run-scoped handler is not given the header"
          + " tenant (its scope is run-authoritative), even naming a tenant it belongs to")
  void serviceIdentityOnRunScopedHandlerDoesNotAdoptHeader() throws Exception {
    authenticateAs(USER_ID);
    MockHttpServletRequest request = headerRequest(HEADER_TENANT);
    request.setAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE);
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, runScopedHandler());

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    assertThat(response.getHeaders(HttpHeaders.VARY)).isEmpty();
  }

  @Test
  @DisplayName(
      "the verified cross-platform service identity on a plain handler adopts the single header id"
          + " it belongs to, mirroring the resolver's caller-authorized resolution")
  void serviceIdentityOnPlainHandlerAdoptsHeaderTenant() {
    authenticateAs(USER_ID);
    when(tenantMembershipCacheManager.existsByUserIdAndTenantId(USER_ID, HEADER_TENANT))
        .thenReturn(true);
    MockHttpServletRequest request = headerRequest(HEADER_TENANT);
    request.setAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE);
    MockHttpServletResponse response = new MockHttpServletResponse();

    // A plain Object handler has no @RunTenantScope parameter, so the service identity is not on a
    // run-authoritative callback: its TxCtx would be resolved from the header through the
    // caller-authorized path, and the ambient tenant must follow it.
    interceptor.preHandle(request, response, new Object());

    assertThat(TenantContext.getCurrentTenant()).isEqualTo(HEADER_TENANT);
    assertThat(response.getHeaders(HttpHeaders.VARY))
        .contains(TxCtxArgumentResolver.TENANT_IDS_HEADER);
  }

  @Test
  @DisplayName(
      "an authenticated non-member on a public phishing tracking route is not refused by the"
          + " interceptor: the {tenantId} there is a route parameter, not a request tenant")
  void phishingTrackingRouteWithNonMemberIsNotRefused() {
    authenticateAs(USER_ID);
    // Deliberately no membership stub: the caller is NOT a member of the path tenant, so treating
    // that {tenantId} as a request tenant (as the interceptor did before covering /api/**) would
    // 403 this token-authenticated public request before the handler's own token check.
    MockHttpServletRequest request = phishingTrackingRequest("phishing-tenant");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatCode(() -> interceptor.preHandle(request, response, new Object()))
        .doesNotThrowAnyException();
    // No X-Tenant-Ids header on the tracking request, so the header branch adopts nothing either.
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    assertThat(response.getHeaders(HttpHeaders.VARY)).isEmpty();
  }

  @Test
  @DisplayName(
      "a single header id with no authenticated caller is not adopted and does not mark Vary"
          + " (anonymous)")
  void singleHeaderIdWithoutAuthenticationIsNotAdopted() {
    // No authenticateAs: the SecurityContext carries no authentication, so an anonymous caller must
    // not select a tenant through the header (it has no memberships to validate against).
    MockHttpServletRequest request = headerRequest(HEADER_TENANT);
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
