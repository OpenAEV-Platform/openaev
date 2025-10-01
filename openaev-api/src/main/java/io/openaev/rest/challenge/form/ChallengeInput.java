package io.openaev.rest.challenge.form;

import static io.openaev.config.AppConfig.EMPTY_MESSAGE;
import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ChallengeInput(
    @NotBlank(message = MANDATORY_MESSAGE) @JsonProperty("challenge_name") String name,
    @JsonProperty("challenge_category") String category,
    @JsonProperty("challenge_content") String content,
    @JsonProperty("challenge_score") Double score,
    @JsonProperty("challenge_max_attempts") Integer maxAttempts,
    @JsonProperty("challenge_tags") List<String> tagIds,
    @JsonProperty("challenge_documents") List<String> documentIds,
    @NotEmpty(message = EMPTY_MESSAGE) @JsonProperty("challenge_flags") List<FlagInput> flags) {

  public ChallengeInput {
    if (tagIds == null) {
      tagIds = List.of();
    }
    if (documentIds == null) {
      documentIds = List.of();
    }
    if (flags == null) {
      flags = List.of();
    }
  }
}
