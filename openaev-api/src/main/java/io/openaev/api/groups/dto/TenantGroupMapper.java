package io.openaev.api.groups.dto;

import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import java.util.stream.Collectors;

public class TenantGroupMapper {

  private TenantGroupMapper() {}

  /** Call inside the request transaction: it loads the LAZY {@code Group.users}. */
  public static TenantGroupOutput toOutput(Group group) {
    return new TenantGroupOutput(
        group.getId(),
        group.getName(),
        group.getDescription(),
        group.isDefaultUserAssignation(),
        group.isAutoNewMarking(),
        group.getGrants().stream().map(TenantGroupMapper::toGrantOutput).toList(),
        group.getUsers().stream().map(User::getId).toList(),
        group.getRoles().stream().map(Role::getId).toList(),
        group.getMarkings().stream().map(MarkingDefinition::getId).collect(Collectors.toSet()));
  }

  public static TenantGroupGrantOutput toGrantOutput(Grant grant) {
    return new TenantGroupGrantOutput(
        grant.getId(),
        grant.getName(),
        grant.getGroup() != null ? grant.getGroup().getId() : null,
        grant.getResourceId(),
        grant.getGrantResourceType());
  }
}
