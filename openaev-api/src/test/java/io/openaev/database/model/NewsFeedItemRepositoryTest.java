package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.NewsFeedItemRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.NewsFeedUtils;
import io.openaev.utils.fixtures.NewsFeedItemFixture;
import io.openaev.utils.fixtures.UserFixture;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
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
      assertThat(NewsFeedUtils.fromHubValue(reloaded.getType())).isEmpty();
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
  @DisplayName("Rows of a Hub item")
  class RowsOfAHubItem {

    @Test
    @DisplayName("Given rows of several users and items, should page the ids of one Hub item")
    void given_rowsOfSeveralUsersAndItems_should_pageIdsOfOneHubItem() {
      // Arrange
      List<String> ids =
          IntStream.range(0, 3)
              .mapToObj(
                  i ->
                      newsFeedItemRepository
                          .save(NewsFeedItemFixture.createNewsFeedItem(createUser(), "hub-1"))
                          .getId())
              .sorted()
              .toList();
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(createUser(), "hub-2"));
      entityManager.flush();

      // Act
      List<String> firstPage =
          newsFeedItemRepository.findIdsByHubItemIdAfter("hub-1", "", PageRequest.ofSize(2));
      List<String> secondPage =
          newsFeedItemRepository.findIdsByHubItemIdAfter(
              "hub-1", firstPage.getLast(), PageRequest.ofSize(2));

      // Assert
      assertThat(firstPage).containsExactly(ids.get(0), ids.get(1));
      assertThat(secondPage).containsExactly(ids.get(2));
    }

    @Test
    @DisplayName("Given Hub items some of which are held, should tell which ones have rows")
    void given_hubItems_should_tellWhichOnesHaveRows() {
      // Arrange
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(createUser(), "hub-1"));
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(createUser(), "hub-1"));
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(createUser(), "hub-3"));
      entityManager.flush();

      // Act
      List<String> held =
          newsFeedItemRepository.findHubItemIdsWithRows(List.of("hub-1", "hub-2", "hub-3"));

      // Assert: once each, and none for the item nobody holds
      assertThat(held).containsExactlyInAnyOrder("hub-1", "hub-3");
    }

    @Test
    @DisplayName("Given ids, should load their rows with their user in fewer statements")
    void given_ids_should_loadRowsWithTheirUserInFewerStatements() {
      // Arrange
      User first = createUser();
      User second = createUser();
      NewsFeedItem firstItem =
          newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(first, "hub-1"));
      NewsFeedItem secondItem =
          newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(second, "hub-1"));
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(first, "hub-2"));
      entityManager.flush();
      List<String> ids = List.of(firstItem.getId(), secondItem.getId());
      Statistics stats =
          entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
      // The statistics belong to the session factory shared by every test: put them back as found
      boolean statisticsWereEnabled = stats.isStatisticsEnabled();
      stats.setStatisticsEnabled(true);
      long withoutJoinFetch;
      long withJoinFetch;
      List<NewsFeedItem> items;
      try {
        // Act: the same rows without the join fetch, the users being EAGER, load them afterwards
        entityManager.clear();
        stats.clear();
        entityManager
            .createQuery("select n from NewsFeedItem n where n.id in :ids", NewsFeedItem.class)
            .setParameter("ids", ids)
            .getResultList();
        withoutJoinFetch = stats.getPrepareStatementCount();
        entityManager.clear();
        stats.clear();
        items = newsFeedItemRepository.findAllWithUserByIdIn(ids);
        withJoinFetch = stats.getPrepareStatementCount();
      } finally {
        stats.setStatisticsEnabled(statisticsWereEnabled);
      }

      // Assert
      assertThat(items)
          .extracting(NewsFeedItem::getId, NewsFeedItem::getOwnerUserId)
          .containsExactlyInAnyOrder(
              tuple(firstItem.getId(), first.getId()), tuple(secondItem.getId(), second.getId()));
      assertThat(withJoinFetch)
          .as("the users come with the rows instead of in statements of their own")
          .isLessThan(withoutJoinFetch);
    }
  }

  @Nested
  @DisplayName("Old items")
  class OldItems {

    private static final Instant CUTOFF = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant OLD = Instant.parse("2025-12-01T00:00:00Z");
    private static final String BLOG_POST = NewsFeedType.BLOG_POST.name();

    private NewsFeedItem saveCreatedAt(NewsFeedItem item, Instant creationDate) {
      item.setCreationDate(creationDate);
      return newsFeedItemRepository.save(item);
    }

    @Test
    @DisplayName("Given old and recent rows of every kind, should page only the old non blog posts")
    void given_oldAndRecentRows_should_pageOnlyOldRowsThatAreNotBlogPosts() {
      // Arrange
      User user = createUser();
      NewsFeedItem oldResource =
          saveCreatedAt(NewsFeedItemFixture.createNewsFeedItem(user, "hub-old"), OLD);
      NewsFeedItem oldLegacyType =
          saveCreatedAt(NewsFeedItemFixture.createNewsFeedItem(user, "hub-legacy"), OLD);
      saveCreatedAt(
          NewsFeedItemFixture.createNewsFeedItem(user, "hub-recent"), CUTOFF.plusSeconds(1));
      saveCreatedAt(NewsFeedItemFixture.createNewsFeedItem(user, "hub-at-cutoff"), CUTOFF);
      saveCreatedAt(NewsFeedItemFixture.createBlogPost(user, "hub-old-blog"), OLD);
      entityManager.flush();
      entityManager
          .createNativeQuery(
              "update news_feed_items set news_feed_item_type = 'RESOURCE_OBSOLETE'"
                  + " where news_feed_item_id = :id")
          .setParameter("id", oldLegacyType.getId())
          .executeUpdate();
      entityManager.clear();

      // Act
      List<String> ids =
          newsFeedItemRepository.findIdsCreatedBeforeExcludingTypeAfter(
              CUTOFF, BLOG_POST, "", PageRequest.ofSize(10));

      // Assert: strictly before the cutoff, whatever the type but the blog post, in id order
      assertThat(ids)
          .containsExactlyElementsOf(
              Stream.of(oldResource.getId(), oldLegacyType.getId()).sorted().toList());
    }

    @Test
    @DisplayName("Given more old rows than a page, should page them by keyset")
    void given_moreOldRowsThanAPage_should_pageThemByKeyset() {
      // Arrange
      User user = createUser();
      List<String> ids =
          IntStream.range(0, 3)
              .mapToObj(
                  i ->
                      saveCreatedAt(NewsFeedItemFixture.createNewsFeedItem(user, "hub-" + i), OLD)
                          .getId())
              .sorted()
              .toList();
      entityManager.flush();

      // Act
      List<String> firstPage =
          newsFeedItemRepository.findIdsCreatedBeforeExcludingTypeAfter(
              CUTOFF, BLOG_POST, "", PageRequest.ofSize(2));
      List<String> secondPage =
          newsFeedItemRepository.findIdsCreatedBeforeExcludingTypeAfter(
              CUTOFF, BLOG_POST, firstPage.getLast(), PageRequest.ofSize(2));

      // Assert
      assertThat(firstPage).containsExactly(ids.get(0), ids.get(1));
      assertThat(secondPage).containsExactly(ids.get(2));
    }
  }

  @Nested
  @DisplayName("Concurrent updates")
  class ConcurrentUpdates {

    @Test
    @DisplayName("Given an item marked read meanwhile, updating its title should keep it read")
    void given_itemMarkedReadMeanwhile_should_keepItReadWhenTitleChanges() {
      // Arrange
      NewsFeedItem item =
          newsFeedItemRepository.saveAndFlush(
              NewsFeedItemFixture.createDefaultNewsFeedItem(createUser()));
      entityManager.clear();
      NewsFeedItem loaded = newsFeedItemRepository.findById(item.getId()).orElseThrow();
      // Another transaction marks the item read after the collector loaded it
      entityManager
          .createNativeQuery(
              "update news_feed_items set news_feed_item_is_read = true"
                  + " where news_feed_item_id = :id")
          .setParameter("id", item.getId())
          .executeUpdate();

      // Act
      loaded.setTitle("Renamed");
      entityManager.flush();
      entityManager.clear();

      // Assert: only the title column was written
      NewsFeedItem reloaded = newsFeedItemRepository.findById(item.getId()).orElseThrow();
      assertThat(reloaded.getTitle()).isEqualTo("Renamed");
      assertThat(reloaded.isRead()).isTrue();
    }
  }

  @Nested
  @DisplayName("Unread items")
  class UnreadItems {

    @Test
    @DisplayName("Given read and unread items of several users, should only find the unread ones")
    void given_itemsOfSeveralUsers_should_findOnlyUnreadItemsOfTheUser() {
      // Arrange
      User first = createUser();
      User second = createUser();
      NewsFeedItem unread =
          newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(first, "hub-1"));
      NewsFeedItem read = NewsFeedItemFixture.createNewsFeedItem(first, "hub-2");
      read.setRead(true);
      newsFeedItemRepository.save(read);
      newsFeedItemRepository.save(NewsFeedItemFixture.createNewsFeedItem(second, "hub-1"));
      entityManager.flush();
      entityManager.clear();

      // Act
      long count = newsFeedItemRepository.countByUserIdAndReadFalse(first.getId());
      List<NewsFeedItem> items = newsFeedItemRepository.findAllByUserIdAndReadFalse(first.getId());

      // Assert
      assertThat(count).isEqualTo(1);
      assertThat(items).extracting(NewsFeedItem::getId).containsExactly(unread.getId());
    }
  }

  @Nested
  @DisplayName("Metadata")
  class Metadata {

    @Test
    @DisplayName("Given metadata, should persist them and replace them in full")
    void given_metadata_should_persistAndReplaceThemInFull() {
      // Arrange
      NewsFeedItem item =
          newsFeedItemRepository.save(NewsFeedItemFixture.createBlogPost(createUser(), "hub-1"));
      NewsFeedItem reloaded = reload(item);
      assertThat(reloaded.getMetadata())
          .containsOnly(Map.entry("description", "About hub-1"), Map.entry("read_minutes", "7"));

      // Act
      reloaded.getMetadata().clear();
      reloaded.getMetadata().put("image_url", "https://img.test/1.png");

      // Assert
      assertThat(reload(reloaded).getMetadata())
          .containsOnly(Map.entry("image_url", "https://img.test/1.png"));
    }

    @Test
    @DisplayName("Given rows with metadata, should load the metadata of all of them at once")
    void given_rowsWithMetadata_should_loadTheirMetadataAtOnce() {
      // Arrange
      User user = createUser();
      List<String> ids =
          IntStream.range(0, 3)
              .mapToObj(
                  i ->
                      newsFeedItemRepository
                          .save(NewsFeedItemFixture.createBlogPost(user, "hub-" + i))
                          .getId())
              .toList();
      entityManager.flush();
      entityManager.clear();
      List<NewsFeedItem> items = newsFeedItemRepository.findAllWithUserByIdIn(ids);
      Statistics stats =
          entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
      // The statistics belong to the session factory shared by every test: put them back as found
      boolean statisticsWereEnabled = stats.isStatisticsEnabled();
      stats.setStatisticsEnabled(true);
      long statements;
      try {
        stats.clear();

        // Act
        items.forEach(item -> assertThat(item.getMetadata()).hasSize(2));
        statements = stats.getPrepareStatementCount();
      } finally {
        stats.setStatisticsEnabled(statisticsWereEnabled);
      }

      // Assert
      assertThat(statements).as("one statement for the metadata of every row").isEqualTo(1);
    }

    @Test
    @DisplayName("Given an item with metadata, should delete its metadata with it")
    void given_itemWithMetadata_should_deleteItsMetadataWithIt() {
      // Arrange
      NewsFeedItem item =
          newsFeedItemRepository.saveAndFlush(
              NewsFeedItemFixture.createBlogPost(createUser(), "hub-1"));

      // Act
      newsFeedItemRepository.delete(item);
      entityManager.flush();

      // Assert
      Number left =
          (Number)
              entityManager
                  .createNativeQuery(
                      "SELECT count(*) FROM news_feed_items_metadata WHERE news_feed_item_id = ?1")
                  .setParameter(1, item.getId())
                  .getSingleResult();
      assertThat(left.longValue()).isZero();
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
      user.setUnsubscribedNewsFeedTypes(new String[] {NewsFeedUtils.ALL_TYPES});
      userRepository.save(user);

      // Act
      entityManager.flush();
      entityManager.clear();
      User reloaded = userRepository.findById(user.getId()).orElseThrow();

      // Assert
      assertThat(reloaded.getUnsubscribedNewsFeedTypes()).containsExactly(NewsFeedUtils.ALL_TYPES);
    }
  }
}
