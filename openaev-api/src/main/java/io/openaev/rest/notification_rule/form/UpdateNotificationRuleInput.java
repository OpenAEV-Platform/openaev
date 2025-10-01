package io.openaev.rest.notification_rule.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Getter
@Builder
public class UpdateNotificationRuleInput {

  @JsonProperty("subject")
  @NotNull
  private String subject;
}
