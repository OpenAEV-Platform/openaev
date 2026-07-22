package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Partial update of the lessons learned settings of a simulation or a scenario. Every field is
 * nullable so callers only change what they send (existing API consumers that only send {@code
 * lessons_anonymized} must not reset the enabled flag, and vice versa).
 */
@Setter
@Getter
public class LessonsInput {

  @JsonProperty("lessons_anonymized")
  @Schema(description = "Whether questionnaire answers are anonymized (unchanged when absent)")
  private Boolean lessonsAnonymized;

  @JsonProperty("lessons_enabled")
  @Schema(description = "Whether the lessons learned module is enabled (unchanged when absent)")
  private Boolean lessonsEnabled;
}
