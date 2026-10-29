package io.openaev.utils.fixtures;

import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.model.NewsFeedType;
import io.openaev.database.model.User;
import java.time.Instant;
import java.util.UUID;

public class NewsFeedItemFixture {

  public static NewsFeedItem createDefaultNewsFeedItem(User user) {
    return createNewsFeedItem(user, UUID.randomUUID().toString());
  }

  public static NewsFeedItem createNewsFeedItem(User user, String hubItemId) {
    NewsFeedItem item = new NewsFeedItem();
    item.setHubItemId(hubItemId);
    item.setTitle("New scenario " + hubItemId);
    item.setType(NewsFeedType.RESOURCE_SCENARIO);
    item.setTags(new String[] {"phishing", "ransomware"});
    item.setUrlPath("/scenarios/" + hubItemId);
    item.setCreationDate(Instant.parse("2026-10-20T10:15:30Z"));
    item.setUser(user);
    return item;
  }
}
