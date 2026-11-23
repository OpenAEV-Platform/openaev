package io.openaev.api.graph;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Endpoint;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exploration graph preview feature ({@code EXPLORATION_GRAPH}) with its default, off: the
 * graph and asset link routes answer a 404 as if they did not exist. The XTM counterpart routes
 * (the cross-product pivots) are not behind the flag (Sam, 23 Nov 2026) and answer. The flag-on
 * side is {@link GraphFeatureOnTest}.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Exploration graph feature flag: off by default")
class GraphFeatureGateTest extends IntegrationTest {

  static final String STORY = TENANT_PREFIX + "/graph/story";
  static final String LINKS = "/api/assets/{assetId}/links";
  static final String CONNECTIONS = TENANT_PREFIX + "/xtm/connections";
  static final String LOCAL_ASSETS = TENANT_PREFIX + "/xtm/objects/assets";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;

  private String assetId;

  @BeforeEach
  void setUp() {
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    endpointComposer.reset();
    Endpoint endpoint = EndpointFixture.createEndpoint();
    assetId = endpointComposer.forEndpoint(endpoint).persist().get().getId();
    entityManager.flush();
  }

  @Test
  @DisplayName("given the flag off, the graph landing story is not found")
  void given_flagOff_should_refuseTheGraph() throws Exception {
    mvc.perform(get(STORY, DEFAULT_TENANT_UUID).with(csrf())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given the flag off, the links of an existing asset are not found")
  void given_flagOff_should_refuseAssetLinks() throws Exception {
    mvc.perform(get(LINKS, assetId).with(csrf())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given the flag off, the XTM connections and reverse lookups still answer")
  void given_flagOff_should_serveXtmCounterparts() throws Exception {
    mvc.perform(get(CONNECTIONS, DEFAULT_TENANT_UUID).with(csrf())).andExpect(status().isOk());
    mvc.perform(
            post(LOCAL_ASSETS, DEFAULT_TENANT_UUID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"counterpart_keys\":[\"srv-01\"]}")
                .with(csrf()))
        .andExpect(status().isOk());
  }
}
