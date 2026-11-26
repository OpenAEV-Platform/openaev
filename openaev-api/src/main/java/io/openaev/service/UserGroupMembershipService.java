package io.openaev.service;

import io.openaev.database.model.Group;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds or removes one {@code users_groups} row by a direct native write on the join table itself,
 * never by loading and resaving {@code Group} or {@code User}: both carry the membership as a
 * {@code List} bag on their own side of the same {@code @JoinTable}, and Hibernate flushes a bag by
 * deleting every row of the owning entity's side and reinserting what is currently loaded. Writing
 * through either entity therefore recreates the whole owning row's membership from whatever happens
 * to be in memory, which can drop a row written by a concurrent caller or by the other side's bag
 * in the same transaction. A row of the {@code users_groups} join table carries no state of its own
 * (no index, no audit trail, no stream event is attached to a membership-only change: {@code
 * Group}'s and {@code User}'s own {@code @PostUpdate}/{@code @PreUpdate} listener only fires when
 * one of their own columns changes, never for a join-table-only flush), so a native write loses
 * nothing the entity-side write produced.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UserGroupMembershipService {

  private static final String INSERT_MEMBERSHIP =
      "INSERT INTO users_groups (user_id, group_id) VALUES (:userId, :groupId) "
          + "ON CONFLICT (group_id, user_id) DO NOTHING";
  private static final String DELETE_MEMBERSHIP =
      "DELETE FROM users_groups WHERE user_id = :userId AND group_id = :groupId";
  private static final String COUNT_MEMBERSHIPS =
      "SELECT count(*) FROM users_groups WHERE user_id = :userId AND group_id IN (:groupIds)";

  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  private final EntityManager entityManager;

  /** Adds {@code userId} to {@code groupId}. Returns whether the membership is new. */
  public boolean addMembership(@NotBlank final String userId, @NotBlank final String groupId) {
    boolean platformGroup = isPlatformGroup(groupId);
    requireUser(userId);
    int inserted =
        platformGroup
            ? withPlatformWriteMarker(
                () -> executeMembershipWrite(INSERT_MEMBERSHIP, userId, groupId))
            : executeMembershipWrite(INSERT_MEMBERSHIP, userId, groupId);
    return inserted > 0;
  }

  /** Removes {@code userId} from {@code groupId}. Returns whether a membership existed. */
  public boolean removeMembership(@NotBlank final String userId, @NotBlank final String groupId) {
    boolean platformGroup = isPlatformGroup(groupId);
    int deleted =
        platformGroup
            ? withPlatformWriteMarker(
                () -> executeMembershipWrite(DELETE_MEMBERSHIP, userId, groupId))
            : executeMembershipWrite(DELETE_MEMBERSHIP, userId, groupId);
    return deleted > 0;
  }

  /**
   * Whether {@code userId} holds any of {@code groupIds}, read from the join table rather than from
   * a loaded bag, which is stale once a native membership write has run in the transaction.
   */
  public boolean isMemberOfAny(
      @NotBlank final String userId, @NotNull final Collection<String> groupIds) {
    if (groupIds.isEmpty()) {
      return false;
    }
    Number count =
        (Number)
            entityManager
                .createNativeQuery(COUNT_MEMBERSHIPS)
                .setParameter("userId", userId)
                .setParameter("groupIds", groupIds)
                .getSingleResult();
    return count.longValue() > 0;
  }

  /**
   * Whether {@code groupId} is a platform row (no tenant). 404s the same as the old entity load.
   */
  private boolean isPlatformGroup(String groupId) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));
    return group.getTenant() == null;
  }

  private void requireUser(String userId) {
    if (!userRepository.existsById(userId)) {
      throw new ElementNotFoundException("User not found with id: " + userId);
    }
  }

  private int executeMembershipWrite(String sql, String userId, String groupId) {
    return entityManager
        .createNativeQuery(sql)
        .setParameter("userId", userId)
        .setParameter("groupId", groupId)
        .executeUpdate();
  }

  /**
   * Runs {@code write} with {@code app.platform_write} set, then puts back whatever value the
   * transaction carried before, so the marker does not outlive the one statement it guards. Pending
   * changes are flushed first, as in {@code TenantScopeTransactionAspect#proceedAsPlatformWrite}:
   * the native statement auto-flushes, and a pending edit of a platform group must not be written
   * under the marker.
   */
  private int withPlatformWriteMarker(Supplier<Integer> write) {
    entityManager.flush();
    String previous = currentPlatformWrite();
    setPlatformWrite("true");
    try {
      return write.get();
    } finally {
      setPlatformWrite(previous);
    }
  }

  // The scope queries touch no table, so they must not trigger Hibernate's auto-flush.
  private String currentPlatformWrite() {
    return (String)
        entityManager
            .createNativeQuery("SELECT coalesce(current_setting('app.platform_write', true), '')")
            .setFlushMode(FlushModeType.COMMIT)
            .getSingleResult();
  }

  private void setPlatformWrite(String value) {
    entityManager
        .createNativeQuery("SELECT set_config('app.platform_write', :value, true)")
        .setFlushMode(FlushModeType.COMMIT)
        .setParameter("value", value)
        .getSingleResult();
  }
}
