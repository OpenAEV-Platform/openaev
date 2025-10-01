package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InjectUpdateActivationInput {

  @JsonProperty("inject_enabled")
  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
