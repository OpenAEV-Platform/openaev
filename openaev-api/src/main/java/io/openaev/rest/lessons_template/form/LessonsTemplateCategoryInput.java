package io.openaev.rest.lessons_template.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LessonsTemplateCategoryInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("lessons_template_category_name")
  private String name;

  @JsonProperty("lessons_template_category_description")
  private String description;

  @JsonProperty("lessons_template_category_order")
  @NotNull
  private int order;
}
