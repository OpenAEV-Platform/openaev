package io.openaev.rest.channel.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class ChannelUpdateInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("channel_type")
  private String type;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("channel_name")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("channel_description")
  private String description;

  @JsonProperty("channel_mode")
  private String mode;

  @JsonProperty("channel_primary_color_dark")
  private String primaryColorDark;

  @JsonProperty("channel_primary_color_light")
  private String primaryColorLight;

  @JsonProperty("channel_secondary_color_dark")
  private String secondaryColorDark;

  @JsonProperty("channel_secondary_color_light")
  private String secondaryColorLight;

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

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPrimaryColorDark() {
    return primaryColorDark;
  }

  public void setPrimaryColorDark(String primaryColorDark) {
    this.primaryColorDark = primaryColorDark;
  }

  public String getPrimaryColorLight() {
    return primaryColorLight;
  }

  public void setPrimaryColorLight(String primaryColorLight) {
    this.primaryColorLight = primaryColorLight;
  }

  public String getSecondaryColorDark() {
    return secondaryColorDark;
  }

  public void setSecondaryColorDark(String secondaryColorDark) {
    this.secondaryColorDark = secondaryColorDark;
  }

  public String getSecondaryColorLight() {
    return secondaryColorLight;
  }

  public void setSecondaryColorLight(String secondaryColorLight) {
    this.secondaryColorLight = secondaryColorLight;
  }
}
