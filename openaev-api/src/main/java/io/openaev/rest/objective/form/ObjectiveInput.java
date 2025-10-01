package io.openaev.rest.objective.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ObjectiveInput {

  @JsonProperty("objective_title")
  private String title;

  @JsonProperty("objective_description")
  private String description;

  @JsonProperty("objective_priority")
  private Short priority;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Short getPriority() {
    return priority;
  }

  public void setPriority(Short priority) {
    this.priority = priority;
  }
}
