package io.openaev.rest.stream.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AiQueryMessageModel {

  @JsonProperty("role")
  private String role;

  @JsonProperty("content")
  private String content;

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
