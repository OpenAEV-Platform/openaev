package io.openaev.api.asset;

import static io.openaev.api.asset.AssetOptionsApi.TENANT_ASSET_URI;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.asset.dto.AssetLinkInput;
import io.openaev.database.model.AssetLinkType;
import io.openaev.database.model.Endpoint;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** {@code /api/assets/{id}/links} (#9447): typed links between two assets of one tenant. */
@Transactional
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,EXPLORATION_GRAPH")
@DisplayName("Asset links API")
class AssetLinkApiTest extends IntegrationTest {

  /** The links routes under the default tenant, where the fixtures live, as the UI calls them. */
  private static final String LINKS_URI =
      TENANT_ASSET_URI.replace("{tenantId}", DEFAULT_TENANT_UUID) + "/{assetId}/links";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private EndpointComposer endpointComposer;
  @PersistenceContext private EntityManager entityManager;

  private JdbcTemplate jdbc;
  private Endpoint host;
  private Endpoint account;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    endpointComposer.reset();
    host = endpoint("links-host-");
    account = endpoint("links-account-");
    entityManager.flush();
  }

  @Test
  @DisplayName("given two assets, should store one link, list it from both ends, and keep it once")
  void given_twoAssets_should_storeAndListTheLink() throws Exception {
    // Act
    addLink(account.getId(), host.getId(), AssetLinkType.LOGGED_ON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_link_source_id").value(account.getId()))
        .andExpect(jsonPath("$.asset_link_target_id").value(host.getId()))
        .andExpect(jsonPath("$.asset_link_target_name").value(host.getName()))
        .andExpect(jsonPath("$.asset_link_type").value("LOGGED_ON"));
    addLink(account.getId(), host.getId(), AssetLinkType.LOGGED_ON).andExpect(status().isOk());

    // Assert
    assertThat(storedLinks(account.getId())).isEqualTo(1);
    mvc.perform(get(LINKS_URI, host.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].asset_link_source_id").value(account.getId()));
  }

  @Test
  @DisplayName("given a link to the asset itself, should refuse with 400")
  void given_selfLink_should_refuse() throws Exception {
    // Act and assert
    addLink(host.getId(), host.getId(), AssetLinkType.USES).andExpect(status().isBadRequest());
    assertThat(storedLinks(host.getId())).isZero();
  }

  @Test
  @DisplayName("given an unknown linked asset, should answer not found and store nothing")
  void given_unknownLinkedAsset_should_answerNotFound() throws Exception {
    // Act and assert
    addLink(host.getId(), UUID.randomUUID().toString(), AssetLinkType.CONNECTS_TO)
        .andExpect(status().isNotFound());
    assertThat(storedLinks(host.getId())).isZero();
  }

  @Test
  @DisplayName("given a stored link, should delete it, then answer not found on a second delete")
  void given_storedLink_should_deleteIt() throws Exception {
    // Arrange
    addLink(account.getId(), host.getId(), AssetLinkType.LOGGED_ON).andExpect(status().isOk());

    // Act
    mvc.perform(
            delete(
                    LINKS_URI + "/{linkedAssetId}/{linkType}",
                    account.getId(),
                    host.getId(),
                    "LOGGED_ON")
                .with(csrf()))
        .andExpect(status().isOk());

    // Assert
    entityManager.flush();
    assertThat(storedLinks(account.getId())).isZero();
    mvc.perform(
            delete(
                    LINKS_URI + "/{linkedAssetId}/{linkType}",
                    account.getId(),
                    host.getId(),
                    "LOGGED_ON")
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  private Endpoint endpoint(String prefix) {
    return endpointComposer
        .forEndpoint(EndpointFixture.createEndpoint(prefix + UUID.randomUUID()))
        .persist()
        .get();
  }

  private ResultActions addLink(String assetId, String linkedAssetId, AssetLinkType type)
      throws Exception {
    return mvc.perform(
        post(LINKS_URI, assetId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(new AssetLinkInput(linkedAssetId, type))));
  }

  private int storedLinks(String assetId) {
    entityManager.flush();
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM assets_links WHERE asset_id = ?", Integer.class, assetId);
    return count == null ? 0 : count;
  }
}
