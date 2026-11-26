package io.openaev.utils.fixtures;

import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.model.NewsFeedType;
import io.openaev.database.model.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    // Relative: the cleanup deletes the items older than the retention, whatever day the test runs
    item.setCreationDate(Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
    item.setUser(user);
    return item;
  }

  public static NewsFeedItem createBlogPost(User user, String hubItemId) {
    NewsFeedItem item = createNewsFeedItem(user, hubItemId);
    item.setTitle("Blogpost " + hubItemId);
    item.setType(NewsFeedType.BLOG_POST);
    item.setTags(new String[] {"threat intel"});
    item.setUrlPath("/redirect/blog/" + hubItemId);
    item.getMetadata().put("read_minutes", "7");
    item.getMetadata().put("description", "About " + hubItemId);
    return item;
  }
}
