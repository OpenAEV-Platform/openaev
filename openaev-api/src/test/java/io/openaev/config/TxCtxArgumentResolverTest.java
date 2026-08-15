package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exception.TenantAccessDeniedException;
import io.openaev.rest.exception.TenantSelectorRequiredException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Unit coverage for the no-selector fallback on selector-requiring endpoints. A selector is never
 * mandatory: tenant-unaware API clients (collectors, injectors, plain scripts) must keep working.
 * The Community Edition case (a caller authorized on a single tenant) cannot be reached by the
 * MockMvc integration test, whose seed always creates extra tenant memberships, so it is proven
 * here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TxCtx no-selector fallback on selector-requiring endpoints")
class TxCtxArgumentResolverTest {

  private static final String USER_ID = "user-id";

  @Mock private TenantMembershipCacheManager membershipCache;
  @Mock private AutonomousRunTenantLocator runTenantLocator;
  @Mock private MethodParameter parameter;
  @Mock private NativeWebRequest webRequest;

  private TxCtxArgumentResolver resolver;

  @BeforeEach
  void setUp() {
    resolver =
        new TxCtxArgumentResolver(new TenantScopeResolver(), membershipCache, runTenantLocator);
    OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
    // lenient: the run-tenant-scope path returns before it ever resolves the current user, so these
    // are unused by those cases (and MockitoExtension strict stubbing would otherwise flag them).
    lenient().when(principal.getId()).thenReturn(USER_ID);
    Authentication authentication = mock(Authentication.class);
    lenient().when(authentication.getPrincipal()).thenReturn(principal);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authorizeTenants(String... tenantIds) {
    when(membershipCache.findTenantIdsByUserId(USER_ID)).thenReturn(Arrays.asList(tenantIds));
  }

  private void requireSelector() {
    // lenient: the resolver now checks @RunTenantScope first, so hasParameterAnnotation is also
    // invoked with RunTenantScope.class - which must not trip strict stubbing's PotentialStubbing
    // guard on this RequireTenantSelector stub.
    lenient().when(parameter.hasParameterAnnotation(RequireTenantSelector.class)).thenReturn(true);
  }

  private TxCtx resolve() {
    return (TxCtx) resolver.resolveArgument(parameter, null, webRequest, null);
  }

  @Test
  @DisplayName("a single-tenant caller (Community Edition) resolves to its own tenant")
  void singleTenantCallerResolvesWithoutSelector() {
    authorizeTenants("tenant-1");
    requireSelector();

    assertThat(resolve().toGuc()).isEqualTo("tenant-1");
  }

  @Test
  @DisplayName("a multi-tenant caller authorized on the default tenant falls back to it")
  void multiTenantCallerFallsBackToDefaultTenant() {
    authorizeTenants("tenant-1", Tenant.DEFAULT_TENANT_UUID, "tenant-2");
    requireSelector();

    assertThat(resolve().toGuc()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName("a multi-tenant caller without default-tenant access is the only refused case")
  void multiTenantCallerWithoutDefaultTenantIsRefused() {
    authorizeTenants("tenant-1", "tenant-2");
    requireSelector();

    assertThatThrownBy(this::resolve).isInstanceOf(TenantSelectorRequiredException.class);
  }

  @Test
  @DisplayName("without the annotation, no selector still yields the full allowed set")
  void withoutAnnotationNoSelectorYieldsFullSet() {
    authorizeTenants("tenant-1", "tenant-2");

    assertThat(resolve().toGuc()).isEqualTo("tenant-1,tenant-2");
  }

  private void runTenantScoped() {
    when(parameter.hasParameterAnnotation(RunTenantScope.class)).thenReturn(true);
  }

  private void pathRunId(String runId) {
    pathVariables(Map.of("runId", runId));
  }

  private void pathVariables(Map<String, String> variables) {
    when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST))
        .thenReturn(variables);
  }

  @Test
  @DisplayName("a run-tenant-scoped callback derives the run's own tenant, ignoring the caller")
  void runTenantScopeDerivesTheParentRunTenant() {
    // The caller is a member of two OTHER tenants and selected none of the run's; the scope must
    // still come from the run, so the callback acts on the run's tenant regardless of the caller.
    runTenantScoped();
    pathRunId("run-1");
    when(runTenantLocator.findRunTenant("run-1")).thenReturn(Optional.of("run-owner-tenant"));

    assertThat(resolve().toGuc()).isEqualTo("run-owner-tenant");
  }

  @Test
  @DisplayName("a run-tenant-scoped callback for an unknown run is fail-closed (missing scope)")
  void runTenantScopeUnknownRunIsFailClosed() {
    runTenantScoped();
    pathRunId("ghost-run");
    when(runTenantLocator.findRunTenant("ghost-run")).thenReturn(Optional.empty());

    // Missing scope denies every row, so the callback's own run lookup then reports a 404.
    assertThat(resolve()).isInstanceOf(TxCtx.Missing.class);
  }

  @Test
  @DisplayName("a run-tenant-scoped callback without a {runId} path variable is fail-closed")
  void runTenantScopeWithoutRunIdPathVariableIsFailClosed() {
    // Defensive contract for a future handler that carries the annotation but names its path
    // variable differently: the derivation cannot identify the run, so it must deny everything
    // (the safe direction) rather than fall back to any caller-derived scope.
    runTenantScoped();
    pathVariables(Map.of());

    assertThat(resolve()).isInstanceOf(TxCtx.Missing.class);
    verifyNoInteractions(runTenantLocator);
  }

  @Test
  @DisplayName(
      "on the tenant-prefixed route the annotation is inert: the caller-authorized resolution"
          + " applies")
  void runTenantScopeOnTenantPrefixedRouteKeepsCallerAuthorizedResolution() {
    // The service-identity derivation exists only for the legacy non-prefixed callback route. A
    // request that addresses a tenant through the URL (the operator route) keeps the standard
    // "rights are the boundary" resolution: the scope is the addressed tenant, validated against
    // the caller's memberships, and the run's own tenant is never consulted.
    runTenantScoped();
    pathVariables(Map.of("tenantId", "tenant-1", "runId", "run-1"));
    authorizeTenants("tenant-1", "tenant-2");

    assertThat(resolve().toGuc()).isEqualTo("tenant-1");
    verifyNoInteractions(runTenantLocator);
  }

  @Test
  @DisplayName("on the tenant-prefixed route a tenant outside the caller's rights stays refused")
  void runTenantScopeOnTenantPrefixedRouteRefusesForeignTenant() {
    // The annotated handler must not become a caller-independent door on the prefixed route: a
    // caller addressing a tenant it is not a member of is refused exactly like on any other
    // prefixed endpoint, whatever tenant the run named by {runId} belongs to.
    runTenantScoped();
    pathVariables(Map.of("tenantId", "tenant-3", "runId", "run-1"));
    authorizeTenants("tenant-1", "tenant-2");

    assertThatThrownBy(this::resolve).isInstanceOf(TenantAccessDeniedException.class);
    verifyNoInteractions(runTenantLocator);
  }
}
