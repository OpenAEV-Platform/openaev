package io.openaev.rest.stream.ai;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiMessageInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("ai_input")
  private String input;

  @Nullable
  @JsonProperty("ai_paragraphs")
  private Integer paragraphs = 5;

  @Nullable
  @JsonProperty("ai_context")
  private String context = "No context";

  @Nullable
  @JsonProperty("ai_tone")
  private String tone = "natural";

  @Nullable
  @JsonProperty("ai_sender")
  private String sender;

  @Nullable
  @JsonProperty("ai_recipient")
  private String recipient;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("ai_format")
  private String format;
}
