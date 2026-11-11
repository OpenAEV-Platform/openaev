package io.openaev.rest.asset_group;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.rest.tag_rule.TagRuleApi.TAG_RULE_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tag;
import io.openaev.database.model.TagRule;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.Session;
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
 * A tag rule links the asset groups of its own tenant, and an asset group links the assets of its
 * own tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The ids of the body
 * resolve in the tenant of the rule or of the group written alone: an id outside that tenant is
 * treated as an unknown id (a 404 for a tag rule, skipped for an asset group). The links are read
 * from the join tables through JDBC on the test connection, which the statement inspector does not
 * rewrite. The class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Tag rules and asset groups link only the rows of their own tenant")
class AssetGroupLinksTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String TAG_RULE_GROUPS =
      "SELECT asset_group_id FROM tag_rule_asset_groups WHERE tag_rule_id = ?";
  private static final String GROUP_ASSETS =
      "SELECT asset_id FROM asset_groups_assets WHERE asset_group_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String groupInB;
  private String groupInDefault;
  private String assetInB;
  private String assetInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("asset-group-links-b").getId();
    assertThat(tenantB).isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
    groupInB = saveAssetGroup(tenantB, List.of());
    groupInDefault = saveAssetGroup(Tenant.DEFAULT_TENANT_UUID, List.of());
    assetInB = saveEndpoint(tenantB);
    assetInDefault = saveEndpoint(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Tag rule update")
  class TagRuleUpdate {

    @Test
    @DisplayName(
        "given a tag rule of B when the body names a group of each tenant then the update is"
            + " refused as an unknown group and the links stay")
    void given_tagRuleOfB_should_refuseBodyNamingGroupOfEachTenant() throws Exception {
      // -- Arrange --
      String linked = saveAssetGroup(tenantB, List.of());
      Tag tag = saveTag(tenantB);
      String rule = saveTagRule(tenantB, tag, List.of(linked));
      armTagRule(rule, tenantB, tag, linked);

      // -- Act --
      int status =
          statusOf(
              putOnHeaderRoute(
                  TAG_RULE_URI + "/" + rule, tagRuleBody(tag, List.of(groupInB, groupInDefault))));

      // -- Assert --
      assertThat(status)
          .as(
              "status of a tag rule update naming %s (B) and %s (default)",
              groupInB, groupInDefault)
          .isEqualTo(404);
      assertThat(column(entityManager, TAG_RULE_GROUPS, rule))
          .as("groups linked to the tag rule of B")
          .containsExactly(linked);
    }

    @Test
    @DisplayName(
        "given a tag rule of B when the body names only a group of the default tenant then the"
            + " update is refused as an unknown group and the links stay")
    void given_tagRuleOfB_should_refuseBodyNamingOnlyGroupOfDefaultTenant() throws Exception {
      // -- Arrange --
      String linked = saveAssetGroup(tenantB, List.of());
      Tag tag = saveTag(tenantB);
      String rule = saveTagRule(tenantB, tag, List.of(linked));
      armTagRule(rule, tenantB, tag, linked);

      // -- Act --
      int status =
          statusOf(
              putOnHeaderRoute(
                  TAG_RULE_URI + "/" + rule, tagRuleBody(tag, List.of(groupInDefault))));

      // -- Assert --
      assertThat(status)
          .as("status of a tag rule update naming only %s (default)", groupInDefault)
          .isEqualTo(404);
      assertThat(column(entityManager, TAG_RULE_GROUPS, rule))
          .as("groups linked to the tag rule of B")
          .containsExactly(linked);
    }

    @Test
    @DisplayName(
        "given a tag rule of B without link when the body names a group of B then the group is"
            + " linked")
    void given_tagRuleOfBWithoutLink_should_linkGroupOfB() throws Exception {
      // -- Arrange --
      Tag tag = saveTag(tenantB);
      String rule = saveTagRule(tenantB, tag, List.of());
      armTagRule(rule, tenantB, tag);

      // -- Act --
      int status =
          statusOf(
              putOnHeaderRoute(TAG_RULE_URI + "/" + rule, tagRuleBody(tag, List.of(groupInB))));

      // -- Assert --
      assertThat(status).as("status of a tag rule update naming %s (B)", groupInB).isEqualTo(200);
      assertThat(column(entityManager, TAG_RULE_GROUPS, rule))
          .as("groups linked to the tag rule of B")
          .containsExactly(groupInB);
    }

    @Test
    @DisplayName(
        "given a tag rule of B holding a group of B when the group list is null then the update"
            + " succeeds and clears the links")
    void given_tagRuleOfBWithNullGroupList_should_clearTheLinks() throws Exception {
      // -- Arrange --
      String linked = saveAssetGroup(tenantB, List.of());
      Tag tag = saveTag(tenantB);
      String rule = saveTagRule(tenantB, tag, List.of(linked));
      armTagRule(rule, tenantB, tag, linked);

      // -- Act --
      int status = statusOf(putOnHeaderRoute(TAG_RULE_URI + "/" + rule, tagRuleBody(tag, null)));

      // -- Assert --
      assertThat(status).as("status of a tag rule update with a null group list").isEqualTo(200);
      assertThat(column(entityManager, TAG_RULE_GROUPS, rule))
          .as("groups linked to the tag rule of B")
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given a tag rule of B without link when the body under the path of B names a group of B"
            + " then the group is linked")
    void given_tagRuleOfB_should_linkGroupOfBOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      Tag tag = saveTag(tenantB);
      String rule = saveTagRule(tenantB, tag, List.of());
      armTagRule(rule, tenantB, tag);

      // -- Act --
      int status =
          statusOf(
              mvc.perform(
                  put(TENANT_PREFIX + "/tag-rules/{id}", tenantB, rule)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(tagRuleBody(tag, List.of(groupInB))))
                      .with(csrf())));

      // -- Assert --
      assertThat(status)
          .as("status of a tag rule update naming %s (B) under the path of B", groupInB)
          .isEqualTo(200);
      assertThat(column(entityManager, TAG_RULE_GROUPS, rule))
          .as("groups linked to the tag rule of B")
          .containsExactly(groupInB);
    }
  }

  @Nested
  @DisplayName("Tag rule creation")
  class TagRuleCreation {

    @Test
    @DisplayName(
        "given a caller holding two tenants when a rule is created on the header route naming both"
            + " then the request is refused and no rule is created")
    void given_headerNamingTwoTenants_should_refuseTheCreation() throws Exception {
      // -- Arrange --
      Tag tag = saveTag(tenantB);
      armTagRule(null, tenantB, tag);

      // -- Act --
      int status = statusOf(postOnHeaderRoute(TAG_RULE_URI, tagRuleBody(tag, List.of(groupInB))));

      // -- Assert --
      assertThat(status)
          .as("status of a tag rule creation naming two tenants in the header")
          .isEqualTo(400);
      assertThat(
              column(
                  entityManager, "SELECT tag_rule_id FROM tag_rules WHERE tag_id = ?", tag.getId()))
          .as("tag rules created for the tag of B")
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("Asset group assets update")
  class AssetGroupAssetsUpdate {

    @Test
    @DisplayName(
        "given an asset group of B when the body names an asset of each tenant then only the"
            + " asset of B is linked")
    void given_assetGroupOfB_should_linkOnlyTheAssetOfB() throws Exception {
      // -- Arrange --
      armAssetGroup(groupInB, tenantB);

      // -- Act --
      int status =
          statusOf(
              putOnHeaderRoute(
                  ASSET_GROUP_URI + "/" + groupInB + "/assets",
                  assetsBody(List.of(assetInB, assetInDefault))));

      // -- Assert --
      assertThat(status).as("status of an asset group assets update").isEqualTo(200);
      assertThat(column(entityManager, GROUP_ASSETS, groupInB))
          .as(
              "assets linked to the asset group of B: kept %s, dropped %s",
              assetInB, assetInDefault)
          .containsExactly(assetInB);
    }

    @Test
    @DisplayName(
        "given an asset group of B without link when the body names an asset of B then the asset"
            + " is linked")
    void given_assetGroupOfBWithoutLink_should_linkAssetOfB() throws Exception {
      // -- Arrange --
      armAssetGroup(groupInB, tenantB);

      // -- Act --
      int status =
          statusOf(
              putOnHeaderRoute(
                  ASSET_GROUP_URI + "/" + groupInB + "/assets", assetsBody(List.of(assetInB))));

      // -- Assert --
      assertThat(status).as("status of an asset group assets update").isEqualTo(200);
      assertThat(column(entityManager, GROUP_ASSETS, groupInB))
          .as("assets linked to the asset group of B")
          .containsExactly(assetInB);
    }

    @Test
    @DisplayName(
        "given an asset group of B holding an asset of B when the asset list is null then the"
            + " request is refused and the link stays")
    void given_assetGroupOfBWithNullAssetList_should_refuseAndKeepTheLinks() throws Exception {
      // -- Arrange --
      String group = saveAssetGroup(tenantB, List.of(assetInB));
      armAssetGroup(group, tenantB);
      assertThat(column(entityManager, GROUP_ASSETS, group)).containsExactly(assetInB);

      // -- Act --
      int status =
          statusOf(putOnHeaderRoute(ASSET_GROUP_URI + "/" + group + "/assets", assetsBody(null)));

      // -- Assert --
      assertThat(status)
          .as("status of an asset group assets update with a null list")
          .isEqualTo(400);
      assertThat(column(entityManager, GROUP_ASSETS, group))
          .as("assets linked to the asset group of B")
          .containsExactly(assetInB);
    }

    @Test
    @DisplayName(
        "given an asset group of B without link when the body under the path of B names an asset"
            + " of B then the asset is linked")
    void given_assetGroupOfB_should_linkAssetOfBOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      armAssetGroup(groupInB, tenantB);

      // -- Act --
      int status =
          statusOf(
              mvc.perform(
                  put(TENANT_PREFIX + "/asset_groups/{id}/assets", tenantB, groupInB)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(assetsBody(List.of(assetInB))))
                      .with(csrf())));

      // -- Assert --
      assertThat(status)
          .as("status of an asset group assets update under the path of B")
          .isEqualTo(200);
      assertThat(column(entityManager, GROUP_ASSETS, groupInB))
          .as("assets linked to the asset group of B")
          .containsExactly(assetInB);
    }
  }

  // -- Helpers --

  private int statusOf(ResultActions result) {
    return result.andReturn().getResponse().getStatus();
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

  private Map<String, Object> tagRuleBody(Tag tag, List<String> assetGroups) {
    Map<String, Object> body = new HashMap<>();
    body.put("tag_name", tag.getName());
    body.put("asset_groups", assetGroups);
    return body;
  }

  private Map<String, Object> assetsBody(List<String> assets) {
    Map<String, Object> body = new HashMap<>();
    body.put("asset_group_assets", assets);
    return body;
  }

  /** A tag of the tenant under a lowercase name that neither tenant holds. */
  private Tag saveTag(String tenantId) {
    Tag tag = TagFixture.getTagWithText("asset-group-links-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenantId));
    Tag saved = tagRepository.save(tag);
    entityManager.flush();
    return saved;
  }

  private String saveTagRule(String tenantId, Tag tag, List<String> groupIds) {
    TagRule tagRule = new TagRule();
    tagRule.setTag(tag);
    tagRule.setTenant(new Tenant(tenantId));
    List<AssetGroup> groups = new ArrayList<>();
    assetGroupRepository.findAllById(groupIds).forEach(groups::add);
    tagRule.setAssetGroups(groups);
    return flushed(tagRuleRepository.save(tagRule).getId());
  }

  private String saveAssetGroup(String tenantId, List<String> assetIds) {
    AssetGroup assetGroup =
        AssetGroupFixture.createDefaultAssetGroup("asset-group-links-" + UUID.randomUUID());
    assetGroup.setTenant(new Tenant(tenantId));
    List<io.openaev.database.model.Asset> assets = new ArrayList<>();
    endpointRepository.findAllById(assetIds).forEach(assets::add);
    assetGroup.setAssets(assets);
    return flushed(assetGroupRepository.save(assetGroup).getId());
  }

  private String saveEndpoint(String tenantId) {
    Endpoint endpoint = EndpointFixture.createEndpoint("asset-group-links-" + UUID.randomUUID());
    endpoint.setTenant(new Tenant(tenantId));
    return flushed(endpointRepository.save(endpoint).getId());
  }

  private String flushed(String id) {
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  /**
   * The rows the scenario relies on carry the tenant it expects, then the ambient tenant is reset
   * to the default one the request thread of the header route carries.
   */
  private void armCommon() {
    assertThat(tenantOf("asset_groups", "asset_group_id", groupInB)).containsExactly(tenantB);
    assertThat(tenantOf("asset_groups", "asset_group_id", groupInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(tenantOf("assets", "asset_id", assetInB)).containsExactly(tenantB);
    assertThat(tenantOf("assets", "asset_id", assetInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private void armTagRule(String rule, String ruleTenant, Tag tag, String... linkedGroups) {
    assertThat(tenantOf("tags", "tag_id", tag.getId())).containsExactly(ruleTenant);
    if (rule != null) {
      assertThat(tenantOf("tag_rules", "tag_rule_id", rule)).containsExactly(ruleTenant);
      assertThat(column(entityManager, TAG_RULE_GROUPS, rule))
          .containsExactlyInAnyOrder(linkedGroups);
    }
    for (String linked : linkedGroups) {
      assertThat(tenantOf("asset_groups", "asset_group_id", linked)).containsExactly(ruleTenant);
    }
    armCommon();
  }

  private void armAssetGroup(String group, String groupTenant) {
    assertThat(tenantOf("asset_groups", "asset_group_id", group)).containsExactly(groupTenant);
    armCommon();
  }

  private List<String> tenantOf(String table, String idColumn, String id) {
    return column(
        entityManager, "SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?", id);
  }

  /** The first column of every row the query returns for the single parameter. */
  private static List<String> column(EntityManager entityManager, String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> values = new ArrayList<>();
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                  }
                }
              }
              return values;
            });
  }
}
