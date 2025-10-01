package io.openaev.rest.user.form.user;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class UpdatePasswordInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_plain_password")
  private String password;

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
