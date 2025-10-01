package io.openaev.rest.notification_rule.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Getter
@Builder(toBuilder = true)
public class CreateNotificationRuleInput {

  @JsonProperty("resource_type")
  @NotNull
  private String resourceType;

  @JsonProperty("resource_id")
  @NotNull
  private String resourceId;

  @JsonProperty("trigger")
  @NotNull
  private String trigger;

  @JsonProperty("type")
  @NotNull
  private String type;

  @JsonProperty("subject")
  @NotNull
  private String subject;
}
