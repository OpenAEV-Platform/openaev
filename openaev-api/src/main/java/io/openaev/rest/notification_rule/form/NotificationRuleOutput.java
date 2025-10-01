package io.openaev.rest.notification_rule.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationRuleOutput {
  @JsonProperty("notification_rule_id")
  @Schema(description = "ID of the notification rule")
  @NotNull
  private String id;

  @JsonProperty("notification_rule_resource_type")
  @Schema(description = "Resource type of the resource associated with the rule")
  private String resourceType;

  @JsonProperty("notification_rule_resource_id")
  @Schema(description = "Resource id of the resource associated with the rule")
  private String resourceId;

  @JsonProperty("notification_rule_trigger")
  @Schema(description = "Event that will trigger the notification")
  private String trigger;

  @JsonProperty("notification_rule_subject")
  @Schema(description = "Subject of the notification rule")
  private String subject;

  @JsonProperty("notification_rule_owner")
  @Schema(description = "Owner of the notification rule")
  private String ownerId;
}
