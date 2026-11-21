package io.openaev.api.news_feed;

import io.openaev.database.model.NewsFeedItem;
import io.openaev.utils.NewsFeedUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class NewsFeedItemMapper {

  private NewsFeedItemMapper() {}

  public static NewsFeedItemOutput toOutput(final NewsFeedItem item) {
    return new NewsFeedItemOutput(
        item.getId(),
        item.getHubItemId(),
        item.getTitle(),
        NewsFeedUtils.fromHubValue(item.getType()).orElse(null),
        item.getTags() == null ? List.of() : Arrays.asList(item.getTags()),
        item.getUrlPath(),
        item.getMetadata().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new NewsFeedItemMetadataOutput(entry.getKey(), entry.getValue()))
            .toList(),
        item.getCreationDate(),
        item.isRead());
  }
}
