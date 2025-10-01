package io.openaev.rest.lessons.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LessonsSendInput {

  @JsonProperty("subject")
  private String subject;

  @JsonProperty("body")
  private String body;

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }
}
