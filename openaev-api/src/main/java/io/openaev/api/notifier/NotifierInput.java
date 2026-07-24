package io.openaev.api.notifier;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.NotifierType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Getter
@Builder(toBuilder = true)
public class NotifierInput {

  @JsonProperty("notifier_name")
  @Schema(description = "Name of the notifier")
  @NotBlank
  private String name;

  @JsonProperty("notifier_description")
  @Schema(description = "Description of the notifier")
  private String description;

  @JsonProperty("notifier_type")
  @Schema(description = "Type of the notifier (UI, EMAIL, WEBHOOK)")
  @NotNull
  private NotifierType type;

  @JsonProperty("notifier_configuration")
  @Schema(
      description =
          "Type-specific configuration: email = subject/template (FreeMarker), webhook ="
              + " url/verb/headers/template")
  @Builder.Default
  private Map<String, Object> configuration = new HashMap<>();
}
