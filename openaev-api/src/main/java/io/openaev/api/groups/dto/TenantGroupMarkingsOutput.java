package io.openaev.api.groups.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of a marking write on a {@link Group} (Task 2).
 *
 * <p>Deliberately narrow rather than the full {@code Group} entity: the endpoint's contract is
 * "here is the marking grant on this group now" (plus its name, for a readable confirmation), not a
 * general-purpose group read — nothing else about the group, including the {@code group_grants}/
 * {@code group_users}/{@code group_roles} lazy relations, is part of this response.
 */
@Getter
@Builder
@JsonInclude(NON_NULL)
public class TenantGroupMarkingsOutput {

  @Schema(description = "Group Id")
  @JsonProperty("group_id")
  @NotBlank
  private String id;

  @Schema(description = "Group name")
  @JsonProperty("group_name")
  @NotBlank
  private String name;

  @Schema(description = "Markings currently granted by the group")
  @JsonProperty("group_markings")
  private Set<String> markingIds;

  public static TenantGroupMarkingsOutput from(Group group) {
    return TenantGroupMarkingsOutput.builder()
        .id(group.getId())
        .name(group.getName())
        .markingIds(
            group.getMarkings().stream().map(MarkingDefinition::getId).collect(Collectors.toSet()))
        .build();
  }
}
