package io.openaev.rest.user.form.me;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.AuditLogRedact;
import jakarta.validation.constraints.NotBlank;

public class UpdateMePasswordInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_current_password")
  @AuditLogRedact
  private String currentPassword;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_plain_password")
  @AuditLogRedact
  private String password;

  public String getCurrentPassword() {
    return currentPassword;
  }

  public void setCurrentPassword(String currentPassword) {
    this.currentPassword = currentPassword;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
