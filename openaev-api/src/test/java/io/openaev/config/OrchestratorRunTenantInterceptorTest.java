package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantContext;
import io.openaev.security.token.XtmJwksExtractor;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Pins the v1 {@link TenantContext} bridge for the orchestrator callback route. The callbacks reach
 * still-v1-filtered tables (workflows, injects, exercises, teams, assets, findings) on the legacy
 * non-prefixed route that {@code TenantInterceptor} never covers, so without this bridge a run
 * owned by a non-default tenant silently reads/writes NOTHING through the v1 filter (empty
 * attack-path state, no workflow mirror, no team enablement). The gating must match {@link
 * TxCtxArgumentResolver} exactly (verified cross-platform service identity, non-prefixed route,
 * {@code runId} present) or v1 and v2 could derive different tenants; and the ThreadLocal must be
 * cleared on every exit path or a pooled thread leaks one run's tenant into an unrelated request.
 */
@DisplayName("OrchestratorRunTenantInterceptor bridges the v1 TenantContext for run callbacks")
class OrchestratorRunTenantInterceptorTest {

  private static final String RUN_ID = "run-123";
  private static final String RUN_TENANT = "tenant-non-default";

  private final AutonomousRunTenantLocator locator = mock(AutonomousRunTenantLocator.class);
  private final OrchestratorRunTenantInterceptor interceptor =
      new OrchestratorRunTenantInterceptor(locator);

  @BeforeEach
  @AfterEach
  void resetContext() {
    // Isolate from any tenant a sibling test left on this (pooled) thread, both before and after.
    TenantContext.clearCurrentTenant();
  }

  private static MockHttpServletRequest request(boolean crossPlatform, Map<String, String> vars) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (crossPlatform) {
      request.setAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE);
    }
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, vars);
    return request;
  }

  @Test
  @DisplayName("service caller on the non-prefixed route with a runId gets the run's v1 tenant")
  void setsRunTenantForServiceCallerOnNonPrefixedRoute() {
    when(locator.findRunTenant(RUN_ID)).thenReturn(Optional.of(RUN_TENANT));

    interceptor.preHandle(
        request(true, Map.of("runId", RUN_ID)), new MockHttpServletResponse(), new Object());

    assertThat(TenantContext.hasCurrentTenant()).isTrue();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(RUN_TENANT);
  }

  @Test
  @DisplayName("afterCompletion clears the ThreadLocal so a pooled thread never carries the tenant")
  void afterCompletionClears() {
    TenantContext.setCurrentTenant(RUN_TENANT);

    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
  }

  @Test
  @DisplayName("afterConcurrentHandlingStarted clears too (the async dispatch exit path)")
  void afterConcurrentHandlingStartedClears() {
    TenantContext.setCurrentTenant(RUN_TENANT);

    interceptor.afterConcurrentHandlingStarted(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
  }

  @Test
  @DisplayName("a non-service caller is never given the run's tenant (no cross-tenant reach)")
  void ignoresNonServiceCaller() {
    interceptor.preHandle(
        request(false, Map.of("runId", RUN_ID)), new MockHttpServletResponse(), new Object());

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    verifyNoInteractions(locator);
  }

  @Test
  @DisplayName("the prefixed operator route (tenantId present) is left to TenantInterceptor")
  void ignoresPrefixedRoute() {
    interceptor.preHandle(
        request(true, Map.of("tenantId", "tenant-x", "runId", RUN_ID)),
        new MockHttpServletResponse(),
        new Object());

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    verifyNoInteractions(locator);
  }

  @Test
  @DisplayName("a callback without a runId path variable sets nothing (fail-safe)")
  void ignoresMissingRunId() {
    interceptor.preHandle(request(true, Map.of()), new MockHttpServletResponse(), new Object());

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    verifyNoInteractions(locator);
  }

  @Test
  @DisplayName("an unknown / soft-deleted run resolves empty and sets nothing (fail-closed)")
  void ignoresUnknownRun() {
    when(locator.findRunTenant(RUN_ID)).thenReturn(Optional.empty());

    interceptor.preHandle(
        request(true, Map.of("runId", RUN_ID)), new MockHttpServletResponse(), new Object());

    assertThat(TenantContext.hasCurrentTenant()).isFalse();
  }
}
