package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExerciseUpdateLogoInput {

  @JsonProperty("exercise_logo_dark")
  private String logoDark;

  @JsonProperty("exercise_logo_light")
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
