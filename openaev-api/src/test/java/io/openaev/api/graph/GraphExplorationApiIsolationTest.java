package io.openaev.api.graph;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exploration graph endpoints (#9388) through the real HTTP stack: an entity of one tenant is
 * never part of a slice read on another tenant's path, the owner tenant sees it with its ring, an
 * unknown entity or edge type is a 400, and a caller without ASSET READ is refused before any read.
 * Every read of {@link GraphExplorationService} carries an explicit tenant predicate, so these
 * assertions hold whatever the statement inspector's table list is.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,EXPLORATION_GRAPH")
@DisplayName("Exploration graph: tenant isolation and access gate through the real HTTP endpoints")
class GraphExplorationApiIsolationTest extends IntegrationTest {

  private static final String SCOPED = TENANT_PREFIX + "/graph";
  private static final String NEIGHBOURHOOD = SCOPED + "/{type}/{id}/neighbourhood";
  private static final String HOT_PATHS = SCOPED + "/hot-paths";
  private static final String PATH = SCOPED + "/path";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private UserComposer userComposer;

  private String tenantA;
  private String tenantB;
  private Seed seedA;

  private record Seed(String endpointId, String assetGroupId) {}

  @BeforeEach
  void seedOneAssetGroupInTenantA() throws Exception {
    endpointComposer.reset();
    assetGroupComposer.reset();
    tenantA = tenantHelper.createTenantWithCurrentUser("graph-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("graph-iso-b").getId();
    seedA = seed(tenantA);
    // Seeded entities leave the persistence context, so every read below goes to the database.
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void tearDown() {
    userComposer.reset();
  }

  @Test
  @DisplayName("given A's asset, when reading its neighbourhood on A's path, then it and its group")
  void given_ownTenantAsset_should_returnTheAssetAndItsGroup() throws Exception {
    mvc.perform(get(NEIGHBOURHOOD, tenantA, "asset", seedA.endpointId()).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graph_focus").value("ASSET:" + seedA.endpointId()))
        .andExpect(
            jsonPath("$.graph_nodes[?(@.node_entity_id == '%s')]", seedA.assetGroupId())
                .isNotEmpty())
        .andExpect(jsonPath("$.graph_edges[?(@.edge_type == 'MEMBER_OF')]").isNotEmpty());
  }

  @Test
  @DisplayName("given A's asset, when B's path reads its neighbourhood, then the slice is empty")
  void given_otherTenantAsset_should_returnAnEmptySlice() throws Exception {
    String body =
        mvc.perform(get(NEIGHBOURHOOD, tenantB, "asset", seedA.endpointId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.graph_nodes").isEmpty())
            .andExpect(jsonPath("$.graph_edges").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(seedA.endpointId()).doesNotContain(seedA.assetGroupId());
  }

  @Test
  @DisplayName("given A's asset group, when B's path reads it, then none of A's members leak")
  void given_otherTenantAssetGroup_should_leakNoMember() throws Exception {
    String body =
        mvc.perform(get(NEIGHBOURHOOD, tenantB, "asset_group", seedA.assetGroupId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.graph_nodes").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(seedA.endpointId());
  }

  @Test
  @DisplayName("given A's asset and group, when B's path asks for a path between them, none")
  void given_otherTenantEntities_should_findNoPath() throws Exception {
    mvc.perform(
            get(PATH, tenantB)
                .param("fromType", "asset")
                .param("fromId", seedA.endpointId())
                .param("toType", "asset_group")
                .param("toId", seedA.assetGroupId())
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.graph_path").isEmpty())
        .andExpect(jsonPath("$.graph_nodes").isEmpty());
  }

  @Test
  @DisplayName("given an unknown entity type, when reading a neighbourhood, then 400")
  void given_unknownEntityType_should_answerBadRequest() throws Exception {
    mvc.perform(get(NEIGHBOURHOOD, tenantA, "not_a_type", seedA.endpointId()).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("given an unknown edge type, when reading a neighbourhood, then 400")
  void given_unknownEdgeType_should_answerBadRequest() throws Exception {
    mvc.perform(
            get(NEIGHBOURHOOD, tenantA, "asset", seedA.endpointId())
                .param("edges", "NOT_AN_EDGE")
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("given a caller without ASSET READ, when reading the landing view, then 403")
  void given_callerWithoutAssetRead_should_beForbidden() throws Exception {
    User plain =
        userComposer
            .forUser(
                UserFixture.getUser("Graph", "NoGrant", UUID.randomUUID() + "@unittests.invalid"))
            .persist()
            .get();
    mvc.perform(
            get(HOT_PATHS, tenantA)
                .with(authentication(buildAuthenticationToken(plain)))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given A's seed, when B's path reads the landing view, then none of A's rows")
  void given_otherTenantSeed_should_keepTheLandingViewFreeOfIt() throws Exception {
    String body =
        mvc.perform(get(HOT_PATHS, tenantB).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(seedA.endpointId()).doesNotContain(seedA.assetGroupId());
  }

  // -- Seed --

  private Seed seed(String tenantId) {
    Tenant tenant = new Tenant(tenantId);
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setTenant(tenant);
    AssetGroup group = AssetGroupFixture.createDefaultAssetGroup("graph-iso-group");
    group.setTenant(tenant);
    assetGroupComposer
        .forAssetGroup(group)
        .withAsset(endpointComposer.forEndpoint(endpoint))
        .persist();
    return new Seed(endpoint.getId(), group.getId());
  }
}
