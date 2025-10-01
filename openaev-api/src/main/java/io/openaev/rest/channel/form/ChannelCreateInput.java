package io.openaev.rest.channel.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class ChannelCreateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("channel_type")
  private String type;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("channel_name")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("channel_description")
  private String description;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
