package io.openaev.rest.user;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.player.PlayerOutput;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlayerServiceIntegrationTest extends IntegrationTest {

  @Autowired private PlayerService playerService;
  @Autowired private UserRepository userRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private TagRepository tagRepository;

  @Test
  @DisplayName(
      "Given users with organization and tags, playerPagination should not fail on GROUP BY")
  void givenUsersWithOrganizationAndTags_playerPagination_shouldReturnResults() {
    // PREPARE
    Organization organization =
        organizationRepository.save(OrganizationFixture.createOrganization());
    Tag tag1 = tagRepository.save(TagFixture.getTagWithText("tag1"));
    Tag tag2 = tagRepository.save(TagFixture.getTagWithText("tag2"));

    User user = UserFixture.getUser("John", "Doe", "player@test.com");
    user.setOrganization(organization);
    user.setTags(Set.of(tag1, tag2));
    userRepository.save(user);

    User userWithoutOrg = UserFixture.getUser("Jane", "Doe", "noorg@test.com");
    userWithoutOrg.setTags(Set.of(tag1));
    userRepository.save(userWithoutOrg);

    SearchPaginationInput input = new SearchPaginationInput();
    input.setPage(0);
    input.setSize(10);
    input.setTextSearch("");
    input.setSorts(List.of(new SortField("user_email", null, null)));

    // EXECUTE
    Page<PlayerOutput> result = playerService.playerPagination(input);

    // ASSERT
    Assertions.assertNotNull(result);

    // 3 expected cause 1 default user create
    Assertions.assertEquals(3, result.getTotalElements());

    PlayerOutput playerWithOrg =
        result.getContent().stream()
            .filter(p -> p.getEmail().equals("player@test.com"))
            .findFirst()
            .orElseThrow();

    Assertions.assertEquals(organization.getId(), playerWithOrg.getOrganization());
    Assertions.assertEquals(2, playerWithOrg.getTags().size());
  }

  @Test
  @DisplayName(
      "Given user with tags but no organization, playerPagination should handle null organization")
  void givenUserWithTagsButNoOrganization_playerPagination_shouldReturnNullOrganization() {
    // Given
    Tag tag = new Tag();
    tag.setName("solo-tag");
    tagRepository.save(tag);

    User user = new User();
    user.setEmail("solo@test.com");
    user.setFirstname("Solo");
    user.setLastname("Player");
    user.setTags(Set.of(tag));
    userRepository.save(user);

    SearchPaginationInput input = new SearchPaginationInput();
    input.setPage(0);
    input.setSize(10);
    input.setTextSearch("");
    input.setSorts(List.of(new SortField("user_email", null, null)));

    // When
    Page<PlayerOutput> result = playerService.playerPagination(input);

    // Then
    Assertions.assertNotNull(result);
    PlayerOutput player =
        result.getContent().stream()
            .filter(p -> p.getEmail().equals("solo@test.com"))
            .findFirst()
            .orElseThrow();

    Assertions.assertNull(player.getOrganization());
    Assertions.assertEquals(1, player.getTags().size());
  }
}
