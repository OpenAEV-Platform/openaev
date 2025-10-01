package io.openaev.rest.group.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Builder(toBuilder = true)
@Getter
@Jacksonized
@EqualsAndHashCode
public class GroupUpdateRolesInput {

  @JsonProperty("group_roles")
  @Schema(description = "List of role ids associated with the group")
  private List<String> roleIds;
}
