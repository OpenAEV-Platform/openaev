package io.openaev.service;

import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
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
 * of the current transaction reads.
 *
 * <p>The roles of a group are an eager association of the group: a load by id of a group the
 * session already holds hands out that instance with the roles of its first read, which a scope
 * other than the first one reads differently. A caller that has to hand out the roles of the
 * current scope refreshes only the groups this class returns, so a group whose roles are the same
 * costs no reload. One query reads the roles of all the groups, whatever their number.
 */
public final class GroupRolesInScope {

  private GroupRolesInScope() {}

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
