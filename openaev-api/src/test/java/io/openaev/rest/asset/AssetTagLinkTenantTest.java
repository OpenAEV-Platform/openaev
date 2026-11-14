package io.openaev.rest.asset;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.asset.ai_targets.AiTargetApi.AI_TARGET_URI;
import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.rest.asset.security_platforms.SecurityPlatformApi.SECURITY_PLATFORM_URI;
import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The asset, asset group, endpoint, AI target and security platform updates link the entity to the
 * tags of the entity's own tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The tag ids of the
 * body resolve in the tenant of the entity written alone. The links are read from the join tables
 * through JDBC on the test connection, which the statement inspector does not rewrite. The class
 * arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Asset updates link only the tags of the entity tenant")
class AssetTagLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String ASSETS_TAGS = "SELECT tag_id FROM assets_tags WHERE asset_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String tagInB;
  private String tagInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("asset-tag-link-b").getId();
    tagInB = saveTag(tagRepository, tenantB);
    tagInDefault = saveTag(tagRepository, Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given an AI target of B when the body names a tag of each tenant then only the tag of B"
            + " is linked")
    void given_aiTargetOfB_should_linkOnlyTheTagOfB() throws Exception {
      // -- Arrange --
      String aiTarget = saveAiTarget(tenantB);
      armRequest("assets", "asset_id", aiTarget, tenantB);

      // -- Act --
      putOnHeaderRoute(AI_TARGET_URI + "/" + aiTarget, aiTargetBody(bothTags()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, ASSETS_TAGS, aiTarget))
          .as("tags linked to the AI target of B: kept %s, dropped %s", tagInB, tagInDefault)
          .containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given an asset group of B when the body names a tag of each tenant then only the tag of B"
            + " is linked")
    void given_assetGroupOfB_should_linkOnlyTheTagOfB() throws Exception {
      // -- Arrange --
      String assetGroup = saveAssetGroup(tenantB);
      armRequest("asset_groups", "asset_group_id", assetGroup, tenantB);

      // -- Act --
      putOnHeaderRoute(ASSET_GROUP_URI + "/" + assetGroup, assetGroupBody(bothTags()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(assetGroupTags(assetGroup))
          .as("tags linked to the asset group of B: kept %s, dropped %s", tagInB, tagInDefault)
          .containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given an upsert naming a security platform of B when the body names a tag of each tenant"
            + " then only the tag of B is linked")
    void given_securityPlatformUpsertOfB_should_linkOnlyTheTagOfB() throws Exception {
      // -- Arrange --
      String reference = "asset-tag-link-" + UUID.randomUUID();
      String platform = saveSecurityPlatform(tenantB, reference);
      armRequest("assets", "asset_id", platform, tenantB);

      // -- Act --
      Map<String, Object> body = securityPlatformBody(bothTags());
      body.put("asset_external_reference", reference);
      postOnHeaderRoute(SECURITY_PLATFORM_URI + "/upsert", body).andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, ASSETS_TAGS, platform))
          .as(
              "tags linked to the security platform of B: kept %s, dropped %s",
              tagInB, tagInDefault)
          .containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given a security platform of B when the body names a tag of each tenant then only the tag"
            + " of B is linked")
    void given_securityPlatformOfB_should_linkOnlyTheTagOfB() throws Exception {
      // -- Arrange --
      String platform = saveSecurityPlatform(tenantB, null);
      armRequest("assets", "asset_id", platform, tenantB);

      // -- Act --
      putOnHeaderRoute(SECURITY_PLATFORM_URI + "/" + platform, securityPlatformBody(bothTags()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, ASSETS_TAGS, platform))
          .as(
              "tags linked to the security platform of B: kept %s, dropped %s",
              tagInB, tagInDefault)
          .containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given an endpoint of the default tenant when the body names a tag of each tenant then"
            + " only the tag of the default tenant is linked")
    void given_endpointOfDefaultTenant_should_linkOnlyTheTagOfTheDefaultTenant() throws Exception {
      // -- Arrange --
      String endpoint = saveEndpoint(Tenant.DEFAULT_TENANT_UUID);
      armRequest("assets", "asset_id", endpoint, Tenant.DEFAULT_TENANT_UUID);

      // -- Act --
      putOnHeaderRoute(ENDPOINT_URI + "/" + endpoint, endpointBody(bothTags()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, ASSETS_TAGS, endpoint))
          .as(
              "tags linked to the endpoint of the default tenant: kept %s, dropped %s",
              tagInDefault, tagInB)
          .containsExactly(tagInDefault);
    }
  }

  @Nested
  @DisplayName("Body with a null tag list")
  class BodyWithNullTagList {

    @Test
    @DisplayName(
        "given an asset group update with a null tag list on a group of B holding a tag of B then"
            + " the request is refused and the link stays")
    void given_assetGroupUpdateWithNullTagList_should_refuseAndKeepTheLinks() throws Exception {
      // -- Arrange --
      String assetGroup = saveAssetGroupWithTag(tenantB, tagInB);
      armRequest("asset_groups", "asset_group_id", assetGroup, tenantB);
      Map<String, Object> body = assetGroupBody(null);

      // -- Act --
      int status =
          putOnHeaderRoute(ASSET_GROUP_URI + "/" + assetGroup, body)
              .andReturn()
              .getResponse()
              .getStatus();

      // -- Assert --
      assertThat(status).as("status of an asset group update with a null tag list").isEqualTo(400);
      assertThat(assetGroupTags(assetGroup)).containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given an AI target of B when the body under the path of B names a tag of B then the tag is"
            + " linked")
    void given_aiTargetOfB_should_linkTheTagOfBOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      String aiTarget = saveAiTarget(tenantB);
      armRequest("assets", "asset_id", aiTarget, tenantB);

      // -- Act --
      mvc.perform(
              put(TENANT_PREFIX + "/ai_targets/{id}", tenantB, aiTarget)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(aiTargetBody(List.of(tagInB))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, ASSETS_TAGS, aiTarget)).containsExactly(tagInB);
    }
  }

  // -- Helpers --

  private List<String> bothTags() {
    return List.of(tagInB, tagInDefault);
  }

  private ResultActions putOnHeaderRoute(String uri, Map<String, Object> body) throws Exception {
    return mvc.perform(
        put(uri)
            .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))
            .with(csrf()));
  }

  private ResultActions postOnHeaderRoute(String uri, Map<String, Object> body) throws Exception {
    return mvc.perform(
        post(uri)
            .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))
            .with(csrf()));
  }

  private Map<String, Object> aiTargetBody(List<String> tags) {
    Map<String, Object> body = new HashMap<>();
    body.put("asset_name", "asset-tag-link-" + UUID.randomUUID());
    body.put("ai_target_provider", Asset.AI_TARGET_PROVIDER.OPENAI_COMPATIBLE.name());
    body.put("asset_tags", tags);
    return body;
  }

  private Map<String, Object> assetGroupBody(List<String> tags) {
    Map<String, Object> body = new HashMap<>();
    body.put("asset_group_name", "asset-tag-link-" + UUID.randomUUID());
    body.put("asset_group_tags", tags);
    return body;
  }

  private Map<String, Object> securityPlatformBody(List<String> tags) {
    Map<String, Object> body = new HashMap<>();
    body.put("asset_name", "asset-tag-link-" + UUID.randomUUID());
    body.put("security_platform_type", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name());
    body.put("asset_tags", tags);
    return body;
  }

  private Map<String, Object> endpointBody(List<String> tags) {
    Map<String, Object> body = new HashMap<>();
    body.put("asset_name", "asset-tag-link-" + UUID.randomUUID());
    body.put("endpoint_platform", Endpoint.PLATFORM_TYPE.Windows.name());
    body.put("endpoint_arch", Endpoint.PLATFORM_ARCH.x86_64.name());
    body.put("asset_tags", tags);
    return body;
  }

  private String saveAiTarget(String tenantId) {
    Asset aiTarget = new Asset();
    aiTarget.setName("asset-tag-link-" + UUID.randomUUID());
    aiTarget.setCategory(AssetCategory.AI_TARGET);
    aiTarget.setAiTargetProvider(Asset.AI_TARGET_PROVIDER.OPENAI_COMPATIBLE);
    aiTarget.setAiTargetModality(Asset.AI_TARGET_MODALITY.TEXT);
    aiTarget.setTenant(new Tenant(tenantId));
    return flushed(assetRepository.save(aiTarget).getId());
  }

  private String saveAssetGroup(String tenantId) {
    AssetGroup assetGroup =
        AssetGroupFixture.createDefaultAssetGroup("asset-tag-link-" + UUID.randomUUID());
    assetGroup.setTenant(new Tenant(tenantId));
    return flushed(assetGroupRepository.save(assetGroup).getId());
  }

  private String saveAssetGroupWithTag(String tenantId, String tagId) {
    AssetGroup assetGroup =
        AssetGroupFixture.createDefaultAssetGroup("asset-tag-link-" + UUID.randomUUID());
    assetGroup.setTenant(new Tenant(tenantId));
    assetGroup.setTags(
        new HashSet<>(tagRepository.findAllByIdInAndTenantId(List.of(tagId), tenantId)));
    return flushed(assetGroupRepository.save(assetGroup).getId());
  }

  private String saveSecurityPlatform(String tenantId, String externalReference) {
    SecurityPlatform platform =
        SecurityPlatformFixture.createDefault(
            "asset-tag-link-" + UUID.randomUUID(),
            SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name());
    platform.setTenant(new Tenant(tenantId));
    platform.setExternalReference(externalReference);
    return flushed(securityPlatformRepository.save(platform).getId());
  }

  private String saveEndpoint(String tenantId) {
    Endpoint endpoint = EndpointFixture.createEndpoint("asset-tag-link-" + UUID.randomUUID());
    endpoint.setTenant(new Tenant(tenantId));
    return flushed(endpointRepository.save(endpoint).getId());
  }

  private String flushed(String id) {
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private List<String> assetGroupTags(String assetGroupId) {
    return column(
        entityManager,
        "SELECT tag_id FROM asset_groups_tags WHERE asset_group_id = ?",
        assetGroupId);
  }

  /**
   * Each tag and the owner carry the tenant the scenario relies on, then the ambient tenant is
   * reset to the default one the request thread of the header route carries.
   */
  private void armRequest(String table, String idColumn, String ownerId, String ownerTenant) {
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInB))
        .containsExactly(tenantB);
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?",
                ownerId))
        .containsExactly(ownerTenant);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
