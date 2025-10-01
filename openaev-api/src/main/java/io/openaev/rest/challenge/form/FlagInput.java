package io.openaev.rest.challenge.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class FlagInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("flag_type")
  private String type;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("flag_value")
  private String value;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
