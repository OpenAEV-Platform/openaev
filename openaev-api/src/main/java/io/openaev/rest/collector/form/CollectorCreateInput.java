package io.openaev.rest.collector.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CollectorCreateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("collector_id")
  private String id;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("collector_name")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("collector_type")
  private String type;

  @JsonProperty("collector_period")
  private int period;

  @JsonProperty("collector_security_platform")
  private String securityPlatform;
}
