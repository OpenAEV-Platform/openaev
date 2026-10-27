package io.openaev.service;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.model.NewsFeedType;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawNewsFeedRecipient;
import io.openaev.database.repository.NewsFeedItemRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.NewsFeedUtils;
import io.openaev.utils.ReferenceResolver;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.xtmhub.ProvisionedNewsFeedItem;
import io.openaev.xtmhub.XtmHubService;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsFeedItemService {

  /**
   * Users handled per transaction. Together with {@link #ITEM_CHUNK_SIZE} it bounds the entities
   * held in memory (at most their product) and the work lost on error, whatever the number of users
   * or of items the Hub accumulated.
   */
  public static final int USER_CHUNK_SIZE = 500;

  /** Hub items handled per transaction, see {@link #USER_CHUNK_SIZE}. */
  public static final int ITEM_CHUNK_SIZE = 50;

  private final NewsFeedItemRepository newsFeedItemRepository;
  private final UserService userService;
  private final ReferenceResolver referenceResolver;
  private final TenantScopedTransaction tenantTx;
  private final PreviewFeatureService previewFeatureService;
  private final XtmHubService xtmHubService;

  private record UserItem(String userId, String hubItemId) {}

  private record TypedItem(ProvisionedNewsFeedItem item, NewsFeedType type) {}

  // -- READ --

  public boolean isFeatureEnabled() {
    return previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED);
  }

  /**
   * Searches the news feed items of the current user. The items carry no tenant, so the page is
   * empty when no tenant in the scope of the request is registered on XTM Hub (a tenant that lost
   * its connectivity still shows the items it retrieved).
   */
  public Page<NewsFeedItem> searchMyItems(
      TxCtx ctx, @NotNull final SearchPaginationInput searchPaginationInput) {
    if (!xtmHubService.isAnyTenantRegistered(ctx)) {
      return new PageImpl<>(
          List.of(),
          PageRequest.of(searchPaginationInput.getPage(), searchPaginationInput.getSize()),
          0);
    }
    String userId = userService.currentUser().getId();
    Specification<NewsFeedItem> ownerSpec =
        (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    return buildPaginationJPA(
        (specification, pageable) ->
            newsFeedItemRepository.findAll(ownerSpec.and(specification), pageable),
        searchPaginationInput,
        NewsFeedItem.class);
  }

  // -- CREATE --

  /**
   * Creates the XTM Hub news feed items for every user of the given tenants who is subscribed to
   * their type and does not have them yet. Deleted items and items of a type OpenAEV does not know
   * are ignored, and an item the Hub sent several times is created once.
   *
   * <p>The users and the items are handled by chunks, each in its own transaction: the Hub no
   * longer holds the items, so a failing chunk is logged and the others are still created.
   *
   * @param provisionedItems the items consumed from XTM Hub
   * @param recipientTenantIds the tenants registered on XTM Hub, whose users receive the items
   */
  public void createMissingItems(
      List<ProvisionedNewsFeedItem> provisionedItems, Collection<String> recipientTenantIds) {
    Map<String, TypedItem> typedItems = new LinkedHashMap<>();
    for (ProvisionedNewsFeedItem item : provisionedItems) {
      if (item.deleted()) {
        continue;
      }
      NewsFeedUtils.fromHubValue(item.type())
          .ifPresentOrElse(
              type -> {
                if (typedItems.putIfAbsent(item.id(), new TypedItem(item, type)) != null) {
                  log.warn("Ignoring the XTM Hub news feed item {} sent several times", item.id());
                }
              },
              () ->
                  log.warn(
                      "Skipping the XTM Hub news feed item {} of unknown type {}",
                      item.id(),
                      item.type()));
    }
    if (typedItems.isEmpty() || recipientTenantIds.isEmpty()) {
      return;
    }

    // The items are platform-wide, not tenant-scoped: the scope only opens the transaction.
    List<RawNewsFeedRecipient> recipients =
        tenantTx.execute(
            TxCtx.allTenants(), () -> userService.newsFeedRecipients(recipientTenantIds));

    List<TypedItem> items = List.copyOf(typedItems.values());
    int created = 0;
    for (List<RawNewsFeedRecipient> userChunk : chunks(recipients, USER_CHUNK_SIZE)) {
      for (List<TypedItem> itemChunk : chunks(items, ITEM_CHUNK_SIZE)) {
        try {
          created += createMissingItemsWithRetry(userChunk, itemChunk);
        } catch (Exception e) {
          log.error(
              "Could not create {} news feed items for {} users: {}",
              itemChunk.size(),
              userChunk.size(),
              e.getMessage(),
              e);
        }
      }
    }
    log.info("Created {} news feed items from XTM Hub", created);
  }

  private static <T> List<List<T>> chunks(List<T> list, int size) {
    List<List<T>> chunks = new ArrayList<>();
    for (int from = 0; from < list.size(); from += size) {
      chunks.add(list.subList(from, Math.min(from + size, list.size())));
    }
    return chunks;
  }

  /**
   * A concurrent creation of the same items (another run, another node) makes the unique constraint
   * reject the chunk. The retry finds the items created meanwhile and only creates the missing
   * ones.
   */
  private int createMissingItemsWithRetry(List<RawNewsFeedRecipient> users, List<TypedItem> items) {
    try {
      return createMissingItemsForUsers(users, items);
    } catch (DataIntegrityViolationException e) {
      log.warn("News feed items created concurrently, retrying the chunk: {}", e.getMessage());
      return createMissingItemsForUsers(users, items);
    }
  }

  private int createMissingItemsForUsers(List<RawNewsFeedRecipient> users, List<TypedItem> items) {
    return tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          Set<UserItem> existing =
              newsFeedItemRepository
                  .rawKeys(
                      users.stream().map(RawNewsFeedRecipient::getId).toList(),
                      items.stream().map(typed -> typed.item().id()).toList())
                  .stream()
                  .map(key -> new UserItem(key.getUserId(), key.getHubItemId()))
                  .collect(Collectors.toSet());

          List<NewsFeedItem> created = new ArrayList<>();
          for (RawNewsFeedRecipient user : users) {
            for (TypedItem typed : items) {
              if (!NewsFeedUtils.isUnsubscribed(typed.type(), user.getUnsubscribedTypes())
                  && !existing.contains(new UserItem(user.getId(), typed.item().id()))) {
                created.add(toNewsFeedItem(typed, user.getId()));
              }
            }
          }
          // Flushed here, not at commit, so that a constraint violation surfaces to the retry.
          newsFeedItemRepository.saveAllAndFlush(created);
          return created.size();
        });
  }

  private NewsFeedItem toNewsFeedItem(TypedItem typed, String userId) {
    ProvisionedNewsFeedItem item = typed.item();
    NewsFeedItem newsFeedItem = new NewsFeedItem();
    newsFeedItem.setHubItemId(item.id());
    newsFeedItem.setTitle(item.title());
    newsFeedItem.setType(typed.type());
    newsFeedItem.setTags(item.tags().toArray(String[]::new));
    newsFeedItem.setUrlPath(item.urlPath());
    newsFeedItem.setCreationDate(item.creationDate());
    newsFeedItem.setUser(referenceResolver.resolve(userId, User.class));
    return newsFeedItem;
  }
}
