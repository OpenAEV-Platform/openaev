package io.openaev.rest.lessons.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class LessonsCategoryUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("lessons_category_name")
  private String name;

  @JsonProperty("lessons_category_description")
  private String description;

  @JsonProperty("lessons_category_order")
  private int order;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }
}
