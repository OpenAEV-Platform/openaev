package io.openaev.rest.notifier.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.NotifierType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotifierOutput {

  @JsonProperty("notifier_id")
  @Schema(description = "ID of the notifier")
  @NotNull
  private String id;

  @JsonProperty("notifier_name")
  @Schema(description = "Name of the notifier")
  private String name;

  @JsonProperty("notifier_description")
  @Schema(description = "Description of the notifier")
  private String description;

  @JsonProperty("notifier_type")
  @Schema(description = "Type of the notifier (UI, EMAIL, WEBHOOK)")
  private NotifierType type;

  @JsonProperty("notifier_configuration")
  @Schema(description = "Type-specific configuration")
  private Map<String, Object> configuration;

  @JsonProperty("notifier_built_in")
  @Schema(description = "Whether the notifier is built-in (read-only)")
  private boolean builtIn;

  @JsonProperty("notifier_created_at")
  @Schema(description = "Creation date of the notifier")
  private Instant createdAt;

  @JsonProperty("notifier_updated_at")
  @Schema(description = "Last update date of the notifier")
  private Instant updatedAt;
}
