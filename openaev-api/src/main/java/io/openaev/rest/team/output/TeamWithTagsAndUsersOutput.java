package io.openaev.rest.team.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.team.query_model.TeamQueryModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TeamWithTagsAndUsersOutput extends TeamBasicOutput {

  @JsonProperty("team_tags")
  @Schema(description = "List of tags of the team")
  private Set<String> tags;

  @JsonProperty("team_users")
  @Schema(description = "User ids of the team")
  private Set<String> users;

  @JsonProperty("team_users_number")
  @Schema(description = "Number of users of the team")
  private long usersNumber;

  public static TeamWithTagsAndUsersOutput fromQueryModel(@NotNull final TeamQueryModel t) {
    return TeamWithTagsAndUsersOutput.builder()

        .id(t.getId())
        .name(t.getName())
        .description(t.getDescription())
        .contextual(t.getContextual())
        .updatedAt(t.getUpdatedAt())

        .tags(t.getTags())
        .users(t.getUsers())
        .usersNumber(t.getUsersNumber())

        .build();
  }

}
