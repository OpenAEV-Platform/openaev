package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.NewsFeedItemRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.fixtures.NewsFeedItemFixture;
import io.openaev.utils.fixtures.UserFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("NewsFeedItem persistence tests")
class NewsFeedItemRepositoryTest extends IntegrationTest {

  @Autowired private NewsFeedItemRepository newsFeedItemRepository;
  @Autowired private UserRepository userRepository;

  private User createUser() {
    return userRepository.save(UserFixture.getUserWithDefaultEmail());
  }

  private NewsFeedItem reload(NewsFeedItem item) {
    entityManager.flush();
    entityManager.clear();
    return newsFeedItemRepository.findById(item.getId()).orElseThrow();
  }

  @Nested
  @DisplayName("Persistence")
  class Persistence {

    @Test
    @DisplayName("Given a news feed item, should persist all its fields")
    void given_newsFeedItem_should_persistAllFields() {
      // Arrange
      User user = createUser();
      NewsFeedItem item =
          newsFeedItemRepository.save(NewsFeedItemFixture.createDefaultNewsFeedItem(user));

      // Act
      NewsFeedItem reloaded = reload(item);

      // Assert
      assertThat(reloaded.getHubItemId()).isEqualTo(item.getHubItemId());
      assertThat(reloaded.getTitle()).isEqualTo(item.getTitle());
      assertThat(reloaded.getType()).isEqualTo("RESOURCE_SCENARIO");
      assertThat(reloaded.resolveType()).contains(NewsFeedType.RESOURCE_SCENARIO);
      assertThat(reloaded.getTags()).containsExactly("phishing", "ransomware");
      assertThat(reloaded.getUrlPath()).isEqualTo("/scenarios/" + item.getHubItemId());
      assertThat(reloaded.getCreationDate()).isEqualTo(item.getCreationDate());
      assertThat(reloaded.isRead()).isFalse();
      assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Given a news feed item, should be owned by its user")
    void given_newsFeedItem_should_beOwnedByItsUser() {
      // Arrange
      User user = createUser();

      // Act
      NewsFeedItem item =
          newsFeedItemRepository.save(NewsFeedItemFixture.createDefaultNewsFeedItem(user));

      // Assert
      assertThat(item.getOwnerUserId()).isEqualTo(user.getId());
      assertThat(item.getResourceType()).isEqualTo(ResourceType.NEWS_FEED);
    }
  }

  @Nested
  @DisplayName("Type")
  class Type {

    @Test
    @DisplayName("Given a stored type OpenAEV no longer knows, should still load the item")
    void given_obsoleteStoredType_should_loadItemWithoutKnownType() {
      // Arrange
      NewsFeedItem item =
          newsFeedItemRepository.saveAndFlush(
              NewsFeedItemFixture.createDefaultNewsFeedItem(createUser()));
      entityManager
          .createNativeQuery(
              "update news_feed_items set news_feed_item_type = 'RESOURCE_OBSOLETE'"
                  + " where news_feed_item_id = :id")
          .setParameter("id", item.getId())
          .executeUpdate();
      entityManager.clear();

      // Act
      NewsFeedItem reloaded = newsFeedItemRepository.findById(item.getId()).orElseThrow();

      // Assert
      assertThat(reloaded.getType()).isEqualTo("RESOURCE_OBSOLETE");
      assertThat(reloaded.resolveType()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Keys lookup")
  class KeysLookup {

    @Test
    @DisplayName("Given items of several users, should return only the keys of the given users")
    void given_itemsOfSeveralUsers_should_returnKeysOfGivenUsersOnly() {
      // Arrange
      User first = createUser();
      User second = createUser();
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(first, "hub-1"));
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(first, "hub-2"));
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(first, "hub-3"));
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(second, "hub-1"));
      entityManager.flush();

      // Act
      var keys = newsFeedItemRepository.rawKeys(List.of(first.getId()), List.of("hub-1", "hub-2"));

      // Assert
      assertThat(keys)
          .extracting(key -> key.getUserId() + "/" + key.getHubItemId())
          .containsExactlyInAnyOrder(first.getId() + "/hub-1", first.getId() + "/hub-2");
    }

    @Test
    @DisplayName("Given the schema, should index the news feed items by Hub item")
    void given_schema_should_indexItemsByHubItem() {
      // Act
      Number indexes =
          (Number)
              entityManager
                  .createNativeQuery(
                      "select count(*) from pg_indexes where tablename = 'news_feed_items'"
                          + " and indexname = 'idx_news_feed_items_hub_id'")
                  .getSingleResult();

      // Assert
      assertThat(indexes.intValue()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Uniqueness")
  class Uniqueness {

    @Test
    @DisplayName("Given the same Hub item twice for a user, should reject the duplicate")
    void given_sameHubItemTwiceForUser_should_rejectDuplicate() {
      // Arrange
      User user = createUser();
      newsFeedItemRepository.saveAndFlush(NewsFeedItemFixture.createNewsFeedItem(user, "hub-1"));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  newsFeedItemRepository.saveAndFlush(
                      NewsFeedItemFixture.createNewsFeedItem(user, "hub-1")))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Given the same Hub item for two users, should persist both")
    void given_sameHubItemForTwoUsers_should_persistBoth() {
      // Arrange
      User first = createUser();
      User second = createUser();

      // Act
      newsFeedItemRepository.saveAndFlush(NewsFeedItemFixture.createNewsFeedItem(first, "hub-1"));
      newsFeedItemRepository.saveAndFlush(NewsFeedItemFixture.createNewsFeedItem(second, "hub-1"));

      // Assert
      assertThat(newsFeedItemRepository.findAll())
          .filteredOn(item -> "hub-1".equals(item.getHubItemId()))
          .extracting(NewsFeedItem::getOwnerUserId)
          .containsExactlyInAnyOrder(first.getId(), second.getId());
    }
  }

  @Nested
  @DisplayName("User deletion")
  class UserDeletion {

    @Test
    @DisplayName("Given a user with news feed items, deleting the user should delete the items")
    void given_userWithItems_should_deleteItemsWithUser() {
      // Arrange
      User user = createUser();
      NewsFeedItem item =
          newsFeedItemRepository.saveAndFlush(NewsFeedItemFixture.createDefaultNewsFeedItem(user));
      entityManager.clear();

      // Act
      userRepository.deleteById(user.getId());
      userRepository.flush();

      // Assert
      assertThat(newsFeedItemRepository.existsById(item.getId())).isFalse();
    }
  }

  @Nested
  @DisplayName("User unsubscribed types")
  class UserUnsubscribedTypes {

    @Test
    @DisplayName("Given a new user, should have no unsubscribed type")
    void given_newUser_should_haveNoUnsubscribedType() {
      // Arrange
      User user = createUser();

      // Act
      entityManager.flush();
      entityManager.clear();
      User reloaded = userRepository.findById(user.getId()).orElseThrow();

      // Assert
      assertThat(reloaded.getUnsubscribedNewsFeedTypes()).isEmpty();
    }

    @Test
    @DisplayName("Given unsubscribed types, should persist them")
    void given_unsubscribedTypes_should_persistThem() {
      // Arrange
      User user = createUser();
      user.setUnsubscribedNewsFeedTypes(new String[] {NewsFeedType.ALL_TYPES});
      userRepository.save(user);

      // Act
      entityManager.flush();
      entityManager.clear();
      User reloaded = userRepository.findById(user.getId()).orElseThrow();

      // Assert
      assertThat(reloaded.getUnsubscribedNewsFeedTypes()).containsExactly(NewsFeedType.ALL_TYPES);
    }
  }
}
