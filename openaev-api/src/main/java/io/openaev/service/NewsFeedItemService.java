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
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsFeedItemService {

  /**
   * Users handled per transaction when creating items, and rows of one Hub item handled per
   * transaction when updating or deleting them (one row per user). Together with {@link
   * #ITEM_CHUNK_SIZE} it bounds the entities held in memory (at most their product) and the work
   * lost on error, whatever the number of users or of items the Hub accumulated.
   */
  public static final int USER_CHUNK_SIZE = 500;

  /** Hub items handled per transaction, see {@link #USER_CHUNK_SIZE}. */
  public static final int ITEM_CHUNK_SIZE = 50;

  private final NewsFeedItemRepository newsFeedItemRepository;
  private final UserService userService;
  private final ReferenceResolver referenceResolver;
  private final TenantScopedTransaction tenantTx;
  private final PreviewFeatureService previewFeatureService;

  private record UserItem(String userId, String hubItemId) {}

  private record TypedItem(ProvisionedNewsFeedItem item, NewsFeedType type) {}

  // -- READ --

  public boolean isFeatureEnabled() {
    return previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED);
  }

  /** Searches the news feed items of the current user. */
  public Page<NewsFeedItem> searchMyItems(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    String userId = userService.currentUser().getId();
    Specification<NewsFeedItem> ownerSpec =
        (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    return buildPaginationJPA(
        (specification, pageable) ->
            newsFeedItemRepository.findAll(ownerSpec.and(specification), pageable),
        searchPaginationInput,
        NewsFeedItem.class);
  }

  /** The number of news feed items of the current user that are not read yet. */
  public long countMyUnreadItems() {
    return newsFeedItemRepository.countByUserIdAndReadFalse(userService.currentUser().getId());
  }

  /** The news feed types the current user is subscribed to. */
  public List<NewsFeedType> getMySubscribedTypes() {
    return NewsFeedUtils.subscribedTypes(
        userService.findNewsFeedUnsubscribedTypes(userService.currentUser().getId()));
  }

  // -- UPDATE --

  /**
   * Subscribes the current user to the given news feed types and only those. Subscribing to none
   * unsubscribes the user from everything.
   *
   * @return the types the user is now subscribed to
   */
  public List<NewsFeedType> updateMySubscribedTypes(
      @NotNull final Collection<NewsFeedType> subscribedTypes) {
    userService.updateNewsFeedUnsubscribedTypes(
        userService.currentUser().getId(), NewsFeedUtils.toUnsubscribedTypes(subscribedTypes));
    return getMySubscribedTypes();
  }

  /**
   * Marks all the unread news feed items of the current user as read, and only theirs. Idempotent.
   *
   * <p>The items are updated through the session rather than by a bulk update: a bulk update would
   * skip the listener chain, so the user's other open tabs would not be told over SSE that their
   * items changed. Nothing is bounded: the unread items are loaded at once, and the events they
   * publish all fire when the request transaction commits. This is accepted because a user only
   * holds the items the Hub published since they joined, and this runs once per visit.
   *
   * <p>Accepted race: if the collector deletes an item between its load and the commit of the
   * request, the update of that row matches nothing and the commit fails with an optimistic locking
   * error. It cannot be retried here: the failure surfaces at the commit of the request
   * transaction, which is then rolled back, so nothing is half done. The call only marks items read
   * as a side effect of visiting the page: the client ignores its failure and the next visit marks
   * the items again, the deleted one no longer being there.
   */
  public void markMyItemsRead() {
    List<NewsFeedItem> unreadItems =
        newsFeedItemRepository.findAllByUserIdAndReadFalse(userService.currentUser().getId());
    unreadItems.forEach(item -> item.setRead(true));
    newsFeedItemRepository.saveAll(unreadItems);
  }

  // -- SYNCHRONIZE --

  /**
   * Applies the XTM Hub news feed items consumed from the Hub to the items of the users:
   *
   * <ul>
   *   <li>a deleted item deletes its rows, those of every user;
   *   <li>an item whose title, tags, type or URL path changed updates its existing rows, those of
   *       every user whatever they are subscribed to: the read state and the creation date are
   *       kept, and a row that already holds the values is not written, so it publishes no event;
   *   <li>for every user of the given tenants who is subscribed to its type and does not have it
   *       yet, an item gets a row. The Hub sends a changed item again as a whole and does not tell
   *       it from a new one, so a changed item is also created for such a user (who joined, or
   *       subscribed to its type, since its first delivery).
   * </ul>
   *
   * Items of a type OpenAEV does not know are skipped, except when deleted (their rows, if any, are
   * deleted all the same). An item the Hub sent several times is handled once: deleted if any of
   * its occurrences is, else the first one counts.
   *
   * <p>The rows are handled by chunks, each in its own transaction: the Hub no longer holds the
   * items, so a failing chunk is logged and the others are still handled.
   *
   * <p>Accepted risk: the collector runs on every node without a cluster-wide lock, as the creation
   * of items always did. Each run only handles the payload the Hub gave it, and the Hub removes
   * what it returns, so two runs never get the same delivery. The one case left is two nodes
   * consuming two consecutive deliveries of the same item at the same moment, the deletion being
   * applied before the creation of the previous delivery commits: rows of a deleted item would then
   * stay. It takes two runs of the scheduled collector to overlap that closely on the same item, so
   * a cluster-wide lock is not worth its cost here.
   *
   * @param provisionedItems the items consumed from XTM Hub
   * @param recipientTenantIds the tenants registered on XTM Hub, whose users receive new items
   */
  public void applyProvisionedItems(
      List<ProvisionedNewsFeedItem> provisionedItems, Collection<String> recipientTenantIds) {
    Set<String> deletedIds = new LinkedHashSet<>();
    Map<String, TypedItem> typedItems = new LinkedHashMap<>();
    for (ProvisionedNewsFeedItem item : provisionedItems) {
      if (item.deleted()) {
        deletedIds.add(item.id());
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
    // Deleted wins: a deletion is final on the Hub, an item must not come back to life here.
    typedItems.keySet().removeAll(deletedIds);

    if (!deletedIds.isEmpty()) {
      log.info("Deleted {} news feed items from XTM Hub", deleteItems(deletedIds));
    }
    if (typedItems.isEmpty()) {
      return;
    }
    List<TypedItem> items = List.copyOf(typedItems.values());
    int updated = updateItems(items);
    if (updated > 0) {
      log.info("Updated {} news feed items from XTM Hub", updated);
    }
    if (!recipientTenantIds.isEmpty()) {
      createMissingItems(items, recipientTenantIds);
    }
  }

  /**
   * Deletes the rows of the given Hub items, those of every user, through the session and one by
   * one: a bulk delete would skip the listener chain, so the open sessions of the users would not
   * be told over SSE.
   */
  private int deleteItems(Collection<String> hubItemIds) {
    int deleted = 0;
    for (String hubItemId : withRows(hubItemIds)) {
      try {
        deleted +=
            forEachChunkOfRows(
                hubItemId,
                rows -> {
                  newsFeedItemRepository.deleteAll(rows);
                  // Flushed here, not at commit, so that a failure surfaces to the retry.
                  newsFeedItemRepository.flush();
                  return rows.size();
                });
      } catch (Exception e) {
        log.error("Could not delete the news feed item {}: {}", hubItemId, e.getMessage(), e);
      }
    }
    return deleted;
  }

  /**
   * Updates the existing rows of the given items with what the Hub sent, the rows of every user
   * whatever their subscriptions. Only the rows that differ are written, so the others publish no
   * event.
   *
   * @return how many rows were updated
   */
  private int updateItems(List<TypedItem> items) {
    Set<String> withRows =
        new HashSet<>(withRows(items.stream().map(typed -> typed.item().id()).toList()));
    int updated = 0;
    for (TypedItem typed : items) {
      if (!withRows.contains(typed.item().id())) {
        continue;
      }
      try {
        updated +=
            forEachChunkOfRows(
                typed.item().id(),
                rows -> {
                  List<NewsFeedItem> changed =
                      rows.stream().filter(row -> applyChanges(row, typed)).toList();
                  if (!changed.isEmpty()) {
                    newsFeedItemRepository.saveAllAndFlush(changed);
                  }
                  return changed.size();
                });
      } catch (Exception e) {
        log.error(
            "Could not update the news feed item {}: {}", typed.item().id(), e.getMessage(), e);
      }
    }
    return updated;
  }

  /**
   * The given Hub items that at least one user may hold, in the given order, looked up by chunks of
   * {@link #ITEM_CHUNK_SIZE} so that the items nobody holds (the fresh ones, in most runs) cost one
   * query per chunk instead of a transaction and a query each. The lookup is only a shortcut: a
   * chunk that cannot be looked up, even after the retry, is logged and all its items are kept, so
   * that their rows are still paged, and deleted or updated, one item at a time.
   */
  private List<String> withRows(Collection<String> hubItemIds) {
    Set<String> found = new HashSet<>();
    for (List<String> chunk : chunks(List.copyOf(hubItemIds), ITEM_CHUNK_SIZE)) {
      try {
        found.addAll(
            withRetry(
                () ->
                    tenantTx.execute(
                        TxCtx.allTenants(),
                        () -> newsFeedItemRepository.findHubItemIdsWithRows(chunk))));
      } catch (Exception e) {
        log.error(
            "Could not look up the rows of {} news feed items, handling them one by one: {}",
            chunk.size(),
            e.getMessage(),
            e);
        found.addAll(chunk);
      }
    }
    return hubItemIds.stream().filter(found::contains).toList();
  }

  /**
   * Sets on the row what the Hub changed, nothing else (not the read state nor the creation date).
   *
   * @return whether the row changed
   */
  private static boolean applyChanges(NewsFeedItem row, TypedItem typed) {
    ProvisionedNewsFeedItem item = typed.item();
    boolean changed = false;
    if (!Objects.equals(row.getTitle(), item.title())) {
      row.setTitle(item.title());
      changed = true;
    }
    if (!Objects.equals(row.getType(), typed.type().name())) {
      row.setType(typed.type());
      changed = true;
    }
    String[] tags = item.tags().toArray(String[]::new);
    if (!Arrays.equals(row.getTags(), tags)) {
      row.setTags(tags);
      changed = true;
    }
    if (!Objects.equals(row.getUrlPath(), item.urlPath())) {
      row.setUrlPath(item.urlPath());
      changed = true;
    }
    return changed;
  }

  /**
   * Runs {@code action} on the rows of a Hub item, whoever owns them, {@link #USER_CHUNK_SIZE} rows
   * at a time and each chunk in its own transaction. The ids are paged by keyset so a chunk that
   * fails is logged and skipped without losing the position, and the rows deleted by the previous
   * chunks do not shift the next one.
   *
   * @param action handles the rows of one chunk and returns how many it changed
   * @return how many rows the actions changed
   */
  private int forEachChunkOfRows(String hubItemId, ToIntFunction<List<NewsFeedItem>> action) {
    int handled = 0;
    String afterId = "";
    List<String> ids;
    do {
      String cursor = afterId;
      ids =
          tenantTx.execute(
              TxCtx.allTenants(),
              () ->
                  newsFeedItemRepository.findIdsByHubItemIdAfter(
                      hubItemId, cursor, PageRequest.ofSize(USER_CHUNK_SIZE)));
      if (ids.isEmpty()) {
        break;
      }
      List<String> chunkIds = ids;
      afterId = chunkIds.getLast();
      try {
        handled +=
            withRetry(
                () ->
                    tenantTx.execute(
                        TxCtx.allTenants(),
                        () ->
                            action.applyAsInt(
                                newsFeedItemRepository.findAllWithUserByIdIn(chunkIds))));
      } catch (Exception e) {
        log.error(
            "Could not handle {} rows of the news feed item {}: {}",
            chunkIds.size(),
            hubItemId,
            e.getMessage(),
            e);
      }
    } while (ids.size() == USER_CHUNK_SIZE);
    return handled;
  }

  // -- CREATE --

  /**
   * Creates the given items for every user of the given tenants who is subscribed to their type and
   * does not have them yet.
   *
   * <p>The users and the items are handled by chunks, each in its own transaction: the Hub no
   * longer holds the items, so a failing chunk is logged and the others are still created.
   */
  private void createMissingItems(List<TypedItem> items, Collection<String> recipientTenantIds) {
    // The items are platform-wide, not tenant-scoped: the scope only opens the transaction.
    List<RawNewsFeedRecipient> recipients =
        tenantTx.execute(
            TxCtx.allTenants(), () -> userService.newsFeedRecipients(recipientTenantIds));

    int created = 0;
    for (List<RawNewsFeedRecipient> userChunk : chunks(recipients, USER_CHUNK_SIZE)) {
      for (List<TypedItem> itemChunk : chunks(items, ITEM_CHUNK_SIZE)) {
        try {
          created += withRetry(() -> createMissingItemsForUsers(userChunk, itemChunk));
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
   * A concurrent run on the same items (another run, another node, the user marking them read)
   * makes the unique constraint or the update reject the chunk. The retry reads the rows again, so
   * it only handles what is left.
   */
  private <T> T withRetry(Supplier<T> work) {
    try {
      return work.get();
    } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
      log.warn("News feed items changed concurrently, retrying the chunk: {}", e.getMessage());
      return work.get();
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
