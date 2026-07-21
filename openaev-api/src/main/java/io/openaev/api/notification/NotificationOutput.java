package io.openaev.api.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.NotificationTriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationOutput {

  @JsonProperty("notification_id")
  @Schema(description = "ID of the notification")
  @NotNull
  private String id;

  @JsonProperty("notification_name")
  @Schema(description = "Name of the trigger that produced the notification")
  private String name;

  @JsonProperty("notification_type")
  @Schema(description = "Type of the notification (LIVE or DIGEST)")
  private NotificationTriggerType type;

  @JsonProperty("notification_content")
  @Schema(description = "Content groups: [{title, events: [{operation, message, ...}]}]")
  private List<Map<String, Object>> content;

  @JsonProperty("notification_is_read")
  @Schema(description = "Whether the notification has been read")
  private boolean read;

  @JsonProperty("notification_created_at")
  @Schema(description = "Creation date")
  private Instant createdAt;
}
