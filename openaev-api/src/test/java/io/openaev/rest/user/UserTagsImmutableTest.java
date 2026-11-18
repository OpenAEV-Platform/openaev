package io.openaev.rest.user;

import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tags of a saved user are never written through the collection of the entity: it only holds
 * the tags of the scope of its transaction, and a flush that finds it changed deletes the rows of
 * the user it does not hold, the links to the tags out of scope included. {@code User.tags} is
 * {@code @Immutable}, so a change of the loaded collection in place is refused at flush and the
 * links stay as they are. Replacing the collection is not refused by Hibernate and would delete the
 * links of the user, so {@code setTags} refuses a saved user and is only for users that are not
 * saved yet.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tags of a managed user")
class UserTagsImmutableTest extends IntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private UserService userService;

  private enum Change {
    CLEAR_IN_PLACE((user, other) -> user.getTags().clear()),
    ADD_IN_PLACE((user, other) -> user.getTags().add(other));

    private final BiConsumer<User, Tag> apply;

    Change(BiConsumer<User, Tag> apply) {
      this.apply = apply;
    }
  }

  /** A saved user holding one tag, loaded again so that its collection is the managed one. */
  private User managedUserHolding(Tag tag) {
    User user = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    entityManager.flush();
    entityManager
        .createNativeQuery("INSERT INTO users_tags (user_id, tag_id) VALUES (:userId, :tagId)")
        .setParameter("userId", user.getId())
        .setParameter("tagId", tag.getId())
        .executeUpdate();
    entityManager.clear();
    User managed = userRepository.findById(user.getId()).orElseThrow();
    assertThat(managed.getTags()).extracting(Tag::getId).containsExactly(tag.getId());
    return managed;
  }

  @SuppressWarnings("unchecked")
  private List<String> linksOf(User user) {
    return entityManager
        .createNativeQuery("SELECT tag_id FROM users_tags WHERE user_id = :userId")
        .setParameter("userId", user.getId())
        .getResultList();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Change.class)
  @DisplayName("given_changeOfTheLoadedTags_should_beRefusedAtFlushAndKeepTheLinks")
  void given_changeOfTheLoadedTags_should_beRefusedAtFlushAndKeepTheLinks(Change change) {
    // -- PREPARE --
    Tag tag = tagRepository.save(getTagWithText("immutable-" + UUID.randomUUID()));
    Tag other = tagRepository.save(getTagWithText("immutable-other-" + UUID.randomUUID()));
    User managed = managedUserHolding(tag);

    // -- EXECUTE --
    change.apply.accept(managed, other);

    // -- ASSERT --
    assertThatThrownBy(() -> entityManager.flush())
        .satisfies(
            refusal ->
                assertThat(ExceptionUtils.getStackTrace(refusal).toLowerCase())
                    .contains("immutable collection"));
    entityManager.clear();
    assertThat(linksOf(managed)).containsExactly(tag.getId());
  }

  @Test
  @DisplayName("given_setTagsOnASavedUser_should_refuseAndKeepTheLinks")
  void given_setTagsOnASavedUser_should_refuseAndKeepTheLinks() {
    // -- PREPARE --
    Tag tag = tagRepository.save(getTagWithText("immutable-" + UUID.randomUUID()));
    User managed = managedUserHolding(tag);

    // -- EXECUTE --
    assertThatThrownBy(() -> managed.setTags(new HashSet<>()))
        // -- ASSERT --
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("replaceTags");
    entityManager.flush();
    entityManager.clear();
    assertThat(linksOf(managed)).containsExactly(tag.getId());
  }

  @Test
  @DisplayName("given_setTagsOnANewUser_should_linkThemWhenSaved")
  void given_setTagsOnANewUser_should_linkThemWhenSaved() {
    // -- PREPARE --
    Tag tag = tagRepository.save(getTagWithText("immutable-" + UUID.randomUUID()));
    User user = UserFixture.getUserWithDefaultEmail();

    // -- EXECUTE --
    user.setTags(new HashSet<>(Set.of(tag)));
    User saved = userRepository.save(user);

    // -- ASSERT --
    entityManager.flush();
    assertThat(linksOf(saved)).containsExactly(tag.getId());
  }

  @Test
  @DisplayName("given_userHoldingATag_should_beDeletedThroughTheService")
  void given_userHoldingATag_should_beDeletedThroughTheService() {
    // -- PREPARE --
    Tag tag = tagRepository.save(getTagWithText("immutable-" + UUID.randomUUID()));
    User managed = managedUserHolding(tag);

    // -- EXECUTE --
    userService.delete(managed.getId());

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    assertThat(userRepository.findById(managed.getId())).isEmpty();
    assertThat(linksOf(managed)).isEmpty();
  }
}
