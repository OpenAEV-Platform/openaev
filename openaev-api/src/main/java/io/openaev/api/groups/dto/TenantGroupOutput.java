package io.openaev.api.groups.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A tenant group as the group endpoints answer it, with the fields of the {@link Group} entity.
 *
 * <p>Built inside the request transaction: {@code Group.users} is LAZY, and serialising the entity
 * after the commit would load the members outside the request's tenant scope.
 */
public record TenantGroupOutput(
    @JsonProperty("group_id") @NotBlank String id,
    @JsonProperty("group_name") @NotBlank String name,
    @JsonProperty("group_description") String description,
    @JsonProperty("group_default_user_assign") boolean defaultUserAssignation,
    @JsonProperty("group_auto_new_marking") boolean autoNewMarking,
    @JsonProperty("group_grants") List<Grant> grants,
    @JsonProperty("group_users") List<String> userIds,
    @JsonProperty("group_roles") List<String> roleIds,
    @JsonProperty("group_markings") Set<String> markingIds) {

  public static TenantGroupOutput from(Group group) {
    return new TenantGroupOutput(
        group.getId(),
        group.getName(),
        group.getDescription(),
        group.isDefaultUserAssignation(),
        group.isAutoNewMarking(),
        List.copyOf(group.getGrants()),
        group.getUsers().stream().map(User::getId).toList(),
        group.getRoles().stream().map(Role::getId).toList(),
        group.getMarkings().stream().map(MarkingDefinition::getId).collect(Collectors.toSet()));
  }
}
