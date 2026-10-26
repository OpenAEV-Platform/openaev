package io.openaev.service;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.model.NewsFeedType;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawNewsFeedRecipient;
import io.openaev.database.repository.NewsFeedItemRepository;
import io.openaev.utils.ReferenceResolver;
import io.openaev.xtmhub.ProvisionedNewsFeedItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsFeedItemService {

  private final NewsFeedItemRepository newsFeedItemRepository;
  private final UserService userService;
  private final ReferenceResolver referenceResolver;
  private final TenantScopedTransaction tenantTx;

  private record UserItem(String userId, String hubItemId) {}

  // -- CREATE --

  /**
   * Creates the XTM Hub news feed items for every user who is subscribed to their type and does not
   * have them yet. Deleted items and items of a type OpenAEV does not know are ignored.
   *
   * @param provisionedItems the items consumed from XTM Hub
   */
  public void createMissingItems(List<ProvisionedNewsFeedItem> provisionedItems) {
    Map<ProvisionedNewsFeedItem, NewsFeedType> typedItems = new LinkedHashMap<>();
    for (ProvisionedNewsFeedItem item : provisionedItems) {
      if (item.deleted()) {
        continue;
      }
      NewsFeedType.fromHubValue(item.type())
          .ifPresentOrElse(
              type -> typedItems.put(item, type),
              () ->
                  log.warn(
                      "Skipping the XTM Hub news feed item {} of unknown type {}",
                      item.id(),
                      item.type()));
    }
    if (typedItems.isEmpty()) {
      return;
    }

    // The items are platform-wide, not tenant-scoped: the scope only opens the transaction.
    tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          Set<UserItem> existing =
              newsFeedItemRepository
                  .rawKeys(typedItems.keySet().stream().map(ProvisionedNewsFeedItem::id).toList())
                  .stream()
                  .map(key -> new UserItem(key.getUserId(), key.getHubItemId()))
                  .collect(Collectors.toSet());

          List<NewsFeedItem> created = new ArrayList<>();
          for (RawNewsFeedRecipient recipient : userService.newsFeedRecipients()) {
            typedItems.forEach(
                (item, type) -> {
                  if (!type.isUnsubscribed(recipient.getUnsubscribedTypes())
                      && !existing.contains(new UserItem(recipient.getId(), item.id()))) {
                    created.add(toNewsFeedItem(item, type, recipient.getId()));
                  }
                });
          }
          newsFeedItemRepository.saveAll(created);
          log.info("Created {} news feed items from XTM Hub", created.size());
        });
  }

  private NewsFeedItem toNewsFeedItem(
      ProvisionedNewsFeedItem item, NewsFeedType type, String userId) {
    NewsFeedItem newsFeedItem = new NewsFeedItem();
    newsFeedItem.setHubItemId(item.id());
    newsFeedItem.setTitle(item.title());
    newsFeedItem.setType(type);
    newsFeedItem.setTags(item.tags().toArray(String[]::new));
    newsFeedItem.setUrlPath(item.urlPath());
    newsFeedItem.setCreationDate(item.creationDate());
    newsFeedItem.setUser(referenceResolver.resolve(userId, User.class));
    return newsFeedItem;
  }
}
