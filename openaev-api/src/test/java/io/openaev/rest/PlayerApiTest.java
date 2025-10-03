package io.openaev.rest;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.utils.JsonUtils.asJsonString;
import static io.openaev.utils.fixtures.PlayerFixture.PLAYER_FIXTURE_FIRSTNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class PlayerApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Value("${openbas.admin.email:${openaev.admin.email:#{null}}}")
  private String adminEmail;

  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private UserRepository userRepository;

  @DisplayName("Given valid player input, should create a player successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validPlayerInput_should_createPlayerSuccessfully() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();

    // -- EXECUTE --
    String response =
        mvc.perform(
                post(PLAYER_URI)
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(PLAYER_FIXTURE_FIRSTNAME, JsonPath.read(response, "$.user_firstname"));
    assertEquals(playerInput.getTagIds().getFirst(), JsonPath.read(response, "$.user_tags[0]"));
    assertEquals(playerInput.getOrganizationId(), JsonPath.read(response, "$.user_organization"));
  }

  @DisplayName("Given invalid email in player input, should throw exceptions")
  @Test
  @WithMockUser(isAdmin = true)
  void given_invalidEmailInPlayerInput_should_throwExceptions() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = new PlayerInput();
    playerInput.setEmail("email");

    // -- EXECUTE --
    String response =
        mvc.perform(
                post(PLAYER_URI)
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertTrue(response.contains(EMAIL_FORMAT));
  }

  @DisplayName("Given restricted user, should not allow creation of player")
  @Test
  @WithMockUser
  void given_restrictedUser_should_notAllowPlayerCreation() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();

    // --EXECUTE--
    mvc.perform(
            post(PLAYER_URI)
                .content(asJsonString(playerInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @DisplayName("Given valid player input, should upsert player successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validPlayerInput_should_upsertPlayerSuccessfully() throws Exception {
    // --PREPARE--
    PlayerInput playerInput = buildPlayerInput();
    User user = new User();
    user.setUpdateAttributes(playerInput);
    userRepository.save(user);
    String newFirstname = "updatedFirstname";
    playerInput.setFirstname(newFirstname);

    // -- EXECUTE --
    String response =
        mvc.perform(
                post(PLAYER_URI + "/upsert")
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(newFirstname, JsonPath.read(response, "$.user_firstname"));
  }

  @DisplayName("Given non-existing player input, should upsert successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_nonExistingPlayerInput_should_upsertSuccessfully() throws Exception {
    // --PREPARE--
    PlayerInput playerInput = buildPlayerInput();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(PLAYER_URI + "/upsert")
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(PLAYER_FIXTURE_FIRSTNAME, JsonPath.read(response, "$.user_firstname"));
  }

  @DisplayName("Given valid player ID and input, should update player successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validPlayerIdAndInput_should_updatePlayerSuccessfully() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();
    User user = new User();
    user.setUpdateAttributes(playerInput);
    userRepository.save(user);
    String newFirstname = "updatedFirstname";
    playerInput.setFirstname(newFirstname);

    // --EXECUTE--
    String response =
        mvc.perform(
                put(PLAYER_URI + "/" + user.getId())
                    .content(asJsonString(playerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals("updatedFirstname", JsonPath.read(response, "$.user_firstname"));
  }

  @DisplayName("Given restricted user, should not allow updating a player")
  @Test
  @WithMockUser
  void given_restrictedUser_should_notAllowPlayerUpdate() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();
    User user = userRepository.findByEmailIgnoreCase(adminEmail).orElseThrow();

    // -- EXECUTE --
    mvc.perform(
            put(PLAYER_URI + "/" + user.getId())
                .content(asJsonString(playerInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @DisplayName("Given valid player ID, should delete player successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validPlayerId_should_deletePlayerSuccessfully() throws Exception {
    // -- PREPARE --
    PlayerInput playerInput = buildPlayerInput();
    User user = new User();
    user.setUpdateAttributes(playerInput);
    user = userRepository.save(user);

    // -- EXECUTE --
    mvc.perform(
            delete(PLAYER_URI + "/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // -- ASSERT --
    assertTrue(this.userRepository.findById(user.getId()).isEmpty());
  }

  @DisplayName("Given no existing player ID, should throw an exception")
  @Test
  @WithMockUser(isAdmin = true)
  void given_notExistingAssetGroup_should_throwAnException() throws Exception {
    // -- PREPARE --
    String nonexistentAssetGroupId = "nonexistent-id";

    // --EXECUTE--
    mvc.perform(
        delete(PLAYER_URI + "/" + nonexistentAssetGroupId)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON));
  }

  // -- PRIVATE --

  private PlayerInput buildPlayerInput() {
    Organization organization =
        organizationRepository.save(OrganizationFixture.createOrganization());
    Tag tag = tagRepository.save(TagFixture.getTag());
    PlayerInput player = PlayerFixture.createPlayerInput();
    player.setOrganizationId(organization.getId());
    player.setTagIds(List.of(tag.getId()));
    return player;
  }
}
