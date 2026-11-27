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

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.asset.dto.AssetOwnerInput;
import io.openaev.database.model.AssetOwnerRole;
import io.openaev.database.model.AssetRelationSource;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
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

/** {@code /api/assets/{id}/owners} (#9725): people and teams owning or managing an asset. */
@Transactional
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,EXPLORATION_GRAPH")
@DisplayName("Asset owners API")
class AssetOwnerApiTest extends IntegrationTest {

  private static final String OWNERS_URI =
      TENANT_ASSET_URI.replace("{tenantId}", DEFAULT_TENANT_UUID) + "/{assetId}/owners";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private TenantRepository tenantRepository;
  @PersistenceContext private EntityManager entityManager;

  private JdbcTemplate jdbc;
  private Endpoint server;
  private User person;
  private Team team;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    endpointComposer.reset();
    server =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint("owners-server-" + UUID.randomUUID()))
            .persist()
            .get();
    person = UserFixture.getUser("Ada", "Owner", "owner-" + UUID.randomUUID() + "@filigran.io");
    entityManager.persist(person);
    team = TeamFixture.createTeamWithName(null);
    entityManager.persist(team);
    entityManager.flush();
    tenantRepository.addUserToTenant(person.getId(), DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName("given a person and a team, should store both owners once and list them")
  void given_personAndTeam_should_storeAndListOwners() throws Exception {
    // Act
    addOwner(new AssetOwnerInput(person.getId(), null, AssetOwnerRole.OWNED_BY, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_owner_kind").value("USER"))
        .andExpect(jsonPath("$.asset_owner_name").value("Ada Owner"))
        .andExpect(jsonPath("$.asset_owner_role").value("OWNED_BY"))
        .andExpect(jsonPath("$.asset_owner_source").value("MANUAL"));
    addOwner(new AssetOwnerInput(person.getId(), null, AssetOwnerRole.OWNED_BY, null))
        .andExpect(status().isOk());
    addOwner(
            new AssetOwnerInput(
                null, team.getId(), AssetOwnerRole.MANAGED_BY, AssetRelationSource.SYNC))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_owner_kind").value("TEAM"))
        .andExpect(jsonPath("$.asset_owner_source").value("SYNC"));

    // Assert
    assertThat(storedOwners()).isEqualTo(2);
    mvc.perform(get(OWNERS_URI, server.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].asset_owner_user_id").value(person.getId()))
        .andExpect(jsonPath("$[1].asset_owner_team_id").value(team.getId()));
    mvc.perform(get("/api/users/{userId}/owned-assets", person.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].asset_id").value(server.getId()));
    mvc.perform(get("/api/teams/{teamId}/owned-assets", team.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].asset_owner_role").value("MANAGED_BY"));
  }

  @Test
  @DisplayName("given neither or both of a person and a team, should refuse with 400")
  void given_notExactlyOneOwner_should_refuse() throws Exception {
    addOwner(new AssetOwnerInput(null, null, AssetOwnerRole.OWNED_BY, null))
        .andExpect(status().isBadRequest());
    addOwner(new AssetOwnerInput(person.getId(), team.getId(), AssetOwnerRole.OWNED_BY, null))
        .andExpect(status().isBadRequest());
    assertThat(storedOwners()).isZero();
  }

  @Test
  @DisplayName("given a person outside the asset's tenant, should refuse with 400")
  void given_personOutsideTenant_should_refuse() throws Exception {
    // Arrange
    User outsider = UserFixture.getUser("Out", "Sider", "out-" + UUID.randomUUID() + "@x.io");
    entityManager.persist(outsider);
    entityManager.flush();

    // Act and assert
    addOwner(new AssetOwnerInput(outsider.getId(), null, AssetOwnerRole.OWNED_BY, null))
        .andExpect(status().isBadRequest());
    assertThat(storedOwners()).isZero();
  }

  @Test
  @DisplayName("given an unknown team or asset, should answer not found")
  void given_unknownTeamOrAsset_should_answerNotFound() throws Exception {
    addOwner(
            new AssetOwnerInput(
                null, UUID.randomUUID().toString(), AssetOwnerRole.MANAGED_BY, null))
        .andExpect(status().isNotFound());
    mvc.perform(get(OWNERS_URI, UUID.randomUUID().toString())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given a stored owner, should remove it, then answer not found on a second delete")
  void given_storedOwner_should_deleteIt() throws Exception {
    // Arrange
    String body =
        addOwner(new AssetOwnerInput(person.getId(), null, AssetOwnerRole.OWNED_BY, null))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ownerId = JsonPath.read(body, "$.asset_owner_id");

    // Act and assert
    mvc.perform(delete(OWNERS_URI + "/{ownerId}", server.getId(), ownerId).with(csrf()))
        .andExpect(status().isOk());
    assertThat(storedOwners()).isZero();
    mvc.perform(delete(OWNERS_URI + "/{ownerId}", server.getId(), ownerId).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given an owning team, deleting the team should delete the owner row")
  void given_owningTeam_should_cascadeOnTeamDeletion() throws Exception {
    // Arrange
    addOwner(new AssetOwnerInput(null, team.getId(), AssetOwnerRole.MANAGED_BY, null))
        .andExpect(status().isOk());
    assertThat(storedOwners()).isEqualTo(1);

    // Act
    jdbc.update("DELETE FROM teams WHERE team_id = ?", team.getId());

    // Assert
    assertThat(storedOwners()).isZero();
  }

  private ResultActions addOwner(AssetOwnerInput input) throws Exception {
    return mvc.perform(
        post(OWNERS_URI, server.getId())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(input)));
  }

  private int storedOwners() {
    entityManager.flush();
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM assets_owners WHERE asset_id = ?", Integer.class, server.getId());
    return count == null ? 0 : count;
  }
}
