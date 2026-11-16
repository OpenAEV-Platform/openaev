package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.model.NewsFeedType;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawNewsFeedItemKey;
import io.openaev.database.raw.RawNewsFeedRecipient;
import io.openaev.database.repository.NewsFeedItemRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.NewsFeedUtils;
import io.openaev.utils.ReferenceResolver;
import io.openaev.utils.fixtures.NewsFeedItemFixture;
import io.openaev.xtmhub.ProvisionedNewsFeedItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewsFeedItemService tests")
class NewsFeedItemServiceTest {

  private static final Set<String> TENANTS = Set.of("tenant-a");
  private static final Instant CREATION_DATE = Instant.parse("2026-10-20T10:15:30Z");

  @Mock private NewsFeedItemRepository newsFeedItemRepository;
  @Mock private UserService userService;
  @Mock private ReferenceResolver referenceResolver;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private PreviewFeatureService previewFeatureService;

  private NewsFeedItemService service;

  /** The Hub items some user holds a row of, as the lookup of the items with rows finds them. */
  private final Set<String> hubItemIdsWithRows = new HashSet<>();

  @BeforeEach
  void setUp() {
    service =
        new NewsFeedItemService(
            newsFeedItemRepository,
            userService,
            referenceResolver,
            tenantTx,
            previewFeatureService);
    // runs the work inline: the transaction itself is covered by the integration tests
    lenient()
        .doAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get())
        .when(tenantTx)
        .execute(any(TxCtx.class), ArgumentMatchers.<Supplier<Object>>any());
    hubItemIdsWithRows.clear();
    lenient()
        .when(newsFeedItemRepository.findHubItemIdsWithRows(anyCollection()))
        .thenAnswer(
            invocation ->
                invocation.<Collection<String>>getArgument(0).stream()
                    .filter(hubItemIdsWithRows::contains)
                    .toList());
    lenient()
        .when(referenceResolver.resolve(any(), ArgumentMatchers.eq(User.class)))
        .thenReturn(new User());
  }

  private static RawNewsFeedRecipient recipient(String id, String... unsubscribedTypes) {
    return new RawNewsFeedRecipient() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public String[] getUnsubscribedTypes() {
        return unsubscribedTypes;
      }
    };
  }

  private static RawNewsFeedItemKey key(String userId, String hubItemId) {
    return new RawNewsFeedItemKey() {
      @Override
      public String getUserId() {
        return userId;
      }

      @Override
      public String getHubItemId() {
        return hubItemId;
      }
    };
  }

  private static List<RawNewsFeedRecipient> recipients(int count) {
    return IntStream.range(0, count).mapToObj(i -> recipient("user-" + i)).toList();
  }

  private static ProvisionedNewsFeedItem item(String id, String title, String type) {
    return new ProvisionedNewsFeedItem(id, title, type, List.of(), null, CREATION_DATE, false);
  }

  private static List<ProvisionedNewsFeedItem> items(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> item("hub-" + i, "Title " + i, "RESOURCE_SCENARIO"))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<List<NewsFeedItem>> savedBatches(int expectedBatches) {
    ArgumentCaptor<List<NewsFeedItem>> captor = ArgumentCaptor.forClass(List.class);
    verify(newsFeedItemRepository, times(expectedBatches)).saveAllAndFlush(captor.capture());
    return captor.getAllValues();
  }

  @Test
  @DisplayName("Given no recipient tenant, should create nothing and not look for users")
  void given_noRecipientTenant_should_createNothing() {
    // Act
    service.applyProvisionedItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), Set.of());

    // Assert
    verifyNoInteractions(userService);
    verify(newsFeedItemRepository, never()).saveAllAndFlush(anyCollection());
  }

  @Test
  @DisplayName("Given the recipient tenants, should look for the users of these tenants")
  void given_recipientTenants_should_lookForTheirUsers() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS)).thenReturn(recipients(2));

    // Act
    service.applyProvisionedItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

    // Assert
    assertThat(savedBatches(1).getFirst()).hasSize(2);
  }

  @Test
  @DisplayName("Given more users than a chunk, should save them by chunk")
  void given_moreUsersThanAChunk_should_saveByChunk() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS))
        .thenReturn(recipients(NewsFeedItemService.USER_CHUNK_SIZE + 1));

    // Act
    service.applyProvisionedItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

    // Assert
    assertThat(savedBatches(2))
        .extracting(List::size)
        .containsExactly(NewsFeedItemService.USER_CHUNK_SIZE, 1);
  }

  @Test
  @DisplayName("Given a chunk that fails, should still create the items of the next chunks")
  void given_failingChunk_should_stillCreateNextChunks() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS))
        .thenReturn(recipients(NewsFeedItemService.USER_CHUNK_SIZE + 1));
    when(newsFeedItemRepository.saveAllAndFlush(anyCollection()))
        .thenThrow(new IllegalStateException("boom"))
        .thenAnswer(invocation -> new ArrayList<>(invocation.<List<NewsFeedItem>>getArgument(0)));

    // Act
    service.applyProvisionedItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

    // Assert
    assertThat(savedBatches(2))
        .extracting(List::size)
        .containsExactly(NewsFeedItemService.USER_CHUNK_SIZE, 1);
  }

  @Test
  @DisplayName("Given more items than a chunk, should save them by chunk")
  void given_moreItemsThanAChunk_should_saveByChunk() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS)).thenReturn(recipients(1));

    // Act
    service.applyProvisionedItems(items(NewsFeedItemService.ITEM_CHUNK_SIZE + 1), TENANTS);

    // Assert
    assertThat(savedBatches(2))
        .extracting(List::size)
        .containsExactly(NewsFeedItemService.ITEM_CHUNK_SIZE, 1);
  }

  @Test
  @DisplayName("Given items created concurrently, should retry the chunk once")
  void given_itemsCreatedConcurrently_should_retryTheChunkOnce() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS)).thenReturn(recipients(2));
    when(newsFeedItemRepository.saveAllAndFlush(anyCollection()))
        .thenThrow(new DataIntegrityViolationException("duplicate key"))
        .thenAnswer(invocation -> new ArrayList<>(invocation.<List<NewsFeedItem>>getArgument(0)));

    // Act
    service.applyProvisionedItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

    // Assert: the retry looks the existing items up again
    assertThat(savedBatches(2)).extracting(List::size).containsExactly(2, 2);
    verify(newsFeedItemRepository, times(2)).rawKeys(anyCollection(), anyCollection());
  }

  @Test
  @DisplayName("Given a chunk that keeps being rejected, should not retry it more than once")
  void given_chunkKeepsBeingRejected_should_notRetryMoreThanOnce() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS)).thenReturn(recipients(1));
    when(newsFeedItemRepository.saveAllAndFlush(anyCollection()))
        .thenThrow(new DataIntegrityViolationException("duplicate key"));

    // Act
    service.applyProvisionedItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

    // Assert
    savedBatches(2);
  }

  @Test
  @DisplayName("Given the same item several times, should create it once per user")
  void given_sameItemSeveralTimes_should_createItOncePerUser() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS)).thenReturn(recipients(1));

    // Act
    service.applyProvisionedItems(
        List.of(
            item("hub-1", "First title", "RESOURCE_SCENARIO"),
            item("hub-1", "Second title", "RESOURCE_SCENARIO")),
        TENANTS);

    // Assert
    assertThat(savedBatches(1).getFirst())
        .extracting(NewsFeedItem::getHubItemId, NewsFeedItem::getTitle)
        .containsExactly(tuple("hub-1", "First title"));
  }

  @Test
  @DisplayName("Given unsubscribed and already served users, should create items for the others")
  void given_unsubscribedAndAlreadyServedUsers_should_createItemsForOthers() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS))
        .thenReturn(
            List.of(
                recipient("subscribed"),
                recipient("unsubscribed", "RESOURCE_SCENARIO"),
                recipient("unsubscribed-all", NewsFeedUtils.ALL_TYPES),
                recipient("served")));
    when(newsFeedItemRepository.rawKeys(anyCollection(), anyCollection()))
        .thenReturn(
            List.of(
                new RawNewsFeedItemKey() {
                  @Override
                  public String getUserId() {
                    return "served";
                  }

                  @Override
                  public String getHubItemId() {
                    return "hub-1";
                  }
                }));

    // Act
    service.applyProvisionedItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

    // Assert
    verify(referenceResolver, times(1)).resolve("subscribed", User.class);
    assertThat(savedBatches(1).getFirst()).hasSize(1);
  }

  @Test
  @DisplayName("Given deleted and unknown-type items, should create nothing")
  void given_deletedAndUnknownTypeItems_should_createNothing() {
    // Act
    service.applyProvisionedItems(
        List.of(
            new ProvisionedNewsFeedItem(
                "hub-deleted",
                "Deleted",
                "RESOURCE_SCENARIO",
                List.of(),
                null,
                CREATION_DATE,
                true),
            item("hub-unknown", "Unknown", "RESOURCE_PLAYBOOK")),
        TENANTS);

    // Assert
    verifyNoInteractions(userService);
    verify(newsFeedItemRepository, never()).saveAllAndFlush(anyCollection());
  }

  // -- Deleted and changed items --

  private static ProvisionedNewsFeedItem deletedItem(String id, String type) {
    return new ProvisionedNewsFeedItem(id, "Deleted", type, List.of(), null, CREATION_DATE, true);
  }

  private static NewsFeedItem row(String rowId, String hubItemId) {
    User owner = new User();
    owner.setId("owner-" + rowId);
    NewsFeedItem row = NewsFeedItemFixture.createNewsFeedItem(owner, hubItemId);
    row.setId(rowId);
    return row;
  }

  /** The rows a Hub item has, found in one chunk. */
  private void givenRows(String hubItemId, NewsFeedItem... rows) {
    hubItemIdsWithRows.add(hubItemId);
    List<String> ids = Arrays.stream(rows).map(NewsFeedItem::getId).toList();
    when(newsFeedItemRepository.findIdsByHubItemIdAfter(eq(hubItemId), eq(""), any(Pageable.class)))
        .thenReturn(ids);
    when(newsFeedItemRepository.findAllWithUserByIdIn(ids)).thenReturn(List.of(rows));
  }

  /** {@code count} rows of a Hub item, found by keyset chunks of {@link #USER_CHUNK_SIZE}. */
  private void givenManyRows(String hubItemId, int count) {
    hubItemIdsWithRows.add(hubItemId);
    List<String> ids = IntStream.range(0, count).mapToObj(i -> "row-%05d".formatted(i)).toList();
    when(newsFeedItemRepository.findIdsByHubItemIdAfter(
            eq(hubItemId), anyString(), any(Pageable.class)))
        .thenAnswer(
            invocation -> {
              String afterId = invocation.getArgument(1);
              return ids.stream()
                  .filter(id -> id.compareTo(afterId) > 0)
                  .limit(NewsFeedItemService.USER_CHUNK_SIZE)
                  .toList();
            });
    when(newsFeedItemRepository.findAllWithUserByIdIn(anyCollection()))
        .thenAnswer(
            invocation ->
                invocation.<Collection<String>>getArgument(0).stream()
                    .map(id -> row(id, hubItemId))
                    .toList());
  }

  @SuppressWarnings("unchecked")
  private List<List<NewsFeedItem>> deletedBatches(int expectedBatches) {
    ArgumentCaptor<List<NewsFeedItem>> captor = ArgumentCaptor.forClass(List.class);
    verify(newsFeedItemRepository, times(expectedBatches)).deleteAll(captor.capture());
    return captor.getAllValues();
  }

  @Nested
  @DisplayName("Deleted items")
  class DeletedItems {

    @Test
    @DisplayName("Given a deleted item, should delete its rows through the session")
    void given_deletedItem_should_deleteItsRows() {
      // Arrange
      NewsFeedItem first = row("row-1", "hub-1");
      NewsFeedItem second = row("row-2", "hub-1");
      givenRows("hub-1", first, second);

      // Act
      service.applyProvisionedItems(List.of(deletedItem("hub-1", "RESOURCE_SCENARIO")), TENANTS);

      // Assert: deleted one by one (listener chain), never in bulk
      assertThat(deletedBatches(1).getFirst()).containsExactly(first, second);
      verify(newsFeedItemRepository).flush();
      verify(newsFeedItemRepository, never()).deleteAllInBatch(any());
      verify(newsFeedItemRepository, never()).deleteAllByIdInBatch(any());
      verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Given a deleted item of a type OpenAEV does not know, should delete its rows")
    void given_deletedItemOfUnknownType_should_deleteItsRows() {
      // Arrange
      givenRows("hub-1", row("row-1", "hub-1"));

      // Act
      service.applyProvisionedItems(List.of(deletedItem("hub-1", "RESOURCE_PLAYBOOK")), TENANTS);

      // Assert
      assertThat(deletedBatches(1).getFirst()).hasSize(1);
    }

    @Test
    @DisplayName("Given an item deleted and also sent live, should delete it and not recreate it")
    void given_itemDeletedAndSentLive_should_deleteItWithoutCreatingIt() {
      // Arrange
      givenRows("hub-1", row("row-1", "hub-1"));

      // Act
      service.applyProvisionedItems(
          List.of(
              item("hub-1", "Title", "RESOURCE_SCENARIO"),
              deletedItem("hub-1", "RESOURCE_SCENARIO"),
              item("hub-1", "Title", "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert
      assertThat(deletedBatches(1).getFirst()).hasSize(1);
      verify(newsFeedItemRepository, never()).saveAllAndFlush(anyCollection());
      verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Given more rows than a chunk, should delete them by keyset chunk")
    void given_moreRowsThanAChunk_should_deleteByChunk() {
      // Arrange
      givenManyRows("hub-1", NewsFeedItemService.USER_CHUNK_SIZE + 1);

      // Act
      service.applyProvisionedItems(List.of(deletedItem("hub-1", "RESOURCE_SCENARIO")), TENANTS);

      // Assert
      assertThat(deletedBatches(2))
          .extracting(List::size)
          .containsExactly(NewsFeedItemService.USER_CHUNK_SIZE, 1);
    }

    @Test
    @DisplayName("Given a chunk that fails, should still delete the next chunk and the next item")
    void given_failingChunk_should_stillDeleteNextChunkAndNextItem() {
      // Arrange
      givenManyRows("hub-1", NewsFeedItemService.USER_CHUNK_SIZE + 1);
      givenRows("hub-2", row("row-other", "hub-2"));
      doThrow(new IllegalStateException("boom"))
          .doNothing()
          .when(newsFeedItemRepository)
          .deleteAll(anyCollection());

      // Act
      service.applyProvisionedItems(
          List.of(
              deletedItem("hub-1", "RESOURCE_SCENARIO"), deletedItem("hub-2", "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert: the failing first chunk of hub-1 is skipped, not the second, nor hub-2
      assertThat(deletedBatches(3))
          .extracting(List::size)
          .containsExactly(NewsFeedItemService.USER_CHUNK_SIZE, 1, 1);
    }

    @Test
    @DisplayName("Given rows deleted concurrently, should retry the chunk once")
    void given_rowsDeletedConcurrently_should_retryTheChunkOnce() {
      // Arrange
      givenRows("hub-1", row("row-1", "hub-1"));
      doThrow(new OptimisticLockingFailureException("already deleted"))
          .doNothing()
          .when(newsFeedItemRepository)
          .deleteAll(anyCollection());

      // Act
      service.applyProvisionedItems(List.of(deletedItem("hub-1", "RESOURCE_SCENARIO")), TENANTS);

      // Assert: the retry reads the rows again
      deletedBatches(2);
      verify(newsFeedItemRepository, times(2)).findAllWithUserByIdIn(anyCollection());
    }

    @Test
    @DisplayName("Given a deleted item nobody holds, should delete nothing")
    void given_deletedItemWithoutRows_should_deleteNothing() {
      // Act
      service.applyProvisionedItems(List.of(deletedItem("hub-1", "RESOURCE_SCENARIO")), TENANTS);

      // Assert: the lookup of the items with rows tells there is nothing to page
      verify(newsFeedItemRepository, never()).findIdsByHubItemIdAfter(any(), any(), any());
      verify(newsFeedItemRepository, never()).deleteAll(anyCollection());
      verify(newsFeedItemRepository, never()).findAllWithUserByIdIn(anyCollection());
    }
  }

  @Nested
  @DisplayName("Changed items")
  class ChangedItems {

    private ProvisionedNewsFeedItem changed(
        String title, List<String> tags, String urlPath, String type) {
      return new ProvisionedNewsFeedItem(
          "hub-1", title, type, tags, urlPath, Instant.parse("2027-01-01T00:00:00Z"), false);
    }

    @Test
    @DisplayName("Given a changed title, tags and url, should update the rows and keep their state")
    void given_changedItem_should_updateRowsAndKeepTheirState() {
      // Arrange
      NewsFeedItem read = row("row-1", "hub-1");
      read.setRead(true);
      NewsFeedItem unread = row("row-2", "hub-1");
      givenRows("hub-1", read, unread);

      // Act
      service.applyProvisionedItems(
          List.of(
              changed("Renamed", List.of("ransomware"), "/scenarios/renamed", "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert
      assertThat(savedBatches(1).getFirst()).containsExactly(read, unread);
      assertThat(List.of(read, unread))
          .allSatisfy(
              updated -> {
                assertThat(updated.getTitle()).isEqualTo("Renamed");
                assertThat(updated.getTags()).containsExactly("ransomware");
                assertThat(updated.getUrlPath()).isEqualTo("/scenarios/renamed");
                assertThat(updated.getCreationDate()).isEqualTo(CREATION_DATE);
              });
      assertThat(read.isRead()).isTrue();
      assertThat(unread.isRead()).isFalse();
      verify(newsFeedItemRepository, never()).deleteAll(anyCollection());
    }

    @Test
    @DisplayName("Given a removed url path, should clear it on the rows")
    void given_removedUrlPath_should_clearIt() {
      // Arrange
      NewsFeedItem row = row("row-1", "hub-1");
      givenRows("hub-1", row);

      // Act
      service.applyProvisionedItems(
          List.of(
              changed(
                  row.getTitle(), List.of("phishing", "ransomware"), null, "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert
      assertThat(savedBatches(1).getFirst()).containsExactly(row);
      assertThat(row.getUrlPath()).isNull();
    }

    @Test
    @DisplayName("Given a row whose stored type is obsolete, should set the type the Hub sends")
    void given_obsoleteStoredType_should_setTheTypeTheHubSends() {
      // Arrange
      NewsFeedItem row = row("row-1", "hub-1");
      ReflectionTestUtils.setField(row, "type", "RESOURCE_OBSOLETE");
      givenRows("hub-1", row);

      // Act
      service.applyProvisionedItems(
          List.of(
              changed(
                  row.getTitle(),
                  List.of("phishing", "ransomware"),
                  row.getUrlPath(),
                  "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert
      assertThat(savedBatches(1).getFirst()).containsExactly(row);
      assertThat(row.getType()).isEqualTo(NewsFeedType.RESOURCE_SCENARIO.name());
    }

    @Test
    @DisplayName("Given rows that already hold the values, should not write them")
    void given_rowsAlreadyUpToDate_should_notWriteThem() {
      // Arrange
      NewsFeedItem row = row("row-1", "hub-1");
      givenRows("hub-1", row);

      // Act
      service.applyProvisionedItems(
          List.of(
              changed(
                  row.getTitle(),
                  List.of("phishing", "ransomware"),
                  row.getUrlPath(),
                  "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert
      verify(newsFeedItemRepository, never()).saveAllAndFlush(anyCollection());
    }

    @Test
    @DisplayName("Given only some rows out of date, should write only those")
    void given_someRowsOutOfDate_should_writeOnlyThose() {
      // Arrange
      NewsFeedItem stale = row("row-1", "hub-1");
      stale.setTitle("Old title");
      NewsFeedItem upToDate = row("row-2", "hub-1");
      givenRows("hub-1", stale, upToDate);

      // Act
      service.applyProvisionedItems(
          List.of(
              changed(
                  upToDate.getTitle(),
                  List.of("phishing", "ransomware"),
                  upToDate.getUrlPath(),
                  "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert
      assertThat(savedBatches(1).getFirst()).containsExactly(stale);
    }

    @Test
    @DisplayName("Given a changed item the user has no row for, should create it as a new item")
    void given_changedItemWithoutRowForSubscribedUser_should_createIt() {
      // Arrange
      NewsFeedItem existing = row("row-1", "hub-1");
      existing.setTitle("Old title");
      givenRows("hub-1", existing);
      when(userService.newsFeedRecipients(TENANTS))
          .thenReturn(List.of(recipient("owner-row-1"), recipient("newcomer")));
      when(newsFeedItemRepository.rawKeys(anyCollection(), anyCollection()))
          .thenReturn(List.of(key("owner-row-1", "hub-1")));

      // Act
      service.applyProvisionedItems(
          List.of(
              changed(
                  "Renamed",
                  List.of("phishing", "ransomware"),
                  "/scenarios/hub-1",
                  "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert: the existing row is updated, a row is created for the newcomer only
      List<List<NewsFeedItem>> saved = savedBatches(2);
      assertThat(saved.get(0)).containsExactly(existing);
      assertThat(saved.get(1))
          .hasSize(1)
          .first()
          .satisfies(
              created -> {
                assertThat(created.getTitle()).isEqualTo("Renamed");
                assertThat(created.isRead()).isFalse();
              });
    }

    @Test
    @DisplayName("Given fresh items nobody holds, should look the rows up once, not per item")
    void given_freshItems_should_lookRowsUpOnceForTheChunk() {
      // Act
      service.applyProvisionedItems(items(NewsFeedItemService.ITEM_CHUNK_SIZE), TENANTS);

      // Assert
      verify(newsFeedItemRepository, times(1)).findHubItemIdsWithRows(anyCollection());
      verify(newsFeedItemRepository, never()).findIdsByHubItemIdAfter(any(), any(), any());
    }

    @Test
    @DisplayName("Given items of which only one is held, should page only the rows of that one")
    void given_oneItemHeld_should_pageOnlyItsRows() {
      // Arrange
      NewsFeedItem held = row("row-1", "hub-1");
      held.setTitle("Old title");
      givenRows("hub-1", held);

      // Act
      service.applyProvisionedItems(
          List.of(
              item("hub-0", "Title 0", "RESOURCE_SCENARIO"),
              item("hub-1", "New title", "RESOURCE_SCENARIO"),
              item("hub-2", "Title 2", "RESOURCE_SCENARIO")),
          TENANTS);

      // Assert
      verify(newsFeedItemRepository, times(1)).findIdsByHubItemIdAfter(any(), any(), any());
      assertThat(held.getTitle()).isEqualTo("New title");
    }

    @Test
    @DisplayName("Given a changed item of an unknown type, should neither update nor delete it")
    void given_changedItemOfUnknownType_should_beSkipped() {
      // Act
      service.applyProvisionedItems(
          List.of(changed("Renamed", List.of(), null, "RESOURCE_PLAYBOOK")), TENANTS);

      // Assert
      verify(newsFeedItemRepository, never()).findIdsByHubItemIdAfter(any(), any(), any());
      verify(newsFeedItemRepository, never()).deleteAll(anyCollection());
      verify(newsFeedItemRepository, never()).saveAllAndFlush(anyCollection());
    }
  }

  @Test
  @DisplayName("Given the current user, should count only their unread items")
  void given_currentUser_should_countTheirUnreadItems() {
    // Arrange
    User me = new User();
    me.setId("me");
    when(userService.currentUser()).thenReturn(me);
    when(newsFeedItemRepository.countByUserIdAndReadFalse("me")).thenReturn(3L);

    // Act & Assert
    assertThat(service.countMyUnreadItems()).isEqualTo(3L);
  }

  @Test
  @DisplayName("Given unread items of the current user, should mark them all as read")
  void given_unreadItems_should_markThemRead() {
    // Arrange
    User me = new User();
    me.setId("me");
    when(userService.currentUser()).thenReturn(me);
    List<NewsFeedItem> unread =
        List.of(
            NewsFeedItemFixture.createNewsFeedItem(me, "hub-1"),
            NewsFeedItemFixture.createNewsFeedItem(me, "hub-2"));
    when(newsFeedItemRepository.findAllByUserIdAndReadFalse("me")).thenReturn(unread);

    // Act
    service.markMyItemsRead();

    // Assert
    assertThat(unread).allMatch(NewsFeedItem::isRead);
    verify(newsFeedItemRepository).saveAll(unread);
  }

  @Test
  @DisplayName("Given the feature flag, should tell whether the news feed is enabled")
  void given_featureFlag_should_tellWhetherEnabled() {
    // Arrange
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED))
        .thenReturn(true, false);

    // Act & Assert
    assertThat(service.isFeatureEnabled()).isTrue();
    assertThat(service.isFeatureEnabled()).isFalse();
  }
}
