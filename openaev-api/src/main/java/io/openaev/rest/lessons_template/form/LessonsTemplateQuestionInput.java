package io.openaev.rest.lessons_template.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LessonsTemplateQuestionInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("lessons_template_question_content")
  private String content;

  @JsonProperty("lessons_template_question_explanation")
  private String explanation;

  @JsonProperty("lessons_template_question_order")
  @NotNull
  private int order;
}
