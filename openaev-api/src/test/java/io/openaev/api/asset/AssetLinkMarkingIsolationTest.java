package io.openaev.api.asset;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.asset.dto.AssetLinkInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetLinkType;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtm.counterpart.form.CounterpartLookupInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Markings on asset links and on the counterpart reverse lookup (#9447, #9388): with {@code assets}
 * marking-active, a user cleared {@code TLP:GREEN} sees, adds and removes links only between assets
 * they can read, and another XTM product asking with that user's token never learns a {@code
 * TLP:RED} asset.
 *
 * <p>Seeding is raw JDBC, as in {@link AssetMarkingIsolationTest}: the marking guard would refuse
 * an ORM write of a {@code TLP:RED} row by a caller who does not hold it.
 */
@Transactional
@TestPropertySource(
    properties = {
      "openaev.marking.active-tables=assets",
      "openaev.enabled-dev-features=MARKING,EXPLORATION_GRAPH,XTM_COUNTERPARTS"
    })
@WithMockUser(withCapabilities = {Capability.ACCESS_ASSETS, Capability.MANAGE_ASSETS})
@DisplayName("asset links and counterpart lookups under markings")
class AssetLinkMarkingIsolationTest extends IntegrationTest {

  private static final String LINKS_URI = "/api/assets/{assetId}/links";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @PersistenceContext private EntityManager entityManager;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String userId;
  private Group group;

  private Endpoint green;
  private Endpoint otherGreen;
  private Endpoint red;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    tenantGroupComposer.reset();
    endpointComposer.reset();
    markingDefinitionComposer.reset();

    tenantId = TenantContext.getCurrentTenant();
    User user = testUserHolder.get();
    userId = user.getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    // Orders above the seeded 10..50 band so a fixture never ties with a default.
    MarkingDefinition tlpGreen = marking(60);
    MarkingDefinition tlpRed = marking(70);

    green = endpoint("green");
    otherGreen = endpoint("other-green");
    red = endpoint("red");

    group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("link-marking-" + uniqueDefinition()))
            .persist()
            .get();
    group.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(group);

    entityManager.flush();

    mark(green, tlpGreen);
    mark(otherGreen, tlpGreen);
    mark(red, tlpRed);
    // The link to RED exists, put there by someone cleared for it.
    jdbc.update(
        "INSERT INTO assets_links (asset_id, linked_asset_id, asset_link_type,"
            + " asset_link_created_at) VALUES (?, ?, ?, now())",
        green.getId(),
        red.getId(),
        AssetLinkType.CONNECTS_TO.name());
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        group.getId(),
        tlpGreen.getId());
    clearanceCache.evictForUser(userId);
    entityManager.clear();
  }

  @Test
  @DisplayName("given a link to a RED asset, should leave it out of the GREEN asset's links")
  void given_linkToRed_should_hideItFromTheList() throws Exception {
    // -- ARRANGE --
    jdbc.update(
        "INSERT INTO assets_links (asset_id, linked_asset_id, asset_link_type,"
            + " asset_link_created_at) VALUES (?, ?, ?, now())",
        green.getId(),
        otherGreen.getId(),
        AssetLinkType.USES.name());

    // -- ACT --
    String response =
        mvc.perform(get(LINKS_URI, green.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    // The GREEN link proves the list is not simply empty.
    List<String> targets = JsonPath.read(response, "$[*].asset_link_target_id");
    assertThat(targets).containsExactly(otherGreen.getId());
  }

  @Test
  @DisplayName("given a RED target, should refuse a new link with 404 and store nothing")
  void given_redTarget_should_refuseTheLink() throws Exception {
    // -- ACT / ASSERT --
    mvc.perform(
            post(LINKS_URI, otherGreen.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new AssetLinkInput(red.getId(), AssetLinkType.USES))))
        .andExpect(status().isNotFound());
    assertThat(storedLinks(otherGreen.getId(), red.getId())).isZero();
  }

  @Test
  @DisplayName("given a link to a RED asset, should refuse its removal with 404 and keep it")
  void given_linkToRed_should_refuseTheRemoval() throws Exception {
    // -- ACT / ASSERT --
    // 404, not 403: a refusal would confirm that the RED asset and the link exist.
    mvc.perform(
            delete(
                    LINKS_URI + "/{linkedAssetId}/{linkType}",
                    green.getId(),
                    red.getId(),
                    AssetLinkType.CONNECTS_TO.name())
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertThat(storedLinks(green.getId(), red.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName("given a counterpart lookup by hostname, should answer GREEN and never RED")
  void given_counterpartLookup_should_hideRed() throws Exception {
    // -- ACT --
    String response =
        mvc.perform(
                post("/api/tenants/" + tenantId + "/xtm/objects/assets")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        asJsonString(
                            new CounterpartLookupInput(
                                List.of(green.getHostname(), red.getHostname())))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    List<String> ids = JsonPath.read(response, "$[*].object_id");
    assertThat(ids).containsExactly(green.getId());
  }

  // -- HELPERS --

  private int storedLinks(String assetId, String linkedAssetId) {
    entityManager.flush();
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM assets_links WHERE asset_id = ? AND linked_asset_id = ?",
            Integer.class,
            assetId,
            linkedAssetId);
    return count == null ? 0 : count;
  }

  /** Out-of-band write: see the class javadoc for why this is not an ORM save. */
  private void mark(Endpoint endpoint, MarkingDefinition marking) {
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        (Object) new String[] {marking.getId()},
        endpoint.getId());
  }

  private MarkingDefinition marking(int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(
            createMarkingDefinition(
                MarkingDefinition.TYPE_TLP, uniqueDefinition(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get();
  }

  private Endpoint endpoint(String name) {
    // A hostname must be a valid RFC 1034 name: no colon, so not uniqueDefinition().
    String unique = name + "-" + UUID.randomUUID().toString().substring(0, 8);
    return endpointComposer
        .forEndpoint(
            EndpointFixture.createEndpoint(
                "link-marking-" + unique,
                Endpoint.PLATFORM_TYPE.Linux,
                Endpoint.PLATFORM_ARCH.x86_64,
                ("link-marking-" + unique).toLowerCase(Locale.ROOT),
                new String[] {"10.0.0.1"}))
        .persist()
        .get();
  }
}
