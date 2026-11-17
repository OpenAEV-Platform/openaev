package io.openaev.api.groups.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Group;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;

/**
 * A tenant group as the group endpoints answer it, with the fields of the {@link Group} entity.
 *
 * <p>Built inside the request transaction ({@link TenantGroupMapper}): {@code Group.users} is LAZY,
 * and serialising the entity after the commit would load the members outside the request's tenant
 * scope.
 */
public record TenantGroupOutput(
    @JsonProperty("group_id") @NotBlank String id,
    @JsonProperty("group_name") @NotBlank String name,
    @JsonProperty("group_description") String description,
    @JsonProperty("group_default_user_assign") boolean defaultUserAssignation,
    @JsonProperty("group_auto_new_marking") boolean autoNewMarking,
    @JsonProperty("group_grants") List<TenantGroupGrantOutput> grants,
    @JsonProperty("group_users") List<String> userIds,
    @JsonProperty("group_roles") List<String> roleIds,
    @JsonProperty("group_markings") Set<String> markingIds) {}
