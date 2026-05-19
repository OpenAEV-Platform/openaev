package io.openaev.rest.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PlayerServiceTest extends IntegrationTest {

  @Autowired private PlayerService playerService;
  @Autowired private UserComposer userComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private OrganizationComposer organizationComposer;

  @BeforeEach
  void setUp() {
    userComposer.reset();
    tagComposer.reset();
    organizationComposer.reset();
  }

  @Nested
  @DisplayName("Upsert player")
  class UpsertPlayer {

    @Test
    @DisplayName("Given identical input should not update")
    void given_identicalInput_should_notUpdate() {
      // Arrange
      User existingUser = UserFixture.getUserWithDefaultEmail();
      existingUser.setFirstname("newUser");
      userComposer.forUser(existingUser).persist();

      PlayerInput playerInput = new PlayerInput();
      playerInput.setFirstname("newUser");
      playerInput.setEmail(existingUser.getEmail());

      // Act
      User result = playerService.upsertPlayer(playerInput);

      // Assert
      assertThat(result.getId()).isEqualTo(existingUser.getId());
      assertThat(result.getFirstname()).isEqualTo("newUser");
    }

    @Test
    @DisplayName("Given different firstname should update")
    void given_differentFirstname_should_update() {
      // Arrange
      User existingUser = UserFixture.getUserWithDefaultEmail();
      existingUser.setFirstname("oldName");
      userComposer.forUser(existingUser).persist();

      PlayerInput playerInput = new PlayerInput();
      playerInput.setFirstname("newName");
      playerInput.setEmail(existingUser.getEmail());

      // Act
      User result = playerService.upsertPlayer(playerInput);

      // Assert
      assertThat(result.getFirstname()).isEqualTo("newName");
    }

    @Test
    @DisplayName("Given different tags should update")
    void given_differentTags_should_update() {
      // Arrange
      Tag tag = TagFixture.getTagWithText("playerTag");
      tagComposer.forTag(tag).persist();

      User existingUser = UserFixture.getUserWithDefaultEmail();
      userComposer.forUser(existingUser).persist();

      PlayerInput playerInput = new PlayerInput();
      playerInput.setEmail(existingUser.getEmail());
      playerInput.setTagIds(List.of(tag.getId()));

      // Act
      User result = playerService.upsertPlayer(playerInput);

      // Assert
      assertThat(result.getTags()).extracting(Tag::getId).contains(tag.getId());
    }

    @Test
    @DisplayName("Given identical tags should not update")
    void given_identicalTags_should_notUpdate() {
      // Arrange
      Tag tag = TagFixture.getTagWithText("sameTag");
      tagComposer.forTag(tag).persist();

      User existingUser = UserFixture.getUserWithDefaultEmail();
      userComposer.forUser(existingUser).withTag(tagComposer.forTag(tag)).persist();

      PlayerInput playerInput = new PlayerInput();
      playerInput.setEmail(existingUser.getEmail());
      playerInput.setTagIds(List.of(tag.getId()));

      // Act
      User result = playerService.upsertPlayer(playerInput);

      // Assert
      assertThat(result.getId()).isEqualTo(existingUser.getId());
    }

    @Test
    @DisplayName("Given different organization should update")
    void given_differentOrganization_should_update() {
      // Arrange
      Organization oldOrg = OrganizationFixture.createDefaultOrganisation();
      organizationComposer.forOrganization(oldOrg).persist();

      Organization newOrg = OrganizationFixture.createDefaultOrganisation();
      organizationComposer.forOrganization(newOrg).persist();

      User existingUser = UserFixture.getUserWithDefaultEmail();
      existingUser.setOrganization(oldOrg);
      userComposer.forUser(existingUser).persist();

      PlayerInput playerInput = new PlayerInput();
      playerInput.setEmail(existingUser.getEmail());
      playerInput.setOrganizationId(newOrg.getId());

      // Act
      User result = playerService.upsertPlayer(playerInput);

      // Assert
      assertThat(result.getOrganization().getId()).isEqualTo(newOrg.getId());
    }
  }
}
