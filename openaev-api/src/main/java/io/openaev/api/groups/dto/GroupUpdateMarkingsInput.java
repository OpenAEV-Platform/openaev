package io.openaev.api.groups.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The markings a group grants its members.
 *
 * <p>Replace-the-whole-set, like {@code GroupUpdateUsersInput} and {@code GroupUpdateRolesInput}:
 * an empty list revokes every grant. A PATCH-style add/remove would make "what does this group
 * grant?" depend on request ordering, which is the wrong property for a security boundary.
 */
public record GroupUpdateMarkingsInput(
    @JsonProperty("group_markings") @NotNull List<String> markingIds) {

  public GroupUpdateMarkingsInput {
    markingIds = markingIds == null ? List.of() : List.copyOf(markingIds);
  }
}
