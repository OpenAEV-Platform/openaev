package io.openaev.api.xtm_composer;

import static io.openaev.api.xtm_composer.XtmComposerApi.TENANT_XTMCOMPOSER_URI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
@DisplayName("XTM Composer API tenant scope tests")
class XtmComposerApiTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("given_tenantScopedReachableRoute_should_allow_catalogReaders")
  void given_tenantScopedReachableRoute_should_allow_catalogReaders() throws Exception {
    // -- ACT & ASSERT --
    mvc.perform(get(tenantUri(TENANT_XTMCOMPOSER_URI + "/reachable"))).andExpect(status().isOk());
  }
}
