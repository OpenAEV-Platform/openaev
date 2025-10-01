package io.openaev.rest.channel.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChannelUpdateLogoInput {

  @JsonProperty("channel_logo_dark")
  private String logoDark;

  @JsonProperty("channel_logo_light")
  private String logoLight;

  public String getLogoDark() {
    return logoDark;
  }

  public void setLogoDark(String logoDark) {
    this.logoDark = logoDark;
  }

  public String getLogoLight() {
    return logoLight;
  }

  public void setLogoLight(String logoLight) {
    this.logoLight = logoLight;
  }
}
