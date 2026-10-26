package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import io.openaev.utils.ReferenceResolver;
import io.openaev.xtmhub.ProvisionedNewsFeedItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewsFeedItemService tests")
class NewsFeedItemServiceTest {

  private static final Set<String> TENANTS = Set.of("tenant-a");
  private static final Instant CREATION_DATE = Instant.parse("2026-10-20T10:15:30Z");

  @Mock private NewsFeedItemRepository newsFeedItemRepository;
  @Mock private UserService userService;
  @Mock private ReferenceResolver referenceResolver;
  @Mock private TenantScopedTransaction tenantTx;

  private NewsFeedItemService service;

  @BeforeEach
  void setUp() {
    service =
        new NewsFeedItemService(newsFeedItemRepository, userService, referenceResolver, tenantTx);
    // runs the work inline: the transaction itself is covered by the integration tests
    lenient()
        .doAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get())
        .when(tenantTx)
        .execute(any(TxCtx.class), ArgumentMatchers.<Supplier<Object>>any());
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

  private static List<RawNewsFeedRecipient> recipients(int count) {
    return IntStream.range(0, count).mapToObj(i -> recipient("user-" + i)).toList();
  }

  private static ProvisionedNewsFeedItem item(String id, String title, String type) {
    return new ProvisionedNewsFeedItem(id, title, type, List.of(), null, CREATION_DATE, false);
  }

  @SuppressWarnings("unchecked")
  private List<List<NewsFeedItem>> savedBatches(int expectedBatches) {
    ArgumentCaptor<List<NewsFeedItem>> captor = ArgumentCaptor.forClass(List.class);
    verify(newsFeedItemRepository, times(expectedBatches)).saveAll(captor.capture());
    return captor.getAllValues();
  }

  @Test
  @DisplayName("Given no recipient tenant, should create nothing and not look for users")
  void given_noRecipientTenant_should_createNothing() {
    // Act
    service.createMissingItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), Set.of());

    // Assert
    verifyNoInteractions(userService);
    verify(newsFeedItemRepository, never()).saveAll(anyCollection());
  }

  @Test
  @DisplayName("Given the recipient tenants, should look for the users of these tenants")
  void given_recipientTenants_should_lookForTheirUsers() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS)).thenReturn(recipients(2));

    // Act
    service.createMissingItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

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
    service.createMissingItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

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
    when(newsFeedItemRepository.saveAll(anyCollection()))
        .thenThrow(new IllegalStateException("boom"))
        .thenAnswer(invocation -> new ArrayList<>(invocation.<List<NewsFeedItem>>getArgument(0)));

    // Act
    service.createMissingItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

    // Assert
    assertThat(savedBatches(2))
        .extracting(List::size)
        .containsExactly(NewsFeedItemService.USER_CHUNK_SIZE, 1);
  }

  @Test
  @DisplayName("Given the same item several times, should create it once per user")
  void given_sameItemSeveralTimes_should_createItOncePerUser() {
    // Arrange
    when(userService.newsFeedRecipients(TENANTS)).thenReturn(recipients(1));

    // Act
    service.createMissingItems(
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
                recipient("unsubscribed-all", NewsFeedType.ALL_TYPES),
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
    service.createMissingItems(List.of(item("hub-1", "Title", "RESOURCE_SCENARIO")), TENANTS);

    // Assert
    verify(referenceResolver, times(1)).resolve("subscribed", User.class);
    assertThat(savedBatches(1).getFirst()).hasSize(1);
  }

  @Test
  @DisplayName("Given deleted and unknown-type items, should create nothing")
  void given_deletedAndUnknownTypeItems_should_createNothing() {
    // Act
    service.createMissingItems(
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
    verify(newsFeedItemRepository, never()).saveAll(anyCollection());
  }
}
