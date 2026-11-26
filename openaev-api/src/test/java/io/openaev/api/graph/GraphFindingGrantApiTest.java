package io.openaev.api.graph;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finding nodes of the exploration graph follow the findings read capability, not the ASSET READ
 * the endpoints require (#9560): a non-admin caller who reads assets but not findings gets the
 * asset's ring without its findings and with a zero finding count, through the real HTTP stack.
 */
@Transactional
@WithMockUser(isAdmin = false)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,EXPLORATION_GRAPH")
@DisplayName("Exploration graph: finding nodes follow the findings read capability")
class GraphFindingGrantApiTest extends IntegrationTest {

  private static final String NEIGHBOURHOOD = TENANT_PREFIX + "/graph/{type}/{id}/neighbourhood";
  private static final String STARTING_POINTS = TENANT_PREFIX + "/graph/starting-points";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private FindingComposer findingComposer;

  private String tenantId;
  private String endpointId;
  private String findingId;

  private void seed(Set<Capability> capabilities) throws Exception {
    tenantId =
        tenantHelper.createTenantWithCapabilities("graph-finding-grant", capabilities).getId();
    Tenant tenant = new Tenant(tenantId);
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setTenant(tenant);
    Finding finding = FindingFixture.createDefaultCveFindingWithRandomTitle();
    finding.setTenant(tenant);
    findingComposer
        .forFinding(finding)
        .withEndpoint(endpointComposer.forEndpoint(endpoint))
        .persist();
    endpointId = endpoint.getId();
    findingId = finding.getId();
    entityManager.flush();
    entityManager.clear();
  }

  @BeforeEach
  void resetComposers() {
    endpointComposer.reset();
    findingComposer.reset();
  }

  @Test
  @DisplayName("given ASSET READ without findings read, when expanding an asset, then no finding")
  void given_noFindingGrant_should_hideFindingNodesAndCounts() throws Exception {
    seed(Set.of(Capability.ACCESS_ASSETS));

    mvc.perform(get(NEIGHBOURHOOD, tenantId, "asset", endpointId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graph_focus").value("ASSET:" + endpointId))
        .andExpect(jsonPath("$.graph_nodes[?(@.node_type == 'FINDING')]").isEmpty())
        .andExpect(jsonPath("$.graph_edges[?(@.edge_type == 'FOUND_ON')]").isEmpty())
        .andExpect(
            jsonPath("$.graph_nodes[?(@.node_entity_id == '%s')].node_finding_count", endpointId)
                .value(0));
  }

  @Test
  @DisplayName("given no findings read, when opening a finding, then the slice is empty")
  void given_noFindingGrant_should_returnAnEmptySliceOnAFinding() throws Exception {
    seed(Set.of(Capability.ACCESS_ASSETS));

    mvc.perform(get(NEIGHBOURHOOD, tenantId, "finding", findingId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graph_nodes[?(@.node_type == 'FINDING')]").isEmpty());
  }

  @Test
  @DisplayName("given no findings read, when reading the starting points, then no top finding")
  void given_noFindingGrant_should_leaveTopFindingsOutOfTheRail() throws Exception {
    seed(Set.of(Capability.ACCESS_ASSETS));

    mvc.perform(get(STARTING_POINTS, tenantId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.starting_points[?(@.point_kind == 'TOP_FINDING')]").isEmpty());
  }

  @Test
  @DisplayName("given ASSET READ and findings read, when expanding an asset, then its finding")
  void given_findingGrant_should_showFindingNodes() throws Exception {
    seed(Set.of(Capability.ACCESS_ASSETS, Capability.ACCESS_FINDINGS));

    mvc.perform(get(NEIGHBOURHOOD, tenantId, "asset", endpointId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graph_nodes[?(@.node_entity_id == '%s')]", findingId).isNotEmpty())
        .andExpect(jsonPath("$.graph_edges[?(@.edge_type == 'FOUND_ON')]").isNotEmpty())
        .andExpect(
            jsonPath("$.graph_nodes[?(@.node_entity_id == '%s')].node_finding_count", endpointId)
                .value(1));
  }
}
