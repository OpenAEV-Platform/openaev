package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.aop.AccessControl;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end proof, through a real controller and MockMvc, that a {@code TxCtx} method parameter is
 * resolved from the request within the caller's rights: the selector (path or {@code X-Tenant-Ids}
 * header) is intersected with the user's tenants, the path wins when present, a selector outside
 * the rights is refused, and no selector yields the full allowed set. On selector-requiring
 * endpoints, a missing selector falls back (single-tenant caller, then default tenant) rather than
 * being refused, so tenant-unaware API clients keep working; only a multi-tenant caller without
 * default-tenant access is refused. No mocks: a real user with real tenant memberships hits a real
 * endpoint that returns the resolved scope.
 */
@WithMockUser
@DisplayName("TxCtx is resolved from the request within the caller's rights (HTTP)")
class TxCtxArgumentResolverIntegrationTest extends IntegrationTest {

  private static final String NO_PATH = "/api/test/txctx/scope";
  private static final String WITH_PATH = "/api/test/txctx/{tenantId}/scope";

  @Autowired private MockMvc mvc;
  @Autowired private TenantService tenantService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantMembershipCacheManager tenantMembershipCacheManager;

  private String tenantA;
  private String tenantB;
  private String fullScope;

  @BeforeEach
  void seedMemberships() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("t9b-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("t9b-b").getId();
    String userId = SessionHelper.currentUser().getId();
    fullScope =
        tenantService.findTenantsByUserId(userId).stream()
            .map(Tenant::getId)
            .sorted()
            .collect(Collectors.joining(","));
  }

  @Test
  @DisplayName("no selector resolves to the user's full allowed set")
  void noSelectorYieldsFullAuthorized() throws Exception {
    mvc.perform(get(NO_PATH)).andExpect(status().isOk()).andExpect(content().string(fullScope));
  }

  @Test
  @DisplayName("a header selector within the rights is kept")
  void headerWithinRightsIsKept() throws Exception {
    mvc.perform(get(NO_PATH).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk())
        .andExpect(content().string(tenantA));
  }

  @Test
  @DisplayName("a multi-tenant header within the rights is kept, sorted")
  void headerMultipleWithinRightsIsKept() throws Exception {
    String expected = sorted(tenantA, tenantB);
    mvc.perform(get(NO_PATH).header("X-Tenant-Ids", tenantA + "," + tenantB))
        .andExpect(status().isOk())
        .andExpect(content().string(expected));
  }

  @Test
  @DisplayName("a header selector outside the rights is refused (403)")
  void headerOutsideRightsIsForbidden() throws Exception {
    mvc.perform(get(NO_PATH).header("X-Tenant-Ids", "tenant-not-mine"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("a path selector within the rights is kept")
  void pathWithinRightsIsKept() throws Exception {
    mvc.perform(get(WITH_PATH, tenantA))
        .andExpect(status().isOk())
        .andExpect(content().string(tenantA));
  }

  @Test
  @DisplayName("the path wins over the header when both are present")
  void pathWinsOverHeader() throws Exception {
    mvc.perform(get(WITH_PATH, tenantA).header("X-Tenant-Ids", tenantB))
        .andExpect(status().isOk())
        .andExpect(content().string(tenantA));
  }

  @Test
  @DisplayName("a path selector outside the rights is refused (403)")
  void pathOutsideRightsIsForbidden() throws Exception {
    mvc.perform(get(WITH_PATH, "tenant-not-mine")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "a selector-requiring endpoint without one: a multi-tenant caller not on the default tenant"
          + " is the only refused case (400)")
  void requiredSelectorMissingIsRejected() throws Exception {
    // The mock user is a member of tenants A and B but NOT of the default tenant: no fallback is
    // safe (any silent pick could read or write the wrong tenant), so the request is refused.
    mvc.perform(get("/api/test/txctx/required/scope")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "a selector-requiring endpoint without one falls back to the default tenant for a"
          + " multi-tenant caller authorized on it")
  void requiredSelectorMissingFallsBackToDefaultTenant() throws Exception {
    // Tenant-unaware API clients (collectors, injectors) never send a selector; the platform-wide
    // convention for requests without an explicit tenant context is the default tenant.
    String userId = SessionHelper.currentUser().getId();
    tenantRepository.addUserToTenant(userId, Tenant.DEFAULT_TENANT_UUID);
    tenantMembershipCacheManager.evict(userId, Tenant.DEFAULT_TENANT_UUID);

    mvc.perform(get("/api/test/txctx/required/scope"))
        .andExpect(status().isOk())
        .andExpect(content().string(Tenant.DEFAULT_TENANT_UUID));
  }

  @Test
  @DisplayName("an endpoint requiring a selector accepts a request that carries one")
  void requiredSelectorPresentIsAccepted() throws Exception {
    mvc.perform(get("/api/test/txctx/required/scope").header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk())
        .andExpect(content().string(tenantA));
  }

  @Test
  @DisplayName("a required selector that is present but outside the rights is still 403, not 400")
  void requiredSelectorOutsideRightsIsForbidden() throws Exception {
    mvc.perform(get("/api/test/txctx/required/scope").header("X-Tenant-Ids", "tenant-not-mine"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("the response varies by X-Tenant-Ids when the header can influence the scope")
  void varyHeaderIsSetWhenTheHeaderCanInfluenceScope() throws Exception {
    String vary = mvc.perform(get(NO_PATH)).andReturn().getResponse().getHeader("Vary");
    assertTrue(
        vary != null && vary.contains("X-Tenant-Ids"),
        "a header-scoped response must carry Vary: X-Tenant-Ids, got: " + vary);
  }

  @Test
  @DisplayName("the response does not vary by X-Tenant-Ids when the path pins the tenant")
  void varyHeaderIsNotSetWhenPathPinsTheTenant() throws Exception {
    String vary = mvc.perform(get(WITH_PATH, tenantA)).andReturn().getResponse().getHeader("Vary");
    assertTrue(
        vary == null || !vary.contains("X-Tenant-Ids"),
        "a path-scoped response must not claim to vary by the ignored header, got: " + vary);
  }

  private static String sorted(String... ids) {
    return java.util.Arrays.stream(ids).sorted().collect(Collectors.joining(","));
  }

  @TestConfiguration
  static class TestControllerConfig {
    @Bean
    ScopeController scopeController() {
      return new ScopeController();
    }
  }

  /** Returns the scope the resolver produced, so the test can assert it over HTTP. */
  @RestController
  static class ScopeController {

    @GetMapping("/api/test/txctx/scope")
    @AccessControl(skipRBAC = true)
    @Transactional(readOnly = true)
    public String scope(TxCtx ctx) {
      return ctx.toGuc();
    }

    @GetMapping("/api/test/txctx/{tenantId}/scope")
    @AccessControl(skipRBAC = true)
    @Transactional(readOnly = true)
    public String scopeForPath(TxCtx ctx) {
      return ctx.toGuc();
    }

    @GetMapping("/api/test/txctx/required/scope")
    @AccessControl(skipRBAC = true)
    @Transactional(readOnly = true)
    public String requiredScope(@RequireTenantSelector TxCtx ctx) {
      return ctx.toGuc();
    }
  }
}
