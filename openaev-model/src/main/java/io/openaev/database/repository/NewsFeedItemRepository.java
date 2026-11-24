package io.openaev.database.repository;

import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.raw.RawNewsFeedItemKey;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsFeedItemRepository
    extends JpaRepository<NewsFeedItem, String>, JpaSpecificationExecutor<NewsFeedItem> {

  /** Served by the (user, hub item) unique index, which leads with the user. */
  long countByUserIdAndReadFalse(@NotNull String userId);

  List<NewsFeedItem> findAllByUserIdAndReadFalse(@NotNull String userId);

  /** Projection rather than entities: loading an item also loads its user (EAGER). */
  @Query(
      "select n.user.id as userId, n.hubItemId as hubItemId"
          + " from NewsFeedItem n where n.user.id in :userIds and n.hubItemId in :hubItemIds")
  List<RawNewsFeedItemKey> rawKeys(
      @Param("userIds") Collection<String> userIds,
      @Param("hubItemIds") Collection<String> hubItemIds);

  /**
   * The ids of the rows of a Hub item, whoever owns them, after the given id and in id order: a
   * keyset page, so that a Hub item held by many users is handled by bounded chunks. Ids only, as a
   * page of entities would also load their users (EAGER). Served by the Hub item index.
   */
  @Query(
      "select n.id from NewsFeedItem n"
          + " where n.hubItemId = :hubItemId and n.id > :afterId order by n.id")
  List<String> findIdsByHubItemIdAfter(
      @Param("hubItemId") String hubItemId, @Param("afterId") String afterId, Pageable pageable);

  /**
   * Which of the given Hub items at least one user holds. Served by the Hub item index.
   *
   * <p>Not paged: the Hub items are the ones of one chunk of a sync run.
   */
  @Query("select distinct n.hubItemId from NewsFeedItem n where n.hubItemId in :hubItemIds")
  List<String> findHubItemIdsWithRows(@Param("hubItemIds") Collection<String> hubItemIds);

  /**
   * The rows with their users in one query, instead of one per user (EAGER). The users' own EAGER
   * associations are still loaded with them: callers pass a bounded chunk of ids, never every row
   * of an item.
   */
  @Query("select n from NewsFeedItem n join fetch n.user where n.id in :ids")
  List<NewsFeedItem> findAllWithUserByIdIn(@Param("ids") Collection<String> ids);
}
