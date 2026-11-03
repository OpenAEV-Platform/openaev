package io.openaev.database.repository;

import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.raw.RawNewsFeedItemKey;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsFeedItemRepository
    extends JpaRepository<NewsFeedItem, String>, JpaSpecificationExecutor<NewsFeedItem> {

  /** Projection rather than entities: loading an item also loads its user (EAGER). */
  @Query(
      "select n.user.id as userId, n.hubItemId as hubItemId"
          + " from NewsFeedItem n where n.user.id in :userIds and n.hubItemId in :hubItemIds")
  List<RawNewsFeedItemKey> rawKeys(
      @Param("userIds") Collection<String> userIds,
      @Param("hubItemIds") Collection<String> hubItemIds);
}
