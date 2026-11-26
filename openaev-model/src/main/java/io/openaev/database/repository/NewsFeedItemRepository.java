package io.openaev.database.repository;

import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.raw.RawNewsFeedItemKey;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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
   * The ids of the rows created before the cutoff whose stored type is not the excluded one, after
   * the given id and in id order: a keyset page, as {@link #findIdsByHubItemIdAfter}. The type is
   * compared as stored, so rows of a type this version no longer knows match. No index serves it,
   * so every sync run scans the table: that is fine while it only holds the items the Hub published
   * for the users. An index on the creation date becomes worth adding when the table grows large
   * (many users times many items) and the scan shows up in the sync run time.
   */
  @Query(
      "select n.id from NewsFeedItem n"
          + " where n.creationDate < :cutoff and n.type <> :excludedType and n.id > :afterId"
          + " order by n.id")
  List<String> findIdsCreatedBeforeExcludingTypeAfter(
      @Param("cutoff") Instant cutoff,
      @Param("excludedType") String excludedType,
      @Param("afterId") String afterId,
      Pageable pageable);

  /**
   * Of the given rows, those that are still created before the cutoff and still not of the excluded
   * type, under a row lock (SELECT ... FOR UPDATE) held until the caller's transaction ends. A row
   * another transaction is changing (for example to a blog post) is waited for, then checked
   * against what it committed, so the rows returned are safe to delete. No join fetch: the lock
   * only covers the news feed rows, not their users, which are loaded afterwards.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select n from NewsFeedItem n"
          + " where n.id in :ids and n.creationDate < :cutoff and n.type <> :excludedType")
  List<NewsFeedItem> findForUpdateByIdInCreatedBeforeExcludingType(
      @Param("ids") Collection<String> ids,
      @Param("cutoff") Instant cutoff,
      @Param("excludedType") String excludedType);

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
