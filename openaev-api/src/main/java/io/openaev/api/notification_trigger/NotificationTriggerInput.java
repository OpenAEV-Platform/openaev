package io.openaev.api.notification_trigger;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Filters;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerPeriod;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Getter
@Builder(toBuilder = true)
public class NotificationTriggerInput {

  @JsonProperty("notification_trigger_name")
  @Schema(description = "Name of the notification trigger")
  @NotBlank
  private String name;

  @JsonProperty("notification_trigger_type")
  @Schema(description = "Type of the trigger (LIVE or DIGEST)")
  @NotNull
  private NotificationTriggerType type;

  @JsonProperty("notification_trigger_enabled")
  @Schema(description = "Whether the trigger is enabled")
  @Builder.Default
  private boolean enabled = true;

  @JsonProperty("notification_trigger_resource_type")
  @Schema(description = "Resource type watched by a live trigger")
  private ResourceType resourceType;

  @JsonProperty("notification_trigger_event_types")
  @Schema(description = "Subscribed lifecycle operations (CREATE, UPDATE, DELETE)")
  @Builder.Default
  private List<NotificationTriggerEventType> eventTypes = new ArrayList<>();

  @JsonProperty("notification_trigger_filters")
  @Schema(description = "Filter group applied to matching entities")
  private Filters.FilterGroup filters;

  @JsonProperty("notification_trigger_instance_id")
  @Schema(description = "Entity id for instance triggers")
  private String instanceId;

  @JsonProperty("notification_trigger_period")
  @Schema(description = "Digest period (HOUR, DAY, WEEK, MONTH)")
  private NotificationTriggerPeriod period;

  @JsonProperty("notification_trigger_time")
  @Schema(description = "Digest firing time (UTC): DAY=HH:mm, WEEK=<1-7>-HH:mm, MONTH=<1-31>-HH:mm")
  private String triggerTime;

  @JsonProperty("notification_trigger_children")
  @Schema(description = "Composed live trigger ids for a digest")
  @Builder.Default
  private List<String> childTriggerIds = new ArrayList<>();

  @JsonProperty("notification_trigger_notifiers")
  @Schema(description = "Notifier ids used for delivery")
  @Builder.Default
  private List<String> notifierIds = new ArrayList<>();

  @JsonProperty("notification_trigger_recipient_users")
  @Schema(description = "Targeted recipient user ids (admins only; empty = owner)")
  @Builder.Default
  private List<String> recipientUserIds = new ArrayList<>();

  @JsonProperty("notification_trigger_recipient_groups")
  @Schema(description = "Targeted recipient group ids (admins only)")
  @Builder.Default
  private List<String> recipientGroupIds = new ArrayList<>();
}
