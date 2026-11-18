package io.openaev.database.raw;

/** Projection of {@link io.openaev.database.repository.NewsFeedItemRepository#rawKeys} */
public interface RawNewsFeedItemKey {

  String getUserId();

  String getHubItemId();
}
