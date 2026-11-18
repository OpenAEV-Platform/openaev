package io.openaev.service;

import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tells which groups held by a persistence context carry roles that differ from the ones the scope
 * of the current transaction reads, or whether the groups held are not the ones that scope reads at
 * all.
 *
 * <p>The roles of a group are an eager association of the group: a load by id of a group the
 * session already holds hands out that instance with the roles of its first read, which a scope
 * other than the first one reads differently. A caller that has to hand out the roles of the
 * current scope refreshes only the groups this class returns, so a group whose roles are the same
 * costs no reload. One query reads the roles of all the groups, whatever their number.
 *
 * <p>The groups themselves are an eager association of the user, filtered by the scope at load time
 * once {@code groups} is an active table: a user held with the groups of one scope can be missing a
 * group the current scope would add, or hold one it would not. {@link #needsReload} answers whether
 * either kind of mismatch holds for {@code user}, with one query of its own, whatever the number of
 * groups held or visible.
 */
public final class GroupRolesInScope {

  private GroupRolesInScope() {}

  /**
   * @return whether {@code user}, as the persistence context already holds it, is not what the
   *     current transaction scope would read for it: a group it misses or holds in excess, or one
   *     it holds whose roles are not the ones the scope reads
   */
  public static boolean needsReload(EntityManager entityManager, User user) {
    Map<String, Set<String>> inScope = new HashMap<>();
    entityManager
        .createQuery(
            "select g.id, r.id from Group g join g.users u left join g.roles r where u.id ="
                + " :userId",
            Object[].class)
        .setParameter("userId", user.getId())
        .getResultList()
        .forEach(
            row -> {
              Set<String> roles = inScope.computeIfAbsent((String) row[0], id -> new HashSet<>());
              if (row[1] != null) {
                roles.add((String) row[1]);
              }
            });
    List<Group> held = user.getUnscopedGroups();
    if (held.size() != inScope.size()) {
      return true;
    }
    for (Group group : held) {
      Set<String> heldRoleIds =
          group.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
      if (!heldRoleIds.equals(inScope.get(group.getId()))) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return the groups of {@code groups} whose held roles are not the ones the current transaction
   *     scope reads, in the order of {@code groups}
   */
  public static List<Group> withDifferingRoles(
      EntityManager entityManager, Collection<Group> groups) {
    if (groups.isEmpty()) {
      return List.of();
    }
    Map<String, Set<String>> inScope = new HashMap<>();
    entityManager
        .createQuery(
            "select g.id, r.id from Group g join g.roles r where g.id in :groupIds", Object[].class)
        .setParameter("groupIds", groups.stream().map(Group::getId).toList())
        .getResultList()
        .forEach(
            row ->
                inScope
                    .computeIfAbsent((String) row[0], id -> new HashSet<>())
                    .add((String) row[1]));
    return groups.stream()
        .filter(
            group -> {
              Set<String> held =
                  group.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
              return !held.equals(inScope.getOrDefault(group.getId(), Set.of()));
            })
        .toList();
  }
}
