package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InjectUpdateStatusInput {

  @JsonProperty("status")
  private String status;

  @JsonProperty("message")
  private String message;

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
