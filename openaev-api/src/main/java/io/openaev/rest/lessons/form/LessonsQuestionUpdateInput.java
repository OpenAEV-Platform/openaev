package io.openaev.rest.lessons.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class LessonsQuestionUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("lessons_question_content")
  private String content;

  @JsonProperty("lessons_question_explanation")
  private String explanation;

  @JsonProperty("lessons_question_order")
  private int order;

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String explanation) {
    this.explanation = explanation;
  }

  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }
}
