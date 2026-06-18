package io.openaev.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
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
 * the rights is refused, and no selector yields the full allowed set. No mocks: a real user with
 * real tenant memberships hits a real endpoint that returns the resolved scope.
 */
@WithMockUser
@DisplayName("TxCtx is resolved from the request within the caller's rights (HTTP)")
class TxCtxArgumentResolverIntegrationTest extends IntegrationTest {

  private static final String NO_PATH = "/api/test/txctx/scope";
  private static final String WITH_PATH = "/api/test/txctx/{tenantId}/scope";

  @Autowired private MockMvc mvc;
  @Autowired private TenantService tenantService;
  @Autowired private TenantIsolationTestHelper tenantHelper;

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
  }
}
