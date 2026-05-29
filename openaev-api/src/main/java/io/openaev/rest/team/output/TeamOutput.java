package io.openaev.rest.team.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

/**
 * Lightweight team output for search/list endpoints. Contains only scalar fields and a user count
 * — no collection joins (tags, users, exercises, scenarios).
 */
@Builder
@Data
public class TeamOutput {

  @JsonProperty("team_id")
  @NotBlank
  @Schema(description = "ID of the team")
  private String id;

  @JsonProperty("team_name")
  @NotBlank
  @Schema(description = "Name of the team")
  private String name;

  @JsonProperty("team_description")
  @Schema(description = "Description of the team")
  private String description;

  @JsonProperty("team_contextual")
  @Schema(
      description =
          "True if the team is contextual (exists only in the scenario/simulation it is linked to)")
  private Boolean contextual;

  @JsonProperty("team_users_number")
  @Schema(description = "Number of users of the team")
  private long usersNumber;

  @JsonProperty("team_updated_at")
  @NotNull
  @Schema(description = "Update date of the team")
  private Instant updatedAt;

  @JsonProperty("team_tags")
  @Schema(description = "List of tags of the team")
  private Set<String> tags;

  /** Maps a native-query result row to a {@link TeamOutput}. */
  public static TeamOutput fromRow(Object[] row) {
    Set<String> tags = new HashSet<>();
    if (row[6] instanceof Array sqlArray) {
      try {
        tags = new HashSet<>(Arrays.asList((String[]) sqlArray.getArray()));
      } catch (SQLException e) {
        // leave tags empty
      }
    }
    Instant updatedAt = null;
    if (row[4] instanceof java.sql.Timestamp ts) {
      updatedAt = ts.toInstant();
    } else if (row[4] instanceof LocalDateTime ldt) {
      updatedAt = ldt.toInstant(ZoneOffset.UTC);
    } else if (row[4] instanceof OffsetDateTime odt) {
      updatedAt = odt.toInstant();
    } else if (row[4] instanceof Instant i) {
      updatedAt = i;
    }
    return TeamOutput.builder()
        .id((String) row[0])
        .name((String) row[1])
        .description((String) row[2])
        .contextual((Boolean) row[3])
        .updatedAt(updatedAt)
        .usersNumber(((Number) row[5]).longValue())
        .tags(tags)
        .build();
  }
}



