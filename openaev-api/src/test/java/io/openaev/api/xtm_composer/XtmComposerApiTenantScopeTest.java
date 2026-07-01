package io.openaev.api.xtm_composer;

import static io.openaev.api.xtm_composer.XtmComposerApi.TENANT_XTMCOMPOSER_URI;
import static io.openaev.api.xtm_composer.XtmComposerApi.XTMCOMPOSER_URI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("XTM Composer API tenant scope tests")
class XtmComposerApiTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;

  @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
  @Test
  @DisplayName("given_DefaultTenantScopedReachableRoute_should_allow_catalogReaders")
  void given_DefaultTenantScopedReachableRoute_should_allow_catalogReaders() throws Exception {
    // -- ACT & ASSERT --
    mvc.perform(get(tenantUri(TENANT_XTMCOMPOSER_URI + "/reachable"))).andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  @DisplayName("given_NoDefaultTenantScopedReachableRoute_should_allow_catalogReaders")
  void given_NoDefaultTenantScopedReachableRoute_should_allow_catalogReaders() throws Exception {
    // -- ACT & ASSERT --
    var tenant =
        tenantIsolationTestHelper.createTenantWithCapabilities(
            "xtm-composer", Set.of(Capability.ACCESS_TENANT_SETTINGS));
    // Act & Assert: legacy (unscoped) route should be forbidden for tenant-only users
    mvc.perform(get(XTMCOMPOSER_URI + "/reachable")).andExpect(status().isForbidden());
    // Act & Assert: tenant-scoped route should be allowed
    mvc.perform(get((TENANT_XTMCOMPOSER_URI + "/reachable").replace("{tenantId}", tenant.getId())))
        .andExpect(status().isOk());
  }
}
