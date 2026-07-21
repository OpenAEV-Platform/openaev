package io.openaev.rest.notification_trigger.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Filters;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerPeriod;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationTriggerOutput {

  @JsonProperty("notification_trigger_id")
  @Schema(description = "ID of the notification trigger")
  @NotNull
  private String id;

  @JsonProperty("notification_trigger_name")
  @Schema(description = "Name of the notification trigger")
  private String name;

  @JsonProperty("notification_trigger_type")
  @Schema(description = "Type of the trigger (LIVE or DIGEST)")
  private NotificationTriggerType type;

  @JsonProperty("notification_trigger_enabled")
  @Schema(description = "Whether the trigger is enabled")
  private boolean enabled;

  @JsonProperty("notification_trigger_resource_type")
  @Schema(description = "Resource type watched by a live trigger")
  private ResourceType resourceType;

  @JsonProperty("notification_trigger_event_types")
  @Schema(description = "Subscribed lifecycle operations")
  private List<NotificationTriggerEventType> eventTypes;

  @JsonProperty("notification_trigger_filters")
  @Schema(description = "Filter group applied to matching entities")
  private Filters.FilterGroup filters;

  @JsonProperty("notification_trigger_instance_id")
  @Schema(description = "Entity id for instance triggers")
  private String instanceId;

  @JsonProperty("notification_trigger_period")
  @Schema(description = "Digest period")
  private NotificationTriggerPeriod period;

  @JsonProperty("notification_trigger_time")
  @Schema(description = "Digest firing time (UTC)")
  private String triggerTime;

  @JsonProperty("notification_trigger_children")
  @Schema(description = "Composed live trigger ids for a digest")
  private List<String> childTriggerIds;

  @JsonProperty("notification_trigger_notifiers")
  @Schema(description = "Notifier ids used for delivery")
  private List<String> notifierIds;

  @JsonProperty("notification_trigger_recipient_users")
  @Schema(description = "Targeted recipient user ids")
  private List<String> recipientUserIds;

  @JsonProperty("notification_trigger_recipient_groups")
  @Schema(description = "Targeted recipient group ids")
  private List<String> recipientGroupIds;

  @JsonProperty("notification_trigger_owner")
  @Schema(description = "Owner user id")
  private String ownerId;

  @JsonProperty("notification_trigger_created_at")
  @Schema(description = "Creation date")
  private Instant createdAt;

  @JsonProperty("notification_trigger_updated_at")
  @Schema(description = "Last update date")
  private Instant updatedAt;
}
