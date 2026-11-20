package io.openaev.api.graph;

import static io.openaev.api.graph.GraphFeatureGateTest.CONNECTIONS;
import static io.openaev.api.graph.GraphFeatureGateTest.LINKS;
import static io.openaev.api.graph.GraphFeatureGateTest.STORY;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The exploration graph preview feature turned on: the routes refused by default are served. */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,EXPLORATION_GRAPH")
@DisplayName("Exploration graph feature flag: on")
class GraphFeatureOnTest extends IntegrationTest {

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
  @DisplayName("given the flag on, the graph landing story is served")
  void given_flagOn_should_serveTheGraph() throws Exception {
    mvc.perform(get(STORY, DEFAULT_TENANT_UUID).with(csrf())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("given the flag on, the links of an existing asset are served")
  void given_flagOn_should_serveAssetLinks() throws Exception {
    mvc.perform(get(LINKS, assetId).with(csrf())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("given the flag on, the XTM connections are served")
  void given_flagOn_should_serveXtmConnections() throws Exception {
    mvc.perform(get(CONNECTIONS, DEFAULT_TENANT_UUID).with(csrf())).andExpect(status().isOk());
  }
}
