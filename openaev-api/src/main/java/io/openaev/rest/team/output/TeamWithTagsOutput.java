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
public class TeamWithTagsOutput extends TeamBasicOutput {

    @JsonProperty("team_tags")
    @Schema(description = "List of tags of the team")
    private Set<String> tags;

  public static TeamWithTagsOutput fromQueryModel(@NotNull final TeamQueryModel t) {
    return TeamWithTagsOutput.builder()

        .id(t.getId())
        .name(t.getName())
        .description(t.getDescription())
        .contextual(t.getContextual())
        .updatedAt(t.getUpdatedAt())

        .tags(t.getTags())

        .build();
  }

}
