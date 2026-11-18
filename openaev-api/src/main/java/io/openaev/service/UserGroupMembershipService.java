package io.openaev.service;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds or removes one {@code users_groups} row, from the group side: {@code Group.users} is an
 * independently-owned bag whose flush-time recreate is bounded to the one group being saved, so a
 * write here can only ever touch that group's own membership rows, never another group's. {@code
 * User.groups} is the other, separately-owned bag on the same join table; mutating it instead (add,
 * remove, or a full replace) recreates the whole owning user's row set from whatever is currently
 * loaded, dropping a tenant's membership the write never looked at.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UserGroupMembershipService {

  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  private final TenantScopedTransaction tenantScopedTransaction;
  private final EntityManager entityManager;

  /** Adds {@code userId} to {@code groupId}. Returns whether the membership is new. */
  public boolean addMembership(@NotBlank final String userId, @NotBlank final String groupId) {
    Group group = findGroup(groupId);
    if (group.getUsers().stream().anyMatch(user -> user.getId().equals(userId))) {
      return false;
    }
    if (group.getTenant() == null) {
      tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
    }
    group.getUsers().add(findUser(userId));
    groupRepository.save(group);
    return true;
  }

  /** Removes {@code userId} from {@code groupId}. Returns whether a membership existed. */
  public boolean removeMembership(@NotBlank final String userId, @NotBlank final String groupId) {
    Group group = findGroup(groupId);
    boolean removed = group.getUsers().removeIf(user -> user.getId().equals(userId));
    if (!removed) {
      return false;
    }
    if (group.getTenant() == null) {
      tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
    }
    groupRepository.save(group);
    return true;
  }

  /**
   * The group as the database holds it. {@code User.groups} and {@code Group.users} are two
   * independently-owned bags of the same join table, so a membership written from the user side in
   * this persistence context is not in the group's members yet, and writing from that stale list
   * would drop or duplicate it. Pending changes are flushed first, as a refresh discards them.
   */
  private Group findGroup(String groupId) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));
    if (entityManager.contains(group)) {
      entityManager.flush();
      entityManager.refresh(group);
    }
    return group;
  }

  private User findUser(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ElementNotFoundException("User not found with id: " + userId));
  }
}
