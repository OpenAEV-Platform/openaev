package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exception.TenantSelectorRequiredException;
import io.openaev.service.tenants.TenantService;
import java.util.Arrays;
import java.util.List;
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

  @Mock private TenantService tenantService;
  @Mock private MethodParameter parameter;
  @Mock private NativeWebRequest webRequest;

  private TxCtxArgumentResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new TxCtxArgumentResolver(new TenantScopeResolver(), tenantService);
    OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
    when(principal.getId()).thenReturn(USER_ID);
    Authentication authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(principal);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authorizeTenants(String... tenantIds) {
    List<Tenant> tenants = Arrays.stream(tenantIds).map(Tenant::new).toList();
    when(tenantService.findTenantsByUserId(USER_ID)).thenReturn(tenants);
  }

  private void requireSelector() {
    when(parameter.hasParameterAnnotation(RequireTenantSelector.class)).thenReturn(true);
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
}
